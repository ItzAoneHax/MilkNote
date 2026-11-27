package com.milknote.app.ui.writer

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 取色面板的纯色值换算(不依赖 android.graphics,便于 JVM 单测):
 * HEX 解析/格式化、ARGB ↔ RGB、ARGB ↔ HSV、通道字节输入。
 * 约定:颜色恒为无符号 32 位 ARGB 以 Long 承载(`0xAARRGGBBL`),避免 Int 负数误判。
 */

/**
 * 解析 HEX 文本:`#` 可选,3 / 6 / 8 位;
 * 3 位按半字节展开(RGB → RRGGBB),6 位沿用 [fallbackAlpha],8 位(AARRGGBB)自带透明度。
 * 长度不符或含非十六进制字符返回 null。
 */
internal fun parseHexColor(raw: String, fallbackAlpha: Int = 255): Long? {
    val body = raw.trim().removePrefix("#")
    if (body.length != 3 && body.length != 6 && body.length != 8) return null
    if (body.any { it.digitToIntOrNull(16) == null }) return null
    val alpha = fallbackAlpha.coerceIn(0, 255).toLong()
    return when (body.length) {
        3 -> {
            val r = body[0].digitToInt(16) * 17
            val g = body[1].digitToInt(16) * 17
            val b = body[2].digitToInt(16) * 17
            (alpha shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
        }
        6 -> (alpha shl 24) or body.toLong(16)
        8 -> body.toLong(16) and 0xFFFFFFFFL
        else -> null
    }
}

/** 6 位大写 `RRGGBB`(不含透明度,透明度由不透明度滑条单独表达) */
internal fun formatHexRgb(argb: Long): String = "%06X".format(argb and 0xFFFFFFL)

internal fun argbToRgb(argb: Long): Triple<Int, Int, Int> = Triple(
    ((argb shr 16) and 0xFF).toInt(),
    ((argb shr 8) and 0xFF).toInt(),
    (argb and 0xFF).toInt(),
)

/** 通道逐项钳制到 0..255 后组装为无符号 ARGB */
internal fun rgbToArgb(r: Int, g: Int, b: Int, alpha: Int = 255): Long {
    val a = alpha.coerceIn(0, 255).toLong()
    val rr = r.coerceIn(0, 255).toLong()
    val gg = g.coerceIn(0, 255).toLong()
    val bb = b.coerceIn(0, 255).toLong()
    return (a shl 24) or (rr shl 16) or (gg shl 8) or bb
}

/** `[hue(0..360), saturation(0..1), value(0..1)]` */
internal fun argbToHsv(argb: Long): FloatArray {
    val (ri, gi, bi) = argbToRgb(argb)
    val r = ri / 255f
    val g = gi / 255f
    val b = bi / 255f
    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val delta = max - min
    var hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    if (hue < 0f) hue += 360f
    return floatArrayOf(hue, if (max == 0f) 0f else delta / max, max)
}

internal fun hsvToArgb(h: Float, s: Float, v: Float, alpha: Int = 255): Long {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val value = v.coerceIn(0f, 1f)
    val chroma = value * sat
    val sector = hue / 60f
    val second = chroma * (1f - abs(sector % 2f - 1f))
    val (r1, g1, b1) = when (sector.toInt().coerceIn(0, 5)) {
        0 -> Triple(chroma, second, 0f)
        1 -> Triple(second, chroma, 0f)
        2 -> Triple(0f, chroma, second)
        3 -> Triple(0f, second, chroma)
        4 -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }
    val m = value - chroma
    fun channel(x: Float): Int = ((x + m) * 255f).roundToInt().coerceIn(0, 255)
    return rgbToArgb(channel(r1), channel(g1), channel(b1), alpha)
}

/** 数字通道输入:空白或含非数字返回 null,否则钳制到 0..255 */
internal fun parseChannelByte(raw: String): Int? {
    val text = raw.trim()
    if (text.isEmpty() || text.any { !it.isDigit() }) return null
    return text.toIntOrNull()?.coerceIn(0, 255)
}
