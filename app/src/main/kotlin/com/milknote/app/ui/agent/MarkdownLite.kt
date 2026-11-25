package com.milknote.app.ui.agent

/**
 * Markdown 全量解析(AI 回复与生成文档的唯一解析入口):
 * 标题 H1–H6 / 段落 / 无序·有序·任务列表(含缩进嵌套)/ 引用 / 分隔线 /
 * GFM 表格(列对齐)/ 围栏代码块 / `$$…$$` 与 `\[…\]` 公式块 / `#标签` 行 /
 * AI 生成文档块(`milkdoc` / `document:` / `doc:` / `md:` / `markdown:` / `txt:` / `latex:`)。
 *
 * 行内:`***粗斜***` `**粗**` `__粗__` `*斜*` `_斜_` `~~删除~~` `` `代码` `` `$公式$` `\(公式\)`
 * `[文本](链接)` `![图](链接)` `[[双链]]` `@提及`。
 *
 * 流式友好:任何未闭合的标记按纯文本降级,新 chunk 到达后整体重解析即可。
 * 所有历史类型与字段保持不变(Block.ListItem 新增带默认值的 indent),老渲染路径不受影响。
 */
object MarkdownLite {

    sealed interface Span {
        data class Plain(val text: String) : Span
        data class Bold(val text: String) : Span
        data class Code(val text: String) : Span
        data class Math(val latex: String) : Span
        data class Italic(val text: String) : Span
        data class BoldItalic(val text: String) : Span
        data class Strike(val text: String) : Span
        data class Link(val label: String, val url: String) : Span
        data class Image(val alt: String, val url: String) : Span
        data class Wikilink(val target: String) : Span
        data class Mention(val name: String) : Span
    }

    /** 表格列对齐(GFM 分隔行 `:---` / `:---:` / `---:`) */
    enum class TableAlign { LEFT, CENTER, RIGHT }

    /** 生成文档的格式(决定角标、导出扩展名与高亮) */
    enum class GeneratedDocType { MARKDOWN, TEXT, LATEX }

    sealed interface Block {
        data class Heading(val level: Int, val spans: List<Span>) : Block
        data class ListItem(val order: Int?, val spans: List<Span>, val indent: Int = 0) : Block   // order=null 无序
        data class Paragraph(val spans: List<Span>) : Block
        data class MathBlock(val latex: String) : Block
        data class TagLine(val tags: List<String>) : Block

        data class Table(
            val headers: List<List<Span>>,
            val alignments: List<TableAlign>,
            val rows: List<List<List<Span>>>,
        ) : Block

        data class CodeBlock(val language: String, val code: String) : Block

        data class Quote(val blocks: List<Block>) : Block

        data object HorizontalRule : Block

        data class TaskItem(val checked: Boolean, val indent: Int, val spans: List<Span>) : Block

        data class GeneratedDocument(
            val title: String,
            val type: GeneratedDocType,
            val content: String,
        ) : Block
    }

    fun parse(text: String): List<Block> {
        val blocks = ArrayList<Block>()
        val lines = text.split('\n')
        var i = 0
        val para = StringBuilder()

        fun emitBracketText(chunk: String) {
            // 行内成对 \[ … \] 提取为公式块(与 $$ 同一容错口径)
            val parts = chunk.split("\\[", "\\]")
            if (parts.size >= 3) {
                parts.forEachIndexed { idx, seg ->
                    val s2 = seg.trim()
                    if (s2.isEmpty()) return@forEachIndexed
                    if (idx % 2 == 1) blocks.add(Block.MathBlock(s2))
                    else blocks.add(Block.Paragraph(parseInline(s2)))
                }
                return
            }
            blocks.add(Block.Paragraph(parseInline(chunk)))
        }

        fun emitParagraphText(chunk: String) {
            // 行内 $$…$$ 提取为公式块(容错:模型未按块级换行)
            val parts = chunk.split("$$")
            if (parts.size >= 3) {
                parts.forEachIndexed { idx, seg ->
                    val s2 = seg.trim()
                    if (s2.isEmpty()) return@forEachIndexed
                    if (idx % 2 == 1) blocks.add(Block.MathBlock(s2)) else emitBracketText(s2)
                }
                return
            }
            emitBracketText(chunk)
        }

        fun flushPara() {
            val t = para.toString().trim()
            para.setLength(0)
            if (t.isEmpty()) return
            emitParagraphText(t)
        }

        while (i < lines.size) {
            val line = lines[i].trimEnd()

            // 围栏代码块 / 生成文档块(``` 或 ~~~,长度 ≥3,内层围栏智能配对)
            val fence = fenceOpenPattern.matchEntire(line.trim())
            if (fence != null) {
                flushPara()
                i = parseFence(lines, i, fence, blocks)
                continue
            }

            // $$ 公式块(可跨行;未闭合则吞到结尾,流式下半段补全)
            if (line.trimStart().startsWith("$$")) {
                flushPara()
                val sb = StringBuilder(line.trimStart().removePrefix("$$").trim())
                if (sb.isNotEmpty() && sb.endsWith("$$")) {
                    blocks.add(Block.MathBlock(sb.removeSuffix("$$").toString().trim()))
                    i++
                    continue
                }
                i++
                while (i < lines.size) {
                    if (lines[i].trimEnd().endsWith("$$")) {
                        sb.append('\n').append(lines[i].trimEnd().removeSuffix("$$").trim())
                        i++
                        break
                    }
                    sb.append('\n').append(lines[i])
                    i++
                }
                blocks.add(Block.MathBlock(sb.toString().trim()))
                continue
            }

            // \[ … \] 公式块(单行或多行;未闭合同样吞到结尾)
            if (line.trimStart().startsWith("\\[")) {
                flushPara()
                val first = line.trimStart().removePrefix("\\[")
                i++
                if (first.contains("\\]")) {
                    blocks.add(Block.MathBlock(first.substringBefore("\\]").trim()))
                    continue
                }
                val sb = StringBuilder(first.trim())
                while (i < lines.size) {
                    val cur = lines[i]
                    if (cur.contains("\\]")) {
                        sb.append('\n').append(cur.substringBefore("\\]").trim())
                        i++
                        break
                    }
                    sb.append('\n').append(cur)
                    i++
                }
                blocks.add(Block.MathBlock(sb.toString().trim()))
                continue
            }

            // 标题
            val heading = headingPattern.matchEntire(line)
            if (heading != null) {
                flushPara()
                blocks.add(Block.Heading(heading.groupValues[1].length, parseInline(heading.groupValues[2].trim())))
                i++
                continue
            }

            // 标签行(整行只含 #tag 序列;标题已在上面分流,不会混淆)
            val tokens = line.trim().split(Regex("\\s+"))
            val tags = tokens.filter { it.matches(tagPattern) }
            if (tags.isNotEmpty() && tags.size == tokens.size && line.trim().startsWith("#")) {
                flushPara()
                blocks.add(Block.TagLine(tags.map { it.removePrefix("#") }))
                i++
                continue
            }

            // 分隔线(--- / *** / ___;须在列表与表格之前判定)
            if (isHorizontalRule(line)) {
                flushPara()
                blocks.add(Block.HorizontalRule)
                i++
                continue
            }

            // 引用块:连续 > 行成一整块,剥一层 > 后递归解析
            if (quotePattern.matches(line)) {
                flushPara()
                val inner = ArrayList<String>()
                while (i < lines.size) {
                    val cur = lines[i].trimEnd()
                    val q = quotePattern.matchEntire(cur) ?: break
                    inner.add(q.groupValues[1])
                    i++
                }
                blocks.add(Block.Quote(parse(inner.joinToString("\n"))))
                continue
            }

            // 表格:表头 + 分隔行 + 0..n 数据行
            if (line.contains('|') && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
                flushPara()
                i = parseTable(lines, i, blocks)
                continue
            }

            // 任务列表(- [x] …;判定在普通列表之前)
            val task = taskPattern.matchEntire(line)
            if (task != null) {
                flushPara()
                blocks.add(
                    Block.TaskItem(
                        checked = task.groupValues[2].equals("x", ignoreCase = true),
                        indent = indentOf(task.groupValues[1]),
                        spans = parseInline(task.groupValues[3].trim()),
                    ),
                )
                i++
                continue
            }

            // 列表(- / * / + / •,保留缩进层级)
            val bullet = bulletPattern.matchEntire(line)
            if (bullet != null) {
                flushPara()
                blocks.add(
                    Block.ListItem(
                        order = null,
                        spans = parseInline(bullet.groupValues[2].trim()),
                        indent = indentOf(bullet.groupValues[1]),
                    ),
                )
                i++
                continue
            }
            val ordered = orderedPattern.matchEntire(line)
            if (ordered != null) {
                flushPara()
                blocks.add(
                    Block.ListItem(
                        order = ordered.groupValues[2].toIntOrNull() ?: 1,
                        spans = parseInline(ordered.groupValues[3].trim()),
                        indent = indentOf(ordered.groupValues[1]),
                    ),
                )
                i++
                continue
            }

            // 空行 = 段落分隔
            if (line.isBlank()) {
                flushPara()
                i++
                continue
            }

            para.append(line.trim())
            i++
        }
        flushPara()
        return blocks
    }

    // ---------------- 围栏代码块 / 生成文档 ----------------

    private val fenceOpenPattern = Regex("^(`{3,}|~{3,})\\s*(.*)$")
    private val headingPattern = Regex("^(#{1,6})\\s+(.*)$")
    private val tagPattern = Regex("#[\\w\\u4e00-\\u9fff-]+")
    private val quotePattern = Regex("^\\s{0,3}>\\s?(.*)$")
    private val taskPattern = Regex("^(\\s*)[-*+•]\\s+\\[([ xX])]\\s+(.*)$")
    private val bulletPattern = Regex("^(\\s*)[-*+•]\\s+(.*)$")
    private val orderedPattern = Regex("^(\\s*)(\\d+)[.、)]\\s+(.*)$")
    private val separatorCellPattern = Regex("^:?-+:?$")
    private val attributePattern = Regex("([A-Za-z][\\w-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|(\\S+))")

    /** 缩进层级:两个空格算一层(制表符按 4 空格计) */
    private fun indentOf(leading: String): Int {
        var width = 0
        leading.forEach { width += if (it == '\t') 4 else 1 }
        return (width / 2).coerceIn(0, 8)
    }

    private fun isHorizontalRule(line: String): Boolean {
        val compact = line.trim().replace(" ", "")
        if (compact.length < 3) return false
        return compact.all { it == '-' } || compact.all { it == '*' } || compact.all { it == '_' }
    }

    private fun isTableSeparator(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.contains('|')) return false
        val cells = splitRow(trimmed)
        return cells.isNotEmpty() && cells.all { separatorCellPattern.matches(it.replace(" ", "")) }
    }

    private fun splitRow(line: String): List<String> {
        var body = line.trim()
        if (body.startsWith("|")) body = body.substring(1)
        if (body.endsWith("|") && !body.endsWith("\\|")) body = body.dropLast(1)
        val cells = ArrayList<String>()
        val cell = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length && body[i + 1] == '|' -> {
                    cell.append('|')
                    i += 2
                }
                c == '|' -> {
                    cells.add(cell.toString().trim())
                    cell.setLength(0)
                    i++
                }
                else -> {
                    cell.append(c)
                    i++
                }
            }
        }
        cells.add(cell.toString().trim())
        return cells
    }

    private fun parseTable(lines: List<String>, start: Int, out: MutableList<Block>): Int {
        val headers = splitRow(lines[start]).map { parseInline(it) }
        val separator = splitRow(lines[start + 1])
        val alignments = List(headers.size) { idx ->
            val cell = separator.getOrNull(idx)?.replace(" ", "").orEmpty()
            when {
                cell.startsWith(":") && cell.endsWith(":") -> TableAlign.CENTER
                cell.endsWith(":") -> TableAlign.RIGHT
                else -> TableAlign.LEFT
            }
        }
        val rows = ArrayList<List<List<Span>>>()
        var i = start + 2
        while (i < lines.size) {
            val cur = lines[i].trimEnd()
            val trimmed = cur.trim()
            if (trimmed.isEmpty() || !trimmed.contains('|')) break
            if (isTableSeparator(trimmed)) break
            if (fenceOpenPattern.matches(trimmed)) break
            val cells = splitRow(trimmed)
            rows.add(
                List(headers.size) { idx ->
                    val cell = cells.getOrNull(idx).orEmpty()
                    if (cell.isEmpty()) emptyList() else parseInline(cell)
                },
            )
            i++
        }
        out.add(Block.Table(headers, alignments, rows))
        return i
    }

    /** 围栏块的收尾判定:同字符、长度 ≥ 开启长度、且其后只有空白 */
    private fun closesFence(line: String, ch: Char, minLen: Int): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed[0] != ch) return false
        val run = trimmed.takeWhile { it == ch }.length
        return run >= minLen && trimmed.substring(run).isBlank()
    }

    private fun parseFence(
        lines: List<String>,
        start: Int,
        fence: MatchResult,
        out: MutableList<Block>,
    ): Int {
        val marker = fence.groupValues[1]
        val ch = marker[0]
        val minLen = marker.length
        val info = fence.groupValues[2].trim()
        val docHeader = parseDocumentHeader(info)

        val body = ArrayList<String>()
        var i = start + 1
        if (docHeader == null) {
            while (i < lines.size) {
                if (closesFence(lines[i], ch, minLen)) {
                    i++
                    break
                }
                body.add(lines[i].trimEnd())
                i++
            }
            out.add(Block.CodeBlock(languageOf(info), body.joinToString("\n")))
            return i
        }

        // 生成文档:内层围栏(```/~~~)视为正文,只在平衡状态下才认作文档收尾
        var innerChar: Char? = null
        var innerLen = 0
        while (i < lines.size) {
            val cur = lines[i].trimEnd()
            val trimmed = cur.trim()
            val inner = innerChar
            if (inner == null) {
                if (closesFence(cur, ch, minLen)) {
                    i++
                    break
                }
                if (trimmed.length >= 3 && (trimmed.startsWith("```") || trimmed.startsWith("~~~"))) {
                    innerChar = trimmed[0]
                    innerLen = trimmed.takeWhile { it == trimmed[0] }.length
                }
                body.add(cur)
                i++
            } else {
                body.add(cur)
                if (closesFence(cur, inner, innerLen)) innerChar = null
                i++
            }
        }
        out.add(Block.GeneratedDocument(docHeader.title, docHeader.type, body.joinToString("\n")))
        return i
    }

    private fun languageOf(info: String): String =
        info.trim().takeWhile { !it.isWhitespace() }.trim('.', '{', '}').lowercase()

    private data class DocumentHeader(val title: String, val type: GeneratedDocType)

    private val documentKeywords = setOf(
        "milkdoc", "document", "doc", "markdown", "md", "txt", "text", "latex", "tex",
    )

    /**
     * 识别生成文档围栏头:
     * `milkdoc title="x.md" type="markdown"` / `document:x.md` / `doc:x.txt` /
     * `md:x.md` / `markdown:x.md` / `txt:x.txt` / `latex:x.tex` / `markdown title="x.md"`。
     * 裸语言名(```markdown / ```md)不算文档,仍是普通代码块。
     */
    private fun parseDocumentHeader(info: String): DocumentHeader? {
        if (info.isEmpty()) return null
        val attrs = attributePattern.findAll(info).associate { m ->
            m.groupValues[1].lowercase() to m.groupValues[3].ifEmpty {
                m.groupValues[4].ifEmpty { m.groupValues[5] }
            }
        }
        val head = attributePattern.replace(info, " ").trim()
        if (head.isEmpty()) return null
        val keyword = head.takeWhile { !it.isWhitespace() && it != ':' }.lowercase()
        if (keyword !in documentKeywords) return null
        val positional = head.removePrefix(head.takeWhile { !it.isWhitespace() && it != ':' })
            .trim().removePrefix(":").trim()
        val explicitTitle = attrs["title"] ?: attrs["name"] ?: attrs["file"] ?: attrs["filename"]
        val rawTitle = explicitTitle?.trim().takeIf { !it.isNullOrEmpty() }
            ?: positional.takeIf { it.isNotEmpty() }
        if (rawTitle == null && keyword != "milkdoc") return null

        val type = when (attrs["type"]?.lowercase()) {
            "latex", "tex", "math" -> GeneratedDocType.LATEX
            "text", "txt", "plain", "plaintext" -> GeneratedDocType.TEXT
            "markdown", "md" -> GeneratedDocType.MARKDOWN
            else -> inferDocType(rawTitle, keyword)
        }
        val title = rawTitle ?: when (type) {
            GeneratedDocType.MARKDOWN -> "文档.md"
            GeneratedDocType.TEXT -> "文档.txt"
            GeneratedDocType.LATEX -> "文档.tex"
        }
        return DocumentHeader(title, type)
    }

    private fun inferDocType(title: String?, keyword: String): GeneratedDocType {
        val ext = title?.substringAfterLast('.', "")?.lowercase()
        return when (ext) {
            "tex", "latex", "ltx" -> GeneratedDocType.LATEX
            "txt", "text", "log", "csv" -> GeneratedDocType.TEXT
            "md", "markdown" -> GeneratedDocType.MARKDOWN
            else -> when (keyword) {
                "latex", "tex" -> GeneratedDocType.LATEX
                "txt", "text" -> GeneratedDocType.TEXT
                else -> GeneratedDocType.MARKDOWN
            }
        }
    }

    // ---------------- 行内 ----------------

    /** 行内:`***粗斜***` `**粗**` `__粗__` `*斜*` `_斜_` `~~删除~~` `` `代码` `` `$公式$` `\(公式\)` 链接/双链/提及(未闭合按纯文本) */
    fun parseInline(text: String): List<Span> {
        val spans = ArrayList<Span>()
        val plain = StringBuilder()
        var i = 0

        fun flushPlain() {
            if (plain.isNotEmpty()) {
                spans.add(Span.Plain(plain.toString()))
                plain.setLength(0)
            }
        }

        fun literal(c: Char) {
            plain.append(c)
            i++
        }

        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length && text[i + 1] == '(' -> {
                    val end = text.indexOf("\\)", i + 2)
                    if (end > i + 2) {
                        flushPlain()
                        spans.add(Span.Math(text.substring(i + 2, end).trim()))
                        i = end + 2
                    } else {
                        literal(c)
                    }
                }
                c == '\\' && i + 1 < text.length && text[i + 1] in "\\`*_{}[]()#+-.!>~|$@" -> {
                    literal(text[i + 1])
                    i++
                }
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        flushPlain()
                        spans.add(Span.Code(text.substring(i + 1, end)))
                        i = end + 1
                    } else {
                        literal(c)
                    }
                }
                c == '!' && i + 1 < text.length && text[i + 1] == '[' -> {
                    val close = text.indexOf(']', i + 2)
                    if (close > i + 2 && close + 1 < text.length && text[close + 1] == '(') {
                        val urlEnd = text.indexOf(')', close + 2)
                        if (urlEnd >= close + 2) {
                            flushPlain()
                            spans.add(
                                Span.Image(
                                    alt = text.substring(i + 2, close),
                                    url = text.substring(close + 2, urlEnd).trim(),
                                ),
                            )
                            i = urlEnd + 1
                        } else {
                            literal(c)
                        }
                    } else {
                        literal(c)
                    }
                }
                c == '[' && i + 1 < text.length && text[i + 1] == '[' -> {
                    val end = text.indexOf("]]", i + 2)
                    val target = if (end > i + 2) text.substring(i + 2, end).trim().substringBefore('|').trim() else ""
                    if (target.isNotEmpty()) {
                        flushPlain()
                        spans.add(Span.Wikilink(target))
                        i = end + 2
                    } else {
                        literal(c)
                    }
                }
                c == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    if (close >= i + 1 && close + 1 < text.length && text[close + 1] == '(') {
                        val urlEnd = text.indexOf(')', close + 2)
                        if (urlEnd >= close + 2) {
                            flushPlain()
                            spans.add(
                                Span.Link(
                                    label = text.substring(i + 1, close),
                                    url = text.substring(close + 2, urlEnd).trim(),
                                ),
                            )
                            i = urlEnd + 1
                        } else {
                            literal(c)
                        }
                    } else {
                        literal(c)
                    }
                }
                text.startsWith("***", i) -> {
                    val end = text.indexOf("***", i + 3)
                    if (end > i + 3) {
                        flushPlain()
                        spans.add(Span.BoldItalic(text.substring(i + 3, end)))
                        i = end + 3
                    } else {
                        literal(c)
                    }
                }
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        flushPlain()
                        spans.add(Span.Bold(text.substring(i + 2, end)))
                        i = end + 2
                    } else {
                        literal(c)
                    }
                }
                text.startsWith("__", i) && underscoreBoundary(text, i) -> {
                    val end = text.indexOf("__", i + 2)
                    if (end > i + 2 && (end + 2 >= text.length || !text[end + 2].isLetterOrDigit())) {
                        flushPlain()
                        spans.add(Span.Bold(text.substring(i + 2, end)))
                        i = end + 2
                    } else {
                        literal(c)
                    }
                }
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        flushPlain()
                        spans.add(Span.Strike(text.substring(i + 2, end)))
                        i = end + 2
                    } else {
                        literal(c)
                    }
                }
                c == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i + 1 && !text.startsWith("**", end)) {
                        flushPlain()
                        spans.add(Span.Italic(text.substring(i + 1, end)))
                        i = end + 1
                    } else {
                        literal(c)
                    }
                }
                c == '_' -> {
                    val end = if (underscoreBoundary(text, i)) text.indexOf('_', i + 1) else -1
                    if (end > i + 1 && !text.startsWith("__", end) &&
                        (end + 1 >= text.length || !text[end + 1].isLetterOrDigit())
                    ) {
                        flushPlain()
                        spans.add(Span.Italic(text.substring(i + 1, end)))
                        i = end + 1
                    } else {
                        literal(c)
                    }
                }
                c == '$' && !(i + 1 < text.length && text[i + 1] == '$') -> {
                    val end = text.indexOf('$', i + 1)
                    if (end > i + 1) {
                        flushPlain()
                        spans.add(Span.Math(text.substring(i + 1, end).trim()))
                        i = end + 1
                    } else {
                        literal(c)
                    }
                }
                c == '@' -> {
                    // 邮箱/标识符里的 @ 不算提及;中文正文里紧贴 @ 仍可识别
                    val prev = if (i == 0) null else text[i - 1]
                    val emailLike = prev != null && (prev == '_' || (prev.isLetterOrDigit() && prev.code < 128))
                    var j = i + 1
                    while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '-' || text[j] == '.')) j++
                    val name = if (!emailLike) text.substring(i + 1, j).trimEnd('.') else ""
                    if (name.isNotEmpty()) {
                        flushPlain()
                        spans.add(Span.Mention(name))
                        i += 1 + name.length
                    } else {
                        literal(c)
                    }
                }
                else -> literal(c)
            }
        }
        flushPlain()
        return spans
    }

    /** `_` 强调的边界要求:前一个字符不是字母数字(避免 foo_bar_baz 被吃掉) */
    private fun underscoreBoundary(text: String, index: Int): Boolean =
        index == 0 || !text[index - 1].isLetterOrDigit()
}

/** 从一段 Markdown 里挑出所有生成文档块(会话预览中心/导出用) */
fun MarkdownLite.extractGeneratedDocuments(text: String): List<MarkdownLite.Block.GeneratedDocument> =
    parse(text).filterIsInstance<MarkdownLite.Block.GeneratedDocument>()

/** 行内元素降级为纯文本(旧渲染路径兜底 / 复制 / 摘要) */
fun MarkdownLite.Span.plainText(): String = when (this) {
    is MarkdownLite.Span.Plain -> text
    is MarkdownLite.Span.Bold -> text
    is MarkdownLite.Span.Italic -> text
    is MarkdownLite.Span.BoldItalic -> text
    is MarkdownLite.Span.Strike -> text
    is MarkdownLite.Span.Code -> text
    is MarkdownLite.Span.Math -> latex
    is MarkdownLite.Span.Link -> label
    is MarkdownLite.Span.Image -> alt
    is MarkdownLite.Span.Wikilink -> target
    is MarkdownLite.Span.Mention -> "@$name"
}

/** 块级元素降级为纯文本(旧渲染路径兜底 / 卡片预览 / 复制) */
fun MarkdownLite.Block.plainText(): String = when (this) {
    is MarkdownLite.Block.Heading -> spans.joinToString("") { it.plainText() }
    is MarkdownLite.Block.ListItem -> (order?.let { "$it. " } ?: "• ") + spans.joinToString("") { it.plainText() }
    is MarkdownLite.Block.Paragraph -> spans.joinToString("") { it.plainText() }
    is MarkdownLite.Block.MathBlock -> latex
    is MarkdownLite.Block.TagLine -> tags.joinToString(" ") { "#$it" }
    is MarkdownLite.Block.Table -> (listOf(headers) + rows).joinToString("\n") { row ->
        row.joinToString(" | ") { cell -> cell.joinToString("") { it.plainText() } }
    }
    is MarkdownLite.Block.CodeBlock -> code
    is MarkdownLite.Block.Quote -> blocks.joinToString("\n") { it.plainText() }
    MarkdownLite.Block.HorizontalRule -> "———"
    is MarkdownLite.Block.TaskItem -> (if (checked) "☑ " else "☐ ") + spans.joinToString("") { it.plainText() }
    is MarkdownLite.Block.GeneratedDocument -> content.ifBlank { title }
}
