package com.milknote.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.ui.theme.Mono
import com.milknote.app.ui.theme.MonoMeta

/** 笔电量胶囊(DESIGN-V0 §3.3):🪶 82%;<20% 琥珀;未知 --%(长按手势由调用方包一层) */
@Composable
fun PenBatteryCapsule(
    level: Int,
    modifier: Modifier = Modifier,
) {
    val color = when {
        level < 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        level < 20 -> com.milknote.app.ui.theme.LocalMilkWarning.current
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🪶", fontSize = 11.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (level < 0) "--%" else "$level%",
            style = MonoMeta,
            color = color,
        )
    }
}

/** 卡片双层软阴影 + hover 上浮(0.18–0.24s 过渡;§5.1 视觉语言)——委托 softShadow */
@Composable
fun Modifier.softCard(hoverable: Boolean = true): Modifier =
    this.softShadow(elevation = 6.dp, shape = MaterialTheme.shapes.large, hoverLift = hoverable)

/**
 * 柔和基础设施(A2.8,DESIGN-V0 §5.1「柔和的皮」):
 * softShadow 双层 tinted 阴影 / innerHighlight 顶缘内高光 /
 * softHover 悬停淡背景 / softClickable 按压缩放。
 */
@Composable
fun Modifier.softShadow(
    elevation: Dp = 6.dp,
    shape: Shape = MaterialTheme.shapes.large,
    hoverLift: Boolean = false,
): Modifier {
    val dark = com.milknote.app.ui.theme.LocalMilkDark.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(
        targetValue = if (hovered && hoverLift) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "softShadowLift",
    )
    val base = this
        .shadow(
            elevation = elevation + elevation * lift,
            shape = shape,
            ambientColor = if (dark) Color(0x33000000) else Color(0x141B1A16),
            spotColor = if (dark) Color(0x4D000000) else Color(0x2423221E),
        )
        .graphicsLayer { translationY = -2f * lift }
    return if (hoverLift) base.hoverable(interaction) else base
}

/** 顶缘内高光:1px 白渐变线(亮 45%→0 / 暗 10%→0);暗色表面收益最大,白卡上无害 */
@Composable
fun Modifier.innerHighlight(strength: Float = 0.45f): Modifier {
    val dark = com.milknote.app.ui.theme.LocalMilkDark.current
    val alpha = if (dark) strength * 0.22f else strength
    return drawBehind {
        val h = 2.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = alpha),
                1f to Color.Transparent,
                startY = 0f,
                endY = h,
            ),
            topLeft = Offset(0f, 0f),
            size = Size(size.width, h),
        )
    }
}

/**
 * 内侧柔和投影(§5.1「双层软阴影」的可视兜底):四边内侧 tinted 渐变(下浓上淡,光从上来),
 * 画在元素 bounds 内 → 不受 AnimatedVisibility 裁剪、跨 Dialog/主窗口/软渲染环境稳定
 * (elevation 投影在部分模拟器环境不渲染,A2.8 像素实证)。链在 background/border 之后。
 */
@Composable
fun Modifier.paintedShadow(shape: Shape, edge: Dp = 12.dp): Modifier {
    val dark = com.milknote.app.ui.theme.LocalMilkDark.current
    val tint = if (dark) Color(0x4D000000) else Color(0x1A23221E)
    val bottom = tint
    val side = tint.copy(alpha = tint.alpha * 0.66f)
    val top = tint.copy(alpha = tint.alpha * 0.4f)
    return drawBehind {
        val path = when (val o = shape.createOutline(size, layoutDirection, this)) {
            is androidx.compose.ui.graphics.Outline.Rectangle ->
                androidx.compose.ui.graphics.Path().apply { addRect(o.rect) }
            is androidx.compose.ui.graphics.Outline.Rounded ->
                androidx.compose.ui.graphics.Path().apply { addRoundRect(o.roundRect) }
            is androidx.compose.ui.graphics.Outline.Generic -> o.path
        }
        clipPath(path) {
            val e = edge.toPx().coerceAtMost(size.minDimension / 3f)
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, bottom), startY = size.height - e, endY = size.height),
            )
            drawRect(
                Brush.verticalGradient(listOf(top, Color.Transparent), startY = 0f, endY = e * 0.7f),
            )
            drawRect(
                Brush.horizontalGradient(listOf(side, Color.Transparent), startX = 0f, endX = e),
            )
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, side), startX = size.width - e, endX = size.width),
            )
        }
    }
}

/** hover 淡背景(笔悬停/鼠标均触发,200ms 过渡);叠在自身 background 之后使用 */
@Composable
fun Modifier.softHover(bg: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f)): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color by animateColorAsState(
        targetValue = if (hovered) bg else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "softHover",
    )
    return this.hoverable(interaction).background(color)
}

/** 按压缩放 0.97(120ms)+ 默认 ripple;CTA/工具按钮用 */
@Composable
fun Modifier.softClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "softClickable",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interaction,
            indication = androidx.compose.foundation.LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
}

/** 等宽元数据文本(页码/尺寸/时间) */
@Composable
fun MonoText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, modifier = modifier, style = MonoMeta, fontFamily = Mono, color = color, fontSize = 11.sp)
}

/** 时间友好显示(书架「最近编辑」) */
fun formatRelative(ms: Long): String {
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000 -> "刚刚"
        d < 3_600_000 -> "${d / 60_000} 分钟前"
        d < 86_400_000 -> "${d / 3_600_000} 小时前"
        d < 7 * 86_400_000L -> "${d / 86_400_000} 天前"
        else -> "${d / (30 * 86_400_000L)} 个月前"
    }
}

/** 强调蓝渐变(CTA 背景) */
fun accentBrush(): Brush = Brush.linearGradient(
    listOf(Color(0xFF3B5BEC), Color(0xFF5C77F2)),
)

/** 命令面板按钮 ⌘K(Codex 风) */
@Composable
fun CommandKeyCapsule(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌘K", style = MonoMeta, fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


/** 虚线圆角边框(书架「新建笔记本」卡,DESIGN-V0 §5.4) */
fun Modifier.dashedBorder(
    color: Color,
    width: Dp = 1.5.dp,
    cornerRadius: Dp = 20.dp,
): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = Stroke(
            width = w,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
        ),
    )
}
