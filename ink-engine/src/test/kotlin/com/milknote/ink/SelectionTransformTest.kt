package com.milknote.ink

import com.milknote.ink.input.InkCommit
import com.milknote.ink.input.InkController
import com.milknote.ink.input.SelectionHandle
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.ElementGeom
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.PageElement
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.orderedElements
import com.milknote.ink.serialize.ProtoInk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTransformTest {
    private fun pt(x: Float, y: Float) = InkPoint(x, y, 0.5f, 0f, 0f, 0L)

    private fun stroke(id: String = "s", tool: InkTool = InkTool.PEN) = Stroke(
        id, tool, 0xFF23221E, 0.7f, listOf(pt(20f, 20f), pt(40f, 40f)),
    )

    private fun image(id: String = "i") = ImageElement(
        id = id, src = "images/test.png", x = 60f, y = 20f, width = 40f, height = 40f,
    )

    private fun card(id: String = "c") = AnnotationCard(
        id = id, x = 120f, y = 20f, width = 80f, text = "hello",
    )

    private fun controller(
        strokes: List<Stroke> = listOf(stroke()),
        cards: List<AnnotationCard> = listOf(card()),
        images: List<ImageElement> = listOf(image()),
    ): InkController = InkController().apply {
        cardHeightProvider = { 40f }
        loadStrokes(strokes, cards, images)
    }

    private fun selectAll(c: InkController) {
        c.armLasso()
        val points = listOf(pt(0f, 0f), pt(240f, 0f), pt(240f, 100f), pt(0f, 100f))
        c.beginLasso(points.first())
        points.drop(1).forEach(c::extendLasso)
        assertTrue(c.endLasso())
    }

    @Test fun `套索混合选中笔迹图片和卡片`() {
        val c = controller()
        selectAll(c)
        assertEquals(3, c.selectionSnapshot()?.count)
        val box = c.selectionSnapshot()?.box
        assertNotNull(box)
        assertEquals(20f, box!!.l, 0.001f)
        assertEquals(200f, box.r, 0.001f)
    }

    @Test fun `手指点按旋转图片使用真实轮廓并单选`() {
        val rotated = image().copy(x = 100f, y = 100f, width = 100f, height = 40f, rotationDeg = 90f)
        val c = controller(strokes = emptyList(), cards = emptyList(), images = listOf(rotated))
        assertNull(c.selectElementAt(105f, 105f))
        assertNotNull(c.selectElementAt(130f, 120f))
        assertEquals(1, c.selectionSnapshot()?.count)
    }

    @Test fun `角柄等比缩放以对角为枢轴且可撤销重做`() {
        val c = controller(strokes = listOf(stroke()), cards = emptyList(), images = emptyList())
        selectAll(c)
        assertTrue(c.beginSelectionTransform(SelectionHandle.TOP_LEFT))
        c.moveSelectionTransform(2f, 0f)
        assertTrue(c.endSelectionTransform())
        val scaled = c.page.strokes.single()
        assertEquals(0f, scaled.points.first().x, 0.001f)
        assertEquals(0f, scaled.points.first().y, 0.001f)
        assertEquals(1.4f, scaled.baseWidthMm, 0.001f)
        c.undo()
        assertEquals(20f, c.page.strokes.single().points.first().x, 0.001f)
        c.redo()
        assertEquals(0f, c.page.strokes.single().points.first().x, 0.001f)
    }

    @Test fun `矩形任意旋转转换为等效轮廓并进入撤销栈`() {
        val rect = Stroke("r", InkTool.RECT, 0xFF23221E, 0.7f, listOf(pt(20f, 20f), pt(40f, 40f)))
        val c = controller(strokes = listOf(rect), cards = emptyList(), images = emptyList())
        selectAll(c)
        c.beginSelectionTransform(SelectionHandle.ROTATE)
        c.moveSelectionTransform(1f, 90f)
        assertTrue(c.endSelectionTransform())
        val rotated = c.page.strokes.single()
        assertEquals(InkTool.PEN, rotated.tool)
        assertEquals(5, rotated.points.size)
        assertEquals(40f, rotated.points.first().x, 0.001f)
        assertEquals(20f, rotated.points.first().y, 0.001f)
        c.undo()
        assertEquals(InkTool.RECT, c.page.strokes.single().tool)
        c.redo()
        assertEquals(InkTool.PEN, c.page.strokes.single().tool)
    }

    @Test fun `混合旋转同时提交图片角度和卡片内容缩放`() {
        val c = controller()
        val commits = mutableListOf<InkCommit>()
        c.onCommit = commits::add
        selectAll(c)
        c.beginSelectionTransform(SelectionHandle.ROTATE)
        c.moveSelectionTransform(1f, 45f)
        assertTrue(c.endSelectionTransform())
        assertEquals(45f, c.page.images.single().rotationDeg, 0.001f)
        assertEquals(45f, c.page.cards.single().rotationDeg, 0.001f)
        assertEquals(6, commits.size)
        c.undo()
        assertEquals(0f, c.page.images.single().rotationDeg, 0.001f)
        assertEquals(0f, c.page.cards.single().rotationDeg, 0.001f)
    }

    @Test fun `混合缩放保存图片尺寸和卡片缩放`() {
        val c = controller()
        selectAll(c)
        c.beginSelectionTransform(SelectionHandle.BOTTOM_RIGHT)
        c.moveSelectionTransform(1.5f, 0f)
        assertTrue(c.endSelectionTransform())
        assertEquals(60f, c.page.images.single().width, 0.001f)
        assertEquals(1.5f, c.page.cards.single().scale, 0.001f)
        c.undo()
        assertEquals(40f, c.page.images.single().width, 0.001f)
        assertEquals(1f, c.page.cards.single().scale, 0.001f)
    }

    @Test fun `取消变换不产生提交`() {
        val c = controller()
        val commits = mutableListOf<InkCommit>()
        c.onCommit = commits::add
        selectAll(c)
        c.beginSelectionTransform(SelectionHandle.ROTATE)
        c.moveSelectionTransform(1f, 90f)
        c.cancelSelectionTransform()
        assertTrue(commits.isEmpty())
        assertEquals(0f, c.page.images.single().rotationDeg, 0.001f)
        assertEquals(1f, c.selectionTransformSnapshot()?.scale ?: 0f, 0.001f)
        assertEquals(0f, c.selectionTransformSnapshot()?.rotationDeg ?: -1f, 0.001f)
    }

    @Test fun `跨类型上移下移保持全局顺序且可撤销`() {
        val c = controller()
        assertNotNull(c.selectElementAt(80f, 40f))
        assertEquals(listOf("i", "s", "c"), c.page.orderedElements().map { it.ref.id })
        assertTrue(c.reorderSelection(true))
        assertEquals(listOf("s", "i", "c"), c.page.orderedElements().map { it.ref.id })
        assertTrue(c.reorderSelection(true))
        assertEquals(listOf("s", "c", "i"), c.page.orderedElements().map { it.ref.id })
        assertFalse(c.selectionSnapshot()?.canMoveUp ?: true)
        assertTrue(c.reorderSelection(false))
        assertEquals(listOf("s", "i", "c"), c.page.orderedElements().map { it.ref.id })
        c.undo()
        assertEquals(listOf("s", "c", "i"), c.page.orderedElements().map { it.ref.id })
        c.redo()
        assertEquals(listOf("s", "i", "c"), c.page.orderedElements().map { it.ref.id })
    }

    @Test fun `混合删除一次撤销恢复全部元素`() {
        val c = controller()
        selectAll(c)
        assertTrue(c.deleteSelection())
        assertTrue(c.page.orderedElements().isEmpty())
        c.undo()
        assertEquals(3, c.page.orderedElements().size)
        assertEquals(listOf("i", "s", "c"), c.page.orderedElements().map { it.ref.id })
    }

    @Test fun `移动靠近页面左缘时吸附并显示参考线`() {
        val c = controller(strokes = listOf(stroke()), cards = emptyList(), images = emptyList())
        c.pageBoundsProvider = { com.milknote.ink.model.StrokeGeom.Box(0f, 0f, 200f, 200f) }
        selectAll(c)
        c.beginSelectionDrag(20f, 20f)
        c.moveSelectionDrag(-17f, 0f, 5f)
        assertEquals(0f, c.selectionSnapshot()?.box?.l ?: -1f, 0.001f)
        assertEquals(1, c.selectionGuidesSnapshot().size)
        assertTrue(c.selectionGuidesSnapshot().single().vertical)
        assertEquals(0f, c.selectionGuidesSnapshot().single().position, 0.001f)
        assertTrue(c.endSelectionDrag())
        assertEquals(0f, c.page.strokes.single().points.first().x, 0.001f)
        assertTrue(c.selectionGuidesSnapshot().isEmpty())
    }

    @Test fun `移动可吸附其他元素边缘`() {
        val other = stroke("other").copy(points = listOf(pt(100f, 20f), pt(120f, 40f)))
        val c = controller(strokes = listOf(stroke(), other), cards = emptyList(), images = emptyList())
        c.armLasso()
        val loop = listOf(pt(0f, 0f), pt(60f, 0f), pt(60f, 60f), pt(0f, 60f))
        c.beginLasso(loop.first()); loop.drop(1).forEach(c::extendLasso); c.endLasso()
        c.beginSelectionDrag(20f, 20f)
        c.moveSelectionDrag(77f, 0f, 5f)
        assertEquals(100f, c.selectionSnapshot()?.box?.l ?: -1f, 0.001f)
        assertEquals(100f, c.selectionGuidesSnapshot().single().position, 0.001f)
        c.cancelSelectionDrag()
        assertTrue(c.selectionGuidesSnapshot().isEmpty())
        assertEquals(20f, c.page.strokes.first().points.first().x, 0.001f)
    }

    @Test fun `缩放吸附保持对角固定并命中页面右缘`() {
        val c = controller(strokes = listOf(stroke()), cards = emptyList(), images = emptyList())
        c.pageBoundsProvider = { com.milknote.ink.model.StrokeGeom.Box(0f, 0f, 100f, 200f) }
        selectAll(c)
        c.beginSelectionTransform(SelectionHandle.BOTTOM_RIGHT)
        c.moveSelectionTransform(3.9f, 0f, 5f)
        assertEquals(4f, c.selectionTransformSnapshot()?.scale ?: 0f, 0.001f)
        assertEquals(100f, c.selectionSnapshot()?.box?.r ?: -1f, 0.001f)
        assertEquals(20f, c.selectionSnapshot()?.box?.l ?: -1f, 0.001f)
        assertEquals(100f, c.selectionGuidesSnapshot().single().position, 0.001f)
        c.endSelectionTransform()
        assertEquals(100f, c.page.strokes.single().points.last().x, 0.001f)
    }

    @Test fun `旋转在六度内吸附四十五度档位`() {
        val c = controller(strokes = listOf(stroke()), cards = emptyList(), images = emptyList())
        selectAll(c)
        c.beginSelectionTransform(SelectionHandle.ROTATE)
        c.moveSelectionTransform(1f, 43f)
        assertEquals(45f, c.selectionTransformSnapshot()?.rotationDeg ?: 0f, 0.001f)
        c.moveSelectionTransform(1f, 52f)
        assertEquals(52f, c.selectionTransformSnapshot()?.rotationDeg ?: 0f, 0.001f)
        c.moveSelectionTransform(1f, -3f)
        assertEquals(0f, c.selectionTransformSnapshot()?.rotationDeg ?: 99f, 0.001f)
    }

    @Test fun `当前选区可关闭吸附且新选区默认恢复开启`() {
        val c = controller(strokes = listOf(stroke()), cards = emptyList(), images = emptyList())
        c.pageBoundsProvider = { com.milknote.ink.model.StrokeGeom.Box(0f, 0f, 200f, 200f) }
        selectAll(c)
        assertTrue(c.selectionSnapshot()?.snapEnabled == true)
        assertFalse(c.toggleSelectionSnap())
        c.beginSelectionDrag(20f, 20f)
        c.moveSelectionDrag(-17f, 0f, 5f)
        assertEquals(3f, c.selectionSnapshot()?.box?.l ?: -1f, 0.001f)
        assertTrue(c.selectionGuidesSnapshot().isEmpty())
        assertTrue(c.toggleSelectionSnap())
        c.moveSelectionDrag(-17f, 0f, 5f)
        assertEquals(0f, c.selectionSnapshot()?.box?.l ?: -1f, 0.001f)
        assertEquals(1, c.selectionGuidesSnapshot().size)
        c.clearSelection()
        selectAll(c)
        assertTrue(c.selectionSnapshot()?.snapEnabled == true)
    }

    @Test fun `关闭吸附后旋转和缩放保持自由变换`() {
        val c = controller(strokes = listOf(stroke()), cards = emptyList(), images = emptyList())
        c.pageBoundsProvider = { com.milknote.ink.model.StrokeGeom.Box(0f, 0f, 100f, 200f) }
        selectAll(c)
        c.toggleSelectionSnap()
        c.beginSelectionTransform(SelectionHandle.ROTATE)
        c.moveSelectionTransform(1f, 43f)
        assertEquals(43f, c.selectionTransformSnapshot()?.rotationDeg ?: 0f, 0.001f)
        c.cancelSelectionTransform()
        c.beginSelectionTransform(SelectionHandle.BOTTOM_RIGHT)
        c.moveSelectionTransform(3.9f, 0f, 5f)
        assertEquals(3.9f, c.selectionTransformSnapshot()?.scale ?: 0f, 0.001f)
        assertTrue(c.selectionGuidesSnapshot().isEmpty())
    }

    @Test fun `笔迹层级经 proto 往返保持`() {
        val original = stroke().copy(zIndex = 42L)
        val out = ByteArrayOutputStream()
        ProtoInk.writeRecord(out, ProtoInk.recordOf(original))
        val restored = ProtoInk.readPage(ByteArrayInputStream(out.toByteArray())).single()
        assertEquals(42L, restored.zIndex)
    }

    @Test fun `旋转元素几何包围盒与中心命中一致`() {
        val img = image().copy(height = 20f, rotationDeg = 90f)
        val box = ElementGeom.bounds(img)
        assertEquals(70f, box.l, 0.001f)
        assertEquals(10f, box.t, 0.001f)
        assertEquals(90f, box.r, 0.001f)
        assertEquals(50f, box.b, 0.001f)
        assertTrue(ElementGeom.contains(img, 80f, 30f))
    }
}
