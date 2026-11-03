package com.milknote.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.theme.PillShape

/** 模态表面统一配方:L5 20dp 圆角、16dp 阴影、主题 surface 与 hairline。 */
@Composable
fun Modifier.milkDialogSurface(): Modifier = this
    .softShadow(16.dp, RoundedCornerShape(20.dp))
    .clip(RoundedCornerShape(20.dp))
    .background(MaterialTheme.colorScheme.surface)
    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
    .innerHighlight()
    .paintedShadow(RoundedCornerShape(20.dp))

enum class MilkButtonVariant { PRIMARY, SECONDARY, TEXT }

/** 统一文字按钮:48dp 高、胶囊轮廓;Primary 支持忙碌指示并阻止重复点击。 */
@Composable
fun MilkButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MilkButtonVariant = MilkButtonVariant.PRIMARY,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val shadow = if (variant == MilkButtonVariant.PRIMARY) Modifier.softShadow(5.dp, PillShape) else Modifier
    val background = when (variant) {
        MilkButtonVariant.PRIMARY -> Modifier.background(accentBrush())
        MilkButtonVariant.SECONDARY -> Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        MilkButtonVariant.TEXT -> Modifier
    }
    val border = if (variant == MilkButtonVariant.SECONDARY) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, PillShape)
    } else {
        Modifier
    }
    val contentColor = when (variant) {
        MilkButtonVariant.PRIMARY -> Color.White
        MilkButtonVariant.SECONDARY -> MaterialTheme.colorScheme.onSurface
        MilkButtonVariant.TEXT -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier
            .alpha(if (enabled || busy) 1f else 0.45f)
            .then(shadow)
            .clip(PillShape)
            .then(background)
            .then(border)
            .softClickable(enabled = enabled && !busy, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 统一滑条:6dp 轨道 + 18dp 圆形拇指。传入 [trackBrush] 时轨道显示完整渐变，
 * 否则显示 primary 已选区与 surfaceVariant 剩余区。拖动仍由 Material3 Slider 负责。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilkSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    trackBrush: Brush? = null,
    enabled: Boolean = true,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val thumbColor = if (trackBrush == null) activeColor else Color.White
    val thumbBorder = if (trackBrush == null) MaterialTheme.colorScheme.surface else Color.Black.copy(alpha = 0.22f)
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f),
        enabled = enabled,
        valueRange = valueRange,
        thumb = {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(18.dp)
                    .background(thumbColor, CircleShape)
                    .border(1.dp, thumbBorder, CircleShape),
            )
        },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val radius = size.height / 2f
                if (trackBrush != null) {
                    drawRoundRect(trackBrush, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius))
                } else {
                    drawRoundRect(trackColor, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius))
                    val range = state.valueRange
                    val fraction = ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        drawRoundRect(
                            activeColor,
                            size = Size(size.width * fraction, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                        )
                    }
                }
                drawRoundRect(
                    outline,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                    style = Stroke(width = 0.5.dp.toPx()),
                )
            }
        },
    )
}
