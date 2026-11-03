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

// ---- 诊断终端(不随主题反转的固定深色;A-01) ----
val ConsoleBg = Color(0xFF14161B)       // 终端底:两主题恒深
val ConsoleText = Color(0xFFE8E6E0)     // 正文(14.5:1)
val ConsoleSuccess = Color(0xFF95E6CB)  // KEY/BTN(12.5:1)
val ConsoleWarn = Color(0xFFFFB86B)     // PALM(10.6:1)
val ConsoleAccent = Color(0xFF7D92FF)   // TRIGGER(6.4:1)
val ConsoleExport = Color(0xFFFFD65A)   // EXPORT(12.9:1)

// ---- 封面色板(新建弹窗 6 色,与 note-domain COVER_COLORS 对齐) ----
val CoverPalette = listOf(
    Color(0xFF3B5BEC), Color(0xFFD2483C), Color(0xFF2E9E6B),
    Color(0xFFD98E04), Color(0xFF7C5CE0), Color(0xFF2C8CC8),
)

// 封面「用途标签」小字前景:A-03 实测原色对比不足(亮 2.4–4.5 / 暗 2.5–3.8),
// 同色相加深/提亮变体,在 cover 12% 淡底上全部 ≥4.5:1(WCAG,数值见 design-review)
private val CoverFgLight = listOf(
    Color(0xFF3453D8), Color(0xFFB03A2F), Color(0xFF1F7A4F),
    Color(0xFF8A5A02), Color(0xFF5F3FC0), Color(0xFF1F6E9C),
)
private val CoverFgDark = listOf(
    Color(0xFF9DADFF), Color(0xFFF0958C), Color(0xFF7FD6AC),
    Color(0xFFF2B45C), Color(0xFFB7A4F2), Color(0xFF8CCBE8),
)

/** 封面用途标签/封面系小字前景:亮色用同色相加深变体,暗色用提亮变体;色点仍用原色 */
fun coverForeground(cover: Color, dark: Boolean): Color {
    var best = 0
    var bestDist = Long.MAX_VALUE
    CoverPalette.forEachIndexed { i, c ->
        // RGB 加权距离找最近基色(封面创建自 CoverPalette,正常路径精确命中)
        val dr = (c.red - cover.red); val dg = (c.green - cover.green); val db = (c.blue - cover.blue)
        val d = (dr * 3 + dg * 6 + db).toLong()
        if (d < bestDist) { bestDist = d; best = i }
    }
    val table = if (dark) CoverFgDark else CoverFgLight
    return if (bestDist == 0L) table[best]
    else table[best].copy(  // 未知封面色:向白/黑对半混合保底
        red = if (dark) (cover.red + 1f) / 2 else cover.red / 2,
        green = if (dark) (cover.green + 1f) / 2 else cover.green / 2,
        blue = if (dark) (cover.blue + 1f) / 2 else cover.blue / 2,
    )
}

// 书写工具默认 ARGB;每种笔的独立色板由 WriterToolSettings 组合
const val InkBlackArgb = 0xFF23221E
const val InkBlueArgb = 0xFF3B5BEC
const val InkRedArgb = 0xFFD2483C
const val InkGreenArgb = 0xFF2E9E6B
const val InkPurpleArgb = 0xFF7C5CE0
const val BallBlueArgb = 0xFF2C8CC8
const val HighlightYellowArgb = 0xFFFFD65A
const val HighlightPinkArgb = 0xFFFF8FA3
const val HighlightGreenArgb = 0xFF8FDC97
const val HighlightBlueArgb = 0xFF83C5FF

// 兼容其他界面的基础墨色板
val InkPalette = listOf(Color(InkBlackArgb), Color(InkBlueArgb), Color(InkRedArgb), Color(HighlightYellowArgb))
