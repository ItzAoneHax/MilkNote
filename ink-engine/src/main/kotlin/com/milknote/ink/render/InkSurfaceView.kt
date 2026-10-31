package com.milknote.ink.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.graphics.lowlatency.CanvasFrontBufferedRenderer
import com.milknote.ink.input.InkAction
import com.milknote.ink.input.InkController
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.PressureGamma
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.ToolConfig

/**
 * 书写纸面(PEN-ADAPTATION 通路①挂点:「Compose 外层 AndroidView」)。
 *
 * - 笔(STYLUS)书写:批次点展开 + 免缓冲派发 + MotionPredictor 预测尾巴
 * - 双路径出墨(DESIGN-V0 §3.1):
 *   - Canvas 路径(默认):活动笔迹每帧重绘,笔抬后进 Picture 缓存
 *   - 前置缓冲路径(frontBufferEnabled,实验性):活动笔迹增量走
 *     CanvasFrontBufferedRenderer(GL 前置缓冲,<10ms 出墨目标),笔抬后 commit 进
 *     Picture 缓存;初始化失败或关闭时自动回退 Canvas 路径,行为与默认完全一致
 * - 倒置笔(TOOL_TYPE_ERASER)自动切橡皮;副键按住书写 = 临时橡皮(holdActionProvider)
 * - 套索选择(PEN-ADAPTATION §4.4):lassoArmed/按住修饰 → 圈选 → 选中集可拖动/删除
 * - 手指仅导航:单指平移 / 双指捏合缩放(防误掌在 device-mi 拦截器先行;
 *   无笔环境单指=书写/圈选/拖动,保证无笔设备可用)
 * - inputInterceptor:device-mi PenInputPort 首览(吞掉被拒事件/按键修饰模式)
 */
class InkSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var controller: InkController? = null
        set(value) {
            field?.removeInvalidator(invalidator)
            field = value
            value?.addInvalidator(invalidator)
            committedKey = Long.MIN_VALUE
            postInvalidateOnAnimation()
        }

    private val invalidator = { postInvalidateOnAnimation() }

    /** device-mi 首览拦截:返回 true = 消费(事件不再进入本视图) */
    var inputInterceptor: ((MotionEvent) -> Boolean)? = null

    /**
     * 按住修饰查询(落笔瞬间同步调用):主/副键按住书写时由 device-mi 返回
     * LASO(套索圈选)/ TEMP_ERASER(临时橡皮)/ null(正常书写)。
     */
    var holdActionProvider: (() -> InkAction?)? = null

    /** 纸张模板:dot | line | grid | blank */
    var paperStyle: String = "dot"
        set(value) { field = value; committedKey = Long.MIN_VALUE; invalidate() }

    /** 颜色由界面主题注入(奶白纸 + #D8D4C8 点) */
    var paperColor: Int = 0xFFFAF9F5.toInt()
    var dotColor: Int = 0xFFD8D4C8.toInt()
    var backdropColor: Int = 0xFFEFEDE5.toInt()   // 纸外(视口)底色
    var paperFrameColor: Int = 0x1423221E          // 纸面细边

    /**
     * 暗色纸面:显示层整体换色(纸/点阵/视口/光标/套索)。
     * 笔迹颜色是数据(DESIGN-V0 §6.1),存储不变——暗色下墨黑/红/蓝按 [displayColor] 映射为浅色显示,
     * 导出与缩略图恒走浅色纸,不受影响。
     */
    var darkTheme: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyPaperTheme()
            committedKey = Long.MIN_VALUE
            invalidate()
        }

    // ---- 页面几何(逻辑页 mm @ 5.67px/mm ≈ 144dpi;尺寸/方向由笔记本元数据注入) ----
    val mmToPx: Float = 5.67f
    var pageWidthPx: Float = 210f * mmToPx
        private set
    var pageHeightPx: Float = 297f * mmToPx
        private set

    /** 纸张尺寸(px,已含方向);默认 A4 竖向(旧笔记本几何不变) */
    fun setPageSizePx(w: Float, h: Float) {
        if (w == pageWidthPx && h == pageHeightPx) return
        pageWidthPx = w
        pageHeightPx = h
        committedKey = Long.MIN_VALUE
        if (viewportW > 0f) fitPage() else invalidate()
    }

    // 视口变换:view = page * zoom + pan
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var viewportW = 0f
    private var viewportH = 0f

    // Picture 缓存(已提交笔迹;键含页面版本 + 选中集版本 + 压感曲线档)
    private var committed: Picture = Picture()
    private var committedKey: Long = Long.MIN_VALUE

    // 悬停光标
    private var hoverX = -1f
    private var hoverY = -1f

    /** 本会话是否见过笔(DESIGN-V0 §3.4:TYPE_STYLUS 存在时手指只导航;无笔时单指=合法书写,双指=缩放) */
    private var stylusSeen = false
    private var fingerDrawing = false

    // 本笔是否倒置橡皮
    private var invertedEraser = false

    // 套索/拖动输入状态(数据在 controller,这里只记路由)
    private var lassoStrokeActive = false
    private var dragActive = false
    private var dragAnchorX = 0f
    private var dragAnchorY = 0f

    // 轨迹预测(androidx.input MotionEventPredictor;不可用时静默降级)
    private val predictor: Any? = try {
        androidx.input.motionprediction.MotionEventPredictor.newInstance(this)
    } catch (_: Throwable) { null }

    // 手势导航
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            userAdjusted = true
            val newZoom = (zoom * detector.scaleFactor).coerceIn(0.35f, 6f)
            val focusPageX = pageX(detector.focusX)
            val focusPageY = pageY(detector.focusY)
            zoom = newZoom
            panX = detector.focusX - focusPageX * zoom
            panY = detector.focusY - focusPageY * zoom
            invalidate()
            return true
        }
    })
    private var lastPanX = 0f
    private var lastPanY = 0f

    // ---------------- 前置缓冲出墨(DESIGN-V0 §3.1 双路径) ----------------

    /** 前置缓冲渲染开关(默认关;开启失败自动回退 Canvas 路径) */
    var frontBufferEnabled: Boolean
        get() = frontWanted
        set(value) {
            frontWanted = value
            if (value) attachFrontRenderer() else detachFrontRenderer()
            postInvalidateOnAnimation()
        }

    private var frontWanted = false
    private var fbr: CanvasFrontBufferedRenderer<FrontSegment>? = null
    private val frontCallback = FrontInkCallback()

    /** 前置缓冲当前笔的工具信息(落笔时从活动笔迹快照取) */
    private var frontTool: InkTool = InkTool.PEN
    private var frontColor: Long = 0xFF23221E
    private var frontBaseWidthMm = 0.7f
    private var frontPxPerMm = 0f
    private var frontLastPt: InkPoint? = null
    private var pendingCommit: Runnable? = null

    private val frontActive: Boolean get() = fbr != null

    /** 前置缓冲覆盖层:半透明合成在纸面之上(z-order 须在 attach 前设置) */
    private val frontSurface: SurfaceView = SurfaceView(context).apply {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        visibility = GONE
    }

    init {
        setWillNotDraw(false) // ViewGroup 默认跳过 onDraw,显式开启
        addView(
            frontSurface,
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    /** 前置缓冲增量段(视口坐标;渲染线程只读) */
    private data class FrontSegment(
        val points: List<InkPoint>,
        val tool: InkTool,
        val color: Long,
        val baseWidthMm: Float,
        val gamma: PressureGamma,
        val pxPerMm: Float,
    )

    /**
     * 前置缓冲 Canvas 回调(渲染线程):
     * - 前缓冲:增量画段(含预测点尾巴;下一段从上一真实点续起,预测残影被新段覆盖)
     * - 多缓冲:commit 目标层——已提交笔迹由 View 层 Picture 渲染,这里只清屏防残影
     */
    private class FrontInkCallback : CanvasFrontBufferedRenderer.Callback<FrontSegment> {
        private var strokePaint: Paint? = null
        private var clearPaint: Paint? = null

        override fun onDrawFrontBufferedLayer(
            canvas: Canvas,
            bufferWidth: Int,
            bufferHeight: Int,
            param: FrontSegment,
        ) {
            val path = StrokeRenderer.buildPath(
                Stroke("front", param.tool, param.color, param.baseWidthMm, param.points),
                param.pxPerMm,
                param.gamma,
            )
            canvas.drawPath(path, paint(param.color, param.tool))
        }

        override fun onDrawMultiBufferedLayer(
            canvas: Canvas,
            bufferWidth: Int,
            bufferHeight: Int,
            params: Collection<FrontSegment>,
        ) {
            // 注意:这是 FBR 自己的缓冲画布(非 Compose interop),按缓冲尺寸显式清透明
            val p = clearPaint ?: Paint().apply {
                style = Paint.Style.FILL
                color = 0
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }.also { clearPaint = it }
            canvas.drawRect(0f, 0f, bufferWidth.toFloat(), bufferHeight.toFloat(), p)
        }

        private fun paint(color: Long, tool: InkTool): Paint {
            val p = strokePaint ?: Paint().also { strokePaint = it }
            p.reset()
            p.color = color.toInt()
            p.isAntiAlias = true
            p.style = Paint.Style.FILL
            if (tool == InkTool.HIGHLIGHTER) p.alpha = (255 * 0.45f).toInt()
            return p
        }
    }

    private fun attachFrontRenderer() {
        if (fbr != null) return
        // 等到 attach + 有尺寸再建(AndroidView update 可能早于布局)
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
        runCatching {
            fbr = CanvasFrontBufferedRenderer(frontSurface, frontCallback)
            frontSurface.visibility = VISIBLE
        }.onFailure {
            fbr = null
            frontSurface.visibility = GONE
        }
    }

    private fun detachFrontRenderer() {
        cancelPendingCommit()
        runCatching { fbr?.release(true) }
        fbr = null
        frontLastPt = null
        frontSurface.visibility = GONE
    }

    /** 落笔:先落地上一笔的延迟 commit(渲染队列有序,新段不会被清) */
    private fun flushPendingCommit() {
        val r = pendingCommit
        if (r != null) {
            removeCallbacks(r)
            pendingCommit = null
            runCatching { fbr?.commit() }
        }
    }

    /**
     * 抬笔:commit 延后两帧——先让 View 层把已提交笔迹画进 Picture,
     * 再清前置缓冲,避免同帧清空造成的抬笔闪烁。
     */
    private fun scheduleFrontCommit() {
        val r = fbr ?: return
        val runnable = Runnable {
            pendingCommit = null
            runCatching { r.commit() }
        }
        pendingCommit?.let(::removeCallbacks)
        pendingCommit = runnable
        postOnAnimation { postOnAnimation(runnable) }
    }

    private fun cancelPendingCommit() {
        pendingCommit?.let(::removeCallbacks)
        pendingCommit = null
    }

    private fun cancelFrontStroke() {
        frontLastPt = null
        cancelPendingCommit()
        runCatching { fbr?.cancel() }
    }

    private fun renderFront(points: List<InkPoint>, gamma: PressureGamma) {
        val r = fbr ?: return
        if (points.isEmpty()) return
        r.renderFrontBufferedLayer(
            FrontSegment(points, frontTool, displayColor(frontColor), frontBaseWidthMm, gamma, frontPxPerMm)
        )
    }

    /** 增量段 = 上一真实点 → 新批次点 →(可选)预测点;残影由下一段覆盖 */
    private fun pushFrontBatch(raw: List<InkPoint>, predictedView: InkPoint?, gamma: PressureGamma) {
        if (fbr == null || raw.isEmpty()) return
        val pts = ArrayList<InkPoint>(raw.size + 2)
        frontLastPt?.let { pts.add(it) }
        pts.addAll(raw)
        predictedView?.let { pts.add(it) }
        renderFront(pts, gamma)
        frontLastPt = raw.last()
    }

    private fun beginFrontStroke(first: InkPoint, snapshot: Stroke?, gamma: PressureGamma) {
        if (fbr == null) return
        flushPendingCommit()
        snapshot?.let {
            frontTool = it.tool
            frontColor = it.color
            frontBaseWidthMm = it.baseWidthMm
        }
        frontPxPerMm = mmToPx * zoom
        frontLastPt = first
        renderFront(listOf(first), gamma)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 免缓冲派发常开(API30+,一次性,针对笔源)—— PEN-ADAPTATION §3.3
        runCatching { requestUnbufferedDispatch(InputDevice.SOURCE_STYLUS) }
        if (frontWanted) attachFrontRenderer()
    }

    override fun onDetachedFromWindow() {
        detachFrontRenderer()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val first = viewportW <= 0f
        viewportW = w.toFloat(); viewportH = h.toFloat()
        // 尺寸变化(旋转/insets)时自动重新适配;用户手动缩放过后则尊重用户状态
        if (first || !userAdjusted) fitPage()
        if (frontWanted) attachFrontRenderer()
    }

    /** 初始:页高适配视口高,水平居中 */
    fun fitPage() {
        zoom = if (viewportH > 0) viewportH / pageHeightPx else 1f
        panX = (viewportW - pageWidthPx * zoom) / 2f
        panY = 0f
        userAdjusted = false
        invalidate()
    }

    /** 用户手动缩放/平移过后,不再自动 fit */
    private var userAdjusted = false

    private fun pageX(viewX: Float) = (viewX - panX) / zoom
    private fun pageY(viewY: Float) = (viewY - panY) / zoom

    // ---------------- 输入 ----------------

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 通路:device-mi PenInputPort 首览(防误掌/按键/诊断流水)
        if (inputInterceptor?.invoke(event) == true) return true
        return super.dispatchTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val c = controller
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> {
                hoverX = event.x; hoverY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hoverX = -1f; hoverY = -1f
                invalidate()
                return true
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val tool = event.getToolType(0)
                if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
                    stylusSeen = true
                    invertedEraser = tool == MotionEvent.TOOL_TYPE_ERASER
                    if (routeSpecialDown(event, c, collectRaw(event))) return true
                    beginInk(event, c, collectRaw(event))
                    return true
                }
                // 手指:见过笔 → 只导航;没见过笔 → 降级书写/圈选/拖动(§3.4)
                if (stylusSeen || c == null) {
                    lastPanX = event.x; lastPanY = event.y
                    scaleDetector.onTouchEvent(event)
                } else if (routeSpecialDown(event, c, collectRaw(event))) {
                    return true
                } else {
                    fingerDrawing = true
                    beginInk(event, c, collectRaw(event), fixed = true)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                if (fingerDrawing && event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    c?.cancelStroke()   // 双指:取消手指书写,转入缩放
                    fingerDrawing = false
                    cancelFrontStroke()
                }
                if (lassoStrokeActive && event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    lassoStrokeActive = false
                    c?.cancelLasso()
                }
                if (!isWriting() && !lassoStrokeActive && !dragActive) scaleDetector.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                when {
                    lassoStrokeActive && c != null -> {
                        val batch = collectRaw(event).map(::toPage)
                        batch.forEach { c.extendLasso(it) }
                        return true
                    }
                    dragActive && c != null -> {
                        c.moveSelectionDrag(pageX(event.x) - dragAnchorX, pageY(event.y) - dragAnchorY)
                        return true
                    }
                    isWriting() && c != null -> {
                        val raw = collectRaw(event)
                        val batch = raw.map(::toPage).map { if (fingerDrawing) fixedPressure(it) else it }
                        c.extendStroke(batch)
                        if (!fingerDrawing) {
                            val predicted = predictorPredict(event)
                            if (predicted != null) {
                                c.predict(
                                    listOf(
                                        InkPoint(
                                            pageX(predicted.x), pageY(predicted.y),
                                            predicted.pressure.coerceIn(0f, 1f), 0f, 0f, predicted.eventTime,
                                        )
                                    )
                                )
                                if (frontActive) {
                                    pushFrontBatch(
                                        raw,
                                        InkPoint(
                                            predicted.x, predicted.y,
                                            predicted.pressure.coerceIn(0f, 1f), 0f, 0f, predicted.eventTime,
                                        ),
                                        c.tool.pressureGamma,
                                    )
                                }
                            } else if (frontActive) {
                                pushFrontBatch(raw, null, c.tool.pressureGamma)
                            }
                        } else if (frontActive) {
                            pushFrontBatch(raw, null, c.tool.pressureGamma)
                        }
                        postInvalidateOnAnimation()
                    }
                    else -> {
                        scaleDetector.onTouchEvent(event)
                        if (!scaleDetector.isInProgress) {
                            panX += event.x - lastPanX
                            panY += event.y - lastPanY
                            lastPanX = event.x; lastPanY = event.y
                            userAdjusted = true
                            invalidate()
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                when {
                    lassoStrokeActive -> {
                        lassoStrokeActive = false
                        c?.endLasso()
                    }
                    dragActive -> {
                        dragActive = false
                        c?.endSelectionDrag()
                    }
                    isWriting() -> {
                        val raw = collectRaw(event)
                        val batch = raw.map(::toPage).map { if (fingerDrawing) fixedPressure(it) else it }
                        c?.extendStroke(batch)
                        c?.endStroke()
                        fingerDrawing = false
                        if (frontActive) {
                            frontLastPt = null
                            scheduleFrontCommit()
                        }
                    }
                    else -> scaleDetector.onTouchEvent(event)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isWriting()) {
                    c?.cancelStroke()
                    cancelFrontStroke()
                }
                if (lassoStrokeActive) {
                    lassoStrokeActive = false
                    c?.cancelLasso()
                }
                if (dragActive) {
                    dragActive = false
                    c?.cancelSelectionDrag()
                }
                fingerDrawing = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 特殊落笔路由:① 点中选中集 → 拖动;② 套索模式/按住修饰(套索)→ 圈选。
     * 返回 true = 已消费(不再进入书写)。
     */
    private fun routeSpecialDown(event: MotionEvent, c: InkController?, raw: List<InkPoint>): Boolean {
        if (c == null) return false
        val px = pageX(event.x); val py = pageY(event.y)
        // ① 选中集拖动(橡皮/手指同样可拖)
        if (c.hitSelection(px, py)) {
            c.beginSelectionDrag(px, py)
            dragActive = true
            dragAnchorX = px; dragAnchorY = py
            return true
        }
        // ② 套索:模式已武装 或 主/副键按住书写改绑为套索
        val hold = holdActionProvider?.invoke()
        if (c.lassoArmed || hold == InkAction.LASO) {
            c.beginLasso(toPage(raw.first()))
            lassoStrokeActive = true
            return true
        }
        return false
    }

    private fun beginInk(event: MotionEvent, c: InkController?, raw: List<InkPoint>, fixed: Boolean = false) {
        if (c == null) return
        // 工具覆盖:倒置橡皮 > 按住修饰(临时橡皮)> 一次性临时橡皮(改绑到单击类触发)
        val hold = holdActionProvider?.invoke()
        val override: ToolConfig? = when {
            invertedEraser -> ToolConfig(tool = InkTool.ERASER, baseWidthMm = 4f)
            hold == InkAction.TEMP_ERASER || c.consumeOneShotEraser() ->
                ToolConfig(tool = InkTool.ERASER, baseWidthMm = 4f)
            else -> null
        }
        val batch = if (fixed) raw.map { fixedPressure(toPage(it)) } else raw.map(::toPage)
        c.beginStroke(batch.first(), override)
        if (batch.size > 1) c.extendStroke(batch.subList(1, batch.size))
        if (frontActive && c.drawing) {
            beginFrontStroke(raw.first(), c.activeSnapshot(), c.tool.pressureGamma)
        }
        postInvalidateOnAnimation()
    }

    private fun isWriting(): Boolean = controller?.drawing == true

    /** 手指书写:固定压感(降级矩阵:手指 = 固定笔宽) */
    private fun fixedPressure(p: InkPoint) = p.copy(pressure = 0.5f)

    private fun toPage(p: InkPoint) = p.copy(x = pageX(p.x), y = pageY(p.y))

    /** 批次点展开(PEN-ADAPTATION §3.3):历史点 + 当前点,时间序(视口坐标) */
    private fun collectRaw(event: MotionEvent): List<InkPoint> {
        val out = ArrayList<InkPoint>(event.historySize + 1)
        for (h in 0 until event.historySize) {
            out.add(
                InkPoint(
                    event.getHistoricalX(0, h), event.getHistoricalY(0, h),
                    event.getHistoricalPressure(0, h).coerceIn(0f, 1f),
                    event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, 0, h),
                    event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, 0, h),
                    event.getHistoricalEventTime(h),
                )
            )
        }
        out.add(
            InkPoint(
                event.getX(0), event.getY(0),
                event.pressure.coerceIn(0f, 1f),
                event.getAxisValue(MotionEvent.AXIS_TILT, 0),
                event.getAxisValue(MotionEvent.AXIS_ORIENTATION, 0),
                event.eventTime,
            )
        )
        return out
    }

    /** 记录并预测;返回视口坐标的预测事件(不可用/非 MOVE 返回 null) */
    private fun predictorPredict(event: MotionEvent): MotionEvent? {
        val p = predictor as? androidx.input.motionprediction.MotionEventPredictor ?: return null
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return null
        return runCatching {
            p.record(event)
            p.predict()
        }.getOrNull()
    }

    // ---------------- 绘制 ----------------

    private val strokePaint = Paint()

    private val backdropPaint = Paint()

    private val paperPaint = Paint()
    private val framePaint = Paint().apply { color = paperFrameColor; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val cursorPaint = Paint().apply {
        color = 0x6623221E.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f; isAntiAlias = true
    }

    /** 主题应用到纸面层(亮=奶白纸,暗=深纸 + 浅墨光标 + 亮蓝套索) */
    private fun applyPaperTheme() {
        if (darkTheme) {
            paperColor = 0xFF1B1C20.toInt()
            dotColor = 0xFF2C2D33.toInt()
            backdropColor = 0xFF15161A.toInt()
            paperFrameColor = 0x33E8E6E0
            cursorPaint.color = 0x66E8E6E0.toInt()
            lassoPaint.color = 0xFF7D92FF.toInt()
            selectionBoxPaint.color = 0x807D92FF.toInt()
            selectionHaloPaint.color = 0x247D92FF
        } else {
            paperColor = 0xFFFAF9F5.toInt()
            dotColor = 0xFFD8D4C8.toInt()
            backdropColor = 0xFFEFEDE5.toInt()
            paperFrameColor = 0x1423221E
            cursorPaint.color = 0x6623221E.toInt()
            lassoPaint.color = 0xFF3B5BEC.toInt()
            selectionBoxPaint.color = 0x803B5BEC.toInt()
            selectionHaloPaint.color = 0x243B5BEC
        }
    }

    /** 显示色映射:暗色下墨黑/红/蓝 → 浅色(荧光黄不变);颜色数据不变 */
    private fun displayColor(c: Long): Long = when (c) {
        0xFF23221E -> if (darkTheme) 0xFFE8E6E0 else c
        0xFFD2483C -> if (darkTheme) 0xFFE06558 else c
        0xFF3B5BEC -> if (darkTheme) 0xFF7D92FF else c
        else -> c
    }
    private val lassoPaint = Paint().apply {
        color = 0xFF3B5BEC.toInt(); style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val selectionBoxPaint = Paint().apply {
        color = 0x803B5BEC.toInt(); style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val selectionHaloPaint = Paint().apply {
        color = 0x243B5BEC; style = Paint.Style.FILL; isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val c = controller
        // 注意:Compose interop 传入的 canvas 不保证裁剪到自身 bounds,
        // 禁用 drawColor(整画布),必须按自身尺寸显式画矩形
        backdropPaint.reset()
        backdropPaint.color = backdropColor
        backdropPaint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backdropPaint)

        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(zoom, zoom)
        canvas.clipRect(0f, 0f, pageWidthPx, pageHeightPx)

        paperPaint.reset()
        paperPaint.color = paperColor
        paperPaint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, pageWidthPx, pageHeightPx, paperPaint)
        PaperRenderer.draw(canvas, paperStyle, pageWidthPx, pageHeightPx, dotColor)

        if (c != null) {
            val key = renderKey(c)
            if (key != committedKey) rebuildCommitted(c, key)
        }
        canvas.drawPicture(committed)

        if (c != null) drawSelection(canvas, c)

        // 活动笔迹:Canvas 路径每帧重绘;前置缓冲路径由 GL 层绘制(跳过,避免重影)
        val activeOnFront = frontActive && c?.drawing == true
        if (!activeOnFront) {
            c?.activeSnapshot()?.let { s ->
                val path = StrokeRenderer.buildPath(s, mmToPx, c.tool.pressureGamma)
                canvas.drawPath(path, paintFor(s.color, s.tool))
            }
        }
        canvas.restore()

        // 以下屏幕空间元素裁剪到自身 bounds(interop canvas 不保证裁剪,防越界盖 chrome)
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())

        // 纸面细边(纸感优先:纸不可动)
        canvas.drawRect(panX, panY, panX + pageWidthPx * zoom, panY + pageHeightPx * zoom, framePaint)

        // 悬停光标:小圆环 + 当前笔宽预览(DESIGN-V0 §3.1)
        if (hoverX >= 0 && c != null) {
            val r = c.cursorRadiusPx(mmToPx) * zoom
            canvas.drawCircle(hoverX, hoverY, maxOf(r, 3f), cursorPaint)
        }
        canvas.restore()
    }

    /** 缓存键:页面版本 + 选中集版本 + 压感曲线档(笔宽是样式,换档全页重绘) */
    private fun renderKey(c: InkController): Long =
        c.renderStamp * 4L + c.tool.pressureGamma.ordinal

    /** 选中集:halo + 原墨(带拖动偏移)+ 虚线包围盒;圈选轨迹实时虚线 */
    private fun drawSelection(canvas: Canvas, c: InkController) {
        val sel = c.selectionSnapshot()
        val (dx, dy) = c.selectionDragOffset()
        if (sel != null) {
            for (s in c.selectedStrokes()) {
                val moved = if (dx != 0f || dy != 0f) {
                    s.copy(points = s.points.map { it.copy(x = it.x + dx, y = it.y + dy) })
                } else s
                val path = StrokeRenderer.buildPath(moved, mmToPx, c.tool.pressureGamma)
                canvas.drawPath(path, selectionHaloPaint)
                canvas.drawPath(path, paintFor(moved.color, moved.tool))
            }
            sel.box?.let { b ->
                val pad = 10f
                selectionBoxPaint.strokeWidth = 1.5f / zoom
                selectionBoxPaint.pathEffect = DashPathEffect(floatArrayOf(10f / zoom, 8f / zoom), 0f)
                canvas.drawRect(b.l + dx - pad, b.t + dy - pad, b.r + dx + pad, b.b + dy + pad, selectionBoxPaint)
            }
        }
        val loop = c.lassoLoopSnapshot()
        if (loop.isNotEmpty()) {
            val path = Path()
            loop.forEachIndexed { i, pt ->
                if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            path.close()
            lassoPaint.strokeWidth = 2f / zoom
            lassoPaint.pathEffect = DashPathEffect(floatArrayOf(12f / zoom, 9f / zoom), 0f)
            canvas.drawPath(path, lassoPaint)
        }
    }

    private fun rebuildCommitted(c: InkController, key: Long) {
        val p = Picture()
        val canvas = p.beginRecording(pageWidthPx.toInt(), pageHeightPx.toInt())
        val excluded = c.excludedFromCache()
        for (s in c.page.strokes) {
            if (s.id in excluded) continue
            canvas.drawPath(StrokeRenderer.buildPath(s, mmToPx, c.tool.pressureGamma), paintFor(s.color, s.tool))
        }
        p.endRecording()
        committed = p
        committedKey = key
    }

    private fun paintFor(color: Long, tool: InkTool): Paint = strokePaint.let {
        it.reset()
        it.color = displayColor(color).toInt()
        it.isAntiAlias = true
        it.style = Paint.Style.FILL
        if (tool == InkTool.HIGHLIGHTER) it.alpha = (255 * 0.45f).toInt()
        it
    }

    // ---------------- 导出 ----------------

    /** 页面位图(纸+全部已提交笔迹;目标宽 px,高等比) */
    fun snapshotBitmap(targetWidthPx: Int, withPaper: Boolean = true): Bitmap {
        val w = targetWidthPx
        val h = (pageHeightPx / pageWidthPx * w).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val sx = w / pageWidthPx
        canvas.scale(sx, sx)
        if (withPaper) {
            canvas.drawColor(paperColor)
            PaperRenderer.draw(canvas, paperStyle, pageWidthPx, pageHeightPx, dotColor)
        }
        val c = controller
        if (c != null) {
            for (s in c.page.strokes) {
                canvas.drawPath(StrokeRenderer.buildPath(s, mmToPx, c.tool.pressureGamma), paintFor(s.color, s.tool))
            }
        }
        return bmp
    }

    /** 当前页 stroke 数据(导出/切页时读取) */
    fun currentStrokes(): List<Stroke> =
        controller?.page?.strokes?.toList() ?: emptyList()
}
