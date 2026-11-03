package com.milknote.ink.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import com.milknote.ink.model.AnnotationCard

/**
 * 批注卡渲染(Agent 结果贴回;AGENT-BACKEND §9):
 * 白卡 + 细边 + 左缘 accent 条(Agent 元素允许蓝,DESIGN-V0 §5.1 单强调色纪律),
 * 文本 markdown-lite → 样式文本(标题加粗/列表/行内样式/公式 mono;与抽屉渲染器能力对齐)。
 * 画布约定:调用方已处于页坐标系(与笔迹同系);dx/dy 为拖动预览偏移。
 */
object AnnotationCardRenderer {

    const val TEXT_SIZE_PX = 24f        // 页坐标 ≈144dpi 下约 4.2mm 行高基准
    const val LINE_SPACING = 1.38f
    const val PAD_H_PX = 26f
    const val PAD_V_PX = 22f
    const val ACCENT_W_PX = 7f
    const val RADIUS_PX = 16f
    const val COLLAPSE_LINES = 16       // 折叠显示行数(点卡展开)

    // 布局缓存(键:卡片内容指纹;容量粗略上限防膨胀)
    private val layouts = HashMap<String, StaticLayout>()
    private val layoutKeys = HashMap<String, String>()
    private const val CACHE_MAX = 48

    private val textPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = TEXT_SIZE_PX
    }

    private fun layoutKey(card: AnnotationCard, expanded: Boolean): String =
        "${card.id}:${card.width.toInt()}:${card.kind}:${expanded}:${card.text.hashCode()}"

    fun layout(card: AnnotationCard, expanded: Boolean): StaticLayout {
        val key = layoutKey(card, expanded)
        layoutKeys[card.id]?.let { if (it == key) return layouts[card.id]!! }
        if (layouts.size >= CACHE_MAX) { layouts.clear(); layoutKeys.clear() }
        textPaint.typeface = if (card.kind == AnnotationCard.Kind.LATEX) Typeface.MONOSPACE else Typeface.DEFAULT
        val widthPx = (card.width - PAD_H_PX * 2 - ACCENT_W_PX).toInt().coerceAtLeast(40)
        val maxLines = if (expanded) Int.MAX_VALUE else COLLAPSE_LINES
        val content = if (card.kind == AnnotationCard.Kind.LATEX) card.text else styledText(card.text)
        val l = StaticLayout.Builder
            .obtain(content, 0, content.length, textPaint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING)
            .setMaxLines(maxLines)
            .setEllipsize(if (expanded) null else android.text.TextUtils.TruncateAt.END)
            .build()
        layouts[card.id] = l
        layoutKeys[card.id] = key
        return l
    }

    /** markdown-lite → Spannable(行级:标题/列表/标签行/公式块;行内:加粗/代码/$公式$) */
    internal fun styledText(src: String): CharSequence {
        val sb = SpannableStringBuilder()
        var first = true
        for (rawLine in src.split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (!first) sb.append('\n')
            first = false
            val start = sb.length
            when {
                line.startsWith("$$") -> {
                    sb.append(line.removePrefix("$$").removeSuffix("$$").trim())
                    sb.setSpan(TypefaceSpan("monospace"), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                Regex("^#{1,6}\\s+").containsMatchIn(line) -> {
                    val m = Regex("^#{1,6}\\s+").find(line)!!
                    sb.append(line.substring(m.value.length))
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(AbsoluteSizeSpan((TEXT_SIZE_PX * 1.12f).toInt()), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                line.startsWith("#") && line.matches(Regex("(#[\\w\\u4e00-\\u9fff-]+\\s*)+")) -> {
                    sb.append(line)
                    sb.setSpan(ForegroundColorSpan(0xFF3B5BEC.toInt()), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                else -> {
                    val bullet = Regex("^[-*•]\\s+").find(line)
                    val ordered = Regex("^(\\d+)[.、)]\\s+").find(line)
                    when {
                        bullet != null -> sb.append("• ").append(line.substring(bullet.value.length))
                        ordered != null -> sb.append(ordered.groupValues[1]).append(". ").append(line.substring(ordered.value.length))
                        else -> sb.append(line)
                    }
                    appendInlineSpans(sb, start)
                }
            }
        }
        return sb
    }

    /** 行内 **bold** `code` $math$ → 样式 span(标记剔除) */
    private fun appendInlineSpans(sb: SpannableStringBuilder, lineStart: Int) {
        var i = lineStart
        while (i < sb.length) {
            val c = sb[i]
            val (marker, span) = when (c) {
                '*' -> if (i + 1 < sb.length && sb[i + 1] == '*') "**" to StyleSpan(Typeface.BOLD) as Any else null to null
                '`' -> "`" to TypefaceSpan("monospace")
                '$' -> "$" to TypefaceSpan("monospace")
                else -> null to null
            }
            if (marker == null || span == null) { i++; continue }
            val end = sb.indexOf(marker, i + marker.length)
            if (end < 0) { i++; continue }
            sb.delete(i, i + marker.length)          // 去开标记
            sb.delete(end - marker.length, end)      // 去闭标记
            sb.setSpan(span, i, end - marker.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            i = end - marker.length
        }
    }

    fun cardHeight(card: AnnotationCard, expanded: Boolean): Float =
        layout(card, expanded).height + PAD_V_PX * 2

    fun cardWidthDefault(pageWidthPx: Float): Float = (pageWidthPx * 0.36f).coerceIn(320f, 560f)

    /** 命中测试(页坐标,含拖动偏移) */
    fun hit(card: AnnotationCard, x: Float, y: Float, expanded: Boolean): Boolean =
        com.milknote.ink.model.ElementGeom.contains(card, cardHeight(card, expanded), x, y)

    private val bgPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val borderPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val accentPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val rect = RectF()

    fun draw(canvas: Canvas, card: AnnotationCard, dark: Boolean, expanded: Boolean, dx: Float = 0f, dy: Float = 0f) {
        val baseH = cardHeight(card, expanded)
        val displayW = card.width * card.scale
        val displayH = baseH * card.scale
        val cx = card.x + dx + displayW / 2f
        val cy = card.y + dy + displayH / 2f
        val x = cx - card.width / 2f
        val y = cy - baseH / 2f

        if (dark) {
            bgPaint.color = 0xFF26272D.toInt()
            borderPaint.color = 0xFF33343B.toInt()
            accentPaint.color = 0xFF7D92FF.toInt()
        } else {
            bgPaint.color = 0xFFFFFFFF.toInt()
            borderPaint.color = 0xFFE5E1D6.toInt()
            accentPaint.color = 0xFF3B5BEC.toInt()
        }

        canvas.save()
        canvas.rotate(card.rotationDeg, cx, cy)
        canvas.scale(card.scale, card.scale, cx, cy)
        rect.set(x, y, x + card.width, y + baseH)
        canvas.drawRoundRect(rect, RADIUS_PX, RADIUS_PX, bgPaint)
        canvas.drawRoundRect(rect, RADIUS_PX, RADIUS_PX, borderPaint)

        // 左缘 accent 条(圆角卡内,独立小圆角矩形)
        rect.set(x, y + 6f, x + ACCENT_W_PX, y + baseH - 6f)
        canvas.drawRoundRect(rect, ACCENT_W_PX / 2f, ACCENT_W_PX / 2f, accentPaint)

        val l = layout(card, expanded)
        (l.paint as TextPaint).color = if (dark) 0xFFE8E6E0.toInt() else 0xFF23221E.toInt()
        canvas.translate(x + PAD_H_PX + ACCENT_W_PX, y + PAD_V_PX)
        l.draw(canvas)
        canvas.restore()
    }
}
