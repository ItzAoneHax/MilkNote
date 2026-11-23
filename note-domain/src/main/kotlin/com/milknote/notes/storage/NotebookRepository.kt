package com.milknote.notes.storage

import com.milknote.ink.input.InkCommit
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.Stroke
import com.milknote.ink.serialize.ProtoInk
import com.milknote.notes.model.NotebookMeta
import com.milknote.notes.model.NotebookPage
import com.milknote.notes.model.PageSpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** 新建页相对锚点页的位置;END = 追加到末尾(既有 newPage 语义)。 */
enum class PageInsertPosition { BEFORE, AFTER, END }

/**
 * 笔记本仓库:本地优先,一个笔记本 = 一个 `xxx.milk` 目录(DESIGN-V0 §6.2):
 * ```
 * notebooks/
 *   <name>.milk/
 *     notebook.json      # 元数据(含 pageOrder)
 *     p0001.pb           # 页面 = StrokeRecord 追加流
 *     p0002.pb
 * ```
 * 写入策略:每笔抬起即追加(append-only,崩溃安全);undo/redo 追加反向记录。
 * 全部落盘走单线程写队列(顺序保证),在调用方提供的 CoroutineScope 上执行。
 * 页面管理(插入/复制/清除/删除)先排空写队列再在同一把锁内落盘,避免与追加写交错。
 */
class NotebookRepository(
    private val rootDir: File,
    private val scope: CoroutineScope,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val writeQueue = Channel<() -> Unit>(Channel.UNLIMITED)
    private val writeLock = Any()

    init {
        rootDir.mkdirs()
        scope.launch(Dispatchers.IO) {
            for (op in writeQueue) runCatching { synchronized(writeLock) { op() } }
        }
    }

    private fun notebooksDir() = File(rootDir, "notebooks").apply { mkdirs() }
    fun notebooksRoot(): File = notebooksDir()
    private fun dirOf(notebookId: String): File {
        require(Regex("[A-Za-z0-9_-]{1,128}").matches(notebookId)) { "Invalid notebook id" }
        return File(notebooksDir(), "$notebookId.milk")
    }
    private fun pageFile(notebookId: String, pageId: String): File =
        File(dirOf(notebookId), "$pageId.pb")

    // ---- 列表 ----

    fun listNotebooks(includeTrashed: Boolean = false): List<NotebookMeta> =
        notebooksDir().listFiles { f -> f.isDirectory && f.name.endsWith(".milk") }
            ?.mapNotNull { readMeta(it) }
            ?.filter { includeTrashed || !it.trashed }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

    fun listTrashedNotebooks(): List<NotebookMeta> =
        listNotebooks(includeTrashed = true).filter { it.trashed }

    fun getNotebook(notebookId: String): NotebookMeta? = synchronized(writeLock) {
        readMeta(dirOf(notebookId))
    }

    fun setPinned(notebookId: String, pinned: Boolean): NotebookMeta? = synchronized(writeLock) {
        val dir = dirOf(notebookId)
        val meta = readMeta(dir) ?: return@synchronized null
        val updated = meta.copy(pinned = pinned)
        File(dir, "notebook.json").writeText(json.encodeToString(updated))
        updated
    }

    fun moveToTrash(notebookId: String): NotebookMeta? = synchronized(writeLock) {
        val dir = dirOf(notebookId)
        val meta = readMeta(dir) ?: return@synchronized null
        val updated = meta.copy(trashed = true, trashedAt = System.currentTimeMillis())
        File(dir, "notebook.json").writeText(json.encodeToString(updated))
        updated
    }

    fun restoreFromTrash(notebookId: String): NotebookMeta? = synchronized(writeLock) {
        val dir = dirOf(notebookId)
        val meta = readMeta(dir) ?: return@synchronized null
        val updated = meta.copy(trashed = false, trashedAt = 0L)
        File(dir, "notebook.json").writeText(json.encodeToString(updated))
        updated
    }

    fun emptyTrash(): Int = withPageWrite {
        val trashed = listTrashedNotebooks()
        trashed.forEach { dirOf(it.id).deleteRecursively() }
        trashed.size
    }

    /** 回收站保留策略:删除移入超过 [days] 天的笔记本(0 = 永久保留);返回清理数量。 */
    fun purgeExpiredTrash(days: Int, now: Long = System.currentTimeMillis()): Int {
        if (days <= 0) return 0
        val cutoff = now - days * 86_400_000L
        val expired = listTrashedNotebooks().filter { it.trashedAt in 1 until cutoff }
        expired.forEach { dirOf(it.id).deleteRecursively() }
        return expired.size
    }

    /** 页序由 notebook.json 的 pageOrder 决定;旧 .milk 无该字段时按 pageId 字典序。 */
    fun listPages(notebookId: String): List<NotebookPage> = synchronized(writeLock) {
        val dir = dirOf(notebookId)
        val byId = dir.listFiles { f -> f.isFile && f.name.endsWith(".pb") }
            ?.associateBy { it.name.removeSuffix(".pb") }
            ?: return emptyList()
        orderedPageIds(dir, byId.keys).mapNotNull { pageId ->
            byId[pageId]?.let { NotebookPage(notebookId, pageId, it.lastModified()) }
        }
    }

    fun pageCount(notebookId: String): Int = listPages(notebookId).size

    // ---- 建立与删除 ----

    fun create(
        name: String,
        coverColor: String,
        paper: String,
        purpose: String,
        pageSize: String = "B5",
        orientation: String = "landscape",
        paperColor: String = "#FAF9F5",
    ): NotebookMeta {
        val now = System.currentTimeMillis()
        var id = UUID.randomUUID().toString().substring(0, 8)
        while (dirOf(id).exists()) id = UUID.randomUUID().toString().substring(0, 8)
        val meta = NotebookMeta(
            id = id,
            name = name.trim().ifBlank { "未命名笔记" },
            coverColor = coverColor,
            paper = paper,
            purpose = purpose,
            pageSize = pageSize,
            orientation = orientation,
            pageOrder = listOf("p0001"),
            paperColor = paperColor,
            createdAt = now,
            updatedAt = now,
            lastOpened = now,
        )
        val dir = dirOf(meta.id)
        dir.mkdirs()
        File(dir, "notebook.json").writeText(json.encodeToString(meta))
        touchNewPage(meta.id)
        return meta
    }

    fun delete(notebookId: String) {
        withPageWrite { dirOf(notebookId).deleteRecursively() }
    }

    fun updateMeta(meta: NotebookMeta) = synchronized(writeLock) {
        val dir = dirOf(meta.id)
        if (!dir.exists()) return@synchronized
        File(dir, "notebook.json").writeText(json.encodeToString(meta))
    }

    fun touchUpdatedAt(notebookId: String) = synchronized(writeLock) {
        val dir = dirOf(notebookId)
        val m = readMeta(dir) ?: return@synchronized
        File(dir, "notebook.json").writeText(
            json.encodeToString(m.copy(updatedAt = System.currentTimeMillis())),
        )
    }

    fun markOpened(notebookId: String): NotebookMeta? = synchronized(writeLock) {
        val dir = dirOf(notebookId)
        val meta = readMeta(dir) ?: return@synchronized null
        val opened = meta.copy(lastOpened = System.currentTimeMillis())
        File(dir, "notebook.json").writeText(json.encodeToString(opened))
        opened
    }

    fun updatePageSpec(notebookId: String, pageId: String, spec: PageSpec) = synchronized(writeLock) {
        val dir = dirOf(notebookId)
        val meta = readMeta(dir) ?: return@synchronized
        File(dir, "notebook.json").writeText(
            json.encodeToString(meta.copy(pageSpecs = meta.pageSpecs + (pageId to spec))),
        )
    }

    // ---- 页面 ----

    /** 追加到最后一页(既有语义;工具栏/笔键快捷新建)。 */
    fun newPage(notebookId: String): String = insertPage(notebookId, PageInsertPosition.END)

    /**
     * 新建空白页并放到 [position] 指定位置:
     * - BEFORE/AFTER 相对 [anchorPageId];锚点不存在时退化为末尾。
     * - 复用笔记本既有纸张(paper/pageSize/orientation 存在 notebook.json,不逐页覆盖)。
     * 通过 pageOrder 记录页序,不改名既有页面文件,旧 .milk 无需迁移。
     */
    fun insertPage(
        notebookId: String,
        position: PageInsertPosition,
        anchorPageId: String? = null,
    ): String = withPageWrite {
        val dir = dirOf(notebookId)
        dir.mkdirs()
        val existing = pageIds(dir)
        val pageId = nextPageId(existing)
        pageFile(notebookId, pageId).createNewFile()

        val order = orderedPageIds(dir, existing).toMutableList()
        val anchorIndex = anchorPageId?.let { order.indexOf(it) } ?: -1
        val insertAt = when (position) {
            PageInsertPosition.BEFORE -> if (anchorIndex >= 0) anchorIndex else order.size
            PageInsertPosition.AFTER -> if (anchorIndex >= 0) anchorIndex + 1 else order.size
            PageInsertPosition.END -> order.size
        }
        order.add(insertAt.coerceIn(0, order.size), pageId)
        writePageOrderLocked(notebookId, order)
        pageId
    }

    /**
     * 复制页面:复制 pb(笔迹追加流)与 ann/img/media 三份 sidecar,插到源页之后。
     * 图片/音视频元素里的 `src` 是相对 notebookDir 的共享资产引用,**不复制资产文件**。
     * 源页不存在返回 null。
     */
    fun duplicatePage(notebookId: String, sourcePageId: String): String? = withPageWrite {
        val dir = dirOf(notebookId)
        val source = pageFile(notebookId, sourcePageId)
        if (!source.exists()) return@withPageWrite null
        val existing = pageIds(dir)
        val pageId = nextPageId(existing)
        source.copyTo(pageFile(notebookId, pageId), overwrite = true)
        copySidecar(dir, "$sourcePageId.ann.json", "$pageId.ann.json")
        copySidecar(dir, "$sourcePageId.img.json", "$pageId.img.json")
        copySidecar(dir, "$sourcePageId.media.json", "$pageId.media.json")
        copySidecar(dir, "$sourcePageId.md", "$pageId.md")
        File(dir, "$pageId.ann.json.tmp").delete()
        File(dir, "$pageId.img.json.tmp").delete()
        File(dir, "$pageId.media.json.tmp").delete()
        File(dir, "$pageId.md.tmp").delete()

        val order = orderedPageIds(dir, existing).toMutableList()
        val sourceIndex = order.indexOf(sourcePageId)
        order.add((if (sourceIndex >= 0) sourceIndex + 1 else order.size).coerceIn(0, order.size), pageId)
        writePageOrderLocked(notebookId, order)
        readMeta(dir)?.pageSpecs?.get(sourcePageId)?.let { updatePageSpec(notebookId, pageId, it) }
        pageId
    }

    /**
     * 页面重排(千本笔记缩略图长按拖拽 P058 对齐):把 [pageId] 移动到目标下标。
     * 通过 pageOrder 落盘,不改名页面文件;页面不存在返回 null,成功返回新页序。
     */
    fun reorderPage(notebookId: String, pageId: String, toIndex: Int): List<String>? = withPageWrite {
        val dir = dirOf(notebookId)
        val existing = pageIds(dir)
        val order = orderedPageIds(dir, existing).toMutableList()
        val from = order.indexOf(pageId)
        if (from < 0) return@withPageWrite null
        order.removeAt(from)
        order.add(toIndex.coerceIn(0, order.size), pageId)
        writePageOrderLocked(notebookId, order)
        order.toList()
    }

    /**
     * 清除页面内容(笔迹/批注卡/图片元素/音视频元素)但保留页面文件。
     * 直接截断 pb 并删除三份 sidecar,不可撤销;需要撤销时应由当前页走 InkController。
     * 图片/音视频资产文件保留(可能被其他页面共享引用)。
     */
    fun clearPage(notebookId: String, pageId: String): Boolean = withPageWrite {
        val f = pageFile(notebookId, pageId)
        if (!f.exists()) return@withPageWrite false
        FileOutputStream(f, false).close()
        f.setLastModified(System.currentTimeMillis())
        val dir = dirOf(notebookId)
        File(dir, "$pageId.ann.json").delete()
        File(dir, "$pageId.img.json").delete()
        File(dir, "$pageId.media.json").delete()
        File(dir, "$pageId.ann.json.tmp").delete()
        File(dir, "$pageId.img.json.tmp").delete()
        File(dir, "$pageId.media.json.tmp").delete()
        touchUpdatedAtLocked(notebookId)
        true
    }

    /**
     * 删除页面文件与三份 sidecar,并从 pageOrder 移除;图片/音视频资产文件保留。
     * 本仓库没有「封面页」语义(第 1 页也是普通页),因此允许删除最后一页;是否补空白页由调用方决定。
     */
    fun deletePage(notebookId: String, pageId: String): Boolean = withPageWrite {
        val dir = dirOf(notebookId)
        val f = pageFile(notebookId, pageId)
        if (!f.exists()) return@withPageWrite false
        if (!f.delete()) return@withPageWrite false
        File(dir, "$pageId.ann.json").delete()
        File(dir, "$pageId.img.json").delete()
        File(dir, "$pageId.media.json").delete()
        File(dir, "$pageId.md").delete()
        File(dir, "$pageId.ann.json.tmp").delete()
        File(dir, "$pageId.img.json.tmp").delete()
        File(dir, "$pageId.media.json.tmp").delete()
        File(dir, "$pageId.md.tmp").delete()
        writePageOrderLocked(notebookId, orderedPageIds(dir, pageIds(dir)))
        readMeta(dir)?.let { meta ->
            File(dir, "notebook.json").writeText(
                json.encodeToString(meta.copy(pageSpecs = meta.pageSpecs - pageId)),
            )
        }
        touchUpdatedAtLocked(notebookId)
        true
    }

    /** 逐页伴随 Markdown 笔记（<pageId>.md，随 .milk 目录本地存储与备份） */
    fun loadPageMarkdown(notebookId: String, pageId: String): String = runCatching {
        val f = File(dirOf(notebookId), "$pageId.md")
        if (!f.exists()) "" else f.readText()
    }.getOrDefault("")

    fun savePageMarkdown(notebookId: String, pageId: String, markdown: String) = synchronized(writeLock) {
        val dir = dirOf(notebookId)
        if (!dir.exists()) return@synchronized
        val f = File(dir, "$pageId.md")
        if (markdown.isBlank()) {
            f.delete()
            File(dir, "$pageId.md.tmp").delete()
            return@synchronized
        }
        val tmp = File(dir, "$pageId.md.tmp")
        tmp.writeText(markdown)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    /** 首次创建笔记本时的第 1 页 */
    private fun touchNewPage(notebookId: String): String {
        val f = pageFile(notebookId, "p0001")
        f.createNewFile()
        return "p0001"
    }

    fun loadPage(notebookId: String, pageId: String): List<Stroke> {
        val f = pageFile(notebookId, pageId)
        if (!f.exists()) return emptyList()
        return f.inputStream().buffered().use { ProtoInk.readPage(it) }
    }

    // ---- 增量写入(InkController.onCommit → append) ----

    fun appendCommit(notebookId: String, pageId: String, commit: InkCommit) {
        when (commit) {
            // 批注卡/图片/音视频不走 pb 追加流:由调用方走 saveAnnotations / saveImages / saveMedia 整表重写(JSON sidecar)
            is InkCommit.CardAdded, is InkCommit.CardRemoved -> return
            is InkCommit.ImageAdded, is InkCommit.ImageRemoved -> return
            is InkCommit.MediaAdded, is InkCommit.MediaRemoved -> return
            is InkCommit.LayersChanged -> return
            is InkCommit.Added, is InkCommit.Tombstoned -> {}
        }
        writeQueue.trySend {
            val f = pageFile(notebookId, pageId)
            FileOutputStream(f, true).use { out ->
                when (commit) {
                    is InkCommit.Added -> ProtoInk.writeRecord(out, ProtoInk.recordOf(commit.stroke))
                    is InkCommit.Tombstoned -> ProtoInk.writeRecord(out, ProtoInk.tombstoneOf(commit.strokeId))
                    is InkCommit.CardAdded, is InkCommit.CardRemoved -> {}
                    is InkCommit.ImageAdded, is InkCommit.ImageRemoved -> {}   // 上方已 return,不可达
                    is InkCommit.MediaAdded, is InkCommit.MediaRemoved -> {}
                    is InkCommit.LayersChanged -> {}
                }
            }
            f.setLastModified(System.currentTimeMillis())
        }
    }

    /** 调层后刷新全部笔迹的 zIndex；读取时按 zIndex 排序，不依赖追加位置。 */
    fun appendLayerStates(notebookId: String, pageId: String, strokes: List<Stroke>) {
        writeQueue.trySend {
            val f = pageFile(notebookId, pageId)
            FileOutputStream(f, true).use { out ->
                strokes.forEach { ProtoInk.writeRecord(out, ProtoInk.recordOf(it)) }
            }
            f.setLastModified(System.currentTimeMillis())
        }
    }

    private fun readMeta(dir: File): NotebookMeta? = runCatching {
        val f = File(dir, "notebook.json")
        if (!f.exists()) return null
        json.decodeFromString<NotebookMeta>(f.readText())
    }.getOrNull()

    // ---- 页序与页面管理落盘 ----

    private fun pageIds(dir: File): Set<String> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".pb") }
            ?.map { it.name.removeSuffix(".pb") }
            ?.toSet()
            ?: emptySet()

    /** 已存在页面按元数据页序排列;无元数据/新页回退字典序(旧 .milk 兼容)。 */
    private fun orderedPageIds(dir: File, existing: Set<String>): List<String> {
        val stored = readMeta(dir)?.pageOrder.orEmpty().filter { it in existing }
        return stored + existing.filter { it !in stored }.sorted()
    }

    private fun nextPageId(existing: Collection<String>): String {
        val next = (existing.mapNotNull { it.removePrefix("p").toIntOrNull() }.maxOrNull() ?: 0) + 1
        return "p%04d".format(next)
    }

    private fun copySidecar(dir: File, from: String, to: String) {
        val source = File(dir, from)
        val target = File(dir, to)
        if (source.exists()) source.copyTo(target, overwrite = true) else target.delete()
    }

    private fun writePageOrderLocked(notebookId: String, order: List<String>) {
        val dir = dirOf(notebookId)
        val meta = readMeta(dir) ?: return
        File(dir, "notebook.json").writeText(
            json.encodeToString(meta.copy(pageOrder = order, updatedAt = System.currentTimeMillis())),
        )
    }

    private fun touchUpdatedAtLocked(notebookId: String) {
        val dir = dirOf(notebookId)
        val meta = readMeta(dir) ?: return
        File(dir, "notebook.json").writeText(
            json.encodeToString(meta.copy(updatedAt = System.currentTimeMillis())),
        )
    }

    /** 排空追加写队列后再在写锁内执行页面管理,保证复制/清除看到最新落盘内容。 */
    private fun <T> withPageWrite(block: () -> T): T {
        runBlocking {
            val barrier = CompletableDeferred<Unit>()
            if (writeQueue.trySend { barrier.complete(Unit) }.isSuccess) {
                withTimeoutOrNull(5_000) { barrier.await() }
            }
        }
        return synchronized(writeLock) { block() }
    }

    // ---- 批注卡(Agent 结果贴回;JSON sidecar,整表重写;AGENT-BACKEND §7.2 同目录备份) ----

    /** 笔记本目录(<id>.milk;Agent 会话/附件随整本备份) */
    fun notebookDir(notebookId: String): File = dirOf(notebookId)

    /** 音视频资产目录(<id>.milk/media/;MediaElement.src 相对 notebookDir) */
    fun mediaDir(notebookId: String): File = File(dirOf(notebookId), "media").apply { mkdirs() }

    fun loadAnnotations(notebookId: String, pageId: String): List<AnnotationCard> = runCatching {
        val f = File(dirOf(notebookId), "$pageId.ann.json")
        if (!f.exists()) return emptyList()
        json.decodeFromString<List<AnnotationCard>>(f.readText())
    }.getOrDefault(emptyList())

    /** 整表重写(原子替换);走单线程写队列与笔迹追加保序 */
    fun saveAnnotations(notebookId: String, pageId: String, cards: List<AnnotationCard>) {
        writeQueue.trySend {
            val dir = dirOf(notebookId)
            val f = File(dir, "$pageId.ann.json")
            val tmp = File(dir, "$pageId.ann.json.tmp")
            tmp.writeText(json.encodeToString(cards))
            if (!tmp.renameTo(f)) {
                f.delete()
                if (!tmp.renameTo(f)) error("annotation save failed: $pageId")
            }
        }
    }

    // ---- 图片元素(贴图层;JSON sidecar `<pageId>.img.json`,整表原子重写;参照批注卡)----

    fun loadImages(notebookId: String, pageId: String): List<ImageElement> = runCatching {
        val f = File(dirOf(notebookId), "$pageId.img.json")
        if (!f.exists()) return emptyList()
        json.decodeFromString<List<ImageElement>>(f.readText())
    }.getOrDefault(emptyList())

    /** 整表重写(原子替换:临时文件 + rename);走单线程写队列与笔迹追加保序 */
    fun saveImages(notebookId: String, pageId: String, images: List<ImageElement>) {
        writeQueue.trySend {
            val dir = dirOf(notebookId)
            val f = File(dir, "$pageId.img.json")
            val tmp = File(dir, "$pageId.img.json.tmp")
            tmp.writeText(json.encodeToString(images))
            if (!tmp.renameTo(f)) {
                f.delete()
                if (!tmp.renameTo(f)) error("image list save failed: $pageId")
            }
        }
    }

    // ---- 音视频元素(本地录制/导入;JSON sidecar `<pageId>.media.json`,整表原子重写)----

    fun loadMedia(notebookId: String, pageId: String): List<MediaElement> = runCatching {
        val f = File(dirOf(notebookId), "$pageId.media.json")
        if (!f.exists()) return emptyList()
        json.decodeFromString<List<MediaElement>>(f.readText())
    }.getOrDefault(emptyList())

    /** 整表重写(原子替换:临时文件 + rename);走单线程写队列与笔迹追加保序 */
    fun saveMedia(notebookId: String, pageId: String, media: List<MediaElement>) {
        writeQueue.trySend {
            val dir = dirOf(notebookId)
            val f = File(dir, "$pageId.media.json")
            val tmp = File(dir, "$pageId.media.json.tmp")
            tmp.writeText(json.encodeToString(media))
            if (!tmp.renameTo(f)) {
                f.delete()
                if (!tmp.renameTo(f)) error("media list save failed: $pageId")
            }
        }
    }
}
