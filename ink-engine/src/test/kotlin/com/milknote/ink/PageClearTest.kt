package com.milknote.ink

import com.milknote.ink.input.InkCommit
import com.milknote.ink.input.InkController
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 整页清除:笔迹/批注卡/图片一起作为一个 Batch 进撤销栈,并走既有 InkCommit 落盘镜像。 */
class PageClearTest {

    private fun stroke(id: String) = Stroke(
        id = id,
        tool = InkTool.PEN,
        color = 0xFF23221E,
        baseWidthMm = 0.7f,
        points = listOf(InkPoint(0f, 0f, 0.5f, 0f, 0f, 1L), InkPoint(8f, 8f, 0.5f, 0f, 0f, 9L)),
    )

    @Test fun `清空整页可撤销并恢复三类内容`() {
        val controller = InkController()
        val commits = mutableListOf<InkCommit>()
        controller.onCommit = { commits += it }

        controller.loadStrokes(listOf(stroke("s1"), stroke("s2")))
        controller.addAnnotationCard(
            AnnotationCard(id = "c1", x = 1f, y = 2f, width = 100f, text = "t", createdAt = 1L),
        )
        controller.addAnnotationCard(
            AnnotationCard(id = "c2", x = 1f, y = 2f, width = 100f, text = "t", createdAt = 2L),
        )
        controller.addImageElement(
            ImageElement(id = "i1", src = "images/a.png", x = 3f, y = 4f, width = 50f, height = 60f),
        )
        controller.addImageElement(
            ImageElement(id = "i2", src = "images/b.png", x = 3f, y = 4f, width = 50f, height = 60f),
        )
        commits.clear()

        assertTrue(controller.clearPageContent())
        assertTrue(controller.page.strokes.isEmpty())
        assertTrue(controller.page.cards.isEmpty())
        assertTrue(controller.page.images.isEmpty())
        assertTrue(controller.page.canUndo)
        assertTrue(commits.any { it is InkCommit.Tombstoned })
        assertTrue(commits.any { it is InkCommit.CardRemoved })
        assertTrue(commits.any { it is InkCommit.ImageRemoved })

        controller.undo()
        assertEquals(listOf("s1", "s2"), controller.page.strokes.map { it.id })
        assertEquals(listOf("c1", "c2"), controller.annotationCards().map { it.id })
        assertEquals(listOf("i1", "i2"), controller.images().map { it.id })
    }

    @Test fun `空页清除返回 false 不产生撤销项`() {
        val controller = InkController()
        assertFalse(controller.clearPageContent())
        assertFalse(controller.page.canUndo)
    }
}
