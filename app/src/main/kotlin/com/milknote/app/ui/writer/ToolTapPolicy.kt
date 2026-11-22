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
    TOOL, TEXT, SHAPE, IMAGE, LASSO, MATERIAL, RECORD, VIDEO, PLAYBACK, MORE, NEW_PAGE,
}

data class WriterPanelState(val open: WriterPanel? = null) {
    fun open(panel: WriterPanel): WriterPanelState = WriterPanelState(panel)
    fun dismiss(): WriterPanelState = WriterPanelState()
}
