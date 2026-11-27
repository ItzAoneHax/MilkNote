package com.milknote.ink

import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.ElementHitGeom
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.Stroke
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工作区命中几何：区域 = 页坐标轴对齐矩形（可退化为点），元素按真实渲染轮廓判定。
 * 覆盖普通笔迹（真实笔宽/压感）、形状空心、单点圆点、荧光笔加宽、旋转图片/媒体/卡片。
 */
class ElementHitGeomTest {

    private val mmToPx = 5.67f

    private fun pt(x: Float, y: Float, pressure: Float = 0.5f, timeMs: Long = 0L) =
        InkPoint(x, y, pressure, 0f, 0f, timeMs)

    private fun stroke(
        id: String,
        tool: InkTool = InkTool.PEN,
        widthMm: Float = 0.7f,
        vararg xy: Pair<Float, Float>,
    ) = Stroke(
        id = id,
        tool = tool,
        color = 0xFF23221E,
        baseWidthMm = widthMm,
        points = xy.map { pt(it.first, it.second) },
    )

    private fun area(l: Float, t: Float, r: Float, b: Float) = ElementHitGeom.Area(l, t, r, b)

    private fun point(x: Float, y: Float) = area(x, y, x, y)

    private fun hit(s: Stroke, a: ElementHitGeom.Area) =
        ElementHitGeom.stroke(s, a, mmToPx)

    // ---------------- 普通笔迹：真实路径 + 笔宽 ----------------

    @Test
    fun `斜线笔迹 - 路径上的点命中而包围盒空白处不命中`() {
        val diagonal = stroke("pen", xy = *arrayOf(0f to 0f, 100f to 100f))
        assertTrue(hit(diagonal, point(50f, 50f)))
        assertTrue(hit(diagonal, point(50f, 51f)))       // 半个笔宽以内
        assertFalse(hit(diagonal, point(50f, 20f)))      // AABB 内，但离真实路径 > 20px
        assertFalse(hit(diagonal, point(-5f, 50f)))
    }

    @Test
    fun `折线笔迹 - 只命中拐角附近的真实路径`() {
        val corner = stroke("corner", xy = *arrayOf(0f to 0f, 100f to 0f, 100f to 100f))
        assertTrue(hit(corner, point(100f, 50f)))        // 竖边
        assertTrue(hit(corner, point(50f, 0f)))          // 横边
        assertFalse(hit(corner, point(50f, 50f)))        // 折线内空白
        assertFalse(hit(corner, point(20f, 80f)))
    }

    @Test
    fun `单点笔迹 - 以半径圆点判定`() {
        val dot = stroke("dot", xy = *arrayOf(50f to 50f))
        assertTrue(hit(dot, point(50f, 50f)))
        assertTrue(hit(dot, point(51.5f, 50f)))          // 半径 ≈ 1.98px
        assertFalse(hit(dot, point(60f, 50f)))
    }

    @Test
    fun `荧光笔 - 加宽后的点仍命中`() {
        val highlighter = stroke("hl", tool = InkTool.HIGHLIGHTER, xy = *arrayOf(0f to 50f, 100f to 50f))
        // 荧光笔半径 = 0.7mm × 4 × 5.67 / 2 ≈ 7.94px
        assertTrue(hit(highlighter, point(50f, 57f)))
        assertFalse(hit(highlighter, point(50f, 60f)))
    }

    // ---------------- 矩形区域：归一化、边界、反向拖动 ----------------

    @Test
    fun `反向矩形 - 工具未 normalize 也按真实区域命中`() {
        val line = stroke("line", xy = *arrayOf(0f to 50f, 100f to 50f))
        assertTrue(hit(line, area(100f, 60f, 0f, 40f)))
        assertFalse(hit(line, area(100f, 40f, 0f, 44f)))  // 距路径 6px > 半径
    }

    @Test
    fun `矩形贴边 - 边界接触与刚好越界`() {
        val line = stroke("line", xy = *arrayOf(0f to 50f, 100f to 50f))
        assertTrue(hit(line, area(20f, 48f, 40f, 48.5f)))  // 边界在笔宽内
        assertFalse(hit(line, area(20f, 53f, 40f, 60f)))
        assertTrue(hit(line, area(20f, 51.5f, 40f, 60f)))  // 边界距路径 1.5px，仍在半径内
    }

    @Test
    fun `大区域 - 完全包住笔迹`() {
        val line = stroke("line", xy = *arrayOf(10f to 10f, 90f to 90f))
        assertTrue(hit(line, area(0f, 0f, 100f, 100f)))
        assertFalse(hit(line, area(200f, 200f, 300f, 300f)))
    }

    // ---------------- 形状：命中轮廓而非空白内部 ----------------

    @Test
    fun `矩形形状 - 内部空白不命中 边缘命中`() {
        val rect = stroke("r", tool = InkTool.RECT, widthMm = 2f, xy = *arrayOf(100f to 100f, 300f to 300f))
        assertFalse(hit(rect, point(200f, 200f)))
        assertFalse(hit(rect, area(180f, 180f, 220f, 220f)))
        assertTrue(hit(rect, point(200f, 100f)))
        assertTrue(hit(rect, area(150f, 95f, 160f, 105f)))   // 区域跨上边缘
        assertTrue(hit(rect, point(102f, 102f)))              // 角点
    }

    @Test
    fun `椭圆形状 - 圆心空白不命中 圆周命中`() {
        val oval = stroke("o", tool = InkTool.OVAL, widthMm = 2f, xy = *arrayOf(100f to 100f, 300f to 200f))
        assertFalse(hit(oval, point(200f, 150f)))
        assertTrue(hit(oval, point(200f, 100f)))
        assertTrue(hit(oval, point(100f, 150f)))
        assertFalse(hit(oval, area(190f, 140f, 210f, 160f)))
    }

    @Test
    fun `三角形与星形 - 仅轮廓命中`() {
        val triangle = stroke("t", tool = InkTool.TRIANGLE, widthMm = 2f, xy = *arrayOf(100f to 300f, 300f to 100f))
        assertFalse(hit(triangle, point(200f, 220f)))       // 三角形内部但不在边附近
        assertTrue(hit(triangle, point(200f, 300f)))        // 底边
        val star = stroke("s", tool = InkTool.STAR, widthMm = 2f, xy = *arrayOf(100f to 100f, 300f to 300f))
        assertFalse(hit(star, point(200f, 200f)))
        assertTrue(hit(star, point(200f, 100f)))            // 顶部尖点
    }

    @Test
    fun `微小矩形 - 渲染退化为实心后内部也命中`() {
        val tiny = stroke("tiny", tool = InkTool.RECT, widthMm = 4f, xy = *arrayOf(100f to 100f, 102f to 102f))
        assertTrue(hit(tiny, point(101f, 101f)))
    }

    @Test
    fun `细长矩形 - 端点圆帽越过边界即命中`() {
        // 竖长条 [100..120]×[0..200]；笔迹中轴线在 x≤99.5，端点自身已越左边界(半径≈1.98px)
        val thin = area(100f, 0f, 120f, 200f)
        val touch = stroke("touch", xy = *arrayOf(80f to 100f, 99.5f to 100f))
        assertTrue(hit(touch, thin))

        // 端点到矩形距离 3px > 笔宽半径：中轴线、四角都不相交，应判不命中
        val apart = stroke("apart", xy = *arrayOf(80f to 100f, 97f to 100f))
        assertFalse(hit(apart, thin))
    }

    @Test
    fun `细长矩形 - 端点圆帽双向与拐点都按笔宽判定`() {
        // 横长条 [0..200]×[100..120]，笔迹从上方接近；端点半径内应命中
        val thin = area(0f, 100f, 200f, 120f)
        val fromTop = stroke("top", xy = *arrayOf(100f to 80f, 100f to 98.5f))
        assertTrue(hit(fromTop, thin))
        assertFalse(hit(stroke("top-far", xy = *arrayOf(100f to 80f, 100f to 96f)), thin))

        // 折线拐点位于边界外一笔宽内：拐点圆帽同样应命中
        val joint = stroke("joint", xy = *arrayOf(50f to 98.5f, 99f to 98.5f, 99f to 60f))
        assertTrue(hit(joint, thin))
    }

    // ---------------- 图片 / 媒体 / 卡片：旋转足迹 ----------------

    @Test
    fun `旋转图片 - 包围盒角落不误判 真实长轴上命中`() {
        val image = ImageElement(
            id = "i", src = "images/a.png",
            x = 0f, y = 80f, width = 200f, height = 40f, rotationDeg = 45f,
        )
        val rotated = ElementHitGeom.image(image, point(100f + 60f, 100f + 60f))
        assertTrue(rotated)                                  // 长轴方向
        assertFalse(ElementHitGeom.image(image, point(100f + 80f, 100f + 80f)))  // AABB 角落但在旋转矩形外
        assertFalse(ElementHitGeom.image(image, point(100f + 80f, 100f - 80f)))
    }

    @Test
    fun `旋转媒体的区域相交 - 小矩形只与真实足迹重叠`() {
        val media = MediaElement(
            id = "m", x = 0f, y = 80f, width = 200f, height = 40f, rotationDeg = 45f,
        )
        assertTrue(ElementHitGeom.media(media, area(155f, 155f, 165f, 165f)))
        assertFalse(ElementHitGeom.media(media, area(175f, 175f, 185f, 185f)))
    }

    @Test
    fun `卡片 - 展开高度与 scale 参与足迹 旋转后仍按真实角落判定`() {
        val card = AnnotationCard(
            id = "c", x = 0f, y = 0f, width = 100f, text = "t",
            scale = 2f, rotationDeg = 0f,
        )
        // baseHeight = 50 → 显示 200×100；未缩放时 (150,50) 在卡外
        assertTrue(ElementHitGeom.card(card, baseHeightPx = 50f, area = point(150f, 50f)))
        assertFalse(ElementHitGeom.card(card, baseHeightPx = 50f, area = point(250f, 50f)))

        val rotated = card.copy(rotationDeg = 90f)
        // 旋转 90° 后 200×100 的中心 (100,50) 不变；原 (150,50) → (100,100)
        assertTrue(ElementHitGeom.card(rotated, baseHeightPx = 50f, area = point(100f, 100f)))
        assertFalse(ElementHitGeom.card(rotated, baseHeightPx = 50f, area = point(100f, 160f)))
    }

    @Test
    fun `空尺寸元素不命中`() {
        val empty = ImageElement(id = "e", src = "", x = 0f, y = 0f, width = 0f, height = 0f)
        assertFalse(ElementHitGeom.image(empty, point(0f, 0f)))
    }
}
