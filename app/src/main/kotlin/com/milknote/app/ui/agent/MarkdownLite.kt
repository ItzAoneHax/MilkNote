package com.milknote.app.ui.agent

/**
 * Markdown 子集解析(AGENT-BACKEND §6.3):
 * 支持标题(#..####)/无序与有序列表/加粗/行内代码/$行内公式$/$$公式块$$/#标签 行;
 * 超集语法按纯文本降级(容错,不要求模型 100% 守约)。
 * 流式友好:未闭合的加粗/代码/公式标记按纯文本处理,新 chunk 到达后重解析即可。
 */
object MarkdownLite {

    sealed interface Span {
        data class Plain(val text: String) : Span
        data class Bold(val text: String) : Span
        data class Code(val text: String) : Span
        data class Math(val latex: String) : Span
    }

    sealed interface Block {
        data class Heading(val level: Int, val spans: List<Span>) : Block
        data class ListItem(val order: Int?, val spans: List<Span>) : Block   // order=null 无序
        data class Paragraph(val spans: List<Span>) : Block
        data class MathBlock(val latex: String) : Block
        data class TagLine(val tags: List<String>) : Block
    }

    fun parse(text: String): List<Block> {
        val blocks = ArrayList<Block>()
        val lines = text.split('\n')
        var i = 0
        val para = StringBuilder()

        fun flushPara() {
            val t = para.toString().trim()
            para.setLength(0)
            if (t.isEmpty()) return
            // 行内 $$…$$ 提取为公式块(容错:模型未按块级换行)
            val parts = t.split("$$")
            if (parts.size >= 3) {
                parts.forEachIndexed { idx, seg ->
                    val s2 = seg.trim()
                    if (s2.isEmpty()) return@forEachIndexed
                    if (idx % 2 == 1) blocks.add(Block.MathBlock(s2))
                    else blocks.add(Block.Paragraph(parseInline(s2)))
                }
                return
            }
            blocks.add(Block.Paragraph(parseInline(t)))
        }

        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()

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

            // 标题
            val heading = Regex("^(#{1,6})\\s+(.*)$").matchEntire(line)
            if (heading != null) {
                flushPara()
                blocks.add(Block.Heading(heading.groupValues[1].length, parseInline(heading.groupValues[2])))
                i++
                continue
            }

            // 标签行(整行只含 #tag 序列)
            val tags = line.trim().split(Regex("\\s+")).filter { it.matches(Regex("#[\\w\\u4e00-\\u9fff-]+")) }
            if (tags.isNotEmpty() && tags.size == line.trim().split(Regex("\\s+")).size && line.trim().startsWith("#")) {
                flushPara()
                blocks.add(Block.TagLine(tags.map { it.removePrefix("#") }))
                i++
                continue
            }

            // 列表(- / * / 1.)
            val bullet = Regex("^[-*•]\\s+(.*)$").matchEntire(line.trim())
            if (bullet != null) {
                flushPara()
                blocks.add(Block.ListItem(null, parseInline(bullet.groupValues[1])))
                i++
                continue
            }
            val ordered = Regex("^(\\d+)[.、)]\\s+(.*)$").matchEntire(line.trim())
            if (ordered != null) {
                flushPara()
                blocks.add(Block.ListItem(ordered.groupValues[1].toIntOrNull() ?: 1, parseInline(ordered.groupValues[2])))
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

    /** 行内:**加粗** `代码` $公式$(未闭合按纯文本) */
    fun parseInline(text: String): List<Span> {
        val spans = ArrayList<Span>()
        var i = 0
        val plain = StringBuilder()

        fun flushPlain() {
            if (plain.isNotEmpty()) {
                spans.add(Span.Plain(plain.toString()))
                plain.setLength(0)
            }
        }

        while (i < text.length) {
            val rest = text.substring(i)
            when {
                rest.startsWith("**") -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        flushPlain()
                        spans.add(Span.Bold(text.substring(i + 2, end)))
                        i = end + 2
                    } else {
                        plain.append(text[i]); i++
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        flushPlain()
                        spans.add(Span.Code(text.substring(i + 1, end)))
                        i = end + 1
                    } else {
                        plain.append(text[i]); i++
                    }
                }
                text[i] == '$' && !(i + 1 < text.length && text[i + 1] == '$') -> {
                    val end = text.indexOf('$', i + 1)
                    if (end > i + 1) {
                        flushPlain()
                        spans.add(Span.Math(text.substring(i + 1, end)))
                        i = end + 1
                    } else {
                        plain.append(text[i]); i++
                    }
                }
                else -> {
                    plain.append(text[i]); i++
                }
            }
        }
        flushPlain()
        return spans
    }
}
