package com.milknote.app.ui.writer

import com.milknote.notes.model.NotebookPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageNavigationTest {
    private val pages = listOf("p0001", "p0002", "p0003").map { NotebookPage("nb", it, 0L) }

    @Test fun `前后翻页使用页面列表顺序`() {
        assertEquals("p0001", adjacentPageId(pages, "p0002", -1))
        assertEquals("p0003", adjacentPageId(pages, "p0002", 1))
    }

    @Test fun `首尾页越界时不翻页也不自动新建`() {
        assertNull(adjacentPageId(pages, "p0001", -1))
        assertNull(adjacentPageId(pages, "p0003", 1))
    }

    @Test fun `未知页或空列表不翻页`() {
        assertNull(adjacentPageId(pages, "missing", 1))
        assertNull(adjacentPageId(emptyList(), "p0001", 1))
    }

    @Test fun `界面可用状态与页码边界一致`() {
        val first = WriterUiState(pages = pages, currentPageId = "p0001")
        val last = WriterUiState(pages = pages, currentPageId = "p0003")
        assertEquals(0, first.currentPageIndex)
        assertFalse(first.canPreviousPage)
        assertTrue(first.canNextPage)
        assertEquals(2, last.currentPageIndex)
        assertTrue(last.canPreviousPage)
        assertFalse(last.canNextPage)
    }

    @Test fun `删除页后优先停留下一页 否则上一页`() {
        assertEquals("p0002", pageIdAfterDelete(pages, "p0001"))
        assertEquals("p0003", pageIdAfterDelete(pages, "p0002"))
        assertEquals("p0002", pageIdAfterDelete(pages, "p0003"))
    }

    @Test fun `删除唯一页后没有相邻页 由调用方补空白页`() {
        assertNull(pageIdAfterDelete(pages.take(1), "p0001"))
        assertNull(pageIdAfterDelete(pages, "missing"))
    }
}
