package com.milknote.app.ui.writer

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milknote.app.di.ServiceLocator
import com.milknote.ink.input.InkAction
import com.milknote.ink.input.InkCommit
import com.milknote.ink.input.InkController
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.ToolConfig
import com.milknote.ink.render.StrokeRenderer
import com.milknote.notes.model.NotebookMeta
import com.milknote.notes.model.NotebookPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 书写页 UI 状态 */
data class WriterUiState(
    val notebook: NotebookMeta? = null,
    val pages: List<NotebookPage> = emptyList(),
    val currentPageId: String = "p0001",
    val tool: ToolConfig = ToolConfig(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val lassoArmed: Boolean = false,          // 套索模式(等待圈选)
    val selectionCount: Int = 0,              // 选中笔迹数(>0 显示操作条)
    val frontBufferEnabled: Boolean = false,  // 前置缓冲出墨(设置)
)

/** 轻提示 */
data class Toast(val text: String)

class WriterViewModel : ViewModel() {

    private val repo = ServiceLocator.repository

    /** 每次进入书写页新建(书架→书写);跨页存活于本对象内 */
    val controller = InkController().apply {
        onCommit = { commit -> persistCommit(commit) }
        addInvalidator { syncToolbarState() }
    }

    private val _state = MutableStateFlow(WriterUiState())
    val state: StateFlow<WriterUiState> = _state

    /** 页缩略图 */
    val thumbs = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())

    val toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 4)

    private var notebookId: String = ""
    private var thumbJob: Job? = null

    init {
        // 设置 → 压感曲线档(笔宽是样式,换档即时生效)/ 前置缓冲开关
        viewModelScope.launch {
            ServiceLocator.appSettings.flow.collect { s ->
                frontBufferFlag = s.frontBufferInk
                if (controller.tool.pressureGamma != s.pressureGamma) {
                    controller.tool = controller.tool.copy(pressureGamma = s.pressureGamma)
                }
                _state.value = _state.value.copy(
                    tool = controller.tool,
                    frontBufferEnabled = s.frontBufferInk,
                )
            }
        }
    }

    /** 笔动作(笔键手势路由结果;按住修饰类由视图直接处理,不经过这里) */
    fun onPenAction(action: InkAction) {
        when (action) {
            InkAction.TOGGLE_ERASER -> {
                val t = _state.value.tool
                setTool(if (t.tool == InkTool.ERASER) InkTool.PEN else InkTool.ERASER)
            }
            InkAction.UNDO -> controller.undo()
            InkAction.REDO -> controller.redo()
            InkAction.NEW_PAGE -> newPage()
            InkAction.LASO -> toggleLasso()
            InkAction.SNIP -> toast("框选截图:M2 交付")
            InkAction.TEMP_ERASER -> controller.armOneShotEraser() // 改绑到单击类触发:下一笔临时为橡皮
        }
    }

    // ---- 套索选择 ----

    fun toggleLasso() {
        if (controller.lassoArmed) controller.disarmLasso() else controller.armLasso()
    }

    fun deleteSelection() = controller.deleteSelection() // 落盘经 onCommit 镜像

    fun clearSelection() = controller.clearSelection()

    // ---- 打开/切页 ----

    /** 设置快照(open() 整体重建 UiState 时保留,避免清掉已生效的设置) */
    @Volatile private var frontBufferFlag = false

    fun open(nbId: String) {
        if (notebookId == nbId) return
        notebookId = nbId
        val nb = repo.listNotebooks().firstOrNull { it.id == nbId } ?: return
        val pages = repo.listPages(nbId)
        val cur = pages.lastOrNull()?.pageId ?: repo.newPage(nbId)
        _state.value = WriterUiState(
            notebook = nb,
            pages = repo.listPages(nbId),
            currentPageId = cur,
            tool = controller.tool,
            frontBufferEnabled = frontBufferFlag,
        )
        controller.loadStrokes(repo.loadPage(nbId, cur))
        refreshAllThumbs()
    }

    fun switchPage(pageId: String) {
        if (pageId == _state.value.currentPageId) return
        viewModelScope.launch(Dispatchers.IO) {
            val strokes = repo.loadPage(notebookId, pageId)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(currentPageId = pageId)
                controller.loadStrokes(strokes)
            }
        }
    }

    fun newPage() {
        val pageId = repo.newPage(notebookId)
        _state.value = _state.value.copy(pages = repo.listPages(notebookId), currentPageId = pageId)
        controller.loadStrokes(emptyList())
        refreshAllThumbs()
    }

    // ---- 工具 ----

    fun setTool(tool: InkTool) {
        controller.tool = controller.tool.copy(tool = tool)
        _state.value = _state.value.copy(tool = controller.tool)
    }

    fun setColor(color: Long) {
        // 荧光黄切到荧光笔,其余色回钢笔(简化交互)
        val isHighlight = color == 0xFFFFD65AL
        controller.tool = controller.tool.copy(
            color = color,
            tool = if (isHighlight) InkTool.HIGHLIGHTER
                   else if (controller.tool.tool == InkTool.HIGHLIGHTER) InkTool.PEN
                   else controller.tool.tool,
        )
        _state.value = _state.value.copy(tool = controller.tool)
    }

    fun setWidth(mm: Float) {
        controller.tool = controller.tool.copy(baseWidthMm = mm)
        _state.value = _state.value.copy(tool = controller.tool)
    }

    fun undo() { controller.undo() }
    fun redo() { controller.redo() }

    // ---- 持久化(每笔抬起即追加;300ms 防抖刷缩略图与元数据) ----

    private fun persistCommit(commit: InkCommit) {
        val page = _state.value.currentPageId
        repo.appendCommit(notebookId, page, commit)
        scheduleFlush()
    }

    private var flushJob: Job? = null
    private fun scheduleFlush() {
        flushJob?.cancel()
        flushJob = viewModelScope.launch {
            delay(300)
            repo.touchUpdatedAt(notebookId)
            renderCurrentThumb()
        }
    }

    private fun syncToolbarState() {
        _state.value = _state.value.copy(
            canUndo = controller.page.canUndo,
            canRedo = controller.page.canRedo,
            lassoArmed = controller.lassoArmed,
            selectionCount = controller.selectionSnapshot()?.count ?: 0,
        )
    }

    // ---- 缩略图 ----

    private fun refreshAllThumbs() {
        viewModelScope.launch(Dispatchers.Default) {
            val nbId = notebookId
            val nb = _state.value.notebook ?: return@launch
            val map = repo.listPages(nbId).associate { p ->
                p.pageId to renderPageThumb(repo.loadPage(nbId, p.pageId), nb.paper, nb.pageSize, nb.orientation)
            }
            thumbs.value = map
        }
    }

    private suspend fun renderCurrentThumb() {
        val pageId = _state.value.currentPageId
        val nb = _state.value.notebook
        val strokes = controller.page.strokes.toList()
        val bmp = withContext(Dispatchers.Default) {
            renderPageThumb(strokes, nb?.paper ?: "dot", nb?.pageSize ?: "A4", nb?.orientation ?: "portrait")
        }
        thumbs.value = thumbs.value + (pageId to bmp)
    }

    fun toast(text: String) {
        toasts.tryEmit(Toast(text))
    }

    // ---- 导出 ----

    fun exportPng(onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val pageId = _state.value.currentPageId
            val nb = _state.value.notebook ?: return@launch
            val bmp = renderPageFull(repo.loadPage(notebookId, pageId), nb.paper, nb.pageSize, nb.orientation)
            val uri = PageExporter.savePng(ServiceLocator.appContext, bmp, "${nb.name}-$pageId")
            withContext(Dispatchers.Main) { onDone(uri) }
        }
    }

    fun exportPdf(onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val nb = _state.value.notebook ?: return@launch
            val pages = repo.listPages(notebookId).map { it.pageId to repo.loadPage(notebookId, it.pageId) }
            val uri = PageExporter.savePdf(
                ServiceLocator.appContext, pages, nb.paper, nb.name,
                nb.pageSize, nb.orientation,
            )
            withContext(Dispatchers.Main) { onDone(uri) }
        }
    }
}

// ---------- 页面 → 位图(缩略图与全量导出共用路径;尺寸按笔记本元数据) ----------

private const val MM_TO_PX = 5.67f

/** 页面尺寸(px @ MM_TO_PX,方向生效) */
internal fun pageDimsPx(pageSize: String, orientation: String): Pair<Float, Float> {
    val (w, h) = com.milknote.notes.model.PAGE_SIZES_MM[pageSize] ?: com.milknote.notes.model.PAGE_SIZES_MM.getValue("A4")
    return if (orientation == "landscape") h * MM_TO_PX to w * MM_TO_PX else w * MM_TO_PX to h * MM_TO_PX
}

internal fun renderPageThumb(
    strokes: List<com.milknote.ink.model.Stroke>,
    paper: String,
    pageSize: String = "A4",
    orientation: String = "portrait",
): ImageBitmap {
    val (pageW, pageH) = pageDimsPx(pageSize, orientation)
    val w = 132
    val bmp = Bitmap.createBitmap(w, (pageH / pageW * w).toInt(), Bitmap.Config.ARGB_8888)
    renderStrokes(android.graphics.Canvas(bmp), strokes, paper, w.toFloat(), pageW, pageH, thumb = true)
    return bmp.asImageBitmap()
}

internal fun renderPageFull(
    strokes: List<com.milknote.ink.model.Stroke>,
    paper: String,
    pageSize: String = "A4",
    orientation: String = "portrait",
): Bitmap {
    val (pageW, pageH) = pageDimsPx(pageSize, orientation)
    val w = pageW.toInt() * 2 // 2x 清晰度
    val bmp = Bitmap.createBitmap(w, (pageH / pageW * w).toInt(), Bitmap.Config.ARGB_8888)
    renderStrokes(android.graphics.Canvas(bmp), strokes, paper, w.toFloat(), pageW, pageH, thumb = false)
    return bmp
}

private fun renderStrokes(
    canvas: android.graphics.Canvas,
    strokes: List<com.milknote.ink.model.Stroke>,
    paper: String,
    outW: Float,
    pageW: Float,
    pageH: Float,
    thumb: Boolean,
) {
    val scale = outW / pageW
    canvas.scale(scale, scale)
    canvas.drawColor(0xFFFAF9F5.toInt())
    if (!thumb) com.milknote.ink.render.PaperRenderer.draw(canvas, paper, pageW, pageH, 0xFFD8D4C8.toInt())
    val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    for (s in strokes) {
        paint.color = s.color.toInt()
        paint.alpha = if (s.tool == com.milknote.ink.model.InkTool.HIGHLIGHTER) (255 * 0.45f).toInt() else 255
        canvas.drawPath(StrokeRenderer.buildPath(s, MM_TO_PX, com.milknote.ink.model.PressureGamma.STANDARD), paint)
    }
}
