package com.milknote.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val LightColors = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    background = PaperLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = ChromeLight,
    onSurfaceVariant = MutedLight,
    outline = LineLight,
    outlineVariant = LineLight,
    secondary = InkLight,
    error = RedPen,
)

private val DarkColors = darkColorScheme(
    primary = AccentBlueDark,
    onPrimary = Color(0xFF101223),
    background = PaperDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = ChromeDark,
    onSurfaceVariant = MutedDark,
    outline = LineDark,
    outlineVariant = LineDark,
    secondary = InkDark,
    error = Color(0xFFE06558),
)

/** 警示色(M3 scheme 无 warning 槽,经 CompositionLocal 提供) */
val LocalMilkWarning = staticCompositionLocalOf { WarnAmber }

/** 成功色(绿色状态点/成功徽标;暗色提亮变体) */
val LocalMilkSuccess = staticCompositionLocalOf { GreenOk }

/** 当前是否暗色主题(themeMode 手动覆盖时不等于系统值;柔和层配色用) */
val LocalMilkDark = staticCompositionLocalOf { false }

/**
 * 减弱动效(A-28/红线「尊重系统减少动画」):系统 Animator 时长缩放为 0 时为 true。
 * 为 true 时空间动画(位移/缩放/展开)退化为 ≤60ms 淡化或即时切换;
 * 颜色过渡与书写路径不受影响。随 MilkRoot 组合时读取。
 */
val LocalMilkMotion = staticCompositionLocalOf { false }

/** 统一圆角阶梯:所有新组件只从这里取,不再写裸 dp */
object MilkRadius {
    val xs: Dp = 6.dp
    val sm: Dp = 8.dp
    val md: Dp = 10.dp
    val lg: Dp = 12.dp
    val xl: Dp = 18.dp
    val pill: Dp = 999.dp
}

/** 统一间距阶梯:面板内边距/控件间距的唯一来源 */
object MilkSpace {
    val xxs: Dp = 4.dp
    val xs: Dp = 6.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
}

/**
 * 圆角层级(与 MilkRadius 对齐):同一层级同一答案——
 * 弹窗/中央面板 18(xl) · 卡片 12(lg) · 输入框/嵌套面板 10(md) · 缩略图/小控件 8(sm) · 微元素 6(xs)。
 */
val MilkShapes = Shapes(
    extraSmall = RoundedCornerShape(MilkRadius.xs),
    small = RoundedCornerShape(MilkRadius.sm),
    medium = RoundedCornerShape(MilkRadius.md),
    large = RoundedCornerShape(MilkRadius.lg),
    extraLarge = RoundedCornerShape(MilkRadius.xl),
)

/** 全胶囊 */
val PillShape = RoundedCornerShape(999.dp)

/** 模态遮罩:40% 黑,足以把注意力交给浮层,又不至于像关灯 */
val MilkScrim = Color(0x66000000)

/**
 * 苹果风格按压指示器:按下时在内容上平涂一层 6% 黑(暗色 9% 白),
 * 60ms 渐入、140ms 淡出。替代默认扩散波纹——触控笔/手指优先的克制反馈。
 */
class MilkPressIndication(private val dark: Boolean = false) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        MilkPressIndicationNode(interactionSource, dark)

    override fun equals(other: Any?): Boolean = other is MilkPressIndication && other.dark == dark

    override fun hashCode(): Int = dark.hashCode()
}

private class MilkPressIndicationNode(
    private val interactionSource: InteractionSource,
    private val dark: Boolean,
) : Modifier.Node(), DrawModifierNode {
    private val pressAlpha = if (dark) 0.09f else 0.06f
    private var alpha by mutableFloatStateOf(0f)
    private var animation: Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> animateTo(pressAlpha, 60)
                    is PressInteraction.Release, is PressInteraction.Cancel -> animateTo(0f, 140)
                }
            }
        }
    }

    private fun animateTo(target: Float, durationMillis: Int) {
        animation?.cancel()
        animation = coroutineScope.launch {
            animate(
                initialValue = alpha,
                targetValue = target,
                animationSpec = tween(durationMillis, easing = LinearEasing),
            ) { value, _ -> alpha = value }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (alpha > 0f) {
            drawRect(
                color = if (dark) Color.White else Color.Black,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                alpha = alpha,
            )
        }
    }
}

@Composable
fun MilkTheme(darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MilkTypography,
        shapes = MilkShapes,
    ) {
        // 关键:未显式指定颜色的 Text/Icon 默认走 LocalContentColor,
        // 其全局默认是纯黑(只有 Material3 Surface 会设)——不设则深色模式下黑字配黑底。
        CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides colors.onBackground,
            LocalMilkWarning provides if (darkTheme) WarnAmberDark else WarnAmber,
            LocalMilkSuccess provides if (darkTheme) GreenOkDark else GreenOk,
            LocalMilkDark provides darkTheme,
            LocalIndication provides remember(darkTheme) { MilkPressIndication(darkTheme) },
        ) {
            content()
        }
    }
}
