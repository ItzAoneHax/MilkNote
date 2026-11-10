package com.milknote.app.ui.bookshelf

import com.milknote.notes.model.PageSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PDF 导入纯逻辑单测:文件名回退/清洗 与 PDF pt→mm/px 几何。
 * 不依赖 Android runtime,可在 JVM 单测直接运行。
 */
class PdfImportSupportTest {

    // ---- 几何 ----

    @Test
    fun `points to mm uses 72 points per inch`() {
        assertEquals(25.4f, PdfImportGeometry.pointsToMm(72), 1e-4f)
        assertEquals(12.7f, PdfImportGeometry.pointsToMm(36), 1e-4f)
        assertEquals(209.9f, PdfImportGeometry.pointsToMm(595), 0.2f)
        assertEquals(297.1f, PdfImportGeometry.pointsToMm(842), 0.2f)
    }

    @Test
    fun `page spec preserves pdf physical size and orientation`() {
        val a4 = PdfImportGeometry.pageSpec(595, 842)
        assertEquals(209.9f, a4.widthMm, 0.2f)
        assertEquals(297.1f, a4.heightMm, 0.2f)
        assertEquals("blank", a4.paper)
        assertEquals("#FFFFFF", a4.paperColor)
        assertEquals("portrait", PdfImportGeometry.orientationOf(a4))

        val landscape = PdfImportGeometry.pageSpec(842, 595)
        assertTrue(landscape.widthMm > landscape.heightMm)
        assertEquals("landscape", PdfImportGeometry.orientationOf(landscape))
        assertEquals(a4.widthMm, landscape.heightMm, 1e-4f)
    }

    @Test
    fun `page size px matches notebook page coordinate scale`() {
        val (width, height) = PdfImportGeometry.pageSizePx(PageSpec(210f, 297f))
        assertEquals(1190.7f, width, 0.05f)
        assertEquals(1683.99f, height, 0.05f)
    }

    @Test
    fun `render size doubles page px and keeps aspect within cap`() {
        val normal = PdfImportGeometry.renderSizePx(PageSpec(210f, 297f))
        assertEquals(2381, normal.width)
        assertEquals(3368, normal.height)

        val capped = PdfImportGeometry.renderSizePx(PageSpec(1000f, 100f), maxEdgePx = 4096)
        assertEquals(4096, capped.width)
        assertEquals(410, capped.height)
        assertTrue(capped.width.toFloat() / capped.height in 9.9f..10.1f)
    }

    @Test
    fun `nearest page size maps common pdf sizes`() {
        assertEquals("A4", PdfImportGeometry.nearestPageSize(PdfImportGeometry.pageSpec(595, 842)))
        assertEquals("A5", PdfImportGeometry.nearestPageSize(PageSpec(148f, 210f)))
        assertEquals("B5", PdfImportGeometry.nearestPageSize(PageSpec(176f, 250f)))
    }

    // ---- 显示名 / 文件名 ----

    @Test
    fun `display name strips directories and pdf suffix`() {
        assertEquals("讲义", PdfImportNaming.displayName(" 讲义.PDF "))
        assertEquals("report", PdfImportNaming.displayName("dir/sub/report.pdf"))
        assertEquals("file", PdfImportNaming.displayName("C:\\docs\\file.pdf"))
    }

    @Test
    fun `display name falls back to uri hint then default`() {
        assertEquals("课程讲义", PdfImportNaming.displayName(null, "课程讲义.pdf"))
        assertEquals("Fallback", PdfImportNaming.displayName("   ", "Fallback"))
        assertEquals(PdfImportNaming.DEFAULT_NOTEBOOK_NAME, PdfImportNaming.displayName(null, null))
        assertEquals(PdfImportNaming.DEFAULT_NOTEBOOK_NAME, PdfImportNaming.displayName("  .pdf "))
    }

    @Test
    fun `sanitize removes illegal filename characters and collapses whitespace`() {
        assertEquals("a b c d e f g h", PdfImportNaming.sanitize("a:b*c?d\"e<f>g|h"))
        assertEquals("报告 2024", PdfImportNaming.sanitize("  报告   2024  "))
        assertEquals("bad name", PdfImportNaming.sanitize("bad\u0000name"))
        assertEquals("v1.2 讲义", PdfImportNaming.sanitize("v1.2 讲义"))
        assertEquals("报告", PdfImportNaming.sanitize("报告."))
    }

    @Test
    fun `sanitize falls back and limits length`() {
        assertEquals(PdfImportNaming.DEFAULT_NOTEBOOK_NAME, PdfImportNaming.sanitize("..."))
        assertEquals(PdfImportNaming.DEFAULT_NOTEBOOK_NAME, PdfImportNaming.notebookName(" .pdf "))
        assertEquals(
            PdfImportNaming.MAX_NAME_LENGTH,
            PdfImportNaming.sanitize("中".repeat(80)).length,
        )
        assertEquals("讲义", PdfImportNaming.notebookName(" 讲义.pdf "))
        assertEquals("Fallback", PdfImportNaming.notebookName(null, "Fallback"))
    }
}
