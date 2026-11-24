package com.milknote.app.ui.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardPresentationTest {
    @Test
    fun `卡片预览去掉重复标题和标记但保留内容`() {
        val preview = cardPreviewText(
            title = "第 1 页 · 伴随笔记",
            body = "### 第 1 页 · 伴随笔记\n\n- 核心公式：\$A x = \\lambda x\$\n- 关联参考：[[线性代数讲义.pdf]]\n\n#笔记 #重点",
        )

        assertEquals("• 核心公式：A x = \\lambda x\n• 关联参考：线性代数讲义.pdf", preview)
        assertFalse(preview.contains("###"))
        assertFalse(preview.contains("[["))
        assertFalse(preview.contains("${'$'}A"))
    }

    @Test
    fun `卡片预览保留非重复标题和列表`() {
        val preview = cardPreviewText(
            title = "特征值",
            body = "## 特征值\n\n### 证明\n1. 写出 **特征方程**\n2. 代入 `x`",
        )

        assertTrue(preview.contains("证明"))
        assertTrue(preview.contains("1. 写出 特征方程"))
        assertTrue(preview.contains("2. 代入 x"))
        assertFalse(preview.contains("特征值\n"))
    }

    @Test
    fun `普通文本首行作为标题时也不重复`() {
        val preview = cardPreviewText(
            title = "QA Heading",
            body = "QA Heading\n\n- Formula: \$x^2\$\n- Link: [[Reference.pdf]]",
        )

        assertEquals("• Formula: x^2\n• Link: Reference.pdf", preview)
    }

    @Test
    fun `来源显示可读名称和页码`() {
        assertEquals(
            "线性代数 · 第 2 页",
            cardSourceLabel("线性代数", listOf("p0001", "p0002"), "p0002"),
        )
        assertEquals("线性代数 · 页面不可用", cardSourceLabel("线性代数", emptyList(), "p0002"))
        assertEquals("来源不可用", cardSourceLabel(null, emptyList(), "p0002"))
    }
}
