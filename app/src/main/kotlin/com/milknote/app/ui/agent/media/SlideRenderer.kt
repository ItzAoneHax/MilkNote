package com.milknote.app.ui.agent.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.LeadingMarginSpan

/**
 * 把 [PptxSlide] 画成一张 16:9 的「干净幻灯片卡」(白底 + MilkNote 纸墨排版)。
 *
 * 同一套画法同时服务预览缩略图与高分辨率附件导出:调用方只改 `widthPx`。
 * 所有尺寸都以宽度为基准等比缩放,所以 320px 缩略图与 1920px 导出视觉完全一致。
 *
 * 绘制顺序:白底 → 顶部强调条 → 标题 → 正文要点 → 表格 → 内嵌图片(右栏) → 页码胶囊。
 */
object SlideRenderer {

    // ---- MilkNote 纸墨(与 ui/theme/Color.kt 对齐;幻灯片恒为亮色卡面) ----
    private const val COLOR_PAGE = Color.WHITE
    private const val COLOR_BORDER = 0xFFE2E4E9.toInt()
    private const val COLOR_INK = 0xFF18191B.toInt()
    private const val COLOR_MUTED = 0xFF5A5F68.toInt()
    private const val COLOR_GUIDE = 0xFF9DA3AE.toInt()
    private const val COLOR_ACCENT = 0xFF2563EB.toInt()
    private const val COLOR_TABLE_HEADER = 0xFFF6F6F8.toInt()
    private const val COLOR_TABLE_LINE = 0xFFE8EAEE.toInt()
    private const val COLOR_BADGE = 0xFFF1F2F5.toInt()

    /** 基准画布宽度:所有相对尺寸按 `width / BASE_WIDTH` 缩放 */
    private const val BASE_WIDTH = 1280f

    private const val PADDING = 72f          // 基准内边距
    private const val TITLE_SIZE = 52f
    private const val BULLET_SIZE = 30f
    private const val TABLE_SIZE = 23f
    private const val BADGE_SIZE = 22f
    private const val MAX_IMAGES = 2

    fun render(slide: PptxSlide, totalSlides: Int, widthPx: Int = 1280, heightPx: Int = 720): Bitmap {
        val width = widthPx.coerceIn(320, 4096)
        val height = heightPx.coerceIn(180, 4096)
        val unit = width / BASE_WIDTH
        val pad = PADDING * unit

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(COLOR_PAGE)

        val contentLeft = pad
        val contentRight = width - pad
        val contentWidth = (contentRight - contentLeft).coerceAtLeast(1f)
        val contentBottom = height - pad * 1.35f

        // 顶部强调条:短而克制,只用来暗示「这是文档页」而不是装饰
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_ACCENT }
        canvas.drawRoundRect(
            RectF(contentLeft, pad * 0.62f, contentLeft + 132f * unit, pad * 0.62f + 7f * unit),
            3.5f * unit,
            3.5f * unit,
            accentPaint,
        )

        // ---- 标题 ----
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_INK
            textSize = TITLE_SIZE * unit
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        var cursorY = pad * 1.18f
        if (slide.title.isNotBlank()) {
            val titleLayout = StaticLayout.Builder
                .obtain(slide.title, 0, slide.title.length, titlePaint, contentWidth.toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f * unit, 1.05f)
                .setIncludePad(false)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            canvas.save()
            canvas.translate(contentLeft, cursorY)
            titleLayout.draw(canvas)
            canvas.restore()
            cursorY += titleLayout.height + 34f * unit
        }

        val hasImages = slide.images.isNotEmpty()
        val imageColumnWidth = if (hasImages) contentWidth * 0.38f else 0f
        val imageGap = if (hasImages) 34f * unit else 0f
        val textWidth = (contentWidth - imageColumnWidth - imageGap).coerceAtLeast(contentWidth * 0.35f)

        // ---- 表格占正文下方一块固定区域(先量高度再决定要点可用高度)----
        val textColumnBottom = contentBottom
        var tableTop = textColumnBottom
        var tableHeight = 0f
        if (slide.tables.isNotEmpty()) {
            val tablePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_INK
                textSize = TABLE_SIZE * unit
            }
            val rowHeight = (TABLE_SIZE * unit) * 2.35f
            val available = (textColumnBottom - cursorY).coerceAtLeast(rowHeight)
            val rowsFitting = (available / rowHeight * 0.55f).toInt().coerceAtLeast(1)
            val rows = slide.tables.first().take(rowsFitting.coerceAtMost(8))
            tableHeight = rows.size * rowHeight
            tableTop = textColumnBottom - tableHeight
            drawTable(canvas, slide.tables.first(), rows, contentLeft, tableTop, textWidth, rowHeight, tablePaint, unit)
        }

        // ---- 正文要点 ----
        val bulletTop = cursorY
        val bulletBottom = if (tableHeight > 0f) tableTop - 22f * unit else textColumnBottom
        if (slide.bullets.isNotEmpty() && bulletBottom - bulletTop > BULLET_SIZE * unit) {
            val bulletPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_INK
                textSize = BULLET_SIZE * unit
            }
            val lineHeight = BULLET_SIZE * unit * 1.62f
            val maxLines = ((bulletBottom - bulletTop) / lineHeight).toInt().coerceAtLeast(1)
            val body = bulletSpans(slide.bullets, (BULLET_SIZE * unit * 1.35f).toInt())
            val layout = StaticLayout.Builder
                .obtain(body, 0, body.length, bulletPaint, textWidth.toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(8f * unit, 1.02f)
                .setIncludePad(false)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            canvas.save()
            canvas.translate(contentLeft, bulletTop)
            layout.draw(canvas)
            canvas.restore()
        }

        // ---- 内嵌图片(右栏,最多 2 张)----
        if (hasImages && contentBottom - cursorY > 40f * unit && imageColumnWidth > 0f) {
            drawImages(
                canvas = canvas,
                images = slide.images,
                left = contentRight - imageColumnWidth,
                top = cursorY,
                right = contentRight,
                bottom = contentBottom,
                gap = 16f * unit,
            )
        }

        // ---- 页码胶囊 ----
        drawBadge(canvas, slide.index, totalSlides, width, height, pad, unit)

        // ---- 卡面细边:让白卡在任意预览底色上都有边界 ----
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * unit
            color = COLOR_BORDER
        }
        canvas.drawRect(
            1f * unit,
            1f * unit,
            width - 1f * unit,
            height - 1f * unit,
            borderPaint,
        )
        return bitmap
    }

    /** 要点文本:每段前置 `•` + 悬挂缩进,换行后与正文左缘对齐 */
    private fun bulletSpans(bullets: List<String>, indentPx: Int): SpannableString {
        val text = bullets.joinToString("\n") { "•  $it" }
        val spans = SpannableString(text)
        if (text.isEmpty()) return spans
        spans.setSpan(
            LeadingMarginSpan.Standard(0, indentPx.coerceAtLeast(1)),
            0,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return spans
    }

    private fun drawTable(
        canvas: Canvas,
        table: List<List<String>>,
        rows: List<List<String>>,
        left: Float,
        top: Float,
        width: Float,
        rowHeight: Float,
        paint: TextPaint,
        unit: Float,
    ) {
        if (rows.isEmpty()) return
        val columns = rows.maxOf { it.size }.coerceAtLeast(1)
        val cellWidth = width / columns
        val cellPad = 10f * unit

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_TABLE_HEADER }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * unit
            color = COLOR_TABLE_LINE
        }

        val headerBottom = top + rowHeight
        canvas.drawRoundRect(
            RectF(left, top, left + width, headerBottom),
            8f * unit,
            8f * unit,
            headerPaint,
        )

        for (rowIndex in rows.indices) {
            val row = rows[rowIndex]
            val rowTop = top + rowIndex * rowHeight
            paint.typeface = if (rowIndex == 0) {
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            } else {
                Typeface.DEFAULT
            }
            val baseline = rowTop + rowHeight / 2f - (paint.descent() + paint.ascent()) / 2f
            for (column in 0 until columns) {
                val cell = row.getOrNull(column).orEmpty()
                if (cell.isBlank()) continue
                val available = (cellWidth - cellPad * 2f).coerceAtLeast(1f)
                val text = TextUtils.ellipsize(cell, paint, available, TextUtils.TruncateAt.END)
                canvas.drawText(text, 0, text.length, left + column * cellWidth + cellPad, baseline, paint)
            }
            canvas.drawLine(left, rowTop + rowHeight, left + width, rowTop + rowHeight, linePaint)
        }
        canvas.drawLine(left, top, left + width, top, linePaint)
        canvas.drawRect(left, top, left + width, top + rows.size * rowHeight, linePaint)
        if (table.size > rows.size) {
            // 行被截断时给一个安静的提示,而不是静默丢内容
            val hintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_GUIDE
                textSize = paint.textSize * 0.8f
            }
            canvas.drawText(
                "+${table.size - rows.size} 行",
                left,
                top + rows.size * rowHeight + hintPaint.textSize,
                hintPaint,
            )
        }
    }

    private fun drawImages(
        canvas: Canvas,
        images: List<PptxMedia>,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        gap: Float,
    ) {
        val selected = images.take(MAX_IMAGES)
        if (selected.isEmpty()) return
        val areaHeight = (bottom - top).coerceAtLeast(1f)
        val slotHeight = ((areaHeight - gap * (selected.size - 1)) / selected.size).coerceAtLeast(1f)
        val areaWidth = (right - left).coerceAtLeast(1f)

        for ((i, media) in selected.withIndex()) {
            val slotTop = top + i * (slotHeight + gap)
            val slotBottom = slotTop + slotHeight
            val bitmap = decodeBounded(media.bytes, areaWidth, slotHeight) ?: continue
            try {
                val scale = minOf(areaWidth / bitmap.width, slotHeight / bitmap.height)
                val drawWidth = (bitmap.width * scale).coerceAtLeast(1f)
                val drawHeight = (bitmap.height * scale).coerceAtLeast(1f)
                val drawLeft = left + (areaWidth - drawWidth) / 2f
                val drawTop = slotTop + (slotHeight - drawHeight) / 2f
                val dst = RectF(drawLeft, drawTop, drawLeft + drawWidth, drawTop + drawHeight)
                val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_TABLE_HEADER }
                canvas.drawRoundRect(dst, 8f, 8f, frame)
                canvas.save()
                canvas.clipRect(dst)
                canvas.drawBitmap(bitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            if (slotBottom > bottom) break
        }
    }

    private fun drawBadge(
        canvas: Canvas,
        index: Int,
        totalSlides: Int,
        width: Int,
        height: Int,
        pad: Float,
        unit: Float,
    ) {
        val total = if (totalSlides > 0) totalSlides else index
        val text = "$index / $total"
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = BADGE_SIZE * unit
        }
        val textWidth = paint.measureText(text)
        val badgeHeight = BADGE_SIZE * unit * 1.85f
        val badgeWidth = textWidth + BADGE_SIZE * unit * 1.3f
        val right = width - pad * 0.55f
        val bottom = height - pad * 0.5f
        val rect = RectF(right - badgeWidth, bottom - badgeHeight, right, bottom)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BADGE }
        canvas.drawRoundRect(rect, badgeHeight / 2f, badgeHeight / 2f, background)
        val baseline = rect.centerY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, rect.left + (rect.width() - textWidth) / 2f, baseline, paint)
    }

    /** 采样解码到目标区域(先读尺寸再按 2 的幂降采样,零 OOM 风险) */
    private fun decodeBounded(bytes: ByteArray, maxWidth: Float, maxHeight: Float): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val targetWidth = maxWidth.toInt().coerceAtLeast(1)
        val targetHeight = maxHeight.toInt().coerceAtLeast(1)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth && bounds.outHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
    }
}
