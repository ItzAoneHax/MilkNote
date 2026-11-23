package com.milknote.app.ui.writer

import com.milknote.ink.model.InkTool

/** P016 主工具带的完整顺序；视口宽度只影响滚动位置，不改变工具集合。 */
enum class WriterMajorTool(val id: String, val label: String) {
    BRUSH("brush", "画笔"),
    HIGHLIGHTER("highlighter", "荧光笔"),
    ERASER("eraser", "橡皮"),
    LASSO("lasso", "套索"),
    IMAGE("image", "图片"),
    TEXT("text", "文本"),
    DOODLE("doodle", "图案笔"),
    SHAPE("shape", "图形"),
    SNIP("snip", "摘取工具"),
    TAPE("tape", "胶带"),
    READING("reading", "阅读工具"),
    LASER("laser", "激光笔"),
}

enum class WriterRailMode { PAGES, SNIPPETS, OUTLINE }

/** 顶栏/笔匣工具按钮的激活态 = 当前所选工具 */
internal fun isMajorToolActive(tool: WriterMajorTool, selected: WriterMajorTool, state: WriterUiState): Boolean = tool == selected

val writerMajorToolOrder: List<WriterMajorTool> = listOf(
    WriterMajorTool.BRUSH,
    WriterMajorTool.HIGHLIGHTER,
    WriterMajorTool.ERASER,
    WriterMajorTool.LASSO,
    WriterMajorTool.IMAGE,
    WriterMajorTool.TEXT,
    WriterMajorTool.DOODLE,
    WriterMajorTool.SHAPE,
    WriterMajorTool.SNIP,
    WriterMajorTool.TAPE,
    WriterMajorTool.READING,
    WriterMajorTool.LASER,
)

fun writerInkTool(tool: WriterMajorTool, settings: WriterToolSettings): InkTool = when (tool) {
    WriterMajorTool.BRUSH -> settings.currentTool.takeIf { it == InkTool.PEN || it == InkTool.BALL } ?: InkTool.PEN
    WriterMajorTool.HIGHLIGHTER -> InkTool.HIGHLIGHTER
    WriterMajorTool.ERASER -> InkTool.ERASER
    WriterMajorTool.DOODLE -> InkTool.DOODLE
    WriterMajorTool.LASER -> InkTool.LASER
    else -> settings.currentTool
}

enum class WriterMinorKind {
    PEN, HIGHLIGHTER, ERASER, LASSO, IMAGE, TEXT, SHAPE, SNIP, TAPE, READING, LASER,
}

data class WriterMinorSpec(
    val kind: WriterMinorKind,
    val tool: InkTool? = null,
    val colors: List<Long> = emptyList(),
    val widths: List<Float> = emptyList(),
)

fun writerMinorSpec(tool: WriterMajorTool, settings: WriterToolSettings): WriterMinorSpec {
    val inkTool = writerInkTool(tool, settings)
    return when (tool) {
        WriterMajorTool.BRUSH, WriterMajorTool.DOODLE, WriterMajorTool.LASER -> WriterMinorSpec(
            kind = when (tool) {
                WriterMajorTool.DOODLE -> WriterMinorKind.PEN
                WriterMajorTool.LASER -> WriterMinorKind.LASER
                else -> WriterMinorKind.PEN
            },
            tool = inkTool,
            colors = settings.palettes[inkTool].orEmpty().take(if (tool == WriterMajorTool.LASER) 3 else 5),
            widths = if (tool == WriterMajorTool.LASER) emptyList() else listOf(0.4f, 0.7f, 1.2f),
        )
        WriterMajorTool.HIGHLIGHTER -> WriterMinorSpec(
            kind = WriterMinorKind.HIGHLIGHTER,
            tool = InkTool.HIGHLIGHTER,
            colors = settings.palettes[InkTool.HIGHLIGHTER].orEmpty().take(5),
            widths = listOf(1.2f, 2f, 3f),
        )
        WriterMajorTool.ERASER -> WriterMinorSpec(
            kind = WriterMinorKind.ERASER,
            tool = InkTool.ERASER,
            widths = listOf(2f, 5f, 9f),
        )
        WriterMajorTool.LASSO -> WriterMinorSpec(WriterMinorKind.LASSO)
        WriterMajorTool.IMAGE -> WriterMinorSpec(WriterMinorKind.IMAGE)
        WriterMajorTool.TEXT -> WriterMinorSpec(WriterMinorKind.TEXT)
        WriterMajorTool.SHAPE -> WriterMinorSpec(WriterMinorKind.SHAPE)
        WriterMajorTool.SNIP -> WriterMinorSpec(WriterMinorKind.SNIP)
        WriterMajorTool.TAPE -> WriterMinorSpec(WriterMinorKind.TAPE)
        WriterMajorTool.READING -> WriterMinorSpec(WriterMinorKind.READING)
    }
}

/** 双列页面栏以行为滚动单位；当前页已可见时不打断用户滚动。 */
fun pageRailScrollTarget(
    pageIds: List<String>,
    currentPageId: String,
    visibleRows: Set<Int>,
): Int? {
    val currentIndex = pageIds.indexOf(currentPageId)
    if (currentIndex < 0) return null
    val row = currentIndex / 2
    return row.takeUnless { it in visibleRows }
}

fun toggleWriterRail(current: WriterRailMode?, target: WriterRailMode): WriterRailMode? =
    if (current == target) null else target
