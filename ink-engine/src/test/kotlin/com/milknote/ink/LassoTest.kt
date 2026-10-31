package com.milknote.ink

import com.milknote.ink.input.InkCommit
import com.milknote.ink.input.InkController
import com.milknote.ink.model.InkOp
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.LassoGeom
import com.milknote.ink.model.PageInkState
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.ToolConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 套索几何:闭合路径命中(射线法,凹多边形) */
class LassoGeomTest {

    private fun pt(x: Float, y: Float) = InkPoint(x, y, 0.5f, 0f, 0f, 0L)

    private fun loop(vararg xy: Pair<Float, Float>) = xy.map { pt(it.first, it.second) }

    @Test fun `矩形内外的点`() {
        val square = loop(0f to 0f, 100f to 0f, 100f to 100f, 0f to 100f)
        assertTrue(LassoGeom.contains(square, 50f, 50f))
        assertFalse(LassoGeom.contains(square, 150f, 50f))
        assertFalse(LassoGeom.contains(square, -1f, -1f))
    }

    @Test fun `凹多边形 - 缺口里的点在外`() {
        // L 形:右上角缺口 (50..100, 0..50)
        val lShape = loop(0f to 0f, 50f to 0f, 50f to 50f, 100f to 50f, 100f to 100f, 0f to 100f)
        assertTrue(LassoGeom.contains(lShape, 25f, 25f))    // 左上臂
        assertTrue(LassoGeom.contains(lShape, 75f, 75f))    // 下臂
        assertFalse(LassoGeom.contains(lShape, 75f, 25f))   // 缺口
    }

    @Test fun `少于三点不构成区域`() {
        assertFalse(LassoGeom.contains(loop(0f to 0f, 10f to 10f), 5f, 5f))
    }

    @Test fun `捕获 = 全部点在路径内`() {
        val square = loop(0f to 0f, 100f to 0f, 100f to 100f, 0f to 100f)
        val inside = Stroke("a", InkTool.PEN, 0, 0.7f, listOf(pt(20f, 20f), pt(80f, 80f)))
        val crossing = Stroke("b", InkTool.PEN, 0, 0.7f, listOf(pt(20f, 20f), pt(180f, 80f)))
        assertTrue(LassoGeom.captures(square, inside))
        assertFalse(LassoGeom.captures(square, crossing))
        assertFalse(LassoGeom.captures(square, Stroke("e", InkTool.PEN, 0, 0.7f, emptyList())))
    }
}

/** 套索选择状态机:圈选 → 拖动/删除,进撤销栈 + 落盘镜像 */
class InkControllerLassoTest {

    private fun s(id: String, x: Float, y: Float) = Stroke(
        id = id, tool = InkTool.PEN, color = 0xFF23221E, baseWidthMm = 0.7f,
        points = listOf(InkPoint(x, y, 0.5f, 0f, 0f, 1L), InkPoint(x + 10f, y, 0.6f, 0f, 0f, 9L)),
    )

    private fun loopAround(x: Float, y: Float, r: Float = 40f): List<InkPoint> = listOf(
        InkPoint(x - r, y - r, 0.5f, 0f, 0f, 0L),
        InkPoint(x + r, y - r, 0.5f, 0f, 0f, 1L),
        InkPoint(x + r, y + r, 0.5f, 0f, 0f, 2L),
        InkPoint(x - r, y + r, 0.5f, 0f, 0f, 3L),
    )

    private fun controllerWith(vararg strokes: Stroke): Pair<InkController, MutableList<InkCommit>> {
        val page = PageInkState()
        strokes.forEach { page.apply(InkOp.Add(it)) }
        val c = InkController()
        c.loadStrokes(page.strokes.toList())
        val mirror = mutableListOf<InkCommit>()
        c.onCommit = { mirror.add(it) }
        return c to mirror
    }

    private fun selectAround(c: InkController, x: Float, y: Float) {
        c.armLasso()
        val loop = loopAround(x, y)
        c.beginLasso(loop.first())
        loop.drop(1).forEach { c.extendLasso(it) }
        c.endLasso()
    }

    @Test fun `圈选 - 全包围的笔迹被选中`() {
        val (c, _) = controllerWith(s("a", 50f, 50f), s("b", 500f, 500f))
        selectAround(c, 55f, 50f) // 只圈住 a(其两点 50,50 与 60,50 都在 (15..95)×(10..90) 内)
        val sel = c.selectionSnapshot()
        assertNotNull(sel)
        assertEquals(1, sel!!.count)
        assertTrue(c.selectedStrokes().all { it.id == "a" })
    }

    @Test fun `删除选中 - 进撤销栈并镜像 tombstone`() {
        val (c, mirror) = controllerWith(s("a", 50f, 50f), s("b", 500f, 500f))
        selectAround(c, 55f, 50f)
        assertTrue(c.deleteSelection())
        assertEquals(listOf("b"), c.page.strokes.map { it.id })
        assertEquals(1, mirror.size)
        assertTrue(mirror[0] is InkCommit.Tombstoned && (mirror[0] as InkCommit.Tombstoned).strokeId == "a")
        assertTrue(c.page.canUndo)
        c.undo()
        assertEquals(listOf("a", "b"), c.page.strokes.map { it.id }) // 原序恢复
        assertNull(c.selectionSnapshot())                             // undo 清选中
    }

    @Test fun `拖动选中 - 位移落为新笔迹并可撤销还原`() {
        val (c, mirror) = controllerWith(s("a", 50f, 50f))
        selectAround(c, 55f, 50f)
        c.beginSelectionDrag(50f, 50f)
        c.moveSelectionDrag(30f, 40f)
        assertTrue(c.endSelectionDrag())

        assertEquals(1, c.page.strokes.size)
        val moved = c.page.strokes[0]
        assertFalse(moved.id == "a")                                  // 新 id
        assertEquals(80f, moved.points[0].x, 0.001f)                  // 50+30
        assertEquals(90f, moved.points[0].y, 0.001f)                  // 50+40
        // 镜像:先 tombstone 旧,再 added 新(追加流重放语义正确)
        assertEquals(2, mirror.size)
        assertTrue(mirror[0] is InkCommit.Tombstoned)
        assertTrue(mirror[1] is InkCommit.Added)

        c.undo()
        assertEquals(listOf("a"), c.page.strokes.map { it.id })       // 原位原序还原
        assertEquals(50f, c.page.strokes[0].points[0].x, 0.001f)
        c.redo()
        assertEquals(80f, c.page.strokes[0].points[0].x, 0.001f)
    }

    @Test fun `空圈选与未选中删除`() {
        val (c, mirror) = controllerWith(s("a", 500f, 500f))
        selectAround(c, 50f, 50f) // 没圈到任何笔迹
        assertNull(c.selectionSnapshot())
        assertFalse(c.deleteSelection())
        assertTrue(mirror.isEmpty())
        assertFalse(c.lassoArmed) // 一圈后自动退出武装
    }

    @Test fun `未位移拖动不产生记录`() {
        val (c, mirror) = controllerWith(s("a", 50f, 50f))
        selectAround(c, 55f, 50f)
        c.beginSelectionDrag(50f, 50f)
        c.moveSelectionDrag(0.2f, 0f) // <1px 视为未移动
        assertFalse(c.endSelectionDrag())
        assertTrue(mirror.isEmpty())
        assertEquals(listOf("a"), c.page.strokes.map { it.id })
    }
}

/** 临时橡皮:副键按住书写 = 该笔工具覆盖为橡皮(整笔划擦除) */
class TempEraserTest {

    private fun pt(x: Float, y: Float) = InkPoint(x, y, 0.5f, 0f, 0f, 0L)

    @Test fun `按住副键书写 - 覆盖为橡皮并整笔划擦除`() {
        val page = PageInkState()
        page.apply(
            InkOp.Add(
                Stroke("a", InkTool.PEN, 0xFF23221E, 0.7f, listOf(pt(50f, 50f), pt(80f, 80f)))
            )
        )
        val c = InkController()
        c.loadStrokes(page.strokes.toList())
        val mirror = mutableListOf<InkCommit>()
        c.onCommit = { mirror.add(it) }

        // 视图在 holdActionProvider=TEMP_ERASER 时以橡皮覆盖落笔(InkSurfaceView 同款参数)
        c.beginStroke(pt(40f, 40f), ToolConfig(tool = InkTool.ERASER, baseWidthMm = 4f))
        c.extendStroke(listOf(pt(90f, 90f)))
        c.endStroke()

        assertTrue(c.page.strokes.isEmpty())
        assertEquals(1, mirror.size)
        assertTrue(mirror[0] is InkCommit.Tombstoned && (mirror[0] as InkCommit.Tombstoned).strokeId == "a")
        c.undo()
        assertEquals(1, c.page.strokes.size) // 撤销恢复被擦笔迹
    }

    @Test fun `一次性临时橡皮 - 取用即复位`() {
        val c = InkController()
        assertFalse(c.consumeOneShotEraser())
        c.armOneShotEraser()
        assertTrue(c.consumeOneShotEraser())
        assertFalse(c.consumeOneShotEraser())
    }
}
