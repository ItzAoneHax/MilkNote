package com.milknote.app.ui.agent

import com.milknote.app.ui.agent.MarkdownLite.Block
import com.milknote.app.ui.agent.MarkdownLite.Span
import java.util.Locale

/**
 * Markdown + LaTeX → 自包含 HTML(全部离线:脚本与字体都来自 `file:///android_asset/katex/`)。
 *
 * 纯 Kotlin(JVM 单测可覆盖),不依赖 Android/Compose:
 * - 所有正文与属性都做 HTML 转义,链接/图片只放行安全 scheme(防 XSS + `javascript:` 注入)。
 * - 公式以原文 `$$…$$` / `$…$` 输出,由 KaTeX `auto-render` 在 WebView 内排版,天然支持流式重排。
 * - 配色与 MilkNote 亮/暗两套纸墨完全对齐;宽表格/宽公式/长代码块横向滚动,不撑破气泡。
 */
internal object MarkdownHtmlRenderer {

    // ---- MilkNote 纸墨(与 ui/theme/Color.kt 对齐) ----
    private const val LIGHT_PAGE = "#FFFFFF"
    private const val LIGHT_INK = "#18191B"
    private const val LIGHT_MUTED = "#5A5F68"
    private const val LIGHT_LINE = "#E2E4E9"
    private const val LIGHT_SURFACE = "#F6F6F8"
    private const val LIGHT_ACCENT = "#2563EB"

    private const val DARK_PAGE = "#1F2126"
    private const val DARK_INK = "#ECEEF0"
    private const val DARK_MUTED = "#9DA3AE"
    private const val DARK_LINE = "#2A2D34"
    private const val DARK_SURFACE = "#16171A"
    private const val DARK_ACCENT = "#5B92FF"

    private const val KATEX_CSS = "file:///android_asset/katex/katex.min.css"
    private const val KATEX_JS = "file:///android_asset/katex/katex.min.js"
    private const val AUTORENDER_JS = "file:///android_asset/katex/auto-render.min.js"

    private const val MAX_DOC_DEPTH = 3

    /** `$` 行内定界符:写成字面量会触发 Kotlin 字符串模板,统一由字符码构造 */
    private val DOLLAR = 36.toChar().toString()
    private val MATH_FENCE = DOLLAR + DOLLAR

    /** 完整 Markdown + LaTeX 预览页(可滚动 WebView 用) */
    fun renderFullHtml(markdown: String, dark: Boolean, fontScale: Float = 1f): String {
        val scale = if (fontScale.isFinite() && fontScale > 0f) fontScale else 1f
        val body = StringBuilder()
        renderBlocks(MarkdownLite.parse(markdown), body, dark, 0)
        val sb = StringBuilder(4096)
        sb.append("<!DOCTYPE html><html><head>")
        sb.append("<meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no\">")
        sb.append("<link rel=\"stylesheet\" href=\"").append(KATEX_CSS).append("\">")
        sb.append("<style>").append(baseCss(dark, scale)).append("</style>")
        sb.append("</head><body class=\"").append(if (dark) "dark" else "light").append("\">")
        sb.append("<div id=\"content\" class=\"milk\">").append(body).append("</div>")
        sb.append("<script src=\"").append(KATEX_JS).append("\"></script>")
        sb.append("<script src=\"").append(AUTORENDER_JS).append("\"></script>")
        sb.append("<script>").append(typesetScript()).append("</script>")
        sb.append("</body></html>")
        return sb.toString()
    }

    /** 单条公式页:只渲染一个 `$$…$$`,并回报精确高度,供 Compose AndroidView 量高 */
    fun renderMathFormulaHtml(latex: String, displayMode: Boolean, dark: Boolean): String {
        val ink = if (dark) DARK_INK else LIGHT_INK
        val sb = StringBuilder(1024)
        sb.append("<!DOCTYPE html><html><head>")
        sb.append("<meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no\">")
        sb.append("<link rel=\"stylesheet\" href=\"").append(KATEX_CSS).append("\">")
        sb.append("<style>")
        sb.append("html,body{margin:0;padding:0;background:transparent;color:").append(ink).append(";}")
        sb.append("html,body{overflow-x:auto;overflow-y:hidden;-webkit-overflow-scrolling:touch;}")
        sb.append("body{font-size:16px;-webkit-text-size-adjust:100%;}")
        sb.append("#math{display:inline-block;padding:1px 2px;max-width:none;}")
        sb.append("#math .katex{color:").append(ink).append(";}")
        sb.append(".katex-display{margin:0;}")
        sb.append("</style></head><body>")
        sb.append("<span id=\"math\"></span>")
        sb.append("<script src=\"").append(KATEX_JS).append("\"></script>")
        sb.append("<script>")
        sb.append("(function(){")
        sb.append("var latex=").append(jsString(latex)).append(";")
        sb.append("var displayMode=").append(if (displayMode) "true" else "false").append(";")
        sb.append("var el=document.getElementById('math');")
        sb.append("function report(){")
        sb.append("try{var h=Math.ceil(document.body.scrollHeight||el.scrollHeight||0);")
        sb.append("if(window.MilkMathBridge&&window.MilkMathBridge.onHeight){window.MilkMathBridge.onHeight(h);}}catch(e){}")
        sb.append("}")
        sb.append("function run(){")
        sb.append("try{katex.render(latex,el,{displayMode:displayMode,throwOnError:false,strict:false});}")
        sb.append("catch(e){el.textContent=latex;}")
        sb.append("report();setTimeout(report,40);setTimeout(report,180);")
        sb.append("}")
        sb.append("if(document.readyState==='complete'){run();}else{window.addEventListener('load',run);}")
        sb.append("})();")
        sb.append("</script></body></html>")
        return sb.toString()
    }

    // ---------------- 块 ----------------

    private fun renderBlocks(blocks: List<Block>, sb: StringBuilder, dark: Boolean, depth: Int) {
        var i = 0
        while (i < blocks.size) {
            val block = blocks[i]
            if (block is Block.ListItem || block is Block.TaskItem) {
                val entries = ArrayList<ListEntry>()
                while (i < blocks.size) {
                    val cur = blocks[i]
                    if (cur is Block.ListItem) {
                        entries.add(ListEntry(cur.order, null, cur.indent.coerceAtLeast(0), renderSpans(cur.spans, dark)))
                        i++
                    } else if (cur is Block.TaskItem) {
                        entries.add(ListEntry(null, cur.checked, cur.indent.coerceAtLeast(0), renderSpans(cur.spans, dark)))
                        i++
                    } else {
                        break
                    }
                }
                sb.append(buildList(entries))
                continue
            }
            renderBlock(block, sb, dark, depth)
            i++
        }
    }

    private fun renderBlock(block: Block, sb: StringBuilder, dark: Boolean, depth: Int) {
        when (block) {
            is Block.Heading -> {
                val level = block.level.coerceIn(1, 6)
                sb.append("<h").append(level).append(">")
                sb.append(renderSpans(block.spans, dark))
                sb.append("</h").append(level).append(">")
            }
            is Block.Paragraph -> {
                sb.append("<p>").append(renderSpans(block.spans, dark)).append("</p>")
            }
            is Block.MathBlock -> {
                sb.append("<div class=\"math-block\">")
                sb.append(MATH_FENCE).append(escapeHtml(block.latex)).append(MATH_FENCE)
                sb.append("</div>")
            }
            is Block.TagLine -> {
                sb.append("<div class=\"tags\">")
                block.tags.forEach {
                    sb.append("<span class=\"tag\">#").append(escapeHtml(it)).append("</span>")
                }
                sb.append("</div>")
            }
            is Block.Table -> {
                sb.append("<div class=\"table-wrap\"><table><thead><tr>")
                block.headers.forEachIndexed { idx, cell ->
                    sb.append("<th style=\"text-align:").append(alignCss(block.alignments.getOrNull(idx))).append("\">")
                    sb.append(renderSpans(cell, dark)).append("</th>")
                }
                sb.append("</tr></thead><tbody>")
                block.rows.forEach { row ->
                    sb.append("<tr>")
                    block.headers.indices.forEach { idx ->
                        sb.append("<td style=\"text-align:").append(alignCss(block.alignments.getOrNull(idx))).append("\">")
                        sb.append(renderSpans(row.getOrNull(idx).orEmpty(), dark)).append("</td>")
                    }
                    sb.append("</tr>")
                }
                sb.append("</tbody></table></div>")
            }
            is Block.CodeBlock -> {
                sb.append("<div class=\"code\">")
                if (block.language.isNotBlank()) {
                    sb.append("<div class=\"code-head\"><span class=\"lang\">")
                        .append(escapeHtml(block.language)).append("</span></div>")
                }
                sb.append("<pre><code>").append(escapeHtml(block.code)).append("</code></pre></div>")
            }
            is Block.Quote -> {
                sb.append("<blockquote>")
                renderBlocks(block.blocks, sb, dark, depth)
                sb.append("</blockquote>")
            }
            Block.HorizontalRule -> sb.append("<hr>")
            is Block.TaskItem, is Block.ListItem -> {
                // 已在 renderBlocks 的列表聚合里处理;单独出现时退化成单行
                val spans = if (block is Block.TaskItem) block.spans else (block as Block.ListItem).spans
                sb.append("<p>").append(renderSpans(spans, dark)).append("</p>")
            }
            is Block.GeneratedDocument -> {
                sb.append("<section class=\"doc\">")
                sb.append("<div class=\"doc-head\"><span class=\"doc-title\">")
                    .append(escapeHtml(block.title)).append("</span><span class=\"doc-badge\">")
                    .append(docBadge(block.type)).append("</span></div>")
                when (block.type) {
                    MarkdownLite.GeneratedDocType.MARKDOWN ->
                        if (depth < MAX_DOC_DEPTH) {
                            sb.append("<div class=\"doc-body\">")
                            renderBlocks(MarkdownLite.parse(block.content), sb, dark, depth + 1)
                            sb.append("</div>")
                        } else {
                            sb.append("<pre><code>").append(escapeHtml(block.content)).append("</code></pre>")
                        }
                    else -> sb.append("<pre><code>").append(escapeHtml(block.content)).append("</code></pre>")
                }
                sb.append("</section>")
            }
        }
    }

    private class ListEntry(
        val order: Int?,
        val checked: Boolean?,
        val indent: Int,
        val html: String,
    )

    private class ListNode(
        val order: Int?,
        val checked: Boolean?,
        val html: String,
        val children: MutableList<ListNode> = ArrayList(),
    )

    private fun buildList(entries: List<ListEntry>): String {
        val roots = ArrayList<ListNode>()
        val path = ArrayList<Pair<Int, ListNode>>()
        for (entry in entries) {
            val node = ListNode(entry.order, entry.checked, entry.html)
            while (path.isNotEmpty() && path.last().first >= entry.indent) path.removeAt(path.size - 1)
            if (path.isEmpty()) roots.add(node) else path.last().second.children.add(node)
            path.add(entry.indent to node)
        }
        val sb = StringBuilder()
        renderListGroups(roots, sb)
        return sb.toString()
    }

    private fun renderListGroups(nodes: List<ListNode>, sb: StringBuilder) {
        var i = 0
        while (i < nodes.size) {
            val ordered = nodes[i].order != null
            val tag = if (ordered) "ol" else "ul"
            sb.append("<").append(tag).append(">")
            var j = i
            while (j < nodes.size && (nodes[j].order != null) == ordered) {
                val node = nodes[j]
                sb.append("<li>")
                if (node.checked != null) {
                    sb.append("<span class=\"task\">").append(if (node.checked) "☑" else "☐").append("</span>")
                }
                if (node.checked == true) sb.append("<span class=\"strike\">").append(node.html).append("</span>")
                else sb.append(node.html)
                if (node.children.isNotEmpty()) renderListGroups(node.children, sb)
                sb.append("</li>")
                j++
            }
            sb.append("</").append(tag).append(">")
            i = j
        }
    }

    // ---------------- 行内 ----------------

    private fun renderSpans(spans: List<Span>, dark: Boolean): String {
        val sb = StringBuilder()
        spans.forEach { span ->
            when (span) {
                is Span.Plain -> sb.append(escapeHtml(span.text))
                is Span.Bold -> sb.append("<strong>").append(escapeHtml(span.text)).append("</strong>")
                is Span.Italic -> sb.append("<em>").append(escapeHtml(span.text)).append("</em>")
                is Span.BoldItalic -> sb.append("<strong><em>").append(escapeHtml(span.text))
                    .append("</em></strong>")
                is Span.Strike -> sb.append("<del>").append(escapeHtml(span.text)).append("</del>")
                is Span.Code -> sb.append("<code>").append(escapeHtml(span.text)).append("</code>")
                is Span.Math -> sb.append("<span class=\"math-inline\">").append(DOLLAR)
                    .append(escapeHtml(span.latex)).append(DOLLAR).append("</span>")
                is Span.Link -> sb.append("<a href=\"").append(escapeAttribute(sanitizeUrl(span.url)))
                    .append("\">").append(escapeHtml(span.label.ifBlank { span.url })).append("</a>")
                is Span.Image -> sb.append("<img src=\"").append(escapeAttribute(sanitizeUrl(span.url)))
                    .append("\" alt=\"").append(escapeAttribute(span.alt)).append("\">")
                is Span.Wikilink -> sb.append("<span class=\"wikilink\">")
                    .append(escapeHtml(span.target)).append("</span>")
                is Span.Mention -> sb.append("<span class=\"mention\">@")
                    .append(escapeHtml(span.name)).append("</span>")
            }
        }
        return sb.toString()
    }

    private fun alignCss(align: MarkdownLite.TableAlign?): String = when (align) {
        MarkdownLite.TableAlign.CENTER -> "center"
        MarkdownLite.TableAlign.RIGHT -> "right"
        else -> "left"
    }

    private fun docBadge(type: MarkdownLite.GeneratedDocType): String = when (type) {
        MarkdownLite.GeneratedDocType.MARKDOWN -> "MD"
        MarkdownLite.GeneratedDocType.TEXT -> "TXT"
        MarkdownLite.GeneratedDocType.LATEX -> "LaTeX"
    }

    /** 只放行安全 scheme,杜绝 `javascript:` / `data:text/html` 注入 */
    private fun sanitizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val lower = trimmed.lowercase(Locale.US)
        val scheme = lower.substringBefore(':', "")
        val safe = when {
            trimmed.startsWith("//") -> false
            lower.startsWith("#") -> true
            lower.startsWith("/") || lower.startsWith("./") || lower.startsWith("../") -> true
            scheme.isEmpty() -> true
            else -> scheme in SAFE_SCHEMES
        }
        return if (safe) trimmed else "#"
    }

    private val SAFE_SCHEMES = setOf("http", "https", "mailto", "file", "milknote", "note")

    // ---------------- 转义 ----------------

    fun escapeHtml(text: String): String {
        val sb = StringBuilder(text.length + 16)
        text.forEach { c ->
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun escapeAttribute(text: String): String = escapeHtml(text)

    /** 嵌入 `<script>` 的字符串字面量:`<`/`>` 以 \u 形式转义,避免提前闭合脚本标签 */
    private fun jsString(text: String): String {
        val sb = StringBuilder(text.length + 16)
        sb.append('"')
        text.forEach { c ->
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '<' -> sb.append("\\u003c")
                '>' -> sb.append("\\u003e")
                '&' -> sb.append("\\u0026")
                '\u2028' -> sb.append("\\u2028")
                '\u2029' -> sb.append("\\u2029")
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    // ---------------- 样式与脚本 ----------------

    private fun baseCss(dark: Boolean, fontScale: Float): String {
        val page = if (dark) DARK_PAGE else LIGHT_PAGE
        val ink = if (dark) DARK_INK else LIGHT_INK
        val muted = if (dark) DARK_MUTED else LIGHT_MUTED
        val line = if (dark) DARK_LINE else LIGHT_LINE
        val surface = if (dark) DARK_SURFACE else LIGHT_SURFACE
        val accent = if (dark) DARK_ACCENT else LIGHT_ACCENT
        val strong = String.format(Locale.US, "%.2f", fontScale)
        fun px(base: Float) = String.format(Locale.US, "%.2fpx", base * fontScale)
        return StringBuilder(2048).apply {
            append(":root{--ink:").append(ink).append(";--muted:").append(muted)
            append(";--line:").append(line).append(";--surface:").append(surface)
            append(";--page:").append(page).append(";--accent:").append(accent).append(";}")
            append("html{-webkit-text-size-adjust:100%;}")
            append("body{margin:0;padding:2px 0 10px;background:transparent;color:var(--ink);")
            append("font-family:-apple-system,\"Noto Sans SC\",\"PingFang SC\",Roboto,sans-serif;")
            append("font-size:").append(px(14.5f)).append(";line-height:").append(px(23f))
            append(";word-break:break-word;overflow-wrap:anywhere;}")
            append(".milk>*:first-child{margin-top:0;}")
            append(".milk>*:last-child{margin-bottom:0;}")
            append("p{margin:0 0 ")
                .append(String.format(Locale.US, "%.2fpx", 8f * fontScale)).append(";}")
            append("h1,h2,h3,h4,h5,h6{margin:")
                .append(px(14f)).append(" 0 ").append(px(6f))
                .append(";font-weight:600;line-height:1.35;letter-spacing:-0.01em;}")
            append("h1{font-size:").append(px(20f)).append(";}")
            append("h2{font-size:").append(px(18f)).append(";}")
            append("h3{font-size:").append(px(16.5f)).append(";}")
            append("h4{font-size:").append(px(15f)).append(";}")
            append("h5{font-size:").append(px(14f)).append(";}")
            append("h6{font-size:").append(px(13.5f)).append(";color:var(--muted);}")
            append("strong{font-weight:600;}")
            append("em{font-style:italic;}")
            append("del{color:var(--muted);}")
            append("a{color:var(--accent);text-decoration:underline;text-underline-offset:2px;}")
            append("code{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:").append(px(13f))
            append(";background:var(--surface);border-radius:5px;padding:1px 4px;}")
            append("pre{margin:0;padding:").append(px(10f)).append(" ").append(px(12f))
            append(";overflow-x:auto;}")
            append("pre code{background:none;padding:0;font-size:").append(px(12.5f))
            append(";line-height:1.55;white-space:pre;}")
            append(".code{border:1px solid var(--line);border-radius:10px;overflow:hidden;margin:")
                .append(px(8f)).append(" 0;background:var(--surface);}")
            append(".code-head{display:flex;align-items:center;gap:6px;padding:")
                .append(px(6f)).append(" ").append(px(12f)).append(";border-bottom:1px solid var(--line);}")
            append(".lang{font-family:ui-monospace,monospace;font-size:").append(px(10.5f))
            append(";letter-spacing:.06em;text-transform:uppercase;color:var(--muted);}")
            append("blockquote{margin:").append(px(8f)).append(" 0;padding:")
                .append(px(2f)).append(" 0 ").append(px(2f)).append(" ").append(px(12f))
                .append(";border-left:2.5px solid ").append(accent).append("66;color:var(--ink);}")
            append("blockquote p:last-child{margin-bottom:0;}")
            append("ul,ol{margin:0 0 ").append(px(8f)).append(";padding-left:")
                .append(px(22f)).append(";}")
            append("li{margin:").append(px(2f)).append(" 0;}")
            append("ul ul,ol ol,ul ol,ol ul{margin:").append(px(2f)).append(" 0;}")
            append(".task{display:inline-block;margin-right:6px;color:var(--accent);}")
            append(".strike{text-decoration:line-through;color:var(--muted);}")
            append("hr{border:none;border-top:1px solid var(--line);margin:")
                .append(px(12f)).append(" 0;}")
            append(".table-wrap{overflow-x:auto;margin:").append(px(8f)).append(" 0;}")
            append("table{border-collapse:collapse;min-width:100%;font-size:")
                .append(px(13.5f)).append(";}")
            append("th,td{border:1px solid var(--line);padding:").append(px(6f)).append(" ")
                .append(px(10f)).append(";vertical-align:top;}")
            append("th{background:var(--surface);font-weight:600;white-space:nowrap;}")
            append("tbody tr:nth-child(even){background:var(--surface);}")
            append(".tags{display:flex;flex-wrap:wrap;gap:6px;margin:").append(px(6f)).append(" 0;}")
            append(".tag{font-family:ui-monospace,monospace;font-size:").append(px(11f))
            append(";color:var(--accent);background:").append(accent)
            append("1A;border-radius:999px;padding:2px 8px;}")
            append(".mention{color:var(--accent);background:").append(accent)
            append("14;border-radius:6px;padding:0 3px;font-weight:500;}")
            append(".wikilink{color:var(--accent);border-bottom:1px solid ").append(accent)
            append("66;padding-bottom:1px;}")
            append(".math-inline .katex{font-size:").append(px(16f)).append(";}")
            append(".math-block{overflow-x:auto;overflow-y:hidden;margin:")
                .append(px(8f)).append(" 0;padding:").append(px(2f)).append(" 0;}")
            append(".math-block .katex-display{margin:0;}")
            append(".math-block .katex{font-size:").append(px(17f)).append(";}")
            append(".katex{color:var(--ink);}")
            append("img{max-width:100%;height:auto;border-radius:8px;}")
            append(".doc{border:1px solid var(--line);border-radius:12px;margin:")
                .append(px(10f)).append(" 0;overflow:hidden;}")
            append(".doc-head{display:flex;align-items:center;justify-content:space-between;gap:8px;")
                .append("padding:").append(px(8f)).append(" ").append(px(12f))
                .append(";background:var(--surface);border-bottom:1px solid var(--line);}")
            append(".doc-title{font-weight:600;font-size:").append(px(13.5f)).append(";}")
            append(".doc-badge{font-family:ui-monospace,monospace;font-size:").append(px(10.5f))
                .append(";color:var(--accent);background:").append(accent)
                .append("1A;border-radius:999px;padding:1px 7px;letter-spacing:.05em;}")
            append(".doc-body{padding:").append(px(10f)).append(" ").append(px(12f)).append(";}")
            append(".doc-body>*:first-child{margin-top:0;}")
            append(".doc-body>*:last-child{margin-bottom:0;}")
            append("/* font-scale ").append(strong).append(" */")
        }.toString()
    }

    private fun typesetScript(): String = StringBuilder(768).apply {
        append("(function(){")
        append("function report(){try{")
        append("if(window.MilkMathBridge&&window.MilkMathBridge.onHeight){")
        append("window.MilkMathBridge.onHeight(Math.ceil(document.body.scrollHeight||0));}")
        append("}catch(e){}}")
        append("function typeset(){")
        append("var root=document.getElementById('content')||document.body;")
        append("if(typeof renderMathInElement==='function'){")
        append("try{renderMathInElement(root,{")
        append("delimiters:[")
        append("{left:'").append(MATH_FENCE).append("',right:'").append(MATH_FENCE).append("',display:true},")
        append("{left:'\\\\[',right:'\\\\]',display:true},")
        append("{left:'").append(DOLLAR).append("',right:'").append(DOLLAR).append("',display:false},")
        append("{left:'\\\\(',right:'\\\\)',display:false}],")
        append("throwOnError:false,strict:false,")
        append("ignoredTags:['script','noscript','style','textarea','pre','code','option']")
        append("});}catch(e){}}")
        append("report();setTimeout(report,60);setTimeout(report,240);")
        append("}")
        append("if(document.readyState==='complete'){typeset();}")
        append("else{window.addEventListener('load',typeset);}")
        append("if(window.ResizeObserver){try{new ResizeObserver(report).observe(document.body);}catch(e){}}")
        append("})();")
    }.toString()
}
