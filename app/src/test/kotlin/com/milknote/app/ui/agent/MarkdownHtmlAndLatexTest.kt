package com.milknote.app.ui.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown → HTML(离线 KaTeX 页)与原生 LaTeX AST 的纯 JVM 覆盖:
 * 转义/安全 scheme/离线资源引用/数学原文保留,以及分式·开方·上下标·符号表·矩阵·残缺流式源码的容错。
 */
class MarkdownHtmlAndLatexTest {

    // ---------------- HTML 渲染 ----------------

    @Test
    fun `HTML 转义阻断脚本注入`() {
        val html = MarkdownHtmlRenderer.renderFullHtml("<script>alert(1)</script> & **粗**", false)
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertFalse(html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("<strong>粗</strong>"))
        assertTrue(html.contains("&amp;"))
    }

    @Test
    fun `链接只放行安全协议`() {
        val html = MarkdownHtmlRenderer.renderFullHtml(
            "[点我](javascript:alert(1)) [安全](https://example.com/a?b=1&c=2)",
            false,
        )
        assertTrue(html.contains("href=\"#\""))
        assertTrue(html.contains("href=\"https://example.com/a?b=1&amp;c=2\""))
        assertFalse(html.contains("javascript:"))
    }

    @Test
    fun `数学原文保留给 KaTeX 排版`() {
        val html = MarkdownHtmlRenderer.renderFullHtml("行内 \$x^2\$ 说明\n\n\$\$\\frac{a}{b}\$\$", false)
        assertTrue(html.contains("math-inline"))
        assertTrue(html.contains("\$x^2\$"))
        assertTrue(html.contains("math-block"))
        assertTrue(html.contains("\\frac{a}{b}"))
    }

    @Test
    fun `双链与提及渲染为可点样式`() {
        val html = MarkdownHtmlRenderer.renderFullHtml("看 [[双链]] 与 @小明", false)
        assertTrue(html.contains("class=\"wikilink\""))
        assertTrue(html.contains("class=\"mention\""))
        assertTrue(html.contains("@小明"))
    }

    @Test
    fun `离线资源与双主题配色`() {
        val light = MarkdownHtmlRenderer.renderFullHtml("x", false)
        val dark = MarkdownHtmlRenderer.renderFullHtml("x", true)
        listOf(light, dark).forEach { html ->
            assertTrue(html.contains("file:///android_asset/katex/katex.min.css"))
            assertTrue(html.contains("file:///android_asset/katex/katex.min.js"))
            assertTrue(html.contains("file:///android_asset/katex/auto-render.min.js"))
            assertTrue(html.contains("renderMathInElement"))
            assertTrue(html.contains("throwOnError:false"))
        }
        assertTrue(light.contains("#FFFFFF"))
        assertTrue(light.contains("#2563EB"))
        assertTrue(dark.contains("#1F2126"))
        assertTrue(dark.contains("#ECEEF0"))
        assertTrue(dark.contains("#5B92FF"))
    }

    @Test
    fun `宽度自适应与字体缩放`() {
        val scaled = MarkdownHtmlRenderer.renderFullHtml("正文", false, fontScale = 1.25f)
        assertTrue(scaled.contains("18.13px"))
    }

    @Test
    fun `表格 代码块 引用 任务列表的 HTML 结构`() {
        val html = MarkdownHtmlRenderer.renderFullHtml(
            """
            | 左 | 右 |
            |:--|--:|
            | 1 | 2 |

            ```kotlin
            val x = 1
            ```

            > 引用文字

            - [x] 完成
            - [ ] 待办
            """.trimIndent(),
            false,
        )
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("text-align:left"))
        assertTrue(html.contains("text-align:right"))
        assertTrue(html.contains("<pre><code>"))
        assertTrue(html.contains("class=\"lang\">kotlin"))
        assertTrue(html.contains("<blockquote>"))
        assertTrue(html.contains("☑"))
        assertTrue(html.contains("☐"))
    }

    @Test
    fun `生成文档在整页 HTML 内成节`() {
        val html = MarkdownHtmlRenderer.renderFullHtml(
            "```milkdoc title=\"小结.md\"\n# 小结\n正文\n```",
            false,
        )
        assertTrue(html.contains("class=\"doc\""))
        assertTrue(html.contains("小结.md"))
        assertTrue(html.contains(">MD<"))
        assertTrue(html.contains("<h1>小结</h1>"))
    }

    @Test
    fun `单公式页回报高度并安全嵌入源码`() {
        val html = MarkdownHtmlRenderer.renderMathFormulaHtml("\\frac{a}{b}", true, true)
        assertTrue(html.contains("katex.render"))
        assertTrue(html.contains("displayMode=true"))
        assertTrue(html.contains("MilkMathBridge.onHeight"))
        assertTrue(html.contains("scrollHeight"))
        assertTrue(html.contains("file:///android_asset/katex/katex.min.js"))

        val inline = MarkdownHtmlRenderer.renderMathFormulaHtml("x^2", false, false)
        assertTrue(inline.contains("displayMode=false"))

        val evil = MarkdownHtmlRenderer.renderMathFormulaHtml("\\text{</script><script>alert(1)</script>}", true, false)
        assertFalse(evil.contains("</script><script>alert(1)"))
    }

    // ---------------- LaTeX AST ----------------

    @Test
    fun `分式与开方`() {
        val frac = LatexAstParser.parse("""\frac{a}{b}""")
        assertTrue(frac is LatexAstParser.Frac)

        val square = LatexAstParser.parse("""\sqrt{x+1}""")
        assertTrue(square is LatexAstParser.Sqrt)
        assertNull((square as LatexAstParser.Sqrt).degree)

        val cubic = LatexAstParser.parse("""\sqrt[3]{x}""")
        assertTrue(cubic is LatexAstParser.Sqrt)
        assertNotNull((cubic as LatexAstParser.Sqrt).degree)
    }

    @Test
    fun `上下标与组合`() {
        val sup = LatexAstParser.parse("x^2") as LatexAstParser.Sup
        assertEquals("2", (sup.exp as LatexAstParser.Symbol).symbol)

        val sub = LatexAstParser.parse("a_i") as LatexAstParser.Sub
        assertEquals("i", (sub.index as LatexAstParser.Symbol).symbol)

        val both = LatexAstParser.parse("x_i^2") as LatexAstParser.SubSup
        assertNotNull(both.index)
        assertNotNull(both.exp)
    }

    @Test
    fun `希腊字母与运算符符号表`() {
        assertEquals("α", (LatexAstParser.parse("""\alpha""") as LatexAstParser.Symbol).symbol)
        assertEquals("Ω", (LatexAstParser.parse("""\Omega""") as LatexAstParser.Symbol).symbol)
        assertEquals("≤", (LatexAstParser.parse("""\le""") as LatexAstParser.Symbol).symbol)
        assertEquals("∞", (LatexAstParser.parse("""\infty""") as LatexAstParser.Symbol).symbol)
        assertEquals("⇒", (LatexAstParser.parse("""\Rightarrow""") as LatexAstParser.Symbol).symbol)
        assertEquals("…", (LatexAstParser.parse("""\ldots""") as LatexAstParser.Symbol).symbol)
    }

    @Test
    fun `大型运算符带上下限`() {
        val sum = LatexAstParser.parse("""\sum_{i=1}^{n} a_i""")
        val sumSeq = sum as LatexAstParser.Seq
        val bigOp = sumSeq.items.first()
        assertTrue(bigOp is LatexAstParser.BigOp)
        assertNotNull((bigOp as LatexAstParser.BigOp).lower)
        assertNotNull(bigOp.upper)

        val integral = LatexAstParser.parse("""\int_0^1 x dx""") as LatexAstParser.Seq
        val op = integral.items.first() as LatexAstParser.BigOp
        assertEquals("∫", op.operator)
        assertNotNull(op.lower)
        assertNotNull(op.upper)
    }

    @Test
    fun `矩阵与分段函数环境`() {
        val pmatrixNode = LatexAstParser.parse("""\begin{pmatrix} a & b \\ c & d \end{pmatrix}""")
        assertTrue(pmatrixNode is LatexAstParser.Matrix)
        val pmatrix = pmatrixNode as LatexAstParser.Matrix
        assertEquals("pmatrix", pmatrix.env)
        assertEquals(2, pmatrix.rows.size)
        assertEquals(2, pmatrix.rows[0].size)

        val casesNode = LatexAstParser.parse("""\begin{cases} x & x>0 \\ 0 & x\le 0\end{cases}""")
        assertTrue(casesNode is LatexAstParser.Matrix)
        assertEquals(2, (casesNode as LatexAstParser.Matrix).rows.size)

        val alignedNode = LatexAstParser.parse("""\begin{aligned} a &= b \\ c &= d \end{aligned}""")
        assertTrue(alignedNode is LatexAstParser.Matrix)
        val aligned = alignedNode as LatexAstParser.Matrix
        assertEquals("aligned", aligned.env)
        assertEquals(2, aligned.rows.size)
        assertEquals(2, aligned.rows[0].size)
    }

    @Test
    fun `重音 定界符与字体样式`() {
        val hat = LatexAstParser.parse("""\hat{x}""")
        assertTrue(hat is LatexAstParser.Accent)
        assertEquals("hat", (hat as LatexAstParser.Accent).accent)

        val delimNode = LatexAstParser.parse("""\left(\frac{a}{b}\right)""")
        assertTrue(delimNode is LatexAstParser.Delim)
        val delim = delimNode as LatexAstParser.Delim
        assertEquals("(", delim.left)
        assertEquals(")", delim.right)
        assertTrue(delim.body is LatexAstParser.Frac)

        val bold = LatexAstParser.parse("""\mathbf{v}""")
        assertEquals(LatexAstParser.Style.BOLD, (bold as LatexAstParser.Styled).style)

        val text = LatexAstParser.parse("""\text{速度 m/s}""") as LatexAstParser.Styled
        assertEquals(LatexAstParser.Style.TEXT, text.style)
        assertEquals("速度 m/s", (text.body as LatexAstParser.Text).text)
    }

    @Test
    fun `残缺流式公式不抛异常`() {
        val broken = listOf(
            "", "\\frac{a}{", "{", "}", "\\begin{matrix}", "x^", "\\left(",
            "\\unknowncmd", "\\sqrt[", "\\sum_{", "a & b", "\\\\",
        )
        broken.forEach { src ->
            val node = LatexAstParser.parse(src)
            assertNotNull("解析失败:$src", node)
        }
    }
}
