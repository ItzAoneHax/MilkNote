package com.milknote.app.ui.writer

/** 首击切换；再击打开设置；设置已打开时再击关闭。 */
enum class ToolTapOutcome { ACTIVATE, OPEN_SETTINGS, CLOSE_SETTINGS }

fun resolveToolTap(currentlyActive: Boolean, settingsOpen: Boolean = false): ToolTapOutcome = when {
    !currentlyActive -> ToolTapOutcome.ACTIVATE
    settingsOpen -> ToolTapOutcome.CLOSE_SETTINGS
    else -> ToolTapOutcome.OPEN_SETTINGS
}

/** 任一时刻只有一个调整面板；选区条、Toast、翻页控件不属于此状态。 */
enum class WriterPanel {
    TOOL, TEXT, SHAPE, IMAGE, LASSO, MATERIAL, RECORD, VIDEO, PLAYBACK, MORE, NEW_PAGE, FUNCTION_GRAPH,
}

data class WriterPanelState(val open: WriterPanel? = null) {
    fun open(panel: WriterPanel): WriterPanelState = WriterPanelState(panel)
    fun dismiss(): WriterPanelState = WriterPanelState()
}

/** 退出阅读模式时恢复的书写工具：回到进入前的工具（避开阅读自身和模态框选），默认钢笔。 */
fun restoreToolAfterReading(previousTool: WriterMajorTool): WriterMajorTool =
    previousTool.takeIf { it != WriterMajorTool.READING && it != WriterMajorTool.SNIP } ?: WriterMajorTool.BRUSH
