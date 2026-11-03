package com.milknote.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 对比度验收(A-01/A-03):封面「用途标签」前景与诊断终端文字
 * 在真实合成底色上 ≥4.5:1。数值随 Color.kt 变更即时失败,防止回归。
 */
class ColorContrastTest {

    private fun linear(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)

    private fun ratio(fg: Color, bg: Color): Double {
        val l1 = maxOf(luminance(fg), luminance(bg))
        val l2 = minOf(luminance(fg), luminance(bg))
        return (l1 + 0.05) / (l2 + 0.05)
    }

    @Test
    fun `封面六色用途标签前景在各自 12 percent 淡底上达到 4_5`() {
        val surfaceLight = Color(0xFFFFFFFF)   // 书架卡片 surface
        val surfaceDark = Color(0xFF26272D)
        CoverPalette.forEach { cover ->
            val tintLight = cover.copy(alpha = 0.12f).compositeOver(surfaceLight)
            val tintDark = cover.copy(alpha = 0.12f).compositeOver(surfaceDark)
            val light = ratio(coverForeground(cover, dark = false), tintLight)
            val dark = ratio(coverForeground(cover, dark = true), tintDark)
            assertTrue("亮色 ${cover} 前景对比 $light < 4.5", light >= 4.5)
            assertTrue("暗色 ${cover} 前景对比 $dark < 4.5", dark >= 4.5)
        }
    }

    @Test
    fun `诊断终端文字在固定深底上达到 4_5 且不随主题反转`() {
        assertTrue(ratio(ConsoleText, ConsoleBg) >= 4.5)
        assertTrue(ratio(ConsoleSuccess, ConsoleBg) >= 4.5)
        assertTrue(ratio(ConsoleWarn, ConsoleBg) >= 4.5)
        assertTrue(ratio(ConsoleAccent, ConsoleBg) >= 4.5)
        assertTrue(ratio(ConsoleExport, ConsoleBg) >= 4.5)
    }
}
