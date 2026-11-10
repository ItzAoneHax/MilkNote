package com.milknote.app.ui.writer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.Stroke
import com.milknote.notes.model.PageSpec
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 一次导出实际落盘的文件(MediaStore 或分享缓存)。 */
data class SavedExport(
    val uri: Uri,
    val fileName: String,
    val location: String,
    val mimeType: String,
)

/** 导出:PNG/JPG(单页或多页)/ PDF(合并)/ ZIP(MilkNote .milk 整本备份)。 */
object PageExporter {

    private const val MM_TO_PX = 5.67f
    private const val IMAGE_RELATIVE_PATH = "Pictures/MilkNote"
    private const val DOCUMENT_RELATIVE_PATH = "Documents/MilkNote"
    private const val IMAGE_LOCATION = "图片/MilkNote"
    private const val DOCUMENT_LOCATION = "文档/MilkNote"

    // ---- 兼容旧调用(WriterViewModel 现有导出) ----

    fun savePng(context: Context, bitmap: Bitmap, name: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${name.replace('/', '_')}-${System.currentTimeMillis() % 100000}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MilkNote")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return "保存失败"
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return "已导出 PNG → 图片/MilkNote"
    }

    /** 整本 PDF:页面物理尺寸(1mm = 72/25.4pt),方向生效;批注卡与图片层随页导出 */
    fun savePdf(
        context: Context,
        pages: List<Triple<String, List<Stroke>, List<AnnotationCard>>>,
        paper: String,
        name: String,
        pageSize: String = "A4",
        orientation: String = "portrait",
        imageDir: String? = null,                                    // notebook 目录(ImageElement.src 相对它)
        imagesOf: ((pageId: String) -> List<ImageElement>)? = null,  // 页 → 图片元素(缺省不带图片层)
        mediaOf: ((pageId: String) -> List<MediaElement>)? = null,   // 页 → 音视频元素(静态占位)
        pageSpecOf: ((pageId: String) -> PageSpec)? = null,
    ): String {
        if (pages.isEmpty()) return "没有页面"
        val doc = buildPdf(pages, paper, pageSize, orientation, imageDir, imagesOf, mediaOf, pageSpecOf)
        try {
            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, "${name.replace('/', '_')}-${System.currentTimeMillis() % 100000}.pdf")
                put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/MilkNote")
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: return "保存失败"
            context.contentResolver.openOutputStream(uri)?.use { out: OutputStream -> doc.writeTo(out) }
                ?: return "保存失败"
            return "已导出 PDF(${pages.size} 页) → 文档/MilkNote"
        } finally {
            doc.close()
        }
    }

    // ---- 新导出页使用:明确文件名 + 可分享 Uri ----

    /** JPG/PNG 单页图片;写入图片/MilkNote,失败时回退到应用缓存供分享。 */
    fun saveImageFile(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        format: ExportFormat,
    ): SavedExport? {
        if (!format.isImage) return null
        return writeMedia(
            context = context,
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            fileName = fileName,
            mimeType = format.mimeType,
            relativePath = IMAGE_RELATIVE_PATH,
            location = IMAGE_LOCATION,
        ) { out ->
            val compressFormat =
                if (format == ExportFormat.JPG) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            val quality = if (format == ExportFormat.JPG) 94 else 100
            if (!bitmap.compress(compressFormat, quality, out)) error("图片编码失败")
        }
    }

    /** 合并 PDF;页面尺寸按笔记本元数据,渲染仍走 PageRenderer。 */
    fun savePdfFile(
        context: Context,
        pages: List<Triple<String, List<Stroke>, List<AnnotationCard>>>,
        paper: String,
        fileName: String,
        pageSize: String = "A4",
        orientation: String = "portrait",
        imageDir: String? = null,
        imagesOf: ((pageId: String) -> List<ImageElement>)? = null,
        mediaOf: ((pageId: String) -> List<MediaElement>)? = null,
        pageSpecOf: ((pageId: String) -> PageSpec)? = null,
    ): SavedExport? {
        if (pages.isEmpty()) return null
        val doc = buildPdf(pages, paper, pageSize, orientation, imageDir, imagesOf, mediaOf, pageSpecOf)
        return try {
            writeMedia(
                context = context,
                collection = MediaStore.Files.getContentUri("external"),
                fileName = fileName,
                mimeType = "application/pdf",
                relativePath = DOCUMENT_RELATIVE_PATH,
                location = DOCUMENT_LOCATION,
            ) { out -> doc.writeTo(out) }
        } finally {
            doc.close()
        }
    }

    /**
     * MilkNote 自有 .milk 备份:只读遍历 notebook 目录,打包为 ZIP。
     * ZIP 内根目录为 `<笔记名>.milk/`,内容为 notebook.json、页面 pb、批注/图片 sidecar 与 images 附件。
     * 这是 MilkNote 备份,不声明兼容其它应用的 ZIP 格式。
     */
    fun saveZipBackup(
        context: Context,
        notebookDir: File,
        fileName: String,
        rootDirName: String,
    ): SavedExport? {
        if (!notebookDir.isDirectory) return null
        val files = runCatching {
            notebookDir.walkTopDown()
                .filter { it.isFile && !it.name.endsWith(".tmp") }
                .sortedBy { it.toRelativeString(notebookDir) }
                .toList()
        }.getOrElse { return null }
        if (files.isEmpty()) return null
        val root = rootDirName.trim('/').ifBlank { "MilkNote.milk" }
        return writeMedia(
            context = context,
            collection = MediaStore.Files.getContentUri("external"),
            fileName = fileName,
            mimeType = "application/zip",
            relativePath = DOCUMENT_RELATIVE_PATH,
            location = DOCUMENT_LOCATION,
        ) { out ->
            ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                for (file in files) {
                    val relative = file.toRelativeString(notebookDir).replace('\\', '/')
                    zip.putNextEntry(ZipEntry("$root/$relative"))
                    file.inputStream().buffered().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    // ---- 内部实现 ----

    private fun buildPdf(
        pages: List<Triple<String, List<Stroke>, List<AnnotationCard>>>,
        paper: String,
        pageSize: String,
        orientation: String,
        imageDir: String?,
        imagesOf: ((pageId: String) -> List<ImageElement>)?,
        mediaOf: ((pageId: String) -> List<MediaElement>)?,
        pageSpecOf: ((pageId: String) -> PageSpec)?,
    ): PdfDocument {
        val (wMm, hMm) = com.milknote.notes.model.PAGE_SIZES_MM[pageSize]
            ?: com.milknote.notes.model.PAGE_SIZES_MM.getValue("A4")
        val fallback = if (orientation == "landscape") PageSpec(hMm, wMm, paper) else PageSpec(wMm, hMm, paper)
        val doc = PdfDocument()
        for ((index, triple) in pages.withIndex()) {
            val (pageId, strokes, cards) = triple
            val spec = pageSpecOf?.invoke(pageId) ?: fallback
            val pw = (spec.widthMm / 25.4f * 72).toInt().coerceAtLeast(1)
            val ph = (spec.heightMm / 25.4f * 72).toInt().coerceAtLeast(1)
            val pageWpx = spec.widthMm * MM_TO_PX
            val pageHpx = spec.heightMm * MM_TO_PX
            val pageInfo = PdfDocument.PageInfo.Builder(pw, ph, index + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            val scale = pw / pageWpx
            canvas.scale(scale, scale)
            com.milknote.app.agent.PageRenderer.drawAll(
                canvas,
                com.milknote.app.agent.AgentBridge.PageData(
                    strokes, cards, spec.paper, pageSize, orientation,
                    images = imagesOf?.invoke(pageId) ?: emptyList(),
                    media = mediaOf?.invoke(pageId) ?: emptyList(),
                    imageDir = imageDir, paperColor = spec.paperColor,
                    pageWidthMm = spec.widthMm, pageHeightMm = spec.heightMm,
                ),
                pageWpx, pageHpx, clip = null,
            )
            doc.finishPage(page)
        }
        return doc
    }

    /**
     * 优先写 MediaStore(无需存储权限);插入或写入失败时回退到 cache/export,
     * 通过 Manifest 中的 FileProvider 暴露给系统 Sharesheet。
     */
    private fun writeMedia(
        context: Context,
        collection: Uri,
        fileName: String,
        mimeType: String,
        relativePath: String,
        location: String,
        write: (OutputStream) -> Unit,
    ): SavedExport? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull()
        if (uri != null) {
            try {
                val out = resolver.openOutputStream(uri) ?: error("无法打开导出输出流")
                out.use(write)
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                return SavedExport(uri, fileName, location, mimeType)
            } catch (_: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
            }
        }
        return writeCache(context, fileName, mimeType, write)
    }

    private fun writeCache(
        context: Context,
        fileName: String,
        mimeType: String,
        write: (OutputStream) -> Unit,
    ): SavedExport? = runCatching {
        val root = File(context.cacheDir, "export")
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        runCatching {
            root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
        }
        val session = File(root, "${System.currentTimeMillis()}-${System.nanoTime()}").apply { mkdirs() }
        val file = File(session, fileName.replace('/', '_').replace('\\', '_'))
        file.outputStream().buffered().use(write)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        SavedExport(uri, file.name, "应用缓存（可分享）", mimeType)
    }.getOrNull()
}
