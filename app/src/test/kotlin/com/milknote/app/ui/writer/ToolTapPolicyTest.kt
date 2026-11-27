package com.milknote.app.ui.writer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolTapPolicyTest {
    @Test
    fun `首击切换 再击设置 面板开启时再击关闭`() {
        assertEquals(ToolTapOutcome.ACTIVATE, resolveToolTap(false))
        assertEquals(ToolTapOutcome.ACTIVATE, resolveToolTap(false, settingsOpen = true))
        assertEquals(ToolTapOutcome.OPEN_SETTINGS, resolveToolTap(true))
        assertEquals(ToolTapOutcome.CLOSE_SETTINGS, resolveToolTap(true, settingsOpen = true))
    }

    @Test
    fun `工具与文档面板互斥且关闭后清空`() {
        val initial = WriterPanelState()
        val pen = initial.open(WriterPanel.TOOL)
        val text = pen.open(WriterPanel.TEXT)
        val shape = text.open(WriterPanel.SHAPE)
        assertEquals(WriterPanel.TOOL, pen.open)
        assertEquals(WriterPanel.TEXT, text.open)
        assertEquals(WriterPanel.SHAPE, shape.open)
        assertNull(shape.dismiss().open)
        assertNull(initial.open)
    }

    @Test
    fun `任一目标面板都替换旧面板而不是叠层`() {
        WriterPanel.entries.forEach { target ->
            val state = WriterPanelState(WriterPanel.MORE).open(target)
            assertEquals(target, state.open)
        }
    }

    @Test
    fun `退出阅读模式恢复进入前的书写工具 否则回退钢笔`() {
        assertEquals(WriterMajorTool.HIGHLIGHTER, restoreToolAfterReading(WriterMajorTool.HIGHLIGHTER))
        assertEquals(WriterMajorTool.ERASER, restoreToolAfterReading(WriterMajorTool.ERASER))
        assertEquals(WriterMajorTool.BRUSH, restoreToolAfterReading(WriterMajorTool.READING))
        assertEquals(WriterMajorTool.BRUSH, restoreToolAfterReading(WriterMajorTool.SNIP))
    }
}
