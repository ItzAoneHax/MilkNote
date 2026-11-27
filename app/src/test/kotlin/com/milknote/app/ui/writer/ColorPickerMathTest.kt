package com.milknote.app.ui.writer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPickerMathTest {
    @Test
    fun `HEX 六位沿用当前透明度`() {
        assertEquals(0xFF2563EBL, parseHexColor("#2563EB"))
        assertEquals(0x802563EBL, parseHexColor("2563eb", fallbackAlpha = 0x80))
        assertEquals(0xFF2563EBL, parseHexColor("  #2563EB  "))
    }

    @Test
    fun `HEX 三位按半字节展开`() {
        assertEquals(0xFFAABBCCL, parseHexColor("ABC"))
        assertEquals(0x40FF0000L, parseHexColor("#F00", fallbackAlpha = 0x40))
        assertEquals(0xFFFFFFFFL, parseHexColor("FFF"))
        assertEquals(0xFF000000L, parseHexColor("000"))
    }

    @Test
    fun `HEX 八位自带透明度并优先于回退值`() {
        assertEquals(0x80112233L, parseHexColor("80112233", fallbackAlpha = 0xFF))
        assertEquals(0x00112233L, parseHexColor("00112233"))
        assertEquals(0xFFFFFFFFL, parseHexColor("ffffffff"))
    }

    @Test
    fun `HEX 非法长度或字符返回 null`() {
        assertNull(parseHexColor(""))
        assertNull(parseHexColor("#"))
        assertNull(parseHexColor("12"))
        assertNull(parseHexColor("1234"))
        assertNull(parseHexColor("1234567"))
        assertNull(parseHexColor("123456789"))
        assertNull(parseHexColor("GG0000"))
        assertNull(parseHexColor("FF00 00"))
    }

    @Test
    fun `凡可解析的 HEX 都能被格式化回六位大写`() {
        assertEquals("2563EB", formatHexRgb(0xFF2563EBL))
        assertEquals("000000", formatHexRgb(0x00000000L))
        assertEquals("FFFFFF", formatHexRgb(0xFFFFFFFFL))
        assertEquals("AABBCC", formatHexRgb(0x40AABBCCL))
    }

    @Test
    fun `RGB 与 ARGB 双向换算`() {
        assertEquals(Triple(0x25, 0x63, 0xEB), argbToRgb(0xFF2563EBL))
        assertEquals(Triple(0, 0, 0), argbToRgb(0x00000000L))
        assertEquals(Triple(255, 255, 255), argbToRgb(0xFFFFFFFFL))
        assertEquals(0xFF2563EBL, rgbToArgb(0x25, 0x63, 0xEB))
        assertEquals(0x802563EBL, rgbToArgb(0x25, 0x63, 0xEB, alpha = 0x80))
    }

    @Test
    fun `通道越界被钳制而不是回绕`() {
        assertEquals(0xFFFFFFFFL, rgbToArgb(300, 999, 500, alpha = 400))
        assertEquals(0xFF00FF00L, rgbToArgb(-1, 256, 0, alpha = 300))
        assertEquals(0x00000000L, rgbToArgb(0, 0, 0, alpha = -5))
    }

    @Test
    fun `HSV 往返保持颜色不变`() {
        val colors = listOf(0xFF2563EBL, 0xFFE53935L, 0xFFFFB300L, 0xFF48B66AL, 0xFF000000L, 0xFFFFFFFFL)
        colors.forEach { argb ->
            val hsv = argbToHsv(argb)
            assertTrue(hsv[0] in 0f..360f)
            assertTrue(hsv[1] in 0f..1f)
            assertTrue(hsv[2] in 0f..1f)
            assertEquals(argb, hsvToArgb(hsv[0], hsv[1], hsv[2], alpha = ((argb shr 24) and 0xFF).toInt()))
        }
    }

    @Test
    fun `HSV 关键色相位置`() {
        assertEquals(0f, argbToHsv(0xFF808080L)[0], 0.001f)
        assertEquals(0f, argbToHsv(0xFFFFFFFFL)[1], 0.001f)
        assertEquals(0f, argbToHsv(0xFF000000L)[2], 0.001f)
        assertEquals(0f, argbToHsv(0xFFFF0000L)[0], 0.001f)
        assertEquals(240f, argbToHsv(0xFF0000FFL)[0], 0.001f)
        assertEquals(0xFFFF0000L, hsvToArgb(0f, 1f, 1f))
        assertEquals(0xFF00FF00L, hsvToArgb(120f, 1f, 1f))
        assertEquals(0xFF0000FFL, hsvToArgb(240f, 1f, 1f))
        // 色相循环与越界钳制
        assertEquals(0xFFFF0000L, hsvToArgb(360f, 1f, 1f))
        assertEquals(0xFF00FF00L, hsvToArgb(480f, 1f, 1f))
        assertEquals(0xFFFFFFFFL, hsvToArgb(0f, 0f, 2f))
        assertEquals(0xFF000000L, hsvToArgb(0f, 0f, -1f))
    }

    @Test
    fun `通道输入只接受数字并钳制`() {
        assertEquals(0, parseChannelByte("0"))
        assertEquals(255, parseChannelByte(" 255 "))
        assertEquals(200, parseChannelByte("200"))
        assertEquals(255, parseChannelByte("999"))
        assertNull(parseChannelByte(""))
        assertNull(parseChannelByte("   "))
        assertNull(parseChannelByte("-1"))
        assertNull(parseChannelByte("1a"))
        assertNull(parseChannelByte("1 2"))
    }
}
