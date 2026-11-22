package com.milknote.app.ui.writer

import com.milknote.ink.model.InkTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WriterMajorToolTest {
    @Test
    fun `主工具顺序与 P016 完全一致`() {
        assertEquals(
            listOf(
                WriterMajorTool.BRUSH,
                WriterMajorTool.HIGHLIGHTER,
                WriterMajorTool.ERASER,
                WriterMajorTool.LASSO,
                WriterMajorTool.IMAGE,
                WriterMajorTool.TEXT,
                WriterMajorTool.DOODLE,
                WriterMajorTool.SHAPE,
                WriterMajorTool.SNIP,
                WriterMajorTool.TAPE,
                WriterMajorTool.READING,
                WriterMajorTool.LASER,
            ),
            writerMajorToolOrder,
        )
    }

    @Test
    fun `完整工具集合不受容量或历史 pins 配置影响`() {
        assertEquals(WriterMajorTool.entries.toSet(), writerMajorToolOrder.toSet())
        assertEquals(WriterMajorTool.entries.size, writerMajorToolOrder.size)
        assertEquals(WriterMajorTool.entries.size, writerMajorToolOrder.map { it.id }.distinct().size)
        assertEquals(WriterMajorTool.LASER, writerMajorToolOrder.last())
    }

    @Test
    fun `辅助栏随笔和橡皮切换颜色及圆点档位`() {
        val settings = WriterToolSettings()
        val pen = writerMinorSpec(WriterMajorTool.BRUSH, settings)
        assertEquals(WriterMinorKind.PEN, pen.kind)
        assertEquals(settings.palettes[InkTool.PEN]?.take(5), pen.colors)
        assertEquals(listOf(0.4f, 0.7f, 1.2f), pen.widths)

        val marker = writerMinorSpec(WriterMajorTool.HIGHLIGHTER, settings)
        assertEquals(WriterMinorKind.HIGHLIGHTER, marker.kind)
        assertEquals(settings.palettes[InkTool.HIGHLIGHTER]?.take(5), marker.colors)
        assertEquals(listOf(1.2f, 2f, 3f), marker.widths)

        val eraser = writerMinorSpec(WriterMajorTool.ERASER, settings)
        assertEquals(WriterMinorKind.ERASER, eraser.kind)
        assertEquals(listOf(2f, 5f, 9f), eraser.widths)
        assertTrue(eraser.colors.isEmpty())
    }

    @Test
    fun `辅助栏覆盖所有工具状态且激光仅显示三色`() {
        val settings = WriterToolSettings()
        assertEquals(WriterMinorKind.LASSO, writerMinorSpec(WriterMajorTool.LASSO, settings).kind)
        assertEquals(WriterMinorKind.IMAGE, writerMinorSpec(WriterMajorTool.IMAGE, settings).kind)
        assertEquals(WriterMinorKind.TEXT, writerMinorSpec(WriterMajorTool.TEXT, settings).kind)
        assertEquals(WriterMinorKind.SHAPE, writerMinorSpec(WriterMajorTool.SHAPE, settings).kind)
        assertEquals(WriterMinorKind.SNIP, writerMinorSpec(WriterMajorTool.SNIP, settings).kind)
        assertEquals(WriterMinorKind.TAPE, writerMinorSpec(WriterMajorTool.TAPE, settings).kind)
        assertEquals(WriterMinorKind.READING, writerMinorSpec(WriterMajorTool.READING, settings).kind)
        val laser = writerMinorSpec(WriterMajorTool.LASER, settings)
        assertEquals(WriterMinorKind.LASER, laser.kind)
        assertEquals(settings.palettes[InkTool.LASER]?.take(3), laser.colors)
        assertTrue(laser.widths.isEmpty())
    }

    @Test
    fun `画笔保留圆珠笔且图案笔使用独立色板`() {
        val ball = WriterToolSettings().selectTool(InkTool.BALL)
        assertEquals(InkTool.BALL, writerInkTool(WriterMajorTool.BRUSH, ball))
        assertEquals(WriterMinorKind.PEN, writerMinorSpec(WriterMajorTool.DOODLE, ball).kind)
        assertEquals(ball.palettes[InkTool.DOODLE]?.take(5), writerMinorSpec(WriterMajorTool.DOODLE, ball).colors)
    }

    @Test
    fun `双列页面栏仅当前页所在行不可见才滚动`() {
        val pages = (1..20).map { "p%04d".format(it) }
        assertEquals(5, pageRailScrollTarget(pages, "p0012", visibleRows = emptySet()))
        assertEquals(9, pageRailScrollTarget(pages, "p0020", visibleRows = setOf(0, 1, 2)))
        assertNull(pageRailScrollTarget(pages, "p0012", visibleRows = setOf(4, 5, 6)))
        assertNull(pageRailScrollTarget(pages, "missing", visibleRows = emptySet()))
    }

    @Test
    fun `页面与摘取栏互斥且再次点击可收起`() {
        val pages = toggleWriterRail(null, WriterRailMode.PAGES)
        assertEquals(WriterRailMode.PAGES, pages)
        val snippets = toggleWriterRail(pages, WriterRailMode.SNIPPETS)
        assertEquals(WriterRailMode.SNIPPETS, snippets)
        assertNull(toggleWriterRail(snippets, WriterRailMode.SNIPPETS))
        assertEquals(WriterRailMode.PAGES, toggleWriterRail(snippets, WriterRailMode.PAGES))
    }
}
