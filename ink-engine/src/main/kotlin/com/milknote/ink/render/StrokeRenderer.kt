package com.milknote.ink.render

import android.graphics.Path
import android.graphics.RectF
import com.milknote.ink.input.DisplayWidthFilter
import com.milknote.ink.input.PressureCurve
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.PressureGamma
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.isShape
import kotlin.math.hypot

/**
 * 变宽笔迹渲染:逐点法线偏移构成闭合轮廓(上边前进 + 下边回退),
 * 压感经 S-γ 曲线映射宽度,显示宽度做 3ms EMA 平滑。
 * 荧光笔:恒宽 ×4 基准、低不透明度(alpha 由画笔侧控制)。
 * 形状(RECT/OVAL/LINE,两点 Stroke):恒定线宽轮廓(填充路径,调用方无需区分画笔样式)。
 */
object StrokeRenderer {

    const val HIGHLIGHT_WIDTH_MULTIPLIER = 4f

    /** 每点显示半径(px);荧光笔恒宽 */
    fun radii(stroke: Stroke, mmToPx: Float, gamma: PressureGamma): FloatArray {
        val pts = stroke.points
        val out = FloatArray(pts.size)
        if (pts.isEmpty()) return out
        if (stroke.tool == InkTool.HIGHLIGHTER) {
            val r = stroke.baseWidthMm * HIGHLIGHT_WIDTH_MULTIPLIER * mmToPx / 2f
            java.util.Arrays.fill(out, r)
            return out
        }
        if (stroke.tool.isShape) {
            java.util.Arrays.fill(out, stroke.baseWidthMm * mmToPx / 2f)
            return out
        }
        val filter = DisplayWidthFilter()
        var lastT = pts[0].timeMs
        for (i in pts.indices) {
            val p = pts[i]
            val ratio = PressureCurve.widthRatio(stroke.tool, p.pressure, gamma)
            val wPx = stroke.baseWidthMm * ratio * mmToPx
            val dt = (p.timeMs - lastT).toFloat().coerceIn(0.1f, 50f)
            lastT = p.timeMs
            out[i] = filter.next(wPx, dt) / 2f
        }
        return out
    }

    /** 构建填充路径。空/单点退化为圆点;形状工具走 [buildShapePath]。 */
    fun buildPath(stroke: Stroke, mmToPx: Float, gamma: PressureGamma): Path {
        if (stroke.tool.isShape) return buildShapePath(stroke, mmToPx)
        val pts = stroke.points
        val path = Path()
        if (pts.isEmpty()) return path
        val r = radii(stroke, mmToPx, gamma)
        if (pts.size == 1) {
            path.addCircle(pts[0].x, pts[0].y, r[0].coerceAtLeast(0.4f), Path.Direction.CW)
            return path
        }
        // 上侧前进
        for (i in 0 until pts.size) {
            val (ox, oy) = offset(i, pts, r, left = true)
            if (i == 0) path.moveTo(pts[0].x + ox, pts[0].y + oy) else path.lineTo(pts[i].x + ox, pts[i].y + oy)
        }
        // 下侧回退
        for (i in pts.size - 1 downTo 0) {
            val (ox, oy) = offset(i, pts, r, left = false)
            path.lineTo(pts[i].x + ox, pts[i].y + oy)
        }
        path.close()
        return path
    }

    /**
     * 形状轮廓(两点 = 起点终点;线宽 = baseWidthMm × mmToPx,恒定无压感):
     * - LINE:线段四边形(平头端帽)
     * - RECT:外框 − 内框(内缩一线宽),EVEN_ODD 成环;过小实心退化
     * - OVAL:外椭圆 − 内椭圆,同上
     * 全部为闭合填充路径,与既有 FILL 画笔路径兼容。
     */
    fun buildShapePath(stroke: Stroke, mmToPx: Float): Path {
        val pts = stroke.points
        val path = Path()
        if (pts.isEmpty()) return path
        val a = pts.first()
        val b = pts.last()
        val half = (stroke.baseWidthMm * mmToPx) / 2f
        when (stroke.tool) {
            InkTool.LINE -> {
                val dx = b.x - a.x; val dy = b.y - a.y
                val len = hypot(dx, dy)
                if (len < 1e-4f) {
                    path.addCircle(a.x, a.y, half.coerceAtLeast(0.4f), Path.Direction.CW)
                    return path
                }
                val nx = -dy / len * half
                val ny = dx / len * half
                path.moveTo(a.x + nx, a.y + ny)
                path.lineTo(b.x + nx, b.y + ny)
                path.lineTo(b.x - nx, b.y - ny)
                path.lineTo(a.x - nx, a.y - ny)
                path.close()
            }
            InkTool.RECT -> {
                val l = minOf(a.x, b.x); val t = minOf(a.y, b.y)
                val r = maxOf(a.x, b.x); val bo = maxOf(a.y, b.y)
                val inset = half * 2f
                if (r - l <= inset + 0.8f || bo - t <= inset + 0.8f) {
                    path.addRect(l, t, r, bo, Path.Direction.CW)   // 过小:实心退化
                    return path
                }
                path.setFillType(Path.FillType.EVEN_ODD)
                path.addRect(l, t, r, bo, Path.Direction.CW)
                path.addRect(l + inset, t + inset, r - inset, bo - inset, Path.Direction.CW)
            }
            InkTool.OVAL -> {
                val l = minOf(a.x, b.x); val t = minOf(a.y, b.y)
                val r = maxOf(a.x, b.x); val bo = maxOf(a.y, b.y)
                val rect = RectF(l, t, r, bo)
                val inset = half * 2f
                if (r - l <= inset + 0.8f || bo - t <= inset + 0.8f) {
                    path.addOval(rect, Path.Direction.CW)          // 过小:实心退化
                    return path
                }
                path.setFillType(Path.FillType.EVEN_ODD)
                path.addOval(rect, Path.Direction.CW)
                path.addOval(RectF(l + inset, t + inset, r - inset, bo - inset), Path.Direction.CW)
            }
            else -> {}
        }
        return path
    }

    /** i 点的法向偏移(±半径);端点取邻段方向 */
    private fun offset(i: Int, pts: List<InkPoint>, r: FloatArray, left: Boolean): Pair<Float, Float> {
        val dx: Float; val dy: Float
        when {
            i == 0 -> { dx = pts[1].x - pts[0].x; dy = pts[1].y - pts[0].y }
            i == pts.size - 1 -> { dx = pts[i].x - pts[i - 1].x; dy = pts[i].y - pts[i - 1].y }
            else -> { dx = pts[i + 1].x - pts[i - 1].x; dy = pts[i + 1].y - pts[i - 1].y }
        }
        val len = hypot(dx, dy)
        val sign = if (left) 1f else -1f
        if (len < 1e-4f) return 0f to -r[i] * sign
        return (-dy / len * r[i] * sign) to (dx / len * r[i] * sign)
    }
}
