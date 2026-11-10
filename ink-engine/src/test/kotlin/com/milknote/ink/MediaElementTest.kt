package com.milknote.ink

import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkOp
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.MediaKind
import com.milknote.ink.model.PageInkState
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.nextZIndex
import com.milknote.ink.model.orderedElements
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 媒体模型兼容性 + 全局层序:
 * - 旧/最小 JSON 缺省新字段可直接解码(默认 AUDIO / 0);
 * - MediaElement 参与 orderedElements 的 zIndex → legacyRank → 原列表位置排序;
 * - nextZIndex 把媒体层一并计入。
 */
class MediaElementTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private fun stroke(id: String) = Stroke(
        id = id, tool = InkTool.PEN, color = 0xFF23221E, baseWidthMm = 0.7f,
        points = listOf(InkPoint(0f, 0f, 0.5f, 0f, 0f, 1L)),
    )

    private fun card(id: String) = com.milknote.ink.model.AnnotationCard(
        id = id, x = 0f, y = 0f, width = 100f, text = "t",
    )

    private fun image(id: String) = ImageElement(
        id = id, src = "images/a.png", x = 0f, y = 0f, width = 10f, height = 10f,
    )

    private fun media(id: String, zIndex: Long = 0L) = MediaElement(
        id = id, kind = MediaKind.VIDEO, src = "media/a.mp4",
        x = 1f, y = 2f, width = 30f, height = 20f, durationMs = 1234L, zIndex = zIndex,
    )

    @Test fun `最小 JSON 缺省字段回退为音频与零值`() {
        val decoded = json.decodeFromString<MediaElement>("""{"id":"m1","src":"media/a.m4a"}""")
        assertEquals("m1", decoded.id)
        assertEquals(MediaKind.AUDIO, decoded.kind)
        assertEquals("media/a.m4a", decoded.src)
        assertEquals(0f, decoded.x, 0f)
        assertEquals(0f, decoded.y, 0f)
        assertEquals(0f, decoded.width, 0f)
        assertEquals(0f, decoded.height, 0f)
        assertEquals(0L, decoded.durationMs)
        assertEquals(0f, decoded.rotationDeg, 0f)
        assertEquals(0L, decoded.zIndex)
    }

    @Test fun `未知字段被忽略且视频字段完整往返`() {
        val original = media("v1", zIndex = 7L).copy(rotationDeg = 30f, createdAt = 99L)
        val text = json.encodeToString(original)
        val decoded = json.decodeFromString<MediaElement>(text)
        assertEquals(original, decoded)

        val withFuture = json.decodeFromString<MediaElement>(
            """{"id":"v1","kind":"VIDEO","src":"media/a.mp4","futureField":true}""",
        )
        assertEquals(MediaKind.VIDEO, withFuture.kind)
    }

    @Test fun `媒体与旧元素按 legacy 层带排序`() {
        val ordered = orderedElements(
            strokes = listOf(stroke("s")),
            cards = listOf(card("c")),
            images = listOf(image("i")),
            media = listOf(media("m")),
        )
        assertEquals(listOf("i", "m", "s", "c"), ordered.map { it.ref.id })
    }

    @Test fun `显式 zIndex 优先于 legacy 层带`() {
        val ordered = orderedElements(
            strokes = listOf(stroke("s")),
            cards = listOf(card("c")),
            images = listOf(image("i")),
            media = listOf(media("m", zIndex = 3L)),
        )
        assertEquals(listOf("i", "s", "c", "m"), ordered.map { it.ref.id })
    }

    @Test fun `nextZIndex 计入媒体层`() {
        val page = PageInkState()
        page.apply(InkOp.AddMedia(media("m").copy(zIndex = 7L)))
        assertEquals(8L, page.nextZIndex())
        assertTrue(page.media.single().zIndex == 7L)
    }
}
