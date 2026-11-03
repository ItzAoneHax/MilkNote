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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.milknote.app.ui.common.innerHighlight
import com.milknote.app.ui.common.paintedShadow
import com.milknote.app.ui.common.softShadow
import com.milknote.ink.model.InkTool
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun PenSettingsDialog(
    tool: InkTool,
    settings: WriterToolSettings,
    onDismiss: () -> Unit,
    onWidth: (Float) -> Unit,
    onSelectColor: (Long) -> Unit,
    onColorDetail: (Int) -> Unit,
) {
    val current = settings.current
    val title = when (tool) {
        InkTool.PEN -> "钢笔详细设定"
        InkTool.BALL -> "圆珠笔详细设定"
        InkTool.HIGHLIGHTER -> "荧光笔详细设定"
        else -> "笔详细设定"
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(430.dp)
                .softShadow(14.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .innerHighlight()
                .paintedShadow(RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RealPenIcon(tool, current.color, modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            StrokePreview(tool, current.color, current.baseWidthMm)
            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("笔迹粗细", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    "%.2f mm".format(current.baseWidthMm),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = current.baseWidthMm,
                onValueChange = onWidth,
                valueRange = 0.2f..3f,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.4f, 0.7f, 1.2f).forEach { mm ->
                    val active = abs(current.baseWidthMm - mm) < 0.05f
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                            .clickable { onWidth(mm) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            "$mm mm",
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                            color = if (active) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("专属色板", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "点击选色，再次点击当前颜色可精细调色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                settings.palettes.getValue(tool).forEachIndexed { index, color ->
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
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("完成") }
            }
        }
    }
}

@Composable
private fun StrokePreview(tool: InkTool, color: Long, widthMm: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 22.dp)) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.68f)
                cubicTo(size.width * 0.18f, size.height * 0.08f, size.width * 0.32f, size.height * 0.94f, size.width * 0.52f, size.height * 0.42f)
                cubicTo(size.width * 0.68f, size.height * 0.02f, size.width * 0.80f, size.height * 0.80f, size.width, size.height * 0.28f)
            }
            drawPath(
                path = path,
                color = Color(color.toInt()).let { it.copy(alpha = it.alpha * if (tool == InkTool.HIGHLIGHTER) 0.45f else 1f) },
                style = Stroke(
                    width = (widthMm * if (tool == InkTool.HIGHLIGHTER) 4f else 1f).dp.toPx().coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                ),
            )
        }
    }
}

private enum class ColorPickerTab { GRID, SLIDERS }

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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(480.dp)
                .softShadow(18.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF242424))
                .border(1.dp, Color(0xFF3B3B3B), RoundedCornerShape(28.dp))
                .paintedShadow(RoundedCornerShape(28.dp))
                .padding(24.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    "颜色",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3A3A3A))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text("×", color = Color.White, fontSize = 22.sp) }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF383838))
                    .padding(4.dp),
            ) {
                ColorPickerTab.entries.forEach { item ->
                    val selected = tab == item
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (selected) Color(0xFF626262) else Color.Transparent)
                            .clickable { tab = item }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (item == ColorPickerTab.GRID) "网格取色" else "滑块取色",
                            color = if (selected) Color.White else Color(0xFFD0D0D0),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (tab == ColorPickerTab.GRID) {
                Text("色相", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(10.dp))
                ColorGrid(current = current, onSelect = { updateColor(it) })
            } else {
                Column(Modifier.fillMaxWidth().height(264.dp), verticalArrangement = Arrangement.SpaceEvenly) {
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
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("不透明度", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text("${(opacity * 100).roundToInt()}%", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF151515))
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(current.toInt()).copy(alpha = 1f)))),
            ) {
                Slider(
                    value = opacity,
                    onValueChange = {
                        opacity = it
                        updateColor(current)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(current.toInt()))
                        .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(13.dp)),
                )
                Spacer(Modifier.width(18.dp))
                Box(
                    Modifier
                        .size(25.dp)
                        .clip(CircleShape)
                        .background(Color(initialColor.toInt()))
                        .border(1.dp, Color(0x66FFFFFF), CircleShape),
                )
            }
        }
    }
}

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
            .height(256.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        styles.forEachIndexed { row, (saturation, value) ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
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
            .background(Color(argb.toInt()))
            .border(if (selected) 2.dp else 0.dp, if (selected) Color.White else Color.Transparent)
            .clickable { onSelect(argb) },
    )
}

@Composable
private fun GradientColorSlider(
    label: String,
    brush: Brush,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(brush),
    ) {
        Slider(
            value = value,
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
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
