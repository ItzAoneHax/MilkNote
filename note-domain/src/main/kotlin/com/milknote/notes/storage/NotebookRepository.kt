package com.milknote.notes.storage

import com.milknote.ink.input.InkCommit
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.Stroke
import com.milknote.ink.serialize.ProtoInk
import com.milknote.notes.model.NotebookMeta
import com.milknote.notes.model.NotebookPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 笔记本仓库:本地优先,一个笔记本 = 一个 `xxx.milk` 目录(DESIGN-V0 §6.2):
 * ```
 * notebooks/
 *   <name>.milk/
 *     notebook.json      # 元数据
 *     p0001.pb           # 页面 = StrokeRecord 追加流
 *     p0002.pb
 * ```
 * 写入策略:每笔抬起即追加(append-only,崩溃安全);undo/redo 追加反向记录。
 * 全部落盘走单线程写队列(顺序保证),在调用方提供的 CoroutineScope 上执行。
 */
class NotebookRepository(
    private val rootDir: File,
    private val scope: CoroutineScope,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val writeQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        rootDir.mkdirs()
        scope.launch(Dispatchers.IO) {
            for (op in writeQueue) runCatching { op() }
        }
    }

    private fun notebooksDir() = File(rootDir, "notebooks").apply { mkdirs() }
    private fun dirOf(notebookId: String): File = File(notebooksDir(), "$notebookId.milk")
    private fun pageFile(notebookId: String, pageId: String): File =
        File(dirOf(notebookId), "$pageId.pb")

    // ---- 列表 ----

    fun listNotebooks(): List<NotebookMeta> =
        notebooksDir().listFiles { f -> f.isDirectory && f.name.endsWith(".milk") }
            ?.mapNotNull { readMeta(it) }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

    fun listPages(notebookId: String): List<NotebookPage> =
        dirOf(notebookId).listFiles { f -> f.name.endsWith(".pb") }
            ?.map { NotebookPage(notebookId, it.name.removeSuffix(".pb"), it.lastModified()) }
            ?.sortedBy { it.pageId }
            ?: emptyList()

    fun pageCount(notebookId: String): Int = listPages(notebookId).size

    // ---- 建立与删除 ----

    fun create(
        name: String,
        coverColor: String,
        paper: String,
        purpose: String,
        pageSize: String = "B5",
        orientation: String = "landscape",
    ): NotebookMeta {
        val now = System.currentTimeMillis()
        val meta = NotebookMeta(
            id = UUID.randomUUID().toString().substring(0, 8),
            name = name.trim().ifBlank { "未命名笔记" },
            coverColor = coverColor,
            paper = paper,
            purpose = purpose,
            pageSize = pageSize,
            orientation = orientation,
            createdAt = now,
            updatedAt = now,
        )
        val dir = dirOf(meta.id)
        dir.mkdirs()
        File(dir, "notebook.json").writeText(json.encodeToString(meta))
        touchNewPage(meta.id)
        return meta
    }

    fun delete(notebookId: String) {
        dirOf(notebookId).deleteRecursively()
    }

    fun updateMeta(meta: NotebookMeta) {
        val dir = dirOf(meta.id)
        if (!dir.exists()) return
        File(dir, "notebook.json").writeText(json.encodeToString(meta))
    }

    fun touchUpdatedAt(notebookId: String) {
        val m = readMeta(dirOf(notebookId)) ?: return
        updateMeta(m.copy(updatedAt = System.currentTimeMillis()))
    }

    // ---- 页面 ----

    fun newPage(notebookId: String): String {
        val next = (listPages(notebookId).maxOfOrNull { it.pageId.removePrefix("p").toIntOrNull() ?: 0 } ?: 0) + 1
        val pageId = "p%04d".format(next)
        pageFile(notebookId, pageId).createNewFile()
        touchUpdatedAt(notebookId)
        return pageId
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
            // 批注卡/图片不走 pb 追加流:由调用方走 saveAnnotations / saveImages 整表重写(JSON sidecar)
            is InkCommit.CardAdded, is InkCommit.CardRemoved -> return
            is InkCommit.ImageAdded, is InkCommit.ImageRemoved -> return
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
                }
            }
            f.setLastModified(System.currentTimeMillis())
        }
    }

    private fun readMeta(dir: File): NotebookMeta? = runCatching {
        val f = File(dir, "notebook.json")
        if (!f.exists()) return null
        json.decodeFromString<NotebookMeta>(f.readText())
    }.getOrNull()

    // ---- 批注卡(Agent 结果贴回;JSON sidecar,整表重写;AGENT-BACKEND §7.2 同目录备份) ----

    /** 笔记本目录(<id>.milk;Agent 会话/附件随整本备份) */
    fun notebookDir(notebookId: String): File = dirOf(notebookId)

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
}
