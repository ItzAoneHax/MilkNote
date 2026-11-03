package com.milknote.ink.model

import kotlinx.serialization.Serializable

/** 工具(DESIGN-V0 §6.1 Stroke.tool) */
enum class InkTool(val id: String) {
    PEN("pen"),           // 钢笔:压感变宽 + 倾斜笔锋
    BALL("ball"),         // 圆珠:恒定宽度,轻压感
    HIGHLIGHTER("highlighter"), // 荧光:宽、半透明
    ERASER("eraser"),     // 擦除(整笔划)
    RECT("rect"),         // 矩形:两点 = 对角,轮廓描边
    OVAL("oval"),         // 椭圆:两点 = 外接矩形,轮廓描边
    LINE("line");         // 直线:两点 = 线段,描边

    companion object { fun of(id: String): InkTool = entries.firstOrNull { it.id == id } ?: PEN }
}

/** 形状工具:两点 Stroke(起点/终点定义几何),恒定轮廓宽 = baseWidthMm */
val InkTool.isShape: Boolean
    get() = this == InkTool.RECT || this == InkTool.OVAL || this == InkTool.LINE

/** 单采样点。压感为归一化原始值 [0,1](压感是数据,笔宽是样式 —— PEN-ADAPTATION §3.3) */
data class InkPoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tiltX: Float, // 0..90,0 = 笔垂直纸面
    val tiltY: Float,
    val timeMs: Long,
)

/** 一笔完整笔迹 */
data class Stroke(
    val id: String,
    val tool: InkTool,
    val color: Long,        // ARGB
    val baseWidthMm: Float, // 压感映射前基准宽
    val points: List<InkPoint>,
    val zIndex: Long = 0L,  // 全局层序;旧数据 0 按历史层带排序
)

/**
 * 页面图片元素(用户/Agent 贴图;GRAPHICS-ENGINE §图片层):
 * src 为相对 notebookDir 的相对路径(如 `images/xxx.png`),文件本体随笔记本目录备份;
 * x/y/width/height 为页坐标 px(与笔迹同一坐标系)。落盘走 `<pageId>.img.json` 整表重写。
 */
@Serializable
data class ImageElement(
    val id: String,
    val src: String,            // 相对 notebookDir 的路径
    val x: Float,               // 左上角 x(页坐标 px)
    val y: Float,               // 左上角 y
    val width: Float,           // 显示宽(px)
    val height: Float,          // 显示高(px)
    val createdAt: Long = 0L,
    val rotationDeg: Float = 0f,
    val zIndex: Long = 0L,
)

/** 当前笔设置(工具条状态) */
data class ToolConfig(
    val tool: InkTool = InkTool.PEN,
    val color: Long = 0xFF23221E,      // 墨色 ink
    val baseWidthMm: Float = 0.7f,     // PEN 基准
    val pressureGamma: PressureGamma = PressureGamma.STANDARD,
)

/** 压感曲线三档(PEN-ADAPTATION §3.4:γ 1.4/1.0/0.7) */
enum class PressureGamma(val gamma: Float, val label: String) {
    SOFT(1.4f, "柔和"), STANDARD(1.0f, "标准"), SENSITIVE(0.7f, "灵敏");
}

/** 撤销栈操作:写入 = Add,擦除 = Remove;批注卡/图片同栈(DESIGN-V0 §4 / AGENT-BACKEND §9 / 图片层) */
sealed interface InkOp {
    data class Add(val stroke: Stroke) : InkOp
    data class Remove(val stroke: Stroke, val index: Int) : InkOp
    data class AddCard(val card: AnnotationCard) : InkOp
    data class RemoveCard(val card: AnnotationCard, val index: Int = -1) : InkOp
    data class AddImage(val image: ImageElement) : InkOp
    data class RemoveImage(val image: ImageElement, val index: Int) : InkOp
    data class Reorder(val before: List<LayerState>, val after: List<LayerState>) : InkOp
    data class Batch(val ops: List<InkOp>) : InkOp
}

/**
 * 批注卡(Agent 结果贴回的承载;页坐标 px,与笔迹同一坐标系)。
 * 高度由文本布局测量得出(渲染层计算),不落库。
 */
@Serializable
data class AnnotationCard(
    val id: String,
    val x: Float,               // 左上角 x(页坐标 px)
    val y: Float,               // 左上角 y
    val width: Float,           // 卡宽(px;默认约 74mm)
    val kind: Kind = Kind.TEXT,
    val text: String,           // markdown-lite 源文(TEXT)/ LaTeX 源码(LATEX)
    val createdAt: Long = 0L,
    val rotationDeg: Float = 0f,
    val scale: Float = 1f,      // 内容整体缩放;保留原宽度用于文本布局缓存
    val zIndex: Long = 0L,
) {
    enum class Kind { TEXT, LATEX }
}

/** 页面墨迹状态:提交的笔迹 + 批注卡 + 图片元素 + 撤销/重做栈 */
class PageInkState {
    val strokes: MutableList<Stroke> = mutableListOf()
    val cards: MutableList<AnnotationCard> = mutableListOf()
    val images: MutableList<ImageElement> = mutableListOf()
    private val undoStack: ArrayDeque<InkOp> = ArrayDeque()
    private val redoStack: ArrayDeque<InkOp> = ArrayDeque()

    var version: Long = 0
        private set

    fun apply(op: InkOp) {
        doApply(op)
        undoStack.addLast(op)
        redoStack.clear()
        version++
    }

    /** 整页替换(切页加载;撤销栈清零,历史由文件承载) */
    fun reset(
        strokes: List<Stroke>,
        cards: List<AnnotationCard> = emptyList(),
        images: List<ImageElement> = emptyList(),
    ) {
        this.strokes.clear()
        this.strokes.addAll(strokes)
        this.cards.clear()
        this.cards.addAll(cards)
        this.images.clear()
        this.images.addAll(images)
        undoStack.clear()
        redoStack.clear()
        version++
    }

    /** 返回被撤销的 op(供落盘镜像:反向记录) */
    fun undo(): InkOp? {
        val op = undoStack.removeLastOrNull() ?: return null
        undoApply(op)
        redoStack.addLast(op)
        version++
        return op
    }

    /** 返回被重做的 op(供落盘镜像:正向记录) */
    fun redo(): InkOp? {
        val op = redoStack.removeLastOrNull() ?: return null
        doApply(op)
        undoStack.addLast(op)
        version++
        return op
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** 整笔划擦除:与 eraser 轨迹包围盒/轨迹相交的笔迹全部移除,返回产生的 Batch op */
    fun eraseStrokes(eraser: Stroke): InkOp.Batch? {
        val ops = mutableListOf<InkOp>()
        val it = strokes.listIterator()
        while (it.hasNext()) {
            val s = it.next()
            if (StrokeGeom.intersects(s, eraser)) {
                ops.add(InkOp.Remove(s, it.previousIndex()))
            }
        }
        if (ops.isEmpty()) return null
        return InkOp.Batch(ops.reversed()) // 从后往前删,保留下标语义
    }

    private fun applyLayers(states: List<LayerState>) {
        val byRef = states.associateBy { it.ref }
        strokes.replaceAll { s ->
            byRef[ElementRef(ElementRef.Kind.STROKE, s.id)]?.let { s.copy(zIndex = it.zIndex) } ?: s
        }
        cards.replaceAll { c ->
            byRef[ElementRef(ElementRef.Kind.CARD, c.id)]?.let { c.copy(zIndex = it.zIndex) } ?: c
        }
        images.replaceAll { i ->
            byRef[ElementRef(ElementRef.Kind.IMAGE, i.id)]?.let { i.copy(zIndex = it.zIndex) } ?: i
        }
    }

    private fun doApply(op: InkOp) {
        when (op) {
            is InkOp.Add -> strokes.add(op.stroke)
            is InkOp.Remove -> strokes.removeAt(strokes.indexOfFirst { it.id == op.stroke.id }.takeIf { it >= 0 } ?: return)
            is InkOp.AddCard -> cards.add(op.card)
            is InkOp.RemoveCard -> cards.removeAt(cards.indexOfFirst { it.id == op.card.id }.takeIf { it >= 0 } ?: return)
            is InkOp.AddImage -> images.add(op.image)
            is InkOp.RemoveImage -> images.removeAt(images.indexOfFirst { it.id == op.image.id }.takeIf { it >= 0 } ?: return)
            is InkOp.Reorder -> applyLayers(op.after)
            is InkOp.Batch -> op.ops.forEach(::doApply)
        }
    }

    private fun undoApply(op: InkOp) {
        when (op) {
            is InkOp.Add -> strokes.removeAt(strokes.indexOfFirst { it.id == op.stroke.id }.takeIf { it >= 0 } ?: return)
            is InkOp.Remove -> {
                val idx = op.index.coerceIn(0, strokes.size)
                strokes.add(idx, op.stroke)
            }
            is InkOp.AddCard -> cards.removeAt(cards.indexOfFirst { it.id == op.card.id }.takeIf { it >= 0 } ?: return)
            is InkOp.RemoveCard -> {
                val idx = if (op.index < 0) cards.size else op.index.coerceIn(0, cards.size)
                cards.add(idx, op.card)
            }
            is InkOp.AddImage -> images.removeAt(images.indexOfFirst { it.id == op.image.id }.takeIf { it >= 0 } ?: return)
            is InkOp.RemoveImage -> {
                val idx = op.index.coerceIn(0, images.size)
                images.add(idx, op.image)                                // 原层位恢复
            }
            is InkOp.Reorder -> applyLayers(op.before)
            is InkOp.Batch -> op.ops.reversed().forEach(::undoApply)
        }
    }
}
