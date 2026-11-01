package com.milknote.app.ui.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Markdown 子集解析(AGENT-BACKEND §6.3/§14:容错降级) */
class MarkdownLiteTest {

    @Test
    fun `标题列表标签`() {
        val blocks = MarkdownLite.parse(
            """
            #### 解题思路
            - 第一步:**去分母**
            - 第二步:`移项`
            1. 代入 ${'$'}x=2${'$'}
            #代数 #分式方程
            """.trimIndent(),
        )
        assertEquals(MarkdownLite.Block.Heading::class, blocks[0]::class)
        val h = blocks[0] as MarkdownLite.Block.Heading
        assertEquals(4, h.level)

        val bullet = blocks[1] as MarkdownLite.Block.ListItem
        assertEquals(null, bullet.order)
        assertEquals(MarkdownLite.Span.Bold::class, bullet.spans[1]::class)

        val code = blocks[2] as MarkdownLite.Block.ListItem
        assertEquals(MarkdownLite.Span.Code::class, code.spans[1]::class)

        val ordered = blocks[3] as MarkdownLite.Block.ListItem
        assertEquals(1, ordered.order)
        assertTrue(ordered.spans.any { it is MarkdownLite.Span.Math && it.latex == "x=2" })

        val tags = blocks.last() as MarkdownLite.Block.TagLine
        assertEquals(listOf("代数", "分式方程"), tags.tags)
    }

    @Test
    fun `公式块跨行与单行`() {
        val blocks = MarkdownLite.parse("前文\n\$\$x^2+y^2=r^2\$\$\n后文")
        assertEquals(MarkdownLite.Block.MathBlock::class, blocks[1]::class)
        assertEquals("x^2+y^2=r^2", (blocks[1] as MarkdownLite.Block.MathBlock).latex)
        assertTrue(blocks[2].toString().contains("后文"))

        val multi = MarkdownLite.parse("\$\$\\begin{aligned}\na&=b\\\\\nc&=d\n\$\$")
        val mb = multi[0] as MarkdownLite.Block.MathBlock
        assertTrue(mb.latex.contains("aligned"))
    }

    @Test
    fun `行内公式与代码`() {
        val s2 = MarkdownLite.parseInline("由 ${'$'}E=mc^2${'$'} 可知 `x`")
        assertTrue(s2.any { it is MarkdownLite.Span.Math && it.latex == "E=mc^2" })
        assertTrue(s2.any { it is MarkdownLite.Span.Code && it.text == "x" })
    }

    @Test
    fun `未闭合标记按纯文本降级(流式半截)`() {
        val spans = MarkdownLite.parseInline("加粗未闭 **半截")
        assertTrue(spans.all { it is MarkdownLite.Span.Plain })
        // 行首未闭合 \$\$ → 吞到结尾为公式块;行中未闭合 → 段落降级(下一 chunk 补全后重解析)
        val leading = MarkdownLite.parse("${'$'}${'$'}x^2")
        assertEquals(MarkdownLite.Block.MathBlock::class, leading.last()::class)
        val mid = MarkdownLite.parse("正在输出 ${'$'}${'$'}x^2")
        assertEquals(MarkdownLite.Block.Paragraph::class, mid.last()::class)
    }

    @Test
    fun `行中成对公式提取`() {
        val blocks = MarkdownLite.parse("公式:${'$'}${'$'}a^2+b^2=c^2${'$'}${'$'}即勾股定理")
        assertEquals(MarkdownLite.Block.Paragraph::class, blocks[0]::class)
        assertEquals(MarkdownLite.Block.MathBlock::class, blocks[1]::class)
        assertEquals("a^2+b^2=c^2", (blocks[1] as MarkdownLite.Block.MathBlock).latex)
        assertEquals(MarkdownLite.Block.Paragraph::class, blocks[2]::class)
    }

    @Test
    fun `超集语法表格降级为段落`() {
        val blocks = MarkdownLite.parse("| a | b |\n|---|---|\n| 1 | 2 |")
        // 表格不受支持 → 按普通行/段落处理,不抛异常
        assertTrue(blocks.isNotEmpty())
    }
}
