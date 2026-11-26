package com.milknote.app.ui.writer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milknote.agent.Intents
import com.milknote.agent.NotebookWorkspacePort
import com.milknote.agent.PageDimensions
import com.milknote.agent.PageElementSummary
import com.milknote.agent.PageInfo
import com.milknote.agent.PageRef
import com.milknote.agent.Rect4
import com.milknote.app.agent.AgentBridge
import com.milknote.app.agent.PageRenderer
import com.milknote.app.agent.WorkspaceElementSummaries
import com.milknote.app.agent.WorkspaceSidecarOps
import com.milknote.app.di.ServiceLocator
import com.milknote.ink.input.InkAction
import com.milknote.ink.input.InkCommit
import com.milknote.ink.input.InkController
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.CardTextStyle
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.LassoContent
import com.milknote.ink.model.LassoMode
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.PageImageKind
import com.milknote.ink.model.PressureGamma
import com.milknote.ink.model.StrokeGeom
import com.milknote.ink.model.ToolConfig
import com.milknote.ink.render.AnnotationCardRenderer
import com.milknote.notes.model.NoteCard
import com.milknote.notes.model.NotebookMeta
import com.milknote.notes.model.NotebookPage
import com.milknote.notes.storage.PageInsertPosition
import java.io.File
import java.security.MessageDigest
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
    val openTabs: List<NotebookMeta> = emptyList(),
    val splitEnabled: Boolean = false,
    val splitNotebook: NotebookMeta? = null,
    val splitPages: List<NotebookPage> = emptyList(),
    val splitPageId: String = "p0001",
    val pageMarkdown: String = "",
    val toolSettings: WriterToolSettings = WriterToolSettings(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val lassoArmed: Boolean = false,          // 套索模式(等待圈选)
    val lassoMode: LassoMode = LassoMode.FREEHAND,
    val lassoFilters: Set<LassoContent> = LassoContent.entries.toSet(),
    val selectionCount: Int = 0,              // 混合选中元素数(>0 显示变换菜单)
    val selectionBox: StrokeGeom.Box? = null, // 当前选区(页坐标,含预览)
    val selectionCanMoveUp: Boolean = false,
    val selectionCanMoveDown: Boolean = false,
    val selectionSnapEnabled: Boolean = true,
    val frontBufferEnabled: Boolean = false,  // 前置缓冲出墨(设置)
    val snip: SnipUiState = SnipUiState(),    // ④ 框选模式(AGENT-BACKEND §4.2)
) {
    val tool: ToolConfig get() = toolSettings.current
    val currentPageIndex: Int get() = pages.indexOfFirst { it.pageId == currentPageId }
    val canPreviousPage: Boolean get() = currentPageIndex > 0
    val canNextPage: Boolean get() = currentPageIndex >= 0 && currentPageIndex < pages.lastIndex
    val splitPageIndex: Int get() = splitPages.indexOfFirst { it.pageId == splitPageId }
}

/** ④ 框选状态:IDLE → FROZEN(拖选中)→ CONFIRMED(松开待发) */
data class SnipUiState(
    val phase: SnipPhase = SnipPhase.IDLE,
    val rect: Rect4? = null,                  // 视口坐标(overlay 与画布同 bounds)
    val intent: Intents.Id = Intents.Id.EXPLAIN,  // 意图条当前选中(默认讲解)
    val drawerWasOpen: Boolean = false,       // 进入框选前抽屉是否已开
    val attachOnly: Boolean = false,          // 只加入附件、不立即发送(输入框附件菜单进入)
)

enum class SnipPhase { IDLE, FROZEN, CONFIRMED }

/** 轻提示 */
data class Toast(val text: String)

/** 一页的完整内容快照(切页/删除/导出共用,避免四元组)。 */
private data class PageContent(
    val strokes: List<com.milknote.ink.model.Stroke>,
    val cards: List<AnnotationCard>,
    val images: List<ImageElement>,
    val media: List<MediaElement>,
)

class WriterViewModel : ViewModel() {

    private val repo = ServiceLocator.repository

    /** 每次进入书写页新建(书架→书写);跨页存活于本对象内 */
    val controller = InkController().apply {
        onCommit = { commit -> persistCommit(commit) }
        addInvalidator { syncToolbarState() }
        // 橡皮「自动切回画笔」(千本 P019 自动取消):擦完一笔回到原书写笔
        onEraserUsed = {
            val s = _state.value.toolSettings
            if (s.eraserAutoRevert && s.eraserTemporary) toggleEraser()
        }
    }

    private val _state = MutableStateFlow(WriterUiState())
    val state: StateFlow<WriterUiState> = _state

    /** Agent 工作区端口(页管理 / 画布元素;registerAgentBridge 注册到 [AgentBridge]) */
    private val workspacePort = WriterWorkspacePort()

    /** 页缩略图 */
    val thumbs = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())

    val toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 4)

    private var notebookId: String = ""
    private var thumbJob: Job? = null
    private var pageSwitchJob: Job? = null
    private var pendingPageId: String? = null
    /** 进入「框选加入附件」前的草稿文字(openDrawer 会清空输入框) */
    private var draftTextBeforeSnip: String? = null

    init {
        // 设置 → 压感曲线档(笔宽是样式,换档即时生效)/ 前置缓冲开关
        viewModelScope.launch {
            ServiceLocator.appSettings.flow.collect { s ->
                frontBufferFlag = s.frontBufferInk
                val settings = _state.value.toolSettings.withPressureGamma(s.pressureGamma)
                applyToolSettings(settings)
                _state.value = _state.value.copy(
                    frontBufferEnabled = s.frontBufferInk,
                )
                penPresets.value = PenPresets.decode(s.penPresets)
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
        AgentBridge.workspaceHandler = workspacePort
    }

    override fun onCleared() {
        // 书写页销毁后 agent 不再持有已失效的控制器(工具调用应回退到磁盘或返回未接入)
        if (AgentBridge.workspaceHandler === workspacePort) AgentBridge.workspaceHandler = null
        super.onCleared()
    }

    private fun currentPageData(pageId: String): AgentBridge.PageData? {
        val nb = _state.value.notebook ?: return null
        val strokes = if (pageId == _state.value.currentPageId) {
            controller.page.strokes.toList()
        } else null
        val cards = if (pageId == _state.value.currentPageId) {
            controller.annotationCards()
        } else null
        val images = if (pageId == _state.value.currentPageId) controller.images() else null
        val media = if (pageId == _state.value.currentPageId) controller.media() else null
        val spec = nb.pageSpec(pageId)
        return AgentBridge.PageData(
            strokes = strokes ?: repo.loadPage(notebookId, pageId),
            cards = cards ?: repo.loadAnnotations(notebookId, pageId),
            paper = spec.paper,
            pageSize = nb.pageSize,
            orientation = nb.orientation,
            images = images ?: repo.loadImages(notebookId, pageId),
            media = media ?: repo.loadMedia(notebookId, pageId),
            imageDir = repo.notebookDir(notebookId).absolutePath,
            paperColor = spec.paperColor,
            pageWidthMm = spec.widthMm,
            pageHeightMm = spec.heightMm,
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
        val (pageW, pageH) = nb.pageMm(page.pageId, PageRenderer.MM_TO_PX)
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

    /**
     * Agent 工作区端口(AGENT-BACKEND 页管理 / 画布工具):
     * 当前打开的笔记本走内存控制器(撤销栈 + 缩略图),其余页整表重写 sidecar;
     * 所有 `controller` 与 `_state` 修改都在主线程,磁盘读写在 IO。
     */
    private inner class WriterWorkspacePort : NotebookWorkspacePort {

        override suspend fun listPages(notebookId: String): List<PageInfo> = withContext(Dispatchers.IO) {
            val current = _state.value.currentPageId
            repo.listPages(notebookId).mapIndexed { index, page ->
                PageInfo(
                    pageId = page.pageId,
                    index = index + 1,
                    isCurrent = notebookId == this@WriterViewModel.notebookId && page.pageId == current,
                )
            }
        }

        override suspend fun pageDimensions(page: PageRef): PageDimensions? = withContext(Dispatchers.IO) {
            val meta = repo.getNotebook(page.notebookId) ?: return@withContext null
            val (width, height) = meta.pageMm(page.pageId, PageRenderer.MM_TO_PX)
            PageDimensions(width, height)
        }

        override suspend fun addPage(
            notebookId: String,
            position: String,
            anchorPageId: String?,
            switchTo: Boolean,
        ): PageInfo? {
            val insert = when (position.lowercase()) {
                "before" -> PageInsertPosition.BEFORE
                "end" -> PageInsertPosition.END
                else -> PageInsertPosition.AFTER
            }
            val pageId = withContext(Dispatchers.IO) { repo.insertPage(notebookId, insert, anchorPageId) }
            val pages = withContext(Dispatchers.IO) { repo.listPages(notebookId) }
            val active = isActiveNotebook(notebookId)
            if (active) {
                if (switchTo) {
                    openPageOnMain(pageId)
                } else {
                    withContext(Dispatchers.Main) { _state.value = _state.value.copy(pages = pages) }
                    refreshThumbFromDisk(PageRef(notebookId, pageId))
                }
            }
            val index = pages.indexOfFirst { it.pageId == pageId } + 1
            val isCurrent = active && _state.value.currentPageId == pageId
            return PageInfo(pageId, index.coerceAtLeast(1), isCurrent)
        }

        override suspend fun deletePage(notebookId: String, pageId: String): Boolean {
            val before = withContext(Dispatchers.IO) { repo.listPages(notebookId) }
            val fallback = pageIdAfterDelete(before, pageId)
            if (!withContext(Dispatchers.IO) { repo.deletePage(notebookId, pageId) }) return false
            var pages = withContext(Dispatchers.IO) { repo.listPages(notebookId) }
            if (pages.isEmpty()) {
                // 与页内删除语义一致:删空后补一张空白页,避免书写页落到无页状态
                withContext(Dispatchers.IO) { repo.newPage(notebookId) }
                pages = withContext(Dispatchers.IO) { repo.listPages(notebookId) }
            }
            if (!isActiveNotebook(notebookId)) return true
            val currentBefore = _state.value.currentPageId
            val currentMissing = pages.none { it.pageId == currentBefore }
            val target = if (currentMissing) {
                fallback?.takeIf { id -> pages.any { it.pageId == id } } ?: pages.first().pageId
            } else {
                currentBefore
            }
            val replacement = if (currentMissing) {
                withContext(Dispatchers.IO) {
                    PageContent(
                        repo.loadPage(notebookId, target),
                        repo.loadAnnotations(notebookId, target),
                        repo.loadImages(notebookId, target),
                        repo.loadMedia(notebookId, target),
                    )
                }
            } else {
                null
            }
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(pages = pages, currentPageId = target)
                replacement?.let {
                    controller.loadStrokes(it.strokes, it.cards, it.images, it.media)
                    bindAgent(notebookId, target)
                }
                thumbs.value = thumbs.value - pageId
            }
            refreshAllThumbs()
            return true
        }

        override suspend fun insertMarkdownCard(
            page: PageRef,
            markdown: String,
            xPx: Float,
            yPx: Float,
            widthPx: Float,
        ): String? {
            val id = UUID.randomUUID().toString().substring(0, 8)
            val card = AnnotationCard(
                id = id,
                x = xPx,
                y = yPx,
                width = widthPx.coerceAtLeast(80f),
                kind = AnnotationCard.Kind.MARKDOWN,
                text = markdown,
                createdAt = System.currentTimeMillis(),
            )
            if (isCurrentPage(page)) {
                withContext(Dispatchers.Main) { controller.addAnnotationCard(card) }
                refreshThumbOf(page.pageId)
                return id
            }
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val cards = repo.loadAnnotations(page.notebookId, page.pageId).toMutableList()
                    val top = (cards.maxOfOrNull { it.zIndex } ?: 0L) + 1L
                    cards.add(card.copy(zIndex = top))
                    repo.saveAnnotations(page.notebookId, page.pageId, cards)
                }.isSuccess
            }
            if (!saved) return null
            refreshThumbFromDisk(page)
            return id
        }

        override suspend fun listElements(page: PageRef): List<PageElementSummary> {
            val dims = pageDimensions(page) ?: return emptyList()
            val snapshot = if (isCurrentPage(page)) {
                withContext(Dispatchers.Main) {
                    PageContent(
                        controller.page.strokes.toList(),
                        controller.annotationCards(),
                        controller.images(),
                        controller.media(),
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    PageContent(
                        repo.loadPage(page.notebookId, page.pageId),
                        repo.loadAnnotations(page.notebookId, page.pageId),
                        repo.loadImages(page.notebookId, page.pageId),
                        repo.loadMedia(page.notebookId, page.pageId),
                    )
                }
            }
            // 卡片高度要跑文本排版(含 Markdown 富渲染),放默认线程避免阻塞主线程
            return withContext(Dispatchers.Default) {
                WorkspaceElementSummaries.of(
                    snapshot.strokes, snapshot.cards, snapshot.images, snapshot.media,
                    dims.widthPx, dims.heightPx,
                )
            }
        }

        override suspend fun moveElement(
            page: PageRef,
            elementId: String,
            elementKind: String?,
            targetX: Float?,
            targetY: Float?,
            dx: Float?,
            dy: Float?,
        ): Boolean {
            val kind = WorkspaceElementSummaries.parseKind(elementKind)
            if (isCurrentPage(page)) {
                val moved = withContext(Dispatchers.Main) {
                    controller.moveElementById(kind, elementId, targetX, targetY, dx, dy)
                }
                if (moved) refreshThumbOf(page.pageId)
                return moved
            }
            if (targetX == null && targetY == null && dx == null && dy == null) return false
            val moved = withContext(Dispatchers.IO) {
                WorkspaceSidecarOps.move(repo, page, kind, elementId, targetX, targetY, dx, dy)
            }
            if (moved) refreshThumbFromDisk(page)
            return moved
        }

        override suspend fun deleteElement(page: PageRef, elementId: String, elementKind: String?): Boolean {
            val kind = WorkspaceElementSummaries.parseKind(elementKind)
            if (isCurrentPage(page)) {
                val deleted = withContext(Dispatchers.Main) { controller.deleteElementById(kind, elementId) }
                if (deleted) refreshThumbOf(page.pageId)
                return deleted
            }
            val deleted = withContext(Dispatchers.IO) {
                WorkspaceSidecarOps.delete(repo, page, kind, elementId)
            }
            if (deleted) refreshThumbFromDisk(page)
            return deleted
        }

        private fun isActiveNotebook(target: String): Boolean =
            target == this@WriterViewModel.notebookId && _state.value.notebook != null

        private fun isCurrentPage(page: PageRef): Boolean =
            page.notebookId == this@WriterViewModel.notebookId &&
                page.pageId == _state.value.currentPageId
    }

    /** 非当前页的改动只落在磁盘 sidecar:从磁盘重绘该页缩略图(不影响当前页内存内容) */
    private suspend fun refreshThumbFromDisk(page: PageRef) {
        if (page.notebookId != notebookId) return
        val nb = withContext(Dispatchers.IO) { repo.getNotebook(page.notebookId) } ?: return
        val spec = nb.pageSpec(page.pageId)
        val snapshot = withContext(Dispatchers.IO) {
            PageContent(
                repo.loadPage(page.notebookId, page.pageId),
                repo.loadAnnotations(page.notebookId, page.pageId),
                repo.loadImages(page.notebookId, page.pageId),
                repo.loadMedia(page.notebookId, page.pageId),
            )
        }
        val bmp = withContext(Dispatchers.Default) {
            renderPageThumb(
                snapshot.strokes, snapshot.cards, spec.paper, nb.pageSize, nb.orientation,
                images = snapshot.images, media = snapshot.media,
                imageDir = repo.notebookDir(page.notebookId).absolutePath,
                paperColor = spec.paperColor, pageWidthMm = spec.widthMm, pageHeightMm = spec.heightMm,
            )
        }
        thumbs.value = thumbs.value + (page.pageId to bmp)
    }

    /** 手动插入文字框:沿用批注卡的拖动、撤销、落盘与导出链路。 */
    fun createTextCard(text: String, style: CardTextStyle = CardTextStyle()) {
        val nb = _state.value.notebook ?: return
        val pageId = _state.value.currentPageId
        val (pageW, pageH) = nb.pageMm(pageId, PageRenderer.MM_TO_PX)
        val width = AnnotationCardRenderer.cardWidthDefault(pageW)
        val card = AnnotationCard(
            id = UUID.randomUUID().toString().substring(0, 8),
            x = (pageW - width) / 2f,
            y = pageH * 0.28f,
            width = width,
            kind = AnnotationCard.Kind.TEXT,
            text = text,
            textStyle = style,
            createdAt = System.currentTimeMillis(),
        )
        controller.addAnnotationCard(card)
        viewModelScope.launch { refreshThumbOf(pageId) }
    }

    fun selectedTextCard(): AnnotationCard? = controller.selectedAnnotationCard()

    /** 单选正好是音视频元素时返回(选中条播放入口);否则 null。 */
    fun selectedMedia(): MediaElement? = controller.selectedMedia()

    fun updateTextCard(card: AnnotationCard, text: String, style: CardTextStyle) {
        if (controller.updateAnnotationCard(card.copy(text = text, textStyle = style))) {
            viewModelScope.launch { refreshThumbOf(_state.value.currentPageId) }
        }
    }

    /** 卡片库回插：切到指定页后，文字与图片都走现有 InkOp/撤销/持久化链路。 */
    fun insertLibraryCard(card: NoteCard, pageId: String) {
        if (notebookId.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (_state.value.pages.none { it.pageId == pageId }) {
                withContext(Dispatchers.Main) { toast("插入失败：目标页面不存在") }
                return@launch
            }
            if (_state.value.currentPageId != pageId) openPageOnMain(pageId)
            val nb = _state.value.notebook ?: return@launch
            val (pageW, pageH) = nb.pageMm(pageId, PageRenderer.MM_TO_PX)
            var copiedImage: ImageElement? = null
            card.imageSrc?.let { relative ->
                val cardsRoot = File(ServiceLocator.appContext.filesDir, "cards")
                val source = File(cardsRoot, relative)
                if (source.canonicalFile.toPath().startsWith(cardsRoot.canonicalFile.toPath()) && source.isFile) {
                    val imageDir = File(repo.notebookDir(notebookId), "images").apply { mkdirs() }
                    val target = File(imageDir, "card-${UUID.randomUUID()}.${source.extension.ifBlank { "png" }}")
                    source.copyTo(target, overwrite = false)
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(target.absolutePath, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        val scale = minOf(pageW * 0.6f / options.outWidth, pageH * 0.55f / options.outHeight, 1f)
                        copiedImage = ImageElement(
                            id = UUID.randomUUID().toString().substring(0, 12), src = "images/${target.name}",
                            x = (pageW - options.outWidth * scale) / 2f, y = pageH * 0.22f,
                            width = options.outWidth * scale, height = options.outHeight * scale,
                            createdAt = System.currentTimeMillis(),
                        )
                    }
                }
            }
            withContext(Dispatchers.Main) {
                val text = listOf(card.title, card.body).filter { it.isNotBlank() }.joinToString("\n\n")
                if (text.isNotBlank()) {
                    val width = AnnotationCardRenderer.cardWidthDefault(pageW)
                    controller.addAnnotationCard(AnnotationCard(
                        id = UUID.randomUUID().toString().substring(0, 12),
                        x = (pageW - width) / 2f, y = pageH * 0.18f, width = width,
                        text = text, createdAt = System.currentTimeMillis(),
                    ))
                }
                copiedImage?.let { controller.addImageElement(it) }
                refreshThumbOf(pageId)
                toast("已插入到 $pageId")
            }
        }
    }

    /** 形状使用两点 Stroke,撤销/擦除/套索与 proto 沿用普通笔迹路径。 */
    fun addShape(config: ShapeConfig, start: Pair<Float, Float>, end: Pair<Float, Float>) {
        val now = System.currentTimeMillis()
        val previous = controller.tool
        controller.tool = previous.copy(tool = config.tool, color = config.color, baseWidthMm = config.widthMm)
        controller.beginStroke(InkPoint(start.first, start.second, 1f, 0f, 0f, now))
        controller.extendStroke(listOf(InkPoint(end.first, end.second, 1f, 0f, 0f, now + 1)))
        controller.endStroke()
        controller.tool = previous
        viewModelScope.launch { refreshThumbOf(_state.value.currentPageId) }
    }

    /** 将相册图片复制进笔记本资产目录,再作为页面元素插入,避免 content Uri 授权过期。 */
    fun insertImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val nb = _state.value.notebook ?: return@launch
            val pageId = _state.value.currentPageId
            val dir = File(repo.notebookDir(notebookId), "images").apply { mkdirs() }
            val digest = MessageDigest.getInstance("SHA-256")
            val tmp = File(dir, "${UUID.randomUUID()}.tmp")
            val copied = runCatching {
                ServiceLocator.appContext.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: error("无法读取图片")
            }
            if (copied.isFailure) {
                tmp.delete()
                toast("插图失败:无法读取图片")
                return@launch
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }.substring(0, 24)
            val ext = when (ServiceLocator.appContext.contentResolver.getType(uri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val target = File(dir, "$hash.$ext")
            if (target.exists()) tmp.delete() else if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true); tmp.delete()
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                toast("插图失败:图片格式不支持")
                return@launch
            }
            runCatching {
                val library = File(ServiceLocator.appContext.filesDir, "materials/images").apply { mkdirs() }
                target.copyTo(File(library, target.name), overwrite = true)
            }
            val (pageW, pageH) = nb.pageMm(pageId, PageRenderer.MM_TO_PX)
            val scale = minOf(pageW * 0.55f / options.outWidth, pageH * 0.55f / options.outHeight, 1f)
            val width = options.outWidth * scale
            val height = options.outHeight * scale
            val image = ImageElement(
                id = UUID.randomUUID().toString().substring(0, 12),
                src = "images/${target.name}",
                x = (pageW - width) / 2f,
                y = (pageH - height) / 2f,
                width = width,
                height = height,
                createdAt = System.currentTimeMillis(),
            )
            withContext(Dispatchers.Main) {
                controller.addImageElement(image)
                refreshThumbOf(pageId)
                toast("已插入图片")
            }
        }
    }

    /** 本地素材库中的图片/胶带/贴纸复制到当前笔记本，再进入统一 ImageElement 链路。 */
    fun insertMaterialFile(source: File, kind: PageImageKind) {
        viewModelScope.launch(Dispatchers.IO) { insertMaterialFileInternal(source, kind) }
    }

    fun insertMaterial(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(ServiceLocator.appContext.filesDir, "materials/images").apply { mkdirs() }
            val target = File(dir, "image-${UUID.randomUUID()}.img")
            val ok = runCatching {
                ServiceLocator.appContext.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().buffered().use { input.copyTo(it) }
                } ?: error("无法读取图片")
            }.isSuccess
            if (ok) insertMaterialFileInternal(target, PageImageKind.IMAGE)
            else toast("素材导入失败：无法读取图片")
        }
    }

    fun insertBuiltinMaterial(kind: PageImageKind, variant: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(ServiceLocator.appContext.filesDir, "materials/builtin").apply { mkdirs() }
            val file = File(dir, "${kind.name.lowercase()}-$variant.png")
            if (!file.exists()) {
                val tape = kind == PageImageKind.TAPE
                val bitmap = Bitmap.createBitmap(if (tape) 320 else 220, if (tape) 120 else 220, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                val colors = listOf(0xCC3B5BEC.toInt(), 0xCCD2483C.toInt(), 0xCC2E9E6B.toInt(), 0xCCD98E04.toInt())
                paint.color = colors[variant.mod(colors.size)]
                if (tape) {
                    canvas.save()
                    canvas.rotate(-5f, 160f, 60f)
                    canvas.drawRoundRect(8f, 18f, 312f, 102f, 12f, 12f, paint)
                    paint.color = 0x55FFFFFF
                    for (x in 20..300 step 34) canvas.drawLine(x.toFloat(), 22f, x - 24f, 98f, paint)
                    canvas.restore()
                } else {
                    val path = android.graphics.Path()
                    val cx = 110f; val cy = 110f
                    for (i in 0 until 10) {
                        val radius = if (i % 2 == 0) 88f else 40f
                        val angle = -Math.PI / 2 + i * Math.PI / 5
                        val x = cx + radius * kotlin.math.cos(angle).toFloat()
                        val y = cy + radius * kotlin.math.sin(angle).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close(); canvas.drawPath(path, paint)
                    paint.color = 0xCCFFFFFF.toInt()
                    canvas.drawCircle(82f, 102f, 8f, paint); canvas.drawCircle(138f, 102f, 8f, paint)
                }
                file.outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            insertMaterialFileInternal(file, kind)
        }
    }

    private suspend fun insertMaterialFileInternal(source: File, kind: PageImageKind) {
        val nb = _state.value.notebook ?: return
        val pageId = _state.value.currentPageId
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            withContext(Dispatchers.Main) { toast("素材格式不支持") }
            return
        }
        val dir = File(repo.notebookDir(notebookId), "images/materials").apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.${if (source.extension == "png") "png" else "img"}")
        source.copyTo(target, overwrite = false)
        val (pageW, pageH) = nb.pageMm(pageId, PageRenderer.MM_TO_PX)
        val scale = minOf(pageW * 0.45f / options.outWidth, pageH * 0.35f / options.outHeight, 1f)
        val image = ImageElement(
            id = UUID.randomUUID().toString().substring(0, 12), src = "images/materials/${target.name}",
            x = (pageW - options.outWidth * scale) / 2f, y = (pageH - options.outHeight * scale) / 2f,
            width = options.outWidth * scale, height = options.outHeight * scale,
            kind = kind, createdAt = System.currentTimeMillis(),
        )
        withContext(Dispatchers.Main) {
            controller.addImageElement(image)
            refreshThumbOf(pageId)
            toast("已插入${when (kind) { PageImageKind.TAPE -> "胶带"; PageImageKind.STICKER -> "贴纸"; else -> "图片" }}")
        }
    }

    /** 视频经 SAF 选择后复制进 notebook media 目录,再作为可选中/变换/导出的媒体元素插入。 */
    fun insertVideo(uri: Uri) {
        if (notebookId.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val nb = _state.value.notebook ?: return@launch
            val pageId = _state.value.currentPageId
            val imported = MediaImport.copyVideo(
                ServiceLocator.appContext, uri, repo.mediaDir(notebookId),
            )
            if (imported == null) {
                withContext(Dispatchers.Main) { toast("插入视频失败:无法读取该文件") }
                return@launch
            }
            val (pageW, pageH) = nb.pageMm(pageId, PageRenderer.MM_TO_PX)
            val element = MediaImport.videoElement(pageW, pageH, imported, System.currentTimeMillis())
            withContext(Dispatchers.Main) {
                controller.addMediaElement(element)
                refreshThumbOf(pageId)
                toast("已插入视频")
            }
        }
    }

    /** 停止录音后的 m4a → notebook media 目录,插入为音频元素(时长探测失败用录音计时兜底)。 */
    fun insertRecordedAudio(tempFile: File, elapsedMs: Long) {
        if (notebookId.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val nb = _state.value.notebook ?: return@launch
            val pageId = _state.value.currentPageId
            val imported = MediaImport.importRecordedAudio(tempFile, repo.mediaDir(notebookId), elapsedMs)
            if (imported == null) {
                withContext(Dispatchers.Main) { toast("保存录音失败") }
                return@launch
            }
            val (pageW, pageH) = nb.pageMm(pageId, PageRenderer.MM_TO_PX)
            val element = MediaImport.audioElement(pageW, pageH, imported, System.currentTimeMillis())
            withContext(Dispatchers.Main) {
                controller.addMediaElement(element)
                refreshThumbOf(pageId)
                toast("已插入录音")
            }
        }
    }

    /** 笔动作(笔键手势路由结果;按住修饰类由视图直接处理,不经过这里) */
    fun onPenAction(action: InkAction) {
        when (action) {
            InkAction.TOGGLE_ERASER -> toggleEraser()
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

    fun setLassoMode(mode: LassoMode) {
        controller.setLassoMode(mode)
        syncToolbarState()
    }

    fun toggleLassoFilter(filter: LassoContent) {
        val next = controller.lassoFiltersSnapshot().toMutableSet().apply {
            if (!add(filter)) remove(filter)
        }
        controller.setLassoFilters(next)
        syncToolbarState()
    }

    fun deleteSelection() = controller.deleteSelection() // 落盘经 onCommit 镜像

    fun clearSelection() = controller.clearSelection()

    fun refreshSelectionOverlay() = syncToolbarState()

    fun reorderSelection(up: Boolean) = controller.reorderSelection(up)

    fun toggleSelectionSnap() = controller.toggleSelectionSnap()

    /** 将混合选区的当前包围盒作为图片附件，沿用默认讲解意图直接发送。 */
    fun sendSelectionToAgent() {
        val box = controller.selectionSnapshot()?.box?.expand(12f) ?: return
        val nb = _state.value.notebook ?: return
        val pageId = _state.value.currentPageId
        viewModelScope.launch {
            val agent = ServiceLocator.agentController
            agent.bindPage(PageRef(nb.id, pageId))
            val name = agent.addSnip(Rect4(box.l, box.t, box.r, box.b))
            if (name == null) toast("发送失败:选区渲染不可用")
            else agent.sendWithIntent(Intents.Id.EXPLAIN)
        }
    }

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

    /**
     * 从输入框附件菜单进入「框选加入附件」:先收起抽屉让画布完全露出,
     * 框选结束后不再自动发送,只把选区挂到附件栏(并由开关决定是否自动 @)。
     */
    fun enterSnipForAttachment() {
        controller.disarmLasso()
        controller.clearSelection()
        val agent = ServiceLocator.agentController
        val wasOpen = agent.state.value.drawerOpen
        draftTextBeforeSnip = agent.state.value.draftText
        if (wasOpen) agent.closeDrawer()
        _state.value = _state.value.copy(
            snip = SnipUiState(
                phase = SnipPhase.FROZEN,
                drawerWasOpen = wasOpen,
                attachOnly = true,
            ),
        )
    }

    fun exitSnip() {
        val s = _state.value.snip
        _state.value = _state.value.copy(snip = SnipUiState())
        if (s.attachOnly && s.drawerWasOpen) restoreDrawerAfterSnip()
        draftTextBeforeSnip = null
    }

    /** 附件模式的选区落地:加进草稿附件与附件栏,不触发发送 */
    fun snipAttachOnly(viewToPage: (Float, Float) -> Pair<Float, Float>) {
        val s = _state.value.snip
        val rect = s.rect ?: return exitSnip()
        val nb = _state.value.notebook ?: return exitSnip()
        val pageId = _state.value.currentPageId
        val reopen = s.drawerWasOpen
        _state.value = _state.value.copy(snip = SnipUiState())
        viewModelScope.launch {
            val agent = ServiceLocator.agentController
            agent.bindPage(PageRef(nb.id, pageId))
            if (reopen) restoreDrawerAfterSnip()
            val rectPage = rect.normalized().let {
                val (l, t) = viewToPage(it.l, it.t)
                val (r, b) = viewToPage(it.r, it.b)
                Rect4(l, t, r, b)
            }
            val name = agent.addSnip(rectPage)
            draftTextBeforeSnip = null
            if (name == null) toast("框选失败:页面渲染不可用")
        }
    }

    /** 重新打开抽屉并恢复进入框选前的草稿文字(openDrawer 会清空输入框) */
    private fun restoreDrawerAfterSnip() {
        val nb = _state.value.notebook ?: return
        val pageId = _state.value.currentPageId
        if (pageId.isEmpty()) return
        val agent = ServiceLocator.agentController
        agent.openDrawer(PageRef(nb.id, pageId))
        draftTextBeforeSnip?.let { agent.setDraftText(it) }
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

    /** 拖动被系统取消(pointerCancel):清空未确认选区,留在 FROZEN 供重试(不执行任何动作) */
    fun snipResetDrag() {
        val s = _state.value.snip
        if (s.phase == SnipPhase.FROZEN) {
            _state.value = _state.value.copy(snip = s.copy(rect = null))
        }
    }

    /** 全页:选区扩为整页(页坐标矩形,由视图换算传入) */
    fun snipFullPage(pageRect: Rect4) {
        _state.value = _state.value.copy(snip = _state.value.snip.copy(rect = pageRect, phase = SnipPhase.CONFIRMED))
    }

    fun snipPickIntent(intent: Intents.Id) {
        _state.value = _state.value.copy(snip = _state.value.snip.copy(intent = intent))
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

    /** 摘取保存为独立卡片：位图复制到 cards/assets，不依赖 Agent 或外部服务。 */
    fun snipSaveCard(viewToPage: (Float, Float) -> Pair<Float, Float>) {
        val s = _state.value.snip
        val r = s.rect ?: return
        val nb = _state.value.notebook ?: return
        val pageId = _state.value.currentPageId
        val rect = r.normalized().let {
            val (l, t) = viewToPage(it.l, it.t)
            val (rr, b) = viewToPage(it.r, it.b)
            Rect4(l, t, rr, b)
        }
        exitSnip()
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching {
                val bitmap = PageRenderer.render(PageRef(nb.id, pageId), rect) ?: error("选区渲染不可用")
                val dir = File(ServiceLocator.appContext.filesDir, "cards/assets").apply { mkdirs() }
                val file = File(dir, "${UUID.randomUUID()}.png")
                file.outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val now = System.currentTimeMillis()
                ServiceLocator.cardRepository.upsert(NoteCard(
                    id = UUID.randomUUID().toString().substring(0, 12),
                    title = "页面摘取 $pageId", body = "", imageSrc = "assets/${file.name}",
                    sourceNotebookId = nb.id, sourcePageId = pageId, createdAt = now, updatedAt = now,
                ))
            }
            withContext(Dispatchers.Main) {
                toast(if (saved.isSuccess) "已保存到卡片" else "保存卡片失败：${saved.exceptionOrNull()?.message}")
            }
        }
    }

    // ---- 打开/切页 ----

    /** 双开分屏右侧独立控制器(支持对照阅读与直接手写批注) */
    val splitController = InkController().apply {
        onCommit = { commit -> persistSplitCommit(commit) }
    }

    /** 设置快照(open() 整体重建 UiState 时保留,避免清掉已生效的设置) */
    @Volatile private var frontBufferFlag = false

    fun open(nbId: String, asSplit: Boolean = false) {
        val allLive = repo.listNotebooks(includeTrashed = false)
        val nb = allLive.firstOrNull { it.id == nbId } ?: return
        if (asSplit && notebookId.isNotEmpty() && notebookId != nbId) {
            val currentTabs = (_state.value.openTabs.map { it.id } + nbId).distinct()
                .mapNotNull { id -> allLive.firstOrNull { it.id == id } }
            loadSplitNotebook(nb, currentTabs)
            return
        }
        if (notebookId == nbId && _state.value.notebook != null) {
            if (asSplit && !_state.value.splitEnabled) toggleSplit()
            return
        }
        pageSwitchJob?.cancel()
        pendingPageId = null
        notebookId = nbId
        repo.markOpened(nbId)
        val pages = repo.listPages(nbId)
        val cur = pages.lastOrNull()?.pageId ?: repo.newPage(nbId)
        val existingTabs = _state.value.openTabs.map { it.id }
        val tabIds = if (nbId in existingTabs) existingTabs else existingTabs + nbId
        val resolvedTabs = tabIds.mapNotNull { id -> allLive.firstOrNull { it.id == id } }
            .ifEmpty { listOf(nb) }
        val md = repo.loadPageMarkdown(nbId, cur)
        _state.value = _state.value.copy(
            notebook = nb,
            pages = repo.listPages(nbId),
            currentPageId = cur,
            openTabs = resolvedTabs,
            pageMarkdown = md,
            frontBufferEnabled = frontBufferFlag,
        )
        controller.loadStrokes(
            repo.loadPage(nbId, cur),
            repo.loadAnnotations(nbId, cur),
            repo.loadImages(nbId, cur),
            repo.loadMedia(nbId, cur),
        )
        bindAgent(nb.id, cur)
        refreshAllThumbs()
        if (asSplit) toggleSplit()
    }

    fun closeTab(tabId: String) {
        val remaining = _state.value.openTabs.filter { it.id != tabId }
        if (remaining.isEmpty()) return
        _state.value = _state.value.copy(openTabs = remaining)
        if (notebookId == tabId) {
            notebookId = ""
            open(remaining.first().id)
        }
    }

    fun toggleSplit(targetNbId: String? = null) {
        val st = _state.value
        if (st.splitEnabled && targetNbId == null) {
            _state.value = st.copy(splitEnabled = false)
            return
        }
        val allLive = repo.listNotebooks(includeTrashed = false)
        val candidate = when {
            targetNbId != null -> allLive.firstOrNull { it.id == targetNbId }
            st.splitNotebook != null && st.splitNotebook.id != notebookId -> st.splitNotebook
            else -> st.openTabs.firstOrNull { it.id != notebookId }
                ?: allLive.firstOrNull { it.id != notebookId }
                ?: st.notebook
        } ?: return
        val tabIds = (st.openTabs.map { it.id } + candidate.id).distinct()
        val tabs = tabIds.mapNotNull { id -> allLive.firstOrNull { it.id == id } }
        loadSplitNotebook(candidate, tabs)
    }

    private fun loadSplitNotebook(nb: NotebookMeta, tabs: List<NotebookMeta>) {
        val pages = repo.listPages(nb.id)
        val cur = pages.firstOrNull()?.pageId ?: repo.newPage(nb.id)
        splitController.tool = _state.value.toolSettings.effective
        splitController.loadStrokes(
            repo.loadPage(nb.id, cur),
            repo.loadAnnotations(nb.id, cur),
            repo.loadImages(nb.id, cur),
            repo.loadMedia(nb.id, cur),
        )
        _state.value = _state.value.copy(
            openTabs = tabs,
            splitEnabled = true,
            splitNotebook = nb,
            splitPages = repo.listPages(nb.id),
            splitPageId = cur,
        )
    }

    fun stepSplitPage(delta: Int) {
        val st = _state.value
        val splitNb = st.splitNotebook ?: return
        val nextId = adjacentPageId(st.splitPages, st.splitPageId, delta) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val strokes = repo.loadPage(splitNb.id, nextId)
            val cards = repo.loadAnnotations(splitNb.id, nextId)
            val images = repo.loadImages(splitNb.id, nextId)
            val media = repo.loadMedia(splitNb.id, nextId)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(splitPageId = nextId)
                splitController.loadStrokes(strokes, cards, images, media)
            }
        }
    }

    fun updatePageMarkdown(markdown: String) {
        val nbId = notebookId.takeIf { it.isNotEmpty() } ?: return
        val pageId = _state.value.currentPageId
        _state.value = _state.value.copy(pageMarkdown = markdown)
        viewModelScope.launch(Dispatchers.IO) {
            repo.savePageMarkdown(nbId, pageId, markdown)
        }
    }

    fun saveMarkdownAsCard() {
        val md = _state.value.pageMarkdown.trim()
        if (md.isBlank()) {
            toast("当前页伴随 Markdown 为空")
            return
        }
        saveTextAsCard(md)
    }

    /** 把一段文本(伴随 Markdown / Agent 回答)存为知识卡片:首个标题行为卡片名,#标签 自动提取 */
    fun saveTextAsCard(text: String) {
        val nb = _state.value.notebook ?: return
        val pageId = _state.value.currentPageId
        val body = text.trim()
        if (body.isBlank()) {
            toast("没有可保存的内容")
            return
        }
        val title = body.lineSequence().firstOrNull { it.isNotBlank() }
            ?.trimStart('#', ' ', '*')?.trim()?.takeIf { it.isNotBlank() } ?: "摘录 · $pageId"
        val tags = Regex("#([^\\s#<]+)").findAll(body).map { it.groupValues[1] }.distinct().toList()
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            ServiceLocator.cardRepository.upsert(
                NoteCard(
                    id = UUID.randomUUID().toString().substring(0, 12),
                    title = title,
                    body = body,
                    tags = tags,
                    color = nb.coverColor,
                    sourceNotebookId = nb.id,
                    sourcePageId = pageId,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            withContext(Dispatchers.Main) { toast("已存入卡片") }
        }
    }

    fun setPagePaper(paper: String) {
        val nb = _state.value.notebook ?: return
        val pageId = _state.value.currentPageId
        val updatedSpec = nb.pageSpec(pageId).copy(paper = paper)
        val updatedNb = nb.copy(paper = paper, pageSpecs = nb.pageSpecs + (pageId to updatedSpec))
        repo.updateMeta(updatedNb)
        _state.value = _state.value.copy(notebook = updatedNb)
        viewModelScope.launch { refreshThumbOf(pageId) }
    }

    fun setPagePaperColor(paperColor: String) {
        val nb = _state.value.notebook ?: return
        val pageId = _state.value.currentPageId
        val updatedSpec = nb.pageSpec(pageId).copy(paperColor = paperColor)
        val updatedNb = nb.copy(paperColor = paperColor, pageSpecs = nb.pageSpecs + (pageId to updatedSpec))
        repo.updateMeta(updatedNb)
        _state.value = _state.value.copy(notebook = updatedNb)
        viewModelScope.launch { refreshThumbOf(pageId) }
    }

    fun outlineEntries(): List<Pair<String, String>> {
        val nbId = notebookId.takeIf { it.isNotEmpty() } ?: return emptyList()
        return _state.value.pages.mapIndexed { idx, pg ->
            val md = if (pg.pageId == _state.value.currentPageId) _state.value.pageMarkdown
            else repo.loadPageMarkdown(nbId, pg.pageId)
            val heading = md.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("#") }
                ?.trimStart('#', ' ')
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    val cards = if (pg.pageId == _state.value.currentPageId) controller.annotationCards()
                    else repo.loadAnnotations(nbId, pg.pageId)
                    cards.firstOrNull()?.text?.lineSequence()?.firstOrNull()?.take(24)
                }
                ?: "第 ${idx + 1} 页"
            pg.pageId to heading
        }
    }

    private fun persistSplitCommit(commit: InkCommit) {
        val splitNb = _state.value.splitNotebook ?: return
        val page = _state.value.splitPageId
        when (commit) {
            is InkCommit.Added, is InkCommit.Tombstoned -> repo.appendCommit(splitNb.id, page, commit)
            is InkCommit.CardAdded, is InkCommit.CardRemoved ->
                repo.saveAnnotations(splitNb.id, page, splitController.annotationCards())
            is InkCommit.ImageAdded, is InkCommit.ImageRemoved ->
                repo.saveImages(splitNb.id, page, splitController.images())
            is InkCommit.MediaAdded, is InkCommit.MediaRemoved ->
                repo.saveMedia(splitNb.id, page, splitController.media())
            is InkCommit.LayersChanged -> {
                repo.appendLayerStates(splitNb.id, page, splitController.page.strokes.toList())
                repo.saveAnnotations(splitNb.id, page, splitController.annotationCards())
                repo.saveImages(splitNb.id, page, splitController.images())
                repo.saveMedia(splitNb.id, page, splitController.media())
            }
        }
    }

    private fun bindAgent(nbId: String, pageId: String) {
        ServiceLocator.agentController.bindPage(PageRef(nbId, pageId))
    }

    fun switchPage(pageId: String) {
        val st = _state.value
        if (st.pages.none { it.pageId == pageId } || pageId == pendingPageId) return
        if (pageId == st.currentPageId) {
            pageSwitchJob?.cancel()
            pendingPageId = null
            return
        }
        pendingPageId = pageId
        pageSwitchJob?.cancel()
        pageSwitchJob = viewModelScope.launch(Dispatchers.IO) {
            val strokes = repo.loadPage(notebookId, pageId)
            val cards = repo.loadAnnotations(notebookId, pageId)
            val images = repo.loadImages(notebookId, pageId)
            val media = repo.loadMedia(notebookId, pageId)
            val md = repo.loadPageMarkdown(notebookId, pageId)
            withContext(Dispatchers.Main) {
                if (pendingPageId != pageId) return@withContext
                pendingPageId = null
                _state.value = _state.value.copy(currentPageId = pageId, pageMarkdown = md)
                controller.loadStrokes(strokes, cards, images, media)
                bindAgent(notebookId, pageId)
            }
        }
    }

    fun previousPage() = stepPage(-1)

    fun nextPage() = stepPage(1)

    private fun stepPage(delta: Int) {
        val st = _state.value
        val basePageId = pendingPageId ?: st.currentPageId
        adjacentPageId(st.pages, basePageId, delta)?.let(::switchPage)
    }

    /** 缘滑到末页后继续翻页(千本:滑过末页自动新建空白页);仅缘滑路径进入,按钮不受影响 */
    fun appendPageAtEnd() {
        val st = _state.value
        if (st.pages.isEmpty()) return
        val basePageId = pendingPageId ?: st.currentPageId
        if (st.pages.last().pageId != basePageId) return
        insertPage(PageInsertPosition.END, basePageId)
        toast("已新建第 ${st.pages.size + 1} 页")
    }

    /** 页面重排(千本缩略图长按拖拽):落盘 pageOrder 后按新序刷新列表与缩略图。 */
    fun movePage(pageId: String, toIndex: Int) {
        if (notebookId.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val order = repo.reorderPage(notebookId, pageId, toIndex)
            withContext(Dispatchers.Main) {
                if (order == null) {
                    toast("移动失败:页面不存在")
                } else {
                    _state.value = _state.value.copy(pages = repo.listPages(notebookId))
                    toast("已移动到第 ${(toIndex.coerceAtLeast(0)) + 1} 页")
                }
            }
        }
    }

    /** 追加空白页(工具栏/笔键快捷入口)。 */
    fun newPage() = insertPage(PageInsertPosition.END, _state.value.currentPageId)

    /**
     * 新建空白页并切过去:位置相对 [anchorPageId],纸张沿用笔记本元数据,
     * 不实现「替换页面」(无数据模型支持,宁可暂缺也不伪造)。
     */
    fun insertPage(position: PageInsertPosition, anchorPageId: String? = _state.value.currentPageId) {
        if (notebookId.isEmpty()) return
        pageSwitchJob?.cancel()
        pendingPageId = null
        viewModelScope.launch(Dispatchers.IO) {
            val newPageId = repo.insertPage(notebookId, position, anchorPageId)
            openPageOnMain(newPageId)
        }
    }

    /** 复制页面(pb/ann/img + 共享图片资产引用)并切到副本。 */
    fun duplicatePage(pageId: String) {
        if (notebookId.isEmpty()) return
        pageSwitchJob?.cancel()
        pendingPageId = null
        viewModelScope.launch(Dispatchers.IO) {
            val copyId = repo.duplicatePage(notebookId, pageId)
            if (copyId == null) {
                withContext(Dispatchers.Main) { toast("复制失败:页面不存在") }
                return@launch
            }
            openPageOnMain(copyId)
            withContext(Dispatchers.Main) { toast("已复制页面 $pageId") }
        }
    }

    /**
     * 清除页面内容。当前页走 InkController 的 Batch 撤销栈(可撤销,落盘走既有 InkCommit);
     * 非当前页没有跨页撤销栈,直接截断磁盘并二次确认前由 UI 说明。
     */
    fun clearPage(pageId: String) {
        if (pageId == _state.value.currentPageId) {
            val cleared = controller.clearPageContent()
            if (cleared) {
                toast("已清除当前页,可撤销")
                viewModelScope.launch { refreshThumbOf(pageId) }
            } else {
                toast("当前页已是空白")
            }
            return
        }
        if (notebookId.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val cleared = repo.clearPage(notebookId, pageId)
            withContext(Dispatchers.Main) {
                if (cleared) toast("已清除页面 $pageId") else toast("页面 $pageId 不存在")
            }
            if (cleared) refreshAllThumbs()
        }
    }

    /**
     * 删除页面文件与 sidecar(图片资产文件保留)。没有封面页语义,允许删除任意页;
     * 若删到一本空本则补一张空白页,避免书写页落到无页状态。
     */
    fun deletePage(pageId: String) {
        if (notebookId.isEmpty()) return
        pageSwitchJob?.cancel()
        pendingPageId = null
        viewModelScope.launch(Dispatchers.IO) {
            val before = repo.listPages(notebookId)
            val fallback = pageIdAfterDelete(before, pageId)
            if (!repo.deletePage(notebookId, pageId)) {
                withContext(Dispatchers.Main) { toast("删除失败:页面不存在") }
                return@launch
            }
            var pages = repo.listPages(notebookId)
            if (pages.isEmpty()) {
                repo.newPage(notebookId)
                pages = repo.listPages(notebookId)
            }
            val currentBefore = _state.value.currentPageId
            val currentMissing = pages.none { it.pageId == currentBefore }
            val target = if (currentMissing) {
                fallback?.takeIf { id -> pages.any { it.pageId == id } } ?: pages.first().pageId
            } else {
                currentBefore
            }
            val targetData = if (currentMissing) {
                PageContent(
                    repo.loadPage(notebookId, target),
                    repo.loadAnnotations(notebookId, target),
                    repo.loadImages(notebookId, target),
                    repo.loadMedia(notebookId, target),
                )
            } else {
                null
            }
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(pages = pages, currentPageId = target)
                if (targetData != null) {
                    controller.loadStrokes(
                        targetData.strokes, targetData.cards, targetData.images, targetData.media,
                    )
                    bindAgent(notebookId, target)
                }
                thumbs.value = thumbs.value - pageId
                toast("已删除页面 $pageId")
            }
            refreshAllThumbs()
        }
    }

    /** IO 线程读页 → 主线程替换页状态与控制器(页面管理共用)。 */
    private suspend fun openPageOnMain(pageId: String) {
        val pages = repo.listPages(notebookId)
        val strokes = repo.loadPage(notebookId, pageId)
        val cards = repo.loadAnnotations(notebookId, pageId)
        val images = repo.loadImages(notebookId, pageId)
        val media = repo.loadMedia(notebookId, pageId)
        val md = repo.loadPageMarkdown(notebookId, pageId)
        withContext(Dispatchers.Main) {
            _state.value = _state.value.copy(pages = pages, currentPageId = pageId, pageMarkdown = md)
            controller.loadStrokes(strokes, cards, images, media)
            bindAgent(notebookId, pageId)
        }
        refreshAllThumbs()
    }

    fun rotateCurrentPage() {
        val nb = _state.value.notebook ?: return
        val pageId = _state.value.currentPageId
        val spec = nb.pageSpec(pageId)
        controller.rotateAllContent(spec.widthMm * PageRenderer.MM_TO_PX, spec.heightMm * PageRenderer.MM_TO_PX)
        val rotated = spec.copy(widthMm = spec.heightMm, heightMm = spec.widthMm)
        repo.updatePageSpec(notebookId, pageId, rotated)
        _state.value = _state.value.copy(
            notebook = nb.copy(pageSpecs = nb.pageSpecs + (pageId to rotated)),
        )
        viewModelScope.launch { refreshThumbOf(pageId) }
        toast("当前页已顺时针旋转 90°")
    }

    // ---- 工具 ----

    fun setTool(tool: InkTool) = applyToolSettings(_state.value.toolSettings.selectTool(tool))

    fun toggleEraser() = applyToolSettings(_state.value.toolSettings.toggleEraser())

    fun setColor(color: Long) = applyToolSettings(_state.value.toolSettings.selectColor(color))

    fun setPaletteColor(index: Int, color: Long) = applyToolSettings(
        _state.value.toolSettings.updatePaletteColor(index, color),
    )

    fun setWidth(mm: Float) = applyToolSettings(_state.value.toolSettings.selectWidth(mm))

    fun setEraserWidth(mm: Float) = applyToolSettings(_state.value.toolSettings.selectEraserWidth(mm))

    /** 橡皮内容开关(千本 P019);切换时保持临时橡皮态不跳笔 */
    fun setEraserTarget(tool: InkTool, enabled: Boolean) =
        applyToolSettings(_state.value.toolSettings.setEraserTarget(tool, enabled))

    fun setEraserAutoRevert(enabled: Boolean) =
        applyToolSettings(_state.value.toolSettings.setEraserAutoRevert(enabled))

    /** 当前笔压感档(千本钢笔「笔尖」/画笔「压感灵敏度」) */
    fun setPenGamma(gamma: PressureGamma) =
        applyToolSettings(_state.value.toolSettings.selectPressureGamma(gamma))

    // ---- 笔盒收藏(DESIGN-V2 pop-pen):当前书写笔的 工具/墨色/粗细 一键收藏,DataStore 持久化 ----

    val penPresets = MutableStateFlow<List<PenPreset>>(emptyList())

    fun saveCurrentPenPreset() {
        val t = _state.value.tool
        if (t.tool !in writingTools) {
            toast("只有书写笔可以收藏到笔盒")
            return
        }
        val preset = PenPreset(
            id = t.tool.id + t.color + t.baseWidthMm,
            name = "${penLabel(t.tool)} · #${"%06X".format(0xFFFFFFL and t.color)} · ${"%.1f".format(t.baseWidthMm)}mm",
            tool = t.tool, color = t.color, widthMm = t.baseWidthMm,
        )
        if (penPresets.value.any { it.id == preset.id }) {
            toast("这支笔已在笔盒中")
            return
        }
        persistPenPresets(penPresets.value + preset)
        toast("已收藏到笔盒")
    }

    fun applyPenPreset(p: PenPreset) = applyToolSettings(
        _state.value.toolSettings.selectTool(p.tool).selectColor(p.color).selectWidth(p.widthMm),
    )

    fun deletePenPreset(id: String) = persistPenPresets(penPresets.value.filter { it.id != id })

    fun clearPenPresets() = persistPenPresets(emptyList())

    private fun persistPenPresets(list: List<PenPreset>) {
        penPresets.value = list
        viewModelScope.launch(Dispatchers.IO) { ServiceLocator.appSettings.setPenPresets(PenPresets.encode(list)) }
    }

    private fun penLabel(tool: InkTool): String = when (tool) {
        InkTool.PEN -> "钢笔"
        InkTool.BALL -> "圆珠笔"
        InkTool.HIGHLIGHTER -> "荧光笔"
        else -> "图案笔"
    }

    private fun applyToolSettings(settings: WriterToolSettings) {
        controller.eraserWidthMm = settings.eraserWidthMm
        controller.eraserTargets = settings.eraserTargets
        controller.eraserAutoRevert = settings.eraserAutoRevert
        controller.tool = settings.effective
        splitController.eraserWidthMm = settings.eraserWidthMm
        splitController.eraserTargets = settings.eraserTargets
        splitController.eraserAutoRevert = settings.eraserAutoRevert
        splitController.tool = settings.effective
        _state.value = _state.value.copy(toolSettings = settings)
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
            is InkCommit.ImageAdded, is InkCommit.ImageRemoved ->
                repo.saveImages(notebookId, page, controller.images())
            is InkCommit.MediaAdded, is InkCommit.MediaRemoved ->
                repo.saveMedia(notebookId, page, controller.media())
            is InkCommit.LayersChanged -> {
                repo.appendLayerStates(notebookId, page, controller.page.strokes.toList())
                repo.saveAnnotations(notebookId, page, controller.annotationCards())
                repo.saveImages(notebookId, page, controller.images())
                repo.saveMedia(notebookId, page, controller.media())
            }
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
        val selection = controller.selectionSnapshot()
        _state.value = _state.value.copy(
            canUndo = controller.page.canUndo,
            canRedo = controller.page.canRedo,
            lassoArmed = controller.lassoArmed,
            lassoMode = controller.lassoMode,
            lassoFilters = controller.lassoFiltersSnapshot(),
            selectionCount = selection?.count ?: 0,
            selectionBox = selection?.box,
            selectionCanMoveUp = selection?.canMoveUp ?: false,
            selectionCanMoveDown = selection?.canMoveDown ?: false,
            selectionSnapEnabled = selection?.snapEnabled ?: true,
        )
    }

    // ---- 缩略图 ----

    private fun refreshAllThumbs() {
        viewModelScope.launch {
            val nbId = notebookId
            val nb = _state.value.notebook ?: return@launch
            val map = repo.listPages(nbId).associate { p ->
                val spec = nb.pageSpec(p.pageId)
                p.pageId to renderPageThumb(
                    repo.loadPage(nbId, p.pageId), repo.loadAnnotations(nbId, p.pageId), spec.paper, nb.pageSize, nb.orientation,
                    images = repo.loadImages(nbId, p.pageId), media = repo.loadMedia(nbId, p.pageId),
                    imageDir = repo.notebookDir(nbId).absolutePath,
                    paperColor = spec.paperColor, pageWidthMm = spec.widthMm, pageHeightMm = spec.heightMm,
                )
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
        val spec = nb.pageSpec(pageId)
        val strokes = controller.page.strokes.toList()
        val cards = controller.annotationCards()
        val bmp = withContext(Dispatchers.Default) {
            renderPageThumb(
                strokes, cards, spec.paper, nb.pageSize, nb.orientation,
                images = controller.images(), media = controller.media(),
                imageDir = repo.notebookDir(notebookId).absolutePath,
                paperColor = spec.paperColor, pageWidthMm = spec.widthMm, pageHeightMm = spec.heightMm,
            )
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
            val spec = nb.pageSpec(pageId)
            val data = AgentBridge.PageData(
                repo.loadPage(notebookId, pageId),
                repo.loadAnnotations(notebookId, pageId),
                spec.paper, nb.pageSize, nb.orientation,
                images = repo.loadImages(notebookId, pageId),
                media = repo.loadMedia(notebookId, pageId),
                imageDir = repo.notebookDir(notebookId).absolutePath,
                paperColor = spec.paperColor, pageWidthMm = spec.widthMm, pageHeightMm = spec.heightMm,
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
                imageDir = repo.notebookDir(notebookId).absolutePath,
                imagesOf = { pageId -> repo.loadImages(notebookId, pageId) },
                mediaOf = { pageId -> repo.loadMedia(notebookId, pageId) },
                pageSpecOf = { pageId -> nb.pageSpec(pageId) },
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
    images: List<ImageElement> = emptyList(),
    media: List<MediaElement> = emptyList(),
    imageDir: String? = null,
    paperColor: String = "#FAF9F5",
    pageWidthMm: Float? = null,
    pageHeightMm: Float? = null,
    widthPx: Int = 132,
): ImageBitmap {
    val data = AgentBridge.PageData(
        strokes, cards, paper, pageSize, orientation, images = images, media = media, imageDir = imageDir,
        paperColor = paperColor, pageWidthMm = pageWidthMm, pageHeightMm = pageHeightMm,
    )
    val (pageW, pageH) = PageRenderer.pageDimsPx(data)
    val w = widthPx.coerceAtLeast(32)
    val bmp = Bitmap.createBitmap(w, (pageH / pageW * w).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val scale = w / pageW
    canvas.scale(scale, scale)
    PageRenderer.drawAll(
        canvas,
        data,
        pageW, pageH, clip = null,
    )
    return bmp.asImageBitmap()
}

