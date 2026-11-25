package com.milknote.app.ui.agent.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import com.milknote.agent.AgentController
import com.milknote.agent.Attachment
import com.milknote.agent.DraftPageImage
import com.milknote.agent.ImageBudget
import com.milknote.app.agent.SnipEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.UUID

/** 文档类型(决定分页方式、渲染器与附件语义)。 */
enum class DocumentKind {
    MARKDOWN,
    TEXT,
    PDF,
    PPTX,
    PPT;

    /** SAF 文件选择器的 MIME 过滤条件 */
    val mimeTypes: List<String>
        get() = when (this) {
            MARKDOWN -> listOf("text/markdown", "text/x-markdown", "text/plain")
            TEXT -> listOf("text/plain")
            PDF -> listOf("application/pdf")
            PPTX -> listOf("application/vnd.openxmlformats-officedocument.presentationml.presentation")
            PPT -> listOf("application/vnd.ms-powerpoint")
        }

    companion object {
        /** 由文件名后缀 + MIME 推断文档类型;不支持时返回 null */
        fun detect(fileName: String, mimeType: String?): DocumentKind? {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            when (extension) {
                "md", "markdown", "mdown", "mkd", "mdx" -> return MARKDOWN
                "txt", "text", "log" -> return TEXT
                "pdf" -> return PDF
                "pptx" -> return PPTX
                "ppt" -> return PPT
            }
            return when (mimeType?.lowercase()?.substringBefore(';')?.trim()) {
                "text/markdown", "text/x-markdown" -> MARKDOWN
                "text/plain" -> TEXT
                "application/pdf" -> PDF
                "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> PPTX
                "application/vnd.ms-powerpoint" -> PPT
                else -> null
            }
        }
    }
}

/** 文档加载/渲染失败(消息面向用户,均为可读中文)。 */
class DocumentLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Markdown / 纯文本分页(纯 Kotlin,JVM 单测覆盖)。
 *
 * 分页优先级:
 * 1. 独占一行的 `---` / `***` / `___`(围栏代码块内的不算)视为硬分页;
 * 2. 当前页已超过 400 字时的 H1/H2 标题另起一页;
 * 3. 单页超过 [maxCharsPerPage] 时在段落/行/句末标点处切开(绝不切在围栏代码块内——代码块整块保留,
 *    即使它本身超过上限,也不会被拦腰截断)。
 */
object MarkdownPaginator {

    private const val DEFAULT_MAX_CHARS = 1800
    private const val HEADING_BREAK_THRESHOLD = 400
    private const val MIN_LIMIT = 300

    fun paginate(markdown: String, maxCharsPerPage: Int = DEFAULT_MAX_CHARS): List<String> {
        val limit = maxCharsPerPage.coerceAtLeast(MIN_LIMIT)
        val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isBlank()) return listOf("")

        val blocks = splitBlocks(normalized)
        if (blocks.isEmpty()) return listOf("")

        // 先按上限切长段落(围栏代码块与原子的标题不切)
        val expanded = ArrayList<Block>(blocks.size)
        for (block in blocks) {
            expanded += if (!block.atomic && !block.isHeading && block.text.length > limit) {
                splitLongText(block.text, limit)
            } else {
                listOf(block)
            }
        }

        val pages = ArrayList<String>()
        val buffer = StringBuilder()
        var lastWasHeading = false

        fun flush() {
            val page = trimBlankLines(buffer.toString())
            if (page.isNotEmpty()) pages += page
            buffer.setLength(0)
            lastWasHeading = false
        }

        for (block in expanded) {
            if (block.pageBreak) {
                flush()
                continue
            }
            if (block.isHeading && buffer.length > HEADING_BREAK_THRESHOLD) flush()

            val separator = when {
                buffer.isEmpty() -> 0
                lastWasHeading -> 1
                else -> 2
            }
            val needsNewPage = buffer.isNotEmpty() && buffer.length + separator + block.text.length > limit
            // 代码块整体搬家:宁可整块挪到下一页,也不截断
            if (needsNewPage) flush()

            if (buffer.isNotEmpty()) buffer.append(if (lastWasHeading) "\n" else "\n\n")
            buffer.append(block.text)
            lastWasHeading = block.isHeading
        }
        flush()

        return if (pages.isEmpty()) listOf("") else pages
    }

    private class Block(
        val text: String,
        val atomic: Boolean = false,
        val isHeading: Boolean = false,
        val pageBreak: Boolean = false,
    )

    /** 切成「段落 / 标题 / 围栏代码块 / 硬分页」四类原子块 */
    private fun splitBlocks(markdown: String): List<Block> {
        val blocks = ArrayList<Block>()
        val lines = markdown.split('\n')
        val paragraph = StringBuilder()
        var fence: String? = null
        val fenceBuffer = StringBuilder()

        fun flushParagraph() {
            val text = trimBlankLines(paragraph.toString())
            if (text.isNotEmpty()) blocks += Block(text)
            paragraph.setLength(0)
        }

        for (line in lines) {
            val trimmed = line.trim()
            val fenceMarker = fenceMarker(trimmed)

            if (fence != null) {
                fenceBuffer.append('\n').append(line)
                if (fenceMarker != null && fenceMarker == fence) {
                    blocks += Block(fenceBuffer.toString(), atomic = true)
                    fenceBuffer.setLength(0)
                    fence = null
                }
                continue
            }
            if (fenceMarker != null) {
                flushParagraph()
                fence = fenceMarker
                fenceBuffer.setLength(0)
                fenceBuffer.append(line)
                continue
            }
            if (isPageBreak(trimmed)) {
                flushParagraph()
                blocks += Block(text = "", pageBreak = true)
                continue
            }
            if (isHeading(trimmed)) {
                flushParagraph()
                blocks += Block(trimmed, isHeading = true)
                continue
            }
            if (trimmed.isEmpty()) {
                flushParagraph()
                continue
            }
            if (paragraph.isNotEmpty()) paragraph.append('\n')
            paragraph.append(line)
        }

        // 未闭合的围栏:当作普通文本收尾,不丢内容
        if (fenceBuffer.isNotEmpty()) {
            blocks += Block(fenceBuffer.toString().trimStart('\n'), atomic = true)
        }
        flushParagraph()
        return blocks
    }

    private fun fenceMarker(trimmed: String): String? = when {
        trimmed.startsWith("```") -> "```"
        trimmed.startsWith("~~~") -> "~~~"
        else -> null
    }

    private fun isPageBreak(trimmed: String): Boolean {
        if (trimmed.length < 3) return false
        if (trimmed.any { it != '-' && it != '*' && it != '_' && it != ' ' && it != '\t' }) return false
        val fenceChars = trimmed.filter { it == '-' || it == '*' || it == '_' }
        if (fenceChars.length < 3) return false
        // 只认「同一种符号重复 ≥3」的独占行
        return fenceChars.all { it == fenceChars.first() }
    }

    private fun isHeading(trimmed: String): Boolean {
        val hashes = trimmed.takeWhile { it == '#' }.length
        return hashes in 1..2 && trimmed.length > hashes && trimmed[hashes] == ' '
    }

    /** 长段落按行/句切分,每片不超过 [limit] */
    private fun splitLongText(text: String, limit: Int): List<Block> {
        val out = ArrayList<Block>()
        var remaining = text
        while (remaining.length > limit) {
            val window = (limit * 0.6f).toInt()
            var cut = remaining.lastIndexOf('\n', limit - 1)
            if (cut < window) cut = sentenceBoundary(remaining, limit, window)
            if (cut < window) cut = limit
            if (cut > 0 && cut < remaining.length && remaining[cut - 1].isHighSurrogate()) cut--
            val head = remaining.substring(0, cut).trimEnd()
            if (head.isNotEmpty()) out += Block(head)
            remaining = remaining.substring(cut).trimStart('\n')
        }
        if (remaining.isNotBlank()) out += Block(remaining)
        return out
    }

    private fun sentenceBoundary(text: String, limit: Int, window: Int): Int {
        val end = minOf(limit, text.length)
        for (i in end - 1 downTo window) {
            if (text[i] in "。！？；.!?;") return i + 1
        }
        return -1
    }

    private fun trimBlankLines(text: String): String {
        var start = 0
        var end = text.length
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        return text.substring(start, end)
    }
}

/**
 * 已加载的文档:预览用(按需渲染某页位图 / 取某页文字)+ 附件组装用(选中页 → 可发送载荷)。
 *
 * 生命周期:内部持有临时缓存文件与 [PdfRenderer];必须在用完后调用 [close]。
 * [renderPageBitmap] 返回的 Bitmap 归本对象缓存所有,**调用方不得 recycle**。
 */
class LoadedDocument internal constructor(
    val fileName: String,
    val kind: DocumentKind,
    val pageCount: Int,
    val warning: String? = null,
    private val markdownPages: List<String> = emptyList(),
    private val slides: List<PptxSlide> = emptyList(),
    /** PDF 专用:已复制到 cache 的临时文件(由本对象负责删除) */
    private val pdfFile: File? = null,
    cacheBytes: Int = 48 * 1024 * 1024,
) : Closeable {

    private val pdfLock = Any()
    private var pdfDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var closed = false

    private val bitmapCache = object : LruCache<String, Bitmap>(cacheBytes.coerceAtLeast(4 * 1024 * 1024)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    // ---------------------------------------------------------------- 预览

    /**
     * 按需渲染一页(0 基):
     * - PDF:白底 + 缩放渲染(渲染器全程串行,防并发崩溃)
     * - PPTX/PPT:走 [SlideRenderer] 画 16:9 幻灯片
     * - MARKDOWN/TEXT:返回 null(由 Markdown 视图直接显示,不需要位图)
     */
    suspend fun renderPageBitmap(pageIndexZeroBased: Int, maxEdgePx: Int = 1200): Bitmap? {
        if (pageIndexZeroBased !in 0 until pageCount || closed) return null
        return when (kind) {
            DocumentKind.MARKDOWN, DocumentKind.TEXT -> null
            DocumentKind.PDF -> withContext(Dispatchers.IO) {
                cached("pdf:$pageIndexZeroBased:$maxEdgePx") { renderPdfPage(pageIndexZeroBased, maxEdgePx) }
            }
            DocumentKind.PPTX, DocumentKind.PPT -> withContext(Dispatchers.Default) {
                cached("slide:$pageIndexZeroBased:$maxEdgePx") { renderSlidePage(pageIndexZeroBased, maxEdgePx) }
            }
        }
    }

    /** 某页可读文字:Markdown 原文 / 幻灯片纯文本(含备注);PDF 无文本层返回 "" */
    fun pageText(pageIndexZeroBased: Int): String = when (kind) {
        DocumentKind.MARKDOWN, DocumentKind.TEXT -> markdownPages.getOrNull(pageIndexZeroBased).orEmpty()
        DocumentKind.PPTX, DocumentKind.PPT -> slides.getOrNull(pageIndexZeroBased)?.toPlainText().orEmpty()
        DocumentKind.PDF -> ""
    }

    /** 某页幻灯片结构(仅 PPTX/PPT);其它类型为 null */
    fun slideOf(pageIndexZeroBased: Int): PptxSlide? = slides.getOrNull(pageIndexZeroBased)

    // ---------------------------------------------------------------- 附件组装

    /**
     * 把选中的 1 基页码组装成可直接交给 [AgentController.addDocumentAttachment] 的载荷。
     * 页码会先收敛到 `1..pageCount` 并去重排序;全部无效时返回 null。
     */
    suspend fun buildAttachmentPayload(selectedPagesOneBased: List<Int>): BuiltDocumentAttachment? {
        if (closed) return null
        val pages = selectedPagesOneBased
            .filter { it in 1..pageCount }
            .distinct()
            .sorted()
        if (pages.isEmpty()) return null

        val baseName = fileName.substringBeforeLast('.', fileName).ifBlank { "文档" }
        val extension = fileName.substringAfterLast('.', "")
        val logicalName = if (extension.isNotEmpty()) fileName else "$baseName.${defaultExtension(kind)}"
        val pageRangeLabel = PageRangeParser.formatChineseLabel(pages)
        val mentionName = mentionNameOf(baseName, pages)

        return when (kind) {
            DocumentKind.MARKDOWN, DocumentKind.TEXT -> {
                val text = pages.mapNotNull { markdownPages.getOrNull(it - 1) }.joinToString("\n\n---\n\n")
                if (text.isBlank()) {
                    null
                } else {
                    BuiltDocumentAttachment(
                        logicalName = logicalName,
                        mentionName = mentionName,
                        pageRangeLabel = pageRangeLabel,
                        source = if (kind == DocumentKind.MARKDOWN) {
                            Attachment.Source.MARKDOWN_DOC
                        } else {
                            Attachment.Source.TEXT_DOC
                        },
                        mimeType = if (kind == DocumentKind.MARKDOWN) "text/markdown" else "text/plain",
                        textContent = text,
                    )
                }
            }
            DocumentKind.PDF, DocumentKind.PPTX, DocumentKind.PPT -> {
                val pageImages = ArrayList<DraftPageImage>(pages.size)
                val texts = ArrayList<String>(pages.size)
                for (page in pages) {
                    val slide = slides.getOrNull(page - 1)
                    val bitmap = renderPageBitmap(page - 1, EXPORT_EDGE_PX) ?: continue
                    val (bytes, jpeg) = SnipEncoder.encode(bitmap)
                    if (bytes.isEmpty()) continue
                    val (width, height) = encodedSize(bitmap.width, bitmap.height)
                    pageImages += DraftPageImage(
                        pageNumber = page,
                        png = bytes,
                        jpeg = jpeg,
                        widthPx = width,
                        heightPx = height,
                        text = slide?.toPlainText().orEmpty(),
                    )
                    slide?.toPlainText()?.takeIf { it.isNotBlank() }?.let { texts += it }
                }
                if (pageImages.isEmpty()) {
                    null
                } else {
                    val cover = pageImages.first()
                    BuiltDocumentAttachment(
                        logicalName = logicalName,
                        mentionName = mentionName,
                        pageRangeLabel = pageRangeLabel,
                        source = if (kind == DocumentKind.PDF) {
                            Attachment.Source.PDF_DOC
                        } else {
                            Attachment.Source.PPT_DOC
                        },
                        mimeType = mimeTypeOf(kind),
                        textContent = texts.joinToString("\n\n"),
                        coverPng = cover.png,
                        coverJpeg = cover.jpeg,
                        widthPx = cover.widthPx,
                        heightPx = cover.heightPx,
                        extraPages = pageImages,
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------- 释放

    override fun close() {
        synchronized(pdfLock) {
            if (closed) return
            closed = true
            runCatching { pdfRenderer?.close() }
            pdfRenderer = null
            runCatching { pdfDescriptor?.close() }
            pdfDescriptor = null
        }
        bitmapCache.evictAll()
        pdfFile?.let { runCatching { it.delete() } }
    }

    // ---------------------------------------------------------------- 内部

    private fun cached(key: String, producer: () -> Bitmap?): Bitmap? {
        bitmapCache.get(key)?.let { if (!it.isRecycled) return it }
        val bitmap = producer() ?: return null
        bitmapCache.put(key, bitmap)
        return bitmap
    }

    private fun renderPdfPage(index: Int, maxEdgePx: Int): Bitmap? = synchronized(pdfLock) {
        if (closed) return null
        val renderer = ensurePdfOpenLocked() ?: return null
        val page = runCatching { renderer.openPage(index) }.getOrNull() ?: return null
        try {
            val longestEdge = maxOf(page.width, page.height).coerceAtLeast(1)
            val edge = maxEdgePx.coerceIn(320, 4000)
            val scale = (edge.toFloat() / longestEdge).coerceAtMost(3f)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                // PdfRenderer 未绘制区域是透明的:先铺白,缩略图贴在任意底色上都干净
                bitmap.eraseColor(Color.WHITE)
                val matrix = Matrix().apply {
                    setScale(width / page.width.toFloat(), height / page.height.toFloat())
                }
                val mode = if (edge > 1400) {
                    PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                } else {
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                }
                page.render(bitmap, null, matrix, mode)
                bitmap
            } catch (e: Throwable) {
                bitmap.recycle()
                throw e
            }
        } finally {
            runCatching { page.close() }
        }
    }

    private fun renderSlidePage(index: Int, maxEdgePx: Int): Bitmap? {
        val slide = slides.getOrNull(index) ?: return null
        val width = maxEdgePx.coerceIn(480, 2048)
        val height = (width * 9 / 16).coerceAtLeast(270)
        return SlideRenderer.render(slide, slides.size, width, height)
    }

    private fun ensurePdfOpenLocked(): PdfRenderer? {
        pdfRenderer?.let { return it }
        if (closed) return null
        val file = pdfFile ?: return null
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (e: Throwable) {
            runCatching { descriptor.close() }
            throw e
        }
        pdfDescriptor = descriptor
        pdfRenderer = renderer
        return renderer
    }

    private fun mentionNameOf(baseName: String, pages: List<Int>): String {
        val clean = baseName.replace("@", "").replace(Regex("\\s+"), "").take(24).ifBlank { "文档" }
        return if (pages.size == pageCount) clean else "$clean·${PageRangeParser.format(pages)}"
    }

    private fun encodedSize(width: Int, height: Int): Pair<Int, Int> {
        val longEdge = maxOf(width, height)
        if (longEdge <= ImageBudget.LONG_EDGE_PX) return width to height
        val scale = ImageBudget.LONG_EDGE_PX.toFloat() / longEdge
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    companion object {
        /** 导出附件用的长边(PDF 页 / 幻灯片) */
        internal const val EXPORT_EDGE_PX = 1568

        private fun defaultExtension(kind: DocumentKind): String = when (kind) {
            DocumentKind.MARKDOWN -> "md"
            DocumentKind.TEXT -> "txt"
            DocumentKind.PDF -> "pdf"
            DocumentKind.PPTX -> "pptx"
            DocumentKind.PPT -> "ppt"
        }

        private fun mimeTypeOf(kind: DocumentKind): String = when (kind) {
            DocumentKind.MARKDOWN -> "text/markdown"
            DocumentKind.TEXT -> "text/plain"
            DocumentKind.PDF -> "application/pdf"
            DocumentKind.PPTX -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            DocumentKind.PPT -> "application/vnd.ms-powerpoint"
        }
    }
}

/** 选中页码组装好的文档附件(可直接投递给 [AgentController.addDocumentAttachment])。 */
data class BuiltDocumentAttachment(
    val logicalName: String,
    val mentionName: String,
    val pageRangeLabel: String,
    val source: Attachment.Source,
    val mimeType: String,
    val textContent: String = "",
    val coverPng: ByteArray = ByteArray(0),
    val coverJpeg: Boolean = false,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val extraPages: List<DraftPageImage> = emptyList(),
)

/** 便捷投递:一行把 [BuiltDocumentAttachment] 加进草稿附件(返回逻辑名,null = 无内容) */
fun AgentController.attachDocument(built: BuiltDocumentAttachment): String? = addDocumentAttachment(
    logicalName = built.logicalName,
    source = built.source,
    mimeType = built.mimeType,
    textContent = built.textContent,
    png = built.coverPng,
    jpeg = built.coverJpeg,
    widthPx = built.widthPx,
    heightPx = built.heightPx,
    extraPages = built.extraPages,
    pageRangeLabel = built.pageRangeLabel,
    mentionName = built.mentionName,
)

/**
 * 统一文档加载器:SAF → 临时缓存文件 → 对应解析器。
 *
 * 为什么要落临时文件:PdfRenderer 需要可随机读写的文件句柄,ZipFile 需要真实 File;
 * 很多 SAF provider(云盘/媒体库)给的是一次性流或不可 seek 的句柄。这里统一复制到
 * `cacheDir/agent_doc_preview/`,由 [LoadedDocument.close] 负责删除。
 */
object DocumentAttachmentLoader {

    /** 单个文档上限(与 SnipEncoder/ImageBudget 的图像预算无关,是纯文件体积护栏) */
    const val MAX_FILE_BYTES = 50L * 1024 * 1024

    private const val TEMP_DIR = "agent_doc_preview"
    private const val MAX_TEXT_PAGES = 400

    /** 支持的 MIME(给 SAF 选择器用) */
    val pickerMimeTypes: Array<String> = DocumentKind.entries
        .flatMap { it.mimeTypes }
        .distinct()
        .toTypedArray()

    /** 加载并解析文档;失败时返回带中文消息的 [Result.failure]。 */
    suspend fun loadFromUri(context: Context, uri: Uri): Result<LoadedDocument> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(load(context.applicationContext, uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: DocumentLoadException) {
                Result.failure(e)
            } catch (e: OutOfMemoryError) {
                Result.failure(DocumentLoadException("文件过大，设备内存不足，无法预览", e))
            } catch (e: Exception) {
                Result.failure(DocumentLoadException("无法读取该文档，请重试或换一个文件", e))
            }
        }

    private fun load(context: Context, uri: Uri): LoadedDocument {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "未命名文档"
        val mimeType = runCatching { resolver.getType(uri) }.getOrNull()
        val kind = DocumentKind.detect(displayName, mimeType)
            ?: throw DocumentLoadException("暂不支持该文件类型，请选择 Markdown / 文本 / PDF / PPT / PPTX")

        val tempDir = File(context.cacheDir, TEMP_DIR).apply { mkdirs() }
        if (!tempDir.isDirectory) throw DocumentLoadException("无法创建临时目录，请检查存储空间")
        val tempFile = File(tempDir, "${UUID.randomUUID()}.${tempExtension(kind, displayName)}")

        var keepTemp = false
        try {
            val input = resolver.openInputStream(uri)
                ?: throw DocumentLoadException("无法打开所选文件，请重新选择")
            val bytes = copyBounded(input, tempFile)
            if (bytes <= 0L) throw DocumentLoadException("所选文件是空文件，无法预览")
            val document = when (kind) {
                DocumentKind.PDF -> openPdf(displayName, tempFile)
                DocumentKind.PPTX, DocumentKind.PPT -> openSlides(displayName, kind, tempFile)
                DocumentKind.MARKDOWN, DocumentKind.TEXT -> openText(displayName, kind, tempFile)
            }
            keepTemp = true
            return document
        } finally {
            if (!keepTemp) runCatching { tempFile.delete() }
        }
    }

    private fun openPdf(displayName: String, file: File): LoadedDocument {
        val pageCount = readPdfPageCount(file)
        if (pageCount <= 0) throw DocumentLoadException("该 PDF 没有可预览的页面")
        return LoadedDocument(
            fileName = displayName,
            kind = DocumentKind.PDF,
            pageCount = pageCount,
            pdfFile = file,
        )
    }

    private fun openSlides(displayName: String, kind: DocumentKind, file: File): LoadedDocument {
        val document = PptxParser.parse(file)
        runCatching { file.delete() }
        val warning = document.warning
        val slides = document.slides.ifEmpty {
            listOf(
                PptxSlide(
                    index = 1,
                    title = displayName.substringBeforeLast('.', displayName),
                    bullets = listOf(warning ?: "未能解析该演示文稿的内容"),
                ),
            )
        }
        return LoadedDocument(
            fileName = displayName,
            kind = kind,
            pageCount = slides.size,
            warning = warning,
            slides = slides,
        )
    }

    private fun openText(displayName: String, kind: DocumentKind, file: File): LoadedDocument {
        val text = decodeText(file.readBytes())
        runCatching { file.delete() }
        val allPages = MarkdownPaginator.paginate(text)
        val pages = if (allPages.size > MAX_TEXT_PAGES) allPages.take(MAX_TEXT_PAGES) else allPages
        val warning = if (allPages.size > MAX_TEXT_PAGES) {
            "文档较长（共 ${allPages.size} 页），仅展示前 $MAX_TEXT_PAGES 页"
        } else {
            null
        }
        return LoadedDocument(
            fileName = displayName,
            kind = kind,
            pageCount = pages.size.coerceAtLeast(1),
            warning = warning,
            markdownPages = pages.ifEmpty { listOf("") },
        )
    }

    private fun readPdfPageCount(file: File): Int {
        val descriptor = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: IOException) {
            throw DocumentLoadException("无法打开该 PDF，请检查文件后重试", e)
        }
        try {
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (e: SecurityException) {
                throw DocumentLoadException("该 PDF 已加密，需要密码才能打开", e)
            } catch (e: IOException) {
                throw DocumentLoadException("无法读取该 PDF：文件已损坏或格式不受支持", e)
            } catch (e: RuntimeException) {
                throw DocumentLoadException("无法读取该 PDF：文件已损坏或格式不受支持", e)
            }
            try {
                return renderer.pageCount
            } finally {
                runCatching { renderer.close() }
            }
        } finally {
            runCatching { descriptor.close() }
        }
    }

    /** 复制到临时文件,超上限立即中断(不把 50MB+ 读进内存) */
    private fun copyBounded(input: InputStream, target: File): Long {
        target.parentFile?.mkdirs()
        var total = 0L
        input.use { source ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_FILE_BYTES) {
                        throw DocumentLoadException(
                            "文件超过 ${MAX_FILE_BYTES / 1024 / 1024} MB，暂不支持预览",
                        )
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        return total
    }

    /** UTF-8 优先;出现大量替换字符时按 GB18030 重解(国内常见 GBK 编码 txt) */
    private fun decodeText(bytes: ByteArray): String {
        val utf8 = decodeStrict(bytes, Charsets.UTF_8)
        val replacementRatio = if (utf8.isEmpty()) 0f else utf8.count { it == '\uFFFD' }.toFloat() / utf8.length
        if (replacementRatio <= 0.02f) return utf8
        val gb = runCatching { decodeStrict(bytes, Charset.forName("GB18030")) }.getOrNull()
        if (gb == null) return utf8
        val gbRatio = if (gb.isEmpty()) 0f else gb.count { it == '\uFFFD' }.toFloat() / gb.length
        return if (gbRatio < replacementRatio) gb else utf8
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { String(bytes, charset) }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun tempExtension(kind: DocumentKind, displayName: String): String {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val allowed = when (kind) {
            DocumentKind.MARKDOWN -> setOf("md", "markdown", "mdown", "mkd", "mdx")
            DocumentKind.TEXT -> setOf("txt", "text", "log")
            DocumentKind.PDF -> setOf("pdf")
            DocumentKind.PPTX -> setOf("pptx")
            DocumentKind.PPT -> setOf("ppt")
        }
        if (extension in allowed) return extension
        return when (kind) {
            DocumentKind.MARKDOWN -> "md"
            DocumentKind.TEXT -> "txt"
            DocumentKind.PDF -> "pdf"
            DocumentKind.PPTX -> "pptx"
            DocumentKind.PPT -> "ppt"
        }
    }
}
