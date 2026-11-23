package com.milknote.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.theme.PillShape

/**
 * 模态表面统一配方:L2 浮层 = 24dp 圆角、16dp 柔和阴影、surface 底、hairline 边、顶缘内高光。
 * 所有 Dialog / 中央面板 / 命令面板共用,层级一致(A-31 token 语义化)。
 */
@Composable
fun Modifier.milkDialogSurface(): Modifier = this
    .softShadow(16.dp, MaterialTheme.shapes.extraLarge)
    .clip(MaterialTheme.shapes.extraLarge)
    .background(MaterialTheme.colorScheme.surface)
    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.9f), MaterialTheme.shapes.extraLarge)
    .innerHighlight(strength = 0.35f)
    .paintedShadow(MaterialTheme.shapes.extraLarge)

enum class MilkButtonVariant { PRIMARY, SECONDARY, TEXT }

/**
 * 统一文字按钮:48dp 高、胶囊;Primary 支持忙碌指示并阻止重复点击;
 * destructive = 危险动作(删除等)用 error 实心,视觉上与普通主操作区分。
 */
@Composable
fun MilkButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MilkButtonVariant = MilkButtonVariant.PRIMARY,
    enabled: Boolean = true,
    busy: Boolean = false,
    destructive: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val shadow = if (variant == MilkButtonVariant.PRIMARY) Modifier.softShadow(5.dp, PillShape) else Modifier
    val background = when {
        variant == MilkButtonVariant.PRIMARY && destructive -> Modifier.background(cs.error, PillShape)
        variant == MilkButtonVariant.PRIMARY -> Modifier.background(accentBrush(), PillShape)
        variant == MilkButtonVariant.SECONDARY -> Modifier.background(cs.surfaceVariant.copy(alpha = 0.7f), PillShape)
        else -> Modifier
    }
    val border = when {
        variant == MilkButtonVariant.SECONDARY -> Modifier.border(1.dp, cs.outline, PillShape)
        variant == MilkButtonVariant.TEXT && destructive -> Modifier.border(1.dp, cs.error.copy(alpha = 0.5f), PillShape)
        else -> Modifier
    }
    val contentColor = when {
        variant == MilkButtonVariant.PRIMARY -> Color.White
        variant == MilkButtonVariant.SECONDARY -> cs.onSurface
        destructive -> cs.error
        else -> cs.primary
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
            .padding(horizontal = 20.dp),
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
        leading?.let {
            it()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
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
            Box(
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
                    outline.copy(alpha = 0.7f),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                    style = Stroke(width = 0.5.dp.toPx()),
                )
            }
        },
    )
}
