package com.milknote.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.LocalMilkSuccess
import com.milknote.app.ui.theme.LocalMilkWarning
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.PillShape
import com.milknote.app.ui.theme.SurfaceElevatedDark

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
    minWidth: Dp? = null,
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
            .then(if (minWidth != null) Modifier.widthIn(min = minWidth) else Modifier)
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

// ==================== 行内动作 / 状态(Gen2 紧凑基准) ====================

/**
 * 行内紧凑控件的语义色调:中性/强调走 M3 colorScheme,成功/警示由主题 CompositionLocal 提供,
 * 危险取 `colorScheme.error`。
 */
enum class MilkInlineTone { NEUTRAL, ACCENT, SUCCESS, WARNING, DANGER }

/** 一枚行内胶囊的配色三件套:填充 / 描边 / 文字与图标前景 */
private class InlinePaint(
    val fill: Color,
    val border: Color,
    val fg: Color,
    val icon: Color,
)

@Composable
private fun inlinePaint(
    tone: MilkInlineTone,
    selected: Boolean,
    enabled: Boolean,
    statusSurface: Boolean,
): InlinePaint {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    if (!enabled) {
        return InlinePaint(
            fill = Color.Transparent,
            border = cs.outline.copy(alpha = 0.5f),
            fg = cs.onSurfaceVariant.copy(alpha = 0.4f),
            icon = cs.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
    return when (if (selected) MilkInlineTone.ACCENT else tone) {
        MilkInlineTone.NEUTRAL -> InlinePaint(
            fill = if (statusSurface) {
                cs.surfaceVariant.copy(alpha = 0.7f)
            } else {
                if (dark) SurfaceElevatedDark else cs.surface
            },
            border = cs.outline,
            fg = if (statusSurface) cs.onSurfaceVariant else cs.onSurface,
            icon = cs.onSurfaceVariant,
        )

        MilkInlineTone.ACCENT -> InlinePaint(
            fill = cs.primary.copy(alpha = if (dark) 0.16f else 0.09f),
            border = cs.primary.copy(alpha = 0.38f),
            fg = cs.primary,
            icon = cs.primary,
        )

        MilkInlineTone.SUCCESS -> {
            val success = LocalMilkSuccess.current
            InlinePaint(success.copy(alpha = 0.10f), success.copy(alpha = 0.32f), success, success)
        }

        MilkInlineTone.WARNING -> {
            val warning = LocalMilkWarning.current
            InlinePaint(warning.copy(alpha = 0.10f), warning.copy(alpha = 0.32f), warning, warning)
        }

        MilkInlineTone.DANGER -> InlinePaint(
            cs.error.copy(alpha = 0.10f),
            cs.error.copy(alpha = 0.32f),
            cs.error,
            cs.error,
        )
    }
}

/** `Dp.Unspecified` 的 `==` 比较在值类上不可靠,统一用底层的 NaN 判断 */
private val Dp.isUnspecified: Boolean get() = value.isNaN()

/**
 * 行内动作 / 状态的公共几何(全应用唯一答案):
 * 外层 ≥36dp 命中框 + 内层 30dp 视觉胶囊 + `MilkRadius.sm` 圆角 + 1dp 描边 + 9dp 内边距。
 * 状态不带点击,因此与动作并排时基线、高度、圆角完全一致——文案变化只会改变宽度。
 */
@Composable
private fun MilkInlineLane(
    label: String,
    modifier: Modifier,
    icon: ImageVector?,
    showDot: Boolean,
    tone: MilkInlineTone,
    selected: Boolean,
    enabled: Boolean,
    minWidth: Dp,
    statusSurface: Boolean,
    onClick: (() -> Unit)?,
) {
    val reduced = LocalMilkMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (onClick != null && pressed && enabled && !reduced) 0.97f else 1f,
        animationSpec = tween(MilkMotion.press, easing = MilkMotion.EaseOut),
        label = "milkInlineScale",
    )
    val paint = inlinePaint(tone, selected, enabled, statusSurface)
    val colorSpec = if (reduced) snap<Color>() else MilkMotion.colorSpec<Color>()
    val fill by animateColorAsState(paint.fill, colorSpec, label = "milkInlineFill")
    val border by animateColorAsState(paint.border, colorSpec, label = "milkInlineBorder")
    val fg by animateColorAsState(paint.fg, colorSpec, label = "milkInlineFg")
    val iconTint by animateColorAsState(paint.icon, colorSpec, label = "milkInlineIcon")
    val shape = RoundedCornerShape(MilkRadius.sm)

    Box(
        modifier
            .heightIn(min = 36.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .height(30.dp)
                .then(if (minWidth.isUnspecified) Modifier else Modifier.widthIn(min = minWidth))
                .clip(shape)
                .background(fill)
                .border(1.dp, border, shape)
                .padding(horizontal = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                icon != null -> {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = iconTint)
                    Spacer(Modifier.width(5.dp))
                }

                showDot -> {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(iconTint))
                    Spacer(Modifier.width(5.dp))
                }
            }
            Text(
                text = label,
                style = MilkText.label,
                color = fg,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 行内动作:30dp 视觉胶囊、8dp 圆角、1dp 描边,用于消息操作栏、面板顶栏等密集区域。
 * [minWidth] 用于锁死文案切换时的宽度(`贴回纸面`↔`已贴回`、`复制`↔`已复制`)。
 */
@Composable
fun MilkInlineAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: MilkInlineTone = MilkInlineTone.NEUTRAL,
    enabled: Boolean = true,
    selected: Boolean = false,
    minWidth: Dp = Dp.Unspecified,
) = MilkInlineLane(
    label = label,
    modifier = modifier,
    icon = icon,
    showDot = false,
    tone = tone,
    selected = selected,
    enabled = enabled,
    minWidth = minWidth,
    statusSurface = false,
    onClick = onClick,
)

/**
 * 行内状态:与 [MilkInlineAction] 完全同几何,只去掉点击。
 * 与动作并排时(`[ 已中断 ] [ 重试 ]`)共享同一基线,状态文案变化不推动相邻控件。
 */
@Composable
fun MilkInlineStatus(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    showDot: Boolean = icon == null,
    tone: MilkInlineTone = MilkInlineTone.NEUTRAL,
    minWidth: Dp = Dp.Unspecified,
) = MilkInlineLane(
    label = label,
    modifier = modifier,
    icon = icon,
    showDot = showDot,
    tone = tone,
    selected = false,
    enabled = true,
    minWidth = minWidth,
    statusSurface = true,
    onClick = null,
)
