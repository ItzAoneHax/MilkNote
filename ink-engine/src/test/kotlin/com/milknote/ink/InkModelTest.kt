package com.milknote.ink

import com.milknote.ink.model.InkOp
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.PageInkState
import com.milknote.ink.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 页面状态:撤销/重做/整笔划擦除语义 */
class InkModelTest {

    private fun s(id: String) = Stroke(
        id = id, tool = InkTool.PEN, color = 0xFF23221E, baseWidthMm = 0.7f,
        points = listOf(InkPoint(10f, 10f, 0.5f, 0f, 0f, 1L), InkPoint(20f, 20f, 0.6f, 0f, 0f, 9L)),
    )

    private fun eraser(vararg xy: Pair<Float, Float>) = Stroke(
        id = "e", tool = InkTool.ERASER, color = 0, baseWidthMm = 4f,
        points = xy.map { InkPoint(it.first, it.second, 0.5f, 0f, 0f, 1L) },
    )

    @Test fun `undo redo roundtrip`() {
        val page = PageInkState()
        page.apply(InkOp.Add(s("a")))
        page.apply(InkOp.Add(s("b")))
        assertEquals(listOf("a", "b"), page.strokes.map { it.id })
        assertTrue(page.undo() != null)
        assertEquals(listOf("a"), page.strokes.map { it.id })
        assertTrue(page.redo() != null)
        assertEquals(listOf("a", "b"), page.strokes.map { it.id })
        assertFalse(page.redo() != null)
    }

    @Test fun `整笔划擦除 与撤销恢复`() {
        val page = PageInkState()
        page.apply(InkOp.Add(s("a")))
        page.apply(InkOp.Add(s("b")))
        page.apply(InkOp.Add(s("c")))
        val op = page.eraseStrokes(eraser(15f to 15f, 100f to 100f))
        assertTrue(op != null)
        page.apply(op!!)
        assertEquals(emptyList<String>(), page.strokes.map { it.id })  // 三笔全被擦
        page.undo()
        assertEquals(listOf("a", "b", "c"), page.strokes.map { it.id }) // 原序恢复
    }

    @Test fun `擦除只命中轨迹相交者`() {
        val page = PageInkState()
        page.apply(InkOp.Add(s("near")))
        val far = s("far").let {
            it.copy(points = it.points.map { p -> p.copy(x = p.x + 500f, y = p.y + 500f) })
        }
        page.apply(InkOp.Add(far))
        val op = page.eraseStrokes(eraser(15f to 15f))
        assertEquals(1, op!!.ops.size)
        page.apply(op)
        assertEquals(listOf("far"), page.strokes.map { it.id })
    }

    @Test fun `reset 清空撤销栈`() {
        val page = PageInkState()
        page.apply(InkOp.Add(s("a")))
        page.reset(listOf(s("x")))
        assertFalse(page.canUndo)
        assertEquals(listOf("x"), page.strokes.map { it.id })
    }
}
