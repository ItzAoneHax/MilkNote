package com.milknote.app.ui.writer

import com.milknote.agent.GraphViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 结构化公式输入:分式/根式/上标、斜杠转分式、原子删除与视口解析 */
class FormulaInputEditorTest {

    @Test
    fun `斜杠把前一个原子变成真分数`() {
        var state = FormulaInputEditor.insertText(FormulaInputState.of(""), "1")
        state = FormulaInputEditor.insertText(state, "/")
        assertEquals("\\frac{1}{\\square}", state.latex)
        assertEquals("\\frac{1}{".length, state.cursor)

        state = FormulaInputEditor.insertText(state, "2")
        assertEquals("\\frac{1}{2}", state.latex)
        assertEquals("\\frac{1}{2".length, state.cursor)
    }

    @Test
    fun `分式模板与占位符填充`() {
        var state = FormulaInputEditor.insertTemplate(FormulaInputState.of(""), FormulaTemplate.FRACTION)
        assertEquals("\\frac{\\square}{\\square}", state.latex)
        assertEquals("\\frac{".length, state.cursor)

        state = FormulaInputEditor.insertText(state, "x")
        assertEquals("\\frac{x}{\\square}", state.latex)

        state = FormulaInputEditor.insertTemplate(state, FormulaTemplate.SQRT)
        assertTrue(state.latex.contains("\\sqrt{\\square}"))
    }

    @Test
    fun `根式上标与符号按钮都是格式化模板`() {
        var state = FormulaInputEditor.insertTemplate(FormulaInputState.of(""), FormulaTemplate.SQRT)
        state = FormulaInputEditor.insertText(state, "x")
        assertEquals("\\sqrt{x}", state.latex)

        state = FormulaInputEditor.insertTemplate(FormulaInputState.of("x"), FormulaTemplate.SQUARE)
        assertEquals("x^{2}", state.latex)

        assertEquals("\\pi", FormulaInputEditor.insertTemplate(FormulaInputState.of(""), FormulaTemplate.PI).latex)
        assertEquals(
            "\\sin(\\square)",
            FormulaInputEditor.insertTemplate(FormulaInputState.of(""), FormulaTemplate.SIN).latex,
        )
        assertEquals(
            "|\\square|",
            FormulaInputEditor.insertTemplate(FormulaInputState.of(""), FormulaTemplate.ABS).latex,
        )
    }

    @Test
    fun `退格按命令或括号组原子删除`() {
        assertEquals(
            "",
            FormulaInputEditor.backspace(FormulaInputState.of("\\frac{1}{2}")).latex,
        )
        assertEquals(
            "x+",
            FormulaInputEditor.backspace(FormulaInputState.of("x+\\sqrt{y}")).latex,
        )
        assertEquals(
            "",
            FormulaInputEditor.backspace(FormulaInputState.of("\\sin(x)")).latex,
        )
        assertEquals(
            "",
            FormulaInputEditor.backspace(FormulaInputState.of("|x|")).latex,
        )
        assertEquals(
            "\\sqrt{",
            FormulaInputEditor.backspace(FormulaInputState.of("\\sqrt{x")).latex,
        )
    }

    @Test
    fun `左右移动按原子边界`() {
        val state = FormulaInputState.of("x+\\sqrt{y}")
        val left = FormulaInputEditor.moveLeft(state)
        assertEquals(2, left.cursor)
        assertEquals(state.latex, left.latex)
        assertEquals(1, FormulaInputEditor.moveLeft(left).cursor)
        assertEquals(0, FormulaInputEditor.moveLeft(FormulaInputEditor.moveLeft(left)).cursor)
        assertEquals(1, FormulaInputEditor.moveRight(FormulaInputState.of("x+\\sqrt{y}", 0)).cursor)
    }

    @Test
    fun `隐藏输入框变更走同一套结构化规则`() {
        val typed = FormulaInputEditor.onFieldChange(FormulaInputState.of(""), "x", 1)
        assertEquals("x", typed.latex)
        assertEquals(1, typed.cursor)

        val slash = FormulaInputEditor.onFieldChange(FormulaInputState.of("1", 1), "1/", 2)
        assertEquals("\\frac{1}{\\square}", slash.latex)
        assertEquals("\\frac{1}{".length, slash.cursor)

        val deleted = FormulaInputEditor.onFieldChange(
            FormulaInputState.of("\\frac{1}{2}", "\\frac{1}{2}".length),
            "\\frac{1}{2",
            "\\frac{1}{2".length,
        )
        assertEquals("", deleted.latex)
    }

    @Test
    fun `视口文本解析与默认值格式化`() {
        val vp = parseGraphViewport(listOf("-10", "10", "-7", "7")).getOrThrow()
        assertEquals(GraphViewport(), vp)
        assertTrue(parseGraphViewport(listOf("10", "-10", "-7", "7")).isFailure)
        assertTrue(parseGraphViewport(listOf("a", "10", "-7", "7")).isFailure)
        assertTrue(parseGraphViewport(listOf("1", "2")).isFailure)
        assertEquals("10", formatGraphViewportValue(10.0))
        assertEquals("-7", formatGraphViewportValue(-7.0))
    }
}
