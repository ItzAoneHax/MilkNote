package com.milknote.app.ui.writer

import com.milknote.ink.model.InkTool
import com.milknote.ink.model.PressureGamma
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
    fun `橡皮三档保留原笔且实际 effective 宽度生效`() {
        val pen = WriterToolSettings().selectColor(0xFF102030).selectWidth(1.2f)
        val small = pen.selectEraserWidth(2f)
        assertEquals(2f, small.effective.baseWidthMm)
        assertEquals(InkTool.ERASER, small.effective.tool)
        val large = small.selectEraserWidth(9f)
        assertEquals(9f, large.effective.baseWidthMm)
        assertEquals(0xFF102030, large.current.color)
        val restored = large.toggleEraser()
        assertEquals(pen.current, restored.current)
        assertFalse(restored.eraserTemporary)
        assertEquals(9f, restored.eraserWidthMm)
    }

    @Test
    fun `激光与图案笔可切换且激光有独立色板`() {
        val laser = WriterToolSettings().selectTool(InkTool.LASER).selectColor(0xFF00FF00)
        assertEquals(InkTool.LASER, laser.effective.tool)
        assertEquals(0xFF00FF00, laser.effective.color)
        val doodle = laser.selectTool(InkTool.DOODLE)
        assertEquals(InkTool.DOODLE, doodle.effective.tool)
        assertNotEquals(doodle.palettes[InkTool.DOODLE], doodle.palettes[InkTool.LASER])
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

    @Test
    fun `橡皮内容开关可关类型但不可全部关闭`() {
        val off = WriterToolSettings().setEraserTarget(InkTool.HIGHLIGHTER, false)
        assertFalse(InkTool.HIGHLIGHTER in off.eraserTargets)
        assertTrue(InkTool.PEN in off.eraserTargets)
        // 再关掉剩余所有类别也应被拒绝,保留最后一类
        var s = off
        for (tool in listOf(
            InkTool.PEN, InkTool.BALL, InkTool.DOODLE, InkTool.LINE, InkTool.DASHED_LINE,
            InkTool.ARROW, InkTool.RECT, InkTool.OVAL, InkTool.TRIANGLE, InkTool.STAR, InkTool.POLYGON,
        )) s = s.setEraserTarget(tool, false)
        assertFalse(s.eraserTargets.isEmpty())
    }

    @Test
    fun `自动切回开关可切换`() {
        assertTrue(!WriterToolSettings().eraserAutoRevert)
        assertTrue(WriterToolSettings().setEraserAutoRevert(true).eraserAutoRevert)
    }

    @Test
    fun `压感档只覆盖当前笔且激光不响应`() {
        val pen = WriterToolSettings().selectPressureGamma(PressureGamma.SENSITIVE)
        assertEquals(PressureGamma.SENSITIVE, pen.current.pressureGamma)
        // 其它笔保持默认
        assertEquals(PressureGamma.STANDARD, pen.copy(currentTool = InkTool.BALL).current.pressureGamma)
        // 激光不响应
        val laser = pen.copy(currentTool = InkTool.LASER).selectPressureGamma(PressureGamma.SENSITIVE)
        assertEquals(PressureGamma.STANDARD, laser.current.pressureGamma)
        // 相同档不产生新对象
        assertEquals(pen, pen.selectPressureGamma(PressureGamma.SENSITIVE))
    }

    @Test
    fun `笔盒收藏编码往返保真 且非法分隔符与坏段被清洗`() {
        val list = listOf(
            PenPreset("a", "钢笔 · 墨", InkTool.PEN, 0xFF18191B, 0.7f),
            PenPreset("b", "荧光|黄;粗", InkTool.HIGHLIGHTER, 0xFFFFD65A, 2f),
        )
        val decoded = PenPresets.decode(PenPresets.encode(list) + ";garbage")
        assertEquals(2, decoded.size)
        assertEquals(InkTool.PEN, decoded[0].tool)
        assertEquals(0xFF18191B, decoded[0].color)
        assertEquals(0.7f, decoded[0].widthMm, 1e-6f)
        assertEquals(InkTool.HIGHLIGHTER, decoded[1].tool)
        assertEquals("荧光 黄 粗", decoded[1].name)
        assertTrue(decoded.none { it.name.contains('|') || it.name.contains(';') })
        assertTrue(PenPresets.decode("").isEmpty())
    }
}
