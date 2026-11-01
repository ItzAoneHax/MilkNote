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
    secondary = InkDark,
    error = Color(0xFFE06558),
)

/** 圆角比例(§5.1 视觉语言):卡片/弹窗 ~20,缩略图 ~10,小控件全胶囊 */
val MilkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** 全胶囊 */
val PillShape = RoundedCornerShape(999.dp)

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
        ) {
            content()
        }
    }
}
