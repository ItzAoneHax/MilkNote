package com.milknote.ink.render

import android.graphics.Canvas
import android.graphics.Paint

/** 纸张模板绘制(DESIGN-V0 §5.1:点阵/横线/方格/空白,26px 网距,暗角由 UI 层叠加) */
object PaperRenderer {

    const val GRID_SPACING_PX = 26f

    fun draw(canvas: Canvas, paper: String, w: Float, h: Float, lineColor: Int) {
        when (paper) {
            "dot" -> drawDots(canvas, w, h, lineColor)
            "line" -> drawLines(canvas, w, h, lineColor)
            "grid" -> { drawDots(canvas, w, h, lineColor, sparse = true); drawLines(canvas, w, h, lineColor) }
            else -> {} // blank
        }
    }

    private fun drawDots(canvas: Canvas, w: Float, h: Float, dotColor: Int, sparse: Boolean = false) {
        val step = GRID_SPACING_PX * (if (sparse) 2f else 1f)
        val paint = Paint().apply { color = dotColor; strokeWidth = 1.6f; style = Paint.Style.FILL }
        var y = step
        while (y < h - 1) {
            var x = step
            while (x < w - 1) {
                canvas.drawPoint(x, y, paint)
                x += step
            }
            y += step
        }
    }

    private fun drawLines(canvas: Canvas, w: Float, h: Float, lineColor: Int) {
        val paint = Paint().apply { color = lineColor; strokeWidth = 1f; style = Paint.Style.STROKE }
        var y = GRID_SPACING_PX
        while (y < h - 1) {
            canvas.drawLine(0f, y, w, y, paint)
            y += GRID_SPACING_PX
        }
    }
}
