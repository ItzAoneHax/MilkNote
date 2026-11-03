package com.milknote.ink.input

import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.ElementGeom
import com.milknote.ink.model.ElementRef
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkOp
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.LassoGeom
import com.milknote.ink.model.LayerState
import com.milknote.ink.model.PageElement
import com.milknote.ink.model.PageInkState
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.StrokeGeom
import com.milknote.ink.model.nextZIndex
import com.milknote.ink.model.orderedElements
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import java.util.UUID

/** 落盘回调:笔迹新增 / 笔迹删除(tombstone)/ 批注卡、图片增删,由 note-domain 转成追加/整表重写 */
sealed interface InkCommit {
    data class Added(val stroke: Stroke) : InkCommit
    data class Tombstoned(val strokeId: String) : InkCommit
    data class CardAdded(val card: AnnotationCard) : InkCommit
    data class CardRemoved(val cardId: String) : InkCommit
    data class ImageAdded(val image: ImageElement) : InkCommit
    data class ImageRemoved(val imageId: String) : InkCommit
    data object LayersChanged : InkCommit
}

/** 选中集快照(UI 显示用;box 为当前预览的轴对齐包围盒,页坐标) */
data class SelectionSnapshot(
    val count: Int,
    val box: StrokeGeom.Box?,
    val canMoveUp: Boolean = false,
    val canMoveDown: Boolean = false,
    val snapEnabled: Boolean = true,
)

enum class SelectionHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, ROTATE }

data class SelectionTransformSnapshot(
    val box: StrokeGeom.Box,
    val dx: Float,
    val dy: Float,
    val scale: Float,
    val rotationDeg: Float,
    val pivotX: Float,
    val pivotY: Float,
)

/** 吸附参考线（页坐标）；vertical=true 表示 x=position 的竖线。 */
data class SelectionGuide(val vertical: Boolean, val position: Float, val start: Float, val end: Float)

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

    // ---- 混合套索选择 + 变换(笔迹/图片/卡片同一选择集、同一撤销栈)----

    /** 已进入套索模式(等待下一笔圈选);由 UI(笔键触发/工具栏)置位,一圈后自动退出 */
    @Volatile var lassoArmed: Boolean = false
        private set

    private var lassoLoop: MutableList<InkPoint>? = null
    private var selectionRefs: LinkedHashSet<ElementRef>? = null
    private var selectionBox: StrokeGeom.Box? = null
    private var selectionStamp = 0L
        private set

    /** 卡片高度由 Android 文本布局提供;纯 JVM 测试可注入固定值。 */
    var cardHeightProvider: (AnnotationCard) -> Float = { 0f }

    /** 页面边界由视图提供，用于移动/缩放贴边吸附。 */
    var pageBoundsProvider: () -> StrokeGeom.Box? = { null }

    private val selectionGuides = mutableListOf<SelectionGuide>()
    var selectionSnapEnabled = true
        private set

    fun toggleSelectionSnap(): Boolean {
        selectionSnapEnabled = !selectionSnapEnabled
        if (!selectionSnapEnabled) selectionGuides.clear()
        notifyInvalidate()
        return selectionSnapEnabled
    }

    fun selectionGuidesSnapshot(): List<SelectionGuide> = selectionGuides.toList()

    private var dragDx = 0f
    private var dragDy = 0f
    private var dragStarted = false
    private var transformStarted: SelectionHandle? = null
    private var transformScale = 1f
    private var transformRotationDeg = 0f
    private var transformPivotX = 0f
    private var transformPivotY = 0f

    fun armLasso() { clearSelection(); lassoArmed = true; notifyInvalidate() }
    fun disarmLasso() { lassoArmed = false; notifyInvalidate() }

    fun beginLasso(first: InkPoint) {
        clearSelection()
        lassoLoop = mutableListOf(first)
        notifyInvalidate()
    }

    fun extendLasso(point: InkPoint) {
        lassoLoop?.add(point)
        notifyInvalidate()
    }

    /** 完全包围即选中；图片/卡片按旋转后的四角判定。 */
    fun endLasso(): Boolean {
        val loop = lassoLoop
        lassoLoop = null
        lassoArmed = false
        val refs = LinkedHashSet<ElementRef>()
        if (loop != null) {
            for (element in page.orderedElements()) {
                val captured = when (element) {
                    is PageElement.Ink -> LassoGeom.captures(loop, element.value)
                    is PageElement.Image -> ElementGeom.rotatedCorners(
                        element.value.x, element.value.y, element.value.width, element.value.height, element.value.rotationDeg,
                    ).all { LassoGeom.contains(loop, it.x, it.y) }
                    is PageElement.Card -> ElementGeom.rotatedCorners(
                        element.value.x, element.value.y,
                        element.value.width * element.value.scale,
                        cardHeightProvider(element.value) * element.value.scale,
                        element.value.rotationDeg,
                    ).all { LassoGeom.contains(loop, it.x, it.y) }
                }
                if (captured) refs.add(element.ref)
            }
        }
        selectionRefs = refs.ifEmpty { null }
        selectionBox = boundsForSelection(refs)
        selectionStamp++
        notifyInvalidate()
        return selectionRefs != null
    }

    fun lassoLoopSnapshot(): List<InkPoint> = lassoLoop?.toList() ?: emptyList()

    fun cancelLasso() {
        lassoLoop = null
        notifyInvalidate()
    }

    /** 手指点按图片/文字卡：按全局层序取最上层目标并单选。 */
    fun selectElementAt(x: Float, y: Float): ElementRef? {
        val target = page.orderedElements().asReversed().firstOrNull { element ->
            when (element) {
                is PageElement.Image -> ElementGeom.contains(element.value, x, y)
                is PageElement.Card -> ElementGeom.contains(
                    element.value, cardHeightProvider(element.value), x, y,
                )
                is PageElement.Ink -> false
            }
        } ?: return null
        clearSelection()
        lassoArmed = false
        selectionRefs = linkedSetOf(target.ref)
        selectionBox = boundsForSelection(selectionRefs!!)
        selectionStamp++
        notifyInvalidate()
        return target.ref
    }

    fun isSelected(ref: ElementRef): Boolean = selectionRefs?.contains(ref) == true

    private fun boundsForSelection(refs: Set<ElementRef>): StrokeGeom.Box? {
        var result: StrokeGeom.Box? = null
        for (element in page.orderedElements()) {
            if (element.ref !in refs) continue
            val b = when (element) {
                is PageElement.Ink -> StrokeGeom.bounds(element.value)
                is PageElement.Image -> ElementGeom.bounds(element.value)
                is PageElement.Card -> ElementGeom.bounds(element.value, cardHeightProvider(element.value))
            }
            result = result?.let {
                StrokeGeom.Box(minOf(it.l, b.l), minOf(it.t, b.t), maxOf(it.r, b.r), maxOf(it.b, b.b))
            } ?: b
        }
        return result
    }

    private fun previewTransform() = ElementGeom.Transform(
        dx = dragDx, dy = dragDy, scale = transformScale, rotationDeg = transformRotationDeg,
        pivotX = transformPivotX, pivotY = transformPivotY,
    )

    private fun displayBox(): StrokeGeom.Box? {
        val b = selectionBox ?: return null
        val t = previewTransform()
        return ElementGeom.bounds(
            listOf(b.l to b.t, b.r to b.t, b.r to b.b, b.l to b.b).map { (x, y) -> t.map(x, y) }
        )
    }

    fun selectionSnapshot(): SelectionSnapshot? = selectionRefs?.let {
        SelectionSnapshot(
            it.size, displayBox(), canReorderSelection(true), canReorderSelection(false), selectionSnapEnabled,
        )
    }

    fun selectionTransformSnapshot(): SelectionTransformSnapshot? {
        val b = selectionBox ?: return null
        if (selectionRefs == null) return null
        return SelectionTransformSnapshot(
            b, dragDx, dragDy, transformScale, transformRotationDeg, transformPivotX, transformPivotY,
        )
    }

    fun selectedStrokes(): List<Stroke> {
        val refs = selectionRefs ?: return emptyList()
        return page.strokes.filter { ElementRef(ElementRef.Kind.STROKE, it.id) in refs }
    }

    fun excludedFromCache(): Set<String> = selectionRefs
        ?.filter { it.kind == ElementRef.Kind.STROKE }?.map { it.id }?.toSet() ?: emptySet()

    fun selectionDragOffset(): Pair<Float, Float> = dragDx to dragDy

    fun hitSelection(x: Float, y: Float, pad: Float = 12f): Boolean =
        selectionRefs?.isNotEmpty() == true && displayBox()?.expand(pad)?.contains(x, y) == true

    /** 控制柄位置随预览变换；rotationOffset 为页坐标，视图按 zoom 补偿。 */
    fun selectionHandlePoint(handle: SelectionHandle, rotationOffset: Float = 48f): ElementGeom.Point? {
        val b = selectionBox ?: return null
        val p = when (handle) {
            SelectionHandle.TOP_LEFT -> b.l to b.t
            SelectionHandle.TOP_RIGHT -> b.r to b.t
            SelectionHandle.BOTTOM_RIGHT -> b.r to b.b
            SelectionHandle.BOTTOM_LEFT -> b.l to b.b
            SelectionHandle.ROTATE -> (b.l + b.r) / 2f to b.t - rotationOffset
        }
        return previewTransform().map(p.first, p.second)
    }

    fun hitSelectionHandle(x: Float, y: Float, pad: Float, rotationOffset: Float = 48f): SelectionHandle? {
        if (selectionRefs == null) return null
        return SelectionHandle.entries.firstOrNull {
            val p = selectionHandlePoint(it, rotationOffset) ?: return@firstOrNull false
            hypot(x - p.x, y - p.y) <= pad
        }
    }

    private data class AxisTarget(val value: Float, val start: Float, val end: Float)
    private data class DragSnap(val target: AxisTarget, val delta: Float, val distance: Float)
    private data class ScaleSnap(
        val scale: Float,
        val target: AxisTarget,
        val vertical: Boolean,
        val distance: Float,
    )

    private fun alignmentTargets(): Pair<List<AxisTarget>, List<AxisTarget>> {
        val refs = selectionRefs ?: return emptyList<AxisTarget>() to emptyList()
        val boxes = page.orderedElements().filter { it.ref !in refs }.map { element ->
            when (element) {
                is PageElement.Ink -> StrokeGeom.bounds(element.value)
                is PageElement.Image -> ElementGeom.bounds(element.value)
                is PageElement.Card -> ElementGeom.bounds(element.value, cardHeightProvider(element.value))
            }
        }
        val allBoxes = boxes + listOfNotNull(pageBoundsProvider())
        return allBoxes.map { AxisTarget(it.l, it.t, it.b) } +
            allBoxes.map { AxisTarget((it.l + it.r) / 2f, it.t, it.b) } +
            allBoxes.map { AxisTarget(it.r, it.t, it.b) } to
            allBoxes.map { AxisTarget(it.t, it.l, it.r) } +
            allBoxes.map { AxisTarget((it.t + it.b) / 2f, it.l, it.r) } +
            allBoxes.map { AxisTarget(it.b, it.l, it.r) }
    }

    private fun translated(box: StrokeGeom.Box, dx: Float, dy: Float) =
        StrokeGeom.Box(box.l + dx, box.t + dy, box.r + dx, box.b + dy)

    private fun bestDragSnap(
        box: StrokeGeom.Box,
        targets: List<AxisTarget>,
        tolerance: Float,
        vertical: Boolean,
    ): DragSnap? {
        val edges = if (vertical) {
            listOf(box.l, (box.l + box.r) / 2f, box.r)
        } else {
            listOf(box.t, (box.t + box.b) / 2f, box.b)
        }
        return targets.flatMap { target ->
            edges.map { edge ->
                val delta = target.value - edge
                DragSnap(target, delta, abs(delta))
            }
        }.filter { it.distance > 0.001f && it.distance <= tolerance }.minByOrNull { it.distance }
    }

    private fun showGuide(target: AxisTarget, box: StrokeGeom.Box, vertical: Boolean) {
        selectionGuides.add(
            if (vertical) SelectionGuide(
                true, target.value, minOf(target.start, box.t), maxOf(target.end, box.b),
            ) else SelectionGuide(
                false, target.value, minOf(target.start, box.l), maxOf(target.end, box.r),
            )
        )
    }

    private fun snapDrag(dx: Float, dy: Float, tolerance: Float) {
        val base = selectionBox ?: return
        val raw = translated(base, dx, dy)
        val (verticalTargets, horizontalTargets) = alignmentTargets()
        selectionGuides.clear()
        val snapX = bestDragSnap(raw, verticalTargets, tolerance, vertical = true)
        val snapY = bestDragSnap(raw, horizontalTargets, tolerance, vertical = false)
        dragDx = dx + (snapX?.delta ?: 0f)
        dragDy = dy + (snapY?.delta ?: 0f)
        val snapped = translated(base, dragDx, dragDy)
        snapX?.let { showGuide(it.target, snapped, vertical = true) }
        snapY?.let { showGuide(it.target, snapped, vertical = false) }
    }

    private fun snapScale(rawScale: Float, tolerance: Float): Float {
        val base = selectionBox ?: return rawScale
        val handle = transformStarted ?: return rawScale
        val (movingX, movingY) = when (handle) {
            SelectionHandle.TOP_LEFT -> base.l to base.t
            SelectionHandle.TOP_RIGHT -> base.r to base.t
            SelectionHandle.BOTTOM_RIGHT -> base.r to base.b
            SelectionHandle.BOTTOM_LEFT -> base.l to base.b
            SelectionHandle.ROTATE -> return rawScale
        }
        val currentX = transformPivotX + (movingX - transformPivotX) * rawScale
        val currentY = transformPivotY + (movingY - transformPivotY) * rawScale
        val (verticalTargets, horizontalTargets) = alignmentTargets()
        val candidates = mutableListOf<ScaleSnap>()
        verticalTargets.forEach { target ->
            val denominator = movingX - transformPivotX
            if (abs(denominator) > 0.001f) {
                val candidate = (target.value - transformPivotX) / denominator
                val distance = abs(target.value - currentX)
                if (candidate in 0.05f..40f && distance > 0.001f && distance <= tolerance) {
                    candidates.add(ScaleSnap(candidate, target, true, distance))
                }
            }
        }
        horizontalTargets.forEach { target ->
            val denominator = movingY - transformPivotY
            if (abs(denominator) > 0.001f) {
                val candidate = (target.value - transformPivotY) / denominator
                val distance = abs(target.value - currentY)
                if (candidate in 0.05f..40f && distance > 0.001f && distance <= tolerance) {
                    candidates.add(ScaleSnap(candidate, target, false, distance))
                }
            }
        }
        val best = candidates.minByOrNull { it.distance } ?: return rawScale
        selectionGuides.clear()
        transformScale = best.scale
        showGuide(best.target, displayBox() ?: base, best.vertical)
        return best.scale
    }

    /** 吸附按元素最终朝向计算；多选时选择修正量最小的元素作为参考。 */
    private fun snapRotation(angle: Float): Float {
        val refs = selectionRefs
        val baseAngles = if (refs == null) emptyList() else buildList {
            page.images.filter { ElementRef(ElementRef.Kind.IMAGE, it.id) in refs }
                .forEach { add(it.rotationDeg) }
            page.cards.filter { ElementRef(ElementRef.Kind.CARD, it.id) in refs }
                .forEach { add(it.rotationDeg) }
        }
        if (baseAngles.isEmpty()) return snapRotationDelta(angle)
        var best: Float? = null
        var bestCorrection = Float.MAX_VALUE
        for (base in baseAngles) {
            val absolute = ((base + angle) % 360f + 360f) % 360f
            val nearest = (absolute / 45f).roundToInt() * 45
            val correction = nearest - absolute
            if (abs(correction) <= 6f && abs(correction) < bestCorrection) {
                best = angle + correction
                bestCorrection = abs(correction)
            }
        }
        return best ?: angle
    }

    /** 无图片/文字卡可参考时（纯笔迹选区）退回按旋转增量吸附。 */
    private fun snapRotationDelta(angle: Float): Float {
        val normalized = (angle % 360f + 360f) % 360f
        val nearest = (normalized / 45f).roundToInt() * 45
        val delta = (normalized - nearest + 540f) % 360f - 180f
        return if (abs(delta) <= 6f) angle - delta else angle
    }

    fun beginSelectionDrag(x: Float, y: Float) {
        resetPreview()
        dragStarted = selectionRefs != null
    }

    fun moveSelectionDrag(dx: Float, dy: Float, snapTolerance: Float = 12f) {
        if (!dragStarted) return
        if (selectionSnapEnabled) {
            snapDrag(dx, dy, snapTolerance)
        } else {
            selectionGuides.clear()
            dragDx = dx; dragDy = dy
        }
        notifyInvalidate()
    }

    fun endSelectionDrag(): Boolean {
        if (!dragStarted) return false
        dragStarted = false
        if (abs(dragDx) <= 1f && abs(dragDy) <= 1f) {
            resetPreview(); notifyInvalidate(); return false
        }
        return commitSelectionTransform()
    }

    fun beginSelectionTransform(handle: SelectionHandle): Boolean {
        val b = selectionBox ?: return false
        if (selectionRefs == null) return false
        resetPreview()
        transformStarted = handle
        when (handle) {
            SelectionHandle.TOP_LEFT -> { transformPivotX = b.r; transformPivotY = b.b }
            SelectionHandle.TOP_RIGHT -> { transformPivotX = b.l; transformPivotY = b.b }
            SelectionHandle.BOTTOM_RIGHT -> { transformPivotX = b.l; transformPivotY = b.t }
            SelectionHandle.BOTTOM_LEFT -> { transformPivotX = b.r; transformPivotY = b.t }
            SelectionHandle.ROTATE -> { transformPivotX = (b.l + b.r) / 2f; transformPivotY = (b.t + b.b) / 2f }
        }
        return true
    }

    fun moveSelectionTransform(scale: Float, rotationDeg: Float, snapTolerance: Float = 12f) {
        val handle = transformStarted ?: return
        selectionGuides.clear()
        if (handle == SelectionHandle.ROTATE) {
            transformRotationDeg = if (selectionSnapEnabled) snapRotation(rotationDeg) else rotationDeg
        } else {
            val rawScale = scale.coerceIn(0.05f, 40f)
            transformScale = if (selectionSnapEnabled) snapScale(rawScale, snapTolerance) else rawScale
        }
        notifyInvalidate()
    }

    fun endSelectionTransform(): Boolean {
        if (transformStarted == null) return false
        transformStarted = null
        if (abs(transformScale - 1f) < 0.001f && abs(transformRotationDeg) < 0.1f) {
            resetPreview(); notifyInvalidate(); return false
        }
        return commitSelectionTransform()
    }

    fun cancelSelectionTransform() {
        transformStarted = null
        resetPreview()
        notifyInvalidate()
    }

    fun cancelSelectionDrag() = cancelSelectionTransform()

    private fun resetPreview() {
        dragStarted = false
        dragDx = 0f; dragDy = 0f
        transformScale = 1f; transformRotationDeg = 0f
        transformPivotX = 0f; transformPivotY = 0f
        selectionGuides.clear()
    }

    private fun transformedStroke(s: Stroke, t: ElementGeom.Transform): Stroke {
        val convertShape = (s.tool == InkTool.RECT || s.tool == InkTool.OVAL) && abs(t.rotationDeg) > 0.01f
        val source = if (convertShape) StrokeGeom.polyline(s) else s.points
        return s.copy(
            id = UUID.randomUUID().toString(),
            tool = if (convertShape) InkTool.PEN else s.tool,
            baseWidthMm = s.baseWidthMm * t.scale,
            points = source.map { p ->
                val mapped = t.map(p.x, p.y)
                p.copy(
                    x = mapped.x, y = mapped.y,
                    pressure = if (convertShape) 1f else p.pressure,
                )
            },
        )
    }

    private fun transformedImage(image: ImageElement, t: ElementGeom.Transform): ImageElement {
        val center = t.map(image.x + image.width / 2f, image.y + image.height / 2f)
        val width = image.width * t.scale
        val height = image.height * t.scale
        return image.copy(
            x = center.x - width / 2f, y = center.y - height / 2f,
            width = width, height = height,
            rotationDeg = (image.rotationDeg + t.rotationDeg) % 360f,
        )
    }

    private fun transformedCard(card: AnnotationCard, t: ElementGeom.Transform): AnnotationCard {
        val baseH = cardHeightProvider(card)
        val center = t.map(
            card.x + card.width * card.scale / 2f,
            card.y + baseH * card.scale / 2f,
        )
        val scale = card.scale * t.scale
        return card.copy(
            x = center.x - card.width * scale / 2f,
            y = center.y - baseH * scale / 2f,
            scale = scale,
            rotationDeg = (card.rotationDeg + t.rotationDeg) % 360f,
        )
    }

    /** 移动/缩放/旋转统一提交为一个可逆 Batch；笔迹换新 id，其余元素保留 id。 */
    private fun commitSelectionTransform(): Boolean {
        val refs = selectionRefs ?: return false
        val t = previewTransform()
        val removes = mutableListOf<InkOp>()
        val adds = mutableListOf<InkOp>()
        val newStrokeIds = mutableMapOf<String, String>()
        page.strokes.withIndex().filter { ElementRef(ElementRef.Kind.STROKE, it.value.id) in refs }
            .sortedByDescending { it.index }.forEach { indexed ->
                removes.add(InkOp.Remove(indexed.value, indexed.index))
            }
        page.strokes.filter { ElementRef(ElementRef.Kind.STROKE, it.id) in refs }.forEach { s ->
            val transformed = transformedStroke(s, t)
            newStrokeIds[s.id] = transformed.id
            adds.add(InkOp.Add(transformed))
        }
        page.images.withIndex().filter { ElementRef(ElementRef.Kind.IMAGE, it.value.id) in refs }
            .sortedByDescending { it.index }.forEach { removes.add(InkOp.RemoveImage(it.value, it.index)) }
        page.images.filter { ElementRef(ElementRef.Kind.IMAGE, it.id) in refs }
            .forEach { adds.add(InkOp.AddImage(transformedImage(it, t))) }
        page.cards.withIndex().filter { ElementRef(ElementRef.Kind.CARD, it.value.id) in refs }
            .sortedByDescending { it.index }.forEach { removes.add(InkOp.RemoveCard(it.value, it.index)) }
        page.cards.filter { ElementRef(ElementRef.Kind.CARD, it.id) in refs }
            .forEach { adds.add(InkOp.AddCard(transformedCard(it, t))) }
        if (removes.isEmpty()) { resetPreview(); notifyInvalidate(); return false }
        val batch = InkOp.Batch(removes + adds)
        page.apply(batch)
        selectionRefs = refs.map { ref ->
            if (ref.kind == ElementRef.Kind.STROKE) ref.copy(id = newStrokeIds[ref.id] ?: ref.id) else ref
        }.toCollection(LinkedHashSet())
        resetPreview()
        selectionBox = boundsForSelection(selectionRefs!!)
        selectionStamp++
        emitForward(batch)
        notifyInvalidate()
        return true
    }

    /** 删除混合选中集，一次撤销步。 */
    fun deleteSelection(): Boolean {
        val refs = selectionRefs ?: return false
        val ops = mutableListOf<InkOp>()
        page.strokes.withIndex().filter { ElementRef(ElementRef.Kind.STROKE, it.value.id) in refs }
            .sortedByDescending { it.index }.forEach { ops.add(InkOp.Remove(it.value, it.index)) }
        page.images.withIndex().filter { ElementRef(ElementRef.Kind.IMAGE, it.value.id) in refs }
            .sortedByDescending { it.index }.forEach { ops.add(InkOp.RemoveImage(it.value, it.index)) }
        page.cards.withIndex().filter { ElementRef(ElementRef.Kind.CARD, it.value.id) in refs }
            .sortedByDescending { it.index }.forEach { ops.add(InkOp.RemoveCard(it.value, it.index)) }
        if (ops.isEmpty()) { clearSelection(); return false }
        val batch = InkOp.Batch(ops)
        clearSelection()
        page.apply(batch)
        emitForward(batch)
        notifyInvalidate()
        return true
    }

    private fun reorderedSelection(up: Boolean): Pair<List<LayerState>, List<LayerState>>? {
        val refs = selectionRefs ?: return null
        val ordered = page.orderedElements().map { it.ref }.toMutableList()
        val before = page.orderedElements().map { LayerState(it.ref, it.zIndex) }
        var changed = false
        if (up) {
            for (i in ordered.size - 2 downTo 0) {
                if (ordered[i] in refs && ordered[i + 1] !in refs) {
                    java.util.Collections.swap(ordered, i, i + 1); changed = true
                }
            }
        } else {
            for (i in 1 until ordered.size) {
                if (ordered[i] in refs && ordered[i - 1] !in refs) {
                    java.util.Collections.swap(ordered, i, i - 1); changed = true
                }
            }
        }
        if (!changed) return null
        return before to ordered.mapIndexed { i, ref -> LayerState(ref, i + 1L) }
    }

    fun canReorderSelection(up: Boolean): Boolean = reorderedSelection(up) != null

    fun reorderSelection(up: Boolean): Boolean {
        val (before, after) = reorderedSelection(up) ?: return false
        val op = InkOp.Reorder(before, after)
        page.apply(op)
        emitForward(op)
        notifyInvalidate()
        return true
    }

    fun clearSelection() {
        if (selectionRefs == null && selectionBox == null && !dragStarted && transformStarted == null) return
        selectionRefs = null
        selectionBox = null
        selectionSnapEnabled = true
        transformStarted = null
        resetPreview()
        selectionStamp++
        notifyInvalidate()
    }

    /** 渲染缓存失效键(页面版本 + 选中集版本 + 图片拖动版本) */
    val renderStamp: Long get() = page.version * 1_000_003L + selectionStamp + imageDragStamp

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
            zIndex = page.nextZIndex(),
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
        val batch = InkOp.Batch(listOf(InkOp.RemoveCard(card, page.cards.indexOfFirst { it.id == card.id }), InkOp.AddCard(moved)))
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
        val op = InkOp.AddCard(card.copy(zIndex = page.nextZIndex()))
        page.apply(op)
        emitForward(op)
        notifyInvalidate()
    }

    fun deleteAnnotationCard(cardId: String): Boolean {
        val index = page.cards.indexOfFirst { it.id == cardId }
        if (index < 0) return false
        val op = InkOp.RemoveCard(page.cards[index], index)
        page.apply(op)
        emitForward(op)
        notifyInvalidate()
        return true
    }

    // ---- 图片元素(贴图层;页坐标,渲染在笔迹之下、批注卡之下;同一撤销栈)----

    fun images(): List<ImageElement> = page.images.toList()

    /** 插入图片元素(进撤销栈;落盘经 onCommit → saveImages 整表重写) */
    fun addImageElement(image: ImageElement) {
        val op = InkOp.AddImage(image.copy(zIndex = page.nextZIndex()))
        page.apply(op)
        emitForward(op)
        notifyInvalidate()
    }

    /** 删除图片元素(RemoveImage 记录层位,撤销原位恢复;可撤销);返回是否删除 */
    fun deleteImageElement(imageId: String): Boolean {
        val idx = page.images.indexOfFirst { it.id == imageId }
        if (idx < 0) return false
        val op = InkOp.RemoveImage(page.images[idx], idx)
        page.apply(op)
        emitForward(op)
        notifyInvalidate()
        return true
    }

    // ---- 图片拖动(与批注卡拖动同构:拖动预览由视图渲染,抬起才落 Batch op)----

    private var imageDragId: String? = null
    private var imageDragDx = 0f
    private var imageDragDy = 0f
    private var imageDragStarted = false
    /** 图片拖动版本(进入/退出拖动时推进 → 视图 committed 缓存把拖动中的图片摘出来重画) */
    private var imageDragStamp = 0L

    /** 拖动中的图片 id(渲染缓存排除集用;null = 无拖动) */
    fun draggingImageId(): String? = imageDragId

    /** 拖动预览偏移(id to (dx, dy);页坐标) */
    fun imageDragOffset(): Pair<String, Pair<Float, Float>>? =
        imageDragId?.let { it to (imageDragDx to imageDragDy) }

    /** 开始图片拖动(命中后调用;id 不存在则不进入拖动态) */
    fun beginImageDrag(imageId: String) {
        imageDragId = imageId
        imageDragDx = 0f; imageDragDy = 0f
        imageDragStarted = page.images.any { it.id == imageId }
        imageDragStamp++
        notifyInvalidate()
    }

    fun moveImageDrag(dx: Float, dy: Float) {
        if (!imageDragStarted) return
        imageDragDx = dx; imageDragDy = dy
        notifyInvalidate()
    }

    /**
     * 拖动结束:位移 >1px 落 Batch(RemoveImage 旧 + AddImage 平移,同 id 保持 src 不变),
     * 可撤销(undo 先撤 AddImage 再按 index 原位恢复);返回是否落盘。
     */
    fun endImageDrag(): Boolean {
        if (!imageDragStarted) return false
        imageDragStarted = false
        val id = imageDragId
        val dx = imageDragDx; val dy = imageDragDy
        imageDragId = null; imageDragDx = 0f; imageDragDy = 0f
        val idx = page.images.indexOfFirst { it.id == id }
        val image = idx.takeIf { it >= 0 }?.let { page.images[it] }
        if (image == null || (dx * dx + dy * dy) < 1f) {
            imageDragStamp++
            notifyInvalidate()
            return false
        }
        val moved = image.copy(x = image.x + dx, y = image.y + dy)
        val batch = InkOp.Batch(listOf(InkOp.RemoveImage(image, idx), InkOp.AddImage(moved)))
        page.apply(batch)
        imageDragStamp++
        emitForward(batch)
        notifyInvalidate()
        return true
    }

    /** 放弃进行中的拖动(第二指落下/事件取消);不产生记录 */
    fun cancelImageDrag() {
        if (!imageDragStarted && imageDragId == null) return
        imageDragStarted = false
        imageDragId = null
        imageDragDx = 0f; imageDragDy = 0f
        imageDragStamp++
        notifyInvalidate()
    }

    private fun emitInverse(op: InkOp) = when (op) {
        is InkOp.Add -> onCommit?.invoke(InkCommit.Tombstoned(op.stroke.id))
        is InkOp.Remove -> onCommit?.invoke(InkCommit.Added(op.stroke))
        is InkOp.AddCard -> onCommit?.invoke(InkCommit.CardRemoved(op.card.id))
        is InkOp.RemoveCard -> onCommit?.invoke(InkCommit.CardAdded(op.card))
        is InkOp.AddImage -> onCommit?.invoke(InkCommit.ImageRemoved(op.image.id))
        is InkOp.RemoveImage -> onCommit?.invoke(InkCommit.ImageAdded(op.image))
        is InkOp.Reorder -> onCommit?.invoke(InkCommit.LayersChanged)
        is InkOp.Batch -> op.ops.reversed().forEach(::emitInverse)
    }

    private fun emitForward(op: InkOp) = when (op) {
        is InkOp.Add -> onCommit?.invoke(InkCommit.Added(op.stroke))
        is InkOp.Remove -> onCommit?.invoke(InkCommit.Tombstoned(op.stroke.id))
        is InkOp.AddCard -> onCommit?.invoke(InkCommit.CardAdded(op.card))
        is InkOp.RemoveCard -> onCommit?.invoke(InkCommit.CardRemoved(op.card.id))
        is InkOp.AddImage -> onCommit?.invoke(InkCommit.ImageAdded(op.image))
        is InkOp.RemoveImage -> onCommit?.invoke(InkCommit.ImageRemoved(op.image.id))
        is InkOp.Reorder -> onCommit?.invoke(InkCommit.LayersChanged)
        is InkOp.Batch -> op.ops.forEach(::emitForward)
    }

    fun clear() {
        page.strokes.clear()
        page.cards.clear()
        page.images.clear()
        clearSelection()
        notifyInvalidate()
    }

    /** 整页加载(切页;批注卡/图片一并) */
    fun loadStrokes(
        strokes: List<Stroke>,
        cards: List<AnnotationCard> = emptyList(),
        images: List<ImageElement> = emptyList(),
    ) {
        clearSelection()
        page.reset(strokes, cards, images)
        notifyInvalidate()
    }
}

/** 界面可响应的笔动作(由 PenActionRouter 映射产生) */
enum class InkAction {
    TOGGLE_ERASER, LASO, SNIP, UNDO, REDO, NEW_PAGE, TEMP_ERASER;
}
