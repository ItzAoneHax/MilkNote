package com.milknote.ink

import com.milknote.ink.input.InkCommit
import com.milknote.ink.input.InkController
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.CardTextAlign
import com.milknote.ink.model.CardTextStyle
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.LassoContent
import com.milknote.ink.model.PageImageKind
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.StrokeGeom
import com.milknote.ink.model.ToolConfig
import com.milknote.ink.model.isShape
import com.milknote.ink.serialize.ProtoInk
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FusionToolTest {
    private fun pt(x: Float, y: Float) = InkPoint(x, y, 0.5f, 0f, 0f, 0L)

    @Test
    fun `激光抬笔不产生永久笔迹 撤销或落盘`() {
        val controller = InkController()
        val commits = mutableListOf<InkCommit>()
        controller.onCommit = commits::add
        controller.tool = ToolConfig(tool = InkTool.LASER)
        controller.beginStroke(pt(1f, 1f))
        controller.extendStroke(listOf(pt(20f, 20f)))
        val transient = controller.endStroke()
        assertEquals(InkTool.LASER, transient?.tool)
        assertTrue(controller.page.strokes.isEmpty())
        assertTrue(commits.isEmpty())
        assertFalse(controller.page.canUndo)
    }

    @Test
    fun `套索筛选只捕获允许类型 默认仍支持混合`() {
        val controller = InkController()
        controller.cardHeightProvider = { 40f }
        controller.loadStrokes(
            strokes = listOf(Stroke("ink", InkTool.PEN, 0, 1f, listOf(pt(20f, 20f), pt(30f, 30f)))),
            cards = listOf(AnnotationCard("card", 10f, 10f, 60f, text = "text")),
            images = listOf(ImageElement("image", "images/a.png", 5f, 5f, 70f, 70f)),
        )
        controller.setLassoFilters(setOf(LassoContent.INK))
        val loop = listOf(pt(0f, 0f), pt(100f, 0f), pt(100f, 100f), pt(0f, 100f))
        controller.beginLasso(loop.first()); loop.drop(1).forEach(controller::extendLasso)
        assertTrue(controller.endLasso())
        assertEquals(1, controller.selectionSnapshot()?.count)
        assertEquals(listOf("ink"), controller.selectedStrokes().map { it.id })
        controller.setLassoFilters(LassoContent.entries.toSet())
        controller.beginLasso(loop.first()); loop.drop(1).forEach(controller::extendLasso)
        controller.endLasso()
        assertEquals(3, controller.selectionSnapshot()?.count)
    }

    @Test
    fun `文字再编辑保留 id 和层级且可撤销重做`() {
        val controller = InkController()
        val original = AnnotationCard("card", 0f, 0f, 200f, text = "old", zIndex = 9)
        controller.loadStrokes(cards = listOf(original))
        val style = CardTextStyle(36f, 0xFFD2483C, CardTextAlign.CENTER, bold = true, underline = true)
        assertTrue(controller.updateAnnotationCard(original.copy(text = "new", textStyle = style)))
        assertEquals("new", controller.page.cards.single().text)
        assertEquals(9L, controller.page.cards.single().zIndex)
        controller.undo()
        assertEquals("old", controller.page.cards.single().text)
        controller.redo()
        assertEquals(style, controller.page.cards.single().textStyle)
    }

    @Test
    fun `旧文字与图片 JSON 缺省新字段可回退`() {
        val json = Json { ignoreUnknownKeys = true }
        val card = json.decodeFromString(AnnotationCard.serializer(), """{"id":"c","x":0,"y":0,"width":100,"text":"old"}""")
        assertEquals(CardTextStyle(), card.textStyle)
        val image = json.decodeFromString(ImageElement.serializer(), """{"id":"i","src":"images/a.png","x":0,"y":0,"width":10,"height":10}""")
        assertEquals(PageImageKind.IMAGE, image.kind)
    }

    @Test
    fun `新图形和图案笔 proto 往返保留工具类型`() {
        val tools = listOf(InkTool.DASHED_LINE, InkTool.ARROW, InkTool.TRIANGLE, InkTool.STAR, InkTool.POLYGON, InkTool.DOODLE)
        val out = ByteArrayOutputStream()
        tools.forEach { tool ->
            ProtoInk.writeRecord(out, ProtoInk.recordOf(Stroke(tool.id, tool, 0, 1f, listOf(pt(0f, 0f), pt(10f, 10f)))))
        }
        val restored = ProtoInk.readPage(out.toByteArray().inputStream())
        assertEquals(tools, restored.map { it.tool })
    }

    @Test
    fun `扩展图形有真实几何轮廓`() {
        InkTool.entries.filter { it.isShape }.forEach { tool ->
            val stroke = Stroke("s", tool, 0, 1f, listOf(pt(10f, 10f), pt(100f, 80f)))
            val points = StrokeGeom.polyline(stroke)
            assertTrue("$tool 没有几何点", points.isNotEmpty())
            if (tool == InkTool.TRIANGLE || tool == InkTool.STAR || tool == InkTool.POLYGON) {
                assertEquals(points.first().x, points.last().x)
                assertEquals(points.first().y, points.last().y)
            }
        }
    }
}
