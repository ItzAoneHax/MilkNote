package com.milknote.app.ui.writer

/** 统一的工具点击规则：未选中时切换，再次点击当前工具打开中央设置。 */
enum class ToolTapOutcome { ACTIVATE, OPEN_SETTINGS }

fun resolveToolTap(currentlyActive: Boolean): ToolTapOutcome =
    if (currentlyActive) ToolTapOutcome.OPEN_SETTINGS else ToolTapOutcome.ACTIVATE

/** 页面级中央调整面板互斥；选区条、Toast、翻页控件不属于此状态。 */
enum class WriterPanel {
    TOOL, TEXT, SHAPE, IMAGE, LASSO, MATERIAL, CONSOLE, RECORD, VIDEO, PLAYBACK,
}

data class WriterPanelState(val open: WriterPanel? = null) {
    fun open(panel: WriterPanel): WriterPanelState = WriterPanelState(panel)
    fun dismiss(): WriterPanelState = WriterPanelState()
}
