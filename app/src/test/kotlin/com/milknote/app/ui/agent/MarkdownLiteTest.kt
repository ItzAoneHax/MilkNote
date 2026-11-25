package com.milknote.app.ui.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        // GFM 表格现已原生支持 → 解析为 Table(不再降级为段落)
        assertTrue(blocks.isNotEmpty())
        assertEquals(MarkdownLite.Block.Table::class, blocks[0]::class)
    }

    // ---------------- GFM 全量语法 ----------------

    @Test
    fun `表格列对齐与行列结构`() {
        val blocks = MarkdownLite.parse(
            """
            | 左 | 中 | 右 |
            |:---|:---:|---:|
            | 1 | 2 | 3 |
            | 四 | 五 | 六 |
            """.trimIndent(),
        )
        val table = blocks[0] as MarkdownLite.Block.Table
        assertEquals(3, table.headers.size)
        assertEquals(
            listOf(
                MarkdownLite.TableAlign.LEFT,
                MarkdownLite.TableAlign.CENTER,
                MarkdownLite.TableAlign.RIGHT,
            ),
            table.alignments,
        )
        assertEquals(2, table.rows.size)
        assertEquals("五", table.rows[1][1].first().plainText())
    }

    @Test
    fun `围栏代码块保留语言与正文`() {
        val blocks = MarkdownLite.parse("```kotlin\nval x = 1\nprintln(x)\n```")
        val code = blocks[0] as MarkdownLite.Block.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1\nprintln(x)", code.code)

        val tilde = MarkdownLite.parse("~~~python\nprint(1)\n~~~")
        assertEquals("python", (tilde[0] as MarkdownLite.Block.CodeBlock).language)
    }

    @Test
    fun `引用块递归解析内部块`() {
        val blocks = MarkdownLite.parse("> 引用 **加粗**\n> 第二行")
        val quote = blocks[0] as MarkdownLite.Block.Quote
        assertTrue(quote.blocks.single() is MarkdownLite.Block.Paragraph)
        assertTrue((quote.blocks.single() as MarkdownLite.Block.Paragraph).spans.any { it is MarkdownLite.Span.Bold })

        val nested = MarkdownLite.parse("> > 深层")
        val outer = nested[0] as MarkdownLite.Block.Quote
        assertTrue(outer.blocks.single() is MarkdownLite.Block.Quote)
    }

    @Test
    fun `列表保留缩进层级 有序与无序混排`() {
        val blocks = MarkdownLite.parse("- 一级\n  - 二级\n    1. 三级有序")
        val first = blocks[0] as MarkdownLite.Block.ListItem
        val second = blocks[1] as MarkdownLite.Block.ListItem
        val third = blocks[2] as MarkdownLite.Block.ListItem
        assertEquals(0, first.indent)
        assertNull(first.order)
        assertEquals(1, second.indent)
        assertEquals(2, third.indent)
        assertEquals(1, third.order)
    }

    @Test
    fun `任务列表勾选状态与缩进`() {
        val blocks = MarkdownLite.parse("- [x] 已完成\n- [ ] 未完成\n  * [X] 缩进已完成")
        val done = blocks[0] as MarkdownLite.Block.TaskItem
        val todo = blocks[1] as MarkdownLite.Block.TaskItem
        val indented = blocks[2] as MarkdownLite.Block.TaskItem
        assertTrue(done.checked)
        assertTrue(!todo.checked)
        assertEquals(0, todo.indent)
        assertTrue(indented.checked)
        assertEquals(1, indented.indent)
        assertEquals("未完成", todo.spans.first().plainText())
    }

    @Test
    fun `分隔线三种写法`() {
        assertEquals(MarkdownLite.Block.HorizontalRule, MarkdownLite.parse("---")[0])
        assertEquals(MarkdownLite.Block.HorizontalRule, MarkdownLite.parse("***")[0])
        assertEquals(MarkdownLite.Block.HorizontalRule, MarkdownLite.parse("___")[0])
    }

    @Test
    fun `反斜杠公式块单行与多行`() {
        val single = MarkdownLite.parse("\\[a^2+b^2\\]")
        assertEquals("a^2+b^2", (single[0] as MarkdownLite.Block.MathBlock).latex)

        val multi = MarkdownLite.parse("\\[\na^2\n+b^2\n\\]")
        val math = multi[0] as MarkdownLite.Block.MathBlock
        assertEquals("a^2\n+b^2", math.latex.trim())
    }

    @Test
    fun `行内语法全集`() {
        val spans = MarkdownLite.parseInline(
            "***粗斜*** **粗** __也粗__ *斜* _也斜_ ~~删~~ `码` [链接](https://a.b) [[双链]] @小明",
        )
        assertTrue(spans.any { it is MarkdownLite.Span.BoldItalic && it.text == "粗斜" })
        assertTrue(spans.any { it is MarkdownLite.Span.Bold && it.text == "粗" })
        assertTrue(spans.any { it is MarkdownLite.Span.Bold && it.text == "也粗" })
        assertTrue(spans.any { it is MarkdownLite.Span.Italic && it.text == "斜" })
        assertTrue(spans.any { it is MarkdownLite.Span.Italic && it.text == "也斜" })
        assertTrue(spans.any { it is MarkdownLite.Span.Strike && it.text == "删" })
        assertTrue(spans.any { it is MarkdownLite.Span.Code && it.text == "码" })
        assertTrue(spans.any { it is MarkdownLite.Span.Link && it.url == "https://a.b" })
        assertTrue(spans.any { it is MarkdownLite.Span.Wikilink && it.target == "双链" })
        assertTrue(spans.any { it is MarkdownLite.Span.Mention && it.name == "小明" })
    }

    @Test
    fun `下划线强调不破坏标识符`() {
        val spans = MarkdownLite.parseInline("foo_bar_baz 与 __粗__")
        assertTrue(spans.first() is MarkdownLite.Span.Plain)
        assertTrue((spans.first() as MarkdownLite.Span.Plain).text.contains("foo_bar_baz"))
        assertTrue(spans.any { it is MarkdownLite.Span.Bold && it.text == "粗" })
    }

    @Test
    fun `邮箱与图片与行内反斜杠公式`() {
        val spans = MarkdownLite.parseInline("""a@b.com \(x+1\) 见图![图](pic.png)""")
        assertTrue(spans.none { it is MarkdownLite.Span.Mention })
        assertTrue(spans.any { it is MarkdownLite.Span.Math && it.latex == "x+1" })
        assertTrue(spans.any { it is MarkdownLite.Span.Image && it.alt == "图" && it.url == "pic.png" })
    }

    // ---------------- 生成文档 ----------------

    @Test
    fun `milkdoc 围栏解析为生成文档`() {
        val blocks = MarkdownLite.parse(
            """
            ```milkdoc title="notes.md" type="markdown"
            # 标题
            - 要点
            ```
            """.trimIndent(),
        )
        val doc = blocks.single() as MarkdownLite.Block.GeneratedDocument
        assertEquals("notes.md", doc.title)
        assertEquals(MarkdownLite.GeneratedDocType.MARKDOWN, doc.type)
        assertTrue(doc.content.startsWith("# 标题"))
    }

    @Test
    fun `文档围栏简写与前缀语法`() {
        val cases = mapOf(
            "document:报告.md" to ("报告.md" to MarkdownLite.GeneratedDocType.MARKDOWN),
            "doc:说明.txt" to ("说明.txt" to MarkdownLite.GeneratedDocType.TEXT),
            "md:x.md" to ("x.md" to MarkdownLite.GeneratedDocType.MARKDOWN),
            "markdown:y.md" to ("y.md" to MarkdownLite.GeneratedDocType.MARKDOWN),
            "txt:z.txt" to ("z.txt" to MarkdownLite.GeneratedDocType.TEXT),
            "latex:f.tex" to ("f.tex" to MarkdownLite.GeneratedDocType.LATEX),
            "markdown title='单引号.md'" to ("单引号.md" to MarkdownLite.GeneratedDocType.MARKDOWN),
        )
        cases.forEach { (header, expected) ->
            val doc = MarkdownLite.parse("```$header\n正文\n```").single() as MarkdownLite.Block.GeneratedDocument
            assertEquals(header, expected.first, doc.title)
            assertEquals(header, expected.second, doc.type)
        }
    }

    @Test
    fun `生成文档内层围栏不提前结束`() {
        val blocks = MarkdownLite.parse(
            """
            ```milkdoc title="a.md"
            ```kotlin
            val x = 1
            ```
            收尾文字
            ```
            """.trimIndent(),
        )
        val doc = blocks.single() as MarkdownLite.Block.GeneratedDocument
        assertTrue(doc.content.contains("```kotlin"))
        assertTrue(doc.content.contains("收尾文字"))
    }

    @Test
    fun `裸语言名仍是代码块而非文档`() {
        val blocks = MarkdownLite.parse("```markdown\n# 标题\n```")
        assertTrue(blocks.single() is MarkdownLite.Block.CodeBlock)
    }

    @Test
    fun `四反引号围栏可容纳三层反引号`() {
        val blocks = MarkdownLite.parse(
            """
            ````milkdoc title="big.md"
            # 标题
            ```kotlin
            val x = 1
            ```
            ````
            """.trimIndent(),
        )
        val doc = blocks.single() as MarkdownLite.Block.GeneratedDocument
        assertTrue(doc.content.contains("```kotlin"))
        assertTrue(doc.content.contains("val x = 1"))
    }

    @Test
    fun `提取会话内全部生成文档`() {
        val text = """
            前言
            ```milkdoc title="one.md"
            一
            ```
            中间
            ```latex:two.tex
            x^2
            ```
            结尾
        """.trimIndent()
        val docs = MarkdownLite.extractGeneratedDocuments(text)
        assertEquals(listOf("one.md", "two.tex"), docs.map { it.title })
        assertEquals(MarkdownLite.GeneratedDocType.MARKDOWN, docs[0].type)
        assertEquals(MarkdownLite.GeneratedDocType.LATEX, docs[1].type)
    }

    @Test
    fun `纯文本降级保留全部内容`() {
        val text = "| a | b |\n|---|---|\n| 1 | 2 |\n\n> 引用\n\n- [x] 完成"
        val plain = MarkdownLite.parse(text).joinToString("\n") { it.plainText() }
        assertTrue(plain.contains("1 | 2"))
        assertTrue(plain.contains("引用"))
        assertTrue(plain.contains("☑ 完成"))
    }
}
