package com.milknote.ink.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.MediaKind

/**
 * 音视频元素静态占位渲染(本地媒体)。
 *
 * 纸面、缩略图、PNG/JPG/PDF 导出与 Agent 截图共用这一条绘制路径:
 * 画圆角占位块 + 类型图标 + 时长,不依赖解码器、不播放、不在后台加载媒体。
 * 真实播放只发生在书写页中央播放面板(VideoView / MediaPlayer)。
 * 调用方须已处于页坐标系(与笔迹同系),rotationDeg 在此统一应用。
 */
object MediaElementRenderer {

    private const val VIDEO_FILL = 0x223B5BEC
    private const val VIDEO_LINE = 0x8A3B5BEC.toInt()
    private const val AUDIO_FILL = 0x222E9E6B
    private const val AUDIO_LINE = 0x8A2E9E6B.toInt()
    private const val TEXT_COLOR = 0xCC23221E.toInt()

    private val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val linePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val glyphPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val textPaint = Paint().apply { isAntiAlias = true; color = TEXT_COLOR }
    private val rect = RectF()
    private val iconPath = Path()

    /** 绘制静态占位(页坐标;旋转围绕元素中心)。 */
    fun draw(canvas: Canvas, media: MediaElement) {
        val w = media.width
        val h = media.height
        if (w <= 0f || h <= 0f) return
        val video = media.kind == MediaKind.VIDEO
        val cx = media.x + w / 2f
        val cy = media.y + h / 2f
        canvas.save()
        canvas.rotate(media.rotationDeg, cx, cy)

        rect.set(media.x, media.y, media.x + w, media.y + h)
        val radius = minOf(w, h) * 0.14f
        fillPaint.color = if (video) VIDEO_FILL else AUDIO_FILL
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        linePaint.color = if (video) VIDEO_LINE else AUDIO_LINE
        linePaint.strokeWidth = (minOf(w, h) * 0.012f).coerceIn(1f, 3f)
        canvas.drawRoundRect(rect, radius, radius, linePaint)

        val pad = minOf(w, h) * 0.12f
        val iconSize = minOf(h - pad * 2f, w * 0.34f).coerceAtLeast(1f)
        val iconCx = media.x + pad + iconSize / 2f
        glyphPaint.color = if (video) 0xCC3B5BEC.toInt() else 0xCC2E9E6B.toInt()
        if (video) {
            drawPlayGlyph(canvas, iconCx, cy, iconSize)
        } else {
            drawAudioGlyph(canvas, iconCx, cy, iconSize)
        }

        // 文本区:图标右侧;过窄时只保留图标,避免叠字。
        val textLeft = iconCx + iconSize / 2f + pad * 0.7f
        val available = media.x + w - pad - textLeft
        if (available > iconSize * 0.6f) {
            val titleSize = minOf(h * 0.24f, available * 0.22f, 22f).coerceAtLeast(8f)
            textPaint.textSize = titleSize
            textPaint.alpha = 0xCC
            canvas.drawText(if (video) "视频" else "录音", textLeft, cy - titleSize * 0.08f, textPaint)
            if (media.durationMs > 0L) {
                val subSize = (titleSize * 0.78f).coerceAtLeast(7f)
                textPaint.textSize = subSize
                textPaint.alpha = 0x99
                canvas.drawText(formatDuration(media.durationMs), textLeft, cy + titleSize * 0.95f, textPaint)
            }
        }
        canvas.restore()
    }

    private fun drawPlayGlyph(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        iconPath.reset()
        iconPath.moveTo(cx - size * 0.26f, cy - size * 0.36f)
        iconPath.lineTo(cx + size * 0.40f, cy)
        iconPath.lineTo(cx - size * 0.26f, cy + size * 0.36f)
        iconPath.close()
        canvas.drawPath(iconPath, glyphPaint)
    }

    /** 3 根圆角声波条,高度按中间高两侧低,与类型图标视觉区分。 */
    private fun drawAudioGlyph(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val barW = (size * 0.16f).coerceAtLeast(1f)
        val gap = barW * 0.85f
        val heights = listOf(size * 0.34f, size * 0.62f, size * 0.44f)
        val totalW = barW * 3 + gap * 2
        var x = cx - totalW / 2f
        for (barH in heights) {
            rect.set(x, cy - barH / 2f, x + barW, cy + barH / 2f)
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, glyphPaint)
            x += barW + gap
        }
    }

    /** mm:ss(超过 1 小时显示 h:mm:ss)。未知时长(<=0)返回 "--:--"。 */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "--:--"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}
