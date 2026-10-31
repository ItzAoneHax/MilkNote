package com.milknote.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
        level < 20 -> Color(0xFFD98E04)
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

/** 卡片双层软阴影 + hover 上浮(0.18–0.24s 过渡;§5.1 视觉语言) */
@Composable
fun Modifier.softCard(hoverable: Boolean = true): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "cardHover",
    )
    val base = this
        .shadow(
            elevation = (6 + 6 * lift).dp,
            shape = MaterialTheme.shapes.large,
            ambientColor = Color(0x141B1A16),
            spotColor = Color(0x2423221E),
        )
        .graphicsLayer { translationY = -2f * lift }
    return if (hoverable) base.hoverable(interaction) else base
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
