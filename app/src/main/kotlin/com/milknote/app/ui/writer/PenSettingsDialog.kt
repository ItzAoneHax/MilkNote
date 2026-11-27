package com.milknote.app.ui.writer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonSize
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkPanelHeader
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.MilkSlider
import com.milknote.app.ui.common.MilkSwitch
import com.milknote.app.ui.theme.InkBlackArgb
import com.milknote.app.ui.theme.InkDark
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.PressureGamma
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 笔设置中央面板(2026-11 重设计):标准浮层头(笔图标 + 标题 + 关闭)+ 眉标分区
 * (笔迹预览 / 粗细 / 压感灵敏度 / 专属色板);橡皮分支 = 大小芯片 + 擦除内容开关列表。
 */
@Composable
internal fun PenSettingsDialog(
    tool: InkTool,
    settings: WriterToolSettings,
    onDismiss: () -> Unit,
    onWidth: (Float) -> Unit,
    onSelectColor: (Long) -> Unit,
    onColorDetail: (Int) -> Unit,
    onEraserWidth: (Float) -> Unit = {},
    onEraseTarget: (InkTool, Boolean) -> Unit = { _, _ -> },
    onAutoRevert: (Boolean) -> Unit = {},
    onGamma: (PressureGamma) -> Unit = {},
    presets: List<PenPreset> = emptyList(),
    onSavePreset: () -> Unit = {},
    onApplyPreset: (PenPreset) -> Unit = {},
    onClearPresets: () -> Unit = {},
) {
    if (tool == InkTool.ERASER) {
        MilkCenterPanel(
            title = "橡皮",
            onDismiss = onDismiss,
            maxWidth = 460.dp,
            icon = {
                val cs = MaterialTheme.colorScheme
                val dark = LocalMilkDark.current
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(MilkRadius.sm))
                        .background(cs.primary.copy(alpha = if (dark) 0.18f else 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    RealPenIcon(InkTool.ERASER, 0L, modifier = Modifier.size(19.dp))
                }
            },
            footer = {
                Spacer(Modifier.weight(1f))
                MilkButton(label = "完成", onClick = onDismiss)
            },
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).testTag("writer-panel-scroll")) {
                Spacer(Modifier.height(6.dp))
                MilkSectionLabel("橡皮大小", description = "按住擦过即整笔划删除")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2f to "小", 5f to "中", 9f to "大").forEach { (mm, label) ->
                        com.milknote.app.ui.common.MilkChip(
                            label = "$label · ${mm.toInt()} mm",
                            selected = abs(settings.eraserWidthMm - mm) < 0.1f,
                            onClick = { onEraserWidth(mm) },
                            small = true,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                MilkSectionLabel("擦除内容", description = "关闭后橡皮划过该类笔迹不再删除；胶带 / 图片 / 文字卡片不受橡皮影响")
                Spacer(Modifier.height(6.dp))
                val eraseGroups = listOf(
                    Triple("擦除画笔", listOf(InkTool.PEN, InkTool.BALL), "writer-eraser-target-ink"),
                    Triple("擦除荧光笔", listOf(InkTool.HIGHLIGHTER), "writer-eraser-target-highlighter"),
                    Triple("擦除图案笔", listOf(InkTool.DOODLE), "writer-eraser-target-doodle"),
                    Triple("擦除图形", listOf(
                        InkTool.LINE, InkTool.DASHED_LINE, InkTool.ARROW,
                        InkTool.RECT, InkTool.OVAL, InkTool.TRIANGLE, InkTool.STAR, InkTool.POLYGON,
                    ), "writer-eraser-target-shape"),
                )
                eraseGroups.forEachIndexed { index, (label, tools, tag) ->
                    val on = tools.any { it in settings.eraserTargets }
                    if (index > 0) PanelHairline()
                    PanelSwitchRow(
                        label = label,
                        checked = on,
                        tag = tag,
                        onChange = { enabled -> tools.forEach { onEraseTarget(it, enabled) } },
                    )
                }
                PanelHairline()
                PanelSwitchRow(
                    label = "自动切回画笔",
                    description = "擦完一笔后自动回到原书写笔（千本「自动取消」）",
                    checked = settings.eraserAutoRevert,
                    tag = "writer-eraser-auto-revert",
                    onChange = onAutoRevert,
                )
                Spacer(Modifier.height(MilkSpace.sm))
            }
        }
        return
    }
    val current = settings.current
    val title = when (tool) {
        InkTool.PEN -> "钢笔"
        InkTool.BALL -> "圆珠笔"
        InkTool.HIGHLIGHTER -> "荧光笔"
        InkTool.DOODLE -> "图案笔"
        InkTool.LASER -> "激光笔"
        else -> "笔"
    }
    WriterFloatingPanel(onDismiss = onDismiss, width = 440.dp, maxHeight = 660.dp) {
        Column(Modifier.fillMaxWidth()) {
            MilkPanelHeader(
                title = title,
                icon = {
                    val cs = MaterialTheme.colorScheme
                    val dark = LocalMilkDark.current
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(MilkRadius.sm))
                            .background(cs.primary.copy(alpha = if (dark) 0.18f else 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        RealPenIcon(tool, current.color, modifier = Modifier.size(19.dp))
                    }
                },
                onDismiss = onDismiss,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .testTag("writer-panel-scroll")
                    .padding(horizontal = 20.dp),
            ) {
                // 笔迹预览:恒亮纸面小样(DESIGN-V2 pop-pen)
                StrokePreview(tool, current.color, current.baseWidthMm)
                Spacer(Modifier.height(14.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("粗细", style = MilkText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("%.2f mm".format(current.baseWidthMm), style = MilkText.monoMeta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                MilkSlider(
                    value = current.baseWidthMm,
                    onValueChange = onWidth,
                    valueRange = 0.2f..3f,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.4f to "细", 0.7f to "中", 1.2f to "粗").forEach { (mm, label) ->
                        com.milknote.app.ui.common.MilkChip(
                            label = "$label · ${mm} mm",
                            selected = abs(current.baseWidthMm - mm) < 0.05f,
                            onClick = { onWidth(mm) },
                            small = true,
                        )
                    }
                }
                if (tool in writingTools) {
                    Spacer(Modifier.height(20.dp))
                    MilkSectionLabel("压感灵敏度", description = "灵敏 = 轻压即出粗线；柔和 = 重按才变粗")
                    Spacer(Modifier.height(8.dp))
                    MilkSegmented(
                        options = PressureGamma.entries,
                        selected = current.pressureGamma,
                        label = { it.label },
                        onSelect = onGamma,
                        modifier = Modifier.testTag("writer-pen-gamma"),
                        testTagFor = { "writer-pen-gamma-${it.name.lowercase()}" },
                    )
                }
                Spacer(Modifier.height(20.dp))
                MilkSectionLabel("专属色板", description = "点击选色；再次点击当前颜色可精细调色")
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    Modifier.fillMaxWidth().testTag("writer-pen-palette"),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(settings.palettes.getValue(tool), key = { index, _ -> "palette-$index" }) { index, color ->
                        InkColorDot(
                            color = color,
                            label = "颜色 ${index + 1}",
                            active = current.color == color,
                            onClick = {
                                if (current.color == color) onColorDetail(index) else onSelectColor(color)
                            },
                        )
                    }
                }
                if (tool in writingTools) {
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("笔盒", style = MilkText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (presets.isNotEmpty()) {
                            com.milknote.app.ui.common.MilkActionChip(label = "清空", onClick = onClearPresets, small = true)
                            Spacer(Modifier.width(6.dp))
                        }
                        com.milknote.app.ui.common.MilkActionChip(label = "+ 收藏", selected = true, onClick = onSavePreset, small = true)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (presets.isEmpty()) {
                        Text(
                            "把当前 工具 / 墨色 / 粗细 收藏起来，下次一键换笔",
                            style = MilkText.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    } else {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            presets.forEach { preset ->
                                com.milknote.app.ui.common.MilkChip(
                                    label = preset.name,
                                    selected = preset.tool == current.tool && preset.color == current.color &&
                                        abs(preset.widthMm - current.baseWidthMm) < 0.05f,
                                    onClick = { onApplyPreset(preset) },
                                    small = true,
                                    leading = {
                                        Box(
                                            Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(preset.color.toInt()))
                                                .border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MilkButton(label = "完成", onClick = onDismiss)
                }
                Spacer(Modifier.height(MilkSpace.lg))
            }
        }
    }
}

@Composable
private fun PanelHairline() {
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 0.dp),
    )
}

@Composable
private fun PanelSwitchRow(
    label: String,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MilkText.body, color = MaterialTheme.colorScheme.onSurface)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, style = MilkText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        MilkSwitch(
            checked = checked,
            onChange = onChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/** 笔迹预览:恒亮纸面小样(56dp,8dp 圆角 hairline),一条随粗细/墨色变化的曲线;荧光笔半透明 */
@Composable
private fun StrokePreview(tool: InkTool, color: Long, widthMm: Float, compact: Boolean = false) {
    val ink = Color(color.toInt())
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 48.dp else 56.dp)
            .clip(RoundedCornerShape(MilkRadius.sm))
            .background(Color.White)
            .border(1.dp, Color(0x1F18191B), RoundedCornerShape(MilkRadius.sm)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(if (compact) 40.dp else 48.dp).padding(horizontal = 20.dp)) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.62f)
                cubicTo(size.width * 0.18f, size.height * 0.06f, size.width * 0.32f, size.height * 0.94f, size.width * 0.52f, size.height * 0.42f)
                cubicTo(size.width * 0.68f, size.height * 0.02f, size.width * 0.80f, size.height * 0.80f, size.width, size.height * 0.28f)
            }
            drawPath(
                path = path,
                color = ink.copy(alpha = if (tool == InkTool.HIGHLIGHTER) 0.5f else 1f),
                style = Stroke(
                    width = (widthMm * if (tool == InkTool.HIGHLIGHTER) 4f else 1f).dp.toPx().coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                ),
            )
        }
    }
}

private enum class ColorPickerTab { GRID, SPECTRUM, SLIDERS }

/** 取色来源:决定这次改动回写哪些输入框,避免正在输入的内容被自身覆盖 */
private enum class ColorSource { GRID, SPECTRUM, HUE, OPACITY, RGB_SLIDER, RGB_TEXT, HEX, RESTORE }

/**
 * 取色面板(2026-11 升级):三页签(网格 / 色谱 / 滑块)+ 常驻不透明度行 + 底部精度条
 * (当前/原色对照 + HEX)。`current` 是唯一真源,所有改动都经 [emit] 统一回写;
 * 只有影响 RGB 的改动才重算 HSV —— 调不透明度不会再把色相/饱和度/明度打回原形。
 */
@Composable
internal fun ColorSettingsDialog(
    initialColor: Long,
    onDismiss: () -> Unit,
    onColorChange: (Long) -> Unit,
) {
    // 打开面板时的原色快照:编辑期间调色板条目会跟着变,「还原」必须回到打开时的颜色
    val originalColor = remember { initialColor }
    val initialHsv = remember { argbToHsv(originalColor) }
    var current by remember { mutableStateOf(originalColor) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var opacity by remember { mutableFloatStateOf(((originalColor ushr 24) and 0xFF) / 255f) }
    var tab by remember { mutableStateOf(ColorPickerTab.SPECTRUM) }
    var hexInput by remember { mutableStateOf(formatHexRgb(originalColor)) }
    var redText by remember { mutableStateOf(((originalColor shr 16) and 0xFF).toString()) }
    var greenText by remember { mutableStateOf(((originalColor shr 8) and 0xFF).toString()) }
    var blueText by remember { mutableStateOf((originalColor and 0xFF).toString()) }
    // 初次组合也会收到 hasFocus=false,只有「真正失焦」才归一,避免开面板就回写一次颜色
    var hexHadFocus by remember { mutableStateOf(false) }

    fun alphaByte(): Int = (opacity * 255f).roundToInt().coerceIn(0, 255)

    fun syncChannelTexts(argb: Long) {
        val (r, g, b) = argbToRgb(argb)
        redText = r.toString()
        greenText = g.toString()
        blueText = b.toString()
    }

    fun emit(source: ColorSource, argb: Long, syncHsv: Boolean = true) {
        if (syncHsv) {
            val hsv = argbToHsv(argb)
            hue = hsv[0]
            saturation = hsv[1]
            value = hsv[2]
        }
        current = argb
        opacity = ((argb ushr 24) and 0xFF) / 255f
        if (source != ColorSource.RGB_TEXT) syncChannelTexts(argb)
        if (source != ColorSource.HEX) hexInput = formatHexRgb(argb)
        onColorChange(argb)
    }

    fun restore() = emit(ColorSource.RESTORE, originalColor)

    fun applyHexInput(raw: String) {
        val cleaned = raw.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.take(8).uppercase()
        hexInput = cleaned
        if (cleaned.length == 6 || cleaned.length == 8) {
            parseHexColor(cleaned, alphaByte())?.let { emit(ColorSource.HEX, it) }
        }
    }

    // 输入结束(回车 / 失焦):补全三位简写,并把显示归一到六位;颜色未变则不回写
    fun normalizeHex() {
        parseHexColor(hexInput, alphaByte())?.let { if (it != current) emit(ColorSource.HEX, it) }
        hexInput = formatHexRgb(current)
    }

    MilkCenterPanel(
        title = "取色",
        onDismiss = onDismiss,
        maxWidth = 520.dp,
        maxHeight = 760.dp,
        footer = {
            MilkButton(
                label = "还原",
                onClick = ::restore,
                variant = MilkButtonVariant.TEXT,
                size = MilkButtonSize.SMALL,
                enabled = current != originalColor,
            )
            Spacer(Modifier.weight(1f))
            MilkButton(label = "完成", onClick = onDismiss)
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .testTag("writer-panel-scroll"),
        ) {
            Spacer(Modifier.height(10.dp))
            MilkSegmented(
                options = ColorPickerTab.entries,
                selected = tab,
                label = {
                    when (it) {
                        ColorPickerTab.GRID -> "网格"
                        ColorPickerTab.SPECTRUM -> "色谱"
                        ColorPickerTab.SLIDERS -> "滑块"
                    }
                },
                onSelect = { tab = it },
                testTagFor = { "color-picker-tab-${it.name.lowercase()}" },
            )
            Spacer(Modifier.height(16.dp))
            when (tab) {
                ColorPickerTab.GRID -> ColorGrid(
                    current = current,
                    onSelect = { cell ->
                        emit(ColorSource.GRID, (alphaByte().toLong() shl 24) or (cell and 0xFFFFFF))
                    },
                )

                ColorPickerTab.SPECTRUM -> Column(Modifier.fillMaxWidth()) {
                    SpectrumField(
                        hue = hue,
                        saturation = saturation,
                        value = value,
                        onChange = { s, v ->
                            saturation = s
                            value = v
                            emit(ColorSource.SPECTRUM, hsvToArgb(hue, s, v, alphaByte()), syncHsv = false)
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("色相", style = MilkText.label, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${hue.roundToInt()}°",
                            style = MilkText.monoMeta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    MilkSlider(
                        value = hue / 360f,
                        onValueChange = {
                            hue = it * 360f
                            emit(ColorSource.HUE, hsvToArgb(hue, saturation, value, alphaByte()), syncHsv = false)
                        },
                        trackBrush = Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 1f, 1f) }),
                    )
                }

                ColorPickerTab.SLIDERS -> {
                    val (r, g, b) = argbToRgb(current)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RgbChannelRow(
                            label = "R",
                            text = redText,
                            channel = r,
                            trackBrush = Brush.horizontalGradient(
                                listOf(Color(rgbToArgb(0, g, b).toInt()), Color(rgbToArgb(255, g, b).toInt())),
                            ),
                            testTag = "color-picker-r",
                            onTextChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(3)
                                redText = digits
                                parseChannelByte(digits)?.let {
                                    emit(ColorSource.RGB_TEXT, rgbToArgb(it, g, b, alphaByte()))
                                }
                            },
                            onChannelChange = { emit(ColorSource.RGB_SLIDER, rgbToArgb(it, g, b, alphaByte())) },
                            onNormalize = { syncChannelTexts(current) },
                        )
                        RgbChannelRow(
                            label = "G",
                            text = greenText,
                            channel = g,
                            trackBrush = Brush.horizontalGradient(
                                listOf(Color(rgbToArgb(r, 0, b).toInt()), Color(rgbToArgb(r, 255, b).toInt())),
                            ),
                            testTag = "color-picker-g",
                            onTextChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(3)
                                greenText = digits
                                parseChannelByte(digits)?.let {
                                    emit(ColorSource.RGB_TEXT, rgbToArgb(r, it, b, alphaByte()))
                                }
                            },
                            onChannelChange = { emit(ColorSource.RGB_SLIDER, rgbToArgb(r, it, b, alphaByte())) },
                            onNormalize = { syncChannelTexts(current) },
                        )
                        RgbChannelRow(
                            label = "B",
                            text = blueText,
                            channel = b,
                            trackBrush = Brush.horizontalGradient(
                                listOf(Color(rgbToArgb(r, g, 0).toInt()), Color(rgbToArgb(r, g, 255).toInt())),
                            ),
                            testTag = "color-picker-b",
                            onTextChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(3)
                                blueText = digits
                                parseChannelByte(digits)?.let {
                                    emit(ColorSource.RGB_TEXT, rgbToArgb(r, g, it, alphaByte()))
                                }
                            },
                            onChannelChange = { emit(ColorSource.RGB_SLIDER, rgbToArgb(r, g, it, alphaByte())) },
                            onNormalize = { syncChannelTexts(current) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("不透明度", style = MilkText.label, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text(
                    "${(opacity * 100).roundToInt()}%",
                    style = MilkText.monoMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth()) {
                // 棋盘格垫在滑轨正后方,透明段如实反映"这里没有颜色"
                Checkerboard(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .align(Alignment.Center),
                    shape = RoundedCornerShape(3.dp),
                    cell = 3.dp,
                )
                MilkSlider(
                    value = opacity,
                    onValueChange = {
                        opacity = it
                        val alpha = (it * 255f).roundToInt().coerceIn(0, 255).toLong()
                        emit(ColorSource.OPACITY, (alpha shl 24) or (current and 0xFFFFFF), syncHsv = false)
                    },
                    trackBrush = Brush.horizontalGradient(
                        listOf(Color(current.toInt()).copy(alpha = 0f), Color(current.toInt()).copy(alpha = 1f)),
                    ),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SplitSwatch(current = current, initial = originalColor, onRestore = ::restore)
                MilkTextField(
                    value = hexInput,
                    onValueChange = ::applyHexInput,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("color-picker-hex")
                        .onFocusChanged {
                            if (hexHadFocus && !it.hasFocus) normalizeHex()
                            hexHadFocus = it.hasFocus
                        },
                    placeholder = "RRGGBB",
                    mono = true,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                    onDone = ::normalizeHex,
                    textStyle = MilkText.monoBody,
                    leading = {
                        Text(
                            "#",
                            style = MilkText.monoMeta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            Spacer(Modifier.height(MilkSpace.lg))
        }
    }
}

/** 2D 饱和度(S,横向)× 明度(V,纵向)取色画布:点按与拖动都即时生效 */
@Composable
private fun SpectrumField(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
) {
    val shape = RoundedCornerShape(MilkRadius.md)
    Box(
        Modifier
            .fillMaxWidth()
            .height(188.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (w > 0f && h > 0f) {
                        down.consume()
                        onChange((down.position.x / w).coerceIn(0f, 1f), 1f - (down.position.y / h).coerceIn(0f, 1f))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            onChange(
                                (change.position.x / w).coerceIn(0f, 1f),
                                1f - (change.position.y / h).coerceIn(0f, 1f),
                            )
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        }
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(saturation * size.width, (1f - value) * size.height)
            val radius = 10.dp.toPx()
            drawCircle(Color(hsvToArgb(hue, saturation, value).toInt()), radius = radius, center = center)
            drawCircle(Color.White, radius = radius, center = center, style = Stroke(width = 2.5.dp.toPx()))
            drawCircle(
                Color.Black.copy(alpha = 0.28f),
                radius = radius + 1.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/** 单通道精度行:通道名 + 动态渐变滑条 + 三位数字输入(输入与滑条互为镜像) */
@Composable
private fun RgbChannelRow(
    label: String,
    text: String,
    channel: Int,
    trackBrush: Brush,
    testTag: String,
    onTextChange: (String) -> Unit,
    onChannelChange: (Int) -> Unit,
    onNormalize: () -> Unit,
) {
    // 初次组合也会收到 hasFocus=false;只在真正失焦时归一,避免开面板就改写输入框
    var hadFocus by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            style = MilkText.label,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(18.dp),
        )
        Box(Modifier.weight(1f)) {
            MilkSlider(
                value = channel / 255f,
                onValueChange = { onChannelChange((it * 255f).roundToInt().coerceIn(0, 255)) },
                trackBrush = trackBrush,
            )
        }
        MilkTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .width(68.dp)
                .testTag(testTag)
                .onFocusChanged {
                    if (hadFocus && !it.hasFocus) onNormalize()
                    hadFocus = it.hasFocus
                },
            mono = true,
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            onDone = onNormalize,
            textStyle = MilkText.monoBody,
        )
    }
}

/** 当前色 / 原色左右对照色块(压在棋盘格上,半透明色如实显示);点按还原原色 */
@Composable
private fun SplitSwatch(current: Long, initial: Long, onRestore: () -> Unit) {
    val shape = RoundedCornerShape(MilkRadius.md)
    Box(
        Modifier
            .width(96.dp)
            .height(42.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onRestore)
            .semantics { contentDescription = "还原原色" },
    ) {
        Checkerboard(Modifier.matchParentSize(), shape = shape, cell = 5.dp)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().background(Color(current.toInt())))
            Box(Modifier.weight(1f).fillMaxHeight().background(Color(initial.toInt())))
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.18f)),
        )
    }
}

/** 透明色背景棋盘格(白 / 浅灰双色),用于不透明度轨道与对照色块 */
@Composable
private fun Checkerboard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MilkRadius.md),
    cell: Dp = 4.dp,
) {
    Canvas(modifier.clip(shape)) {
        drawRect(Color.White)
        val step = cell.toPx()
        if (step > 0f) {
            var row = 0
            var y = 0f
            while (y < size.height) {
                var x = if (row % 2 == 0) step else 0f
                while (x < size.width) {
                    drawRect(Color(0xFFDCDCDC), topLeft = Offset(x, y), size = Size(step, step))
                    x += step * 2
                }
                y += step
                row++
            }
        }
    }
}

/** 12 色相 × 8 明暗行 + 灰阶列;选中格 = 深色外圈 + 白色内圈,深浅格上都清晰 */
@Composable
private fun ColorGrid(current: Long, onSelect: (Long) -> Unit) {
    val hues = (0 until 12).map { it * 30f }
    val styles = listOf(
        0.18f to 1f, 0.32f to 1f, 0.48f to 1f, 0.68f to 1f,
        0.88f to 0.96f, 1f to 0.82f, 1f to 0.64f, 1f to 0.43f,
    )
    val grays = listOf(0xFFF5F5F5, 0xFFE7E7E7, 0xFFCECECE, 0xFFAFAFAF, 0xFF858585, 0xFF585858, 0xFF323232, 0xFF050505)
    Column(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(MilkRadius.md))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        styles.forEachIndexed { row, (saturation, value) ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                hues.forEach { hue ->
                    val argb = hsvToArgb(hue, saturation, value)
                    ColorGridCell(argb, current, onSelect, Modifier.weight(1f).fillMaxHeight())
                }
                ColorGridCell(grays[row], current, onSelect, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun ColorGridCell(
    argb: Long,
    current: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier,
) {
    val selected = (current and 0xFFFFFF) == (argb and 0xFFFFFF)
    val ringShape = RoundedCornerShape(MilkRadius.xs)
    Box(
        modifier
            .background(Color(argb.toInt()))
            .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { onSelect(argb) })
            .then(
                if (selected) {
                    Modifier
                        .border(1.5.dp, Color.Black.copy(alpha = 0.55f), ringShape)
                        .padding(1.5.dp)
                        .border(2.dp, Color.White, ringShape)
                } else {
                    Modifier
                },
            ),
    )
}
