package com.milknote.notes.storage

import com.milknote.notes.model.PageSpec
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookFusionMetadataTest {
    private val root = Files.createTempDirectory("milknote-meta-test").toFile()
    private val repo = NotebookRepository(root, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    @Test
    fun `旧 notebook 缺省新字段并保留原页面几何`() {
        val dir = File(root, "notebooks/old.milk").apply { mkdirs() }
        File(dir, "notebook.json").writeText("""{"id":"old","name":"旧本","createdAt":0,"updatedAt":0,"pageSize":"A4","orientation":"portrait"}""")
        File(dir, "p0001.pb").createNewFile()
        val meta = repo.listNotebooks().single()
        assertEquals(0L, meta.lastOpened)
        assertEquals("", meta.folder)
        assertEquals("#FAF9F5", meta.paperColor)
        assertEquals(PageSpec(210f, 297f, "dot", "#FAF9F5"), meta.pageSpec("p0001"))
    }

    @Test
    fun `重命名标签文件夹与 lastOpened 持久化且不破坏页序`() {
        val created = repo.create("原名", "#3B5BEC", "dot", "课堂笔记")
        val updatedAt = created.updatedAt
        val opened = repo.markOpened(created.id)!!
        assertTrue(opened.lastOpened > 0)
        assertEquals(updatedAt, opened.updatedAt)
        repo.updateMeta(opened.copy(name = "新名", tagColor = "#D2483C", folder = "考研"))
        val read = repo.listNotebooks().single()
        assertEquals("新名", read.name)
        assertEquals("#D2483C", read.tagColor)
        assertEquals("考研", read.folder)
        assertEquals(listOf("p0001"), read.pageOrder)
        assertEquals(opened.lastOpened, read.lastOpened)
    }

    @Test
    fun `逐页 spec 复制保留且删除清理`() {
        val created = repo.create("PDF", "#3B5BEC", "blank", "阅读摘录")
        repo.updatePageSpec(created.id, "p0001", PageSpec(300f, 200f, "blank", "#FFFFFF"))
        val copy = repo.duplicatePage(created.id, "p0001")!!
        val spec = repo.listNotebooks().single().pageSpec(copy)
        assertEquals(PageSpec(300f, 200f, "blank", "#FFFFFF"), spec)
        assertTrue(repo.deletePage(created.id, copy))
        val remaining = repo.listNotebooks().single().pageSpecs
        assertEquals(setOf("p0001"), remaining.keys)
        assertEquals(PageSpec(300f, 200f, "blank", "#FFFFFF"), remaining["p0001"])
    }

    @Test
    fun `星标 回收站软删除与逐页伴随 Markdown 持久化`() {
        val created = repo.create("高数", "#2563EB", "dot", "课堂笔记")
        assertEquals(false, created.pinned)
        assertEquals(false, created.trashed)

        val pinned = repo.setPinned(created.id, true)!!
        assertTrue(pinned.pinned)
        assertTrue(repo.listNotebooks().single().pinned)

        repo.savePageMarkdown(created.id, "p0001", "### §3.1 特征值\n[[线性代数]] #数学")
        assertEquals("### §3.1 特征值\n[[线性代数]] #数学", repo.loadPageMarkdown(created.id, "p0001"))

        val trashed = repo.moveToTrash(created.id)!!
        assertTrue(trashed.trashed)
        assertTrue(repo.listNotebooks().isEmpty())
        assertEquals(listOf(created.id), repo.listTrashedNotebooks().map { it.id })

        val restored = repo.restoreFromTrash(created.id)!!
        assertEquals(false, restored.trashed)
        assertEquals(listOf(created.id), repo.listNotebooks().map { it.id })

        repo.moveToTrash(created.id)
        assertEquals(1, repo.emptyTrash())
        assertTrue(repo.listNotebooks(includeTrashed = true).isEmpty())
    }
}
