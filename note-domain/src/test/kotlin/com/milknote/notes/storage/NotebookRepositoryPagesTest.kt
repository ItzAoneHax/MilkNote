package com.milknote.notes.storage

import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.Stroke
import com.milknote.ink.serialize.ProtoInk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

/**
 * 页面管理:插入/复制/清除/删除。
 * 页序由 notebook.json 的 pageOrder 承载;旧 .milk 无该字段时按 pageId 字典序。
 */
class NotebookRepositoryPagesTest {

    private val root: File = Files.createTempDirectory("milknote-pages-test").toFile()
    private val repo = NotebookRepository(root, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private fun stroke(id: String) = Stroke(
        id = id,
        tool = InkTool.PEN,
        color = 0xFF23221E,
        baseWidthMm = 0.7f,
        points = listOf(
            InkPoint(0f, 0f, 0.5f, 0f, 0f, 1L),
            InkPoint(10f, 10f, 0.5f, 0f, 0f, 9L),
        ),
    )

    private fun card(id: String) = AnnotationCard(id = id, x = 1f, y = 2f, width = 100f, text = "t", createdAt = 1L)

    private fun image(id: String, src: String = "images/shared.png") = ImageElement(
        id = id, src = src, x = 3f, y = 4f, width = 50f, height = 60f, createdAt = 1L,
    )

    private fun pageFile(notebookId: String, pageId: String) =
        File(repo.notebookDir(notebookId), "$pageId.pb")

    private fun writeStroke(notebookId: String, pageId: String, id: String) {
        FileOutputStream(pageFile(notebookId, pageId), true).use {
            ProtoInk.writeRecord(it, ProtoInk.recordOf(stroke(id)))
        }
    }

    private fun ids(notebookId: String) = repo.listPages(notebookId).map { it.pageId }

    @Test fun `新建页支持之前、之后与最后一页`() {
        val nb = repo.create("页序", "#3B5BEC", "dot", "课堂笔记")
        repo.newPage(nb.id)
        repo.newPage(nb.id)
        assertEquals(listOf("p0001", "p0002", "p0003"), ids(nb.id))

        val before = repo.insertPage(nb.id, PageInsertPosition.BEFORE, "p0002")
        assertEquals(listOf("p0001", before, "p0002", "p0003"), ids(nb.id))

        val after = repo.insertPage(nb.id, PageInsertPosition.AFTER, "p0002")
        assertEquals(listOf("p0001", before, "p0002", after, "p0003"), ids(nb.id))

        val last = repo.insertPage(nb.id, PageInsertPosition.END)
        assertEquals(listOf("p0001", before, "p0002", after, "p0003", last), ids(nb.id))
    }

    @Test fun `旧 milk 无 pageOrder 时按 pageId 排序且可插入`() {
        val dir = File(root, "notebooks/old.milk").apply { mkdirs() }
        File(dir, "notebook.json").writeText(
            """
            {"id":"old","name":"旧本","coverColor":"#3B5BEC","paper":"dot","purpose":"课堂笔记",
             "pageSize":"A4","orientation":"portrait","createdAt":0,"updatedAt":0}
            """.trimIndent(),
        )
        File(dir, "p0001.pb").createNewFile()
        File(dir, "p0002.pb").createNewFile()

        assertEquals(listOf("p0001", "p0002"), ids("old"))
        val inserted = repo.insertPage("old", PageInsertPosition.BEFORE, "p0002")
        assertEquals(listOf("p0001", inserted, "p0002"), ids("old"))
    }

    @Test fun `复制页复制 pb 与 ann img 并共享图片资产引用`() {
        val nb = repo.create("复制", "#D2483C", "line", "错题本")
        val source = ids(nb.id).first()
        val following = repo.newPage(nb.id)
        writeStroke(nb.id, source, "s1")
        repo.saveAnnotations(nb.id, source, listOf(card("c1")))
        repo.saveImages(nb.id, source, listOf(image("i1")))
        val assets = File(repo.notebookDir(nb.id), "images").apply { mkdirs() }
        val asset = File(assets, "shared.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val copy = repo.duplicatePage(nb.id, source)
        assertNotNull(copy)
        assertEquals(listOf(source, copy!!, following), ids(nb.id))
        assertEquals(listOf("s1"), repo.loadPage(nb.id, copy).map { it.id })
        assertEquals(listOf("c1"), repo.loadAnnotations(nb.id, copy).map { it.id })
        assertEquals(listOf("images/shared.png"), repo.loadImages(nb.id, copy).map { it.src })
        // 图片资产不复制文件,两页引用同一相对路径
        assertEquals(listOf("images/shared.png"), repo.loadImages(nb.id, source).map { it.src })
        assertTrue(asset.exists())
        assertEquals(listOf("shared.png"), assets.list()!!.toList())
    }

    @Test fun `清除页清空笔迹与两份 sidecar 但保留图片资产`() {
        val nb = repo.create("清除", "#2E9E6B", "grid", "阅读摘录")
        val pageId = ids(nb.id).first()
        writeStroke(nb.id, pageId, "s1")
        repo.saveAnnotations(nb.id, pageId, listOf(card("c1")))
        repo.saveImages(nb.id, pageId, listOf(image("i1")))
        val asset = File(File(repo.notebookDir(nb.id), "images").apply { mkdirs() }, "shared.png")
            .apply { writeBytes(byteArrayOf(1)) }

        assertTrue(repo.clearPage(nb.id, pageId))
        assertTrue(pageFile(nb.id, pageId).exists())
        assertEquals(0L, pageFile(nb.id, pageId).length())
        assertEquals(emptyList<Stroke>(), repo.loadPage(nb.id, pageId))
        assertEquals(emptyList<AnnotationCard>(), repo.loadAnnotations(nb.id, pageId))
        assertEquals(emptyList<ImageElement>(), repo.loadImages(nb.id, pageId))
        assertFalse(File(repo.notebookDir(nb.id), "$pageId.ann.json").exists())
        assertFalse(File(repo.notebookDir(nb.id), "$pageId.img.json").exists())
        assertTrue(asset.exists())
    }

    @Test fun `删除页删除 pb 与 sidecar 并更新页序`() {
        val nb = repo.create("删除", "#7C5CE0", "blank", "灵感草稿")
        val first = ids(nb.id).first()
        val second = repo.newPage(nb.id)
        writeStroke(nb.id, second, "s2")
        repo.saveAnnotations(nb.id, second, listOf(card("c2")))
        repo.saveImages(nb.id, second, listOf(image("i2")))
        val asset = File(File(repo.notebookDir(nb.id), "images").apply { mkdirs() }, "shared.png")
            .apply { writeBytes(byteArrayOf(1)) }

        assertTrue(repo.deletePage(nb.id, second))
        assertEquals(listOf(first), ids(nb.id))
        assertFalse(pageFile(nb.id, second).exists())
        assertFalse(File(repo.notebookDir(nb.id), "$second.ann.json").exists())
        assertFalse(File(repo.notebookDir(nb.id), "$second.img.json").exists())
        assertTrue(asset.exists())
        assertNull(repo.duplicatePage(nb.id, second))
    }

    @Test fun `删除唯一页成功 不伪造封面页语义`() {
        val nb = repo.create("单页", "#2C8CC8", "dot", "课堂笔记")
        val only = ids(nb.id).single()
        assertTrue(repo.deletePage(nb.id, only))
        assertEquals(emptyList<String>(), ids(nb.id))
    }

    @Test fun `重排页写入 pageOrder 并在重开后保持`() {
        val nb = repo.create("重排", "#3B5BEC", "dot", "课堂笔记")
        val first = ids(nb.id).first()
        val second = repo.newPage(nb.id)
        val third = repo.newPage(nb.id)
        assertEquals(listOf(first, second, third), ids(nb.id))

        // 千本缩略图长按拖拽:把第 3 页移到最前,第 1 页自动变为封面
        val order = repo.reorderPage(nb.id, third, 0)
        assertEquals(listOf(third, first, second), order)
        assertEquals(listOf(third, first, second), ids(nb.id))

        val reopened = NotebookRepository(root, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        assertEquals(listOf(third, first, second), reopened.listPages(nb.id).map { it.pageId })
    }

    @Test fun `重排不存在的页返回 null 且原页序不变`() {
        val nb = repo.create("重排守卫", "#D2483C", "grid", "错题本")
        val first = ids(nb.id).first()
        assertNull(repo.reorderPage(nb.id, "p9999", 0))
        assertEquals(listOf(first), ids(nb.id))
    }
}
