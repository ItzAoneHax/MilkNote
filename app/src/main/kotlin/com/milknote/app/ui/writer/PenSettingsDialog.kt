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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkPanelHeader
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.MilkSlider
import com.milknote.app.ui.common.MilkSwitch
import com.milknote.app.ui.common.MilkKeyCap
import com.milknote.app.ui.theme.InkBlackArgb
import com.milknote.app.ui.theme.InkDark
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.Mono
import com.milknote.app.ui.theme.MonoMeta
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.PressureGamma
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 笔设置中央面板(2026-11 重设计):标准浮层头(笔图标 + 标题 + 关闭)+ 眉标分区
 * (笔迹预览 / 粗细 / 压感灵敏度 / 专属色板);橡皮分支 = 大小卡 + 擦除内容开关列表。
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
) {
    if (tool == InkTool.ERASER) {
        MilkCenterPanel(
            title = "橡皮",
            subtitle = "整笔划擦除；再次点击橡皮可打开此面板",
            onDismiss = onDismiss,
            maxWidth = 460.dp,
            icon = { RealPenIcon(InkTool.ERASER, 0L, modifier = Modifier.size(36.dp)) },
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).testTag("writer-panel-scroll")) {
                Spacer(Modifier.height(12.dp))
                MilkSectionLabel("橡皮大小", description = "按住擦过即整笔划删除")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2f to "小", 5f to "中", 9f to "大").forEach { (mm, label) ->
                        EraserSizeCard(
                            mm = mm,
                            label = label,
                            active = abs(settings.eraserWidthMm - mm) < 0.1f,
                            onClick = { onEraserWidth(mm) },
                            modifier = Modifier.weight(1f),
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
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MilkButton("完成", onClick = onDismiss)
                }
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
                subtitle = "调整会立即生效，并记在这支笔上",
                icon = {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        RealPenIcon(tool, current.color, modifier = Modifier.size(34.dp))
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
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(10.dp))
                StrokePreview(tool, current.color, current.baseWidthMm)
                Spacer(Modifier.height(22.dp))

                MilkSectionLabel("笔迹粗细")
                Spacer(Modifier.height(10.dp))
                MilkSlider(
                    value = current.baseWidthMm,
                    onValueChange = onWidth,
                    valueRange = 0.2f..3f,
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.4f to "细", 0.7f to "中", 1.2f to "粗").forEach { (mm, label) ->
                        PenWidthPreset(
                            mm = mm,
                            label = label,
                            selected = abs(current.baseWidthMm - mm) < 0.05f,
                            onClick = { onWidth(mm) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (tool in writingTools) {
                    Spacer(Modifier.height(24.dp))
                    MilkSectionLabel("压感灵敏度", description = "灵敏 = 轻压即出粗线；柔和 = 重按才变粗")
                    Spacer(Modifier.height(10.dp))
                    MilkSegmented(
                        options = PressureGamma.entries,
                        selected = current.pressureGamma,
                        label = { it.label },
                        onSelect = onGamma,
                        modifier = Modifier.testTag("writer-pen-gamma"),
                        testTagFor = { "writer-pen-gamma-${it.name.lowercase()}" },
                    )
                }
                Spacer(Modifier.height(24.dp))
                MilkSectionLabel("专属色板", description = "点击选色；再次点击当前颜色可精细调色")
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    MilkKeyCap("Esc")
                    Spacer(Modifier.width(6.dp))
                    Text("关闭", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    MilkButton(label = "完成", onClick = onDismiss)
                }
                Spacer(Modifier.height(20.dp))
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
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** 橡皮大小卡:圆点直径按 mm 比例放大 + 标签;选中 = 淡蓝底 + 蓝描边 */
@Composable
private fun EraserSizeCard(mm: Float, label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) cs.primary.copy(alpha = 0.10f) else cs.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, if (active) cs.primary else cs.outline, RoundedCornerShape(14.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size((mm * 3.2f).dp)
                .clip(CircleShape)
                .background(if (active) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.7f)),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.Medium else null,
            color = if (active) cs.primary else cs.onSurface,
        )
        Text(
            "${mm.toInt()} mm",
            style = MonoMeta,
            color = cs.onSurfaceVariant,
        )
    }
}

/** 粗细预设:圆点直径示意 + 标签 + mm;选中 = 淡蓝底蓝描边 */
@Composable
private fun PenWidthPreset(mm: Float, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) cs.primary.copy(alpha = 0.10f) else cs.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, if (selected) cs.primary else cs.outline.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size((3f + mm * 7f).dp)
                    .clip(CircleShape)
                    .background(if (selected) cs.primary else cs.onSurface),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Medium else null,
                color = if (selected) cs.primary else cs.onSurface,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text("%.1f mm".format(mm), style = MonoMeta, color = cs.onSurfaceVariant)
    }
}

/** 笔迹预览:纸面色底 + 点阵纹 + 一条随粗细变化的曲线(荧光笔半透明) */
@Composable
private fun StrokePreview(tool: InkTool, color: Long, widthMm: Float, compact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val ink = Color(color.toInt())
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 64.dp else 96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, cs.outline.copy(alpha = 0.7f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 12.dp.toPx()
            var y = step
            while (y < size.height) {
                var x = step
                while (x < size.width) {
                    drawCircle(cs.onSurfaceVariant.copy(alpha = 0.18f), radius = 0.8f, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        val previewColor = if (LocalMilkDark.current && color == InkBlackArgb) InkDark else ink
        Canvas(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp)) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.68f)
                cubicTo(size.width * 0.18f, size.height * 0.08f, size.width * 0.32f, size.height * 0.94f, size.width * 0.52f, size.height * 0.42f)
                cubicTo(size.width * 0.68f, size.height * 0.02f, size.width * 0.80f, size.height * 0.80f, size.width, size.height * 0.28f)
            }
            drawPath(
                path = path,
                color = previewColor.copy(alpha = previewColor.alpha * if (tool == InkTool.HIGHLIGHTER) 0.45f else 1f),
                style = Stroke(
                    width = (widthMm * if (tool == InkTool.HIGHLIGHTER) 4f else 1f).dp.toPx().coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                ),
            )
        }
        Text(
            "%.2f mm".format(widthMm),
            style = MonoMeta,
            color = cs.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
        )
    }
}

private enum class ColorPickerTab { GRID, SLIDERS }

/** 颜色精细面板:网格 / 滑块两页签(分段控件),不透明度滑条,当前/原色对照 */
@Composable
internal fun ColorSettingsDialog(
    initialColor: Long,
    onDismiss: () -> Unit,
    onColorChange: (Long) -> Unit,
) {
    val initialHsv = remember { argbToHsv(initialColor) }
    var current by remember { mutableStateOf(initialColor) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var opacity by remember { mutableFloatStateOf(((initialColor ushr 24) and 0xFF) / 255f) }
    var tab by remember { mutableStateOf(ColorPickerTab.GRID) }

    fun updateColor(rgb: Long, syncHsv: Boolean = true) {
        if (syncHsv) {
            val hsv = argbToHsv(rgb)
            hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
        }
        val alpha = (opacity * 255f).roundToInt().coerceIn(0, 255).toLong()
        current = (alpha shl 24) or (rgb and 0xFFFFFF)
        onColorChange(current)
    }

    MilkCenterPanel(title = "颜色", subtitle = "网格快速取色，或用滑块精细调整", onDismiss = onDismiss, maxWidth = 500.dp, maxHeight = 720.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).testTag("writer-panel-scroll")) {
            Spacer(Modifier.height(10.dp))
            MilkSegmented(
                options = ColorPickerTab.entries,
                selected = tab,
                label = { if (it == ColorPickerTab.GRID) "网格取色" else "滑块取色" },
                onSelect = { tab = it },
            )
            Spacer(Modifier.height(18.dp))
            if (tab == ColorPickerTab.GRID) {
                MilkSectionLabel("色相", description = "按行由浅到深，末列为灰阶")
                Spacer(Modifier.height(10.dp))
                ColorGrid(current = current, onSelect = { updateColor(it) })
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    GradientColorSlider(
                        label = "色相",
                        brush = Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 1f, 1f) }),
                        value = hue / 360f,
                        onChange = {
                            hue = it * 360f
                            updateColor(Color.hsv(hue, saturation, value).toArgb().toLong() and 0xFFFFFFFFL, false)
                        },
                    )
                    GradientColorSlider(
                        label = "饱和度",
                        brush = Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, value))),
                        value = saturation,
                        onChange = {
                            saturation = it
                            updateColor(Color.hsv(hue, saturation, value).toArgb().toLong() and 0xFFFFFFFFL, false)
                        },
                    )
                    GradientColorSlider(
                        label = "明暗",
                        brush = Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, saturation, 1f))),
                        value = value,
                        onChange = {
                            value = it
                            updateColor(Color.hsv(hue, saturation, value).toArgb().toLong() and 0xFFFFFFFFL, false)
                        },
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("不透明度", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text("${(opacity * 100).roundToInt()}%", style = MonoMeta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            MilkSlider(
                value = opacity,
                onValueChange = {
                    opacity = it
                    updateColor(current)
                },
                trackBrush = Brush.horizontalGradient(listOf(Color.Transparent, Color(current.toInt()).copy(alpha = 1f))),
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(current.toInt()))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("当前", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(initialColor.toInt()))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("原色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Text("#%06X".format(0xFFFFFFL and current), style = MonoMeta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MilkButton(label = "完成", onClick = onDismiss)
            }
        }
    }
}

/** 12 色相 × 8 明暗行 + 灰阶列;格间 2dp 缝,选中格白描边 + 外圈 */
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
            .height(196.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        styles.forEachIndexed { row, (saturation, value) ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                hues.forEach { hue ->
                    val argb = Color.hsv(hue, saturation, value).toArgb().toLong() and 0xFFFFFFFFL
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
    Box(
        modifier
            .fillMaxHeight()
            .background(Color(argb.toInt()))
            .then(if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp)) else Modifier)
            .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { onSelect(argb) }),
    )
}

@Composable
private fun GradientColorSlider(
    label: String,
    brush: Brush,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        MilkSlider(
            value = value,
            onValueChange = onChange,
            trackBrush = brush,
        )
    }
}

private fun argbToHsv(color: Long): FloatArray {
    val r = ((color shr 16) and 0xFF) / 255f
    val g = ((color shr 8) and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    var hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    if (hue < 0f) hue += 360f
    return floatArrayOf(hue, if (max == 0f) 0f else delta / max, max)
}
