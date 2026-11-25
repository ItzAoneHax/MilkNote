package com.milknote.app.ui.agent.media

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** 幻灯片内嵌的一张图片(路径为包内绝对路径,如 `ppt/media/image1.png`)。 */
data class PptxMedia(val path: String, val bytes: ByteArray)

/**
 * 一页幻灯片。
 *
 * @param index 1 基页序(按 presentation.xml 的放映顺序,不是文件名序号)
 * @param title 标题占位符文本;无标题占位符时取第一个文本框的首行
 * @param bullets 正文段落(每段已合并成单行,不含标题)
 * @param tables 表格:行 → 单元格 → 单元格文本
 * @param notes 演讲者备注
 * @param images 该页引用的图片(已过滤超大/矢量等不可解码格式)
 */
data class PptxSlide(
    val index: Int,
    val title: String,
    val bullets: List<String>,
    val tables: List<List<List<String>>> = emptyList(),
    val notes: String = "",
    val images: List<PptxMedia> = emptyList(),
) {
    /** 供模型阅读的纯文本形态(页码 + 标题 + 要点 + 表格 Markdown + 备注) */
    fun toPlainText(): String = buildString {
        append("第 ").append(index).append(" 页")
        if (title.isNotBlank()) append('\n').append("标题: ").append(title)
        if (bullets.isNotEmpty()) {
            append('\n')
            bullets.forEachIndexed { i, bullet ->
                if (i > 0) append('\n')
                append("• ").append(bullet)
            }
        }
        tables.forEach { table ->
            if (table.isEmpty()) return@forEach
            append('\n')
            val columns = table.maxOf { it.size }
            table.forEachIndexed { rowIndex, row ->
                append('|')
                for (col in 0 until columns) {
                    append(' ').append(row.getOrElse(col) { "" }.ifBlank { " " }).append(" |")
                }
                append('\n')
                if (rowIndex == 0) {
                    append('|')
                    repeat(columns) { append(" --- |") }
                    append('\n')
                }
            }
        }
        // 表格循环会在末尾留下换行;备注紧接时收紧一行
        while (isNotEmpty() && last() == '\n') deleteCharAt(length - 1)
        if (notes.isNotBlank()) append('\n').append("演讲备注: ").append(notes)
    }
}

/** 一份演示文稿(解析失败时 [slides] 为空并给出 [warning])。 */
data class PptxDocument(val slides: List<PptxSlide>, val warning: String? = null)

/**
 * `.pptx` 解析(纯 Kotlin + JDK:zip + JAXP DOM),JVM 单测可直接跑,不需要 Android 框架。
 *
 * 安全/健壮性:
 * - 解压总量与单条目都有上限,拒绝 zip 炸弹;只读取 `ppt/` 前缀条目。
 * - XML 解析禁用 DOCTYPE 与外部实体(Android 的 Expat 会忽略部分 JAXP 特性,统一 `runCatching`)。
 * - 任何一步失败都退化为「更少信息」而不是抛异常,调用方永远拿到可展示的 [PptxDocument]。
 * - 旧版二进制 `.ppt`(OLE2)走 [parseLegacyPpt] 记录流扫描,尽力提取文字。
 */
object PptxParser {

    private const val MAX_ENTRY_BYTES = 20L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 80L * 1024 * 1024
    private const val MAX_MEDIA_BYTES = 10L * 1024 * 1024
    private const val MAX_SLIDES = 200
    private const val MAX_TABLE_ROWS = 40
    private const val MAX_TABLE_COLUMNS = 12
    private const val MAX_TEXT_RECORD_BYTES = 512 * 1024

    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private val SLIDE_ENTRY = Regex("^ppt/slides/slide(\\d+)\\.xml$")
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

    private val OLE2_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
    )

    /** PowerPoint 二进制记录:TextCharsAtom(UTF-16LE)/ TextBytesAtom(ISO-8859-1) */
    private const val RECORD_TEXT_CHARS = 0x0FA0
    private const val RECORD_TEXT_BYTES = 0x0FA8
    private const val RECORD_SLIDE_PERSIST = 0x03F3
    private const val RECORD_SLIDE = 0x03EE

    internal const val LEGACY_WARNING = "旧版 .ppt 为二进制格式，建议转为 .pptx 或 PDF 以获得完整排版预览"
    private const val LEGACY_PARTIAL_WARNING =
        "旧版 .ppt 为二进制格式，仅提取到文字；转为 .pptx 或 PDF 可获得完整排版预览"
    private const val EMPTY_WARNING = "未能从该文件中读取到幻灯片内容"
    private const val ZIP_WARNING = "无法读取该演示文稿（文件可能已损坏或不是 PowerPoint 格式）"

    // ---------------------------------------------------------------- 入口

    /** 从磁盘文件解析(优先 `ZipFile`,避免整包读进内存)。 */
    fun parse(file: File): PptxDocument {
        if (!file.isFile) return PptxDocument(emptyList(), ZIP_WARNING)
        if (file.length() <= 0L) return PptxDocument(emptyList(), EMPTY_WARNING)
        if (file.length() > MAX_TOTAL_BYTES) {
            return PptxDocument(
                emptyList(),
                "演示文稿过大（${file.length() / 1024 / 1024} MB），暂不支持预览",
            )
        }
        val head = runCatching {
            file.inputStream().use { input ->
                val buffer = ByteArray(8)
                var read = 0
                while (read < buffer.size) {
                    val n = input.read(buffer, read, buffer.size - read)
                    if (n < 0) break
                    read += n
                }
                buffer.copyOf(read)
            }
        }.getOrNull()
        if (head != null && isOle2(head)) {
            val bytes = runCatching { file.readBytes() }.getOrNull()
                ?: return PptxDocument(emptyList(), ZIP_WARNING)
            return parseLegacyPpt(bytes)
        }
        val entries = runCatching { readZipFile(file) }.getOrNull()
            ?: return PptxDocument(emptyList(), ZIP_WARNING)
        return parseEntries(entries)
    }

    /** 从内存字节解析(自动识别 OLE2 旧版 `.ppt`)。 */
    fun parse(bytes: ByteArray): PptxDocument {
        if (bytes.isEmpty()) return PptxDocument(emptyList(), EMPTY_WARNING)
        if (isOle2(bytes)) return parseLegacyPpt(bytes)
        val entries = runCatching { readZipStream(bytes) }.getOrNull()
            ?: return PptxDocument(emptyList(), ZIP_WARNING)
        return parseEntries(entries)
    }

    // ---------------------------------------------------------------- 旧版 .ppt(OLE2 记录流)

    /**
     * 旧版二进制 `.ppt` 文字提取:扫描 PowerPoint 记录头,取
     * `TextCharsAtom`(0x0FA0,UTF-16LE)与 `TextBytesAtom`(0x0FA8,ISO-8859-1)的文本,
     * 再按 `SlidePersistAtom` / `SlideAtom` 记录位置切分成页。
     *
     * 这是 best-effort:只保证「不崩 + 尽量有字可读」,不还原版式。
     */
    fun parseLegacyPpt(bytes: ByteArray): PptxDocument {
        val runs = scanLegacyTextRuns(bytes)
        if (runs.isEmpty()) {
            return PptxDocument(
                slides = listOf(
                    PptxSlide(
                        index = 1,
                        title = "PowerPoint 演示文稿",
                        bullets = listOf("未能从该文件中提取到文字内容。"),
                    ),
                ),
                warning = LEGACY_WARNING,
            )
        }
        val slides = legacySlides(runs, scanLegacySlideBoundaries(bytes))
        if (slides.isEmpty()) return PptxDocument(emptyList(), LEGACY_WARNING)
        return PptxDocument(slides, LEGACY_PARTIAL_WARNING)
    }

    private class LegacyRun(val offset: Int, val text: String)

    private fun scanLegacyTextRuns(bytes: ByteArray): List<LegacyRun> {
        val runs = ArrayList<LegacyRun>()
        val limit = bytes.size - 8
        var i = 0
        while (i <= limit) {
            if (bytes[i].toInt() == 0 && bytes[i + 1].toInt() == 0) {
                val type = u16(bytes, i + 2)
                if (type == RECORD_TEXT_CHARS || type == RECORD_TEXT_BYTES) {
                    val length = u32(bytes, i + 4)
                    if (length in 2..MAX_TEXT_RECORD_BYTES.toLong() && i + 8 + length <= bytes.size) {
                        val len = length.toInt()
                        val text = if (type == RECORD_TEXT_CHARS) {
                            String(bytes, i + 8, len, Charsets.UTF_16LE)
                        } else {
                            String(bytes, i + 8, len, Charsets.ISO_8859_1)
                        }
                        if (text.isNotBlank()) runs += LegacyRun(i, text)
                        i += 8 + len
                        continue
                    }
                }
            }
            i++
        }
        return runs
    }

    private fun scanLegacySlideBoundaries(bytes: ByteArray): List<Int> {
        val offsets = ArrayList<Int>()
        val limit = bytes.size - 4
        var i = 0
        while (i <= limit) {
            if (bytes[i].toInt() == 0 && bytes[i + 1].toInt() == 0) {
                val type = u16(bytes, i + 2)
                if (type == RECORD_SLIDE_PERSIST || type == RECORD_SLIDE) {
                    if (offsets.isEmpty() || i - offsets.last() > 16) offsets += i
                    i += 4
                    continue
                }
            }
            i++
        }
        return offsets
    }

    private fun legacySlides(runs: List<LegacyRun>, boundaries: List<Int>): List<PptxSlide> {
        val buckets: List<MutableList<LegacyRun>> = if (boundaries.isEmpty()) {
            chunkLegacyRuns(runs)
        } else {
            val grouped = ArrayList<MutableList<LegacyRun>>()
            for (run in runs) {
                var bucket = 0
                for ((i, boundary) in boundaries.withIndex()) {
                    if (run.offset >= boundary) bucket = i else break
                }
                while (grouped.size <= bucket) grouped.add(ArrayList())
                grouped[bucket].add(run)
            }
            grouped.filter { it.isNotEmpty() }
        }
        val slides = ArrayList<PptxSlide>()
        for (bucket in buckets) {
            val lines = ArrayList<String>()
            for (run in bucket) {
                for (line in cleanLegacyText(run.text).split('\n')) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    if (lines.isNotEmpty() && lines.last() == trimmed) continue
                    lines += trimmed
                }
            }
            if (lines.isEmpty()) continue
            slides += PptxSlide(
                index = slides.size + 1,
                title = lines.first().take(120),
                bullets = lines.drop(1).take(40),
            )
            if (slides.size >= MAX_SLIDES) break
        }
        return slides
    }

    private fun chunkLegacyRuns(runs: List<LegacyRun>): List<MutableList<LegacyRun>> {
        val chunks = ArrayList<MutableList<LegacyRun>>()
        var current = ArrayList<LegacyRun>()
        var length = 0
        for (run in runs) {
            if (current.isNotEmpty() && length + run.text.length > 900) {
                chunks += current
                current = ArrayList()
                length = 0
            }
            current += run
            length += run.text.length
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    /** 记录里的换行是 `\r` / `\u000B`,其余控制字符直接丢掉 */
    private fun cleanLegacyText(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when {
                ch == '\r' || ch == '\u000B' || ch == '\u000C' || ch == '\n' -> sb.append('\n')
                ch.code < 0x20 -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun isOle2(bytes: ByteArray): Boolean {
        if (bytes.size < OLE2_MAGIC.size) return false
        for (i in OLE2_MAGIC.indices) if (bytes[i] != OLE2_MAGIC[i]) return false
        return true
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)

    // ---------------------------------------------------------------- 解压

    private fun readZipFile(file: File): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipFile(file).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory || !entry.name.startsWith("ppt/")) continue
                if (entry.size > MAX_ENTRY_BYTES) continue
                if (total >= MAX_TOTAL_BYTES) break
                val bytes = zip.getInputStream(entry).use { readBounded(it, total) } ?: continue
                total += bytes.size
                entries[entry.name] = bytes
            }
        }
        return entries
    }

    private fun readZipStream(bytes: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !entry.name.startsWith("ppt/")) continue
                if (total >= MAX_TOTAL_BYTES) break
                val data = readBounded(zip, total, MAX_ENTRY_BYTES) ?: continue
                total += data.size
                entries[entry.name] = data
            }
        }
        return entries
    }

    /** 有界读取:超过单条目/总量上限返回 null(跳过该条目,不中断整包解析) */
    private fun readBounded(
        input: InputStream,
        usedTotal: Long,
        limit: Long = MAX_ENTRY_BYTES,
    ): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var written = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            written += read
            if (written > limit || usedTotal + written > MAX_TOTAL_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    // ---------------------------------------------------------------- 解析

    private fun parseEntries(entries: Map<String, ByteArray>): PptxDocument {
        if (entries.isEmpty()) return PptxDocument(emptyList(), ZIP_WARNING)
        val presentation = entries["ppt/presentation.xml"]?.let { parseXml(it) }
        val presentationRels = entries["ppt/_rels/presentation.xml.rels"]
            ?.let { parseRelationships(it) }
            .orEmpty()
        val orderedSlides = resolveSlideOrder(presentation, presentationRels, entries)
        if (orderedSlides.isEmpty()) return PptxDocument(emptyList(), EMPTY_WARNING)

        val slides = ArrayList<PptxSlide>(orderedSlides.size)
        for (path in orderedSlides) {
            if (slides.size >= MAX_SLIDES) break
            val bytes = entries[path] ?: continue
            val doc = parseXml(bytes) ?: continue
            val relsPath = "ppt/slides/_rels/${path.substringAfterLast('/')}.rels"
            val slideRels = entries[relsPath]?.let { parseRelationships(it) }.orEmpty()
            val index = slides.size + 1
            slides += buildSlide(doc, index, entries, slideRels)
        }
        if (slides.isEmpty()) return PptxDocument(emptyList(), EMPTY_WARNING)
        return PptxDocument(slides)
    }

    private fun buildSlide(
        doc: Document,
        index: Int,
        entries: Map<String, ByteArray>,
        rels: List<Relationship>,
    ): PptxSlide {
        val root = doc.documentElement ?: return PptxSlide(index, "", emptyList())
        val tree = descendants(root, "spTree").firstOrNull() ?: root

        // (占位符类型 → 段落文本);类型 null 表示普通文本框
        val shapes = ArrayList<Pair<String?, List<String>>>()
        val tables = ArrayList<List<List<String>>>()
        for (child in directChildren(tree)) {
            when (child.localNameSafe()) {
                "sp" -> shapes += placeholderType(child) to paragraphs(child)
                "graphicFrame" -> descendants(child, "tbl").firstOrNull()?.let { table ->
                    readTable(table)?.let { tables += it }
                }
            }
        }

        var title = ""
        var titleShapeIndex = -1
        for ((i, shape) in shapes.withIndex()) {
            val type = shape.first
            if (type == "title" || type == "ctrTitle") {
                title = shape.second.joinToString(" ").trim()
                titleShapeIndex = i
                break
            }
        }

        val bullets = ArrayList<String>()
        for ((i, shape) in shapes.withIndex()) {
            if (i == titleShapeIndex) continue
            bullets += shape.second
        }

        if (title.isEmpty()) {
            // 无标题占位符:把第一个有文字的文本框首行当标题(常见于自绘版式的「大标题文本框」)
            val firstText = shapes.withIndex()
                .filter { it.index != titleShapeIndex }
                .firstOrNull { it.value.second.isNotEmpty() }
            if (firstText != null) {
                title = firstText.value.second.first()
                val consumed = bullets.indexOf(title)
                if (consumed >= 0) bullets.removeAt(consumed)
            }
        }

        return PptxSlide(
            index = index,
            title = title,
            bullets = bullets,
            tables = tables,
            notes = readNotes(entries, rels),
            images = readImages(entries, rels),
        )
    }

    private fun placeholderType(shape: Element): String? {
        val ph = descendants(shape, "ph").firstOrNull() ?: return null
        return ph.getAttribute("type").ifBlank { "obj" }
    }

    /** 形状内正文段落:每个 `<a:p>` 合并全部 `<a:t>` run */
    private fun paragraphs(shape: Element): List<String> {
        val body = descendants(shape, "txBody").firstOrNull() ?: return emptyList()
        return directChildren(body, "p")
            .map { paragraphText(it) }
            .filter { it.isNotBlank() }
    }

    private fun paragraphText(paragraph: Element): String {
        val sb = StringBuilder()
        // 同一段落里的 run 是「同一句话被格式切碎」,直接拼接(空格本来就在 run 里)
        for (node in descendants(paragraph, "t")) {
            sb.append(node.textContent.orEmpty())
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun readTable(table: Element): List<List<String>>? {
        val rows = ArrayList<List<String>>()
        for (tr in directChildren(table, "tr").take(MAX_TABLE_ROWS)) {
            val cells = ArrayList<String>()
            for (tc in directChildren(tr, "tc").take(MAX_TABLE_COLUMNS)) {
                val texts = directChildren(tc, "txBody")
                    .flatMap { body -> directChildren(body, "p").map { paragraphText(it) } }
                    .filter { it.isNotBlank() }
                cells += texts.joinToString(" ")
            }
            if (cells.isNotEmpty()) rows += cells
        }
        if (rows.isEmpty() || rows.all { row -> row.all { it.isBlank() } }) return null
        val columns = rows.maxOf { it.size }
        return rows.map { row -> List(columns) { row.getOrElse(it) { "" } } }
    }

    private fun readNotes(entries: Map<String, ByteArray>, rels: List<Relationship>): String {
        val notesRel = rels.firstOrNull { it.type.endsWith("/notesSlide") } ?: return ""
        val path = resolvePath("ppt/slides", notesRel.target) ?: return ""
        val doc = entries[path]?.let { parseXml(it) } ?: return ""
        val root = doc.documentElement ?: return ""
        val lines = ArrayList<String>()
        for (shape in descendants(root, "sp")) {
            val type = placeholderType(shape)
            // 页码/页眉页脚不是备注正文
            if (type == "sldNum" || type == "ftr" || type == "hdr" || type == "dt") continue
            lines += paragraphs(shape)
        }
        if (lines.isEmpty()) {
            val all = descendants(root, "t").map { it.textContent.orEmpty().trim() }.filter { it.isNotEmpty() }
            return all.joinToString(" ")
        }
        return lines.joinToString("\n").trim()
    }

    private fun readImages(entries: Map<String, ByteArray>, rels: List<Relationship>): List<PptxMedia> {
        val images = LinkedHashMap<String, PptxMedia>()
        for (rel in rels) {
            if (!rel.type.endsWith("/image")) continue
            val path = resolvePath("ppt/slides", rel.target) ?: continue
            val extension = path.substringAfterLast('.', "").lowercase()
            if (extension !in IMAGE_EXTENSIONS) continue
            val bytes = entries[path] ?: continue
            if (bytes.isEmpty() || bytes.size > MAX_MEDIA_BYTES) continue
            images[path] = PptxMedia(path, bytes)
        }
        return images.values.toList()
    }

    // ---------------------------------------------------------------- 包结构

    private data class Relationship(val id: String, val target: String, val type: String)

    private fun resolveSlideOrder(
        presentation: Document?,
        rels: List<Relationship>,
        entries: Map<String, ByteArray>,
    ): List<String> {
        val byId = rels.associateBy { it.id }
        val ordered = ArrayList<String>()
        val root = presentation?.documentElement
        if (root != null) {
            val list = descendants(root, "sldIdLst").firstOrNull()
            for (sldId in list?.let { directChildren(it, "sldId") }.orEmpty()) {
                val relId = relationshipId(sldId) ?: continue
                val target = byId[relId]?.target ?: continue
                val path = resolvePath("ppt", target) ?: continue
                if (entries.containsKey(path) && path !in ordered) ordered += path
            }
        }
        if (ordered.isNotEmpty()) return ordered.take(MAX_SLIDES)
        // 兜底:presentation.xml 缺失时按 slide 文件序号排序
        return entries.keys
            .filter { SLIDE_ENTRY.matches(it) }
            .sortedBy { SLIDE_ENTRY.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }
            .take(MAX_SLIDES)
    }

    /** `<p:sldId r:id="rId2"/>` 的关系 id;命名空间感知优先,失败时按 `xxx:id` 属性兜底 */
    private fun relationshipId(element: Element): String? {
        val namespaced = element.getAttributeNS(REL_NS, "id")
        if (namespaced.isNotBlank()) return namespaced
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.nodeName
            if (name.substringAfter(':', "") == "id" && name.contains(':')) return attr.nodeValue
        }
        return null
    }

    private fun parseRelationships(bytes: ByteArray): List<Relationship> {
        val doc = parseXml(bytes) ?: return emptyList()
        val root = doc.documentElement ?: return emptyList()
        return directChildren(root, "Relationship").mapNotNull { element ->
            val id = element.getAttribute("Id")
            val target = element.getAttribute("Target")
            if (id.isBlank() || target.isBlank()) return@mapNotNull null
            Relationship(id = id, target = target, type = element.getAttribute("Type").orEmpty())
        }
    }

    /** OPC 相对路径 → 包内绝对路径(`../media/x.png` 基于 `ppt/slides` → `ppt/media/x.png`) */
    private fun resolvePath(baseDir: String, target: String): String? {
        val raw = target.trim().substringBefore('#')
        if (raw.isEmpty()) return null
        if (raw.startsWith("/")) return raw.trimStart('/')
        val segments = ArrayList<String>()
        for (part in baseDir.split('/')) {
            if (part.isNotEmpty() && part != ".") segments += part
        }
        for (part in raw.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.size - 1) else return null
                else -> segments += part
            }
        }
        return segments.joinToString("/")
    }

    // ---------------------------------------------------------------- DOM

    private fun parseXml(bytes: ByteArray): Document? {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.isXIncludeAware = false }
        runCatching { factory.isExpandEntityReferences = false }
        val builder = runCatching { factory.newDocumentBuilder() }.getOrNull() ?: return null
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        builder.setErrorHandler(null)
        return runCatching { builder.parse(ByteArrayInputStream(bytes)) }.getOrNull()
    }

    private fun Element.localNameSafe(): String =
        localName ?: nodeName.substringAfter(':')

    private fun directChildren(parent: Element, name: String? = null): List<Element> {
        val out = ArrayList<Element>()
        var node = parent.firstChild
        while (node != null) {
            if (node is Element) {
                if (name == null || node.localNameSafe() == name) out += node
            }
            node = node.nextSibling
        }
        return out
    }

    private fun descendants(root: Element, name: String): List<Element> {
        val out = ArrayList<Element>()
        fun walk(element: Element) {
            var node = element.firstChild
            while (node != null) {
                if (node is Element) {
                    if (node.localNameSafe() == name) out += node
                    walk(node)
                }
                node = node.nextSibling
            }
        }
        walk(root)
        return out
    }
}
