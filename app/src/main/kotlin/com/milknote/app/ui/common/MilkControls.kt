package com.milknote.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.PillShape

/**
 * 第二代浮层表面(DESIGN-V2 · 材质受光):18dp 圆角 + 柔和投影 +
 * 垂直渐变描边(顶缘捕捉一道微光、底缘由 hairline 收住),让浮层像实体材质浮起。
 */
@Composable
fun Modifier.milkDialogSurface(): Modifier {
    val dark = LocalMilkDark.current
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    val rim = if (dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.15f),
            1f to cs.outline.copy(alpha = 0.75f),
        )
    } else {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.6f),
            0.28f to cs.outline.copy(alpha = 0.45f),
            1f to cs.outline.copy(alpha = 0.45f),
        )
    }
    return this
        .softShadow(if (dark) 12.dp else 16.dp, shape)
        .clip(shape)
        .background(cs.surface)
        .border(if (dark) 1.dp else 0.75.dp, rim, shape)
}

enum class MilkButtonVariant { PRIMARY, SECONDARY, TEXT }

/** 按钮高度档:小号 32dp(工具栏/弹窗底栏),标准 38dp(主 CTA) */
enum class MilkButtonSize(val height: Dp) {
    SMALL(32.dp),
    MEDIUM(38.dp),
}

/**
 * 统一胶囊按钮：主按钮实心 primary / hairline 描边次按钮 / 纯文字按钮；
 * 按下微缩 0.975 + 底色加深(100ms)；禁用态给明确底色与文字对比色，而非整块淡化。
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
    size: MilkButtonSize = MilkButtonSize.MEDIUM,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && !busy

    val baseBg = when {
        variant == MilkButtonVariant.PRIMARY && destructive -> cs.error
        variant == MilkButtonVariant.PRIMARY -> cs.primary
        variant == MilkButtonVariant.SECONDARY -> cs.surface
        else -> Color.Transparent
    }
    val bgTarget = when {
        !enabled -> if (variant == MilkButtonVariant.PRIMARY) cs.surfaceVariant else Color.Transparent
        pressed -> when (variant) {
            MilkButtonVariant.PRIMARY -> lerp(baseBg, Color.Black, if (dark) 0.14f else 0.09f)
            MilkButtonVariant.SECONDARY -> cs.onSurface.copy(alpha = if (dark) 0.10f else 0.06f)
            MilkButtonVariant.TEXT -> cs.onSurface.copy(alpha = if (dark) 0.09f else 0.06f)
        }
        else -> baseBg
    }
    val bg by animateColorAsState(
        bgTarget,
        tween(MilkMotion.press, easing = MilkMotion.EaseOut),
        label = "milkButtonBg",
    )

    val borderTarget = when {
        variant != MilkButtonVariant.SECONDARY -> Color.Transparent
        !enabled -> cs.outline.copy(alpha = 0.55f)
        else -> cs.outline
    }
    val borderColor by animateColorAsState(borderTarget, MilkMotion.colorSpec(), label = "milkButtonBorder")

    val fgTarget = when {
        !enabled -> when (variant) {
            MilkButtonVariant.PRIMARY -> cs.onSurfaceVariant.copy(alpha = 0.48f)
            MilkButtonVariant.SECONDARY -> cs.onSurfaceVariant.copy(alpha = 0.55f)
            MilkButtonVariant.TEXT ->
                if (destructive) cs.error.copy(alpha = 0.45f) else cs.onSurfaceVariant.copy(alpha = 0.5f)
        }
        variant == MilkButtonVariant.PRIMARY -> cs.onPrimary
        variant == MilkButtonVariant.SECONDARY -> cs.onBackground
        destructive -> cs.error
        else -> cs.primary
    }
    val fg by animateColorAsState(fgTarget, MilkMotion.colorSpec(), label = "milkButtonFg")

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !busy) 0.975f else 1f,
        animationSpec = tween(MilkMotion.press, easing = MilkMotion.EaseOut),
        label = "milkButtonScale",
    )

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(PillShape)
            .background(bg)
            .then(
                if (variant == MilkButtonVariant.SECONDARY) {
                    Modifier.border(1.dp, borderColor, PillShape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = active,
                role = Role.Button,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = size.height)
            .padding(
                horizontal = if (size == MilkButtonSize.SMALL) 14.dp else 16.dp,
                vertical = if (size == MilkButtonSize.SMALL) 5.dp else 8.dp,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = fg,
            )
            Spacer(Modifier.width(8.dp))
        }
        leading?.let {
            it()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = fg,
            style = MilkText.bodyMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 统一滑条：苹果风格精细拇指(20dp 纯白 + 2dp 柔影 + 0.5dp 描边)，
 * 4dp 细轨(传入 [trackBrush] 时 6dp 以承载渐变)。
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
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val trackColor = cs.surfaceVariant
    val activeColor = cs.primary
    val thumbBorder = if (dark) Color.White.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.14f)
    val trackHeight = if (trackBrush != null) 6.dp else 4.dp

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f),
        enabled = enabled,
        valueRange = valueRange,
        thumb = {
            Box(
                Modifier
                    .size(20.dp)
                    .softShadow(2.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(0.5.dp, thumbBorder, CircleShape),
            )
        },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
                val radius = size.height / 2f
                val corner = androidx.compose.ui.geometry.CornerRadius(radius, radius)
                if (trackBrush != null) {
                    drawRoundRect(trackBrush, size = size, cornerRadius = corner)
                } else {
                    drawRoundRect(trackColor, size = size, cornerRadius = corner)
                    val range = state.valueRange
                    val fraction = ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        drawRoundRect(
                            activeColor,
                            size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height),
                            cornerRadius = corner,
                        )
                    }
                }
            }
        },
    )
}
