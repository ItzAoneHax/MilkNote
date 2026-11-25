package com.milknote.app.ui.agent

import com.milknote.agent.AgentMessage
import com.milknote.agent.AgentUiState
import com.milknote.agent.Attachment
import com.milknote.agent.DraftAttachment
import com.milknote.agent.PageRef
import com.milknote.agent.UiMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentFloatingPreviewTest {

    private fun draft(
        id: String = "d1",
        png: ByteArray = byteArrayOf(1, 2, 3),
        source: Attachment.Source = Attachment.Source.SYSTEM_IMAGE,
        textContent: String = "",
        pages: List<ByteArray> = emptyList(),
        pageRangeLabel: String = "",
    ) = DraftAttachment(
        id = id,
        logicalName = "clip_800×600.png",
        widthPx = 800,
        heightPx = 600,
        png = png,
        jpeg = false,
        anchor = null,
        source = source,
        textContent = textContent,
        pageRangeLabel = pageRangeLabel,
        extraPages = pages.mapIndexed { index, bytes ->
            com.milknote.agent.DraftPageImage(
                pageNumber = index + 1,
                png = bytes,
                widthPx = 800,
                heightPx = 600,
            )
        },
    )

    private fun attachment(
        id: String = "a1",
        source: Attachment.Source = Attachment.Source.PDF_DOC,
        pageFiles: List<String> = listOf("agent/att/abc.png", "agent/att/def.png"),
        textContent: String = "第 1 页正文",
    ) = Attachment(
        id = id,
        file = "agent/att/abc.png",
        sha1 = "abc",
        widthPx = 800,
        heightPx = 600,
        source = source,
        logicalName = "lecture.pdf",
        pageFiles = pageFiles,
        textContent = textContent,
    )

    @Test
    fun `草稿单图转为可编辑图片预览`() {
        val target = draft().toPreviewTarget()
        assertTrue(target is AgentPreviewTarget.Image)
        val image = target as AgentPreviewTarget.Image
        assertEquals("d1", image.draftId)
        assertTrue(image.canEdit)
        assertTrue(image.bytes!!.isNotEmpty())
        assertEquals("800×600", image.subtitle)
    }

    @Test
    fun `草稿多页文档转为逐页图预览`() {
        val target = draft(png = ByteArray(0), pages = listOf(byteArrayOf(1), byteArrayOf(2))).toPreviewTarget()
        assertTrue(target is AgentPreviewTarget.Document)
        val document = target as AgentPreviewTarget.Document
        assertEquals(2, document.pageBytes.size)
        assertTrue(document.pageBytes.all { it.isNotEmpty() })
    }

    @Test
    fun `草稿纯文本文档没有字节时转为文本文档`() {
        val target = draft(png = ByteArray(0), textContent = "正文").toPreviewTarget()
        assertTrue(target is AgentPreviewTarget.Document)
        assertEquals("正文", (target as AgentPreviewTarget.Document).textContent)
    }

    @Test
    fun `已落盘 PDF 附件保留逐页文件引用`() {
        val target = attachment().toPreviewTarget(notebookId = "nb1")
        assertTrue(target is AgentPreviewTarget.Document)
        val document = target as AgentPreviewTarget.Document
        assertEquals("nb1", document.notebookId)
        assertEquals(2, document.pageFiles.size)
        assertEquals(Attachment.Source.PDF_DOC, document.source)
    }

    @Test
    fun `已落盘 Markdown 附件走渲染预览且不写回笔记`() {
        val target = attachment(
            source = Attachment.Source.MARKDOWN_DOC,
            pageFiles = emptyList(),
            textContent = "# 标题",
        ).toPreviewTarget(notebookId = "nb1")
        assertTrue(target is AgentPreviewTarget.Markdown)
        val markdown = target as AgentPreviewTarget.Markdown
        assertEquals("# 标题", markdown.content)
        assertFalse(markdown.canSaveToNotes)
    }

    @Test
    fun `生成文档标题为空时按类型兜底`() {
        val latex = MarkdownLite.Block.GeneratedDocument(
            title = "",
            type = MarkdownLite.GeneratedDocType.LATEX,
            content = "E=mc^2",
        ).toPreviewTarget() as AgentPreviewTarget.Markdown
        assertEquals("LaTeX 文档", latex.title)
        assertEquals(MarkdownLite.GeneratedDocType.LATEX, latex.docType)
    }

    @Test
    fun `LaTeX 缺定界符时补公式包裹`() {
        val bare = AgentPreviewTarget.Markdown(
            title = "公式",
            content = "E=mc^2",
            docType = MarkdownLite.GeneratedDocType.LATEX,
        )
        assertEquals("$$\nE=mc^2\n$$", prepareMarkdownForPreview(bare))

        val delimited = bare.copy(content = "$$\nE=mc^2\n$$")
        assertEquals("$$\nE=mc^2\n$$", prepareMarkdownForPreview(delimited))

        val markdown = bare.copy(docType = MarkdownLite.GeneratedDocType.MARKDOWN, content = "正文")
        assertEquals("正文", prepareMarkdownForPreview(markdown))
    }

    @Test
    fun `会话预览项按草稿附件消息附件生成文档顺序汇总并去重`() {
        val document = AgentMessage(
            id = "m1",
            role = AgentMessage.Role.ASSISTANT,
            sentAt = 1L,
            text = "结果如下\n```milkdoc 提纲\n第一段\n```",
        )
        val state = AgentUiState(
            boundPage = PageRef("nb1", "p0001"),
            draftAttachments = listOf(draft(id = "d1")),
            messages = listOf(
                UiMessage(
                    id = "m0",
                    role = AgentMessage.Role.USER,
                    text = "看一下",
                    attachments = listOf(attachment(id = "a1")),
                ),
                document.toUi(),
            ),
        )

        val targets = collectSessionPreviewItems(
            state = state,
            pageMarkdown = "# 当前页",
            pageLabel = "第 1 页",
            pageIds = listOf("p0001"),
        )

        assertEquals(4, targets.size)
        assertEquals(1, targets.count { it is AgentPreviewTarget.Image })
        assertEquals(1, targets.count { it is AgentPreviewTarget.Document })
        assertEquals(2, targets.count { it is AgentPreviewTarget.Markdown })
        assertTrue(targets.first() is AgentPreviewTarget.Image)
        assertEquals("第 1 页", targets.last().title)

        // 同一份内容重复出现只保留一份
        val duplicated = collectSessionPreviewItems(
            state = state.copy(draftAttachments = listOf(draft(id = "d1"), draft(id = "d1"))),
            pageMarkdown = "# 当前页",
            pageLabel = "第 1 页",
            pageIds = listOf("p0001"),
        )
        assertEquals(targets.size, duplicated.size)
    }

    private fun AgentMessage.toUi() = UiMessage(
        id = id,
        role = role,
        text = text,
    )
}
