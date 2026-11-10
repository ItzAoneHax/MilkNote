package com.milknote.ink

import com.milknote.ink.input.InkCommit
import com.milknote.ink.input.InkController
import com.milknote.ink.input.SelectionHandle
import com.milknote.ink.model.ElementGeom
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.LassoContent
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.MediaKind
import com.milknote.ink.model.orderedElements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 媒体元素:插入/删除、套索、混合变换、整页清除与撤销重做镜像。 */
class MediaControllerTest {

    private fun pt(x: Float, y: Float) = InkPoint(x, y, 0.5f, 0f, 0f, 0L)

    private fun media(
        id: String = "m",
        x: Float = 60f,
        y: Float = 20f,
        width: Float = 40f,
        height: Float = 40f,
        kind: MediaKind = MediaKind.VIDEO,
    ) = MediaElement(
        id = id, kind = kind, src = "media/audio.m4a", x = x, y = y,
        width = width, height = height, durationMs = 5000L,
    )

    private fun controller(vararg media: MediaElement): InkController = InkController().apply {
        cardHeightProvider = { 40f }
        loadStrokes(emptyList(), emptyList(), emptyList(), media.toList())
    }

    private fun selectAll(c: InkController) {
        c.armLasso()
        val loop = listOf(pt(0f, 0f), pt(200f, 0f), pt(200f, 120f), pt(0f, 120f))
        c.beginLasso(loop.first())
        loop.drop(1).forEach(c::extendLasso)
        assertTrue(c.endLasso())
    }

    @Test fun `插入媒体分配层序并镜像新增删除`() {
        val c = InkController()
        val commits = mutableListOf<InkCommit>()
        c.onCommit = commits::add

        c.addMediaElement(media())
        assertEquals(1L, c.page.media.single().zIndex)
        assertEquals(1, c.page.media.size)
        assertTrue(commits.single() is InkCommit.MediaAdded)

        c.undo()
        assertTrue(c.page.media.isEmpty())
        assertTrue(commits.last() is InkCommit.MediaRemoved)

        c.redo()
        assertEquals(1, c.page.media.size)
        assertTrue(commits.last() is InkCommit.MediaAdded)
    }

    @Test fun `手指点按媒体可单选且可删除撤销`() {
        val c = controller(media())
        assertNotNull(c.selectElementAt(80f, 40f))
        assertEquals(1, c.selectionSnapshot()?.count)
        assertEquals("m", c.selectedMedia()?.id)

        assertTrue(c.deleteSelection())
        assertTrue(c.page.media.isEmpty())
        c.undo()
        assertEquals(listOf("m"), c.page.media.map { it.id })
    }

    @Test fun `套索默认选中媒体 关闭 MEDIA 筛选后不选`() {
        val c = controller(media())
        selectAll(c)
        assertNotNull(c.selectedMedia())

        c.setLassoFilters(setOf(LassoContent.INK))
        c.armLasso()
        val loop = listOf(pt(0f, 0f), pt(200f, 0f), pt(200f, 120f), pt(0f, 120f))
        c.beginLasso(loop.first())
        loop.drop(1).forEach(c::extendLasso)
        assertFalse(c.endLasso())
        assertNull(c.selectionSnapshot())
    }

    @Test fun `媒体旋转提交角度并可撤销重做`() {
        val c = controller(media())
        assertNotNull(c.selectElementAt(80f, 40f))
        c.beginSelectionTransform(SelectionHandle.ROTATE)
        c.moveSelectionTransform(1f, 45f)
        assertTrue(c.endSelectionTransform())
        assertEquals(45f, c.page.media.single().rotationDeg, 0.001f)
        // 正方形围绕自身中心旋转，中心与左上角均不变。
        assertEquals(60f, c.page.media.single().x, 0.001f)

        c.undo()
        assertEquals(0f, c.page.media.single().rotationDeg, 0.001f)
        c.redo()
        assertEquals(45f, c.page.media.single().rotationDeg, 0.001f)
    }

    @Test fun `媒体缩放保持中心并更新宽高`() {
        val c = controller(media())
        assertNotNull(c.selectElementAt(80f, 40f))
        c.beginSelectionTransform(SelectionHandle.BOTTOM_RIGHT)
        c.moveSelectionTransform(1.5f, 0f)
        assertTrue(c.endSelectionTransform())
        val scaled = c.page.media.single()
        assertEquals(60f, scaled.width, 0.001f)
        assertEquals(60f, scaled.height, 0.001f)
        // 枢轴 = 左上 (60,20):中心 (80,40) → (90,50),新左上 = (60,20)
        assertEquals(60f, scaled.x, 0.001f)
        assertEquals(20f, scaled.y, 0.001f)

        c.undo()
        assertEquals(40f, c.page.media.single().width, 0.001f)
    }

    @Test fun `整页清除包含媒体并可撤销恢复`() {
        val c = controller(media("m1"), media("m2", x = 120f))
        val commits = mutableListOf<InkCommit>()
        c.onCommit = commits::add
        assertTrue(c.clearPageContent())
        assertTrue(c.page.media.isEmpty())
        assertTrue(commits.any { it is InkCommit.MediaRemoved })

        c.undo()
        assertEquals(listOf("m1", "m2"), c.page.media.map { it.id })
    }

    @Test fun `媒体参与全局调层`() {
        val c = controller(media().copy(zIndex = 1L))
        c.addMediaElement(media("top", x = 0f, y = 0f))
        assertEquals(listOf("m", "top"), c.page.orderedElements().map { it.ref.id })
        assertNotNull(c.selectElementAt(80f, 40f))
        assertEquals("m", c.selectedMedia()?.id)
        assertTrue(c.reorderSelection(true))
        assertEquals(listOf("top", "m"), c.page.orderedElements().map { it.ref.id })
        c.undo()
        assertEquals(listOf("m", "top"), c.page.orderedElements().map { it.ref.id })
    }

    @Test fun `旋转媒体包围盒与命中共用同一轮廓`() {
        val rotated = media(width = 100f, height = 40f, x = 100f, y = 100f).copy(rotationDeg = 90f)
        val box = ElementGeom.bounds(rotated)
        assertEquals(130f, box.l, 0.001f)
        assertEquals(70f, box.t, 0.001f)
        assertEquals(170f, box.r, 0.001f)
        assertEquals(170f, box.b, 0.001f)
        assertTrue(ElementGeom.contains(rotated, 150f, 150f))
    }
}
