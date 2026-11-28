package com.milknote.app.agent

import android.graphics.Bitmap
import com.milknote.agent.AnnotationSink
import com.milknote.agent.FunctionGraphSpec
import com.milknote.agent.ImageBudget
import com.milknote.agent.NotebookWorkspacePort
import com.milknote.agent.PageDimensions
import com.milknote.agent.PageElementSummary
import com.milknote.agent.PageInfo
import com.milknote.agent.PageRef
import com.milknote.agent.Rect4
import com.milknote.agent.SnipResult
import com.milknote.agent.SnipSource
import com.milknote.app.di.ServiceLocator
import com.milknote.app.graph.FunctionGraphAsset
import com.milknote.app.graph.FunctionGraphRenderer
import com.milknote.ink.input.InkController
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.ElementGeom
import com.milknote.ink.model.ElementRef
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.PageInkState
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.StrokeGeom
import com.milknote.ink.render.AnnotationCardRenderer
import com.milknote.notes.storage.NotebookRepository
import com.milknote.notes.storage.PageInsertPosition
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App 装配层(AGENT-BACKEND §2.1)::agent 端口的实现。
 * WriterViewModel 在页面打开/切换时把「渲染当前页」与「插入批注」注册进来,
 * AgentController 通过端口调用 —— :agent 不依赖 ink-engine / note-domain。
 */
object AgentBridge {

    /** 页面数据快照(渲染输入;由 WriterViewModel 提供;images/media/imageDir 可选,旧调用点零改动) */
    data class PageData(
        val strokes: List<com.milknote.ink.model.Stroke>,
        val cards: List<com.milknote.ink.model.AnnotationCard>,
        val paper: String,
        val pageSize: String,
        val orientation: String,
        val images: List<com.milknote.ink.model.ImageElement> = emptyList(),
        val media: List<com.milknote.ink.model.MediaElement> = emptyList(),
        val imageDir: String? = null,   // notebook 目录绝对路径(ImageElement/MediaElement.src 相对它解析)
        val paperColor: String = "#FAF9F5",
        val pageWidthMm: Float? = null,
        val pageHeightMm: Float? = null,
    )

    /** 渲染回调:pageId → 页面数据(null = 页面不在内存,回退磁盘读取由实现方决定) */
    @Volatile var pageProvider: (suspend (PageRef) -> PageData?)? = null

    /** 批注插入回调:返回卡 id(null = 页面不可达) */
    @Volatile var annotationInserter: (suspend (PageRef, Rect4?, String, Boolean, Float) -> String?)? = null

    /** 工作区(页管理 / 画布工具)回调:WriterViewModel 进入书写页后注册 */
    @Volatile var workspaceHandler: NotebookWorkspacePort? = null
}

/**
 * 笔记本工作区端口(:agent → 真实笔记本;AGENT-BACKEND 页管理 / 画布工具)。
 *
 * 优先转发给书写页注册的 [AgentBridge.workspaceHandler](内存控制器 + 撤销栈 + 缩略图);
 * 尚未进入书写页时回退到 [ServiceLocator.repository] 的磁盘操作 —— 此时没有撤销栈,
 * 页面元素只能整表重写 sidecar,笔迹无法程序化改写(返回 false)。
 */
class AppNotebookWorkspacePort : NotebookWorkspacePort {

    private val handler: NotebookWorkspacePort? get() = AgentBridge.workspaceHandler
    private val repository: NotebookRepository?
        get() = runCatching { ServiceLocator.repository }.getOrNull()

    override suspend fun listPages(notebookId: String): List<PageInfo> {
        handler?.let { return it.listPages(notebookId) }
        val repo = repository ?: return emptyList()
        return withContext(Dispatchers.IO) {
            repo.listPages(notebookId).mapIndexed { index, page -> PageInfo(page.pageId, index + 1, false) }
        }
    }

    override suspend fun pageDimensions(page: PageRef): PageDimensions? {
        handler?.let { return it.pageDimensions(page) }
        val repo = repository ?: return null
        return withContext(Dispatchers.IO) {
            val meta = repo.getNotebook(page.notebookId) ?: return@withContext null
            val (width, height) = meta.pageMm(page.pageId, PageRenderer.MM_TO_PX)
            PageDimensions(width, height)
        }
    }

    override suspend fun addPage(
        notebookId: String,
        position: String,
        anchorPageId: String?,
        switchTo: Boolean,
    ): PageInfo? {
        handler?.let { return it.addPage(notebookId, position, anchorPageId, switchTo) }
        val repo = repository ?: return null
        return withContext(Dispatchers.IO) {
            val insert = when (position.lowercase()) {
                "before" -> PageInsertPosition.BEFORE
                "end" -> PageInsertPosition.END
                else -> PageInsertPosition.AFTER
            }
            val pageId = runCatching { repo.insertPage(notebookId, insert, anchorPageId) }.getOrNull()
                ?: return@withContext null
            val index = repo.listPages(notebookId).indexOfFirst { it.pageId == pageId } + 1
            PageInfo(pageId, index.coerceAtLeast(1), false)
        }
    }

    override suspend fun deletePage(notebookId: String, pageId: String): Boolean {
        handler?.let { return it.deletePage(notebookId, pageId) }
        val repo = repository ?: return false
        return withContext(Dispatchers.IO) {
            if (!repo.deletePage(notebookId, pageId)) return@withContext false
            if (repo.listPages(notebookId).isEmpty()) repo.newPage(notebookId)
            true
        }
    }

    override suspend fun insertMarkdownCard(
        page: PageRef,
        markdown: String,
        xPx: Float,
        yPx: Float,
        widthPx: Float,
    ): String? {
        handler?.let { return it.insertMarkdownCard(page, markdown, xPx, yPx, widthPx) }
        val card = AnnotationCard(
            id = UUID.randomUUID().toString().substring(0, 8),
            x = xPx,
            y = yPx,
            width = widthPx.coerceAtLeast(80f),
            kind = AnnotationCard.Kind.MARKDOWN,
            text = markdown,
            createdAt = System.currentTimeMillis(),
        )
        val inserted = editOffscreen(page) { it.addAnnotationCard(card) } != null
        return if (inserted) card.id else null
    }

    override suspend fun insertFunctionGraph(
        page: PageRef,
        spec: FunctionGraphSpec,
        xPx: Float,
        yPx: Float,
        widthPx: Float,
    ): String? {
        handler?.let { return it.insertFunctionGraph(page, spec, xPx, yPx, widthPx) }
        val repo = repository ?: return null
        val pages = withContext(Dispatchers.IO) { repo.listPages(page.notebookId) }
        if (pages.none { it.pageId == page.pageId }) return null
        val dims = pageDimensions(page) ?: return null
        val bitmap = withContext(Dispatchers.Default) {
            FunctionGraphRenderer.render(spec, 1200, 800)
        }
        val png = FunctionGraphRenderer.pngBytes(bitmap)
        val imgW = bitmap.width.coerceAtLeast(1)
        val imgH = bitmap.height.coerceAtLeast(1)
        bitmap.recycle()
        val rel = withContext(Dispatchers.IO) {
            FunctionGraphAsset.savePng(File(repo.notebookDir(page.notebookId), "images"), png)
        }
        val width = widthPx.coerceIn(40f, dims.widthPx)
        val height = (width * imgH / imgW).coerceAtLeast(1f)
        val x = xPx.coerceIn(0f, (dims.widthPx - width).coerceAtLeast(0f))
        val y = yPx.coerceIn(0f, (dims.heightPx - height).coerceAtLeast(0f))
        val image = ImageElement(
            id = UUID.randomUUID().toString().substring(0, 12),
            src = rel,
            x = x,
            y = y,
            width = width,
            height = height,
            createdAt = System.currentTimeMillis(),
        )
        val inserted = editOffscreen(page) {
            it.addImageElement(image)
            true
        } ?: false
        return if (inserted) image.id else null
    }

    override suspend fun listElements(page: PageRef): List<PageElementSummary> {
        handler?.let { return it.listElements(page) }
        val dims = pageDimensions(page) ?: return emptyList()
        val drawing = pageSnapshot(page) ?: return emptyList()
        return withContext(Dispatchers.Default) {
            WorkspaceElementSummaries.of(
                drawing.strokes, drawing.cards, drawing.images, drawing.media,
                dims.widthPx, dims.heightPx,
            )
        }
    }

    override suspend fun elementTimestamps(
        page: PageRef,
        rect: Rect4,
        includeErased: Boolean,
    ) = handler?.elementTimestamps(page, rect, includeErased)
        ?: pageSnapshot(page)?.let { drawing ->
            // 平面列表与已删除笔迹已从同一份快照取得,几何在后台
            withContext(Dispatchers.Default) {
                WorkspaceElementTimestamps.of(
                    drawing.strokes, drawing.cards, drawing.images, drawing.media,
                    rect, if (includeErased) drawing.removedStrokes() else emptyList(),
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
        handler?.let { return it.moveElement(page, elementId, elementKind, targetX, targetY, dx, dy) }
        if (targetX == null && targetY == null && dx == null && dy == null) return false
        val kind = WorkspaceElementSummaries.parseKind(elementKind)
        return editOffscreen(page) {
            it.moveElementById(kind, elementId, targetX, targetY, dx, dy)
        } ?: false
    }

    override suspend fun deleteElement(page: PageRef, elementId: String, elementKind: String?): Boolean {
        handler?.let { return it.deleteElement(page, elementId, elementKind) }
        val kind = WorkspaceElementSummaries.parseKind(elementKind)
        return editOffscreen(page) { it.deleteElementById(kind, elementId) } ?: false
    }

    /** 磁盘页平面快照:单次 `loadDrawing`(页不存在 null,不伪造空页) */
    private suspend fun pageSnapshot(page: PageRef): PageInkState? {
        val repo = repository ?: return null
        return withContext(Dispatchers.IO) {
            if (repo.listPages(page.notebookId).none { it.pageId == page.pageId }) null
            else repo.loadDrawing(page.notebookId, page.pageId)
        }
    }

    /**
     * 离屏页编辑(没有书写页内存控制器时):同一页载入临时控制器,
     * 用既有 InkOp 语义改动,每条变更作为单条日志落盘 —— 与书写路径共用 Journal,
     * 不再整表重写 sidecar(否则磁盘与撤销历史会分叉)。
     * 返回 null = 页不存在;否则返回 [block] 结果。
     */
    private suspend fun <T> editOffscreen(page: PageRef, block: (InkController) -> T): T? {
        val repo = repository ?: return null
        return withContext(Dispatchers.Default) {
            val drawing = withContext(Dispatchers.IO) {
                if (repo.listPages(page.notebookId).none { it.pageId == page.pageId }) null
                else repo.loadDrawing(page.notebookId, page.pageId)
            } ?: return@withContext null
            val controller = InkController().apply { loadDrawing(drawing) }
            controller.onDrawingChange = { change ->
                repo.appendDrawingChange(page.notebookId, page.pageId, change)
            }
            block(controller)
        }
    }
}

/** 页面元素摘要(工作区端口 listElements 的唯一实现;当前页与磁盘回退共用) */
internal object WorkspaceElementSummaries {

    fun parseKind(raw: String?): ElementRef.Kind? = when (raw?.lowercase()) {
        "card", "text", "markdown", "latex" -> ElementRef.Kind.CARD
        "image", "img", "tape", "sticker" -> ElementRef.Kind.IMAGE
        "media", "audio", "video" -> ElementRef.Kind.MEDIA
        "stroke", "ink", "pen" -> ElementRef.Kind.STROKE
        else -> null
    }

    fun of(
        strokes: List<Stroke>,
        cards: List<AnnotationCard>,
        images: List<ImageElement>,
        media: List<MediaElement>,
        pageWidth: Float,
        pageHeight: Float,
    ): List<PageElementSummary> {
        val safeW = pageWidth.coerceAtLeast(1f)
        val safeH = pageHeight.coerceAtLeast(1f)
        val result = ArrayList<PageElementSummary>(strokes.size + cards.size + images.size + media.size)
        fun add(id: String, kind: String, box: StrokeGeom.Box, preview: String) {
            result.add(
                PageElementSummary(
                    id = id,
                    kind = kind,
                    x = box.l,
                    y = box.t,
                    width = box.r - box.l,
                    height = box.b - box.t,
                    xRatio = box.l / safeW,
                    yRatio = box.t / safeH,
                    preview = preview,
                ),
            )
        }
        cards.forEach { card ->
            val height = runCatching { AnnotationCardRenderer.cardHeight(card, expanded = true) }.getOrDefault(0f)
            add(card.id, "card", ElementGeom.bounds(card, height), card.text.replace('\n', ' ').take(60))
        }
        images.forEach { image ->
            add(image.id, "image", ElementGeom.bounds(image), image.src)
        }
        media.forEach { element ->
            add(
                element.id, "media", ElementGeom.bounds(element),
                "${element.kind.name.lowercase()}:${element.src}",
            )
        }
        strokes.forEach { stroke ->
            add(stroke.id, "stroke", StrokeGeom.bounds(stroke), "${stroke.tool.id}:${stroke.points.size}pts")
        }
        return result
    }
}

/**
 * 选区/整页位图编码(§3.5 图片编码预算):长边 ≤1568;PNG 优先,>2MB 降 JPEG q88。
 */
object SnipEncoder {

    fun encode(src: Bitmap): Pair<ByteArray, Boolean> {
        var bmp = src
        val longEdge = maxOf(bmp.width, bmp.height)
        if (longEdge > ImageBudget.LONG_EDGE_PX) {
            val scale = ImageBudget.LONG_EDGE_PX.toFloat() / longEdge
            bmp = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        }
        val png = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        if (!ImageBudget.shouldFallbackToJpeg(png.size)) return png to false
        val jpg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
        return if (jpg.size < png.size) jpg to true else png to false
    }
}

/** InAppSnipSource(§4.1):离屏软件渲染(纸面+墨迹+批注全层),矢量重绘非截屏 */
class AppSnipSource(
    private val render: suspend (PageRef, Rect4?) -> Bitmap?,
) : SnipSource {

    override suspend fun captureSnip(page: PageRef, rect: Rect4): SnipResult? {
        val bmp = render(page, rect) ?: return null
        val (bytes, jpeg) = SnipEncoder.encode(bmp)
        return SnipResult(
            png = bytes, jpeg = jpeg,
            widthPx = bmp.width, heightPx = bmp.height,
            logicalName = if (jpeg) {
                "snip_${bmp.width}×${bmp.height}.jpg"
            } else {
                ImageBudget.logicalNameSnip(bmp.width, bmp.height)
            },
        )
    }

    override suspend fun capturePage(page: PageRef): SnipResult? {
        val bmp = render(page, null) ?: return null
        val (bytes, jpeg) = SnipEncoder.encode(bmp)
        return SnipResult(
            png = bytes, jpeg = jpeg,
            widthPx = bmp.width, heightPx = bmp.height,
            logicalName = ImageBudget.logicalNamePage(page.pageId),
        )
    }

    override suspend fun pagesOf(notebookId: String): List<PageRef> =
        pagesListProvider?.invoke(notebookId) ?: emptyList()

    /** 页 id 列表提供方(WriterViewModel 注册;整章组装用) */
    var pagesListProvider: (suspend (String) -> List<PageRef>)? = null
}

/** AnnotationSink(§9):Agent 结果卡 → 页面批注层 + 撤销栈(经 AgentBridge 转发到 WriterViewModel) */
class AppAnnotationSink : AnnotationSink {
    override suspend fun insertCard(
        page: PageRef,
        anchor: Rect4?,
        text: String,
        latex: Boolean,
        cardWidth: Float,
    ): String? = AgentBridge.annotationInserter?.invoke(page, anchor, text, latex, cardWidth)
}
