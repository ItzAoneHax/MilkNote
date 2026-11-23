package com.milknote.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
val LocalMilkWarning = androidx.compose.runtime.staticCompositionLocalOf { WarnAmber }

/** 当前是否暗色主题(themeMode 手动覆盖时不等于系统值;柔和层配色用) */
val LocalMilkDark = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * 减弱动效(A-28/红线「尊重系统减少动画」):系统 Animator 时长缩放为 0 时为 true。
 * 为 true 时空间动画(位移/缩放/展开)退化为 ≤80ms 淡化或即时切换;
 * 颜色过渡与书写路径不受影响。随 MilkRoot 组合时读取。
 */
val LocalMilkMotion = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * 圆角层级(视觉升级 2026-11):同一层级同一答案——
 * 弹窗/中央面板 24(extraLarge) · 卡片 18(large) · 输入框/嵌套面板 14(medium) · 缩略图 10(small) · 微元素 8。
 */
val MilkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

/** 全胶囊 */
val PillShape = RoundedCornerShape(999.dp)

/** 模态遮罩:40% 黑,足以把注意力交给浮层,又不至于像关灯 */
val MilkScrim = Color(0x66000000)

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
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides colors.onBackground,
            LocalMilkWarning provides if (darkTheme) WarnAmberDark else WarnAmber,
            LocalMilkDark provides darkTheme,
        ) {
            content()
        }
    }
}
