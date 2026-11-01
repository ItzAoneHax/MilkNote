package com.milknote.app.ui.writer

import android.graphics.Bitmap
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milknote.agent.Intents
import com.milknote.agent.PageRef
import com.milknote.agent.Rect4
import com.milknote.app.agent.AgentBridge
import com.milknote.app.agent.PageRenderer
import com.milknote.app.di.ServiceLocator
import com.milknote.ink.input.InkAction
import com.milknote.ink.input.InkCommit
import com.milknote.ink.input.InkController
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.ToolConfig
import com.milknote.ink.render.AnnotationCardRenderer
import com.milknote.notes.model.NotebookMeta
import com.milknote.notes.model.NotebookPage
import java.util.UUID
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
    val snip: SnipUiState = SnipUiState(),    // ④ 框选模式(AGENT-BACKEND §4.2)
)

/** ④ 框选状态:IDLE → FROZEN(拖选中)→ CONFIRMED(松开待发) */
data class SnipUiState(
    val phase: SnipPhase = SnipPhase.IDLE,
    val rect: Rect4? = null,                  // 视口坐标(overlay 与画布同 bounds)
    val intent: Intents.Id = Intents.Id.EXPLAIN,  // 意图条当前选中(默认讲解)
    val drawerWasOpen: Boolean = false,       // 进入框选前抽屉是否已开
)

enum class SnipPhase { IDLE, FROZEN, CONFIRMED }

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
        registerAgentBridge()
    }

    // ---- Agent 桥(渲染源 / 批注落点 / 整章页序;AGENT-BACKEND §2.1 装配) ----

    private fun registerAgentBridge() {
        AgentBridge.pageProvider = { page ->
            if (page.notebookId == notebookId) currentPageData(page.pageId) else null
        }
        ServiceLocator.agentSnipSource.pagesListProvider = { nb ->
            repo.listPages(nb).map { PageRef(nb, it.pageId) }
        }
        AgentBridge.annotationInserter = { page, anchor, text, latex, cardWidth ->
            insertAnnotationCard(page, anchor, text, latex, cardWidth)
        }
    }

    private fun currentPageData(pageId: String): AgentBridge.PageData? {
        val nb = _state.value.notebook ?: return null
        val strokes = if (pageId == _state.value.currentPageId) {
            controller.page.strokes.toList()
        } else null
        val cards = if (pageId == _state.value.currentPageId) {
            controller.annotationCards()
        } else null
        return AgentBridge.PageData(
            strokes = strokes ?: repo.loadPage(notebookId, pageId),
            cards = cards ?: repo.loadAnnotations(notebookId, pageId),
            paper = nb.paper,
            pageSize = nb.pageSize,
            orientation = nb.orientation,
        )
    }

    /** 批注插入(§9 锚定:选区正下方 → 右缘批注列 → 页底追加;进撤销栈) */
    private suspend fun insertAnnotationCard(
        page: PageRef,
        anchor: Rect4?,
        text: String,
        latex: Boolean,
        cardWidth: Float,
    ): String? = withContext(Dispatchers.Main) {
        if (page.notebookId != notebookId || page.pageId != _state.value.currentPageId) return@withContext null
        val nb = _state.value.notebook ?: return@withContext null
        val (pageW, pageH) = nb.pageMm(PageRenderer.MM_TO_PX)
        val width = if (cardWidth > 0f) cardWidth else AnnotationCardRenderer.cardWidthDefault(pageW)
        val id = UUID.randomUUID().toString().substring(0, 8)
        val y = anchor?.let { it.b + 24f } ?: 24f
        val x = anchor?.let { (it.l + it.r) / 2 - width / 2 }?.coerceIn(8f, pageW - width - 8f)
            ?: (pageW - width - 16f)     // 无锚:右缘批注列
        val card = AnnotationCard(
            id = id,
            x = x,
            y = minOf(y, pageH * 0.8f),
            width = width,
            kind = if (latex) AnnotationCard.Kind.LATEX else AnnotationCard.Kind.TEXT,
            text = text,
            createdAt = System.currentTimeMillis(),
        )
        controller.addAnnotationCard(card)   // 进撤销栈;落盘经 onCommit
        refreshThumbOf(page.pageId)
        id
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
            InkAction.SNIP -> toggleSnip()          // 笔键长按 = 框选(DESIGN-V0 §4.1 主路径)
            InkAction.TEMP_ERASER -> controller.armOneShotEraser() // 改绑到单击类触发:下一笔临时为橡皮
        }
    }

    // ---- 套索选择 ----

    fun toggleLasso() {
        if (controller.lassoArmed) controller.disarmLasso() else controller.armLasso()
    }

    fun deleteSelection() = controller.deleteSelection() // 落盘经 onCommit 镜像

    fun clearSelection() = controller.clearSelection()

    // ---- ④ 框选截图(AGENT-BACKEND §4.2) ----

    /** 进入框选模式(冻结画布):套索/选中态先退出,避免多模式并存 */
    fun enterSnip() {
        controller.disarmLasso()
        controller.clearSelection()
        _state.value = _state.value.copy(
            snip = SnipUiState(
                phase = SnipPhase.FROZEN,
                drawerWasOpen = ServiceLocator.agentController.state.value.drawerOpen,
            ),
        )
    }

    fun exitSnip() {
        _state.value = _state.value.copy(snip = SnipUiState())
    }

    private fun toggleSnip() {
        if (_state.value.snip.phase == SnipPhase.IDLE) enterSnip() else exitSnip()
    }

    /** 拖选中(视口坐标矩形) */
    fun snipDrag(rect: Rect4) {
        _state.value = _state.value.copy(snip = _state.value.snip.copy(phase = SnipPhase.FROZEN, rect = rect))
    }

    /** 松开:<48px 视为误触不确认(§4.2 minSize) */
    fun snipRelease() {
        val s = _state.value.snip
        val r = s.rect ?: return exitSnip()
        if (r.width < 48f || r.height < 48f) return exitSnip()
        _state.value = _state.value.copy(snip = s.copy(phase = SnipPhase.CONFIRMED))
    }

    fun snipRectChanged(rect: Rect4) {
        _state.value = _state.value.copy(snip = _state.value.snip.copy(rect = rect, phase = SnipPhase.FROZEN))
    }

    /** 全页:选区扩为整页(页坐标矩形,由视图换算传入) */
    fun snipFullPage(pageRect: Rect4) {
        _state.value = _state.value.copy(snip = _state.value.snip.copy(rect = pageRect, phase = SnipPhase.CONFIRMED))
    }

    fun snipPickIntent(intent: Intents.Id) {
        _state.value = _state.value.copy(snip = _state.value.snip.copy(intent = intent))
        ServiceLocator.agentController.intentPicked(intent)
    }

    /** 发送 ↵:选区(视口坐标)→ 页坐标 → Agent 草稿附件 → 意图发送(§2.2 链路) */
    fun snipSend(viewToPage: (Float, Float) -> Pair<Float, Float>) {
        val s = _state.value.snip
        val r = s.rect ?: return exitSnip()
        val nb = _state.value.notebook ?: return
        val pageId = _state.value.currentPageId
        exitSnip()
        viewModelScope.launch {
            val agent = ServiceLocator.agentController
            agent.bindPage(PageRef(nb.id, pageId))
            val rectPage = r.normalized().let {
                val (l, t) = viewToPage(it.l, it.t)
                val (rr, b) = viewToPage(it.r, it.b)
                Rect4(l, t, rr, b)
            }
            val name = agent.addSnip(rectPage)
            if (name == null) {
                toast("框选失败:页面渲染不可用")
            } else {
                agent.sendWithIntent(s.intent)
            }
        }
    }

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
        controller.loadStrokes(repo.loadPage(nbId, cur), repo.loadAnnotations(nbId, cur))
        bindAgent(nb.id, cur)
        refreshAllThumbs()
    }

    private fun bindAgent(nbId: String, pageId: String) {
        ServiceLocator.agentController.bindPage(PageRef(nbId, pageId))
    }

    fun switchPage(pageId: String) {
        if (pageId == _state.value.currentPageId) return
        viewModelScope.launch(Dispatchers.IO) {
            val strokes = repo.loadPage(notebookId, pageId)
            val cards = repo.loadAnnotations(notebookId, pageId)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(currentPageId = pageId)
                controller.loadStrokes(strokes, cards)
                bindAgent(notebookId, pageId)
            }
        }
    }

    fun newPage() {
        val pageId = repo.newPage(notebookId)
        _state.value = _state.value.copy(pages = repo.listPages(notebookId), currentPageId = pageId)
        controller.loadStrokes(emptyList())
        bindAgent(notebookId, pageId)
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

    // ---- 持久化(每笔抬起即追加;批注卡整表重写;300ms 防抖刷缩略图与元数据) ----

    private fun persistCommit(commit: InkCommit) {
        val page = _state.value.currentPageId
        when (commit) {
            is InkCommit.Added, is InkCommit.Tombstoned -> repo.appendCommit(notebookId, page, commit)
            is InkCommit.CardAdded, is InkCommit.CardRemoved ->
                repo.saveAnnotations(notebookId, page, controller.annotationCards())
        }
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
        viewModelScope.launch {
            val nbId = notebookId
            val nb = _state.value.notebook ?: return@launch
            val map = repo.listPages(nbId).associate { p ->
                p.pageId to renderPageThumb(repo.loadPage(nbId, p.pageId), repo.loadAnnotations(nbId, p.pageId), nb.paper, nb.pageSize, nb.orientation)
            }
            thumbs.value = map
        }
    }

    private suspend fun renderCurrentThumb() {
        renderThumbOf(_state.value.currentPageId)
    }

    private suspend fun refreshThumbOf(pageId: String) {
        renderThumbOf(pageId)
    }

    private suspend fun renderThumbOf(pageId: String) {
        val nb = _state.value.notebook ?: return
        val strokes = controller.page.strokes.toList()
        val cards = controller.annotationCards()
        val bmp = withContext(Dispatchers.Default) {
            renderPageThumb(strokes, cards, nb.paper, nb.pageSize, nb.orientation)
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
            val data = AgentBridge.PageData(
                repo.loadPage(notebookId, pageId),
                repo.loadAnnotations(notebookId, pageId),
                nb.paper, nb.pageSize, nb.orientation,
            )
            val bmp = PageRenderer.renderFull(data)
            val uri = PageExporter.savePng(ServiceLocator.appContext, bmp, "${nb.name}-$pageId")
            withContext(Dispatchers.Main) { onDone(uri) }
        }
    }

    fun exportPdf(onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val nb = _state.value.notebook ?: return@launch
            val pages = repo.listPages(notebookId).map {
                Triple(it.pageId, repo.loadPage(notebookId, it.pageId), repo.loadAnnotations(notebookId, it.pageId))
            }
            val uri = PageExporter.savePdf(
                ServiceLocator.appContext, pages, nb.paper, nb.name,
                nb.pageSize, nb.orientation,
            )
            withContext(Dispatchers.Main) { onDone(uri) }
        }
    }
}

// ---------- 页面 → 位图(缩略图与全量导出共用路径;含批注卡) ----------

internal fun renderPageThumb(
    strokes: List<com.milknote.ink.model.Stroke>,
    cards: List<AnnotationCard>,
    paper: String,
    pageSize: String = "A4",
    orientation: String = "portrait",
): ImageBitmap {
    val (pageW, pageH) = PageRenderer.pageDimsPx(pageSize, orientation)
    val w = 132
    val bmp = Bitmap.createBitmap(w, (pageH / pageW * w).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val scale = w / pageW
    canvas.scale(scale, scale)
    PageRenderer.drawAll(canvas, AgentBridge.PageData(strokes, cards, paper, pageSize, orientation), pageW, pageH, clip = null)
    return bmp.asImageBitmap()
}

