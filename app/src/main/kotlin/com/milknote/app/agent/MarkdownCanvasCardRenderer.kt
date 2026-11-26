package com.milknote.app.agent

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.milknote.app.ui.agent.LatexAstParser
import com.milknote.app.ui.agent.LatexUnicode
import com.milknote.app.ui.agent.MarkdownLite
import com.milknote.app.ui.agent.plainText
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.CardTextAlign
import com.milknote.ink.render.AnnotationCardRenderer
import kotlin.math.max
import kotlin.math.min

/**
 * 画布上的 Markdown / LaTeX 富卡片渲染([AnnotationCardRenderer.RichCardRenderer] 的实现)。
 *
 * 全部是 Android `Canvas` 矢量绘制:解析复用 `MarkdownLite` 与 `LatexAstParser`,
 * 排版用 `StaticLayout` + 本文件的二维公式引擎。**不使用 WebView 离屏截图**
 * —— 因此没有异步首帧空白、没有位图模糊,内存占用也只与文本布局同量级。
 * 同一实现被 `InkSurfaceView`、`PageRenderer`(缩略图 / Agent 截图)与导出共用。
 */
class MarkdownCanvasCardRenderer : AnnotationCardRenderer.RichCardRenderer {

    private val cache = object : LinkedHashMap<String, CardLayout>(CACHE_INITIAL, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CardLayout>?): Boolean =
            size > CACHE_MAX
    }

    override fun measureHeight(card: AnnotationCard, contentWidthPx: Float, expanded: Boolean): Float? {
        // 高度与配色无关:量高固定用亮色布局(暗色布局在首次绘制时再建,几何一致)
        val layout = layoutOf(card, contentWidthPx, expanded, dark = false) ?: return null
        return layout.height
    }

    override fun drawContent(
        canvas: Canvas,
        card: AnnotationCard,
        contentWidthPx: Float,
        dark: Boolean,
        expanded: Boolean,
    ): Boolean {
        val layout = layoutOf(card, contentWidthPx, expanded, dark) ?: return false
        layout.draw(canvas)
        return true
    }

    private fun layoutOf(card: AnnotationCard, contentWidthPx: Float, expanded: Boolean, dark: Boolean): CardLayout? {
        val width = contentWidthPx.toInt().coerceAtLeast(40).toFloat()
        val key = cacheKey(card, width.toInt(), dark, expanded)
        synchronized(cache) { cache[key]?.let { return it } }
        val built = runCatching {
            CardLayoutBuilder(width, dark, card, if (expanded) Float.MAX_VALUE else MarkdownCardText.COLLAPSE_MAX_HEIGHT_PX)
                .build()
        }.getOrNull() ?: return null
        synchronized(cache) {
            if (cache.size >= CACHE_MAX) cache.clear()
            cache[key] = built
        }
        return built
    }

    private fun cacheKey(card: AnnotationCard, widthPx: Int, dark: Boolean, expanded: Boolean): String =
        "${card.id}:$widthPx:$dark:$expanded:${card.kind}:${card.text.hashCode()}:${card.textStyle.hashCode()}"
}

// =====================================================================
// 纯逻辑(不触达 Android 类,单测覆盖)
// =====================================================================

/** Markdown / LaTeX 卡片的纯文本处理:公式块拆分、宽度适配比例、折叠高度、标题缩放。 */
internal object MarkdownCardText {

    const val COLLAPSE_MAX_HEIGHT_PX = 540f

    /** LATEX 卡片正文 → 1..N 条公式源码(`$$…$$` / `\[…\]` 优先,无标记则整段一条) */
    fun latexSources(text: String): List<String> {
        val src = text.trim()
        if (src.isEmpty()) return emptyList()
        val blocks = ArrayList<String>()
        var cursor = 0
        while (true) {
            val open = src.indexOf("$$", cursor)
            if (open < 0) break
            val close = src.indexOf("$$", open + 2)
            if (close < 0) break
            src.substring(open + 2, close).trim().takeIf { it.isNotEmpty() }?.let(blocks::add)
            cursor = close + 2
        }
        if (blocks.isNotEmpty()) return blocks
        cursor = 0
        while (true) {
            val open = src.indexOf("\\[", cursor)
            if (open < 0) break
            val close = src.indexOf("\\]", open + 2)
            if (close < 0) break
            src.substring(open + 2, close).trim().takeIf { it.isNotEmpty() }?.let(blocks::add)
            cursor = close + 2
        }
        if (blocks.isNotEmpty()) return blocks
        return listOf(src)
    }

    /** 单条公式宽度超出卡片内容宽度时的等比缩放(绝不溢出卡片) */
    fun fitScale(measuredWidth: Float, contentWidth: Float): Float =
        if (measuredWidth <= 0f || contentWidth <= 0f || measuredWidth <= contentWidth) 1f
        else contentWidth / measuredWidth

    /** 折叠态可见高度上限(展开态原样返回) */
    fun visibleHeight(totalHeight: Float, expanded: Boolean): Float =
        if (expanded) totalHeight else totalHeight.coerceAtMost(COLLAPSE_MAX_HEIGHT_PX)

    /** 标题 H1–H6 相对正文的字号倍率 */
    fun headingScale(level: Int): Float = when (level.coerceIn(1, 6)) {
        1 -> 1.52f
        2 -> 1.34f
        3 -> 1.2f
        4 -> 1.1f
        5 -> 1.04f
        else -> 1f
    }
}

// =====================================================================
// 布局模型:按顺序堆叠的 painter,每个 painter 自带高度
// =====================================================================

private const val CACHE_MAX = 48
private const val CACHE_INITIAL = 64

private const val BASE_TEXT_PX = 23f
private const val BLOCK_GAP = 10f
private const val PARAGRAPH_GAP = 8f
private const val HEADING_GAP = 11f
private const val QUOTE_INDENT = 14f
private const val CELL_PAD_H = 8f
private const val CELL_PAD_V = 6f
private const val CODE_PAD = 12f
private const val MATH_V_PAD = 10f
private const val CHIP_HEIGHT = 26f
private const val COLLAPSE_HINT_HEIGHT_PX = 32f

private class CardPalette(dark: Boolean) {
    val body = if (dark) 0xFFECEEF0.toInt() else 0xFF23221E.toInt()
    val heading = if (dark) 0xFFF5F6F8.toInt() else 0xFF18191B.toInt()
    val muted = if (dark) 0xFF9AA0AA.toInt() else 0xFF6B7078.toInt()
    val accent = if (dark) 0xFF5B92FF.toInt() else 0xFF2563EB.toInt()
    val border = if (dark) 0xFF3A3F48.toInt() else 0xFFE2E4E9.toInt()
    val surface = if (dark) 0xFF23262C.toInt() else 0xFFF3F4F6.toInt()
    val headerSurface = if (dark) 0xFF2C3038.toInt() else 0xFFF0F2F5.toInt()
    val codeText = if (dark) 0xFFE6E8EC.toInt() else 0xFF2E3238.toInt()
    val chipText = if (dark) 0xFF9DBBFF.toInt() else 0xFF2563EB.toInt()
    val doneText = if (dark) 0xFF7C828C.toInt() else 0xFF9AA0AA.toInt()
}

private interface Painter {
    val height: Float
    fun draw(canvas: Canvas, y: Float)
}

private class SpacePainter(override val height: Float) : Painter {
    override fun draw(canvas: Canvas, y: Float) = Unit
}

private class LayoutPainter(
    private val layout: StaticLayout,
    private val topPad: Float = 0f,
) : Painter {
    override val height: Float get() = layout.height + topPad
    override fun draw(canvas: Canvas, y: Float) {
        canvas.save()
        canvas.translate(0f, y + topPad)
        layout.draw(canvas)
        canvas.restore()
    }
}

private class InsetPainter(private val inner: Painter, private val inset: Float) : Painter {
    override val height: Float get() = inner.height
    override fun draw(canvas: Canvas, y: Float) {
        canvas.save()
        canvas.translate(inset, 0f)
        inner.draw(canvas, y)
        canvas.restore()
    }
}

private class StackPainter(private val children: List<Painter>) : Painter {
    override val height: Float = children.fold(0f) { acc, p -> acc + p.height }
    override fun draw(canvas: Canvas, y: Float) {
        var cursor = y
        children.forEach { child ->
            child.draw(canvas, cursor)
            cursor += child.height
        }
    }
}

private class QuotePainter(
    private val children: List<Painter>,
    private val bar: Paint,
    private val inset: Float,
) : Painter {
    override val height: Float = children.fold(0f) { acc, p -> acc + p.height }
    override fun draw(canvas: Canvas, y: Float) {
        canvas.drawRect(0f, y + 2f, 3f, y + height - 2f, bar)
        var cursor = y
        canvas.save()
        canvas.translate(inset, 0f)
        children.forEach { child ->
            child.draw(canvas, cursor)
            cursor += child.height
        }
        canvas.restore()
    }
}

/** 公式:超宽时等比缩放后居中,永不溢出卡片内容宽度 */
private class MathPainter(
    private val box: MathBox,
    private val scale: Float,
    private val width: Float,
    private val centered: Boolean,
) : Painter {
    override val height: Float get() = box.height * scale + MATH_V_PAD
    override fun draw(canvas: Canvas, y: Float) {
        canvas.save()
        val x = if (centered) ((width - box.width * scale) / 2f).coerceAtLeast(0f) else 0f
        canvas.translate(x, y + MATH_V_PAD / 2f)
        canvas.scale(scale, scale)
        box.draw(canvas, 0f, box.ascent)
        canvas.restore()
    }
}

private class CodeBlockPainter(
    private val body: Painter,
    private val language: String,
    private val width: Float,
    private val background: Paint,
    private val label: TextPaint,
) : Painter {
    private val labelHeight = if (language.isBlank()) 0f else BASE_TEXT_PX * 0.7f

    override val height: Float get() = body.height + CODE_PAD * 2 + labelHeight

    override fun draw(canvas: Canvas, y: Float) {
        canvas.drawRoundRect(RectF(0f, y, width, y + height), 10f, 10f, background)
        if (language.isNotBlank()) {
            canvas.drawText(language.uppercase(), CODE_PAD, y + CODE_PAD + labelHeight * 0.8f, label)
        }
        canvas.save()
        canvas.translate(CODE_PAD, y + CODE_PAD + labelHeight)
        body.draw(canvas, 0f)
        canvas.restore()
    }
}

private class HorizontalRulePainter(private val paint: Paint, private val width: Float) : Painter {
    override val height: Float get() = 15f
    override fun draw(canvas: Canvas, y: Float) {
        canvas.drawLine(0f, y + height / 2f, width, y + height / 2f, paint)
    }
}

private class TagLinePainter(
    private val chips: List<Pair<String, RectF>>,
    private val totalHeight: Float,
    private val fill: Paint,
    private val text: TextPaint,
) : Painter {
    override val height: Float get() = totalHeight
    override fun draw(canvas: Canvas, y: Float) {
        chips.forEach { (label, rect) ->
            val r = RectF(rect.left, rect.top + y, rect.right, rect.bottom + y)
            canvas.drawRoundRect(r, CHIP_HEIGHT / 2f, CHIP_HEIGHT / 2f, fill)
            val baseline = r.centerY() - (text.descent() + text.ascent()) / 2f
            canvas.drawText(label, r.left + 10f, baseline, text)
        }
    }
}

private class TablePainter(
    private val widths: FloatArray,
    private val header: List<StaticLayout>,
    private val headerHeight: Float,
    private val rows: List<Pair<List<StaticLayout>, Float>>,
    private val width: Float,
    private val palette: CardPalette,
) : Painter {

    private val grid = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = palette.border
    }
    private val headerFill = Paint().apply { isAntiAlias = true; color = palette.headerSurface }
    private val headerFoot = Paint().apply {
        isAntiAlias = true
        color = palette.headerSurface
        style = Paint.Style.FILL
    }
    private val outer = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
        color = palette.border
    }

    override val height: Float = headerHeight + rows.fold(0f) { acc, row -> acc + row.second }

    override fun draw(canvas: Canvas, y: Float) {
        val bottom = y + height
        canvas.drawRoundRect(RectF(0f, y, width, y + headerHeight + 8f), 9f, 9f, headerFill)
        canvas.drawRect(0f, y + headerHeight - 4f, width, y + headerHeight + 4f, headerFoot)

        var rowTop = y
        drawRow(canvas, header, headerHeight, rowTop)
        rowTop += headerHeight
        rows.forEach { (layouts, rowHeight) ->
            drawRow(canvas, layouts, rowHeight, rowTop)
            rowTop += rowHeight
        }

        // 网格线:横线全部,竖线按列边界
        var lineY = y
        canvas.drawLine(0f, lineY, width, lineY, grid)
        lineY += headerHeight
        rows.forEach { (_, rowHeight) ->
            canvas.drawLine(0f, lineY, width, lineY, grid)
            lineY += rowHeight
        }
        var lineX = 0f
        widths.forEach { columnWidth ->
            canvas.drawLine(lineX, y, lineX, bottom, grid)
            lineX += columnWidth
        }
        canvas.drawLine(width, y, width, bottom, grid)
        canvas.drawRoundRect(RectF(0.7f, y + 0.7f, width - 0.7f, bottom - 0.7f), 9f, 9f, outer)
    }

    private fun drawRow(canvas: Canvas, layouts: List<StaticLayout>, rowHeight: Float, top: Float) {
        var x = 0f
        layouts.forEachIndexed { index, layout ->
            val columnWidth = widths.getOrElse(index) { 0f }
            canvas.save()
            canvas.clipRect(x, top, x + columnWidth, top + rowHeight)
            canvas.translate(x + CELL_PAD_H, top + CELL_PAD_V)
            layout.draw(canvas)
            canvas.restore()
            x += columnWidth
        }
    }
}

// =====================================================================
// 布局构建:Markdown 块序 → painter 列表
// =====================================================================

private class CardLayout(val width: Float, val height: Float, private val painters: List<Painter>) {
    fun draw(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        var y = 0f
        painters.forEach { painter ->
            painter.draw(canvas, y)
            y += painter.height
        }
        canvas.restore()
    }
}

private class CardLayoutBuilder(
    private val width: Float,
    private val dark: Boolean,
    private val card: AnnotationCard,
    maxHeight: Float,
) {
    private val palette = CardPalette(dark)
    private val painters = ArrayList<Painter>()
    private val cap = maxHeight
    /** 折叠时为省略提示预留一行,保证最终高度不超过上限 */
    private val budget = if (maxHeight == Float.MAX_VALUE) {
        Float.MAX_VALUE
    } else {
        (maxHeight - COLLAPSE_HINT_HEIGHT_PX).coerceAtLeast(0f)
    }
    private var used = 0f
    private var truncated = false

    private val basePx = card.textStyle.fontSizePx.coerceIn(12f, 48f)

    fun build(): CardLayout {
        when (card.kind) {
            AnnotationCard.Kind.LATEX -> MarkdownCardText.latexSources(card.text)
                .forEach { addSpaced(mathPainter(it, width, centered = true), BLOCK_GAP) }

            AnnotationCard.Kind.MARKDOWN -> addBlocks(MarkdownLite.parse(card.text), width, depth = 0)

            else -> addSpaced(
                textPainter(SpannableStringBuilder(card.text), width, basePx, bodyColor(), mono = false),
                PARAGRAPH_GAP,
            )
        }
        if (truncated) add(ellipsisPainter(), force = true)
        val visible = if (cap == Float.MAX_VALUE) used else used.coerceAtMost(cap)
        return CardLayout(width, visible, painters.toList())
    }

    // ---- 块 ----

    private fun addBlocks(blocks: List<MarkdownLite.Block>, width: Float, depth: Int) {
        for (block in blocks) addBlock(block, width, depth)
    }

    private fun addBlock(block: MarkdownLite.Block, width: Float, depth: Int) {
        when (block) {
            is MarkdownLite.Block.Heading -> {
                val size = basePx * MarkdownCardText.headingScale(block.level)
                addSpaced(
                    textPainter(
                        inlineText(block.spans, boldAll = true), width, size, headingColor(),
                        lineSpacing = 1.24f,
                    ),
                    HEADING_GAP,
                )
            }
            is MarkdownLite.Block.Paragraph -> addSpaced(
                textPainter(inlineText(block.spans), width, basePx, bodyColor()),
                PARAGRAPH_GAP,
            )
            is MarkdownLite.Block.ListItem -> {
                val indentPx = block.indent * 18f
                val prefix = block.order?.let { "$it. " } ?: "• "
                val content = SpannableStringBuilder()
                content.append(prefix)
                content.append(inlineText(block.spans, boldAll = false))
                val painter = textPainter(content, (width - indentPx).coerceAtLeast(24f), basePx, bodyColor())
                addSpaced(InsetPainter(painter, indentPx), PARAGRAPH_GAP * 0.6f)
            }
            is MarkdownLite.Block.TaskItem -> {
                val indentPx = block.indent * 18f
                val content = SpannableStringBuilder()
                content.append(if (block.checked) "☑ " else "☐ ")
                content.append(
                    inlineText(
                        block.spans,
                        boldAll = false,
                        strikeAll = block.checked,
                        colorOverride = if (block.checked) palette.doneText else null,
                    ),
                )
                val painter = textPainter(
                    content, (width - indentPx).coerceAtLeast(24f), basePx,
                    if (block.checked) palette.doneText else bodyColor(),
                )
                addSpaced(InsetPainter(painter, indentPx), PARAGRAPH_GAP * 0.6f)
            }
            is MarkdownLite.Block.MathBlock -> addSpaced(mathPainter(block.latex, width, centered = true), BLOCK_GAP)
            is MarkdownLite.Block.CodeBlock -> addSpaced(codePainter(block, width), BLOCK_GAP)
            is MarkdownLite.Block.Quote -> {
                if (depth >= MAX_NESTING) return
                val innerWidth = (width - QUOTE_INDENT).coerceAtLeast(24f)
                val inner = CardLayoutBuilder(innerWidth, dark, card, Float.MAX_VALUE)
                inner.addBlocks(block.blocks, innerWidth, depth + 1)
                val bar = Paint().apply { isAntiAlias = true; color = palette.accent; style = Paint.Style.FILL }
                addSpaced(QuotePainter(inner.painters.toList(), bar, QUOTE_INDENT), BLOCK_GAP)
            }
            is MarkdownLite.Block.Table -> addSpaced(tablePainter(block, width), BLOCK_GAP)
            MarkdownLite.Block.HorizontalRule -> addSpaced(
                HorizontalRulePainter(
                    Paint().apply { isAntiAlias = true; color = palette.border; strokeWidth = 1f },
                    width,
                ),
                4f,
            )
            is MarkdownLite.Block.TagLine -> addSpaced(tagLinePainter(block, width), PARAGRAPH_GAP)
            is MarkdownLite.Block.GeneratedDocument -> {
                if (depth >= MAX_NESTING) return
                addSpaced(documentHeaderPainter(block, width), BLOCK_GAP)
                when (block.type) {
                    MarkdownLite.GeneratedDocType.LATEX -> MarkdownCardText.latexSources(block.content)
                        .forEach { addSpaced(mathPainter(it, width, centered = true), BLOCK_GAP) }
                    MarkdownLite.GeneratedDocType.MARKDOWN -> addBlocks(
                        MarkdownLite.parse(block.content), width, depth + 1,
                    )
                    MarkdownLite.GeneratedDocType.TEXT -> addSpaced(
                        textPainter(
                            SpannableStringBuilder(block.content), width, basePx * 0.92f,
                            bodyColor(), mono = true,
                        ),
                        PARAGRAPH_GAP,
                    )
                }
            }
        }
    }

    private fun addSpaced(painter: Painter, gapBefore: Float) {
        if (painter.height <= 0f) return
        val gap = if (used > 0f) gapBefore else 0f
        if (used + gap + painter.height > budget && used > 0f) {
            truncated = true
            return
        }
        if (gap > 0f) {
            painters.add(SpacePainter(gap))
            used += gap
        }
        painters.add(painter)
        used += painter.height
    }

    private fun add(painter: Painter, force: Boolean = false) {
        if (!force && used + painter.height > budget && used > 0f) {
            truncated = true
            return
        }
        painters.add(painter)
        used += painter.height
    }

    // ---- 文本 ----

    private fun bodyColor(): Int {
        val styleColor = card.textStyle.colorArgb
        return if (styleColor == 0xFF23221EL || styleColor == 0xFF18191BL) palette.body else styleColor.toInt()
    }

    private fun headingColor(): Int {
        val styleColor = card.textStyle.colorArgb
        return if (styleColor == 0xFF23221EL || styleColor == 0xFF18191BL) palette.heading else styleColor.toInt()
    }

    private fun alignment(): Layout.Alignment = when (card.textStyle.align) {
        CardTextAlign.START -> Layout.Alignment.ALIGN_NORMAL
        CardTextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
        CardTextAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
    }

    private fun buildLayout(
        content: CharSequence,
        layoutWidth: Float,
        textSizePx: Float,
        color: Int,
        lineSpacing: Float = 1.32f,
        mono: Boolean = false,
        bold: Boolean = false,
        italic: Boolean = false,
        maxLines: Int = Int.MAX_VALUE,
    ): StaticLayout {
        val paint = TextPaint().apply {
            isAntiAlias = true
            textSize = textSizePx
            this.color = color
            typeface = when {
                mono -> Typeface.MONOSPACE
                bold && italic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                bold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                italic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                else -> Typeface.DEFAULT
            }
        }
        val safeWidth = layoutWidth.toInt().coerceAtLeast(1)
        val builder = StaticLayout.Builder
            .obtain(content, 0, content.length, paint, safeWidth)
            .setAlignment(alignment())
            .setLineSpacing(0f, lineSpacing)
            .setIncludePad(false)
        if (maxLines != Int.MAX_VALUE) {
            builder.setMaxLines(maxLines)
            builder.setEllipsize(TextUtils.TruncateAt.END)
        }
        return builder.build()
    }

    private fun textPainter(
        content: CharSequence,
        layoutWidth: Float,
        textSizePx: Float,
        color: Int,
        lineSpacing: Float = 1.32f,
        mono: Boolean = false,
    ): Painter = LayoutPainter(buildLayout(content, layoutWidth, textSizePx, color, lineSpacing, mono))

    /** 行内 span → Spannable(标记剔除;公式转 Unicode 数学体并用强调色) */
    private fun inlineText(
        spans: List<MarkdownLite.Span>,
        boldAll: Boolean = false,
        strikeAll: Boolean = false,
        colorOverride: Int? = null,
    ): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        for (span in spans) {
            val start = sb.length
            when (span) {
                is MarkdownLite.Span.Plain -> sb.append(span.text)
                is MarkdownLite.Span.Bold -> {
                    sb.append(span.text)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                is MarkdownLite.Span.Italic -> {
                    sb.append(span.text)
                    sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                is MarkdownLite.Span.BoldItalic -> {
                    sb.append(span.text)
                    sb.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                is MarkdownLite.Span.Strike -> {
                    sb.append(span.text)
                    sb.setSpan(StrikethroughSpan(), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                is MarkdownLite.Span.Code -> {
                    sb.append(span.text)
                    sb.setSpan(TypefaceSpan("monospace"), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(
                        ForegroundColorSpan(palette.codeText), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    sb.setSpan(
                        BackgroundColorSpan(palette.surface), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                is MarkdownLite.Span.Math -> {
                    sb.append(LatexUnicode.inline(span.latex))
                    sb.setSpan(TypefaceSpan("serif"), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(
                        ForegroundColorSpan(palette.accent), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                is MarkdownLite.Span.Link -> {
                    sb.append(span.label.ifBlank { span.url })
                    sb.setSpan(
                        ForegroundColorSpan(palette.accent), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    sb.setSpan(UnderlineSpan(), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                is MarkdownLite.Span.Image -> sb.append("🖼 ").append(span.alt.ifBlank { span.url })
                is MarkdownLite.Span.Wikilink -> {
                    sb.append("[[").append(span.target).append("]]")
                    sb.setSpan(
                        ForegroundColorSpan(palette.accent), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                is MarkdownLite.Span.Mention -> {
                    sb.append('@').append(span.name)
                    sb.setSpan(
                        ForegroundColorSpan(palette.accent), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        }
        if (sb.isEmpty()) sb.append(' ')
        if (boldAll) sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (strikeAll) sb.setSpan(StrikethroughSpan(), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        colorOverride?.let {
            sb.setSpan(ForegroundColorSpan(it), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    // ---- 公式 ----

    private fun mathPainter(latex: String, availableWidth: Float, centered: Boolean): Painter {
        if (latex.isBlank()) return SpacePainter(0f)
        val engine = CanvasMathEngine(basePx * 1.12f, bodyColor(), display = true)
        val box = runCatching { engine.box(LatexAstParser.parse(latex)) }.getOrNull()
        if (box == null || box.width <= 0.5f) {
            return textPainter(
                SpannableStringBuilder(latex), availableWidth, basePx * 0.92f, palette.muted, mono = true,
            )
        }
        val fit = MarkdownCardText.fitScale(box.width, availableWidth)
        return MathPainter(box, fit, availableWidth, centered)
    }

    // ---- 代码块 / 表格 / 标签 / 文档 ----

    private fun codePainter(block: MarkdownLite.Block.CodeBlock, availableWidth: Float): Painter {
        val textWidth = (availableWidth - CODE_PAD * 2).coerceAtLeast(24f)
        val body = LayoutPainter(
            buildLayout(
                SpannableStringBuilder(block.code.ifBlank { " " }),
                textWidth,
                basePx * 0.86f,
                palette.codeText,
                lineSpacing = 1.28f,
                mono = true,
            ),
        )
        val background = Paint().apply { isAntiAlias = true; color = palette.surface; style = Paint.Style.FILL }
        val label = TextPaint().apply {
            isAntiAlias = true
            textSize = basePx * 0.62f
            color = palette.muted
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.08f
        }
        return CodeBlockPainter(body, block.language, availableWidth, background, label)
    }

    private fun tablePainter(block: MarkdownLite.Block.Table, availableWidth: Float): Painter {
        val columns = max(block.headers.size, block.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
        val header = List(columns) { index -> block.headers.getOrNull(index).orEmpty() }
        val rows = block.rows.map { row -> List(columns) { index -> row.getOrNull(index).orEmpty() } }
        val measurePaint = TextPaint().apply {
            isAntiAlias = true
            textSize = basePx * 0.86f
        }
        val natural = FloatArray(columns) { column ->
            var maxText = measurePaint.measureText(header[column].joinToString("") { it.plainText() })
            rows.forEach { row ->
                maxText = max(maxText, measurePaint.measureText(row[column].joinToString("") { it.plainText() }))
            }
            maxText + CELL_PAD_H * 2
        }
        val total = natural.sum().coerceAtLeast(1f)
        val widths = FloatArray(columns) { column ->
            val scaled = if (total > availableWidth) natural[column] * (availableWidth / total) else natural[column]
            scaled.coerceAtLeast(28f)
        }
        val columnWidths = if (widths.sum() > availableWidth && widths.sum() > 0f) {
            val k = availableWidth / widths.sum()
            FloatArray(columns) { widths[it] * k }
        } else {
            widths
        }

        fun rowOf(cells: List<List<MarkdownLite.Span>>, bold: Boolean): Pair<List<StaticLayout>, Float> {
            val layouts = List(columns) { column ->
                buildLayout(
                    inlineText(cells.getOrElse(column) { emptyList() }, boldAll = bold),
                    (columnWidths[column] - CELL_PAD_H * 2).coerceAtLeast(12f),
                    basePx * 0.86f,
                    if (bold) headingColor() else bodyColor(),
                    lineSpacing = 1.22f,
                    bold = bold,
                )
            }
            val height = layouts.maxOf { it.height } + CELL_PAD_V * 2
            return layouts to height
        }

        val headerRow = rowOf(header, bold = true)
        val dataRows = rows.map { rowOf(it, bold = false) }
        return TablePainter(
            columnWidths, headerRow.first, headerRow.second, dataRows, availableWidth, palette,
        )
    }

    private fun tagLinePainter(block: MarkdownLite.Block.TagLine, availableWidth: Float): Painter {
        val text = TextPaint().apply {
            isAntiAlias = true
            textSize = basePx * 0.8f
            color = palette.chipText
        }
        val fill = Paint().apply {
            isAntiAlias = true
            color = if (dark) 0xFF2C3242.toInt() else 0xFFEAEFFB.toInt()
            style = Paint.Style.FILL
        }
        val chips = ArrayList<Pair<String, RectF>>()
        var x = 0f
        var y = 0f
        for (tag in block.tags) {
            val label = "#$tag"
            val chipWidth = text.measureText(label) + 20f
            if (x > 0f && x + chipWidth > availableWidth) {
                x = 0f
                y += CHIP_HEIGHT + 6f
            }
            chips.add(label to RectF(x, y, x + chipWidth, y + CHIP_HEIGHT))
            x += chipWidth + 6f
        }
        val totalHeight = if (chips.isEmpty()) 0f else y + CHIP_HEIGHT
        return TagLinePainter(chips, totalHeight, fill, text)
    }

    private fun documentHeaderPainter(block: MarkdownLite.Block.GeneratedDocument, availableWidth: Float): Painter {
        val title = block.title.ifBlank { "文档" }
        val badge = when (block.type) {
            MarkdownLite.GeneratedDocType.LATEX -> "TEX"
            MarkdownLite.GeneratedDocType.TEXT -> "TXT"
            MarkdownLite.GeneratedDocType.MARKDOWN -> "MD"
        }
        val titleLayout = buildLayout(
            SpannableStringBuilder(title), (availableWidth - 76f).coerceAtLeast(24f), basePx * 0.88f,
            headingColor(), lineSpacing = 1.2f, bold = true, maxLines = 1,
        )
        val background = Paint().apply {
            isAntiAlias = true
            color = if (dark) 0xFF2C3038.toInt() else 0xFFF0F2F5.toInt()
            style = Paint.Style.FILL
        }
        val border = Paint().apply {
            isAntiAlias = true
            color = palette.border
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val badgePaint = TextPaint().apply {
            isAntiAlias = true
            textSize = basePx * 0.62f
            color = palette.accent
            typeface = Typeface.MONOSPACE
        }
        return DocumentHeaderPainter(titleLayout, badge, availableWidth, background, border, badgePaint)
    }

    private fun ellipsisPainter(): Painter = LayoutPainter(
        buildLayout(
            SpannableStringBuilder("… 展开查看完整内容"), width, basePx * 0.78f, palette.muted, lineSpacing = 1.1f,
        ),
        4f,
    )

    private companion object {
        const val MAX_NESTING = 2
    }
}

private class DocumentHeaderPainter(
    private val title: StaticLayout,
    private val badge: String,
    private val width: Float,
    private val background: Paint,
    private val border: Paint,
    private val badgePaint: TextPaint,
) : Painter {

    override val height: Float get() = max(title.height + 16f, 34f)

    override fun draw(canvas: Canvas, y: Float) {
        canvas.drawRoundRect(RectF(0f, y, width, y + height), 8f, 8f, background)
        canvas.drawRoundRect(RectF(0.5f, y + 0.5f, width - 0.5f, y + height - 0.5f), 8f, 8f, border)
        canvas.save()
        canvas.clipRect(0f, y, width - 56f, y + height)
        canvas.translate(10f, y + (height - title.height) / 2f)
        title.draw(canvas)
        canvas.restore()
        val baseline = y + height / 2f - (badgePaint.descent() + badgePaint.ascent()) / 2f
        canvas.drawText(badge, width - 10f - badgePaint.measureText(badge), baseline, badgePaint)
    }
}

// =====================================================================
// 二维公式排版引擎(Android Canvas 版,规则对齐 LatexNativeView 的 MathEngine)
// =====================================================================

private class MathBox(
    val width: Float,
    val ascent: Float,
    val descent: Float,
    val draw: (Canvas, Float, Float) -> Unit,
) {
    val height: Float get() = ascent + descent
}

private fun emptyBox(): MathBox = MathBox(0f, 0f, 0f) { _, _, _ -> }

private fun spaceBox(width: Float): MathBox = MathBox(width, 0f, 0f) { _, _, _ -> }

private fun hbox(boxes: List<MathBox>): MathBox {
    if (boxes.isEmpty()) return emptyBox()
    var width = 0f
    var ascent = 0f
    var descent = 0f
    val offsets = FloatArray(boxes.size)
    boxes.forEachIndexed { index, box ->
        offsets[index] = width
        width += box.width
        ascent = max(ascent, box.ascent)
        descent = max(descent, box.descent)
    }
    return MathBox(width, ascent, descent) { canvas, x, y ->
        boxes.forEachIndexed { index, box -> box.draw(canvas, x + offsets[index], y) }
    }
}

private class CanvasMathEngine(
    private val basePx: Float,
    private val color: Int,
    private val display: Boolean,
) {
    private val scriptScale = if (display) 0.7f else 0.68f
    private val axis = 0.26f
    private val paintCache = HashMap<String, TextPaint>()

    fun box(
        node: LatexAstParser.Node,
        scale: Float = 1f,
        upright: Boolean = false,
        bold: Boolean = false,
    ): MathBox {
        val sizePx = basePx * scale
        return when (node) {
            is LatexAstParser.Seq -> seqBox(node.items, scale, upright, bold)
            is LatexAstParser.Text -> measureText(node.text, sizePx, serif = false)
            is LatexAstParser.Symbol -> symbolBox(node.symbol, sizePx, upright, bold)
            is LatexAstParser.Sup -> scriptBox(node.base, node.exp, null, scale, upright, bold)
            is LatexAstParser.Sub -> scriptBox(node.base, null, node.index, scale, upright, bold)
            is LatexAstParser.SubSup -> scriptBox(node.base, node.exp, node.index, scale, upright, bold)
            is LatexAstParser.Frac -> fracBox(node, scale, upright, bold)
            is LatexAstParser.Sqrt -> sqrtBox(node, scale, upright, bold)
            is LatexAstParser.BigOp -> bigOpBox(node, scale)
            is LatexAstParser.Accent -> accentBox(node, scale, upright, bold)
            is LatexAstParser.Delim -> delimBox(node, scale, upright, bold)
            is LatexAstParser.Matrix -> matrixBox(node, scale)
            is LatexAstParser.Styled -> when (node.style) {
                LatexAstParser.Style.BOLD -> box(node.body, scale, upright = true, bold = true)
                LatexAstParser.Style.MATHBB -> mathbbBox(node.body, scale)
                LatexAstParser.Style.ROMAN, LatexAstParser.Style.TEXT ->
                    box(node.body, scale, upright = true, bold = bold)
                LatexAstParser.Style.ITALIC -> box(node.body, scale, upright = false, bold = bold)
            }
        }
    }

    fun fallback(latex: String): MathBox =
        measureText(latex, basePx * 0.9f, serif = false, mono = true)

    private fun seqBox(
        items: List<LatexAstParser.Node>,
        scale: Float,
        upright: Boolean,
        bold: Boolean,
    ): MathBox {
        if (items.isEmpty()) return emptyBox()
        val sizePx = basePx * scale
        val boxes = ArrayList<MathBox>(items.size * 2)
        items.forEachIndexed { index, item ->
            if (index > 0) boxes.add(spaceBox(sizePx * 0.06f))
            boxes.add(box(item, scale, upright, bold))
        }
        return hbox(boxes)
    }

    private fun mathbbBox(node: LatexAstParser.Node, scale: Float): MathBox = when (node) {
        is LatexAstParser.Symbol -> symbolBox(
            LatexAstParser.blackboard(node.symbol), basePx * scale, upright = true, bold = false,
        )
        is LatexAstParser.Seq -> hbox(node.items.map { mathbbBox(it, scale) })
        else -> box(node, scale, upright = true)
    }

    private fun symbolBox(symbol: String, sizePx: Float, upright: Boolean, bold: Boolean): MathBox {
        if (symbol.isEmpty()) return emptyBox()
        if (symbol == " ") return spaceBox(sizePx * 0.22f)
        val italic = !upright && symbol.length == 1 && symbol[0].isLetter() && symbol[0].code < 0x0400
        return measureText(symbol, sizePx, italic = italic, bold = bold, serif = true)
    }

    private fun measureText(
        text: String,
        sizePx: Float,
        italic: Boolean = false,
        bold: Boolean = false,
        serif: Boolean = true,
        mono: Boolean = false,
    ): MathBox {
        if (text.isEmpty()) return emptyBox()
        val paint = textPaint(sizePx, italic, bold, serif, mono)
        val width = paint.measureText(text).coerceAtLeast(0f)
        val metrics = paint.fontMetrics
        val ascent = max(0f, -metrics.ascent)
        val descent = max(0f, metrics.descent)
        return MathBox(width, ascent, descent) { canvas, x, baseline ->
            canvas.drawText(text, x, baseline, paint)
        }
    }

    private fun textPaint(
        sizePx: Float,
        italic: Boolean,
        bold: Boolean,
        serif: Boolean,
        mono: Boolean,
    ): TextPaint {
        val key = "${sizePx.toInt()}|$italic|$bold|$serif|$mono"
        return paintCache.getOrPut(key) {
            val family = when {
                mono -> Typeface.MONOSPACE
                serif -> Typeface.SERIF
                else -> Typeface.SANS_SERIF
            }
            val style = when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            TextPaint().apply {
                isAntiAlias = true
                textSize = sizePx
                typeface = Typeface.create(family, style)
                this.color = this@CanvasMathEngine.color
            }
        }
    }

    private fun strokePaint(width: Float): Paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = this@CanvasMathEngine.color
        strokeWidth = width
    }

    private fun scriptBox(
        baseNode: LatexAstParser.Node,
        sup: LatexAstParser.Node?,
        sub: LatexAstParser.Node?,
        scale: Float,
        upright: Boolean,
        bold: Boolean,
    ): MathBox {
        val sizePx = basePx * scale
        val base = box(baseNode, scale, upright, bold)
        val supBox = sup?.let { box(it, scale * scriptScale, upright, bold) }
        val subBox = sub?.let { box(it, scale * scriptScale, upright, bold) }
        val supShift = sizePx * 0.46f
        val subShift = sizePx * 0.22f
        var ascent = base.ascent
        var descent = base.descent
        if (supBox != null) ascent = max(ascent, supShift + supBox.ascent)
        if (subBox != null) descent = max(descent, subShift + subBox.descent)
        val width = base.width + max(supBox?.width ?: 0f, subBox?.width ?: 0f) + sizePx * 0.02f
        return MathBox(width, ascent, descent) { canvas, x, y ->
            base.draw(canvas, x, y)
            supBox?.draw(canvas, x + base.width, y - supShift)
            subBox?.draw(canvas, x + base.width, y + subShift)
        }
    }

    private fun fracBox(
        node: LatexAstParser.Frac,
        scale: Float,
        upright: Boolean,
        bold: Boolean,
    ): MathBox {
        val sizePx = basePx * scale
        val partScale = scale * if (display) 0.95f else 0.85f
        val num = box(node.num, partScale, upright, bold)
        val den = box(node.den, partScale, upright, bold)
        val ruleHalf = sizePx * 0.028f
        val gap = sizePx * 0.14f
        val axisY = sizePx * axis
        val padding = sizePx * 0.1f
        val width = max(num.width, den.width) + padding * 2
        val ascent = axisY + ruleHalf + gap + num.height
        val descent = axisY + ruleHalf + gap + den.height
        val rule = strokePaint(ruleHalf * 2f)
        return MathBox(width, ascent, descent) { canvas, x, y ->
            val ruleY = y - axisY
            val numBaseline = ruleY - ruleHalf - gap - num.descent
            val denBaseline = ruleY + ruleHalf + gap + den.ascent
            num.draw(canvas, x + (width - num.width) / 2f, numBaseline)
            den.draw(canvas, x + (width - den.width) / 2f, denBaseline)
            canvas.drawLine(x + padding * 0.35f, ruleY, x + width - padding * 0.35f, ruleY, rule)
        }
    }

    private fun sqrtBox(
        node: LatexAstParser.Sqrt,
        scale: Float,
        upright: Boolean,
        bold: Boolean,
    ): MathBox {
        val sizePx = basePx * scale
        val body = box(node.body, scale, upright, bold)
        val degree = node.degree?.let { box(it, scale * 0.55f, upright, bold) }
        val radicalWidth = sizePx * 0.62f
        val overlineY = -body.ascent - sizePx * 0.14f
        val degreeWidth = degree?.width ?: 0f
        val degreeAscent = degree?.let { it.height + sizePx * 0.05f } ?: 0f
        val ascent = body.ascent + sizePx * 0.24f + degreeAscent * 0.4f
        val descent = max(body.descent, sizePx * 0.12f)
        val width = body.width + radicalWidth + degreeWidth + sizePx * 0.08f
        val radicalPaint = strokePaint(sizePx * 0.055f)
        val vinculumPaint = strokePaint(sizePx * 0.05f)
        return MathBox(width, ascent, descent) { canvas, x, y ->
            val topY = y + overlineY
            val path = Path().apply {
                moveTo(x + degreeWidth + radicalWidth * 0.42f, topY + sizePx * 0.18f)
                lineTo(x + degreeWidth + radicalWidth * 0.62f, topY - sizePx * 0.06f)
                lineTo(x + degreeWidth + radicalWidth * 0.78f, topY)
                lineTo(x + degreeWidth + radicalWidth, topY)
            }
            canvas.drawPath(path, radicalPaint)
            canvas.drawLine(
                x + degreeWidth + radicalWidth, topY,
                x + width - sizePx * 0.02f, topY, vinculumPaint,
            )
            body.draw(canvas, x + degreeWidth + radicalWidth + sizePx * 0.04f, y)
            degree?.draw(canvas, x, y - sizePx * 0.55f)
        }
    }

    private fun bigOpBox(node: LatexAstParser.BigOp, scale: Float): MathBox {
        val sizePx = basePx * scale
        val opScale = if (display) 1.45f else 1.05f
        val glyph = box(LatexAstParser.Symbol(node.operator), scale * opScale, upright = true)
        val lower = node.lower?.let { box(it, scale * scriptScale) }
        val upper = node.upper?.let { box(it, scale * scriptScale) }
        if (lower == null && upper == null) return glyph
        if (!display) {
            var width = glyph.width
            var ascent = glyph.ascent
            var descent = glyph.descent
            val supShift = sizePx * 0.5f
            val subShift = sizePx * 0.2f
            upper?.let {
                width = max(width, glyph.width + it.width)
                ascent = max(ascent, supShift + it.ascent)
            }
            lower?.let {
                width = max(width, glyph.width + it.width)
                descent = max(descent, subShift + it.descent)
            }
            return MathBox(width, ascent, descent) { canvas, x, y ->
                glyph.draw(canvas, x, y)
                upper?.draw(canvas, x + glyph.width, y - supShift)
                lower?.draw(canvas, x + glyph.width, y + subShift)
            }
        }
        val gap = sizePx * 0.08f
        val width = listOfNotNull(glyph.width, lower?.width, upper?.width).max()
        val ascent = glyph.ascent + (upper?.let { it.height + gap } ?: 0f)
        val descent = glyph.descent + (lower?.let { it.height + gap } ?: 0f)
        return MathBox(width, ascent, descent) { canvas, x, y ->
            upper?.draw(canvas, x + (width - upper.width) / 2f, y - glyph.ascent - gap - upper.descent)
            glyph.draw(canvas, x + (width - glyph.width) / 2f, y)
            lower?.draw(canvas, x + (width - lower.width) / 2f, y + glyph.descent + gap + lower.ascent)
        }
    }

    private fun accentBox(
        node: LatexAstParser.Accent,
        scale: Float,
        upright: Boolean,
        bold: Boolean,
    ): MathBox {
        val sizePx = basePx * scale
        val body = box(node.body, scale, upright, bold)
        val accentHeight = sizePx * 0.24f
        val ascent = body.ascent + accentHeight
        val width = max(body.width, sizePx * 0.5f)
        val paint = strokePaint(sizePx * 0.05f)
        val dotPaint = Paint().apply {
            isAntiAlias = true
            color = this@CanvasMathEngine.color
            style = Paint.Style.FILL
        }
        return MathBox(width, ascent, body.descent) { canvas, x, y ->
            body.draw(canvas, x + (width - body.width) / 2f, y)
            val topY = y - body.ascent - accentHeight * 0.55f
            val left = x + (width - body.width * 0.8f) / 2f
            val right = left + body.width * 0.8f
            val center = (left + right) / 2f
            when (node.accent) {
                "bar", "overline" -> canvas.drawLine(left, topY, right, topY, paint)
                "vec", "overrightarrow" -> {
                    canvas.drawLine(left, topY, right, topY, paint)
                    canvas.drawLine(right - sizePx * 0.12f, topY - sizePx * 0.07f, right, topY, paint)
                    canvas.drawLine(right - sizePx * 0.12f, topY + sizePx * 0.07f, right, topY, paint)
                }
                "dot" -> canvas.drawCircle(center, topY, sizePx * 0.04f, dotPaint)
                "ddot" -> {
                    canvas.drawCircle(center - sizePx * 0.09f, topY, sizePx * 0.04f, dotPaint)
                    canvas.drawCircle(center + sizePx * 0.09f, topY, sizePx * 0.04f, dotPaint)
                }
                "tilde", "widetilde" -> {
                    val wave = Path().apply {
                        moveTo(left, topY + sizePx * 0.04f)
                        cubicTo(
                            left + (right - left) * 0.3f, topY - sizePx * 0.05f,
                            left + (right - left) * 0.7f, topY + sizePx * 0.09f,
                            right, topY,
                        )
                    }
                    canvas.drawPath(wave, paint)
                }
                "acute" -> canvas.drawLine(
                    center - sizePx * 0.05f, topY + sizePx * 0.03f,
                    center + sizePx * 0.04f, topY - sizePx * 0.06f, paint,
                )
                "grave" -> canvas.drawLine(
                    center + sizePx * 0.05f, topY + sizePx * 0.03f,
                    center - sizePx * 0.04f, topY - sizePx * 0.06f, paint,
                )
                "check" -> {
                    val v = Path().apply {
                        moveTo(center - sizePx * 0.07f, topY - sizePx * 0.05f)
                        lineTo(center, topY + sizePx * 0.03f)
                        lineTo(center + sizePx * 0.07f, topY - sizePx * 0.05f)
                    }
                    canvas.drawPath(v, paint)
                }
                "breve" -> {
                    val v = Path().apply {
                        moveTo(center - sizePx * 0.08f, topY - sizePx * 0.03f)
                        cubicTo(
                            center - sizePx * 0.04f, topY + sizePx * 0.06f,
                            center + sizePx * 0.04f, topY + sizePx * 0.06f,
                            center + sizePx * 0.08f, topY - sizePx * 0.03f,
                        )
                    }
                    canvas.drawPath(v, paint)
                }
                else -> {
                    val v = Path().apply {
                        moveTo(center - sizePx * 0.09f, topY + sizePx * 0.05f)
                        lineTo(center, topY - sizePx * 0.05f)
                        lineTo(center + sizePx * 0.09f, topY + sizePx * 0.05f)
                    }
                    canvas.drawPath(v, paint)
                }
            }
        }
    }

    private fun delimBox(
        node: LatexAstParser.Delim,
        scale: Float,
        upright: Boolean,
        bold: Boolean,
    ): MathBox {
        val body = box(node.body, scale, upright, bold)
        val sizePx = basePx * scale
        val left = if (node.left.isBlank()) null else bracketBox(node.left, body.height, sizePx, bold)
        val right = if (node.right.isBlank()) null else bracketBox(node.right, body.height, sizePx, bold)
        val totalHeight = listOfNotNull(body.height, left?.height, right?.height).max()
        val ascent = (totalHeight - body.height) / 2f + body.ascent
        val descent = totalHeight - ascent
        val gap = sizePx * 0.04f
        val width = (left?.width ?: 0f) + body.width + (right?.width ?: 0f) +
            (if (left != null) gap else 0f) + (if (right != null) gap else 0f)
        return MathBox(width, ascent, descent) { canvas, x, y ->
            var cursor = x
            left?.let {
                val top = y - ascent + (totalHeight - it.height) / 2f
                it.draw(canvas, cursor, top + it.ascent)
                cursor += it.width + gap
            }
            body.draw(canvas, cursor, y)
            cursor += body.width + gap
            right?.let {
                val top = y - ascent + (totalHeight - it.height) / 2f
                it.draw(canvas, cursor, top + it.ascent)
            }
        }
    }

    /** 定界符按内容高度等比放大(最多 6 倍),矩阵/分式括号才不会矮一截 */
    private fun bracketBox(glyph: String, contentHeight: Float, sizePx: Float, bold: Boolean): MathBox {
        var size = sizePx
        var box = measureText(glyph, size, bold = bold, serif = true)
        val target = contentHeight + sizePx * 0.24f
        if (box.height > 0f && box.height < target) {
            size = (size * target / box.height).coerceAtMost(sizePx * 6f)
            box = measureText(glyph, size, bold = bold, serif = true)
        }
        return box
    }

    private fun matrixBox(node: LatexAstParser.Matrix, scale: Float): MathBox {
        val sizePx = basePx * scale
        val cellGap = sizePx * 0.75f
        val rowGap = sizePx * 0.3f
        val cells = node.rows.map { row -> row.map { box(it, scale) } }
        if (cells.isEmpty()) return emptyBox()
        val columns = cells.maxOf { it.size }.coerceAtLeast(1)
        val colWidths = FloatArray(columns)
        cells.forEach { row -> row.forEachIndexed { index, cell -> colWidths[index] = max(colWidths[index], cell.width) } }
        val rowAscents = FloatArray(cells.size)
        val rowDescents = FloatArray(cells.size)
        cells.forEachIndexed { r, row ->
            row.forEach { cell ->
                rowAscents[r] = max(rowAscents[r], cell.ascent)
                rowDescents[r] = max(rowDescents[r], cell.descent)
            }
        }
        val contentWidth = colWidths.sum() + cellGap * (columns - 1).coerceAtLeast(0)
        val contentHeight = rowAscents.indices.sumOf {
            (rowAscents[it] + rowDescents[it] + rowGap).toDouble()
        }.toFloat() - rowGap

        val env = node.env.lowercase()
        val leftBracket = when {
            env.contains("pmatrix") || env.contains("cases") -> if (env.contains("cases")) "{" else "("
            env.contains("bmatrix") -> "["
            env.contains("vmatrix") -> "|"
            else -> ""
        }
        val rightBracket = when {
            env.contains("pmatrix") -> ")"
            env.contains("bmatrix") -> "]"
            env.contains("vmatrix") -> if (env.contains("vmatrix") && node.env.contains("Vmatrix")) "‖" else "|"
            else -> ""
        }
        val left = if (leftBracket.isBlank()) null else bracketBox(leftBracket, contentHeight, sizePx, false)
        val right = if (rightBracket.isBlank()) null else bracketBox(rightBracket, contentHeight, sizePx, false)
        val gap = sizePx * 0.12f
        val width = (left?.width ?: 0f) + contentWidth + (right?.width ?: 0f) +
            (if (left != null) gap else 0f) + (if (right != null) gap else 0f)
        val totalHeight = max(contentHeight, max(left?.height ?: 0f, right?.height ?: 0f))
        val ascent = (totalHeight - contentHeight) / 2f + (rowAscents.firstOrNull() ?: 0f)
        val descent = totalHeight - ascent
        return MathBox(width, ascent, descent) { canvas, x, y ->
            var cursor = x
            left?.let {
                it.draw(canvas, cursor, y - ascent + (totalHeight - it.height) / 2f + it.ascent)
                cursor += it.width + gap
            }
            var rowBase = y - ascent + (totalHeight - contentHeight) / 2f + (rowAscents.firstOrNull() ?: 0f)
            cells.forEachIndexed { r, row ->
                var cellX = cursor
                row.forEachIndexed { c, cell ->
                    cell.draw(canvas, cellX, rowBase)
                    cellX += colWidths[c] + cellGap
                }
                rowBase += rowAscents[r] + rowDescents[r] + rowGap
            }
            cursor += contentWidth + gap
            right?.let {
                it.draw(canvas, cursor, y - ascent + (totalHeight - it.height) / 2f + it.ascent)
            }
        }
    }
}
