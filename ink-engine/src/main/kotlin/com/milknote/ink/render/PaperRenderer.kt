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
            "english" -> drawEnglish(canvas, w, h, lineColor)
            "tian" -> drawTian(canvas, w, h, lineColor)
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

    private fun drawEnglish(canvas: Canvas, w: Float, h: Float, color: Int) {
        val solid = Paint().apply { this.color = color; strokeWidth = 1f }
        val dashed = Paint(solid).apply { pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 5f), 0f) }
        var y = GRID_SPACING_PX * 2f
        while (y < h - GRID_SPACING_PX) {
            canvas.drawLine(0f, y, w, y, solid)
            canvas.drawLine(0f, y - GRID_SPACING_PX * 0.45f, w, y - GRID_SPACING_PX * 0.45f, dashed)
            canvas.drawLine(0f, y + GRID_SPACING_PX * 0.45f, w, y + GRID_SPACING_PX * 0.45f, dashed)
            y += GRID_SPACING_PX * 2f
        }
    }

    private fun drawTian(canvas: Canvas, w: Float, h: Float, color: Int) {
        val cell = GRID_SPACING_PX * 2.5f
        val paint = Paint().apply { this.color = color; strokeWidth = 1f; style = Paint.Style.STROKE }
        val dashed = Paint(paint).apply { pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 4f), 0f) }
        var y = cell
        while (y + cell < h) {
            var x = cell
            while (x + cell < w) {
                canvas.drawRect(x, y, x + cell, y + cell, paint)
                canvas.drawLine(x + cell / 2f, y, x + cell / 2f, y + cell, dashed)
                canvas.drawLine(x, y + cell / 2f, x + cell, y + cell / 2f, dashed)
                x += cell
            }
            y += cell
        }
    }
}
