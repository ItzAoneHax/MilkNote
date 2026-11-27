package com.milknote.notes.storage

import com.milknote.ink.input.InkCommit
import com.milknote.ink.input.InkController
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.MediaKind
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.orderedElements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 页面操作日志(.jrnl):重启后内容与撤销/重做栈恢复;旧 .pb/sidecar 作为基线不被重写;
 * Remove/Undo/Redo/Reorder 只存最小信息;残尾截断、复制/清除/删除与导入校验。
 */
class NotebookRepositoryDrawingJournalTest {

    private val root: File = Files.createTempDirectory("milknote-journal-test").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo = NotebookRepository(root, scope)

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun reopened() = NotebookRepository(root, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private fun pt(x: Float, y: Float, time: Long = 1L) = InkPoint(x, y, 0.5f, 0f, 0f, time)

    private fun card(id: String, x: Float = 1f) =
        AnnotationCard(id = id, x = x, y = 2f, width = 100f, text = "t", createdAt = 10L)

    private fun image(id: String, x: Float = 3f) = ImageElement(
        id = id, src = "images/$id.png", x = x, y = 4f, width = 50f, height = 60f, createdAt = 11L,
    )

    private fun media(id: String, x: Float = 5f) = MediaElement(
        id = id, kind = MediaKind.AUDIO, src = "media/$id.m4a", x = x, y = 6f,
        width = 70f, height = 80f, createdAt = 12L,
    )

    private fun pageFile(nb: String, page: String) = File(repo.notebookDir(nb), "$page.pb")
    private fun journalFile(nb: String, page: String) = File(repo.notebookDir(nb), "$page.jrnl")

    /** 用真实 InkController 驱动:所有操作经 onDrawingChange 进日志,模拟 app 装配。 */
    private fun controllerFor(
        notebookId: String,
        pageId: String,
        repository: NotebookRepository = repo,
    ): InkController = InkController().apply {
        onDrawingChange = { repository.appendDrawingChange(notebookId, pageId, it) }
        loadDrawing(repository.loadDrawing(notebookId, pageId))
    }

    private fun drawStroke(c: InkController, x: Float = 10f): Stroke {
        c.beginStroke(pt(x, x))
        c.extendStroke(listOf(pt(x + 4f, x + 4f, 5L), pt(x + 8f, x + 8f, 9L)))
        return c.endStroke()!!
    }

    private fun pagesOf(nb: String, page: String): List<String> =
        repo.loadPage(nb, page).map { it.id }

    @Test
    fun `笔迹与元素层跨重启恢复内容与撤销重做`() {
        val nb = repo.create("日志", "#3B5BEC", "dot", "课堂笔记")
        val page = repo.listPages(nb.id).single().pageId
        val c1 = controllerFor(nb.id, page)

        val s1 = drawStroke(c1, 10f)
        val s2 = drawStroke(c1, 40f)
        c1.addAnnotationCard(card("c1"))
        c1.addImageElement(image("i1"))
        c1.addMediaElement(media("m1"))
        c1.deleteElementById(com.milknote.ink.model.ElementRef.Kind.STROKE, s2.id)
        repo.loadDrawing(nb.id, page) // 等本实例写队列落盘,模拟进程重启前的最后状态

        assertTrue(journalFile(nb.id, page).exists())
        assertEquals(0L, pageFile(nb.id, page).length()) // 仅新增写日志 payload,不整页重写

        val repo2 = reopened()
        val d1 = repo2.loadDrawing(nb.id, page)
        assertEquals(listOf(s1.id), d1.strokes.map { it.id })
        assertEquals(listOf("c1"), d1.cards.map { it.id })
        assertEquals(listOf("i1"), d1.images.map { it.id })
        assertEquals(listOf("m1"), d1.media.map { it.id })
        assertTrue(d1.canUndo)
        assertFalse(d1.canRedo)
        assertEquals(listOf(s2.id), d1.removedStrokes().map { it.id })
        assertEquals(listOf(s1.id), repo2.loadPage(nb.id, page).map { it.id })
        assertEquals(listOf("c1"), repo2.loadAnnotations(nb.id, page).map { it.id })
        assertEquals(listOf("i1"), repo2.loadImages(nb.id, page).map { it.id })
        assertEquals(listOf("m1"), repo2.loadMedia(nb.id, page).map { it.id })

        // 重启后撤销删除:笔迹按原 index 恢复
        val c2 = controllerFor(nb.id, page, repo2)
        c2.undo()
        repo2.loadDrawing(nb.id, page)
        val repo3 = reopened()
        assertEquals(listOf(s1.id, s2.id), repo3.loadDrawing(nb.id, page).strokes.map { it.id })

        // 再重做:重新删除
        val c3 = controllerFor(nb.id, page, repo3)
        assertTrue(c3.page.canRedo)
        c3.redo()
        repo3.loadDrawing(nb.id, page)
        val repo4 = reopened()
        val d4 = repo4.loadDrawing(nb.id, page)
        assertEquals(listOf(s1.id), d4.strokes.map { it.id })
        assertTrue(d4.canUndo)
    }

    @Test
    fun `批量清除整页跨重启可撤销并恢复各层原 index`() {
        val nb = repo.create("清页", "#D2483C", "line", "错题本")
        val page = repo.listPages(nb.id).single().pageId
        val c1 = controllerFor(nb.id, page)

        val s1 = drawStroke(c1, 10f)
        val s2 = drawStroke(c1, 40f)
        c1.addAnnotationCard(card("c1"))
        c1.addAnnotationCard(card("c2", 50f))
        c1.addImageElement(image("i1"))
        c1.addMediaElement(media("m1"))
        assertTrue(c1.clearPageContent())
        repo.loadDrawing(nb.id, page)

        val cleared = reopened().loadDrawing(nb.id, page)
        assertTrue(cleared.strokes.isEmpty() && cleared.cards.isEmpty())
        assertTrue(cleared.images.isEmpty() && cleared.media.isEmpty())
        assertTrue(cleared.canUndo && !cleared.canRedo)

        val c2 = controllerFor(nb.id, page)
        c2.undo()
        repo.loadDrawing(nb.id, page)
        val restored = reopened().loadDrawing(nb.id, page)
        assertEquals(listOf(s1.id, s2.id), restored.strokes.map { it.id })
        assertEquals(listOf("c1", "c2"), restored.cards.map { it.id })
        assertEquals(listOf("i1"), restored.images.map { it.id })
        assertEquals(listOf("m1"), restored.media.map { it.id })
        assertEquals(0L, pageFile(nb.id, page).length())
    }

    @Test
    fun `既有数据兼容 日志建立后旧 API 不改基线且转为日志变更`() {
        val nb = repo.create("兼容", "#2E9E6B", "grid", "阅读摘录")
        val page = repo.listPages(nb.id).single().pageId
        val s1 = Stroke("s1", InkTool.PEN, 0xFF23221E, 0.7f, listOf(pt(0f, 0f), pt(8f, 8f, 9L)), createdAt = 101L)
        val s2 = Stroke("s2", InkTool.PEN, 0xFF23221E, 0.7f, listOf(pt(20f, 20f), pt(28f, 28f, 9L)), createdAt = 102L)
        val s3 = Stroke("s3", InkTool.PEN, 0xFF23221E, 0.7f, listOf(pt(40f, 40f), pt(48f, 48f, 9L)), createdAt = 103L)
        val s4 = Stroke("s4", InkTool.PEN, 0xFF23221E, 0.7f, listOf(pt(60f, 60f), pt(68f, 68f, 9L)), createdAt = 104L)

        // 旧路径:无日志时仍写 .pb 与 sidecar
        repo.appendCommit(nb.id, page, InkCommit.Added(s1))
        repo.appendCommit(nb.id, page, InkCommit.Added(s2))
        repo.saveAnnotations(nb.id, page, listOf(card("c1")))
        repo.saveImages(nb.id, page, listOf(image("i1")))
        repo.saveMedia(nb.id, page, listOf(media("m1")))

        val before = repo.loadDrawing(nb.id, page)
        assertEquals(listOf("s1", "s2"), before.strokes.map { it.id })
        assertFalse(before.canUndo)
        assertEquals(101L, before.strokes.first().createdAt)
        val pbLength = pageFile(nb.id, page).length()
        val annBytes = File(repo.notebookDir(nb.id), "$page.ann.json").readBytes()
        val imgBytes = File(repo.notebookDir(nb.id), "$page.img.json").readBytes()
        val mediaBytes = File(repo.notebookDir(nb.id), "$page.media.json").readBytes()
        assertTrue(pbLength > 0L)

        // 首次新变更建立日志;之后旧 API 必须转为日志 mutation
        repo.appendDrawingChange(nb.id, page, com.milknote.ink.model.DrawingChange.Apply(com.milknote.ink.model.InkOp.Add(s3)))
        repo.appendCommit(nb.id, page, InkCommit.Added(s4))
        repo.appendCommit(nb.id, page, InkCommit.Tombstoned("s1"))
        repo.appendLayerStates(nb.id, page, listOf(s2.copy(zIndex = 10L), s3.copy(zIndex = 11L), s4.copy(zIndex = 12L)))
        repo.saveAnnotations(
            nb.id, page,
            listOf(card("c1").copy(text = "updated", createdAt = 0L), card("c2", 50f)),
        )
        repo.saveImages(nb.id, page, listOf(image("i1"), image("i2", 60f)))
        repo.saveMedia(nb.id, page, listOf(media("m1"), media("m2", 70f)))
        repo.loadDrawing(nb.id, page)

        val after = reopened().loadDrawing(nb.id, page)
        assertEquals(listOf("s2", "s3", "s4"), after.strokes.map { it.id })
        assertEquals(listOf(10L, 11L, 12L), after.strokes.map { it.zIndex })
        assertEquals(listOf("c1", "c2"), after.cards.map { it.id })
        assertEquals("updated", after.cards.first { it.id == "c1" }.text)
        assertEquals(10L, after.cards.first { it.id == "c1" }.createdAt) // 0 不抹掉旧时间
        assertEquals(listOf("i1", "i2"), after.images.map { it.id })
        assertEquals(listOf("m1", "m2"), after.media.map { it.id })
        assertTrue(after.canUndo)

        assertEquals(pbLength, pageFile(nb.id, page).length())
        assertArrayEquals(annBytes, File(repo.notebookDir(nb.id), "$page.ann.json").readBytes())
        assertArrayEquals(imgBytes, File(repo.notebookDir(nb.id), "$page.img.json").readBytes())
        assertArrayEquals(mediaBytes, File(repo.notebookDir(nb.id), "$page.media.json").readBytes())

        // 新日志页面尚无旧基线历史:撤销只回放日志内动作
        after.undo() // 撤销最后一条 saveMedia 差异
        assertEquals(listOf("m1"), after.media.map { it.id })
    }

    @Test
    fun `选区移动跨日志重放保持 stable id 且不误报擦除`() {
        val nb = repo.create("移动身份", "#3B5BEC", "dot", "课堂笔记")
        val page = repo.listPages(nb.id).single().pageId
        val c = controllerFor(nb.id, page)

        c.beginStroke(pt(10f, 10f))
        c.extendStroke(listOf(pt(14f, 14f, 5L), pt(18f, 18f, 9L)))
        val original = c.endStroke()!!
        repo.loadDrawing(nb.id, page)

        val loop = listOf(pt(0f, 0f), pt(100f, 0f), pt(100f, 100f), pt(0f, 100f))
        c.armLasso()
        c.beginLasso(loop.first())
        loop.drop(1).forEach { c.extendLasso(it) }
        assertTrue(c.endLasso())
        c.beginSelectionDrag(10f, 10f)
        c.moveSelectionDrag(20f, 30f)
        assertTrue(c.endSelectionDrag())
        repo.loadDrawing(nb.id, page) // 排空日志写队列

        val loaded = reopened().loadDrawing(nb.id, page)
        assertEquals(listOf(original.id), loaded.strokes.map { it.id })
        assertEquals(original.createdAt, loaded.strokes.single().createdAt)
        assertTrue(loaded.removedStrokes().isEmpty()) // 变换不是擦除
        assertTrue(loaded.undo() != null)
        assertEquals(original.points, loaded.strokes.single().points)
        assertTrue(loaded.removedStrokes().isEmpty())
    }

    @Test
    fun `旧 onCommit 同 id 替换在日志中不丢笔迹`() {
        val nb = repo.create("旧回调", "#3B5BEC", "dot", "课堂笔记")
        val page = repo.listPages(nb.id).single().pageId
        val original = Stroke("a", InkTool.PEN, 0xFF23221E, 0.7f, listOf(pt(10f, 10f), pt(18f, 18f, 9L)), createdAt = 42L)
        repo.appendDrawingChange(
            nb.id, page, com.milknote.ink.model.DrawingChange.Apply(com.milknote.ink.model.InkOp.Add(original)),
        )
        repo.loadDrawing(nb.id, page)

        // 旧路径的变换镜像:先 Tombstoned(原 id),再 Added(同 id 新内容)
        val moved = original.copy(points = original.points.map { it.copy(x = it.x + 30f, y = it.y + 40f) })
        repo.appendCommit(nb.id, page, InkCommit.Tombstoned("a"))
        repo.appendCommit(nb.id, page, InkCommit.Added(moved))
        repo.loadDrawing(nb.id, page)

        val loaded = reopened().loadDrawing(nb.id, page)
        assertEquals(listOf("a"), loaded.strokes.map { it.id })
        assertEquals(moved.points, loaded.strokes.single().points)
        assertEquals(42L, loaded.strokes.single().createdAt)
        assertTrue(loaded.removedStrokes().isEmpty())

        loaded.undo()
        assertTrue(loaded.strokes.isEmpty()) // 先撤销 Added(moved)
        loaded.undo()
        assertEquals(listOf("a"), loaded.strokes.map { it.id }) // 再撤销 Tombstoned,原笔回来
        assertEquals(original.points, loaded.strokes.single().points)
    }

    @Test
    fun `换层跨重启可撤销且不改基线`() {
        val nb = repo.create("换层", "#3B5BEC", "dot", "课堂笔记")
        val page = repo.listPages(nb.id).single().pageId
        val c = controllerFor(nb.id, page)
        c.addImageElement(image("i1"))
        c.addAnnotationCard(card("c1"))
        c.beginStroke(pt(30f, 30f))
        c.extendStroke(listOf(pt(34f, 34f, 5L), pt(38f, 38f, 9L)))
        c.endStroke()
        repo.loadDrawing(nb.id, page)
        val pbLength = pageFile(nb.id, page).length()

        val before = repo.loadDrawing(nb.id, page).orderedElements().map {
            com.milknote.ink.model.LayerState(it.ref, it.zIndex)
        }
        val after = before.reversed().mapIndexed { index, layer ->
            layer.copy(zIndex = index + 1L)
        }
        repo.appendDrawingChange(
            nb.id, page,
            com.milknote.ink.model.DrawingChange.Apply(com.milknote.ink.model.InkOp.Reorder(before, after)),
        )
        repo.loadDrawing(nb.id, page) // 排空本实例写队列后再模拟进程重启

        val loaded = reopened().loadDrawing(nb.id, page)
        assertEquals(after.map { it.ref.id }, loaded.orderedElements().map { it.ref.id })
        assertTrue(loaded.canUndo)
        loaded.undo()
        assertEquals(before.map { it.ref.id }, loaded.orderedElements().map { it.ref.id })
        assertEquals(pbLength, pageFile(nb.id, page).length())
    }

    @Test
    fun `重做分支在新变更后截断`() {
        val nb = repo.create("分支", "#7C5CE0", "blank", "灵感草稿")
        val page = repo.listPages(nb.id).single().pageId
        val c = controllerFor(nb.id, page)

        val a = drawStroke(c, 10f)
        val b = drawStroke(c, 40f)
        c.undo()
        assertTrue(c.page.canRedo)
        val d = drawStroke(c, 70f)
        repo.loadDrawing(nb.id, page)

        val reloaded = reopened().loadDrawing(nb.id, page)
        assertEquals(listOf(a.id, d.id), reloaded.strokes.map { it.id })
        assertFalse(reloaded.canRedo)
        assertTrue(reloaded.canUndo)

        reloaded.undo()
        assertEquals(listOf(a.id), reloaded.strokes.map { it.id })
        assertFalse(b.id == d.id)
    }

    @Test
    fun `日志残尾之后追加会截断残尾且不越界`() {
        val nb = repo.create("残尾", "#2C8CC8", "dot", "课堂笔记")
        val page = repo.listPages(nb.id).single().pageId
        val s1 = Stroke("s1", InkTool.PEN, 0xFF23221E, 0.7f, listOf(pt(0f, 0f), pt(8f, 8f, 9L)))
        val s2 = Stroke("s2", InkTool.PEN, 0xFF23221E, 0.7f, listOf(pt(20f, 20f), pt(28f, 28f, 9L)), createdAt = 2L)
        val s3 = Stroke("s3", InkTool.PEN, 0xFF23221E, 0.7f, listOf(pt(40f, 40f), pt(48f, 48f, 9L)), createdAt = 3L)

        repo.appendDrawingChange(nb.id, page, com.milknote.ink.model.DrawingChange.Apply(com.milknote.ink.model.InkOp.Add(s1)))
        repo.appendDrawingChange(nb.id, page, com.milknote.ink.model.DrawingChange.Apply(com.milknote.ink.model.InkOp.Add(s2)))
        repo.loadDrawing(nb.id, page) // 排空写队列
        val jf = journalFile(nb.id, page)
        val fullLength = jf.length()
        RandomAccessFile(jf, "rw").use { it.setLength(fullLength - 3) } // 截断在最后一帧内部

        val repo2 = reopened()
        assertEquals(listOf("s1"), repo2.loadDrawing(nb.id, page).strokes.map { it.id })
        repo2.appendDrawingChange(nb.id, page, com.milknote.ink.model.DrawingChange.Apply(com.milknote.ink.model.InkOp.Add(s3)))
        repo2.loadDrawing(nb.id, page)
        val d = reopened().loadDrawing(nb.id, page)
        assertEquals(listOf("s1", "s3"), d.strokes.map { it.id })

        // 垃圾尾(声称 127 字节但没有 body)也不能阻止下一次追加
        val page2 = repo.newPage(nb.id)
        repo.appendDrawingChange(nb.id, page2, com.milknote.ink.model.DrawingChange.Apply(com.milknote.ink.model.InkOp.Add(s1)))
        repo.loadDrawing(nb.id, page2)
        RandomAccessFile(journalFile(nb.id, page2), "rw").use {
            it.seek(it.length())
            it.write(byteArrayOf(0x7F))
        }
        val repo3 = reopened()
        assertEquals(listOf("s1"), repo3.loadDrawing(nb.id, page2).strokes.map { it.id })
        repo3.appendDrawingChange(nb.id, page2, com.milknote.ink.model.DrawingChange.Apply(com.milknote.ink.model.InkOp.Add(s2)))
        repo3.loadDrawing(nb.id, page2)
        assertEquals(listOf("s1", "s2"), reopened().loadDrawing(nb.id, page2).strokes.map { it.id })
    }

    @Test
    fun `多页与同一 repo 快速切换`() {
        val nb = repo.create("多页", "#3B5BEC", "dot", "课堂笔记")
        val p1 = repo.listPages(nb.id).single().pageId
        val p2 = repo.newPage(nb.id)
        val c1 = controllerFor(nb.id, p1)
        val c2 = controllerFor(nb.id, p2)
        val a = drawStroke(c1, 10f)
        val b = drawStroke(c2, 40f)

        repeat(5) {
            assertEquals(listOf(a.id), repo.loadDrawing(nb.id, p1).strokes.map { it.id })
            assertEquals(listOf(b.id), repo.loadDrawing(nb.id, p2).strokes.map { it.id })
        }

        // 追加后立即读取(loadDrawing 自身等待本实例队列)
        val c = drawStroke(c1, 70f)
        assertEquals(listOf(a.id, c.id), repo.loadDrawing(nb.id, p1).strokes.map { it.id })

        val failure = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)
        val workers = (1..4).map {
            Thread {
                try {
                    latch.await()
                    repeat(20) {
                        val one = repo.loadDrawing(nb.id, p1)
                        val two = repo.loadDrawing(nb.id, p2)
                        assertEquals(2, one.strokes.size)
                        assertEquals(1, two.strokes.size)
                    }
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                }
            }.apply { start() }
        }
        latch.countDown()
        workers.forEach { it.join(10_000) }
        failure.get()?.let { throw AssertionError("并发读取失败", it) }
    }

    @Test
    fun `复制页以当前内容为独立基线且不复制源历史`() {
        val nb = repo.create("复制日志", "#D2483C", "line", "错题本")
        val source = repo.listPages(nb.id).single().pageId
        val c = controllerFor(nb.id, source)
        val s1 = drawStroke(c, 10f)
        c.addAnnotationCard(card("c1"))
        repo.loadDrawing(nb.id, source)
        assertTrue(journalFile(nb.id, source).exists())

        val copy = repo.duplicatePage(nb.id, source)!!
        val copyDrawing = reopened().loadDrawing(nb.id, copy)
        assertEquals(listOf(s1.id), copyDrawing.strokes.map { it.id })
        assertEquals(s1.createdAt, copyDrawing.strokes.single().createdAt) // 复制沿用原生成时间
        assertEquals(listOf("c1"), copyDrawing.cards.map { it.id })
        assertEquals(10L, copyDrawing.cards.single().createdAt)
        assertFalse(copyDrawing.canUndo)
        assertFalse(journalFile(nb.id, copy).exists())
        assertTrue(pageFile(nb.id, copy).length() > 0L)

        // 源页历史仍在
        assertTrue(reopened().loadDrawing(nb.id, source).canUndo)
        assertEquals(listOf(s1.id), pagesOf(nb.id, source))
    }

    @Test
    fun `清除页保留 Markdown 且日志页可撤回 删除清理日志并避免页号复用复活`() {
        val nb = repo.create("清除删除", "#2E9E6B", "grid", "阅读摘录")
        val page = repo.listPages(nb.id).single().pageId
        // 先有旧基线 .pb,再建立日志:清页不得截断基线
        val legacy = Stroke("legacy", InkTool.PEN, 0xFF23221E, 0.7f, listOf(pt(90f, 90f), pt(98f, 98f, 9L)))
        repo.appendCommit(nb.id, page, InkCommit.Added(legacy))
        val c = controllerFor(nb.id, page)
        val s1 = drawStroke(c, 10f)
        c.addAnnotationCard(card("c1"))
        repo.savePageMarkdown(nb.id, page, "# 保留")
        repo.loadDrawing(nb.id, page)
        val baselineLength = pageFile(nb.id, page).length()
        assertTrue(baselineLength > 0L)
        assertTrue(journalFile(nb.id, page).exists())

        assertTrue(repo.clearPage(nb.id, page))
        val cleared = reopened().loadDrawing(nb.id, page)
        assertTrue(cleared.strokes.isEmpty() && cleared.cards.isEmpty())
        assertTrue(cleared.canUndo)
        assertEquals("# 保留", repo.loadPageMarkdown(nb.id, page))
        assertEquals(baselineLength, pageFile(nb.id, page).length())

        val undoController = controllerFor(nb.id, page)
        undoController.undo()
        repo.loadDrawing(nb.id, page)
        val restored = reopened().loadDrawing(nb.id, page)
        assertEquals(listOf(legacy.id, s1.id), restored.strokes.map { it.id })
        assertEquals(listOf("c1"), restored.cards.map { it.id })

        // 删除页清理日志;最高页号复用时不复活旧数据
        val second = repo.newPage(nb.id)
        val c2 = controllerFor(nb.id, second)
        drawStroke(c2, 80f)
        repo.loadDrawing(nb.id, second)
        assertTrue(journalFile(nb.id, second).exists())
        assertTrue(repo.deletePage(nb.id, second))
        assertFalse(journalFile(nb.id, second).exists())

        // 删除后的迟到回调必须被丢弃,不能借页号复用复活
        repo.appendDrawingChange(nb.id, second, com.milknote.ink.model.DrawingChange.Apply(
            com.milknote.ink.model.InkOp.Add(s1),
        ))
        repo.saveAnnotations(nb.id, second, listOf(card("late")))
        repo.loadDrawing(nb.id, second)
        assertFalse(journalFile(nb.id, second).exists())
        assertFalse(File(repo.notebookDir(nb.id), "$second.ann.json").exists())

        val reused = repo.newPage(nb.id)
        assertEquals(second, reused)
        assertFalse(journalFile(nb.id, reused).exists())
        assertTrue(reopened().loadDrawing(nb.id, reused).strokes.isEmpty())
        assertTrue(repo.loadPage(nb.id, reused).isEmpty())
    }

    @Test
    fun `导入带日志的档案恢复内容与历史 损坏前缀被拒绝`() {
        val srcRoot = Files.createTempDirectory("milknote-journal-import-src").toFile()
        val srcRepo = NotebookRepository(srcRoot, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val nb = srcRepo.create("导入源", "#3B5BEC", "dot", "课堂笔记")
        val page = srcRepo.listPages(nb.id).single().pageId
        srcRepo.appendDrawingChange(nb.id, page, com.milknote.ink.model.DrawingChange.Apply(
            com.milknote.ink.model.InkOp.Add(
                Stroke("s1", InkTool.PEN, 0xFF23221E, 0.7f, listOf(pt(0f, 0f), pt(8f, 8f, 9L)), createdAt = 5L),
            ),
        ))
        srcRepo.appendDrawingChange(nb.id, page, com.milknote.ink.model.DrawingChange.Apply(
            com.milknote.ink.model.InkOp.Add(
                Stroke("s2", InkTool.PEN, 0xFF23221E, 0.7f, listOf(pt(20f, 20f), pt(28f, 28f, 9L)), createdAt = 6L),
            ),
        ))
        srcRepo.loadDrawing(nb.id, page)
        val srcDir = srcRepo.notebookDir(nb.id)
        val pb = File(srcDir, "$page.pb").readBytes()
        val journal = File(srcDir, "$page.jrnl").readBytes()
        val meta = File(srcDir, "notebook.json").readText()

        val notebooksRoot = File(root, "notebooks").apply { mkdirs() }
        val archive = File(root, "journal.milk.zip")
        writeZip(
            archive,
            listOf(
                "nb.milk/notebook.json" to meta.toByteArray(),
                "nb.milk/$page.pb" to pb,
                "nb.milk/$page.jrnl" to journal,
            ),
        )
        val result = MilkArchiveImporter.import(archive, notebooksRoot)
        val importedRepo = NotebookRepository(root, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val drawing = importedRepo.loadDrawing(result.notebookId, page)
        assertEquals(listOf("s1", "s2"), drawing.strokes.map { it.id })
        assertTrue(drawing.canUndo)
        drawing.undo()
        assertEquals(listOf("s1"), drawing.strokes.map { it.id })

        // 截断尾帧按崩溃残留接受(有效前缀可见)
        val truncatedArchive = File(root, "journal-truncated.milk.zip")
        writeZip(
            truncatedArchive,
            listOf(
                "nb.milk/notebook.json" to meta.toByteArray(),
                "nb.milk/$page.pb" to pb,
                "nb.milk/$page.jrnl" to journal.copyOf(journal.size - 3),
            ),
        )
        val truncated = MilkArchiveImporter.import(truncatedArchive, notebooksRoot)
        assertEquals(listOf("s1"), importedRepo.loadDrawing(truncated.notebookId, page).strokes.map { it.id })

        // 损坏前缀必须拒绝导入且不落盘
        val corruptJournal = journal.copyOf().also { it[0] = 'X'.code.toByte() }
        val corruptArchive = File(root, "journal-corrupt.milk.zip")
        writeZip(
            corruptArchive,
            listOf(
                "nb.milk/notebook.json" to meta.toByteArray(),
                "nb.milk/$page.pb" to pb,
                "nb.milk/$page.jrnl" to corruptJournal,
            ),
        )
        val before = notebooksRoot.listFiles()?.size ?: 0
        assertThrows(MilkImportCorruptException::class.java) {
            MilkArchiveImporter.import(corruptArchive, notebooksRoot)
        }
        assertEquals(before, notebooksRoot.listFiles()?.size ?: 0)
    }

    private fun writeZip(file: File, entries: List<Pair<String, ByteArray>>) {
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
