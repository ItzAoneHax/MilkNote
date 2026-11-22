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

internal val writingTools = listOf(InkTool.PEN, InkTool.BALL, InkTool.HIGHLIGHTER, InkTool.DOODLE)

/** 书写工具各自独立的样式与色板;只随书写页 ViewModel 保留,不改笔迹数据。 */
data class WriterToolSettings(
    val currentTool: InkTool = InkTool.PEN,
    val configs: Map<InkTool, ToolConfig> = defaultToolConfigs,
    val palettes: Map<InkTool, List<Long>> = defaultToolPalettes,
    val eraserTemporary: Boolean = false,
    val eraserWidthMm: Float = 4f,
    val eraserTargets: Set<InkTool> = defaultEraseTargets,
    val eraserAutoRevert: Boolean = false,
    /** 单笔压感档覆盖(千本「笔尖/压感灵敏度」);缺省用全局设置档 */
    val gammaOverrides: Map<InkTool, PressureGamma> = emptyMap(),
) {
    val current: ToolConfig get() = configs.getValue(currentTool).let { config ->
        gammaOverrides[currentTool]?.let { config.copy(pressureGamma = it) } ?: config
    }
    val effective: ToolConfig get() = if (eraserTemporary) {
        configs.getValue(InkTool.ERASER).copy(baseWidthMm = eraserWidthMm, pressureGamma = current.pressureGamma)
    } else current

    fun selectTool(tool: InkTool): WriterToolSettings {
        if (tool == InkTool.ERASER) return copy(eraserTemporary = true)
        val config = (configs[tool] ?: current).copy(tool = tool)
        return copy(currentTool = tool, configs = configs + (tool to config), eraserTemporary = false)
    }

    fun toggleEraser(): WriterToolSettings = copy(eraserTemporary = !eraserTemporary)

    fun selectEraserWidth(mm: Float): WriterToolSettings = copy(
        eraserWidthMm = mm.coerceIn(2f, 12f),
        eraserTemporary = true,
    )

    /** 橡皮内容开关(千本 P019:擦除画笔/荧光笔/涂鸦笔…);不可关闭最后一类,避免橡皮失效难排查 */
    fun setEraserTarget(tool: InkTool, enabled: Boolean): WriterToolSettings {
        val updated = (if (enabled) eraserTargets + tool else eraserTargets - tool)
        if (updated.isEmpty()) return this
        return copy(eraserTargets = updated)
    }

    fun setEraserAutoRevert(enabled: Boolean): WriterToolSettings = copy(eraserAutoRevert = enabled)

    /** 当前笔的压感档覆盖(千本钢笔「笔尖」/画笔「压感灵敏度」);LASER 无笔锋不响应 */
    fun selectPressureGamma(gamma: PressureGamma): WriterToolSettings {
        if (currentTool !in writingTools) return this
        if (gammaOverrides[currentTool] == gamma) return this
        return copy(gammaOverrides = gammaOverrides + (currentTool to gamma))
    }

    fun selectColor(color: Long): WriterToolSettings {
        if (currentTool !in writingTools && currentTool != InkTool.LASER) return this
        return updateCurrent(current.copy(color = color))
    }

    fun selectWidth(mm: Float): WriterToolSettings {
        if (currentTool !in writingTools && currentTool != InkTool.LASER) return this
        return updateCurrent(current.copy(baseWidthMm = mm.coerceIn(0.2f, 3f)))
    }

    /** 自定义颜色写回当前笔的对应色板槽位,并同步当前墨色。 */
    fun updatePaletteColor(index: Int, color: Long): WriterToolSettings {
        if (currentTool !in writingTools && currentTool != InkTool.LASER) return this
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
    InkTool.DOODLE to ToolConfig(tool = InkTool.DOODLE, color = InkPurpleArgb, baseWidthMm = 2.2f),
    InkTool.LASER to ToolConfig(tool = InkTool.LASER, color = InkRedArgb, baseWidthMm = 0.9f),
    InkTool.ERASER to ToolConfig(tool = InkTool.ERASER),
)

/** 橡皮默认可擦全部笔迹类(画笔/圆珠/荧光/图案/图形);胶带/图片/文本卡不走橡皮(千本胶带擦除为有意差异) */
private val defaultEraseTargets: Set<InkTool> = setOf(
    InkTool.PEN, InkTool.BALL, InkTool.HIGHLIGHTER, InkTool.DOODLE,
    InkTool.LINE, InkTool.DASHED_LINE, InkTool.ARROW,
    InkTool.RECT, InkTool.OVAL, InkTool.TRIANGLE, InkTool.STAR, InkTool.POLYGON,
)

private val defaultToolPalettes = mapOf(
    InkTool.PEN to listOf(
        InkBlackArgb, 0xFFF44336, 0xFFFFB300, 0xFF2F80ED, 0xFF4CAF50,
        InkPurpleArgb, 0xFFD98E04, 0xFF858585,
    ),
    InkTool.BALL to listOf(
        InkBlackArgb, BallBlueArgb, InkRedArgb, InkPurpleArgb,
        InkGreenArgb, 0xFFD98E04, 0xFF2C8CC8, 0xFF858585,
    ),
    InkTool.HIGHLIGHTER to listOf(
        HighlightYellowArgb, HighlightPinkArgb, HighlightGreenArgb, HighlightBlueArgb,
        0xFF7C5CE0, 0xFFD98E04, 0xFF2C8CC8, 0xFF858585,
    ),
    InkTool.DOODLE to listOf(
        InkPurpleArgb, InkBlueArgb, InkRedArgb, InkGreenArgb,
        0xFFD98E04, 0xFF2C8CC8, InkBlackArgb, 0xFF858585,
    ),
    InkTool.LASER to listOf(
        InkRedArgb, InkGreenArgb, InkBlueArgb, InkPurpleArgb,
        0xFFD98E04, 0xFF2C8CC8, InkBlackArgb, 0xFF858585,
    ),
)
