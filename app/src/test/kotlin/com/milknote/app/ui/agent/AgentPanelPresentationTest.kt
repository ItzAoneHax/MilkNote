package com.milknote.app.ui.agent

import com.milknote.agent.Attachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `错误详情与标题重复时不重复显示`() {
        assertNull(errorDetailOrNull("未配置视觉模型", "未配置视觉模型"))
        assertNull(errorDetailOrNull("网络超时", "   "))
        assertEquals("HTTP 401 invalid key", errorDetailOrNull("Key 无效或额度不足", "HTTP 401 invalid key"))
    }
}
