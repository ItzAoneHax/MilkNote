package com.milknote.app.graph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.milknote.agent.FunctionGraphSampler
import com.milknote.agent.FunctionGraphSpec
import com.milknote.agent.GraphCrop
import com.milknote.agent.GraphViewport
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 函数图像共享渲染器(Android Canvas):
 * 预览与最终 PNG 走同一实现,输出纸色底、网格、坐标轴刻度、多色曲线与图例。
 * 框选在渲染完成后按归一化坐标裁剪,选择框/遮罩只存在于 Compose 预览层。
 */
object FunctionGraphRenderer {

    private const val PAPER = 0xFFFAF9F5.toInt()
    private const val GRID = 0xFFE3DED2.toInt()
    private const val AXIS = 0xFF8C8578.toInt()
    private const val TEXT = 0xFF4F4A42.toInt()

    fun render(
        spec: FunctionGraphSpec,
        width: Int = 1200,
        height: Int = 800,
        crop: GraphCrop? = null,
    ): Bitmap {
        require(width > 0 && height > 0) { "图像尺寸必须为正" }
        val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawFull(Canvas(full), width, height, spec)
        if (crop == null) return full
        val c = crop.normalized()
        val left = (c.left * width).roundToInt().coerceIn(0, width - 1)
        val top = (c.top * height).roundToInt().coerceIn(0, height - 1)
        val right = (c.right * width).roundToInt().coerceIn(left + 1, width)
        val bottom = (c.bottom * height).roundToInt().coerceIn(top + 1, height)
        return Bitmap.createBitmap(full, left, top, right - left, bottom - top)
    }

    fun pngBytes(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().also {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    private fun drawFull(canvas: Canvas, width: Int, height: Int, spec: FunctionGraphSpec) {
        val vp = spec.viewport
        canvas.drawColor(PAPER)

        val left = width * 0.075f
        val right = width * 0.96f
        val top = height * 0.07f
        val bottom = height * 0.9f
        val plot = RectF(left, top, right, bottom)
        val xSpan = (vp.xMax - vp.xMin).coerceAtLeast(1e-9)
        val ySpan = (vp.yMax - vp.yMin).coerceAtLeast(1e-9)

        fun sx(x: Double): Float = (left + ((x - vp.xMin) / xSpan * (right - left))).toFloat()
        fun sy(y: Double): Float = (bottom - ((y - vp.yMin) / ySpan * (bottom - top))).toFloat()

        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GRID
            strokeWidth = (width * 0.0011f).coerceAtLeast(1f)
            style = Paint.Style.STROKE
        }
        val axis = Paint(grid).apply {
            color = AXIS
            strokeWidth = (width * 0.0018f).coerceAtLeast(1.2f)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT
            textSize = (width * 0.0145f).coerceIn(12f, 22f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val xStep = niceStep(xSpan)
        val yStep = niceStep(ySpan)

        // 网格
        var x = ceil(vp.xMin / xStep) * xStep
        var guard = 0
        while (x <= vp.xMax + 1e-9 && guard++ < 400) {
            val px = sx(x)
            canvas.drawLine(px, top, px, bottom, grid)
            x += xStep
        }
        var y = ceil(vp.yMin / yStep) * yStep
        guard = 0
        while (y <= vp.yMax + 1e-9 && guard++ < 400) {
            val py = sy(y)
            canvas.drawLine(left, py, right, py, grid)
            y += yStep
        }

        // 坐标轴
        val axisY = sy(0.0).takeIf { 0.0 in vp.yMin..vp.yMax }
        val axisX = sx(0.0).takeIf { 0.0 in vp.xMin..vp.xMax }
        axisY?.let { canvas.drawLine(left, it, right, it, axis) }
        axisX?.let { canvas.drawLine(it, top, it, bottom, axis) }

        // 刻度标签(每 2 格一个,避免拥挤)
        fun labelNumber(v: Double, step: Double): String =
            if (step >= 1.0) String.format(Locale.US, "%.0f", v)
            else String.format(Locale.US, "%.1f", v)

        val labelY = (axisY ?: bottom).coerceAtMost(bottom - 4f)
        x = ceil(vp.xMin / xStep) * xStep
        guard = 0
        var index = 0
        while (x <= vp.xMax + 1e-9 && guard++ < 400) {
            if (abs(x) > xStep * 0.25 && index % 2 == 0) {
                val label = labelNumber(x, xStep)
                canvas.drawText(label, sx(x) - text.measureText(label) / 2f, labelY + text.textSize + 6f, text)
            }
            x += xStep
            index++
        }
        y = ceil(vp.yMin / yStep) * yStep
        guard = 0
        index = 0
        while (y <= vp.yMax + 1e-9 && guard++ < 400) {
            if (abs(y) > yStep * 0.25 && index % 2 == 0) {
                val label = labelNumber(y, yStep)
                val tx = (axisX ?: left) - text.measureText(label) - 8f
                canvas.drawText(label, tx.coerceAtLeast(2f), sy(y) + text.textSize * 0.35f, text)
            }
            y += yStep
            index++
        }
        if (axisX != null && axisY != null) {
            canvas.drawText("0", axisX - text.measureText("0") - 6f, axisY + text.textSize + 2f, text)
        }

        // 曲线
        canvas.save()
        canvas.clipRect(plot)
        spec.curves.forEachIndexed { curveIndex, curve ->
            val segments = FunctionGraphSampler.segments(
                spec, curveIndex,
                samples = (width * 1.2f).roundToInt().coerceIn(420, 2200),
            )
            if (segments.isEmpty()) return@forEachIndexed
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = curve.color.toInt()
                style = Paint.Style.STROKE
                strokeWidth = (width * 0.0026f).coerceAtLeast(2f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            segments.forEach { points ->
                if (points.size < 2) return@forEach
                val path = Path()
                path.moveTo(sx(points[0].x), sy(points[0].y))
                for (i in 1 until points.size) path.lineTo(sx(points[i].x), sy(points[i].y))
                canvas.drawPath(path, paint)
            }
        }
        canvas.restore()

        // 图例
        drawLegend(canvas, spec, width, top, right, text)
    }

    private fun drawLegend(
        canvas: Canvas,
        spec: FunctionGraphSpec,
        width: Int,
        top: Float,
        right: Float,
        textPaint: Paint,
    ) {
        if (spec.curves.isEmpty()) return
        val pad = width * 0.01f
        val lineW = width * 0.022f
        val gap = width * 0.008f
        val rowH = textPaint.textSize * 1.45f
        val labels = spec.curves.map { curve ->
            val raw = curve.expression
            if (raw.length <= 26) raw else raw.take(25) + "…"
        }
        val textW = labels.maxOf { textPaint.measureText(it) }
        val boxW = pad * 2 + lineW + gap + textW
        val boxH = pad * 2 + rowH * spec.curves.size
        val box = RectF(
            (right - boxW).coerceAtLeast(0f),
            top + width * 0.008f,
            right,
            top + width * 0.008f + boxH,
        )
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE6FFFFFF.toInt()
            style = Paint.Style.FILL
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x338C8578
            style = Paint.Style.STROKE
            strokeWidth = (width * 0.0009f).coerceAtLeast(1f)
        }
        canvas.drawRoundRect(box, pad, pad, bg)
        canvas.drawRoundRect(box, pad, pad, border)
        spec.curves.forEachIndexed { i, curve ->
            val y = box.top + pad + rowH * i + rowH / 2f
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = curve.color.toInt()
                strokeWidth = (width * 0.0028f).coerceAtLeast(2f)
                strokeCap = Paint.Cap.ROUND
                style = Paint.Style.STROKE
            }
            canvas.drawLine(box.left + pad, y, box.left + pad + lineW, y, line)
            canvas.drawText(
                labels[i],
                box.left + pad + lineW + gap,
                y + textPaint.textSize * 0.35f,
                textPaint,
            )
        }
    }

    private fun niceStep(span: Double): Double {
        val raw = (span / 10.0).coerceAtLeast(1e-9)
        val exp = floor(log10(raw))
        val base = 10.0.pow(exp)
        val norm = raw / base
        val nice = when {
            norm <= 1.0 -> 1.0
            norm <= 2.0 -> 2.0
            norm <= 5.0 -> 5.0
            else -> 10.0
        }
        return nice * base
    }
}

/** 函数图像 PNG 资产:内容 hash 命名,写入 notebook 的 images/ 下 */
object FunctionGraphAsset {

    fun savePng(imagesDir: File, png: ByteArray): String {
        imagesDir.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(png)
            .joinToString("") { "%02x".format(it) }
            .take(24)
        val name = "graph_$digest.png"
        val file = File(imagesDir, name)
        if (!file.exists()) file.writeBytes(png)
        return "images/$name"
    }
}
