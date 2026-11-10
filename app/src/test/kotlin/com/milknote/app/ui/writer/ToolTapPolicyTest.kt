package com.milknote.app.ui.writer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolTapPolicyTest {
    @Test
    fun `首次点击切换工具 再次点击打开设置`() {
        assertEquals(ToolTapOutcome.ACTIVATE, resolveToolTap(false))
        assertEquals(ToolTapOutcome.OPEN_SETTINGS, resolveToolTap(true))
    }

    @Test
    fun `中央面板互斥且返回或外部关闭后清空`() {
        val initial = WriterPanelState()
        val pen = initial.open(WriterPanel.TOOL)
        val text = pen.open(WriterPanel.TEXT)
        assertEquals(WriterPanel.TOOL, pen.open)
        assertEquals(WriterPanel.TEXT, text.open)
        assertNull(text.dismiss().open)
        assertNull(initial.open)
    }
}
