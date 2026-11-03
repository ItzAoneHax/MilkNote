package com.milknote.app.ui.writer

import com.milknote.ink.model.InkTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WriterToolSettingsTest {

    @Test
    fun `三种笔使用不同默认色板`() {
        val settings = WriterToolSettings()
        assertNotEquals(settings.palettes[InkTool.PEN], settings.palettes[InkTool.BALL])
        assertNotEquals(settings.palettes[InkTool.PEN], settings.palettes[InkTool.HIGHLIGHTER])
        assertNotEquals(settings.palettes[InkTool.BALL], settings.palettes[InkTool.HIGHLIGHTER])
    }

    @Test
    fun `切笔恢复各自颜色和粗细`() {
        val pen = WriterToolSettings()
            .selectColor(0xFF102030)
            .selectWidth(1.2f)
        val ball = pen.selectTool(InkTool.BALL)
            .selectColor(0xFF405060)
            .selectWidth(0.4f)

        val restoredPen = ball.selectTool(InkTool.PEN)
        assertEquals(0xFF102030, restoredPen.current.color)
        assertEquals(1.2f, restoredPen.current.baseWidthMm)

        val restoredBall = restoredPen.selectTool(InkTool.BALL)
        assertEquals(0xFF405060, restoredBall.current.color)
        assertEquals(0.4f, restoredBall.current.baseWidthMm)
    }

    @Test
    fun `选色不会自动切换笔`() {
        val settings = WriterToolSettings()
            .selectTool(InkTool.BALL)
            .selectColor(0xFFFFD65A)
        assertEquals(InkTool.BALL, settings.current.tool)
        assertEquals(0xFFFFD65A, settings.current.color)
    }

    @Test
    fun `自定义颜色只更新当前笔的对应槽位`() {
        val original = WriterToolSettings().selectTool(InkTool.BALL)
        val otherPalettes = original.palettes.toMap()
        val custom = 0xFF123ABC
        val updated = original.updatePaletteColor(2, custom)

        assertEquals(custom, updated.current.color)
        assertEquals(custom, updated.palettes.getValue(InkTool.BALL)[2])
        assertEquals(otherPalettes[InkTool.PEN], updated.palettes[InkTool.PEN])
        assertEquals(otherPalettes[InkTool.HIGHLIGHTER], updated.palettes[InkTool.HIGHLIGHTER])
        assertEquals(otherPalettes.getValue(InkTool.BALL)[1], updated.palettes.getValue(InkTool.BALL)[1])
    }

    @Test
    fun `临时橡皮保留原笔配置`() {
        val pen = WriterToolSettings().selectColor(0xFF102030).selectWidth(1.2f)
        val eraser = pen.toggleEraser()
        assertEquals(InkTool.PEN, eraser.currentTool)
        assertEquals(InkTool.ERASER, eraser.effective.tool)
        assertEquals(4f, eraser.effective.baseWidthMm)
        assertEquals(0xFF102030, eraser.current.color)
        assertEquals(1.2f, eraser.current.baseWidthMm)
        assertEquals(pen, eraser.toggleEraser())
    }

    @Test
    fun `临时橡皮下选色改宽会恢复原笔`() {
        val eraser = WriterToolSettings().toggleEraser()
        val colored = eraser.selectColor(0xFF405060)
        assertEquals(false, colored.eraserTemporary)
        assertEquals(InkTool.PEN, colored.effective.tool)
        assertEquals(0xFF405060, colored.current.color)
        val resized = eraser.selectWidth(0.4f)
        assertEquals(false, resized.eraserTemporary)
        assertEquals(0.4f, resized.current.baseWidthMm)
    }

    @Test
    fun `临时橡皮下自定义颜色也会恢复原笔`() {
        val updated = WriterToolSettings().toggleEraser().updatePaletteColor(1, 0xFF123ABC)
        assertEquals(false, updated.eraserTemporary)
        assertEquals(InkTool.PEN, updated.effective.tool)
        assertEquals(0xFF123ABC, updated.current.color)
    }
}
