package com.milknote.ink

import com.milknote.ink.input.InkController
import com.milknote.ink.model.InkOp
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.LassoMode
import com.milknote.ink.model.PageInkState
import com.milknote.ink.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 矩形套索:起点/当前点构成闭合矩形;模式切换清理选区与进行中的圈选 */
class RectangleLassoTest {

    private fun pt(x: Float, y: Float) = InkPoint(x, y, 0.5f, 0f, 0f, 0L)

    private fun s(id: String, x: Float, y: Float) = Stroke(
        id = id, tool = InkTool.PEN, color = 0xFF23221E, baseWidthMm = 0.7f,
        points = listOf(pt(x, y), pt(x + 10f, y)),
    )

    private fun controllerWith(vararg strokes: Stroke): InkController {
        val page = PageInkState()
        strokes.forEach { page.apply(InkOp.Add(it)) }
        val c = InkController()
        c.loadStrokes(page.strokes.toList())
        return c
    }

    @Test fun `默认是自由套索`() {
        assertEquals(LassoMode.FREEHAND, InkController().lassoMode)
    }

    @Test fun `矩形圈选 - 起点与当前点构成闭合五点且不累积`() {
        val c = controllerWith()
        c.setLassoMode(LassoMode.RECTANGLE)
        c.beginLasso(pt(50f, 50f))
        c.extendLasso(pt(100f, 100f))

        val loop = c.lassoLoopSnapshot()
        assertEquals(5, loop.size)
        assertEquals(50f, loop[0].x, 0.001f); assertEquals(50f, loop[0].y, 0.001f)
        assertEquals(100f, loop[1].x, 0.001f); assertEquals(50f, loop[1].y, 0.001f)
        assertEquals(100f, loop[2].x, 0.001f); assertEquals(100f, loop[2].y, 0.001f)
        assertEquals(50f, loop[3].x, 0.001f); assertEquals(100f, loop[3].y, 0.001f)
        assertEquals(50f, loop[4].x, 0.001f); assertEquals(50f, loop[4].y, 0.001f)

        // 再次扩展 = 重算矩形（替换），不是追加轨迹点
        c.extendLasso(pt(120f, 90f))
        val resized = c.lassoLoopSnapshot()
        assertEquals(5, resized.size)
        assertEquals(120f, resized[2].x, 0.001f); assertEquals(90f, resized[2].y, 0.001f)
    }

    @Test fun `矩形圈选 - 只选中完全落在矩形内的笔迹`() {
        val c = controllerWith(s("inside", 60f, 60f), s("cross", 95f, 60f), s("outside", 500f, 500f))
        c.setLassoMode(LassoMode.RECTANGLE)
        c.beginLasso(pt(50f, 50f))
        c.extendLasso(pt(100f, 100f))
        assertTrue(c.endLasso())

        val sel = c.selectionSnapshot()
        assertNotNull(sel)
        assertEquals(1, sel!!.count)
        assertEquals(listOf("inside"), c.selectedStrokes().map { it.id })
    }

    @Test fun `切模式 - 清空已有选区与进行中的圈选`() {
        val c = controllerWith(s("inside", 60f, 60f))
        c.setLassoMode(LassoMode.RECTANGLE)
        c.beginLasso(pt(50f, 50f))
        c.extendLasso(pt(100f, 100f))
        c.endLasso()
        assertNotNull(c.selectionSnapshot())

        c.setLassoMode(LassoMode.FREEHAND)
        assertEquals(LassoMode.FREEHAND, c.lassoMode)
        assertNull(c.selectionSnapshot())
        assertTrue(c.lassoLoopSnapshot().isEmpty())

        // 进行中的矩形圈选也被取消；随后 extend 不会凭空建出圈选
        c.setLassoMode(LassoMode.RECTANGLE)
        c.beginLasso(pt(0f, 0f))
        c.extendLasso(pt(40f, 40f))
        assertEquals(5, c.lassoLoopSnapshot().size)
        c.setLassoMode(LassoMode.FREEHAND)
        c.extendLasso(pt(80f, 80f))
        assertTrue(c.lassoLoopSnapshot().isEmpty())
        assertFalse(c.endLasso())
    }

    @Test fun `cancel 与 end 清理起点 - 下一次圈选从新起点起算`() {
        val c = controllerWith()
        c.setLassoMode(LassoMode.RECTANGLE)
        c.beginLasso(pt(10f, 10f))
        c.extendLasso(pt(100f, 100f))
        c.cancelLasso()
        assertTrue(c.lassoLoopSnapshot().isEmpty())

        c.beginLasso(pt(200f, 200f))
        c.extendLasso(pt(260f, 300f))
        val loop = c.lassoLoopSnapshot()
        assertEquals(5, loop.size)
        assertEquals(200f, loop[0].x, 0.001f); assertEquals(200f, loop[0].y, 0.001f)
        assertEquals(260f, loop[1].x, 0.001f); assertEquals(200f, loop[1].y, 0.001f)
        assertEquals(260f, loop[2].x, 0.001f); assertEquals(300f, loop[2].y, 0.001f)
        assertEquals(200f, loop[3].x, 0.001f); assertEquals(300f, loop[3].y, 0.001f)

        c.endLasso()
        c.beginLasso(pt(0f, 0f))
        c.extendLasso(pt(50f, 50f))
        assertEquals(0f, c.lassoLoopSnapshot()[0].x, 0.001f)
        assertEquals(0f, c.lassoLoopSnapshot()[0].y, 0.001f)
    }

    @Test fun `自由套索保持追加轨迹`() {
        val c = controllerWith()
        c.beginLasso(pt(0f, 0f))
        c.extendLasso(pt(10f, 0f))
        c.extendLasso(pt(10f, 10f))

        val loop = c.lassoLoopSnapshot()
        assertEquals(3, loop.size)
        assertEquals(0f, loop[0].x, 0.001f)
        assertEquals(10f, loop[1].x, 0.001f)
        assertEquals(10f, loop[2].y, 0.001f)
    }
}
