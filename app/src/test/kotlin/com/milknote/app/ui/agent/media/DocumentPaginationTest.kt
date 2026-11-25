package com.milknote.app.ui.agent.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Markdown 分页器 + 图片编辑纯几何状态(全部纯 Kotlin,JVM 可直接跑) */
class DocumentPaginationTest {

    // ---------------------------------------------------------------- MarkdownPaginator

    @Test
    fun `空白文档也至少一页`() {
        assertEquals(listOf(""), MarkdownPaginator.paginate(""))
        assertEquals(listOf(""), MarkdownPaginator.paginate("   \n\n\t "))
    }

    @Test
    fun `短文档只有一页`() {
        val pages = MarkdownPaginator.paginate("# 笔记\n\n第一段\n第二段")
        assertEquals(1, pages.size)
        assertEquals("# 笔记\n第一段\n第二段", pages[0])
    }

    @Test
    fun `独占一行的水平线是硬分页`() {
        val pages = MarkdownPaginator.paginate("第一页内容\n\n---\n\n第二页内容\n\n***\n\n第三页内容")
        assertEquals(listOf("第一页内容", "第二页内容", "第三页内容"), pages)
    }

    @Test
    fun `短页遇到标题不分页 长页遇到标题另起一页`() {
        val shortDoc = MarkdownPaginator.paginate("# 标题\n\n短段落")
        assertEquals(1, shortDoc.size)

        val longDoc = MarkdownPaginator.paginate("铺垫".repeat(300) + "\n\n## 新章节\n\n正文")
        assertEquals(2, longDoc.size)
        assertTrue(longDoc[0].length >= 400)
        assertEquals("## 新章节\n正文", longDoc[1])
    }

    @Test
    fun `围栏代码块不会被拦腰截断`() {
        val codeLines = (1..30).joinToString("\n") { "val v$it = $it" }
        val markdown = "# 练习\n\n```kotlin\n$codeLines\n```\n\n结束语"
        val pages = MarkdownPaginator.paginate(markdown, maxCharsPerPage = 300)

        val codePage = pages.single { it.contains("```kotlin") }
        assertEquals(1, Regex("```kotlin").findAll(codePage).count())
        assertTrue(codePage.trimEnd().endsWith("```"))
        assertTrue(codePage.contains("val v30 = 30"))
        assertFalse(pages.any { it.trim() == "```kotlin" || it.trim() == "```" })
    }

    @Test
    fun `代码块里的水平线不算分页`() {
        val pages = MarkdownPaginator.paginate("```\n---\n***\n```")
        assertEquals(1, pages.size)
        assertTrue(pages[0].contains("---"))
        assertTrue(pages[0].contains("***"))
    }

    @Test
    fun `超长纯文本按上限切分成多页`() {
        val pages = MarkdownPaginator.paginate("字".repeat(5000))
        assertEquals(3, pages.size)
        assertTrue(pages.all { it.length <= 1800 })
        assertEquals(5000, pages.sumOf { it.length })
    }

    @Test
    fun `长句在句末标点处切分`() {
        val sentence = "这是一句用于测试分页的句子。"
        val pages = MarkdownPaginator.paginate(sentence.repeat(400))
        assertTrue(pages.size > 1)
        pages.forEach { page ->
            assertTrue("页面必须以句号收尾: …${page.takeLast(4)}", page.endsWith("。"))
        }
    }

    @Test
    fun `自定义上限生效`() {
        val pages = MarkdownPaginator.paginate("a".repeat(1000), maxCharsPerPage = 400)
        assertTrue(pages.size >= 3)
        assertTrue(pages.all { it.length <= 400 })
    }

    // ---------------------------------------------------------------- NormalizedCropRect

    @Test
    fun `裁剪框收敛到 0 到 1`() {
        val rect = NormalizedCropRect(left = -0.2f, top = 0.1f, right = 1.4f, bottom = 0.9f).clamped()
        assertEquals(0f, rect.left, EPS)
        assertEquals(0.1f, rect.top, EPS)
        assertEquals(1f, rect.right, EPS)
        assertEquals(0.9f, rect.bottom, EPS)
    }

    @Test
    fun `反向拖拽自动交换边`() {
        val rect = NormalizedCropRect(left = 0.8f, top = 0.9f, right = 0.2f, bottom = 0.1f).clamped()
        assertEquals(0.2f, rect.left, EPS)
        assertEquals(0.1f, rect.top, EPS)
        assertEquals(0.8f, rect.right, EPS)
        assertEquals(0.9f, rect.bottom, EPS)
    }

    @Test
    fun `退化框至少保留最小边长`() {
        val rect = NormalizedCropRect(0.5f, 0.5f, 0.5f, 0.5f).clamped()
        assertTrue(rect.right - rect.left >= NormalizedCropRect.MIN_SPAN - EPS)
        assertTrue(rect.bottom - rect.top >= NormalizedCropRect.MIN_SPAN - EPS)
        assertTrue(rect.left >= 0f && rect.right <= 1f)
        assertTrue(rect.top >= 0f && rect.bottom <= 1f)
    }

    @Test
    fun `NaN 输入退化为整图`() {
        val rect = NormalizedCropRect(Float.NaN, Float.NaN, Float.NaN, Float.NaN).clamped()
        assertTrue(rect.isFullFrame())
    }

    @Test
    fun `整图判定`() {
        assertTrue(NormalizedCropRect().isFullFrame())
        assertTrue(NormalizedCropRect(0f, 0f, 1f, 1f).isFullFrame())
        assertFalse(NormalizedCropRect(0f, 0f, 0.5f, 1f).isFullFrame())
        assertFalse(NormalizedCropRect(0.1f, 0.1f, 0.9f, 0.9f).isFullFrame())
    }

    @Test
    fun `按比例吸附 1比1`() {
        // 2000×1000 的图(像素宽高比 2):1:1 的框归一化后是 0.5 × 1
        val rect = NormalizedCropRect().withAspect(CropAspect.SQUARE, imageAspect = 2f)
        assertEquals(0.5f, rect.right - rect.left, 1e-3f)
        assertEquals(1f, rect.bottom - rect.top, 1e-3f)
        assertEquals(0.25f, rect.left, 1e-3f)
        assertEquals(0.75f, rect.right, 1e-3f)
    }

    @Test
    fun `按比例吸附 4比3 与 16比9`() {
        val fourThree = NormalizedCropRect().withAspect(CropAspect.RATIO_4_3, imageAspect = 2f)
        assertEquals(4f / 3f, ((fourThree.right - fourThree.left) / (fourThree.bottom - fourThree.top)) * 2f, 1e-3f)

        val sixteenNine = NormalizedCropRect().withAspect(CropAspect.RATIO_16_9, imageAspect = 16f / 9f)
        assertTrue(sixteenNine.isFullFrame())
    }

    @Test
    fun `吸附比例不会越出当前框`() {
        val base = NormalizedCropRect(0.4f, 0f, 0.6f, 1f)
        val rect = base.withAspect(CropAspect.SQUARE, imageAspect = 1f)
        assertTrue(rect.left >= base.left - 1e-4f)
        assertTrue(rect.right <= base.right + 1e-4f)
        assertTrue(rect.top >= base.top - 1e-4f)
        assertTrue(rect.bottom <= base.bottom + 1e-4f)
    }

    @Test
    fun `自由比例与非法参数原样返回`() {
        val base = NormalizedCropRect(0.2f, 0.2f, 0.8f, 0.8f)
        assertEquals(base.clamped(), base.withAspect(CropAspect.FREE, imageAspect = 2f))
        assertEquals(base.clamped(), base.withAspect(CropAspect.SQUARE, imageAspect = 0f))
        assertEquals(base.clamped(), base.withAspect(CropAspect.SQUARE, imageAspect = Float.NaN))
    }

    // ---------------------------------------------------------------- ImageEditState

    @Test
    fun `旋转按 90 度循环`() {
        val state = ImageEditState()
        assertEquals(90, state.rotateRight().rotationDeg)
        assertEquals(270, state.rotateLeft().rotationDeg)
        assertEquals(0, state.rotateRight().rotateRight().rotateRight().rotateRight().rotationDeg)
        assertEquals(180, state.rotateLeft().rotateLeft().rotationDeg)
    }

    @Test
    fun `非法角度被吸附到 90 度倍数`() {
        assertEquals(90, ImageEditState(rotationDeg = 100).sanitized().rotationDeg)
        assertEquals(270, ImageEditState(rotationDeg = -90).sanitized().rotationDeg)
        assertEquals(0, ImageEditState(rotationDeg = 720).sanitized().rotationDeg)
    }

    @Test
    fun `翻转开关可反复切换`() {
        val state = ImageEditState().toggleFlipH().toggleFlipV()
        assertTrue(state.flipHorizontal)
        assertTrue(state.flipVertical)
        assertFalse(state.toggleFlipH().flipHorizontal)
    }

    @Test
    fun `涂鸦撤销与清空`() {
        val stroke = DoodleStroke(0xFFD2483C, 0.01f, listOf(DoodlePoint(0.1f, 0.1f), DoodlePoint(0.2f, 0.3f)))
        val state = ImageEditState(strokes = listOf(stroke, stroke, stroke))
        assertEquals(2, state.undoStroke().strokes.size)
        assertEquals(0, state.clearStrokes().strokes.size)
        assertEquals(0, ImageEditState().undoStroke().strokes.size)
    }

    @Test
    fun `重置回到初始状态`() {
        val edited = ImageEditState(
            crop = NormalizedCropRect(0.1f, 0.1f, 0.5f, 0.5f),
            rotationDeg = 90,
            flipHorizontal = true,
            brightness = 20f,
            contrast = 1.3f,
            documentEnhance = true,
            strokes = listOf(DoodleStroke(0xFF000000, 0.01f, listOf(DoodlePoint(0.5f, 0.5f)))),
        )
        assertTrue(edited.hasEdits())
        val reset = edited.reset()
        assertFalse(reset.hasEdits())
        assertEquals(ImageEditState(), reset)
    }

    @Test
    fun `hasEdits 对每种编辑都成立`() {
        assertFalse(ImageEditState().hasEdits())
        assertTrue(ImageEditState(rotationDeg = 90).hasEdits())
        assertTrue(ImageEditState(flipHorizontal = true).hasEdits())
        assertTrue(ImageEditState(flipVertical = true).hasEdits())
        assertTrue(ImageEditState(brightness = 10f).hasEdits())
        assertTrue(ImageEditState(contrast = 1.2f).hasEdits())
        assertTrue(ImageEditState(documentEnhance = true).hasEdits())
        assertTrue(ImageEditState(crop = NormalizedCropRect(0f, 0f, 0.5f, 1f)).hasEdits())
        assertTrue(
            ImageEditState(
                strokes = listOf(DoodleStroke(0xFF000000, 0.01f, listOf(DoodlePoint(0f, 0f)))),
            ).hasEdits(),
        )
    }

    @Test
    fun `明暗与对比度被夹紧`() {
        val sanitized = ImageEditState(brightness = 400f, contrast = 9f).sanitized()
        assertEquals(50f, sanitized.brightness, EPS)
        assertEquals(1.5f, sanitized.contrast, EPS)

        val low = ImageEditState(brightness = -400f, contrast = 0.1f).sanitized()
        assertEquals(-50f, low.brightness, EPS)
        assertEquals(0.5f, low.contrast, EPS)
    }

    private companion object {
        const val EPS = 1e-4f
    }
}
