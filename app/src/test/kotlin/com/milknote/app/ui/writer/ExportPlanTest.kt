package com.milknote.app.ui.writer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPlanTest {

    private val pages = listOf("p0001", "p0002", "p0003", "p0004")

    @Test fun `文件名清洗去掉路径与保留字符`() {
        assertEquals("a_b_c__", sanitizeExportBaseName("  a/b:c*?  "))
        assertEquals("高数 第三章", sanitizeExportBaseName("高数   第三章"))
        assertEquals("报告", sanitizeExportBaseName(" ...报告... "))
    }

    @Test fun `空文件名回退`() {
        assertEquals(EXPORT_FALLBACK_NAME, sanitizeExportBaseName(""))
        assertEquals(EXPORT_FALLBACK_NAME, sanitizeExportBaseName("   "))
        assertEquals(EXPORT_FALLBACK_NAME, sanitizeExportBaseName("..."))
        assertEquals("自定义", sanitizeExportBaseName("", fallback = "自定义"))
    }

    @Test fun `文件名长度有上限`() {
        val long = "笔记".repeat(60)
        val name = sanitizeExportBaseName(long)
        assertTrue(name.length <= EXPORT_MAX_BASE_NAME_LENGTH)
        assertTrue(name.isNotEmpty())
    }

    @Test fun `全部页与当前页范围`() {
        assertEquals(pages, resolveExportPageIds(pages, "p0002", ExportRangeMode.ALL, emptySet()))
        assertEquals(listOf("p0002"), resolveExportPageIds(pages, "p0002", ExportRangeMode.CURRENT, emptySet()))
        assertEquals(emptyList<String>(), resolveExportPageIds(pages, "p9999", ExportRangeMode.CURRENT, emptySet()))
    }

    @Test fun `多选范围保持笔记本页序且忽略不存在页`() {
        val selected = linkedSetOf("p0004", "p0002", "gone")
        assertEquals(
            listOf("p0002", "p0004"),
            resolveExportPageIds(pages, "p0001", ExportRangeMode.SELECTED, selected),
        )
        assertEquals(
            emptyList<String>(),
            resolveExportPageIds(pages, "p0001", ExportRangeMode.SELECTED, emptySet()),
        )
    }

    @Test fun `PDF 与 ZIP 始终单文件`() {
        assertEquals(listOf("笔记.pdf"), exportFileNames("笔记", ExportFormat.PDF, pages))
        assertEquals(listOf("笔记.milk.zip"), exportFileNames("笔记", ExportFormat.ZIP, pages))
        assertEquals(listOf("笔记.milk.zip"), exportFileNames("笔记.milk", ExportFormat.ZIP, pages))
    }

    @Test fun `图片单页使用基础名多页追加页 ID`() {
        assertEquals(listOf("笔记.png"), exportFileNames("笔记", ExportFormat.PNG, listOf("p0002")))
        assertEquals(
            listOf("笔记-p0001.jpg", "笔记-p0002.jpg"),
            exportFileNames("笔记", ExportFormat.JPG, listOf("p0001", "p0002")),
        )
        assertEquals(emptyList<String>(), exportFileNames("笔记", ExportFormat.JPG, emptyList()))
    }

    @Test fun `ZIP 根目录使用 milk 后缀`() {
        assertEquals("笔记.milk", exportZipRootDir("笔记"))
        assertEquals("笔记.milk", exportZipRootDir("笔记.milk"))
    }
}
