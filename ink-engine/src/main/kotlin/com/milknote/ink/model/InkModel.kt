package com.milknote.ink.model

import kotlinx.serialization.Serializable

enum class LassoContent(val label: String) {
    INK("笔迹"), SHAPE("图形"), IMAGE("图片"), TEXT("文字"),
    DOODLE("图案笔"), TAPE("胶带"), STICKER("贴纸"), MEDIA("音视频"),
}

/** 套索圈选方式:FREEHAND = 自由轨迹;RECTANGLE = 起点与当前点构成的轴对齐矩形 */
enum class LassoMode { FREEHAND, RECTANGLE }

/** 工具(DESIGN-V0 §6.1 Stroke.tool) */
enum class InkTool(val id: String) {
    PEN("pen"),           // 钢笔:压感变宽 + 倾斜笔锋
    BALL("ball"),         // 圆珠:恒定宽度,轻压感
    HIGHLIGHTER("highlighter"), // 荧光:宽、半透明
    ERASER("eraser"),     // 擦除(整笔划)
    RECT("rect"),         // 矩形:两点 = 对角,轮廓描边
    OVAL("oval"),         // 椭圆:两点 = 外接矩形,轮廓描边
    LINE("line"),         // 直线:两点 = 线段,描边
    DASHED_LINE("dashed_line"),
    ARROW("arrow"),
    TRIANGLE("triangle"),
    STAR("star"),
    POLYGON("polygon"),
    DOODLE("doodle"),     // 图案笔：沿自由轨迹生成花纹
    LASER("laser");       // 临时指示：只显示活动轨迹，不写持久化

    companion object { fun of(id: String): InkTool = entries.firstOrNull { it.id == id } ?: PEN }
}

/** 形状工具:两点 Stroke(起点/终点定义几何),恒定轮廓宽 = baseWidthMm */
val InkTool.isShape: Boolean
    get() = this in setOf(
        InkTool.RECT, InkTool.OVAL, InkTool.LINE, InkTool.DASHED_LINE,
        InkTool.ARROW, InkTool.TRIANGLE, InkTool.STAR, InkTool.POLYGON,
    )

/** 单采样点。压感为归一化原始值 [0,1](压感是数据,笔宽是样式 —— PEN-ADAPTATION §3.3) */
data class InkPoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tiltX: Float, // 0..90,0 = 笔垂直纸面
    val tiltY: Float,
    val timeMs: Long,
)

/** 一笔完整笔迹;createdAt = 生成时刻(Unix 毫秒,0 = 旧数据未记录) */
data class Stroke(
    val id: String,
    val tool: InkTool,
    val color: Long,        // ARGB
    val baseWidthMm: Float, // 压感映射前基准宽
    val points: List<InkPoint>,
    val zIndex: Long = 0L,  // 全局层序;旧数据 0 按历史层带排序
    val createdAt: Long = 0L, // 生成 Unix 毫秒;0 = 旧笔记缺省,不伪造
)

/**
 * 页面图片元素(用户/Agent 贴图;GRAPHICS-ENGINE §图片层):
 * src 为相对 notebookDir 的相对路径(如 `images/xxx.png`),文件本体随笔记本目录备份;
 * x/y/width/height 为页坐标 px(与笔迹同一坐标系)。落盘走 `<pageId>.img.json` 整表重写。
 */
@Serializable
enum class PageImageKind { IMAGE, TAPE, STICKER, PDF_PAGE }

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
    val kind: PageImageKind = PageImageKind.IMAGE,
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
    data class AddMedia(val media: MediaElement) : InkOp
    data class RemoveMedia(val media: MediaElement, val index: Int) : InkOp
    data class Reorder(val before: List<LayerState>, val after: List<LayerState>) : InkOp
    data class Batch(val ops: List<InkOp>) : InkOp
}

/**
 * 批注卡(Agent 结果贴回的承载;页坐标 px,与笔迹同一坐标系)。
 * 高度由文本布局测量得出(渲染层计算),不落库。
 */
@Serializable
enum class CardTextAlign { START, CENTER, END }

@Serializable
data class CardTextStyle(
    val fontSizePx: Float = 24f,
    val colorArgb: Long = 0xFF23221E,
    val align: CardTextAlign = CardTextAlign.START,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
)

@Serializable
data class AnnotationCard(
    val id: String,
    val x: Float,               // 左上角 x(页坐标 px)
    val y: Float,               // 左上角 y
    val width: Float,           // 卡宽(px;默认约 74mm)
    val kind: Kind = Kind.TEXT,
    val text: String,           // markdown-lite 源文(TEXT)/ LaTeX 源码(LATEX)/ 全量 Markdown(MARKDOWN)
    val createdAt: Long = 0L,
    val rotationDeg: Float = 0f,
    val scale: Float = 1f,      // 内容整体缩放;保留原宽度用于文本布局缓存
    val zIndex: Long = 0L,
    val textStyle: CardTextStyle = CardTextStyle(),
) {
    enum class Kind { TEXT, LATEX, MARKDOWN }
}

/** 页面墨迹状态:提交的笔迹 + 批注卡 + 图片元素 + 音视频元素 + 撤销/重做栈 */
class PageInkState {
    val strokes: MutableList<Stroke> = mutableListOf()
    val cards: MutableList<AnnotationCard> = mutableListOf()
    val images: MutableList<ImageElement> = mutableListOf()
    val media: MutableList<MediaElement> = mutableListOf()
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
        media: List<MediaElement> = emptyList(),
    ) {
        this.strokes.clear()
        this.strokes.addAll(strokes)
        this.cards.clear()
        this.cards.addAll(cards)
        this.images.clear()
        this.images.addAll(images)
        this.media.clear()
        this.media.addAll(media)
        undoStack.clear()
        redoStack.clear()
        version++
    }

    /**
     * 恢复内容与撤销/重做两栈(重启后由操作日志回放构建)。
     * 与 [reset] 不同,这里保留历史;调用方负责传入当前页的副本。
     */
    fun restoreDrawing(
        strokes: List<Stroke>,
        cards: List<AnnotationCard>,
        images: List<ImageElement>,
        media: List<MediaElement>,
        undo: List<InkOp>,
        redo: List<InkOp>,
    ) {
        this.strokes.clear()
        this.strokes.addAll(strokes)
        this.cards.clear()
        this.cards.addAll(cards)
        this.images.clear()
        this.images.addAll(images)
        this.media.clear()
        this.media.addAll(media)
        undoStack.clear()
        undoStack.addAll(undo)
        redoStack.clear()
        redoStack.addAll(redo)
        version++
    }

    fun undoStackSnapshot(): List<InkOp> = undoStack.toList()

    fun redoStackSnapshot(): List<InkOp> = redoStack.toList()

    /**
     * 仍生效的「已擦除」笔迹:沿撤销栈顺序回放删除,取当前已不存在的 id。
     * 同 id 的原地移动(Remove + Add 同 id)不算擦除;已被撤销的删除也不算。
     * 返回顺序按首次删除的先后;内容取最后一次删除时的笔迹。
     */
    fun removedStrokes(): List<Stroke> {
        val present = strokes.mapTo(HashSet(strokes.size)) { it.id }
        val removed = LinkedHashMap<String, Stroke>()
        fun visit(op: InkOp) {
            when (op) {
                is InkOp.Remove -> removed[op.stroke.id] = op.stroke
                is InkOp.Add -> removed.remove(op.stroke.id)
                is InkOp.Batch -> op.ops.forEach(::visit)
                else -> {}
            }
        }
        undoStack.forEach(::visit)
        val iterator = removed.keys.iterator()
        while (iterator.hasNext()) {
            if (iterator.next() in present) iterator.remove()
        }
        return removed.values.toList()
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

    /** 整笔划擦除:与 eraser 轨迹包围盒/轨迹相交的笔迹全部移除,返回产生的 Batch op;
     *  [targets] 非空时只擦除命中集合内 tool 的笔迹(千本笔记 P019「擦除画笔/荧光笔/涂鸦笔」对齐)。 */
    fun eraseStrokes(eraser: Stroke, targets: Set<InkTool>? = null): InkOp.Batch? {
        val ops = mutableListOf<InkOp>()
        val it = strokes.listIterator()
        while (it.hasNext()) {
            val s = it.next()
            if (targets != null && s.tool !in targets) continue
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
        media.replaceAll { m ->
            byRef[ElementRef(ElementRef.Kind.MEDIA, m.id)]?.let { m.copy(zIndex = it.zIndex) } ?: m
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
            is InkOp.AddMedia -> media.add(op.media)
            is InkOp.RemoveMedia -> media.removeAt(media.indexOfFirst { it.id == op.media.id }.takeIf { it >= 0 } ?: return)
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
            is InkOp.AddMedia -> media.removeAt(media.indexOfFirst { it.id == op.media.id }.takeIf { it >= 0 } ?: return)
            is InkOp.RemoveMedia -> {
                val idx = op.index.coerceIn(0, media.size)
                media.add(idx, op.media)                                 // 原层位恢复
            }
            is InkOp.Reorder -> applyLayers(op.before)
            is InkOp.Batch -> op.ops.reversed().forEach(::undoApply)
        }
    }
}
