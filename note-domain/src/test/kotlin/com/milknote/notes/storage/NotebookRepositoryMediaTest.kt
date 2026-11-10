package com.milknote.notes.storage

import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.MediaKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 媒体 sidecar(`<pageId>.media.json`)与页面管理:
 * 原子整表读写、复制页复制 sidecar、清除/删除页删除 sidecar,共享媒体资产保留。
 */
class NotebookRepositoryMediaTest {

    private val root: File = Files.createTempDirectory("milknote-media-test").toFile()
    private val repo = NotebookRepository(root, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private fun media(id: String, src: String = "media/audio-shared.m4a") = MediaElement(
        id = id,
        kind = MediaKind.AUDIO,
        src = src,
        x = 12f,
        y = 24f,
        width = 320f,
        height = 70f,
        durationMs = 4321L,
        createdAt = 7L,
    )

    private fun sidecar(notebookId: String, pageId: String) =
        File(repo.notebookDir(notebookId), "$pageId.media.json")

    private fun awaitMedia(notebookId: String, pageId: String, count: Int) {
        repeat(400) {
            if (repo.loadMedia(notebookId, pageId).size == count) return
            Thread.sleep(10)
        }
        assertEquals(count, repo.loadMedia(notebookId, pageId).size)
    }

    @Test fun `媒体 sidecar 整表写入并读取`() {
        val nb = repo.create("媒体", "#3B5BEC", "dot", "课堂笔记")
        val pageId = repo.listPages(nb.id).first().pageId
        repo.saveMedia(nb.id, pageId, listOf(media("m1"), media("m2")))
        awaitMedia(nb.id, pageId, 2)

        assertEquals(listOf("m1", "m2"), repo.loadMedia(nb.id, pageId).map { it.id })
        assertTrue(sidecar(nb.id, pageId).isFile)
        assertEquals(emptyList<MediaElement>(), repo.loadMedia(nb.id, "p9999"))
    }

    @Test fun `复制页复制媒体 sidecar 并共享资产`() {
        val nb = repo.create("复制媒体", "#D2483C", "line", "错题本")
        val source = repo.listPages(nb.id).first().pageId
        val following = repo.newPage(nb.id)
        repo.saveMedia(nb.id, source, listOf(media("m1")))
        val asset = File(repo.mediaDir(nb.id), "audio-shared.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val copy = repo.duplicatePage(nb.id, source)
        assertNotNull(copy)
        assertEquals(listOf(source, copy!!, following), repo.listPages(nb.id).map { it.pageId })
        assertEquals(listOf("m1"), repo.loadMedia(nb.id, copy).map { it.id })
        assertEquals(listOf("media/audio-shared.m4a"), repo.loadMedia(nb.id, copy).map { it.src })
        assertEquals(listOf("media/audio-shared.m4a"), repo.loadMedia(nb.id, source).map { it.src })
        assertTrue(asset.exists())
        assertEquals(1, repo.mediaDir(nb.id).listFiles()!!.size)
    }

    @Test fun `清除页删除媒体 sidecar 但保留资产`() {
        val nb = repo.create("清除媒体", "#2E9E6B", "grid", "阅读摘录")
        val pageId = repo.listPages(nb.id).first().pageId
        repo.saveMedia(nb.id, pageId, listOf(media("m1")))
        val asset = File(repo.mediaDir(nb.id), "audio-shared.m4a").apply { writeBytes(byteArrayOf(1)) }
        awaitMedia(nb.id, pageId, 1)

        assertTrue(repo.clearPage(nb.id, pageId))
        assertEquals(emptyList<MediaElement>(), repo.loadMedia(nb.id, pageId))
        assertFalse(sidecar(nb.id, pageId).exists())
        assertTrue(asset.exists())
    }

    @Test fun `删除页删除媒体 sidecar 并保留资产`() {
        val nb = repo.create("删除媒体", "#7C5CE0", "blank", "灵感草稿")
        val first = repo.listPages(nb.id).first().pageId
        val second = repo.newPage(nb.id)
        repo.saveMedia(nb.id, second, listOf(media("m2")))
        val asset = File(repo.mediaDir(nb.id), "audio-shared.m4a").apply { writeBytes(byteArrayOf(1)) }
        awaitMedia(nb.id, second, 1)

        assertTrue(repo.deletePage(nb.id, second))
        assertEquals(listOf(first), repo.listPages(nb.id).map { it.pageId })
        assertFalse(sidecar(nb.id, second).exists())
        assertTrue(asset.exists())
    }

    @Test fun `无媒体 sidecar 的旧页返回空表`() {
        val nb = repo.create("旧页", "#2C8CC8", "dot", "课堂笔记")
        val pageId = repo.listPages(nb.id).first().pageId
        assertFalse(sidecar(nb.id, pageId).exists())
        assertEquals(emptyList<MediaElement>(), repo.loadMedia(nb.id, pageId))
    }
}
