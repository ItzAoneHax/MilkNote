package com.milknote.app.ui.writer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.milknote.app.R
import com.milknote.ink.model.InkTool

@Composable
internal fun PenToolButton(
    tool: InkTool,
    label: String,
    color: Long,
    active: Boolean,
    enabled: Boolean,
    squeezed: Boolean = false,
    onHoverChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    val ink = Color(color.toInt())
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered, enabled) { onHoverChanged(hovered && enabled) }
    val border by animateColorAsState(
        when {
            active && enabled -> MaterialTheme.colorScheme.onSurface
            hovered && enabled -> ink.copy(alpha = 0.82f)
            else -> ink.copy(alpha = 0.45f)
        },
        tween(200), label = "penBorder",
    )
    val bg by animateColorAsState(
        if ((active || hovered) && enabled) ink.copy(alpha = 0.14f) else Color.Transparent,
        tween(200), label = "penBg",
    )
    val scale by animateFloatAsState(
        when {
            hovered && enabled -> 1.22f
            active && enabled -> 1.16f
            squeezed -> 0.94f
            else -> 1f
        },
        tween(160), label = "penScale",
    )
    val slot by animateFloatAsState(
        when {
            hovered && enabled -> 1.10f
            active && enabled -> 1.06f
            squeezed -> 0.96f
            else -> 1f
        },
        tween(160), label = "penSlot",
    )
    Box(
        Modifier
            .size(52.dp * slot)
            .clip(CircleShape)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics {
                contentDescription = when {
                    active && tool == InkTool.ERASER -> "$label，再次点击恢复原笔"
                    active -> "$label，再次点击打开详细设置"
                    else -> label
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            RealPenIcon(
                tool = tool,
                color = color,
                enabled = enabled,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
internal fun InkColorDot(
    color: Long,
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    squeezed: Boolean = false,
    onHoverChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered, enabled) { onHoverChanged(hovered && enabled) }
    val scale by animateFloatAsState(
        when {
            hovered && enabled -> 1.24f
            active && enabled -> 1.16f
            squeezed -> 0.94f
            else -> 1f
        },
        tween(160), label = "inkScale",
    )
    val slot by animateFloatAsState(
        when {
            hovered && enabled -> 1.10f
            active && enabled -> 1.06f
            squeezed -> 0.96f
            else -> 1f
        },
        tween(160), label = "inkSlot",
    )
    val ring by animateColorAsState(
        if ((active || hovered) && enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
        tween(200), label = "inkRing",
    )
    Box(
        Modifier
            .size(48.dp * slot)
            .clip(CircleShape)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics { contentDescription = if (active) "$label，再次点击详细设定颜色" else label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(19.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1f else 0.35f
                }
                .clip(CircleShape)
                .background(Color(color.toInt()))
                .border(if (active || hovered) 2.dp else 1.dp, ring, CircleShape),
        )
    }
}

/** 小米笔记原始分层资源:笔杆保留原始光影,独立笔头随当前墨色着色。 */
@Composable
internal fun RealPenIcon(
    tool: InkTool,
    color: Long,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val body = when (tool) {
        InkTool.PEN -> R.drawable.ic_pen_fountain_body
        InkTool.BALL -> R.drawable.ic_pen_ballpoint_body
        InkTool.HIGHLIGHTER -> R.drawable.ic_pen_highlighter_body
        InkTool.ERASER -> R.drawable.ic_eraser_body
        else -> R.drawable.ic_pen_fountain_body   // 形状工具(RECT/OVAL/LINE)暂复用钢笔身
    }
    val head = when (tool) {
        InkTool.PEN -> R.drawable.ic_pen_fountain_head
        InkTool.BALL -> R.drawable.ic_pen_ballpoint_head
        InkTool.HIGHLIGHTER -> R.drawable.ic_pen_highlighter_head
        InkTool.ERASER -> null
        else -> null                              // 形状工具暂无笔头
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(body),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (enabled) 1f else 0.45f },
            contentScale = ContentScale.Fit,
        )
        head?.let {
            Image(
                painter = painterResource(it),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (enabled) 1f else 0.45f },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Color(color.toInt()), BlendMode.SrcIn),
            )
        }
    }
}
