package com.milknote.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.LocalMilkSuccess
import com.milknote.app.ui.theme.LocalMilkWarning
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.OverlaySurfaceDark
import com.milknote.app.ui.theme.OverlaySurfaceLight
import com.milknote.app.ui.theme.OverlayText
import com.milknote.app.ui.theme.PillShape
import com.milknote.app.ui.theme.SurfaceElevatedDark

/** 笔电量胶囊:线性笔图标 + 单行百分比;<20% 琥珀;未知 --% */
@Composable
fun PenBatteryCapsule(
    level: Int,
    modifier: Modifier = Modifier,
) {
    val color = when {
        level < 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        level < 20 -> LocalMilkWarning.current
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Edit,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (level < 0) "--%" else "$level%",
            style = MilkText.monoMeta,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 卡片表面(第二代):仅 1dp hairline 描边,无投影——靠留白与描边分层 */
@Composable
fun Modifier.softCard(): Modifier =
    this.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)

/**
 * 标准分组面(全应用唯一的分组卡画法):`MilkRadius.lg` 圆角 + 1dp hairline + 平涂底。
 * 暗色下底色略抬升一档,让卡片在纸面之上但仍不靠投影分层。
 */
@Composable
fun MilkSurfaceGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MilkRadius.lg))
            .background(if (dark) SurfaceElevatedDark.copy(alpha = 0.65f) else cs.surface)
            .border(1.dp, cs.outline, RoundedCornerShape(MilkRadius.lg)),
        content = content,
    )
}

/** 分组内分隔线:1dp 中性 hairline,可左右缩进对齐行文字 */
@Composable
fun MilkHairlineDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 0.dp,
    endIndent: Dp = 0.dp,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent, end = endIndent),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
    )
}

/**
 * 柔和基础设施(A2.8,R2 触控复审收敛):
 * softShadow 双层 tinted 阴影 / innerHighlight 顶缘内高光 / softClickable 按压反馈。
 * hover 反馈已按「触控笔/手指优先」红线移除——按下-抬起才是触控反馈主体(A-32)。
 */
@Composable
fun Modifier.softShadow(
    elevation: Dp = 6.dp,
    shape: Shape = MaterialTheme.shapes.large,
): Modifier {
    val dark = LocalMilkDark.current
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = if (dark) Color(0x33000000) else Color(0x141B1A16),
            spotColor = if (dark) Color(0x4D000000) else Color(0x2423221E),
        )
}

/** 兼容旧调用点:第二代表面不再画顶缘内高光(保留签名,无绘制) */
@Composable
fun Modifier.innerHighlight(strength: Float = 0.45f): Modifier = this

/** 兼容旧调用点:第二代浮层只保留柔和投影,不再手绘四边内侧阴影(保留签名,无绘制) */
@Composable
fun Modifier.paintedShadow(shape: Shape, edge: Dp = 12.dp): Modifier = this

/**
 * 单侧 hairline(1dp):列表分隔、导航顶栏、侧栏边界的统一画法。
 * 与 `drawBehind` 组合,不影响布局尺寸。
 */
fun Modifier.milkEdgeHairline(
    color: Color,
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = false,
): Modifier = drawBehind {
    val t = 1.dp.toPx()
    if (left) drawRect(color, topLeft = Offset(0f, 0f), size = Size(t, size.height))
    if (top) drawRect(color, topLeft = Offset(0f, 0f), size = Size(size.width, t))
    if (right) drawRect(color, topLeft = Offset(size.width - t, 0f), size = Size(t, size.height))
    if (bottom) drawRect(color, topLeft = Offset(0f, size.height - t), size = Size(size.width, t))
}

/**
 * 苹果材质顶缘受光边线(悬浮胶囊/弹出层/纸板浮起):
 * 顺序固定为 softShadow → clip → background → 垂直受光渐变描边,
 * 投影绝不被自身圆角裁掉,顶缘一道细光让表面像实体材质。
 */
@Composable
fun Modifier.milkGlassRim(
    shape: Shape,
    dark: Boolean = LocalMilkDark.current,
    elevation: Dp = 10.dp,
): Modifier {
    val cs = MaterialTheme.colorScheme
    val stroke = if (dark) 1.dp else 0.75.dp
    val rim = if (dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.16f),
            0.45f to cs.outline.copy(alpha = 0.9f),
            1f to Color.White.copy(alpha = 0.04f),
        )
    } else {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.85f),
            0.35f to cs.outline.copy(alpha = 0.55f),
            1f to cs.outline.copy(alpha = 0.85f),
        )
    }
    return this
        .softShadow(elevation, shape)
        .clip(shape)
        .background(cs.surface)
        .border(stroke, rim, shape)
}

/** 应用内轻提示(替代系统 Toast):底部居中浮层胶囊,同轴低位滑入/滑出,退出快于进入 */
@Composable
fun MilkToast(visible: Boolean, text: String, modifier: Modifier = Modifier) {
    val reduced = LocalMilkMotion.current
    val dark = LocalMilkDark.current
    val rim = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.16f),
        1f to Color.White.copy(alpha = 0.05f),
    )
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(if (reduced) MilkMotion.reduced else 220, easing = MilkMotion.EaseOut)) +
            if (reduced) {
                EnterTransition.None
            } else {
                slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(220, easing = MilkMotion.EaseOut),
                )
            },
        exit = fadeOut(tween(if (reduced) MilkMotion.reduced else 140, easing = MilkMotion.EaseOut)) +
            if (reduced) {
                ExitTransition.None
            } else {
                slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(140, easing = MilkMotion.EaseOut),
                )
            },
    ) {
        Box(
            Modifier
                .softShadow(if (dark) 8.dp else 10.dp, PillShape)
                .clip(PillShape)
                .background(if (dark) OverlaySurfaceDark else OverlaySurfaceLight)
                .border(0.75.dp, rim, PillShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(text, color = OverlayText, style = MilkText.label)
        }
    }
}

/** 按压缩放 0.98(100ms,motionPress)+ 全局按压指示(无扩散波纹);CTA/工具按钮用 */
@Composable
fun Modifier.softClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(MilkMotion.press),
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
    Text(text, modifier = modifier, style = MilkText.monoMeta, color = color)
}

/** 时间友好显示(书架「最近编辑」):<1h 刚刚/N 分钟;<1d N 小时;<60d N 天;否则 N 个月(四舍五入,至少 1) */
fun formatRelative(ms: Long): String {
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000 -> "刚刚"
        d < 3_600_000 -> "${d / 60_000} 分钟前"
        d < 86_400_000 -> "${d / 3_600_000} 小时前"
        d < 60 * 86_400_000L -> "${d / 86_400_000} 天前"
        else -> "${((d / 86_400_000L + 15) / 30).coerceAtLeast(1)} 个月前"
    }
}

/** 单一钢笔蓝实心填充(DESIGN-V2 §1 去渐变模板感) */
fun accentBrush(): Brush = Brush.linearGradient(
    listOf(com.milknote.app.ui.theme.AccentBlue, com.milknote.app.ui.theme.AccentBlue),
)

/** 命令面板按钮 ⌘K(Codex 风) */
@Composable
fun CommandKeyCapsule(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌘K", style = MilkText.monoMeta, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 通用线性图标按钮:默认 48dp 命中、内层 [visualSize] 圆角底面承载选中/容器态。
 * selected = 淡强调底 + 主色图标;container = 淡灰底(浮层关闭钮/次级操作)。
 * [touchSize] 收窄命中的唯一出口(密集浮层内的次级图标钮),默认值与历史一致。
 */
@Composable
fun MilkIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    container: Boolean = false,
    iconSize: Dp = 20.dp,
    selected: Boolean = false,
    visualSize: Dp = 36.dp,
    shape: Shape = RoundedCornerShape(MilkRadius.sm),
    touchSize: Dp = 48.dp,
) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalMilkMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.93f else 1f,
        animationSpec = tween(MilkMotion.press, easing = MilkMotion.EaseOut),
        label = "milkIconButtonScale",
    )
    val effectiveTint = when {
        !enabled -> tint.copy(alpha = 0.38f)
        selected -> cs.primary
        else -> tint
    }
    Box(
        modifier
            .size(touchSize)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(visualSize)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(
                    when {
                        selected -> cs.primary.copy(alpha = 0.12f)
                        container -> cs.surfaceVariant.copy(alpha = 0.85f)
                        else -> Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = effectiveTint,
            )
        }
    }
}

/**
 * 克制空状态:居中图标 + 标题 + 可选说明与动作;不套卡片框,直接落在纸面上。
 */
@Composable
fun MilkEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MilkSpace.xxl, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = cs.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(10.dp))
        }
        Text(title, style = MilkText.rowTitle, color = cs.onSurface, textAlign = TextAlign.Center)
        if (description != null) {
            Spacer(Modifier.height(MilkSpace.xs))
            Text(
                description,
                style = MilkText.caption,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            MilkButton(
                label = actionLabel,
                onClick = onAction,
                variant = MilkButtonVariant.SECONDARY,
                size = MilkButtonSize.SMALL,
            )
        }
    }
}

/** 状态胶囊色调(成功/警示/危险/强调/中性) */
enum class MilkStatusTone { NEUTRAL, ACCENT, SUCCESS, WARNING, DANGER }

/** 轻状态胶囊:5dp 状态点 + 小字,底为前景色 10% 淡染 */
@Composable
fun MilkStatusBadge(
    text: String,
    tone: MilkStatusTone = MilkStatusTone.NEUTRAL,
    mono: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val fg = when (tone) {
        MilkStatusTone.NEUTRAL -> cs.onSurfaceVariant
        MilkStatusTone.ACCENT -> cs.primary
        MilkStatusTone.SUCCESS -> LocalMilkSuccess.current
        MilkStatusTone.WARNING -> LocalMilkWarning.current
        MilkStatusTone.DANGER -> cs.error
    }
    Row(
        modifier
            .clip(PillShape)
            .background(fg.copy(alpha = 0.10f))
            .padding(horizontal = MilkSpace.sm, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = if (mono) MilkText.monoMicro else MilkText.micro,
            color = fg,
            maxLines = 1,
        )
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
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = w,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
        ),
    )
}
