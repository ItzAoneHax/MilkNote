package com.milknote.app.ui.bookshelf

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milknote.app.di.ServiceLocator
import com.milknote.notes.model.NotebookMeta
import com.milknote.notes.storage.MilkArchiveImporter
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BookshelfEvent {
    data class Imported(val notebookId: String) : BookshelfEvent
    data class Message(val text: String) : BookshelfEvent
}

class BookshelfViewModel : ViewModel() {

    private val repo = ServiceLocator.repository

    private val _notebooks = MutableStateFlow<List<NotebookMeta>>(emptyList())
    val notebooks: StateFlow<List<NotebookMeta>> = _notebooks

    /** 当前排序方式;保存在 ViewModel 中,刷新/重进书架后保持。 */
    private val _sortMode = MutableStateFlow(BookshelfSortMode.Updated)
    val sortMode: StateFlow<BookshelfSortMode> = _sortMode
    val events = MutableSharedFlow<BookshelfEvent>(extraBufferCapacity = 4)
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    /** 仓库返回的原始列表(默认按修改时间倒序),按当前排序方式投影到 [notebooks]。 */
    private var loaded: List<NotebookMeta> = emptyList()

    /** 页数缓存(打开书架时刷新;删除笔记本后失效) */
    private val pageCounts = mutableMapOf<String, Int>()

    /** 封面缩略图:每本首页的真实渲染(含笔迹/图片/批注);空白页不入表,卡片回退到点阵封面 */
    private val _covers = MutableStateFlow<Map<String, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap())
    val covers: StateFlow<Map<String, androidx.compose.ui.graphics.ImageBitmap>> = _covers
    private var coverJob: kotlinx.coroutines.Job? = null

    init { refresh() }

    fun refresh() {
        loaded = repo.listNotebooks()
        pageCounts.clear()
        _notebooks.value = sortNotebooks(loaded, _sortMode.value)
        renderCovers(loaded)
    }

    /** 首页缩略图在 Default 线程逐本渲染、逐本发布,书架先出卡片再补封面 */
    private fun renderCovers(list: List<NotebookMeta>) {
        coverJob?.cancel()
        coverJob = viewModelScope.launch(Dispatchers.Default) {
            val next = _covers.value.toMutableMap()
            next.keys.retainAll(list.map { it.id }.toSet())
            _covers.value = next
            for (nb in list) {
                val page = runCatching { repo.listPages(nb.id).firstOrNull() }.getOrNull() ?: continue
                val strokes = repo.loadPage(nb.id, page.pageId)
                val cards = repo.loadAnnotations(nb.id, page.pageId)
                val images = repo.loadImages(nb.id, page.pageId)
                val media = repo.loadMedia(nb.id, page.pageId)
                // 从书写页返回 = 内容可能已变,每次刷新都重渲染;旧图在替换前保持可见,不闪点阵
                if (strokes.isEmpty() && cards.isEmpty() && images.isEmpty() && media.isEmpty()) {
                    _covers.value = _covers.value - nb.id
                    continue
                }
                val spec = nb.pageSpec(page.pageId)
                val bmp = runCatching {
                    com.milknote.app.ui.writer.renderPageThumb(
                        strokes, cards, spec.paper, nb.pageSize, nb.orientation,
                        images = images, media = media,
                        imageDir = repo.notebookDir(nb.id).absolutePath,
                        paperColor = spec.paperColor, pageWidthMm = spec.widthMm, pageHeightMm = spec.heightMm,
                        widthPx = 320,
                    )
                }.getOrNull() ?: continue
                _covers.value = _covers.value + (nb.id to bmp)
            }
        }
    }

    /** 切换排序立即生效;同一 ViewModel 生命周期内的后续 refresh 继续沿用。 */
    fun setSortMode(mode: BookshelfSortMode) {
        if (_sortMode.value == mode) return
        _sortMode.value = mode
        _notebooks.value = sortNotebooks(loaded, mode)
    }

    fun pageCountOf(id: String): Int =
        pageCounts.getOrPut(id) { repo.pageCount(id) }

    fun create(
        name: String,
        coverColorHex: String,
        paper: String,
        purpose: String,
        pageSize: String = "B5",
        orientation: String = "landscape",
        paperColor: String = "#FAF9F5",
    ): String {
        val meta = repo.create(name, coverColorHex, paper, purpose, pageSize, orientation, paperColor)
        refresh()
        return meta.id
    }

    fun updateNotebook(meta: NotebookMeta) {
        repo.updateMeta(meta.copy(name = meta.name.trim().ifBlank { "未命名笔记" }, updatedAt = System.currentTimeMillis()))
        refresh()
    }

    fun folders(): List<String> = loaded.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(id)
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    /** SAF 选择在 UI 完成；解析、渲染与 ZIP 校验全部在 IO 线程执行。 */
    fun importDocument(uri: Uri) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val context = ServiceLocator.appContext
                val name = documentDisplayName(context, uri)
                val mime = context.contentResolver.getType(uri).orEmpty()
                when {
                    name.endsWith(".milk.zip", ignoreCase = true) -> {
                        val dir = File(context.cacheDir, "imports").apply { mkdirs() }
                        val temp = File(dir, "${UUID.randomUUID()}.milk.zip")
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                temp.outputStream().buffered().use { input.copyTo(it) }
                            } ?: error("无法读取所选备份")
                            MilkArchiveImporter.import(temp, repo.notebooksRoot()).meta
                        } finally {
                            temp.delete()
                        }
                    }
                    mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) ->
                        DocumentImporter.importPdf(context, uri, name)
                    name.endsWith(".zip", ignoreCase = true) || mime == "application/zip" ->
                        error("仅支持 MilkNote 自有 .milk.zip；千本笔记 ZIP 格式不兼容")
                    else -> error("请选择 PDF 或 MilkNote .milk.zip 备份")
                }
            }
            result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
            withContext(Dispatchers.Main) {
                _importing.value = false
                result.onSuccess { meta ->
                    refresh()
                    events.tryEmit(BookshelfEvent.Imported(meta.id))
                }.onFailure { error ->
                    events.tryEmit(BookshelfEvent.Message("导入失败：${error.message ?: "文件不可读"}"))
                }
            }
        }
    }
}

internal fun documentDisplayName(context: android.content.Context, uri: Uri): String {
    val fromCursor = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
    return fromCursor?.substringAfterLast('/')?.substringAfterLast('\\')
        ?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "导入文件"
}
