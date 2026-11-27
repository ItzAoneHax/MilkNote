package com.milknote.app.agent

import com.milknote.agent.ElementTimestamp
import com.milknote.agent.Rect4
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.Stroke
import com.milknote.ink.render.AnnotationCardRenderer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 工作区元素时间查询（任务 C）：点在/矩形与页面元素相交 → 返回时间。
 * 判定顺序须与渲染一致但**从顶层至底层**（`orderedElements` 的反序），
 * 被上层遮挡的元素同样返回；可选历史笔迹追加在现态之后并标记 erased。
 */
class WorkspaceElementTimestampsTest {

    @Before
    fun installCardMeasurement() {
        // 单元测试没有 Android StaticLayout：用富卡片测量钩子固定展开高度（100 + 上下内边距）
        AnnotationCardRenderer.richCardRenderer = object : AnnotationCardRenderer.RichCardRenderer {
            override fun measureHeight(card: AnnotationCard, contentWidthPx: Float, expanded: Boolean): Float? = 100f
            override fun drawContent(
                canvas: android.graphics.Canvas,
                card: AnnotationCard,
                contentWidthPx: Float,
                dark: Boolean,
                expanded: Boolean,
            ): Boolean = false
        }
    }

    @After
    fun restoreCardMeasurement() {
        AnnotationCardRenderer.richCardRenderer = null
    }

    private fun pt(x: Float, y: Float, timeMs: Long) = InkPoint(x, y, 0.5f, 0f, 0f, timeMs)

    private fun stroke(
        id: String,
        vararg xy: Pair<Float, Float>,
        tool: InkTool = InkTool.PEN,
        widthMm: Float = 0.7f,
        zIndex: Long = 0L,
        createdAt: Long = 1_000L,
        times: List<Long> = emptyList(),
    ) = Stroke(
        id = id,
        tool = tool,
        color = 0xFF23221E,
        baseWidthMm = widthMm,
        points = xy.mapIndexed { i, p -> pt(p.first, p.second, times.getOrElse(i) { i * 10L }) },
        zIndex = zIndex,
        createdAt = createdAt,
    )

    private fun card(
        id: String,
        x: Float,
        y: Float,
        width: Float = 100f,
        zIndex: Long = 0L,
        createdAt: Long = 2_000L,
        scale: Float = 1f,
        rotationDeg: Float = 0f,
    ) = AnnotationCard(
        id = id,
        x = x,
        y = y,
        width = width,
        kind = AnnotationCard.Kind.MARKDOWN,
        text = "# 卡",
        createdAt = createdAt,
        rotationDeg = rotationDeg,
        scale = scale,
        zIndex = zIndex,
    )

    private fun image(id: String, x: Float, y: Float, zIndex: Long = 0L, createdAt: Long = 3_000L) =
        ImageElement(id = id, src = "images/a.png", x = x, y = y, width = 200f, height = 200f, zIndex = zIndex, createdAt = createdAt)

    private fun media(id: String, x: Float, y: Float, zIndex: Long = 0L, createdAt: Long = 4_000L) =
        MediaElement(id = id, kind = com.milknote.ink.model.MediaKind.AUDIO, src = "media/a.m4a", x = x, y = y, width = 200f, height = 120f, zIndex = zIndex, createdAt = createdAt)

    private fun query(
        strokes: List<Stroke> = emptyList(),
        cards: List<AnnotationCard> = emptyList(),
        images: List<ImageElement> = emptyList(),
        media: List<MediaElement> = emptyList(),
        rect: Rect4,
        erased: List<Stroke> = emptyList(),
    ): List<ElementTimestamp> = WorkspaceElementTimestamps.of(strokes, cards, images, media, rect, erased)

    private fun List<ElementTimestamp>.ids() = map { it.id }

    // ---------------- 叠层与顺序 ----------------

    @Test
    fun `单点查询 - 顶层至底层返回全部相交元素含被遮挡者`() {
        val under = stroke("stroke", 0f to 100f, 200f to 100f, zIndex = 0L, createdAt = 1_000L, times = listOf(0L, 120L))
        val mid = card("card", x = 100f, y = 40f, zIndex = 10L, createdAt = 2_000L)
        val top = image("image", x = 100f, y = 0f, zIndex = 20L, createdAt = 3_000L)

        val result = query(listOf(under), listOf(mid), listOf(top), rect = Rect4(160f, 100f, 160f, 100f))

        assertEquals(listOf("image", "card", "stroke"), result.ids())
        assertEquals(listOf("image", "card", "stroke"), result.map { it.kind })
        assertEquals(listOf(3_000L, 2_000L, 1_000L), result.map { it.createdAtMs })
        assertEquals(120L, result.first { it.id == "stroke" }.durationMs)
        assertTrue(result.none { it.erased })
    }

    @Test
    fun `同 zIndex - 按 legacy 层带反序 卡片在顶层`() {
        val s = stroke("stroke", 0f to 100f, 200f to 100f)
        val c = card("card", x = 100f, y = 20f)
        val i = image("image", x = 50f, y = 0f)
        val m = media("media", x = 50f, y = 50f)

        val result = query(listOf(s), listOf(c), listOf(i), listOf(m), rect = Rect4(160f, 100f, 160f, 100f))

        // 渲染顺序(底→顶) = image, media, stroke, card；查询为反序
        assertEquals(listOf("card", "stroke", "media", "image"), result.ids())
    }

    @Test
    fun `矩形区域 - 只返回相交元素`() {
        val inside = stroke("inside", 20f to 20f, 80f to 80f, createdAt = 10L)
        val crossing = stroke("crossing", 0f to 50f, 60f to 50f, createdAt = 20L)
        val outside = stroke("outside", 400f to 400f, 500f to 500f, createdAt = 30L)

        val result = query(listOf(outside, inside, crossing), rect = Rect4(10f, 10f, 70f, 70f))

        assertEquals(listOf("crossing", "inside"), result.ids())
    }

    // ---------------- 点 / 反向矩形 / 边缘 ----------------

    @Test
    fun `反向矩形 - 未 normalize 的起止点与正向结果一致`() {
        val line = stroke("line", 0f to 50f, 100f to 50f)

        val forward = query(listOf(line), rect = Rect4(0f, 40f, 100f, 60f))
        val reversed = query(listOf(line), rect = Rect4(100f, 60f, 0f, 40f))

        assertEquals(forward.ids(), reversed.ids())
        assertEquals(listOf("line"), reversed.ids())
    }

    @Test
    fun `矩形边缘 - 贴边命中而刚好越界不命中`() {
        val line = stroke("line", 0f to 50f, 100f to 50f)

        assertTrue(query(listOf(line), rect = Rect4(20f, 48f, 40f, 49f)).isNotEmpty())
        assertTrue(query(listOf(line), rect = Rect4(20f, 51.5f, 40f, 60f)).isNotEmpty())
        assertTrue(query(listOf(line), rect = Rect4(20f, 53f, 40f, 60f)).isEmpty())
    }

    // ---------------- 几何真实轮廓 ----------------

    @Test
    fun `形状 - 空心内部不返回 轮廓点返回`() {
        val rectShape = stroke("rect", 100f to 100f, 300f to 300f, tool = InkTool.RECT, widthMm = 2f)

        assertTrue(query(listOf(rectShape), rect = Rect4(200f, 200f, 200f, 200f)).isEmpty())
        assertEquals(listOf("rect"), query(listOf(rectShape), rect = Rect4(200f, 100f, 200f, 100f)).ids())
        assertEquals(listOf("rect"), query(listOf(rectShape), rect = Rect4(102f, 102f, 102f, 102f)).ids())
    }

    @Test
    fun `旋转图片 - 包围盒角落不误判`() {
        val rotated = ImageElement(
            id = "img", src = "images/a.png",
            x = 0f, y = 80f, width = 200f, height = 40f, rotationDeg = 45f,
        )
        assertTrue(query(images = listOf(rotated), rect = Rect4(160f, 160f, 160f, 160f)).isNotEmpty())
        assertTrue(query(images = listOf(rotated), rect = Rect4(180f, 180f, 180f, 180f)).isEmpty())
    }

    @Test
    fun `卡片 - 展开高度与缩放参与命中`() {
        // 钩子高度 100 + 上下内边距 44 = 144；scale=2 → 足迹 (0,0)-(200,288)
        val c = card("card", x = 0f, y = 0f, width = 100f, scale = 2f)
        assertTrue(query(cards = listOf(c), rect = Rect4(150f, 200f, 150f, 200f)).isNotEmpty())
        assertTrue(query(cards = listOf(c), rect = Rect4(250f, 200f, 250f, 200f)).isEmpty())
    }

    // ---------------- 时间与旧数据 ----------------

    @Test
    fun `持续时长 - 首尾采样差值 单点无时长`() {
        val three = stroke("three", 0f to 50f, 50f to 50f, 100f to 50f, createdAt = 9L, times = listOf(10L, 30L, 90L))
        val one = stroke("one", 50f to 50f, createdAt = 8L, times = listOf(10L))
        val zeroSpan = stroke("zero", 0f to 50f, 100f to 50f, createdAt = 7L, times = listOf(5L, 5L))

        val result = query(listOf(three, one, zeroSpan), rect = Rect4(0f, 0f, 100f, 100f))
        val byId = result.associateBy { it.id }

        assertEquals(80L, byId.getValue("three").durationMs)
        assertNull(byId.getValue("one").durationMs)
        assertEquals(0L, byId.getValue("zero").durationMs)
    }

    @Test
    fun `旧数据 createdAt 为 0 - 返回 null 而非 1970`() {
        val s = stroke("s", 0f to 50f, 100f to 50f, createdAt = 0L)
        val c = card("c", x = 0f, y = 0f, createdAt = 0L)
        val i = image("i", x = 0f, y = 0f, createdAt = 0L)
        val m = media("m", x = 0f, y = 0f, createdAt = 0L)

        val result = query(listOf(s), listOf(c), listOf(i), listOf(m), rect = Rect4(10f, 10f, 90f, 90f))

        assertEquals(4, result.size)
        assertTrue(result.all { it.createdAtMs == null })
        assertTrue(result.none { it.erased })
    }

    @Test
    fun `新旧笔迹混合 - 旧的 createdAt 为 null 新的保留且时长独立`() {
        val newer = stroke("new", 0f to 50f, 100f to 50f, createdAt = 1_700_000_000_000L, times = listOf(0L, 40L))
        val legacy = stroke("old", 0f to 60f, 100f to 60f, createdAt = 0L, times = listOf(5L, 15L))

        val result = query(listOf(newer, legacy), rect = Rect4(0f, 0f, 100f, 100f))
        val byId = result.associateBy { it.id }

        assertEquals(1_700_000_000_000L, byId.getValue("new").createdAtMs)
        assertEquals(40L, byId.getValue("new").durationMs)
        assertNull(byId.getValue("old").createdAtMs)
        assertEquals(10L, byId.getValue("old").durationMs)
    }

    @Test
    fun `非笔迹元素 - duration 为 null`() {
        val c = card("c", x = 0f, y = 0f)
        val i = image("i", x = 0f, y = 0f)
        val m = media("m", x = 0f, y = 0f)

        val result = query(cards = listOf(c), images = listOf(i), media = listOf(m), rect = Rect4(10f, 10f, 90f, 90f))

        assertEquals(3, result.size)
        assertTrue(result.all { it.durationMs == null })
    }

    // ---------------- 历史笔迹 ----------------

    @Test
    fun `历史笔迹 - 追加在现态之后并标记 erased`() {
        val live = stroke("live", 0f to 50f, 100f to 50f, zIndex = 0L, createdAt = 100L, times = listOf(0L, 40L))
        val erasedTop = stroke("gone-top", 0f to 51f, 100f to 51f, zIndex = 5L, createdAt = 50L, times = listOf(0L, 30L))
        val erasedUnder = stroke("gone-under", 0f to 49f, 100f to 49f, zIndex = 1L, createdAt = 40L)

        val result = query(listOf(live), rect = Rect4(50f, 50f, 50f, 50f), erased = listOf(erasedUnder, erasedTop))

        assertEquals(listOf("live", "gone-top", "gone-under"), result.ids())
        assertFalse(result[0].erased)
        assertTrue(result[1].erased)
        assertTrue(result[2].erased)
        assertEquals(50L, result[1].createdAtMs)
        assertEquals(30L, result[1].durationMs)
    }

    @Test
    fun `历史笔迹 - 不与区域相交的不返回`() {
        val near = stroke("near", 0f to 50f, 100f to 50f, createdAt = 50L)
        val far = stroke("far", 500f to 500f, 600f to 600f, createdAt = 40L)

        val result = query(strokes = emptyList(), rect = Rect4(50f, 50f, 50f, 50f), erased = listOf(far, near))

        assertEquals(listOf("near"), result.ids())
        assertTrue(result.single().erased)
    }

    @Test
    fun `空页面 - 返回空列表`() {
        assertTrue(query(rect = Rect4(0f, 0f, 0f, 0f)).isEmpty())
        assertTrue(query(rect = Rect4(0f, 0f, 100f, 100f)).isEmpty())
    }
}
