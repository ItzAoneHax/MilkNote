package com.milknote.ink.model

import com.milknote.ink.render.StrokeRenderer
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 工作区命中几何（Agent 元素时间查询用）：区域 = 页坐标轴对齐矩形，可退化为单点；
 * 判定复用真实渲染路径，而不是元素包围盒：
 *
 * - 普通笔迹：逐段折线 + [StrokeRenderer.radii] 的显示笔宽（压感 / 荧光 ×4 / 形状恒宽）；
 * - 形状：走 [StrokeGeom.polyline] 的**可见轮廓**（矩形/椭圆/三角形/星形只命中边线，空白内部不算）；
 * - 图片 / 媒体 / 卡片：旋转矩形真实足迹，卡片含展开高度与 `scale`（与 [ElementGeom] 一致）。
 *
 * 已知简化（与渲染的差异）：
 * - 虚线的间隙按连续线段处理（虚线间隔内可能多命中）；
 * - 图案笔按沿路径的连续圆带处理（相邻印章之间的小空隙可能多命中）；
 * - 笔宽变化段取两端半径的较大值（瞬时变宽处略宽松）；
 * - 32 段椭圆轮廓的弦高未补偿（宽笔触边界误差 < 1px）。
 */
object ElementHitGeom {

    /** 与 PageRenderer / InkSurfaceView 的 mm→px 基准一致 */
    const val RENDER_MM_TO_PX = 5.67f

    /** 小于该尺寸的区域视为单点（手指单击的退化矩形） */
    const val POINT_EPS = 1e-3f

    /** 图案笔印章覆盖半径 ≈ 基础半径 × 1.09（中心圆 + 五瓣） */
    private const val DOODLE_STAMP_SCALE = 1.1f

    /** 页坐标轴对齐区域；允许未归一化（工具直接传拖动起止点）。 */
    data class Area(val l: Float, val t: Float, val r: Float, val b: Float) {
        fun normalized(): Area =
            if (l <= r && t <= b) this
            else Area(minOf(l, r), minOf(t, b), maxOf(l, r), maxOf(t, b))
    }

    // ---------------- 笔迹 ----------------

    fun stroke(stroke: Stroke, area: Area, mmToPx: Float = RENDER_MM_TO_PX): Boolean {
        if (stroke.points.isEmpty()) return false
        val a = area.normalized()
        degenerateFilledShape(stroke, mmToPx)?.let { return it.intersects(a) }

        val poly = StrokeGeom.polyline(stroke)
        if (poly.isEmpty()) return false
        val radiusAt = radiusProvider(stroke, poly.size, mmToPx)
        val isPointArea = a.r - a.l <= POINT_EPS && a.b - a.t <= POINT_EPS
        if (isPointArea) return strokeHitsPoint(poly, radiusAt, a.l, a.t)

        for (p in poly) if (p.x in a.l..a.r && p.y in a.t..a.b) return true
        if (poly.size == 1) {
            val r = radiusAt(0)
            return pointToRectDistance(poly[0].x, poly[0].y, a) <= r
        }
        val corners = areaCorners(a)
        for (i in 1 until poly.size) {
            val p0 = poly[i - 1]
            val p1 = poly[i]
            val r0 = radiusAt(i - 1)
            val r1 = radiusAt(i)
            val r = maxOf(r0, r1)
            // 段端点未进入矩形、但端帽半径已覆盖边界（细长矩形 + 笔迹停在边界外一笔宽内）
            if (pointToRectDistance(p0.x, p0.y, a) <= r0) return true
            if (pointToRectDistance(p1.x, p1.y, a) <= r1) return true
            if (segmentIntersectsRect(p0.x, p0.y, p1.x, p1.y, a)) return true
            for (c in corners) {
                if (pointToSegmentDistance(c.x, c.y, p0.x, p0.y, p1.x, p1.y) <= r) return true
            }
        }
        return false
    }

    private fun strokeHitsPoint(poly: List<InkPoint>, radiusAt: (Int) -> Float, x: Float, y: Float): Boolean {
        if (poly.size == 1) return hypot(poly[0].x - x, poly[0].y - y) <= radiusAt(0)
        for (i in 1 until poly.size) {
            val r = maxOf(radiusAt(i - 1), radiusAt(i))
            if (pointToSegmentDistance(x, y, poly[i - 1].x, poly[i - 1].y, poly[i].x, poly[i].y) <= r) return true
        }
        return false
    }

    /** 形状轮廓统一恒宽；普通笔迹按渲染器的逐点显示半径；图案笔按印章覆盖半径。 */
    private fun radiusProvider(stroke: Stroke, polySize: Int, mmToPx: Float): (Int) -> Float {
        if (stroke.tool == InkTool.DOODLE) {
            val r = (stroke.baseWidthMm * mmToPx).coerceIn(2f, 18f) * DOODLE_STAMP_SCALE
            return { r }
        }
        if (stroke.tool.isShape) {
            val r = stroke.baseWidthMm * mmToPx / 2f
            return { r }
        }
        val radii = StrokeRenderer.radii(stroke, mmToPx, PressureGamma.STANDARD)
        return { i -> if (i in radii.indices) radii[i] else 0f }
    }

    /**
     * 渲染层的实心退化：矩形/椭圆小到内外框无法成环时整块填充
     * （[StrokeRenderer.buildShapePath] 同款阈值），此时内部也必须命中。
     */
    private fun degenerateFilledShape(stroke: Stroke, mmToPx: Float): FillRegion? {
        if (stroke.points.size < 2) return null
        if (stroke.tool != InkTool.RECT && stroke.tool != InkTool.OVAL) return null
        val a = stroke.points.first()
        val b = stroke.points.last()
        val l = minOf(a.x, b.x)
        val t = minOf(a.y, b.y)
        val r = maxOf(a.x, b.x)
        val bottom = maxOf(a.y, b.y)
        val inset = stroke.baseWidthMm * mmToPx
        if (r - l > inset + 0.8f && bottom - t > inset + 0.8f) return null
        return if (stroke.tool == InkTool.OVAL) {
            FillRegion.Ellipse((l + r) / 2f, (t + bottom) / 2f, (r - l) / 2f, (bottom - t) / 2f)
        } else {
            FillRegion.Box(l, t, r, bottom)
        }
    }

    private sealed interface FillRegion {
        fun intersects(area: Area): Boolean

        data class Box(val l: Float, val t: Float, val r: Float, val b: Float) : FillRegion {
            override fun intersects(area: Area): Boolean {
                val a = area.normalized()
                if (a.r - a.l <= POINT_EPS && a.b - a.t <= POINT_EPS) {
                    return a.l in l..r && a.t in t..b
                }
                return !(a.l > r || a.r < l || a.t > b || a.b < t)
            }
        }

        data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : FillRegion {
            override fun intersects(area: Area): Boolean {
                val a = area.normalized()
                if (rx <= 0f || ry <= 0f) {
                    return a.l <= cx && cx <= a.r && a.t <= cy && cy <= a.b
                }
                val minU = minOf((a.l - cx) / rx, (a.r - cx) / rx)
                val maxU = maxOf((a.l - cx) / rx, (a.r - cx) / rx)
                val minV = minOf((a.t - cy) / ry, (a.b - cy) / ry)
                val maxV = maxOf((a.t - cy) / ry, (a.b - cy) / ry)
                // 单位圆与矩形的最近点：原点钳制到矩形区间
                val u = 0f.coerceIn(minU, maxU)
                val v = 0f.coerceIn(minV, maxV)
                return u * u + v * v <= 1f
            }
        }
    }

    // ---------------- 图片 / 媒体 / 卡片 ----------------

    fun image(image: ImageElement, area: Area): Boolean =
        box(image.x, image.y, image.width, image.height, image.rotationDeg, area)

    fun media(media: MediaElement, area: Area): Boolean =
        box(media.x, media.y, media.width, media.height, media.rotationDeg, area)

    /**
     * @param baseHeightPx 未缩放内容高度（渲染层的 `AnnotationCardRenderer.cardHeight(card, expanded = true)`）；
     *   卡片足迹 = 该高度与宽度同乘 `card.scale`，再按中心旋转。
     */
    fun card(card: AnnotationCard, baseHeightPx: Float, area: Area): Boolean {
        if (card.scale <= 0f) return false
        return box(
            card.x, card.y,
            card.width * card.scale, baseHeightPx * card.scale,
            card.rotationDeg, area,
        )
    }

    fun box(x: Float, y: Float, width: Float, height: Float, rotationDeg: Float, area: Area): Boolean {
        if (width <= 0f || height <= 0f) return false
        val a = area.normalized()
        if (a.r - a.l <= POINT_EPS && a.b - a.t <= POINT_EPS) {
            return ElementGeom.contains(x, y, width, height, rotationDeg, a.l, a.t)
        }
        val corners = ElementGeom.rotatedCorners(x, y, width, height, rotationDeg)
        return !hasSeparatingAxis(corners, areaCorners(a))
    }

    // ---------------- 基础几何 ----------------

    private fun areaCorners(a: Area): List<ElementGeom.Point> = listOf(
        ElementGeom.Point(a.l, a.t),
        ElementGeom.Point(a.r, a.t),
        ElementGeom.Point(a.r, a.b),
        ElementGeom.Point(a.l, a.b),
    )

    /** 分离轴：任一方向投影不重叠即不相交（旋转矩形 vs 轴对齐矩形）。 */
    private fun hasSeparatingAxis(p: List<ElementGeom.Point>, q: List<ElementGeom.Point>): Boolean {
        for (poly in listOf(p, q)) {
            for (i in poly.indices) {
                val j = (i + 1) % poly.size
                val axisX = -(poly[j].y - poly[i].y)
                val axisY = poly[j].x - poly[i].x
                if (abs(axisX) < 1e-6f && abs(axisY) < 1e-6f) continue
                val pp = project(p, axisX, axisY)
                val qq = project(q, axisX, axisY)
                if (pp.second < qq.first || qq.second < pp.first) return true
            }
        }
        return false
    }

    private fun project(poly: List<ElementGeom.Point>, ax: Float, ay: Float): Pair<Float, Float> {
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (p in poly) {
            val v = p.x * ax + p.y * ay
            if (v < min) min = v
            if (v > max) max = v
        }
        return min to max
    }

    private fun segmentIntersectsRect(x1: Float, y1: Float, x2: Float, y2: Float, a: Area): Boolean {
        if (segmentsIntersect(x1, y1, x2, y2, a.l, a.t, a.r, a.t)) return true
        if (segmentsIntersect(x1, y1, x2, y2, a.r, a.t, a.r, a.b)) return true
        if (segmentsIntersect(x1, y1, x2, y2, a.r, a.b, a.l, a.b)) return true
        return segmentsIntersect(x1, y1, x2, y2, a.l, a.b, a.l, a.t)
    }

    private fun segmentsIntersect(
        x1: Float, y1: Float, x2: Float, y2: Float,
        x3: Float, y3: Float, x4: Float, y4: Float,
    ): Boolean {
        val d = (x2 - x1) * (y4 - y3) - (y2 - y1) * (x4 - x3)
        if (abs(d) < 1e-9f) return false
        val t = ((x3 - x1) * (y4 - y3) - (y3 - y1) * (x4 - x3)) / d
        val u = ((x3 - x1) * (y2 - y1) - (y3 - y1) * (x2 - x1)) / d
        return t in 0f..1f && u in 0f..1f
    }

    private fun pointToSegmentDistance(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-9f) return hypot(px - x1, py - y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        return hypot(px - (x1 + dx * t), py - (y1 + dy * t))
    }

    private fun pointToRectDistance(x: Float, y: Float, a: Area): Float {
        val dx = maxOf(a.l - x, 0f, x - a.r)
        val dy = maxOf(a.t - y, 0f, y - a.b)
        return hypot(dx, dy)
    }
}
