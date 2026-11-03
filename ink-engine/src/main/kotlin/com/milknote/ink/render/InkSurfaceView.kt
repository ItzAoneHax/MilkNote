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
import android.graphics.RectF
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
import com.milknote.ink.input.SelectionHandle
import com.milknote.ink.model.ElementGeom
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.PageElement
import com.milknote.ink.model.PressureGamma
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.StrokeGeom
import com.milknote.ink.model.ToolConfig
import com.milknote.ink.model.orderedElements
import kotlin.math.hypot

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
            value?.cardHeightProvider = { card ->
                AnnotationCardRenderer.cardHeight(card, card.id in expandedCards)
            }
            value?.pageBoundsProvider = { StrokeGeom.Box(0f, 0f, pageWidthPx, pageHeightPx) }
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

    /** 视口缩放/平移后同步 Compose 选区菜单位置。 */
    var onViewportChanged: (() -> Unit)? = null

    /** 纸张模板:dot | line | grid | blank */
    var paperStyle: String = "dot"
        set(value) { field = value; committedKey = Long.MIN_VALUE; invalidate() }

    /**
     * 图片元素根目录(笔记本 `<id>.milk` 目录;ImageElement.src 为其相对路径)。
     * 切换笔记本(目录变更)时重建缓存:解码缓存全量失效 + 已提交层重绘。
     */
    var imageDir: java.io.File? = null
        set(value) {
            if (field == value) return
            field = value
            ImageElementRenderer.clearCache()
            committedKey = Long.MIN_VALUE
            invalidate()
        }

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

    /**
     * 蓝牙笔已连接(GATT 电量有来源)——即便笔尚未碰过屏,也视为「有笔」:
     * 手指只导航不书写(已连笔场景下防手掌/手指误写)。
     */
    var penConnected: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) stylusSeen = true
        }

    // 本笔是否倒置橡皮
    private var invertedEraser = false

    // 套索/拖动输入状态(数据在 controller,这里只记路由)
    private var lassoStrokeActive = false
    private var dragActive = false
    private var dragAnchorX = 0f
    private var dragAnchorY = 0f
    private var transformActive: SelectionHandle? = null
    private var transformStartDistance = 1f
    private var transformStartAngle = 0f

    // 批注卡交互:拖动 + 点按展开/收起(展开态是显示层状态,不落库)
    private var cardDragActive = false
    private var cardAnchorX = 0f
    private var cardAnchorY = 0f
    private var cardDownId: String? = null      // 按下时的卡(UP 未移动 → 切换展开)
    private var cardDownAt = 0L
    private val expandedCards = HashSet<String>()

    // 图片交互:命中即拖动(拖动预览由 controller 偏移承载,抬起落 Batch op)
    private var imageDragActive = false
    private var imageAnchorX = 0f
    private var imageAnchorY = 0f

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
            onViewportChanged?.invoke()
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
            if (tool == InkTool.HIGHLIGHTER) p.alpha = (p.alpha * 0.45f).toInt()
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
        onViewportChanged?.invoke()
        invalidate()
    }

    /** 用户手动缩放/平移过后,不再自动 fit */
    private var userAdjusted = false

    private fun pageX(viewX: Float) = (viewX - panX) / zoom
    private fun pageY(viewY: Float) = (viewY - panY) / zoom

    // ---- 视口变换公开(④框选遮罩 overlay 与本视图同 bounds,坐标互通) ----

    /** 视口坐标 → 页坐标 */
    fun viewToPage(viewX: Float, viewY: Float): Pair<Float, Float> = pageX(viewX) to pageY(viewY)

    /** 页坐标 → 视口坐标 */
    fun pageToView(px: Float, py: Float): Pair<Float, Float> = (px * zoom + panX) to (py * zoom + panY)

    /** 页面矩形在视口中的投影(视口坐标;可能越出本视图 bounds) */
    fun pageBoundsView(): RectF = RectF(panX, panY, panX + pageWidthPx * zoom, panY + pageHeightPx * zoom)

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
                // 笔靠近数字化器即视为「有笔」(悬停也是 STYLUS 事件流)
                if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                    event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
                ) stylusSeen = true
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
                // 手指:控制柄/已选区/图片与文字点按优先;其他区域在连接笔后仅导航。
                if (routeSpecialDown(event, c, collectRaw(event))) {
                    return true
                } else if (stylusSeen || c == null) {
                    lastPanX = event.x; lastPanY = event.y
                    scaleDetector.onTouchEvent(event)
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
                if (cardDragActive && event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    cardDragActive = false
                    cardDownId = null
                    c?.cancelCardDrag()
                }
                if (imageDragActive && event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    imageDragActive = false
                    c?.cancelImageDrag()
                }
                if (dragActive && event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    dragActive = false
                    c?.cancelSelectionDrag()
                }
                if (transformActive != null && event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    transformActive = null
                    c?.cancelSelectionTransform()
                }
                if (!isWriting() && !lassoStrokeActive && !dragActive && !cardDragActive && !imageDragActive && transformActive == null) scaleDetector.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                when {
                    transformActive != null && c != null -> {
                        val t = c.selectionTransformSnapshot()
                        if (t != null) {
                            val px = pageX(event.x); val py = pageY(event.y)
                            if (transformActive == SelectionHandle.ROTATE) {
                                val angle = ElementGeom.angleDeg(t.pivotX, t.pivotY, px, py)
                                c.moveSelectionTransform(1f, angle - transformStartAngle)
                            } else {
                                val distance = hypot(px - t.pivotX, py - t.pivotY)
                                c.moveSelectionTransform(
                                    distance / transformStartDistance.coerceAtLeast(1f),
                                    0f,
                                    10f * resources.displayMetrics.density / zoom,
                                )
                            }
                        }
                        return true
                    }
                    cardDragActive && c != null -> {
                        c.moveCardDrag(pageX(event.x) - cardAnchorX, pageY(event.y) - cardAnchorY)
                        return true
                    }
                    imageDragActive && c != null -> {
                        c.moveImageDrag(pageX(event.x) - imageAnchorX, pageY(event.y) - imageAnchorY)
                        return true
                    }
                    lassoStrokeActive && c != null -> {
                        val batch = collectRaw(event).map(::toPage)
                        batch.forEach { c.extendLasso(it) }
                        return true
                    }
                    dragActive && c != null -> {
                        c.moveSelectionDrag(
                            pageX(event.x) - dragAnchorX,
                            pageY(event.y) - dragAnchorY,
                            10f * resources.displayMetrics.density / zoom,
                        )
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
                            onViewportChanged?.invoke()
                            invalidate()
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                when {
                    transformActive != null -> {
                        transformActive = null
                        c?.endSelectionTransform()
                    }
                    cardDragActive -> {
                        cardDragActive = false
                        // 未移动的短按 = 展开/收起卡
                        val downId = cardDownId
                        if (downId != null && event.eventTime - cardDownAt < 400 &&
                            c?.cardDragOffset()?.second == (0f to 0f)
                        ) {
                            if (!expandedCards.add(downId)) expandedCards.remove(downId)
                            c?.cancelCardDrag()
                            invalidate()
                        } else {
                            c?.endCardDrag()
                        }
                        cardDownId = null
                    }
                    imageDragActive -> {
                        imageDragActive = false
                        c?.endImageDrag()   // 未位移(<1px)在 controller 内视为无记录
                    }
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
                if (transformActive != null) {
                    transformActive = null
                    c?.cancelSelectionTransform()
                }
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
                if (cardDragActive) {
                    cardDragActive = false
                    cardDownId = null
                    c?.cancelCardDrag()
                }
                if (imageDragActive) {
                    imageDragActive = false
                    c?.cancelImageDrag()
                }
                fingerDrawing = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 控制柄 → 套索 → 已选区 → 手指单选。普通书写时笔在图片/文字上直接写字，
     * 不再进入旧的独立拖动；手指点按图片/文字则选中并开启变换套件。
     */
    private fun routeSpecialDown(event: MotionEvent, c: InkController?, raw: List<InkPoint>): Boolean {
        if (c == null) return false
        val px = pageX(event.x); val py = pageY(event.y)
        val handle = c.hitSelectionHandle(px, py, 24f * resources.displayMetrics.density / zoom, 48f * resources.displayMetrics.density / zoom)
        if (handle != null && c.beginSelectionTransform(handle)) {
            transformActive = handle
            val t = c.selectionTransformSnapshot()!!
            transformStartDistance = hypot(px - t.pivotX, py - t.pivotY).coerceAtLeast(1f)
            transformStartAngle = ElementGeom.angleDeg(t.pivotX, t.pivotY, px, py)
            return true
        }
        val hold = holdActionProvider?.invoke()
        if (c.lassoArmed || hold == InkAction.LASO) {
            c.beginLasso(toPage(raw.first()))
            lassoStrokeActive = true
            return true
        }
        val tool = event.getToolType(0)
        val stylus = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
        if (stylus) {
            val onCard = c.page.cards.any { AnnotationCardRenderer.hit(it, px, py, it.id in expandedCards) }
            val onImage = c.page.images.any { ImageElementRenderer.hit(it, px, py) }
            if (onCard || onImage) {
                c.clearSelection()
                return false
            }
        }
        if (c.hitSelection(px, py)) {
            c.beginSelectionDrag(px, py)
            dragActive = true
            dragAnchorX = px; dragAnchorY = py
            return true
        }
        if (!stylus && c.selectElementAt(px, py) != null) {
            c.beginSelectionDrag(px, py)
            dragActive = true
            dragAnchorX = px; dragAnchorY = py
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
            selectionBoxPaint.color = 0xA67D92FF.toInt()
            selectionHaloPaint.color = 0x247D92FF
            selectionGuidePaint.color = 0xC27D92FF.toInt()
            selectionUnderlayPaint.color = 0x8A1B1C20.toInt()
            handleFillPaint.color = 0xFF26272D.toInt()
            handleStrokePaint.color = 0xFF7D92FF.toInt()
            handleHaloPaint.color = 0x187D92FF
            handleDetailPaint.color = 0xB37D92FF.toInt()
        } else {
            paperColor = 0xFFFAF9F5.toInt()
            dotColor = 0xFFD8D4C8.toInt()
            backdropColor = 0xFFEFEDE5.toInt()
            paperFrameColor = 0x1423221E
            cursorPaint.color = 0x6623221E.toInt()
            lassoPaint.color = 0xFF3B5BEC.toInt()
            selectionBoxPaint.color = 0xA63B5BEC.toInt()
            selectionHaloPaint.color = 0x243B5BEC
            selectionGuidePaint.color = 0xB83B5BEC.toInt()
            selectionUnderlayPaint.color = 0xB8FAF9F5.toInt()
            handleFillPaint.color = 0xFFFFFCF7.toInt()
            handleStrokePaint.color = 0xFF3B5BEC.toInt()
            handleHaloPaint.color = 0x103B5BEC
            handleDetailPaint.color = 0x993B5BEC.toInt().toInt()
        }
    }

    /** 显示色映射:暗色下经典墨色用指定浅色,自定义深色按亮度抬升;原始 ARGB 不变。 */
    private fun displayColor(c: Long): Long {
        if (!darkTheme) return c
        when (c) {
            0xFF23221E -> return 0xFFE8E6E0
            0xFFD2483C -> return 0xFFE06558
            0xFF3B5BEC -> return 0xFF7D92FF
        }
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
        if (luminance >= 0.34f) return c
        val lift = ((0.34f - luminance) / 0.34f).coerceIn(0f, 1f) * 0.72f
        fun channel(v: Float): Long = ((v + (1f - v) * lift) * 255f).toInt().coerceIn(0, 255).toLong()
        return (c and 0xFF000000) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
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
    private val selectionUnderlayPaint = Paint().apply {
        color = 0xB8FAF9F5.toInt(); style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val handleFillPaint = Paint().apply {
        color = 0xFFFFFCF7.toInt(); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val handleStrokePaint = Paint().apply {
        color = 0xFF3B5BEC.toInt(); style = Paint.Style.STROKE; isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    private val handleShadowPaint = Paint().apply {
        color = 0x1423221E; style = Paint.Style.FILL; isAntiAlias = true
    }
    private val handleHaloPaint = Paint().apply {
        color = 0x103B5BEC; style = Paint.Style.FILL; isAntiAlias = true
    }
    private val handleDetailPaint = Paint().apply {
        color = 0x993B5BEC.toInt(); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val selectionGuidePaint = Paint().apply {
        color = 0xB83B5BEC.toInt(); style = Paint.Style.STROKE; isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val c = controller
        // 注意:Compose interop 传入的 canvas 不保证裁剪到自身 bounds,
        // 禁用 drawColor(整画布),必须按自身尺寸显式画矩形;且整体再裁剪一次
        // 到自身 bounds,杜绝任何绘制越界(顶栏/状态条)。
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
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

        // 图片拖动预览:拖动中的图片不在 committed 缓存里(rebuildCommitted 已摘除),
        // 单独带偏移重画,跟随手指(页坐标偏移,随视口缩放生效)
        if (c != null) {
            val (imgDragId, imgDragOff) = c.imageDragOffset() ?: (null to (0f to 0f))
            if (imgDragId != null) {
                val img = c.page.images.firstOrNull { it.id == imgDragId }
                if (img != null) {
                    ImageElementRenderer.draw(canvas, img, imageDir, imgDragOff.first, imgDragOff.second)
                }
            }
        }

        // 旧独立卡片拖动仍由预览承载；普通卡片与图片/笔迹一起在 committed 中按全局层序绘制。
        if (c != null) {
            val (dragId, dragOff) = c.cardDragOffset() ?: (null to (0f to 0f))
            val card = c.page.cards.firstOrNull { it.id == dragId }
            if (card != null) {
                AnnotationCardRenderer.draw(canvas, card, darkTheme, card.id in expandedCards, dragOff.first, dragOff.second)
            }
        }

        if (c != null) {
            drawSelection(canvas, c)
            drawSelectionGuides(canvas, c)
        }

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
        canvas.restore() // 整体 bounds 裁剪(见 onDraw 开头)
    }

    /** 缓存键:页面版本 + 选中集版本 + 压感曲线档(笔宽是样式,换档全页重绘) */
    private fun renderKey(c: InkController): Long =
        c.renderStamp * 4L + c.tool.pressureGamma.ordinal

    /** 选中元素统一应用组变换；控制柄在相同坐标系绘制，半径按视口和缩放补偿。 */
    private fun drawSelection(canvas: Canvas, c: InkController) {
        val t = c.selectionTransformSnapshot()
        if (t != null) {
            canvas.save()
            canvas.translate(t.dx, t.dy)
            canvas.rotate(t.rotationDeg, t.pivotX, t.pivotY)
            canvas.scale(t.scale, t.scale, t.pivotX, t.pivotY)
            for (element in c.page.orderedElements()) {
                if (!c.isSelected(element.ref)) continue
                drawElement(canvas, element, c, halo = true)
            }
            val b = t.box
            val pad = 10f
            val visualScale = zoom * t.scale
            val density = resources.displayMetrics.density
            val left = b.l - pad; val top = b.t - pad
            val right = b.r + pad; val bottom = b.b + pad
            selectionUnderlayPaint.strokeWidth = 3f * density / visualScale
            canvas.drawRect(left, top, right, bottom, selectionUnderlayPaint)
            selectionBoxPaint.strokeWidth = 1.1f * density / visualScale
            selectionBoxPaint.pathEffect = DashPathEffect(floatArrayOf(8f / visualScale, 6f / visualScale), 0f)
            canvas.drawRect(left, top, right, bottom, selectionBoxPaint)

            val rotateX = (left + right) / 2f
            val rotateY = top - 48f * density / visualScale
            selectionBoxPaint.pathEffect = null
            selectionUnderlayPaint.strokeWidth = 3f * density / visualScale
            canvas.drawLine(rotateX, top, rotateX, rotateY, selectionUnderlayPaint)
            selectionBoxPaint.strokeWidth = 1.1f * density / visualScale
            canvas.drawLine(rotateX, top, rotateX, rotateY, selectionBoxPaint)

            handleStrokePaint.strokeWidth = 1.5f * density / visualScale
            val cornerRadius = 6.5f * density / visualScale
            listOf(left to top, right to top, right to bottom, left to bottom).forEach { (x, y) ->
                drawTransformHandle(canvas, x, y, cornerRadius, visualScale)
            }
            drawTransformHandle(
                canvas, rotateX, rotateY, 9f * density / visualScale, visualScale, rotation = true,
            )
            canvas.restore()
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

    private fun drawSelectionGuides(canvas: Canvas, c: InkController) {
        val guides = c.selectionGuidesSnapshot()
        if (guides.isEmpty()) return
        val visualScale = zoom
        val density = resources.displayMetrics.density
        selectionGuidePaint.strokeWidth = 1.15f * density / visualScale
        selectionGuidePaint.pathEffect = DashPathEffect(
            floatArrayOf(7f * density / visualScale, 5f * density / visualScale), 0f,
        )
        guides.forEach { guide ->
            if (guide.vertical) {
                canvas.drawLine(guide.position, guide.start, guide.position, guide.end, selectionGuidePaint)
            } else {
                canvas.drawLine(guide.start, guide.position, guide.end, guide.position, selectionGuidePaint)
            }
        }
        selectionGuidePaint.pathEffect = null
    }

    private fun drawTransformHandle(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        visualScale: Float,
        rotation: Boolean = false,
    ) {
        val density = resources.displayMetrics.density
        canvas.drawCircle(x, y, radius + 3f * density / visualScale, handleHaloPaint)
        canvas.drawCircle(x, y + 1.2f * density / visualScale, radius + 0.8f * density / visualScale, handleShadowPaint)
        canvas.drawCircle(x, y, radius, handleFillPaint)
        canvas.drawCircle(x, y, radius, handleStrokePaint)
        if (rotation) {
            drawRotationGlyph(canvas, x, y, radius * 0.48f, visualScale)
        } else {
            canvas.drawCircle(x, y, 1.1f * density / visualScale, handleDetailPaint)
        }
    }

    /** 旋转钮内部的环形箭头；与选框共用当前页坐标和视觉缩放补偿。 */
    private fun drawRotationGlyph(canvas: Canvas, x: Float, y: Float, radius: Float, visualScale: Float) {
        val density = resources.displayMetrics.density
        handleStrokePaint.strokeWidth = 1.35f * density / visualScale
        canvas.drawArc(RectF(x - radius, y - radius, x + radius, y + radius), -60f, 285f, false, handleStrokePaint)
        val end = Math.toRadians(225.0)
        val ex = x + radius * kotlin.math.cos(end).toFloat()
        val ey = y + radius * kotlin.math.sin(end).toFloat()
        val wing = 2.6f * density / visualScale
        for (angle in listOf(20.0, 170.0)) {
            val rad = Math.toRadians(angle)
            canvas.drawLine(
                ex, ey,
                ex + wing * kotlin.math.cos(rad).toFloat(),
                ey + wing * kotlin.math.sin(rad).toFloat(),
                handleStrokePaint,
            )
        }
    }

    private fun drawElement(canvas: Canvas, element: PageElement, c: InkController, halo: Boolean = false) {
        when (element) {
            is PageElement.Image -> ImageElementRenderer.draw(canvas, element.value, imageDir)
            is PageElement.Ink -> {
                val path = StrokeRenderer.buildPath(element.value, mmToPx, c.tool.pressureGamma)
                if (halo) canvas.drawPath(path, selectionHaloPaint)
                canvas.drawPath(path, paintFor(element.value.color, element.value.tool))
            }
            is PageElement.Card -> AnnotationCardRenderer.draw(
                canvas, element.value, darkTheme, element.value.id in expandedCards,
            )
        }
    }

    private fun rebuildCommitted(c: InkController, key: Long) {
        val p = Picture()
        val canvas = p.beginRecording(pageWidthPx.toInt(), pageHeightPx.toInt())
        val imgDragId = c.draggingImageId()
        val cardDragId = c.cardDragOffset()?.first
        for (element in c.page.orderedElements()) {
            if (c.isSelected(element.ref)) continue
            if (element is PageElement.Image && element.value.id == imgDragId) continue
            if (element is PageElement.Card && element.value.id == cardDragId) continue
            drawElement(canvas, element, c)
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

    /** 页面位图(纸+图片+全部已提交笔迹;目标宽 px,高等比) */
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
            for (element in c.page.orderedElements()) {
                when (element) {
                    is PageElement.Image -> ImageElementRenderer.draw(canvas, element.value, imageDir)
                    is PageElement.Ink -> canvas.drawPath(
                        StrokeRenderer.buildPath(element.value, mmToPx, c.tool.pressureGamma),
                        paintFor(element.value.color, element.value.tool),
                    )
                    is PageElement.Card -> AnnotationCardRenderer.draw(canvas, element.value, darkTheme, expanded = true)
                }
            }
        }
        return bmp
    }

    /** 当前页 stroke 数据(导出/切页时读取) */
    fun currentStrokes(): List<Stroke> =
        controller?.page?.strokes?.toList() ?: emptyList()
}
