package com.milknote.ink

import com.milknote.ink.input.InkCommit
import com.milknote.ink.input.InkController
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.DrawingChange
import com.milknote.ink.model.ElementRef
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkOp
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.MediaKind
import com.milknote.ink.model.PageInkState
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.ToolConfig
import com.milknote.ink.serialize.ProtoInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 统一绘图变更流:每 UI Batch 一次 [DrawingChange],旧 onCommit 保持逐项镜像;
 * loadDrawing 恢复内容与两栈;笔迹/元素生成时间(createdAt)锚点与持久化。
 */
class DrawingChangeTest {

    private fun pt(x: Float, y: Float, time: Long = 1L) = InkPoint(x, y, 0.5f, 0f, 0f, time)

    private fun stroke(
        id: String,
        x: Float = 50f,
        y: Float = 50f,
        createdAt: Long = 0L,
    ) = Stroke(
        id = id,
        tool = InkTool.PEN,
        color = 0xFF23221E,
        baseWidthMm = 0.7f,
        points = listOf(pt(x, y), pt(x + 10f, y, time = 9L)),
        createdAt = createdAt,
    )

    private fun card(id: String, createdAt: Long = 0L) =
        AnnotationCard(id = id, x = 1f, y = 2f, width = 100f, text = "t", createdAt = createdAt)

    private fun image(id: String, createdAt: Long = 0L) = ImageElement(
        id = id, src = "images/$id.png", x = 3f, y = 4f, width = 50f, height = 60f,
        createdAt = createdAt,
    )

    private fun media(id: String, createdAt: Long = 0L) = MediaElement(
        id = id, kind = MediaKind.AUDIO, src = "media/$id.m4a", x = 5f, y = 6f,
        width = 70f, height = 80f, createdAt = createdAt,
    )

    @Test
    fun `一笔只发一次 Apply 且旧 onCommit 仍逐项回调`() {
        val controller = InkController()
        val changes = mutableListOf<DrawingChange>()
        val commits = mutableListOf<InkCommit>()
        controller.onDrawingChange = { changes += it }
        controller.onCommit = { commits += it }

        controller.beginStroke(pt(0f, 0f))
        controller.extendStroke(listOf(pt(4f, 4f), pt(8f, 8f)))
        val stroke = controller.endStroke()!!

        assertEquals(1, changes.size)
        val apply = changes.single() as DrawingChange.Apply
        assertEquals(stroke.id, (apply.op as InkOp.Add).stroke.id)
        assertEquals(listOf<InkCommit>(InkCommit.Added(stroke)), commits)
    }

    @Test
    fun `整笔擦除多笔仍只发一次 Apply 但旧回调逐笔墓碑`() {
        val controller = InkController()
        controller.loadStrokes(listOf(stroke("a", 10f, 10f), stroke("b", 14f, 14f), stroke("far", 500f, 500f)))
        val changes = mutableListOf<DrawingChange>()
        val commits = mutableListOf<InkCommit>()
        controller.onDrawingChange = { changes += it }
        controller.onCommit = { commits += it }

        controller.beginStroke(pt(12f, 12f), ToolConfig(tool = InkTool.ERASER, baseWidthMm = 4f))
        controller.endStroke()

        assertEquals(1, changes.size)
        val batch = (changes.single() as DrawingChange.Apply).op as InkOp.Batch
        assertEquals(2, batch.ops.filterIsInstance<InkOp.Remove>().size)
        assertEquals(2, commits.filterIsInstance<InkCommit.Tombstoned>().size)
        assertEquals(listOf("far"), controller.page.strokes.map { it.id })
    }

    @Test
    fun `元素增删移动换层各一次 Apply`() {
        val controller = InkController()
        controller.loadStrokes(listOf(stroke("s1"), stroke("s2")), listOf(card("c1")), listOf(image("i1")), listOf(media("m1")))
        val changes = mutableListOf<DrawingChange>()
        controller.onDrawingChange = { changes += it }

        controller.moveElementById(ElementRef.Kind.CARD, "c1", targetX = 9f, targetY = 9f, dx = null, dy = null)
        assertEquals(1, changes.size)
        assertTrue((changes.single() as DrawingChange.Apply).op is InkOp.Batch)

        changes.clear()
        controller.moveElementById(ElementRef.Kind.IMAGE, "i1", targetX = null, targetY = null, dx = 4f, dy = 4f)
        assertEquals(1, changes.size)

        changes.clear()
        controller.moveElementById(ElementRef.Kind.STROKE, "s1", targetX = null, targetY = null, dx = 3f, dy = 3f)
        assertEquals(1, changes.size)

        changes.clear()
        controller.deleteElementById(ElementRef.Kind.MEDIA, "m1")
        assertEquals(1, changes.size)
        assertTrue((changes.single() as DrawingChange.Apply).op is InkOp.RemoveMedia)

        changes.clear()
        controller.addImageElement(image("i2"))
        controller.addAnnotationCard(card("c2"))
        controller.addMediaElement(media("m2"))
        assertEquals(3, changes.size)

        changes.clear()
        controller.loadStrokes(listOf(stroke("s1", 10f, 50f)), listOf(card("c1")), listOf(image("i1")))
        assertTrue(controller.selectElementAt(10f, 10f) != null)
        assertTrue(controller.reorderSelection(true))
        assertEquals(1, changes.size)
        assertTrue((changes.single() as DrawingChange.Apply).op is InkOp.Reorder)
    }

    @Test
    fun `批量清页一次 Apply 撤销重做只发动作码`() {
        val controller = InkController()
        controller.loadStrokes(listOf(stroke("s1"), stroke("s2")), listOf(card("c1")), listOf(image("i1")), listOf(media("m1")))
        val changes = mutableListOf<DrawingChange>()
        controller.onDrawingChange = { changes += it }

        assertTrue(controller.clearPageContent())
        assertEquals(1, changes.size)
        val batch = (changes.single() as DrawingChange.Apply).op as InkOp.Batch
        assertEquals(2 + 1 + 1 + 1, batch.ops.size)
        assertTrue(controller.page.strokes.isEmpty() && controller.page.cards.isEmpty())

        changes.clear()
        controller.undo()
        assertEquals(listOf<DrawingChange>(DrawingChange.Undo), changes)
        assertEquals(listOf("s1", "s2"), controller.page.strokes.map { it.id })

        changes.clear()
        controller.redo()
        assertEquals(listOf<DrawingChange>(DrawingChange.Redo), changes)
        assertTrue(controller.page.strokes.isEmpty())
    }

    @Test
    fun `loadDrawing 恢复内容与两栈且不触发写入`() {
        val source = InkController()
        source.loadStrokes(listOf(stroke("a", createdAt = 111L)))
        source.addAnnotationCard(card("c1", createdAt = 222L))
        source.addImageElement(image("i1", createdAt = 333L))
        source.undo() // 图片进 redo 栈

        val dest = InkController()
        val changes = mutableListOf<DrawingChange>()
        val commits = mutableListOf<InkCommit>()
        dest.onDrawingChange = { changes += it }
        dest.onCommit = { commits += it }

        dest.loadDrawing(source.page)

        assertTrue(changes.isEmpty())
        assertTrue(commits.isEmpty()) // loadDrawing 不触发落盘回调
        assertEquals(listOf("a"), dest.page.strokes.map { it.id })
        assertEquals(listOf("c1"), dest.page.cards.map { it.id })
        assertTrue(dest.page.images.isEmpty())
        assertEquals(111L, dest.page.strokes.single().createdAt)
        assertTrue(dest.page.canUndo)
        assertTrue(dest.page.canRedo)

        // 副本:改源页不影响已加载页
        source.page.strokes.clear()
        source.page.cards.clear()
        assertEquals(listOf("a"), dest.page.strokes.map { it.id })
        assertEquals(listOf("c1"), dest.page.cards.map { it.id })

        dest.redo()
        assertEquals(listOf<DrawingChange>(DrawingChange.Redo), changes)
        assertEquals(listOf("i1"), dest.page.images.map { it.id })
    }

    @Test
    fun `生成时间锚点 起始与结束之间且旧数据缺省为 0`() {
        val controller = InkController()
        val before = System.currentTimeMillis()
        controller.beginStroke(pt(0f, 0f))
        val afterBegin = System.currentTimeMillis()
        controller.extendStroke(listOf(pt(4f, 4f, 5L), pt(8f, 8f, 9L)))
        Thread.sleep(5)
        val stroke = controller.endStroke()!!

        assertTrue(stroke.createdAt in before..afterBegin)

        // 旧笔记点时间已足够推导时长:末点 - 首点
        assertEquals(8L, stroke.points.last().timeMs - stroke.points.first().timeMs)
    }

    @Test
    fun `Stroke createdAt 走 protobuf 可选字段且旧数据不伪造`() {
        val timed = stroke("new", createdAt = 1_700_000_000_123L)
        val record = ProtoInk.recordOf(timed)
        assertTrue(record.stroke.hasCreatedAt())

        val out = ByteArrayOutputStream()
        ProtoInk.writeRecord(out, record)
        val back = ProtoInk.readPage(ByteArrayInputStream(out.toByteArray()))
        assertEquals(1_700_000_000_123L, back.single().createdAt)

        val legacy = stroke("old")
        assertFalse(ProtoInk.recordOf(legacy).stroke.hasCreatedAt())
        val legacyOut = ByteArrayOutputStream()
        ProtoInk.writeRecord(legacyOut, ProtoInk.recordOf(legacy))
        val legacyBack = ProtoInk.readPage(ByteArrayInputStream(legacyOut.toByteArray()))
        assertEquals(0L, legacyBack.single().createdAt)
    }

    @Test
    fun `中央创建入口补齐元素时间且保留已有时间`() {
        val controller = InkController()
        controller.addAnnotationCard(card("c0", createdAt = 0L))
        controller.addImageElement(image("i0", createdAt = 0L))
        controller.addMediaElement(media("m0", createdAt = 0L))
        controller.addAnnotationCard(card("c1", createdAt = 42L))

        val now = System.currentTimeMillis()
        assertTrue(controller.page.cards.first { it.id == "c0" }.createdAt in 1..now)
        assertTrue(controller.page.images.single().createdAt in 1..now)
        assertTrue(controller.page.media.single().createdAt in 1..now)
        assertEquals(42L, controller.page.cards.first { it.id == "c1" }.createdAt)
    }

    @Test
    fun `原地变换保留原笔生成时间`() {
        val controller = InkController()
        controller.loadStrokes(listOf(stroke("a", 50f, 50f, createdAt = 777L)))
        val loop = listOf(
            pt(10f, 10f), pt(100f, 10f), pt(100f, 100f), pt(10f, 100f),
        )
        controller.armLasso()
        controller.beginLasso(loop.first())
        loop.drop(1).forEach { controller.extendLasso(it) }
        assertTrue(controller.endLasso())

        controller.beginSelectionDrag(50f, 50f)
        controller.moveSelectionDrag(30f, 40f)
        assertTrue(controller.endSelectionDrag())

        val moved = controller.page.strokes.single()
        assertEquals("a", moved.id) // 原地变换保持 stable id
        assertEquals(777L, moved.createdAt)
        assertTrue(controller.page.removedStrokes().isEmpty())
    }

    @Test
    fun `removedStrokes 只取仍生效且当前不存在的笔迹`() {
        val page = PageInkState()
        page.apply(InkOp.Add(stroke("a")))
        page.apply(InkOp.Add(stroke("b")))
        page.apply(InkOp.Remove(stroke("a"), 0))
        assertEquals(listOf("a"), page.removedStrokes().map { it.id })

        page.undo()
        assertTrue(page.removedStrokes().isEmpty())
        page.redo()
        assertEquals(listOf("a"), page.removedStrokes().map { it.id })

        // 同 id 移动(Remove+Add 同 id)不算擦除
        val movedPage = PageInkState()
        val original = stroke("m")
        movedPage.apply(InkOp.Add(original))
        val moved = original.copy(points = original.points.map { it.copy(x = it.x + 10f) })
        movedPage.apply(InkOp.Batch(listOf(InkOp.Remove(original, 0), InkOp.Add(moved))))
        assertTrue(movedPage.removedStrokes().isEmpty())

        // 撤销后的删除不算仍生效
        val undonePage = PageInkState()
        undonePage.apply(InkOp.Add(original))
        undonePage.apply(InkOp.Remove(original, 0))
        undonePage.undo()
        assertTrue(undonePage.removedStrokes().isEmpty())
    }
}
