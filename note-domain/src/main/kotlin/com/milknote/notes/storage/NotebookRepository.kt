package com.milknote.notes.storage

import com.milknote.ink.input.InkCommit
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.DrawingChange
import com.milknote.ink.model.ElementRef
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkOp
import com.milknote.ink.model.LayerState
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.PageInkState
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.orderedElements
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
 *     p0001.pb           # 页面 = StrokeRecord 追加流(基线)
 *     p0001.jrnl         # 操作日志(新增/删除/换层/undo/redo 的增量帧)
 *     p0001.ann.json     # 批注卡 sidecar(基线)
 *     p0001.img.json     # 图片元素 sidecar(基线)
 *     p0001.media.json   # 音视频元素 sidecar(基线)
 * ```
 * 写入策略:每 UI Batch 追加一条操作日志帧(append-only,崩溃安全);原 `.pb`/sidecar
 * 仅作为日志建立前的基线,不再逐笔重写。读取时回放日志,恢复内容与撤销/重做两栈。
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
    /** 当前线程正在执行写队列任务(此时读取直接看锁内状态,避免自锁)。 */
    private val inWriteOp = ThreadLocal<Boolean>()
    /** 每个 `.jrnl` 已验证的有效文件尾(受 writeLock 保护);追加时用于 O(1) 截断残尾。 */
    private val journalEnds = HashMap<String, Long>()

    init {
        rootDir.mkdirs()
        scope.launch(Dispatchers.IO) {
            for (op in writeQueue) {
                inWriteOp.set(true)
                try {
                    runCatching { synchronized(writeLock) { op() } }
                } finally {
                    inWriteOp.remove()
                }
            }
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

    private fun journalFile(notebookId: String, pageId: String): File =
        File(dirOf(notebookId), "$pageId${DrawingJournal.FILE_SUFFIX}")

    private fun journalKey(notebookId: String, pageId: String): String =
        journalFile(notebookId, pageId).absolutePath

    private fun forgetJournalCache(dir: File) {
        val prefix = dir.absolutePath + File.separator
        journalEnds.keys.removeAll { it.startsWith(prefix) }
    }

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
        trashed.forEach {
            val dir = dirOf(it.id)
            dir.deleteRecursively()
            forgetJournalCache(dir)
        }
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
        withPageWrite {
            val dir = dirOf(notebookId)
            dir.deleteRecursively()
            forgetJournalCache(dir)
        }
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
        // 页号复用(删过最高页)时清理旧日志,避免历史数据复活
        journalFile(notebookId, pageId).delete()
        journalEnds.remove(journalKey(notebookId, pageId))

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
        val sourceJournal = journalFile(notebookId, sourcePageId).exists()
        if (sourceJournal) {
            // 有操作日志时复制「当前内容」为独立基线,不复制源历史
            val state = readDrawingForRewriteLocked(notebookId, sourcePageId)
                ?: return@withPageWrite null
            writeBaselineLocked(dir, pageId, state)
            copySidecar(dir, "$sourcePageId.md", "$pageId.md")
        } else {
            source.copyTo(pageFile(notebookId, pageId), overwrite = true)
            copySidecar(dir, "$sourcePageId.ann.json", "$pageId.ann.json")
            copySidecar(dir, "$sourcePageId.img.json", "$pageId.img.json")
            copySidecar(dir, "$sourcePageId.media.json", "$pageId.media.json")
            copySidecar(dir, "$sourcePageId.md", "$pageId.md")
        }
        journalFile(notebookId, pageId).delete()
        journalEnds.remove(journalKey(notebookId, pageId))
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
     * 清除页面内容(笔迹/批注卡/图片元素/音视频元素)但保留页面文件与 Markdown。
     * - 无操作日志:沿用旧行为(截断 pb + 删除三份 sidecar),不可撤销。
     * - 有操作日志:追加一条“删除当前全部元素”的日志帧,不截断基线,重启后仍可撤销。
     * 图片/音视频资产文件保留(可能被其他页面共享引用)。
     */
    fun clearPage(notebookId: String, pageId: String): Boolean = withPageWrite {
        val f = pageFile(notebookId, pageId)
        if (!f.exists()) return@withPageWrite false
        val dir = dirOf(notebookId)
        if (journalFile(notebookId, pageId).exists()) {
            val state = readDrawingForRewriteLocked(notebookId, pageId)
                ?: return@withPageWrite false
            val ops = buildList {
                state.strokes.indices.reversed().forEach { add(InkOp.Remove(state.strokes[it], it)) }
                state.cards.indices.reversed().forEach { add(InkOp.RemoveCard(state.cards[it], it)) }
                state.images.indices.reversed().forEach { add(InkOp.RemoveImage(state.images[it], it)) }
                state.media.indices.reversed().forEach { add(InkOp.RemoveMedia(state.media[it], it)) }
            }
            if (ops.isNotEmpty()) {
                appendJournalLocked(notebookId, pageId, DrawingChange.Apply(InkOp.Batch(ops)))
            }
            touchUpdatedAtLocked(notebookId)
            return@withPageWrite true
        }
        FileOutputStream(f, false).close()
        f.setLastModified(System.currentTimeMillis())
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
        journalFile(notebookId, pageId).delete()
        journalEnds.remove(journalKey(notebookId, pageId))
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
        awaitWrites()
        return synchronized(writeLock) { readDrawingLocked(notebookId, pageId).strokes.toList() }
    }

    /**
     * 一次性恢复页面内容与撤销/重做两栈(分离副本;同步调用,返回前等待本实例写队列)。
     * 无操作日志时退化为旧 `.pb` + sidecar 基线,两栈为空。
     */
    fun loadDrawing(notebookId: String, pageId: String): PageInkState {
        awaitWrites()
        return synchronized(writeLock) { readDrawingLocked(notebookId, pageId) }
    }

    /** 记录一条统一绘图变更;每个 UI Batch 一帧,顺序与页面写入队列一致。 */
    fun appendDrawingChange(notebookId: String, pageId: String, change: DrawingChange) {
        writeQueue.trySend {
            if (!pageFile(notebookId, pageId).exists()) return@trySend // 页已删除:丢弃迟到回调
            appendJournalLocked(notebookId, pageId, change)
        }
    }

    // ---- 增量写入(InkController.onCommit / onDrawingChange → append) ----

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
            if (!pageFile(notebookId, pageId).exists()) return@trySend // 页已删除:丢弃迟到回调
            if (journalFile(notebookId, pageId).exists()) {
                // 日志已建立:旧 API 转为日志 mutation,绝不追加回基线 .pb
                val state = readDrawingLocked(notebookId, pageId)
                when (commit) {
                    is InkCommit.Added -> if (state.strokes.none { it.id == commit.stroke.id }) {
                        appendJournalLocked(
                            notebookId, pageId, DrawingChange.Apply(InkOp.Add(commit.stroke)),
                        )
                    }
                    is InkCommit.Tombstoned -> {
                        val index = state.strokes.indexOfFirst { it.id == commit.strokeId }
                        if (index >= 0) {
                            appendJournalLocked(
                                notebookId, pageId,
                                DrawingChange.Apply(InkOp.Remove(state.strokes[index], index)),
                            )
                        }
                    }
                    else -> {}
                }
                return@trySend
            }
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
            if (!pageFile(notebookId, pageId).exists()) return@trySend
            if (journalFile(notebookId, pageId).exists()) {
                val state = readDrawingLocked(notebookId, pageId)
                val current = state.strokes.associateBy { it.id }
                if (strokes.none { current[it.id]?.zIndex != it.zIndex }) return@trySend
                val before = state.orderedElements().map { LayerState(it.ref, it.zIndex) }
                val after = strokes.map { LayerState(ElementRef(ElementRef.Kind.STROKE, it.id), it.zIndex) }
                appendJournalLocked(
                    notebookId, pageId, DrawingChange.Apply(InkOp.Reorder(before, after)),
                )
                return@trySend
            }
            val f = pageFile(notebookId, pageId)
            FileOutputStream(f, true).use { out ->
                strokes.forEach { ProtoInk.writeRecord(out, ProtoInk.recordOf(it)) }
            }
            f.setLastModified(System.currentTimeMillis())
        }
    }

    // ---- 读取:基线 + 日志回放 ----

    private fun readDrawingLocked(notebookId: String, pageId: String): PageInkState {
        // 页不存在时不回放孤儿日志/侧车,避免删除后又被直接 loadDrawing 复活。
        if (!pageFile(notebookId, pageId).exists()) return PageInkState()
        val dir = dirOf(notebookId)
        val baseline = baselineStateLocked(dir, pageId)
        val journal = journalFile(notebookId, pageId)
        if (!journal.exists()) return baseline
        val read = try {
            DrawingJournal.read(journal)
        } catch (_: DrawingJournalCorruptException) {
            return baseline // 前缀不可信时保基线可读,不崩溃;追加会再次报错
        }
        journalEnds[journalKey(notebookId, pageId)] = read.validEnd
        return try {
            DrawingJournal.replay(baseline, read.frames)
        } catch (_: DrawingJournalCorruptException) {
            baselineStateLocked(dir, pageId) // 回放失败:回到基线,至少不丢旧笔记内容
        }
    }

    /**
     * 供复制/清除等改写操作使用:日志不可完整回放时返回 null,绝不拿基线冒充现态。
     */
    private fun readDrawingForRewriteLocked(notebookId: String, pageId: String): PageInkState? {
        if (!pageFile(notebookId, pageId).exists()) return null
        val dir = dirOf(notebookId)
        val baseline = baselineStateLocked(dir, pageId)
        val journal = journalFile(notebookId, pageId)
        if (!journal.exists()) return baseline
        return try {
            val read = DrawingJournal.read(journal)
            journalEnds[journalKey(notebookId, pageId)] = read.validEnd
            DrawingJournal.replay(baseline, read.frames)
        } catch (_: DrawingJournalCorruptException) {
            null
        }
    }

    private fun baselineStateLocked(dir: File, pageId: String): PageInkState {
        val state = PageInkState()
        state.restoreDrawing(
            strokes = readStrokesFile(File(dir, "$pageId.pb")),
            cards = readSidecar<AnnotationCard>(dir, "$pageId.ann.json"),
            images = readSidecar<ImageElement>(dir, "$pageId.img.json"),
            media = readSidecar<MediaElement>(dir, "$pageId.media.json"),
            undo = emptyList(),
            redo = emptyList(),
        )
        return state
    }

    private fun readStrokesFile(file: File): List<Stroke> = runCatching {
        if (!file.exists()) emptyList()
        else file.inputStream().buffered().use { ProtoInk.readPage(it) }
    }.getOrDefault(emptyList())

    private inline fun <reified T> readSidecar(dir: File, name: String): List<T> = runCatching {
        val file = File(dir, name)
        if (!file.exists()) emptyList() else json.decodeFromString<List<T>>(file.readText())
    }.getOrDefault(emptyList())

    private fun appendJournalLocked(notebookId: String, pageId: String, change: DrawingChange) {
        val key = journalKey(notebookId, pageId)
        val end = DrawingJournal.append(journalFile(notebookId, pageId), change, journalEnds[key])
        journalEnds[key] = end
    }

    /** 把回放后的现态写成独立基线(复制页/迁移用);不复制源历史。 */
    private fun writeBaselineLocked(dir: File, pageId: String, state: PageInkState) {
        FileOutputStream(File(dir, "$pageId.pb"), false).use { out ->
            state.strokes.forEach { ProtoInk.writeRecord(out, ProtoInk.recordOf(it)) }
        }
        writeJsonAtomic(File(dir, "$pageId.ann.json"), json.encodeToString(state.cards))
        writeJsonAtomic(File(dir, "$pageId.img.json"), json.encodeToString(state.images))
        writeJsonAtomic(File(dir, "$pageId.media.json"), json.encodeToString(state.media))
    }

    private fun writeJsonAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) error("sidecar save failed: ${target.name}")
        }
    }

    private fun syncElementsLocked(
        notebookId: String,
        pageId: String,
        sync: (PageInkState) -> List<InkOp>,
    ) {
        if (!journalFile(notebookId, pageId).exists()) return
        val ops = sync(readDrawingLocked(notebookId, pageId))
        if (ops.isEmpty()) return
        appendJournalLocked(notebookId, pageId, DrawingChange.Apply(InkOp.Batch(ops)))
    }

    private fun <T> elementDiff(
        current: List<T>,
        desired: List<T>,
        id: (T) -> String,
        add: (T) -> InkOp,
        remove: (T, Int) -> InkOp,
        /** 更新已存在元素时补回缺失的 createdAt(0 = 旧数据/未提供),避免整表保存抹掉时间。 */
        keepCreatedAt: (current: T, wanted: T) -> T = { _, wanted -> wanted },
    ): List<InkOp> {
        val wanted = desired.map { item ->
            val existing = current.firstOrNull { id(it) == id(item) }
            if (existing == null) item else keepCreatedAt(existing, item)
        }
        val desiredIds = wanted.mapTo(HashSet(wanted.size)) { id(it) }
        val ops = mutableListOf<InkOp>()
        current.withIndex().reversed().forEach { (index, element) ->
            if (id(element) !in desiredIds) ops += remove(element, index)
        }
        wanted.forEach { item ->
            val index = current.indexOfFirst { id(it) == id(item) }
            when {
                index < 0 -> ops += add(item)
                current[index] != item -> {
                    ops += remove(current[index], index)
                    ops += add(item)
                }
            }
        }
        return ops
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

    /** 排空追加写队列(在写队列任务内调用时不等待,避免自锁)。 */
    private fun awaitWrites() {
        if (inWriteOp.get() == true) return
        runBlocking {
            val barrier = CompletableDeferred<Unit>()
            if (writeQueue.trySend { barrier.complete(Unit) }.isSuccess) {
                withTimeoutOrNull(5_000) { barrier.await() }
            }
        }
    }

    /** 排空追加写队列后再在写锁内执行页面管理,保证复制/清除看到最新落盘内容。 */
    private fun <T> withPageWrite(block: () -> T): T {
        awaitWrites()
        return synchronized(writeLock) { block() }
    }

    // ---- 批注卡(Agent 结果贴回;JSON sidecar,整表重写;AGENT-BACKEND §7.2 同目录备份) ----

    /** 笔记本目录(<id>.milk;Agent 会话/附件随整本备份) */
    fun notebookDir(notebookId: String): File = dirOf(notebookId)

    /** 音视频资产目录(<id>.milk/media/;MediaElement.src 相对 notebookDir) */
    fun mediaDir(notebookId: String): File = File(dirOf(notebookId), "media").apply { mkdirs() }

    fun loadAnnotations(notebookId: String, pageId: String): List<AnnotationCard> =
        readPagePart(notebookId, pageId) { it.cards.toList() }

    /** 整表重写(原子替换);日志已建立时转为日志 mutation,不改基线 sidecar */
    fun saveAnnotations(notebookId: String, pageId: String, cards: List<AnnotationCard>) {
        val snapshot = cards.toList()
        writeQueue.trySend {
            if (!pageFile(notebookId, pageId).exists()) return@trySend
            if (journalFile(notebookId, pageId).exists()) {
                syncElementsLocked(notebookId, pageId) { state ->
                    elementDiff(
                        state.cards, snapshot, { it.id },
                        { InkOp.AddCard(it) },
                        { card, index -> InkOp.RemoveCard(card, index) },
                        { old, wanted ->
                            if (wanted.createdAt == 0L && old.createdAt > 0L) {
                                wanted.copy(createdAt = old.createdAt)
                            } else wanted
                        },
                    )
                }
                return@trySend
            }
            writeJsonAtomic(File(dirOf(notebookId), "$pageId.ann.json"), json.encodeToString(snapshot))
        }
    }

    // ---- 图片元素(贴图层;JSON sidecar `<pageId>.img.json`,整表原子重写;参照批注卡)----

    fun loadImages(notebookId: String, pageId: String): List<ImageElement> =
        readPagePart(notebookId, pageId) { it.images.toList() }

    /** 整表重写(原子替换);日志已建立时转为日志 mutation,不改基线 sidecar */
    fun saveImages(notebookId: String, pageId: String, images: List<ImageElement>) {
        val snapshot = images.toList()
        writeQueue.trySend {
            if (!pageFile(notebookId, pageId).exists()) return@trySend
            if (journalFile(notebookId, pageId).exists()) {
                syncElementsLocked(notebookId, pageId) { state ->
                    elementDiff(
                        state.images, snapshot, { it.id },
                        { InkOp.AddImage(it) },
                        { image, index -> InkOp.RemoveImage(image, index) },
                        { old, wanted ->
                            if (wanted.createdAt == 0L && old.createdAt > 0L) {
                                wanted.copy(createdAt = old.createdAt)
                            } else wanted
                        },
                    )
                }
                return@trySend
            }
            writeJsonAtomic(File(dirOf(notebookId), "$pageId.img.json"), json.encodeToString(snapshot))
        }
    }

    // ---- 音视频元素(本地录制/导入;JSON sidecar `<pageId>.media.json`,整表原子重写)----

    fun loadMedia(notebookId: String, pageId: String): List<MediaElement> =
        readPagePart(notebookId, pageId) { it.media.toList() }

    /** 整表重写(原子替换);日志已建立时转为日志 mutation,不改基线 sidecar */
    fun saveMedia(notebookId: String, pageId: String, media: List<MediaElement>) {
        val snapshot = media.toList()
        writeQueue.trySend {
            if (!pageFile(notebookId, pageId).exists()) return@trySend
            if (journalFile(notebookId, pageId).exists()) {
                syncElementsLocked(notebookId, pageId) { state ->
                    elementDiff(
                        state.media, snapshot, { it.id },
                        { InkOp.AddMedia(it) },
                        { item, index -> InkOp.RemoveMedia(item, index) },
                        { old, wanted ->
                            if (wanted.createdAt == 0L && old.createdAt > 0L) {
                                wanted.copy(createdAt = old.createdAt)
                            } else wanted
                        },
                    )
                }
                return@trySend
            }
            writeJsonAtomic(File(dirOf(notebookId), "$pageId.media.json"), json.encodeToString(snapshot))
        }
    }

    private fun <T> readPagePart(notebookId: String, pageId: String, select: (PageInkState) -> T): T {
        awaitWrites()
        return synchronized(writeLock) { select(readDrawingLocked(notebookId, pageId)) }
    }
}
