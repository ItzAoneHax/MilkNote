package com.milknote.app.agent

import com.milknote.app.ui.agent.MarkdownLite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画布 Markdown/LaTeX 卡片渲染器的纯逻辑部分(不触达 Android 绘图类,纯 JVM 可跑)。
 * 真正的排版由 `Canvas` + `StaticLayout` 完成,行为由设备上的画布验证。
 */
class MarkdownCanvasCardRendererTest {

    @Test
    fun `LaTeX 卡片按美元块拆分多段公式`() {
        val sources = MarkdownCardText.latexSources(
            """
            $$ a^2 + b^2 = c^2 $$

            $$ \frac{1}{2} \sum_{i=1}^{n} x_i $$
            """.trimIndent(),
        )
        assertEquals(2, sources.size)
        assertEquals("a^2 + b^2 = c^2", sources[0])
        assertEquals("\\frac{1}{2} \\sum_{i=1}^{n} x_i", sources[1])
    }

    @Test
    fun `LaTeX 卡片支持反斜杠方括号与无标记整段`() {
        assertEquals(
            listOf("x = \\frac{-b}{2a}"),
            MarkdownCardText.latexSources("\\[ x = \\frac{-b}{2a} \\]"),
        )
        assertEquals(
            listOf("E = mc^2"),
            MarkdownCardText.latexSources("  E = mc^2  "),
        )
        assertTrue(MarkdownCardText.latexSources("   ").isEmpty())
    }

    @Test
    fun `公式缩放只在超宽时生效且绝不溢出`() {
        assertEquals(1f, MarkdownCardText.fitScale(120f, 300f), 0.001f)
        assertEquals(1f, MarkdownCardText.fitScale(0f, 300f), 0.001f)
        assertEquals(0.5f, MarkdownCardText.fitScale(600f, 300f), 0.001f)
        assertEquals(0.1f, MarkdownCardText.fitScale(3000f, 300f), 0.001f)

        val contentWidth = 280f
        listOf(281f, 640f, 2048f, 9999f).forEach { measured ->
            val scale = MarkdownCardText.fitScale(measured, contentWidth)
            assertTrue("measured=$measured 超出卡片宽度", measured * scale <= contentWidth + 0.001f)
            assertTrue(scale in 0f..1f)
        }
    }

    @Test
    fun `折叠高度不超过上限展开后完整`() {
        assertEquals(1200f, MarkdownCardText.visibleHeight(1200f, expanded = true), 0.001f)
        assertEquals(
            MarkdownCardText.COLLAPSE_MAX_HEIGHT_PX,
            MarkdownCardText.visibleHeight(1200f, expanded = false),
            0.001f,
        )
        assertEquals(120f, MarkdownCardText.visibleHeight(120f, expanded = false), 0.001f)
    }

    @Test
    fun `标题缩放按层级递减且 H6 为正文大小`() {
        val scales = (1..6).map { MarkdownCardText.headingScale(it) }
        assertEquals(1f, scales.last(), 0.001f)
        scales.zipWithNext().forEach { (a, b) -> assertTrue("标题层级越大字号应越小", a >= b) }
        assertTrue(scales.first() > 1.4f)
        // 越界层级钳到 H1 / H6
        assertEquals(MarkdownCardText.headingScale(1), MarkdownCardText.headingScale(0), 0.001f)
        assertEquals(MarkdownCardText.headingScale(6), MarkdownCardText.headingScale(9), 0.001f)
    }

    @Test
    fun `Markdown 解析产出渲染器需要的全部块类型`() {
        val markdown = """
            # 标题

            正文含 **粗体**、*斜体*、`代码` 与 ${'$'}x^2${'$'}。

            - 无序项
            1. 有序项
            - [x] 已完成
            - [ ] 未完成

            > 引用行

            | 列 A | 列 B |
            | :--- | ---: |
            | 1    | 2    |

            ```kotlin
            val x = 1
            ```

            ---

            ${'$'}${'$'}
            \frac{a}{b}
            ${'$'}${'$'}

            #标签1 #标签2
        """.trimIndent()

        val blocks = MarkdownLite.parse(markdown)
        assertTrue(blocks.any { it is MarkdownLite.Block.Heading })
        assertTrue(blocks.any { it is MarkdownLite.Block.Paragraph })
        assertTrue(blocks.any { it is MarkdownLite.Block.ListItem })
        assertTrue(blocks.any { it is MarkdownLite.Block.TaskItem && it.checked })
        assertTrue(blocks.any { it is MarkdownLite.Block.TaskItem && !it.checked })
        assertTrue(blocks.any { it is MarkdownLite.Block.Quote })
        assertTrue(blocks.any { it is MarkdownLite.Block.Table })
        assertTrue(blocks.any { it is MarkdownLite.Block.CodeBlock })
        assertTrue(blocks.contains(MarkdownLite.Block.HorizontalRule))
        assertTrue(blocks.any { it is MarkdownLite.Block.MathBlock })
        assertTrue(blocks.any { it is MarkdownLite.Block.TagLine })

        val math = blocks.filterIsInstance<MarkdownLite.Block.MathBlock>().first()
        assertEquals("\\frac{a}{b}", math.latex)
    }
}
