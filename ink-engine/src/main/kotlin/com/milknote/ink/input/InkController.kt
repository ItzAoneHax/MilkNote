package com.milknote.ink.input

import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.InkOp
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.LassoGeom
import com.milknote.ink.model.PageInkState
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.StrokeGeom
import java.util.UUID

/** 落盘回调:笔迹新增 / 笔迹删除(tombstone)/ 批注卡增删,由 note-domain 转成 StrokeRecord 追加 */
sealed interface InkCommit {
    data class Added(val stroke: Stroke) : InkCommit
    data class Tombstoned(val strokeId: String) : InkCommit
    data class CardAdded(val card: AnnotationCard) : InkCommit
    data class CardRemoved(val cardId: String) : InkCommit
}

/** 选中集快照(UI 显示用;box 为所选笔迹并集包围盒,页坐标) */
data class SelectionSnapshot(val count: Int, val box: StrokeGeom.Box?)

/**
 * 书写采集状态机:事件点 → 活动笔迹 → 提交/擦除 → 撤销重做 + 套索选择。
 * 与渲染解耦:视图层订阅失效通知(addInvalidator)自行重绘。
 */
class InkController {

    val page = PageInkState()

    var tool: com.milknote.ink.model.ToolConfig = com.milknote.ink.model.ToolConfig()
        set(value) { field = value; notifyInvalidate() }

    /** 活动笔迹(书写中);点列表可变以避免每点拷贝 */
    private var activePoints: MutableList<InkPoint>? = null
    /** 本笔工具覆盖(倒置笔 TOOL_TYPE_ERASER → 橡皮;副键按住书写 = 临时橡皮) */
    private var activeToolOverride: com.milknote.ink.model.ToolConfig? = null
    private val activeTool: com.milknote.ink.model.ToolConfig
        get() = activeToolOverride ?: tool

    /** 轨迹预测尾巴(仅显示,不属于数据) */
    @Volatile var predictedTail: List<InkPoint> = emptyList()

    /** 失效监听(视图重绘 / VM 状态同步) */
    private val invalidators = mutableListOf<() -> Unit>()
    fun addInvalidator(l: () -> Unit) { invalidators.add(l) }
    fun removeInvalidator(l: () -> Unit) { invalidators.remove(l) }
    private fun notifyInvalidate() = invalidators.forEach { it() }

    /** 落盘通知(每次状态变更后按序回调;必须在主线程外自行异步化) */
    var onCommit: ((InkCommit) -> Unit)? = null

    /** 手势/按键动作回调(device-mi PenActionRouter 路由到这里) */
    var onAction: ((InkAction) -> Unit)? = null

    val drawing: Boolean get() = activePoints != null

    // ---- 套索选择(PEN-ADAPTATION §4.4:主键单击默认动作;圈选→拖动/删除,进撤销栈)----

    /** 已进入套索模式(等待下一笔圈选);由 UI(笔键触发/工具栏)置位,一圈后自动退出 */
    @Volatile var lassoArmed: Boolean = false
        private set

    /** 圈选中的闭合路径(页坐标;书写中非空) */
    private var lassoLoop: MutableList<InkPoint>? = null

    private var selectionIds: LinkedHashSet<String>? = null
    private var selectionBox: StrokeGeom.Box? = null
    private var selectionStamp = 0L   // 选中集版本(渲染缓存失效用)
        private set

    // 拖动预览偏移(页坐标;抬起才落 Batch op)
    private var dragDx = 0f
    private var dragDy = 0f
    private var dragStarted = false

    fun armLasso() { clearSelection(); lassoArmed = true; notifyInvalidate() }
    fun disarmLasso() { lassoArmed = false; notifyInvalidate() }

    /** 圈选开始(视图在套索模式/按住修饰下落笔时调用) */
    fun beginLasso(first: InkPoint) {
        clearSelection()
        lassoLoop = mutableListOf(first)
        notifyInvalidate()
    }

    fun extendLasso(point: InkPoint) {
        lassoLoop?.add(point)
        notifyInvalidate()
    }

    /** 圈选结束:全部点落在路径内的笔迹被选中;返回是否选中了笔迹 */
    fun endLasso(): Boolean {
        val loop = lassoLoop
        lassoLoop = null
        lassoArmed = false
        val ids = LinkedHashSet<String>()
        var box: StrokeGeom.Box? = null
        if (loop != null) {
            for (s in page.strokes) {
                if (LassoGeom.captures(loop, s)) {
                    ids.add(s.id)
                    val b = StrokeGeom.bounds(s)
                    box = box?.let { StrokeGeom.Box(minOf(it.l, b.l), minOf(it.t, b.t), maxOf(it.r, b.r), maxOf(it.b, b.b)) } ?: b
                }
            }
        }
        selectionIds = ids.ifEmpty { null }
        selectionBox = if (selectionIds != null) box else null
        selectionStamp++
        notifyInvalidate()
        return selectionIds != null
    }

    fun lassoLoopSnapshot(): List<InkPoint> = lassoLoop?.toList() ?: emptyList()

    /** 放弃进行中的圈选(第二指落下/事件取消);武装状态保持,可重新圈 */
    fun cancelLasso() {
        lassoLoop = null
        notifyInvalidate()
    }

    fun selectionSnapshot(): SelectionSnapshot? =
        selectionIds?.let { SelectionSnapshot(it.size, selectionBox) }

    /** 渲染用:选中笔迹(拖动预览偏移由视图另行应用) */
    fun selectedStrokes(): List<Stroke> {
        val ids = selectionIds ?: return emptyList()
        return page.strokes.filter { ids.contains(it.id) }
    }

    /** 渲染缓存排除集(选中笔迹单独画,带偏移与高亮) */
    fun excludedFromCache(): Set<String> = selectionIds ?: emptySet()

    fun selectionDragOffset(): Pair<Float, Float> = dragDx to dragDy

    /** 命中选中包围盒(页坐标,pad 为命中容差) */
    fun hitSelection(x: Float, y: Float, pad: Float = 12f): Boolean {
        val ids = selectionIds ?: return false
        if (ids.isEmpty()) return false
        return selectionBox?.expand(pad)?.contains(x, y) == true
    }

    fun beginSelectionDrag(x: Float, y: Float) {
        dragDx = 0f; dragDy = 0f
        dragStarted = selectionIds != null
    }

    fun moveSelectionDrag(dx: Float, dy: Float) {
        if (!dragStarted) return
        dragDx = dx; dragDy = dy
        notifyInvalidate()
    }

    /** 拖动结束:位移 >1px 落 Batch(Remove 旧 + Add 平移新),进撤销栈;返回是否落盘 */
    fun endSelectionDrag(): Boolean {
        if (!dragStarted) return false
        dragStarted = false
        val moved = kotlin.math.abs(dragDx) > 1f || kotlin.math.abs(dragDy) > 1f
        val ids = selectionIds
        val dx = dragDx; val dy = dragDy
        dragDx = 0f; dragDy = 0f
        if (!moved || ids == null) { notifyInvalidate(); return false }
        val removes = mutableListOf<InkOp>()
        val adds = mutableListOf<InkOp>()
        val it = page.strokes.listIterator()
        while (it.hasNext()) {
            val s = it.next()
            if (ids.contains(s.id)) {
                removes.add(InkOp.Remove(s, it.previousIndex()))
                adds.add(
                    InkOp.Add(
                        s.copy(
                            id = UUID.randomUUID().toString(),
                            points = s.points.map { p -> p.copy(x = p.x + dx, y = p.y + dy) },
                        )
                    )
                )
            }
        }
        if (removes.isEmpty()) { notifyInvalidate(); return false }
        val batch = InkOp.Batch(removes.reversed() + adds) // 先移除(从后往前保下标)再追加
        page.apply(batch)
        selectionIds = adds.filterIsInstance<InkOp.Add>().map { it.stroke.id }.toCollection(LinkedHashSet())
        selectionBox = selectionBox?.let { StrokeGeom.Box(it.l + dx, it.t + dy, it.r + dx, it.b + dy) }
        selectionStamp++
        emitForward(batch)
        notifyInvalidate()
        return true
    }

    fun cancelSelectionDrag() {
        dragStarted = false
        dragDx = 0f; dragDy = 0f
        notifyInvalidate()
    }

    /** 删除选中笔迹(Batch Remove,可撤销) */
    fun deleteSelection(): Boolean {
        val ids = selectionIds ?: return false
        val ops = mutableListOf<InkOp>()
        val it = page.strokes.listIterator()
        while (it.hasNext()) {
            val s = it.next()
            if (ids.contains(s.id)) ops.add(InkOp.Remove(s, it.previousIndex()))
        }
        if (ops.isEmpty()) { clearSelection(); return false }
        val batch = InkOp.Batch(ops.reversed())
        clearSelection()
        page.apply(batch)
        emitForward(batch)
        notifyInvalidate()
        return true
    }

    fun clearSelection() {
        if (selectionIds == null && selectionBox == null && !dragStarted) return
        selectionIds = null
        selectionBox = null
        dragStarted = false
        dragDx = 0f; dragDy = 0f
        selectionStamp++
        notifyInvalidate()
    }

    /** 渲染缓存失效键(页面版本 + 选中集版本) */
    val renderStamp: Long get() = page.version * 1_000_003L + selectionStamp

    /** 改绑 TEMP_ERASER 到单击类触发时:下一笔临时为橡皮(取用即复位) */
    @Volatile var oneShotEraser: Boolean = false
        private set

    fun armOneShotEraser() { oneShotEraser = true; notifyInvalidate() }

    fun consumeOneShotEraser(): Boolean {
        val v = oneShotEraser
        oneShotEraser = false
        return v
    }


    /** 活动笔迹快照(含预测尾巴,仅显示用) */
    fun activeSnapshot(): Stroke? {
        val pts = activePoints ?: return null
        if (pts.isEmpty()) return null
        val display = pts + predictedTail
        val t = activeTool
        return Stroke(
            id = "active",
            tool = t.tool,
            color = t.color,
            baseWidthMm = t.baseWidthMm,
            points = display,
        )
    }

    /** 悬停光标预览宽度(px) */
    fun cursorRadiusPx(mmToPx: Float): Float =
        StrokeRenderer_cursorRadius(tool.tool, tool.baseWidthMm, mmToPx)

    private fun StrokeRenderer_cursorRadius(tool: InkTool, base: Float, mmToPx: Float): Float =
        (if (tool == InkTool.HIGHLIGHTER) base * 4f else base) * mmToPx / 2f

    fun beginStroke(first: InkPoint, toolOverride: com.milknote.ink.model.ToolConfig? = null) {
        if (activePoints != null) endStroke()
        activeToolOverride = toolOverride
        activePoints = mutableListOf(first)
        predictedTail = emptyList()
    }

    fun extendStroke(points: List<InkPoint>) {
        val pts = activePoints ?: return
        pts.addAll(points)
        notifyInvalidate()
    }

    fun predict(points: List<InkPoint>) {
        if (activePoints == null) { predictedTail = emptyList(); return }
        predictedTail = points
        notifyInvalidate()
    }

    fun endStroke(): Stroke? {
        val pts = activePoints ?: return null
        val t = activeTool
        activePoints = null
        activeToolOverride = null
        predictedTail = emptyList()
        val stroke = Stroke(
            id = UUID.randomUUID().toString(),
            tool = t.tool,
            color = t.color,
            baseWidthMm = t.baseWidthMm,
            points = pts.toList(),
        )
        when (t.tool) {
            InkTool.ERASER -> {
                page.eraseStrokes(stroke)?.let { op ->
                    page.apply(op)
                    op.ops.filterIsInstance<InkOp.Remove>()
                        .forEach { onCommit?.invoke(InkCommit.Tombstoned(it.stroke.id)) }
                    notifyInvalidate()
                }
            }
            else -> {
                page.apply(InkOp.Add(stroke))
                onCommit?.invoke(InkCommit.Added(stroke))
                notifyInvalidate()
            }
        }
        return stroke
    }

    fun cancelStroke() {
        activePoints = null
        predictedTail = emptyList()
        notifyInvalidate()
    }

    /** 撤销;落盘镜像 = 反向记录(Add→Tombstone,Remove→重新 Add) */
    fun undo() {
        clearSelection()
        val op = page.undo() ?: return
        emitInverse(op)
        notifyInvalidate()
    }

    /** 重做;落盘镜像 = 正向记录 */
    fun redo() {
        clearSelection()
        val op = page.redo() ?: return
        emitForward(op)
        notifyInvalidate()
    }

    // ---- 批注卡(Agent 结果贴回;§9):渲染在墨迹之上,同一撤销栈 ----

    private var cardDragId: String? = null
    private var cardDragDx = 0f
    private var cardDragDy = 0f
    private var cardDragStarted = false

    fun annotationCards(): List<AnnotationCard> = page.cards.toList()

    fun cardDragOffset(): Pair<String, Pair<Float, Float>>? =
        cardDragId?.let { it to (cardDragDx to cardDragDy) }

    fun beginCardDrag(cardId: String) {
        cardDragId = cardId
        cardDragDx = 0f; cardDragDy = 0f
        cardDragStarted = page.cards.any { it.id == cardId }
    }

    fun moveCardDrag(dx: Float, dy: Float) {
        if (!cardDragStarted) return
        cardDragDx = dx; cardDragDy = dy
        notifyInvalidate()
    }

    /** 拖动结束:位移 >1px 落 Batch(RemoveCard + AddCard 平移),可撤销;返回是否落盘 */
    fun endCardDrag(): Boolean {
        if (!cardDragStarted) return false
        cardDragStarted = false
        val id = cardDragId
        val dx = cardDragDx; val dy = cardDragDy
        cardDragId = null; cardDragDx = 0f; cardDragDy = 0f
        val card = page.cards.firstOrNull { it.id == id }
        if (card == null || (dx * dx + dy * dy) < 1f) { notifyInvalidate(); return false }
        val moved = card.copy(x = card.x + dx, y = card.y + dy)
        val batch = InkOp.Batch(listOf(InkOp.RemoveCard(card), InkOp.AddCard(moved)))
        page.apply(batch)
        emitForward(batch)
        notifyInvalidate()
        return true
    }

    fun cancelCardDrag() {
        cardDragStarted = false
        cardDragId = null
        cardDragDx = 0f; cardDragDy = 0f
        notifyInvalidate()
    }

    fun addAnnotationCard(card: AnnotationCard) {
        val op = InkOp.AddCard(card)
        page.apply(op)
        emitForward(op)
        notifyInvalidate()
    }

    fun deleteAnnotationCard(cardId: String): Boolean {
        val card = page.cards.firstOrNull { it.id == cardId } ?: return false
        val op = InkOp.RemoveCard(card)
        page.apply(op)
        emitForward(op)
        notifyInvalidate()
        return true
    }

    private fun emitInverse(op: InkOp) = when (op) {
        is InkOp.Add -> onCommit?.invoke(InkCommit.Tombstoned(op.stroke.id))
        is InkOp.Remove -> onCommit?.invoke(InkCommit.Added(op.stroke))
        is InkOp.AddCard -> onCommit?.invoke(InkCommit.CardRemoved(op.card.id))
        is InkOp.RemoveCard -> onCommit?.invoke(InkCommit.CardAdded(op.card))
        is InkOp.Batch -> op.ops.forEach(::emitInverse)
    }

    private fun emitForward(op: InkOp) = when (op) {
        is InkOp.Add -> onCommit?.invoke(InkCommit.Added(op.stroke))
        is InkOp.Remove -> onCommit?.invoke(InkCommit.Tombstoned(op.stroke.id))
        is InkOp.AddCard -> onCommit?.invoke(InkCommit.CardAdded(op.card))
        is InkOp.RemoveCard -> onCommit?.invoke(InkCommit.CardRemoved(op.card.id))
        is InkOp.Batch -> op.ops.forEach(::emitForward)
    }

    fun clear() {
        page.strokes.clear()
        page.cards.clear()
        clearSelection()
        notifyInvalidate()
    }

    /** 整页加载(切页;批注卡一并) */
    fun loadStrokes(strokes: List<Stroke>, cards: List<AnnotationCard> = emptyList()) {
        clearSelection()
        page.reset(strokes, cards)
        notifyInvalidate()
    }
}

/** 界面可响应的笔动作(由 PenActionRouter 映射产生) */
enum class InkAction {
    TOGGLE_ERASER, LASO, SNIP, UNDO, REDO, NEW_PAGE, TEMP_ERASER;
}
