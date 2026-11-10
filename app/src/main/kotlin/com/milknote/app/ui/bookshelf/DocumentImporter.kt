package com.milknote.app.ui.bookshelf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.milknote.app.agent.PageRenderer
import com.milknote.app.di.ServiceLocator
import com.milknote.ink.model.ImageElement
import com.milknote.notes.model.NotebookMeta
import com.milknote.notes.model.PageSpec
import com.milknote.notes.storage.NotebookRepository
import com.milknote.notes.storage.PageInsertPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** PDF 导入失败(消息面向用户,均为可读中文)。 */
class PdfImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** PDF 导入选项;默认每次新建笔记本,绝不覆盖已有笔记本。 */
data class PdfImportOptions(
    /** 覆盖 PDF 文件名推导出的笔记本名;空白时回退 SAF 文件名。 */
    val notebookName: String? = null,
    /** 与书架封面色板一致(默认批注红,便于和手写笔记本区分)。 */
    val coverColor: String = "#D2483C",
    val purpose: String = "阅读摘录",
    /** 单次导入页数上限;最终按 [PdfImportLimits.MAX_PAGES] 硬上限收敛。 */
    val maxPages: Int = PdfImportLimits.MAX_PAGES,
)

/**
 * PDF 导入器(SAF content Uri → 新建 MilkNote 笔记本)。
 *
 * 行为:
 * 1. 只读打开调用方选择的 `content://` PDF,按 PDF 原始页序逐页读取物理尺寸;
 * 2. 新建笔记本(随机 id 目录),复用初始页 `p0001`,后续页按顺序追加;
 * 3. 每页用 [PdfRenderer] 渲染为笔记本目录内的 PNG(`images/pdf/<pageId>.png`),
 *    并写入只含一张整页 [ImageElement] 的 `<pageId>.img.json`;
 * 4. 每页物理尺寸写入 [NotebookMeta.pageSpecs],返回最新 [NotebookMeta];
 * 5. 加密/损坏/不可读/零页/超大 PDF 抛出 [PdfImportException];任一步失败都会删除
 *    本次新建的笔记本目录,不留下半成品。
 *
 * 线程约定:本类的公开入口会执行 ContentResolver 查询、PdfRenderer 渲染和文件写入,
 * **必须在调用方的 IO 协程中调用**(内部不切换到 Main/Default),避免阻塞 UI。
 */
class DocumentImporter(
    private val contentResolver: ContentResolver,
    private val repository: NotebookRepository,
) {
    constructor(context: Context, repository: NotebookRepository) :
        this(context.applicationContext.contentResolver, repository)

    companion object {
        /** 工厂入口:ServiceLocator 场景下 `DocumentImporter.from(context, repository)`。 */
        fun from(context: Context, repository: NotebookRepository): DocumentImporter =
            DocumentImporter(context, repository)

        /**
         * 静态便捷入口(使用 [ServiceLocator.repository]):
         * `DocumentImporter.importPdf(context, uri, displayName)`。
         * 仍需在调用方的 IO 协程中执行。
         */
        suspend fun importPdf(context: Context, uri: Uri, notebookName: String? = null): NotebookMeta =
            from(context, ServiceLocator.repository)
                .importPdf(uri, PdfImportOptions(notebookName = notebookName))
    }

    /**
     * 导入 PDF 并返回新建笔记本的元数据。
     *
     * @throws PdfImportException 加密/损坏/不可读/零页/超过页数或大小上限/落盘失败。
     */
    suspend fun importPdf(uri: Uri, options: PdfImportOptions = PdfImportOptions()): NotebookMeta {
        val providerName = queryDisplayName(uri)
        val uriNameHint = uri.lastPathSegment
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) }
        val notebookName = PdfImportNaming.notebookName(
            options.notebookName?.takeIf { it.isNotBlank() } ?: providerName,
            uriNameHint,
        )

        val descriptor = try {
            contentResolver.openFileDescriptor(uri, "r")
        } catch (e: SecurityException) {
            throw PdfImportException("无法读取所选文件：没有访问权限，请重新选择。", e)
        } catch (e: FileNotFoundException) {
            throw PdfImportException("无法打开所选 PDF：文件不存在或已被移动，请重新选择。", e)
        } catch (e: IOException) {
            throw PdfImportException("无法打开所选 PDF：读取失败，请检查文件后重试。", e)
        } ?: throw PdfImportException("无法打开所选 PDF：系统未返回文件句柄，请重新选择。")

        try {
            val fileSize = runCatching { descriptor.statSize }.getOrDefault(-1L)
            if (fileSize == 0L) throw PdfImportException("所选 PDF 是空文件，无法导入。")
            if (fileSize > PdfImportLimits.MAX_FILE_BYTES) throw tooLarge(fileSize)

            val renderer = try {
                PdfRenderer(descriptor)
            } catch (e: SecurityException) {
                throw PdfImportException("该 PDF 已加密，需要密码才能打开，暂不支持导入。", e)
            } catch (e: IOException) {
                throw PdfImportException("无法读取该 PDF：文件已损坏、被截断或不是有效的 PDF。", e)
            } catch (e: RuntimeException) {
                throw PdfImportException("无法读取该 PDF：文件已损坏或格式不受支持。", e)
            }

            try {
                return importWithRenderer(renderer, options, notebookName)
            } catch (e: SecurityException) {
                // 页面渲染阶段才暴露密码保护(不同 Android 版本行为不一致)
                throw PdfImportException("该 PDF 已加密，需要密码才能打开，暂不支持导入。", e)
            } finally {
                runCatching { renderer.close() }
            }
        } finally {
            runCatching { descriptor.close() }
        }
    }

    /**
     * 渲染与落盘。任一步失败时删除本次创建的笔记本(成功前不返回)。
     * 预检(页数/尺寸)在创建笔记本之前完成,尽量不产生临时目录。
     */
    private suspend fun importWithRenderer(
        renderer: PdfRenderer,
        options: PdfImportOptions,
        notebookName: String,
    ): NotebookMeta {
        var createdNotebookId: String? = null
        var succeeded = false
        try {
            val pageCount = renderer.pageCount
            if (pageCount <= 0) throw PdfImportException("该 PDF 没有可导入的页面。")
            // 硬上限不随调用方放开,避免一次误导入占用过多存储/时间。
            val maxPages = options.maxPages.coerceIn(1, PdfImportLimits.MAX_PAGES)
            if (pageCount > maxPages) {
                throw PdfImportException("该 PDF 共 $pageCount 页，超过单次导入上限 $maxPages 页。")
            }

            val specs = ArrayList<PageSpec>(pageCount)
            for (index in 0 until pageCount) {
                currentCoroutineContext().ensureActive()
                val (widthPoints, heightPoints) = readPageSize(renderer, index)
                val spec = PdfImportGeometry.pageSpec(widthPoints, heightPoints)
                if (spec.widthMm <= 0f || spec.heightMm <= 0f) {
                    throw PdfImportException(
                        "该 PDF 第 ${index + 1} 页尺寸异常（${widthPoints}×${heightPoints} pt），无法导入。",
                    )
                }
                if (spec.widthMm > PdfImportLimits.MAX_PAGE_MM || spec.heightMm > PdfImportLimits.MAX_PAGE_MM) {
                    throw PdfImportException(
                        "该 PDF 第 ${index + 1} 页尺寸过大（${spec.widthMm.toInt()}×${spec.heightMm.toInt()} mm），暂不支持导入。",
                    )
                }
                specs += spec
            }

            // 始终新建:随机 id 目录,不复用/覆盖任何已有笔记本。
            val firstSpec = specs.first()
            val meta = repository.create(
                name = notebookName,
                coverColor = options.coverColor,
                paper = "blank",
                purpose = options.purpose,
                pageSize = PdfImportGeometry.nearestPageSize(firstSpec),
                orientation = PdfImportGeometry.orientationOf(firstSpec),
                paperColor = "#FFFFFF",
            )
            createdNotebookId = meta.id

            // 复用 create() 的初始页 p0001,其余页按 PDF 页序追加到末尾。
            val pageIds = ArrayList<String>(pageCount)
            pageIds += repository.listPages(meta.id).firstOrNull()?.pageId ?: "p0001"
            while (pageIds.size < pageCount) {
                currentCoroutineContext().ensureActive()
                pageIds += repository.insertPage(meta.id, PageInsertPosition.END)
            }

            val imagesDir = File(repository.notebookDir(meta.id), "images/pdf").apply { mkdirs() }
            if (!imagesDir.isDirectory) throw PdfImportException("无法创建笔记本图片目录，导入失败。")

            val createdAt = System.currentTimeMillis()
            for (index in 0 until pageCount) {
                currentCoroutineContext().ensureActive()
                val pageId = pageIds[index]
                val spec = specs[index]
                val png = File(imagesDir, "$pageId.png")
                renderPageToPng(renderer, index, spec, png)

                val (pageWidthPx, pageHeightPx) =
                    PdfImportGeometry.pageSizePx(spec, PageRenderer.MM_TO_PX)
                val image = ImageElement(
                    id = "pdf-" + UUID.randomUUID().toString().substring(0, 12),
                    src = "images/pdf/${png.name}",
                    x = 0f,
                    y = 0f,
                    width = pageWidthPx,
                    height = pageHeightPx,
                    createdAt = createdAt,
                )
                // 空白页 + 整页图片:用户可继续在图片上书写/批注。
                repository.saveImages(meta.id, pageId, listOf(image))
            }

            // saveImages 走仓库单线程写队列;等 sidecar 落盘后再返回,避免调用方立即打开时图片缺失。
            awaitImageSidecars(meta.id, pageIds)

            val finalMeta = meta.copy(
                pageOrder = pageIds,
                pageSpecs = pageIds.indices.associate { pageIds[it] to specs[it] },
                updatedAt = System.currentTimeMillis(),
            )
            repository.updateMeta(finalMeta)
            succeeded = true
            return finalMeta
        } catch (e: CancellationException) {
            throw e
        } catch (e: PdfImportException) {
            throw e
        } catch (e: SecurityException) {
            throw PdfImportException("该 PDF 已加密，需要密码才能打开，暂不支持导入。", e)
        } catch (e: OutOfMemoryError) {
            throw PdfImportException("导入失败：PDF 页面过大，设备内存不足。", e)
        } catch (e: IOException) {
            throw PdfImportException("导入失败：写入笔记本文件时出错，请检查存储空间后重试。", e)
        } catch (e: Exception) {
            throw PdfImportException("导入 PDF 失败，请重试。", e)
        } finally {
            if (!succeeded) {
                createdNotebookId?.let { id -> runCatching { repository.delete(id) } }
            }
        }
    }

    private fun readPageSize(renderer: PdfRenderer, index: Int): Pair<Int, Int> {
        val page = renderer.openPage(index)
        try {
            return page.width to page.height
        } finally {
            runCatching { page.close() }
        }
    }

    /** 按页面物理尺寸渲染白底 PNG(临时文件 + rename,避免半写文件被读取)。 */
    private fun renderPageToPng(renderer: PdfRenderer, index: Int, spec: PageSpec, target: File) {
        val size = PdfImportGeometry.renderSizePx(spec, mmToPx = PageRenderer.MM_TO_PX)
        val page = renderer.openPage(index)
        try {
            val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            try {
                // PdfRenderer 未绘制区域为透明;先铺白,保证 PNG 在任意纸张底色上不透明。
                bitmap.eraseColor(Color.WHITE)
                val matrix = Matrix().apply {
                    setScale(
                        size.width / page.width.toFloat(),
                        size.height / page.height.toFloat(),
                    )
                }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                writePngAtomically(bitmap, target)
            } finally {
                bitmap.recycle()
            }
        } finally {
            runCatching { page.close() }
        }
    }

    private fun writePngAtomically(bitmap: Bitmap, target: File) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        try {
            FileOutputStream(tmp).buffered().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("PNG 编码失败: ${target.name}")
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /**
     * 仓库 saveImages 为异步单线程写队列;这里做有界等待,确保返回前图片 sidecar 已落盘。
     * 超时视为导入失败,由调用方(importWithRenderer)清理新建笔记本。
     */
    private suspend fun awaitImageSidecars(
        notebookId: String,
        pageIds: List<String>,
        timeoutMs: Long = 15_000L,
    ) {
        val dir = repository.notebookDir(notebookId)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            currentCoroutineContext().ensureActive()
            if (pageIds.all { File(dir, "$it.img.json").isFile }) return
            if (System.currentTimeMillis() >= deadline) {
                throw PdfImportException("保存导入页面失败（写入超时），请重试。")
            }
            delay(25L)
        }
    }

    /** 读取 SAF 提供的显示名;provider 不支持查询时静默回退。 */
    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun tooLarge(bytes: Long): PdfImportException {
        val sizeMb = "%.1f".format(bytes / 1024.0 / 1024.0)
        val limitMb = PdfImportLimits.MAX_FILE_BYTES / 1024 / 1024
        return PdfImportException("所选 PDF 过大（$sizeMb MB），超过导入上限 $limitMb MB。")
    }
}
