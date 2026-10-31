package com.milknote.ink.model

/** 笔迹几何:包围盒 + 相交测试(整笔划橡皮擦除用) */
object StrokeGeom {

    data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
        fun expand(pad: Float) = Box(l - pad, t - pad, r + pad, b + pad)
        fun contains(x: Float, y: Float) = x in l..r && y in t..b
        fun intersects(o: Box) = !(o.l > r || o.r < l || o.t > b || o.b < t)
    }

    fun bounds(s: Stroke): Box {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (p in s.points) {
            if (p.x < l) l = p.x; if (p.x > r) r = p.x
            if (p.y < t) t = p.y; if (p.y > b) b = p.y
        }
        if (s.points.isEmpty()) return Box(0f, 0f, 0f, 0f)
        return Box(l, t, r, b)
    }

    /** 擦除判定:eraser 轨迹(按其宽度扩张)与目标笔迹包围盒相交,且有点落在扩张盒内 */
    fun intersects(target: Stroke, eraser: Stroke): Boolean {
        val half = eraser.baseWidthMm * MM_TO_PX * 0.5f + 2f
        val eb = bounds(eraser).expand(half)
        if (!eb.intersects(bounds(target))) return false
        for (p in target.points) if (eb.contains(p.x, p.y)) return true
        // 点阵皆在盒外但轨迹可能穿过:退化为线段相交采样(目标轨迹点到 eraser 线段距离)
        val eps = target.points
        val pts = eraser.points
        if (eps.size < 2 || pts.size < 2) return false
        for (i in 1 until eps.size) for (j in 1 until pts.size) {
            if (segIntersects(eps[i - 1].x, eps[i - 1].y, eps[i].x, eps[i].y,
                    pts[j - 1].x, pts[j - 1].y, pts[j].x, pts[j].y)) return true
        }
        return false
    }

    /** 1mm ≈ px(按 160dpi 屏幕 xhdpi 基准;精确换算由渲染层注入,此处取保守常量) */
    const val MM_TO_PX = 3.78f

    private fun segIntersects(x1: Float, y1: Float, x2: Float, y2: Float,
                              x3: Float, y3: Float, x4: Float, y4: Float): Boolean {
        val d = (x2 - x1) * (y4 - y3) - (y2 - y1) * (x4 - x3)
        if (kotlin.math.abs(d) < 1e-9f) return false
        val t = ((x3 - x1) * (y4 - y3) - (y3 - y1) * (x4 - x3)) / d
        val u = ((x3 - x1) * (y2 - y1) - (y3 - y1) * (x2 - x1)) / d
        return t in 0f..1f && u in 0f..1f
    }
}

/**
 * 套索命中(PEN-ADAPTATION §4.4 主键单击默认动作):
 * 圈选笔迹 = **全部采样点**落在闭合圈选路径内的 stroke(完全包围语义,可预期、可测试);
 * 点在多边形内用射线法(支持凹多边形)。
 */
object LassoGeom {

    /** 点是否在闭合多边形内(射线法;少于 3 点不构成区域) */
    fun contains(polygon: List<InkPoint>, x: Float, y: Float): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val a = polygon[j]
            val b = polygon[i]
            if ((b.y > y) != (a.y > y)) {
                val t = (y - a.y) / (b.y - a.y)
                if (x < a.x + t * (b.x - a.x)) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** stroke 是否被圈选捕获:非空且所有点均在路径内 */
    fun captures(polygon: List<InkPoint>, stroke: Stroke): Boolean =
        stroke.points.isNotEmpty() && stroke.points.all { contains(polygon, it.x, it.y) }
}
