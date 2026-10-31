package com.milknote.ink.render

import android.graphics.Path
import com.milknote.ink.input.DisplayWidthFilter
import com.milknote.ink.input.PressureCurve
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.PressureGamma
import com.milknote.ink.model.Stroke
import kotlin.math.hypot

/**
 * 变宽笔迹渲染:逐点法线偏移构成闭合轮廓(上边前进 + 下边回退),
 * 压感经 S-γ 曲线映射宽度,显示宽度做 3ms EMA 平滑。
 * 荧光笔:恒宽 ×4 基准、低不透明度(alpha 由画笔侧控制)。
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

    /** 构建填充路径。空/单点退化为圆点。 */
    fun buildPath(stroke: Stroke, mmToPx: Float, gamma: PressureGamma): Path {
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
