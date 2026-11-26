package com.milknote.ink

import com.milknote.ink.input.InkController
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.ElementRef
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.MediaKind
import com.milknote.ink.model.Stroke
import com.milknote.ink.render.AnnotationCardRenderer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Agent 工作区端口用到的程序化元素操作(按 id 移动/删除)与富卡片渲染钩子。 */
class ElementProgrammaticOpsTest {

    private fun pt(x: Float, y: Float) = InkPoint(x, y, 0.5f, 0f, 0f, 0L)

    private fun stroke(id: String = "s1") = Stroke(
        id, InkTool.PEN, 0xFF23221E, 0.7f, listOf(pt(20f, 30f), pt(60f, 70f)),
    )

    private fun image(id: String = "i1") = ImageElement(
        id = id, src = "images/test.png", x = 100f, y = 120f, width = 40f, height = 30f,
    )

    private fun mediaElement(id: String = "m1") = MediaElement(
        id = id, kind = MediaKind.VIDEO, src = "media/v.mp4", x = 200f, y = 220f, width = 80f, height = 60f,
    )

    private fun card(id: String = "c1", kind: AnnotationCard.Kind = AnnotationCard.Kind.TEXT) = AnnotationCard(
        id = id, x = 300f, y = 320f, width = 200f, kind = kind, text = "# 标题\n正文",
    )

    private fun controller(): InkController = InkController().apply {
        cardHeightProvider = { 40f }
        loadStrokes(listOf(stroke()), listOf(card()), listOf(image()), listOf(mediaElement()))
    }

    @After
    fun tearDown() {
        AnnotationCardRenderer.richCardRenderer = null
    }

    @Test
    fun `卡片按绝对坐标移动并可撤销重做`() {
        val c = controller()
        assertTrue(c.moveElementById(ElementRef.Kind.CARD, "c1", targetX = 10f, targetY = 20f, dx = null, dy = null))
        assertEquals(10f, c.page.cards.single().x, 0.001f)
        assertEquals(20f, c.page.cards.single().y, 0.001f)
        assertEquals("c1", c.page.cards.single().id)

        c.undo()
        assertEquals(300f, c.page.cards.single().x, 0.001f)
        assertEquals(320f, c.page.cards.single().y, 0.001f)

        c.redo()
        assertEquals(10f, c.page.cards.single().x, 0.001f)
        assertEquals(20f, c.page.cards.single().y, 0.001f)
    }

    @Test
    fun `图片按相对偏移移动且未命中返回 false`() {
        val c = controller()
        assertTrue(c.moveElementById(ElementRef.Kind.IMAGE, "i1", targetX = null, targetY = null, dx = -30f, dy = 5f))
        assertEquals(70f, c.page.images.single().x, 0.001f)
        assertEquals(125f, c.page.images.single().y, 0.001f)
        c.undo()
        assertEquals(100f, c.page.images.single().x, 0.001f)
        assertEquals(120f, c.page.images.single().y, 0.001f)

        assertFalse(c.moveElementById(ElementRef.Kind.IMAGE, "missing", 0f, 0f, null, null))
        assertFalse(c.moveElementById(ElementRef.Kind.IMAGE, "i1", targetX = null, targetY = null, dx = null, dy = null))
    }

    @Test
    fun `媒体元素在 kind 缺省时按 id 自动识别`() {
        val c = controller()
        assertTrue(c.moveElementById(null, "m1", targetX = 5f, targetY = null, dx = null, dy = 7f))
        assertEquals(5f, c.page.media.single().x, 0.001f)
        assertEquals(227f, c.page.media.single().y, 0.001f)
        assertEquals("m1", c.page.media.single().id)
    }

    @Test
    fun `笔迹绝对移动以包围盒左上角对齐且可撤销`() {
        val c = controller()
        assertTrue(c.moveElementById(null, "s1", targetX = 0f, targetY = 0f, dx = null, dy = null))
        val moved = c.page.strokes.single()
        assertEquals(0f, moved.points.first().x, 0.001f)
        assertEquals(0f, moved.points.first().y, 0.001f)
        assertEquals("s1", moved.id)
        assertEquals(2, moved.points.size)

        c.undo()
        assertEquals(20f, c.page.strokes.single().points.first().x, 0.001f)
        assertEquals(30f, c.page.strokes.single().points.first().y, 0.001f)

        c.redo()
        assertEquals(0f, c.page.strokes.single().points.first().x, 0.001f)
    }

    @Test
    fun `笔迹相对移动平移全部采样点`() {
        val c = controller()
        assertTrue(c.moveElementById(ElementRef.Kind.STROKE, "s1", null, null, dx = 10f, dy = -10f))
        val points = c.page.strokes.single().points
        assertEquals(30f, points[0].x, 0.001f)
        assertEquals(20f, points[0].y, 0.001f)
        assertEquals(70f, points[1].x, 0.001f)
        assertEquals(60f, points[1].y, 0.001f)
    }

    @Test
    fun `删除四类元素均可撤销重做`() {
        val c = controller()
        assertTrue(c.deleteElementById(ElementRef.Kind.CARD, "c1"))
        assertTrue(c.deleteElementById(null, "i1"))
        assertTrue(c.deleteElementById(ElementRef.Kind.MEDIA, "m1"))
        assertTrue(c.deleteElementById(null, "s1"))
        assertTrue(c.page.cards.isEmpty())
        assertTrue(c.page.images.isEmpty())
        assertTrue(c.page.media.isEmpty())
        assertTrue(c.page.strokes.isEmpty())

        repeat(4) { c.undo() }
        assertEquals(1, c.page.cards.size)
        assertEquals(1, c.page.images.size)
        assertEquals(1, c.page.media.size)
        assertEquals(1, c.page.strokes.size)

        repeat(4) { c.redo() }
        assertTrue(c.page.cards.isEmpty())
        assertTrue(c.page.strokes.isEmpty())

        assertFalse(c.deleteElementById(ElementRef.Kind.CARD, "missing"))
    }

    @Test
    fun `MARKDOWN 卡片 JSON 往返与旧数据兼容`() {
        val encoded = Json.encodeToString(AnnotationCard.serializer(), card(kind = AnnotationCard.Kind.MARKDOWN))
        assertTrue(encoded.contains("\"MARKDOWN\""))

        val decoded = Json.decodeFromString(AnnotationCard.serializer(), encoded)
        assertEquals(AnnotationCard.Kind.MARKDOWN, decoded.kind)

        val legacy = """{"id":"c9","x":1.0,"y":2.0,"width":120.0,"text":"旧卡片"}"""
        assertEquals(AnnotationCard.Kind.TEXT, Json.decodeFromString(AnnotationCard.serializer(), legacy).kind)
    }

    @Test
    fun `富渲染钩子只接管 MARKDOWN 与 LATEX`() {
        var measuredKind: AnnotationCard.Kind? = null
        var measuredWidth = 0f
        AnnotationCardRenderer.richCardRenderer = object : AnnotationCardRenderer.RichCardRenderer {
            override fun measureHeight(card: AnnotationCard, contentWidthPx: Float, expanded: Boolean): Float? {
                measuredKind = card.kind
                measuredWidth = contentWidthPx
                return 250f
            }

            override fun drawContent(
                canvas: android.graphics.Canvas,
                card: AnnotationCard,
                contentWidthPx: Float,
                dark: Boolean,
                expanded: Boolean,
            ): Boolean = true
        }

        val markdown = card("c1", AnnotationCard.Kind.MARKDOWN)
        assertEquals(250f, AnnotationCardRenderer.richMeasurement(markdown, expanded = true)!!, 0.001f)
        assertEquals(AnnotationCard.Kind.MARKDOWN, measuredKind)
        assertEquals(AnnotationCardRenderer.contentWidthPx(markdown), measuredWidth, 0.001f)

        assertEquals(250f, AnnotationCardRenderer.richMeasurement(card("c2", AnnotationCard.Kind.LATEX), false)!!, 0.001f)
        assertEquals(AnnotationCard.Kind.LATEX, measuredKind)

        // TEXT 不参与富渲染:即使注册了钩子也不查询(纯 JVM 下不触发 StaticLayout 兜底)
        assertNull(AnnotationCardRenderer.richMeasurement(card("c3", AnnotationCard.Kind.TEXT), true))
        assertEquals(AnnotationCard.Kind.LATEX, measuredKind)
    }

    @Test
    fun `富渲染钩子返回 null 时回退内置排版`() {
        AnnotationCardRenderer.richCardRenderer = object : AnnotationCardRenderer.RichCardRenderer {
            override fun measureHeight(card: AnnotationCard, contentWidthPx: Float, expanded: Boolean): Float? = null

            override fun drawContent(
                canvas: android.graphics.Canvas,
                card: AnnotationCard,
                contentWidthPx: Float,
                dark: Boolean,
                expanded: Boolean,
            ): Boolean = false
        }
        assertNull(AnnotationCardRenderer.richMeasurement(card(kind = AnnotationCard.Kind.MARKDOWN), true))
        assertNull(AnnotationCardRenderer.richMeasurement(card(kind = AnnotationCard.Kind.LATEX), false))
    }

    @Test
    fun `元素类型识别按卡片图片媒体笔迹顺序`() {
        val c = controller()
        assertEquals(ElementRef.Kind.CARD, c.elementKindOf("c1"))
        assertEquals(ElementRef.Kind.IMAGE, c.elementKindOf("i1"))
        assertEquals(ElementRef.Kind.MEDIA, c.elementKindOf("m1"))
        assertEquals(ElementRef.Kind.STROKE, c.elementKindOf("s1"))
        assertNull(c.elementKindOf("nope"))
    }
}
