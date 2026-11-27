package com.milknote.app.ui.writer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.common.MilkIcons
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.ink.model.InkTool

@Composable
internal fun InkColorDot(
    color: Long,
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    squeezed: Boolean = false,
    onHoverChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 触屏只认按压:不再监听 hover(触屏点击会触发幽灵悬停)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed, enabled) { onHoverChanged(pressed && enabled) }
    val reduced = LocalMilkMotion.current
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = when {
            reduced -> 1f
            pressed && enabled -> 0.92f
            active && enabled -> 1.04f
            squeezed -> 0.94f
            else -> 1f
        },
        animationSpec = when {
            reduced -> snap()
            pressed -> tween(MilkMotion.press, easing = MilkMotion.EaseOut)
            else -> MilkMotion.uiSpring()
        },
        label = "inkScale",
    )
    val ring by animateColorAsState(
        if (active && enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
        MilkMotion.colorSpec(), label = "inkRing",
    )
    val cs = MaterialTheme.colorScheme
    val dotColor = Color(color.toInt())
    // 对比勾色:深墨用白勾,浅色用黑勾
    val checkTint = if (dotColor.luminance() > 0.55f) Color(0xCC101010) else Color(0xCCFFFFFF)
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.RadioButton,
                onClick = {
                    // 切到新色:轻触感确认(再次点当前色=打开调色,不重复触发)
                    if (!active) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .semantics { contentDescription = if (active) "$label，当前颜色，再次点击详细设定" else label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1f else 0.35f
                }
                .size(26.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(if (active) 2.dp else 1.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                Icon(
                    MilkIcons.Check,
                    contentDescription = null,
                    tint = checkTint,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** 统一双层线性矢量笔图标：笔身随主题前景色，笔尖/笔头随当前墨色着色（暗色下自动提亮深墨）。 */
@Composable
internal fun RealPenIcon(
    tool: InkTool,
    color: Long,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val baseIcon = when (tool) {
        InkTool.PEN -> MilkIcons.FountainPen
        InkTool.BALL -> MilkIcons.BallpointPen
        InkTool.HIGHLIGHTER -> MilkIcons.Highlighter
        InkTool.ERASER -> MilkIcons.Eraser
        InkTool.DOODLE -> MilkIcons.PatternBrush
        InkTool.LASER -> MilkIcons.LaserPointer
        else -> MilkIcons.FountainPen
    }
    val accentIcon = when (tool) {
        InkTool.PEN -> MilkIcons.FountainPenNibAccent
        InkTool.BALL -> MilkIcons.BallpointPenTipAccent
        InkTool.HIGHLIGHTER -> MilkIcons.HighlighterTipAccent
        InkTool.DOODLE -> MilkIcons.PatternBrushAccent
        InkTool.LASER -> MilkIcons.LaserTipAccent
        else -> null
    }
    val rawColor = Color(color.toInt())
    val accentTint = when {
        dark && rawColor.luminance() < 0.08f -> cs.primary
        !dark && rawColor.luminance() > 0.88f -> cs.primary
        else -> rawColor
    }
    val alpha = if (enabled) 1f else 0.45f
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = baseIcon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
            tint = cs.onSurface,
        )
        accentIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                tint = accentTint,
            )
        }
    }
}
