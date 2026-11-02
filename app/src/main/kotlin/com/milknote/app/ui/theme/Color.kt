package com.milknote.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---- 亮色(奶白)· DESIGN-V0 §5.1 ----
val PaperLight = Color(0xFFFAF9F5)
val ChromeLight = Color(0xFFF3F1EA)
val SurfaceLight = Color(0xFFFFFFFF)
val InkLight = Color(0xFF23221E)
val MutedLight = Color(0xFF666256)       // 次要文字:对比度 ≥4.5(奶白底),小字可读
val LineLight = Color(0xFFE5E1D6)
val AccentBlue = Color(0xFF3B5BEC)
val AccentBlueSoft = Color(0xFF5C77F2)   // 渐变亮端
val HighlightYellow = Color(0xFFFFD65A)  // 荧光笔(默认 45% alpha)
val RedPen = Color(0xFFD2483C)
val WarnAmber = Color(0xFFD98E04)        // 警示(低电量等;原散落硬编码)
val DotLight = Color(0xFFD8D4C8)         // 纸面点阵

// ---- 暗色 ----
val PaperDark = Color(0xFF1B1C20)
val ChromeDark = Color(0xFF232429)
val SurfaceDark = Color(0xFF26272D)
val InkDark = Color(0xFFE8E6E0)
val MutedDark = Color(0xFFB7B3A8)        // 次要文字:对比度 ≥4.5(深纸底)
val LineDark = Color(0xFF33343B)
val AccentBlueDark = Color(0xFF7D92FF)
val WarnAmberDark = Color(0xFFE8A838)
val DotDark = Color(0xFF2C2D33)

// ---- 封面色板(新建弹窗 6 色,与 note-domain COVER_COLORS 对齐) ----
val CoverPalette = listOf(
    Color(0xFF3B5BEC), Color(0xFFD2483C), Color(0xFF2E9E6B),
    Color(0xFFD98E04), Color(0xFF7C5CE0), Color(0xFF2C8CC8),
)

// 墨色选择(工具条:墨黑/蓝/红/荧光黄)
val InkPalette = listOf(
    Color(0xFF23221E), Color(0xFF3B5BEC), Color(0xFFD2483C),
    Color(0xFFFFD65A),
)
