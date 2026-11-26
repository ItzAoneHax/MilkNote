package com.milknote.app.ui.agent

import com.milknote.agent.Attachment
import com.milknote.agent.Protocol
import com.milknote.app.ui.common.protocolDisplayLabel
import com.milknote.app.ui.common.protocolShortLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPanelPresentationTest {

    @Test
    fun `token 数按千进位格式化`() {
        assertEquals("0", formatAgentTokenCount(0))
        assertEquals("999", formatAgentTokenCount(999))
        assertEquals("1k", formatAgentTokenCount(1000))
        assertEquals("1.2k", formatAgentTokenCount(1200))
        assertEquals("12k", formatAgentTokenCount(12500))
    }

    @Test
    fun `用量元信息拼接模型与双向 token`() {
        assertEquals("gpt-4o · 1.2k→480 tok", formatAgentUsageMeta("gpt-4o", 1200, 480))
        assertEquals("gpt-4o", formatAgentUsageMeta("gpt-4o", 0, 0))
        assertEquals("1k→2k tok", formatAgentUsageMeta(null, 1000, 2000))
        assertEquals("", formatAgentUsageMeta(null, 0, 0))
        assertEquals("gpt-4o", formatAgentUsageMeta("  gpt-4o  ", 0, 0))
    }

    @Test
    fun `用量元信息在有时长时追加输出速度`() {
        assertEquals(
            "gpt-4o · 1.2k→480 tok · 48.0 tok/s",
            formatAgentUsageMeta("gpt-4o", 1200, 480, generationMs = 10_000L),
        )
        assertEquals(
            "gpt-4o · 1.2k→480 tok",
            formatAgentUsageMeta("gpt-4o", 1200, 480, generationMs = 0L),
        )
        assertEquals("gpt-4o", formatAgentUsageMeta("gpt-4o", 0, 0, generationMs = 5_000L))
        assertEquals(
            "1k→2k tok · 400 tok/s",
            formatAgentUsageMeta(null, 1000, 2000, generationMs = 5_000L),
        )
        assertEquals(
            "gpt-4o · 1.2k→480 tok",
            formatAgentUsageMeta("gpt-4o", 1200, 480, generationMs = -10L),
        )
        assertEquals(
            "claude · 0→12 tok · 8.2 tok/s",
            formatAgentUsageMeta("claude", 0, 12, generationMs = 1_463L),
        )
    }

    @Test
    fun `页码优先按页序索引回退到 pageId 数字`() {
        val pageIds = listOf("p0002", "p0001", "p0007")
        assertEquals("第 2 页", formatPageDisplayLabel("p0001", pageIds))
        assertEquals("第 3 页", formatPageDisplayLabel("p0007", pageIds))
        assertEquals("第 4 页", formatPageDisplayLabel("p0004"))
        assertEquals("第 12 页", formatPageDisplayLabel("P0012"))
        assertEquals("extra", formatPageDisplayLabel("extra"))
        assertEquals("p0000", formatPageDisplayLabel("p0000"))
    }

    @Test
    fun `附件标题按来源给出选区 页码 或投影名`() {
        assertEquals("选区", formatAttachmentTitle(Attachment.Source.SNIP, "snip_1246×742.png"))
        assertEquals("第 1 页", formatAttachmentTitle(Attachment.Source.FULL_PAGE, "page_p0001.png"))
        assertEquals("第 3 页", formatAttachmentTitle(Attachment.Source.EXTRA_PAGE, "page_p0007.png", listOf("p0001", "p0005", "p0007")))
        assertEquals("第 2 页", formatAttachmentTitle(Attachment.Source.FULL_PAGE, "page_P0002.jpg"))
        assertEquals("lecture.4", formatAttachmentTitle(Attachment.Source.PROJECTION, "lecture.4.png"))
    }

    @Test
    fun `多页与文档型附件标题`() {
        assertEquals("第 1-3 页", formatAttachmentTitle(Attachment.Source.EXTRA_PAGE, "pages_1-3.png"))
        assertEquals("第 2-9 页", formatAttachmentTitle(Attachment.Source.FULL_PAGE, "pages_2_9.jpg"))
        assertEquals("photo", formatAttachmentTitle(Attachment.Source.SYSTEM_IMAGE, "photo.jpg"))
        assertEquals("图片", formatAttachmentTitle(Attachment.Source.SYSTEM_IMAGE, ""))
        assertEquals("讲义.md", formatAttachmentTitle(Attachment.Source.MARKDOWN_DOC, "讲义.md"))
        assertEquals("报告.pdf", formatAttachmentTitle(Attachment.Source.PDF_DOC, "报告.pdf"))
        assertEquals("课件.pptx", formatAttachmentTitle(Attachment.Source.PPT_DOC, "课件.pptx"))
        assertEquals("草稿.txt", formatAttachmentTitle(Attachment.Source.TEXT_DOC, "草稿.txt"))
        assertEquals("文档", formatAttachmentTitle(Attachment.Source.PDF_DOC, ""))
    }

    @Test
    fun `附件角标按来源给出二字标签`() {
        assertEquals("选区", formatAttachmentBadge(Attachment.Source.SNIP))
        assertEquals("笔记", formatAttachmentBadge(Attachment.Source.FULL_PAGE))
        assertEquals("笔记", formatAttachmentBadge(Attachment.Source.EXTRA_PAGE))
        assertEquals("笔记", formatAttachmentBadge(Attachment.Source.PROJECTION))
        assertEquals("图片", formatAttachmentBadge(Attachment.Source.SYSTEM_IMAGE))
        assertEquals("MD", formatAttachmentBadge(Attachment.Source.MARKDOWN_DOC))
        assertEquals("PDF", formatAttachmentBadge(Attachment.Source.PDF_DOC))
        assertEquals("PPT", formatAttachmentBadge(Attachment.Source.PPT_DOC))
        assertEquals("TXT", formatAttachmentBadge(Attachment.Source.TEXT_DOC))
    }

    @Test
    fun `输出速度按档位保留小数`() {
        assertEquals("—", formatAgentSpeed(null))
        assertEquals("—", formatAgentSpeed(0.0))
        assertEquals("—", formatAgentSpeed(-3.0))
        assertEquals("—", formatAgentSpeed(Double.NaN))
        assertEquals("8.2 tok/s", formatAgentSpeed(8.23))
        assertEquals("42.5 tok/s", formatAgentSpeed(42.5))
        assertEquals("100 tok/s", formatAgentSpeed(99.99))
        assertEquals("120 tok/s", formatAgentSpeed(120.4))
    }

    @Test
    fun `上下文用量带上窗口分母`() {
        assertEquals("上下文 0 tok", formatSessionContext(0))
        assertEquals("上下文 3.2k tok", formatSessionContext(3200))
        assertEquals("上下文 3.2k / 128k", formatSessionContext(3200, 128))
        assertEquals("上下文 12k / 200k", formatSessionContext(12_500, 200))
        assertEquals("上下文 0 tok", formatSessionContext(-5))
    }

    @Test
    fun `行尾提及查询可空可空串`() {
        assertNull(extractMentionQuery("没有提及"))
        assertNull(extractMentionQuery("a@b.com"))
        assertNull(extractMentionQuery("@讲义 后面还有"))
        assertEquals("", extractMentionQuery("@"))
        assertNull(extractMentionQuery("看 "))
        assertEquals("", extractMentionQuery("看 @"))
        assertEquals("讲", extractMentionQuery("看 @讲"))
        assertEquals("第1页", extractMentionQuery("请 @第1页"))
        assertEquals("讲义.pdf", extractMentionQuery("@讲义.pdf"))
    }

    @Test
    fun `行尾提及替换为选中项`() {
        assertEquals("@讲义.pdf ", replaceTrailingMention("@", "讲义.pdf"))
        assertEquals("@讲义.pdf ", replaceTrailingMention("@讲", "讲义.pdf"))
        assertEquals("看 @讲义.pdf ", replaceTrailingMention("看 @讲", "讲义.pdf"))
        assertEquals("没有提及", replaceTrailingMention("没有提及", "讲义.pdf"))
        assertEquals("a@b.com", replaceTrailingMention("a@b.com", "讲义.pdf"))
        assertEquals("@a b", replaceTrailingMention("@a b", "讲义.pdf"))
    }

    @Test
    fun `错误详情与标题重复时不重复显示`() {
        assertNull(errorDetailOrNull("未配置视觉模型", "未配置视觉模型"))
        assertNull(errorDetailOrNull("网络超时", "   "))
        assertEquals("HTTP 401 invalid key", errorDetailOrNull("Key 无效或额度不足", "HTTP 401 invalid key"))
    }

    /** Bug 2:错误原文是排障的全部依据,展示层绝不截断(展开详情与复制都用同一份原文) */
    @Test
    fun `错误详情保留完整原文不截断`() {
        val raw = "HTTP 400 upstream error: " + "x".repeat(400) + "\n第二行:模型不存在"
        val detail = errorDetailOrNull("请求失败", raw)
        assertEquals(raw, detail)
        assertEquals(raw.length, detail?.length)
        assertTrue(detail!!.contains('\n'))
    }

    /** 协议标签:设置/面板一律显示产品名,不用 schema id */
    @Test
    fun `协议标签按产品名映射`() {
        assertEquals("ChatCompletions", protocolDisplayLabel(Protocol.OPENAI_COMPATIBLE))
        assertEquals("Anthropic", protocolDisplayLabel(Protocol.ANTHROPIC))
        assertEquals("Responses", protocolDisplayLabel(Protocol.OPENAI_RESPONSES))
        assertEquals("Chat", protocolShortLabel(Protocol.OPENAI_COMPATIBLE))
        assertEquals("Anthropic", protocolShortLabel(Protocol.ANTHROPIC))
        assertEquals("Responses", protocolShortLabel(Protocol.OPENAI_RESPONSES))
    }

    @Test
    fun `提及补全与替换可组合成输入流`() {
        val typed = "请 @讲"
        val query = extractMentionQuery(typed)
        assertEquals("讲", query)
        val chosen = query?.let { "讲义.pdf" }
        assertEquals("请 @讲义.pdf ", replaceTrailingMention(typed, chosen!!))
        assertTrue(replaceTrailingMention(typed, "讲义.pdf").endsWith(" "))
    }
}
