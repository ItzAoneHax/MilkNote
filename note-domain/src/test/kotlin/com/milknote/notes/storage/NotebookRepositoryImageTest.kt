package com.milknote.notes.storage

import com.milknote.ink.model.ImageElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 图片元素落盘:`<pageId>.img.json` 整表原子重写(参照批注卡 .ann.json)。
 * 写入走单线程写队列(异步),测试以轮询等待落盘完成。
 */
class NotebookRepositoryImageTest {

    private val root: File = Files.createTempDirectory("milknote-repo-test").toFile()
    private val repo = NotebookRepository(root, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private fun img(id: String, src: String = "images/$id.png") = ImageElement(
        id = id, src = src, x = 12f, y = 34f, width = 200f, height = 100f, createdAt = id.hashCode().toLong(),
    )

    /** 写队列异步:轮询直到断言成立(超时 5s) */
    private fun await(timeoutMs: Long = 5_000, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > deadline) error("condition not met within timeout")
            Thread.sleep(20)
        }
    }

    @Test fun `saveImages 到 loadImages roundtrip`() {
        val nb = repo.create("图本", "#3B5BEC", "dot", "课堂笔记")
        val pageId = repo.listPages(nb.id).first().pageId

        repo.saveImages(nb.id, pageId, listOf(img("a"), img("b")))
        await { repo.loadImages(nb.id, pageId).size == 2 }

        val back = repo.loadImages(nb.id, pageId)
        assertEquals(listOf("a", "b"), back.map { it.id })
        assertEquals("images/a.png", back[0].src)
        assertEquals(12f, back[0].x, 1e-4f)
        assertEquals(100f, back[0].height, 1e-4f)
        assertEquals("a".hashCode().toLong(), back[0].createdAt)

        val f = File(repo.notebookDir(nb.id), "$pageId.img.json")
        assertTrue(f.exists())
        assertFalse(File(repo.notebookDir(nb.id), "$pageId.img.json.tmp").exists())   // 原子替换无残留
    }

    @Test fun `整表重写 - 覆盖旧表且损坏残留不致命`() {
        val nb = repo.create("图本2", "#D2483C", "line", "错题本")
        val pageId = repo.listPages(nb.id).first().pageId

        repo.saveImages(nb.id, pageId, listOf(img("a"), img("b")))
        await { repo.loadImages(nb.id, pageId).size == 2 }
        repo.saveImages(nb.id, pageId, listOf(img("c")))
        await { repo.loadImages(nb.id, pageId).size == 1 }
        assertEquals(listOf("c"), repo.loadImages(nb.id, pageId).map { it.id })

        // 旧笔记本/空页 → 空列表,不抛
        assertEquals(emptyList<ImageElement>(), repo.loadImages(nb.id, "p9999"))
        assertEquals(emptyList<ImageElement>(), repo.loadImages("ghost", "p0001"))
    }

    @Test fun `批注卡与图片互不干扰(同一页面两份 sidecar)`() {
        val nb = repo.create("图本3", "#2E9E6B", "grid", "阅读摘录")
        val pageId = repo.listPages(nb.id).first().pageId

        val card = com.milknote.ink.model.AnnotationCard(
            id = "card1", x = 0f, y = 0f, width = 320f, text = "hello", createdAt = 1L,
        )
        repo.saveAnnotations(nb.id, pageId, listOf(card))
        repo.saveImages(nb.id, pageId, listOf(img("img1")))
        await { repo.loadAnnotations(nb.id, pageId).size == 1 }
        await { repo.loadImages(nb.id, pageId).size == 1 }

        assertEquals(listOf("card1"), repo.loadAnnotations(nb.id, pageId).map { it.id })
        assertEquals(listOf("img1"), repo.loadImages(nb.id, pageId).map { it.id })

        // 图片重写不影响批注卡文件
        repo.saveImages(nb.id, pageId, listOf(img("img2")))
        await { repo.loadImages(nb.id, pageId).map { it.id } == listOf("img2") }
        assertEquals(listOf("card1"), repo.loadAnnotations(nb.id, pageId).map { it.id })
        assertFalse(File(repo.notebookDir(nb.id), "$pageId.ann.json.tmp").exists())
    }
}
