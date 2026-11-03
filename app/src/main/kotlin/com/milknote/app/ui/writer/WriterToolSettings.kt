package com.milknote.app.ui.writer

import com.milknote.app.ui.theme.BallBlueArgb
import com.milknote.app.ui.theme.HighlightBlueArgb
import com.milknote.app.ui.theme.HighlightGreenArgb
import com.milknote.app.ui.theme.HighlightPinkArgb
import com.milknote.app.ui.theme.HighlightYellowArgb
import com.milknote.app.ui.theme.InkBlackArgb
import com.milknote.app.ui.theme.InkBlueArgb
import com.milknote.app.ui.theme.InkGreenArgb
import com.milknote.app.ui.theme.InkPurpleArgb
import com.milknote.app.ui.theme.InkRedArgb
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.PressureGamma
import com.milknote.ink.model.ToolConfig

internal val writingTools = listOf(InkTool.PEN, InkTool.BALL, InkTool.HIGHLIGHTER)

/** 书写工具各自独立的样式与色板;只随书写页 ViewModel 保留,不改笔迹数据。 */
data class WriterToolSettings(
    val currentTool: InkTool = InkTool.PEN,
    val configs: Map<InkTool, ToolConfig> = defaultToolConfigs,
    val palettes: Map<InkTool, List<Long>> = defaultToolPalettes,
    val eraserTemporary: Boolean = false,
) {
    val current: ToolConfig get() = configs.getValue(currentTool)
    val effective: ToolConfig get() = if (eraserTemporary) {
        configs.getValue(InkTool.ERASER).copy(baseWidthMm = 4f, pressureGamma = current.pressureGamma)
    } else current

    fun selectTool(tool: InkTool): WriterToolSettings {
        if (tool == InkTool.ERASER) return copy(eraserTemporary = true)
        val config = (configs[tool] ?: current).copy(tool = tool)
        return copy(currentTool = tool, configs = configs + (tool to config), eraserTemporary = false)
    }

    fun toggleEraser(): WriterToolSettings = copy(eraserTemporary = !eraserTemporary)

    fun selectColor(color: Long): WriterToolSettings {
        if (currentTool !in writingTools) return this
        return updateCurrent(current.copy(color = color))
    }

    fun selectWidth(mm: Float): WriterToolSettings {
        if (currentTool !in writingTools) return this
        return updateCurrent(current.copy(baseWidthMm = mm.coerceIn(0.2f, 3f)))
    }

    /** 自定义颜色写回当前笔的对应色板槽位,并同步当前墨色。 */
    fun updatePaletteColor(index: Int, color: Long): WriterToolSettings {
        if (currentTool !in writingTools) return this
        val palette = palettes.getValue(currentTool)
        if (index !in palette.indices) return this
        return copy(
            configs = configs + (currentTool to current.copy(color = color)),
            palettes = palettes + (currentTool to palette.toMutableList().apply { this[index] = color }),
            eraserTemporary = false,
        )
    }

    fun withPressureGamma(gamma: PressureGamma): WriterToolSettings = copy(
        configs = configs.mapValues { (_, config) -> config.copy(pressureGamma = gamma) },
    )

    private fun updateCurrent(config: ToolConfig): WriterToolSettings = copy(
        configs = configs + (currentTool to config),
        eraserTemporary = false,
    )
}

private val defaultToolConfigs = mapOf(
    InkTool.PEN to ToolConfig(tool = InkTool.PEN, color = InkBlackArgb),
    InkTool.BALL to ToolConfig(tool = InkTool.BALL, color = InkBlackArgb),
    InkTool.HIGHLIGHTER to ToolConfig(tool = InkTool.HIGHLIGHTER, color = HighlightYellowArgb),
    InkTool.ERASER to ToolConfig(tool = InkTool.ERASER),
)

private val defaultToolPalettes = mapOf(
    InkTool.PEN to listOf(InkBlackArgb, InkBlueArgb, InkRedArgb, InkGreenArgb),
    InkTool.BALL to listOf(InkBlackArgb, BallBlueArgb, InkRedArgb, InkPurpleArgb),
    InkTool.HIGHLIGHTER to listOf(HighlightYellowArgb, HighlightPinkArgb, HighlightGreenArgb, HighlightBlueArgb),
)
