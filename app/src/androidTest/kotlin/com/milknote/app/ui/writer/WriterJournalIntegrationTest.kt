package com.milknote.app.ui.writer

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.milknote.agent.PageRef
import com.milknote.agent.Rect4
import com.milknote.app.agent.AgentBridge
import com.milknote.app.agent.AppNotebookWorkspacePort
import com.milknote.app.di.ServiceLocator
import com.milknote.ink.input.InkController
import com.milknote.ink.model.ElementRef
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.PageInkState
import com.milknote.ink.model.Stroke
import com.milknote.ink.render.InkSurfaceView
import com.milknote.notes.storage.NotebookRepository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 任务 D 应用集成:控制器变更 → 操作日志 → 新仓库/控制器恢复(内容 + 两栈),
 * 以及工作区端口的元素时序查询(当前页同帧内存优先、页不存在 null)。
 *
 * 存储/端口契约由 ink-engine(`DrawingChange`/`PageInkState.removedStrokes`)与
 * note-domain(`appendDrawingChange`/`loadDrawing`)提供;本类只验证 app 装配。
 */
@RunWith(AndroidJUnit4::class)
class WriterJournalIntegrationTest {

    @get:Rule
    val rule = createComposeRule()

    private val tempRoot: File = Files.createTempDirectory("milknote-writer-journal").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo = NotebookRepository(tempRoot, scope)
    private val createdNotebookIds = mutableListOf<String>()
    private val viewModelStores = mutableListOf<ViewModelStore>()
    private var previousWorkspaceHandler: com.milknote.agent.NotebookWorkspacePort? = null

    @After
    fun tearDown() {
        viewModelStores.forEach { runCatching { it.clear() } }
        AgentBridge.workspaceHandler = previousWorkspaceHandler
        createdNotebookIds.forEach { id -> runCatching { ServiceLocator.repository.delete(id) } }
        tempRoot.deleteRecursively()
    }

    // ---------------------------------------------------------------- 真实 Surface + 日志

    @Test
    fun stylusStroke_isJournaled_andUndoRedoSurvivesReopen() {
        val nb = repo.create("日志落笔", "#3B5BEC", "dot", "课堂笔记")
        val page = repo.listPages(nb.id).first().pageId
        val controller = journalController(repo, nb.id, page)

        withSurface(controller) { surface ->
            surface.send(MotionEvent.ACTION_DOWN, 120f, 160f, MotionEvent.TOOL_TYPE_STYLUS)
            surface.send(MotionEvent.ACTION_MOVE, 220f, 260f, MotionEvent.TOOL_TYPE_STYLUS)
            surface.send(MotionEvent.ACTION_UP, 220f, 260f, MotionEvent.TOOL_TYPE_STYLUS)
        }
        assertEquals(1, controller.page.strokes.size)

        val loaded = awaitDrawing(nb.id, page) { it.strokes.size == 1 }
        assertTrue("日志回放应保留撤销栈", loaded.canUndo)
        assertFalse(loaded.canRedo)

        // 跨新仓库 + 新控制器撤销:删除写回日志,并非只在内存生效
        journalController(reopen(), nb.id, page).undo()
        val undone = awaitDrawing(nb.id, page) { it.strokes.isEmpty() }
        assertTrue("撤销后可重做", undone.canRedo)

        journalController(reopen(), nb.id, page).redo()
        assertEquals(1, awaitDrawing(nb.id, page) { it.strokes.size == 1 }.strokes.size)
    }

    // ---------------------------------------------------------------- 工作区端口(同帧内存优先)

    @Test
    fun workspacePort_reportsOverlappingTimestampsFromLiveController() {
        val nb = createAppNotebook("端口时序")
        val page = ServiceLocator.repository.listPages(nb).first().pageId
        val vm = newWriterViewModel()
        onMain { vm.open(nb) }
        awaitCondition("书写页未打开") { vm.state.value.notebook?.id == nb }

        val strokeId = onMainValue {
            vm.controller.addImageElement(
                ImageElement(
                    id = "img1", src = "images/none.png", x = 100f, y = 100f,
                    width = 200f, height = 200f, createdAt = 42L,
                ),
            )
            vm.controller.beginStroke(inkPoint(140f, 140f))
            vm.controller.extendStroke(listOf(inkPoint(160f, 160f), inkPoint(180f, 180f)))
            vm.controller.endStroke()!!.id
        }

        val port = AppNotebookWorkspacePort()
        val overlap = runBlocking { port.elementTimestamps(PageRef(nb, page), Rect4(120f, 120f, 200f, 200f), false) }
        assertNotNull("重叠笔迹与图片都应命中", overlap)
        assertEquals(listOf(strokeId, "img1"), overlap!!.map { it.id })
        assertEquals(listOf("stroke", "image"), overlap.map { it.kind })
        assertNotNull("笔迹应带生成时间", overlap.first().createdAtMs)

        // 只读渲染/摘要路径不暴露时间
        val summaries = runBlocking { port.listElements(PageRef(nb, page)) }
        assertEquals(2, summaries.size)

        // 擦除后:普通查询不含,includeErased 才补一条 erased 叠层
        onMain { vm.controller.deleteElementById(ElementRef.Kind.STROKE, strokeId) }
        val live = runBlocking { port.elementTimestamps(PageRef(nb, page), Rect4(120f, 120f, 200f, 200f), false) }
        assertEquals(listOf("img1"), live!!.map { it.id })
        val withErased = runBlocking {
            port.elementTimestamps(PageRef(nb, page), Rect4(120f, 120f, 200f, 200f), true)
        }
        assertEquals(listOf("img1", strokeId), withErased!!.map { it.id })
        assertTrue(withErased.last().erased)
        assertFalse(withErased.first().erased)

        // 页不存在(notebook 存在但 page id 不在列表)→ null
        assertNull(runBlocking { port.elementTimestamps(PageRef(nb, "p9999"), Rect4(0f, 0f, 10f, 10f), false) })
        assertNull(runBlocking { port.elementTimestamps(PageRef("no-such-book", page), Rect4(0f, 0f, 10f, 10f), false) })
    }

    @Test
    fun workspacePort_prefersSameFrameMemoryOverDisk() {
        val nb = createAppNotebook("端口内存")
        val page = ServiceLocator.repository.listPages(nb).first().pageId
        val vm = newWriterViewModel()
        onMain { vm.open(nb) }
        awaitCondition("书写页未打开") { vm.state.value.notebook?.id == nb }

        // 只在内存里加一笔(不进日志),端口仍应看到它 → 证明查询走同帧快照而非磁盘
        onMain {
            vm.controller.page.strokes.add(
                Stroke(
                    id = "mem-only", tool = InkTool.PEN, color = 0xFF23221E, baseWidthMm = 0.7f,
                    points = listOf(inkPoint(300f, 300f), inkPoint(340f, 340f)), createdAt = 7L,
                ),
            )
        }
        val hits = runBlocking {
            AppNotebookWorkspacePort().elementTimestamps(PageRef(nb, page), Rect4(280f, 280f, 360f, 360f), false)
        }
        assertEquals(listOf("mem-only"), hits!!.map { it.id })
        assertTrue(runBlocking { ServiceLocator.repository.loadDrawing(nb, page) }.strokes.isEmpty())
    }

    // ---------------------------------------------------------------- 同页双窗格

    @Test
    fun duplicateSplitPane_mirrorsMainAndKeepsSingleHistory() {
        val nb = createAppNotebook("同页分屏")
        val page = ServiceLocator.repository.listPages(nb).first().pageId
        val vm = newWriterViewModel()
        onMain { vm.open(nb) }
        awaitCondition("书写页未打开") { vm.state.value.notebook?.id == nb }
        onMain { vm.toggleSplit(nb) }
        awaitCondition("分屏未打开") { vm.state.value.splitEnabled }
        assertEquals(page, vm.state.value.currentPageId)
        assertEquals(page, vm.state.value.splitPageId)

        onMain {
            vm.controller.beginStroke(inkPoint(80f, 80f))
            vm.controller.extendStroke(listOf(inkPoint(120f, 120f), inkPoint(160f, 160f)))
            vm.controller.endStroke()
        }
        val strokeId = onMainValue { vm.controller.page.strokes.single().id }

        // 重复窗格复制主窗格画面与两栈
        assertEquals(listOf(strokeId), onMainValue { vm.splitController.page.strokes.map { it.id } })
        assertTrue(onMainValue { vm.splitController.page.canUndo })
        assertEquals(
            1,
            awaitDrawing(nb, page, repository = ServiceLocator.repository) { it.strokes.size == 1 }.strokes.size,
        )

        // 分屏只读兜底:即使拿到重复窗格控制器,也不会重复写盘
        onMain {
            vm.splitController.beginStroke(inkPoint(400f, 400f))
            vm.splitController.extendStroke(listOf(inkPoint(430f, 430f)))
            vm.splitController.endStroke()
        }
        assertEquals(
            1,
            awaitDrawing(nb, page, repository = ServiceLocator.repository) { it.strokes.size == 1 }.strokes.size,
        )
    }

    // ---------------------------------------------------------------- 分屏翻页守卫

    @Test
    fun splitPageStep_keepsOnlyLastTarget() {
        val mainNb = createAppNotebook("主窗格")
        val splitNb = createAppNotebook("分屏翻页")
        ServiceLocator.repository.newPage(splitNb)
        ServiceLocator.repository.newPage(splitNb)
        val splitPages = ServiceLocator.repository.listPages(splitNb).map { it.pageId }
        assertEquals(3, splitPages.size)

        // 给末页一个可辨识的卡片,确认最终载入的是哪一页
        val seed = InkController()
        seed.onDrawingChange = { ServiceLocator.repository.appendDrawingChange(splitNb, splitPages.last(), it) }
        seed.loadDrawing(ServiceLocator.repository.loadDrawing(splitNb, splitPages.last()))
        seed.addAnnotationCard(
            com.milknote.ink.model.AnnotationCard(
                id = "seed-card", x = 10f, y = 12f, width = 100f, text = "末页", createdAt = 1L,
            ),
        )

        val vm = newWriterViewModel()
        onMain { vm.open(mainNb) }
        awaitCondition("书写页未打开") { vm.state.value.notebook?.id == mainNb }
        onMain { vm.toggleSplit(splitNb) }
        awaitCondition("分屏未打开") { vm.state.value.splitEnabled }
        assertEquals(splitPages.first(), vm.state.value.splitPageId)

        // 连点两次:旧任务必须失效,不能把界面拉回中间页
        onMain {
            vm.stepSplitPage(1)
            vm.stepSplitPage(1)
        }
        awaitCondition("分屏未停到最后一次目标页") { vm.state.value.splitPageId == splitPages.last() }
        assertTrue(
            "最终载入内容应是末页",
            onMainValue { vm.splitController.annotationCards().any { it.id == "seed-card" } },
        )
    }

    // ---------------------------------------------------------------- 快速打开 latest-wins

    @Test
    fun rapidOpen_keepsLatestNotebook() {
        val first = createAppNotebook("快速打开一")
        val second = createAppNotebook("快速打开二")
        val secondPage = ServiceLocator.repository.listPages(second).first().pageId
        val seed = InkController()
        seed.onDrawingChange = { ServiceLocator.repository.appendDrawingChange(second, secondPage, it) }
        seed.loadDrawing(ServiceLocator.repository.loadDrawing(second, secondPage))
        seed.addAnnotationCard(
            com.milknote.ink.model.AnnotationCard(
                id = "rapid-card", x = 8f, y = 8f, width = 90f, text = "最新", createdAt = 2L,
            ),
        )

        val vm = newWriterViewModel()
        onMain {
            vm.open(first)
            vm.open(second)
        }
        awaitCondition("未停到最新打开的本") { vm.state.value.notebook?.id == second }

        // 旧请求不得在后来的帧里回写:保持一小段观察窗口
        val deadline = SystemClock.uptimeMillis() + 600
        while (SystemClock.uptimeMillis() < deadline) {
            assertEquals(second, onMainValue { vm.state.value.notebook?.id })
            Thread.sleep(20)
        }
        assertTrue("载入内容应属于最新本", onMainValue { vm.controller.annotationCards().any { it.id == "rapid-card" } })
    }

    // ---------------------------------------------------------------- 关闭标签:窗口期内笔迹仍归属原页

    @Test
    fun closeTab_keepsInFlightStrokeOnClosedPage() {
        val first = createAppNotebook("关闭标签主")
        val second = createAppNotebook("关闭标签次")
        val secondPage = ServiceLocator.repository.listPages(second).first().pageId
        val vm = newWriterViewModel()
        onMain { vm.open(first) }
        awaitCondition("第一个本未打开") { vm.state.value.notebook?.id == first }
        onMain { vm.open(second) }
        awaitCondition("第二个本未打开") { vm.state.value.notebook?.id == second }

        // 同一主线程回调内:关闭当前标签,并在下一本载入前完成当前页的一笔
        val strokeId = onMainValue {
            vm.closeTab(second)
            vm.controller.beginStroke(inkPoint(140f, 140f))
            vm.controller.extendStroke(listOf(inkPoint(170f, 170f), inkPoint(200f, 200f)))
            vm.controller.endStroke()!!.id
        }
        awaitCondition("关闭标签后未切到另一本") { vm.state.value.notebook?.id == first }

        // 该笔仍应落在被关闭的页(不因异步切换丢归属)
        assertEquals(
            listOf(strokeId),
            awaitDrawing(second, secondPage, repository = ServiceLocator.repository) { it.strokes.isNotEmpty() }
                .strokes.map { it.id },
        )
        // 回到原本可见:重开该本,笔迹仍在画布上
        onMain { vm.open(second) }
        awaitCondition("重开第二个本未完成") { vm.state.value.notebook?.id == second }
        assertEquals(listOf(strokeId), onMainValue { vm.controller.page.strokes.map { it.id } })
    }

    // ---------------------------------------------------------------- 离屏清空可撤回

    @Test
    fun offscreenClear_isJournaled_andUndoableAfterOpeningThePage() {
        val nb = createAppNotebook("离屏清空")
        ServiceLocator.repository.newPage(nb)
        val vm = newWriterViewModel()
        onMain { vm.open(nb) }
        awaitCondition("书写页未打开") { vm.state.value.notebook?.id == nb }
        val contentPage = vm.state.value.currentPageId

        onMain {
            vm.controller.beginStroke(inkPoint(90f, 90f))
            vm.controller.extendStroke(listOf(inkPoint(130f, 130f), inkPoint(170f, 170f)))
            vm.controller.endStroke()
        }
        assertEquals(
            1,
            awaitDrawing(nb, contentPage, repository = ServiceLocator.repository) { it.strokes.size == 1 }.strokes.size,
        )

        // 切到另一页后清空刚才那页(离屏)
        onMain { vm.switchPage(vm.state.value.pages.first { it.pageId != contentPage }.pageId) }
        awaitCondition("切页未完成") { vm.state.value.currentPageId != contentPage }
        onMain { vm.clearPage(contentPage) }
        assertTrue(
            "离屏清空应写入日志并从画面消失",
            awaitDrawing(nb, contentPage, repository = ServiceLocator.repository) { it.strokes.isEmpty() }
                .strokes.isEmpty(),
        )

        // 打开该页仍可撤销 → 笔迹回到日志
        onMain { vm.switchPage(contentPage) }
        awaitCondition("回页未完成") { vm.state.value.currentPageId == contentPage }
        assertTrue("打开后应能撤销清空", onMainValue { vm.controller.page.canUndo })
        onMain { vm.undo() }
        assertEquals(
            1,
            awaitDrawing(nb, contentPage, repository = ServiceLocator.repository) { it.strokes.size == 1 }.strokes.size,
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun reopen() = NotebookRepository(tempRoot, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private fun journalController(
        repository: NotebookRepository,
        notebookId: String,
        pageId: String,
    ): InkController = InkController().apply {
        onDrawingChange = { repository.appendDrawingChange(notebookId, pageId, it) }
        loadDrawing(repository.loadDrawing(notebookId, pageId))
    }

    /** 另一个仓库实例看不到本实例的写队列,按「会等待 pending IO」的契约轮询到可见为止。 */
    private fun awaitDrawing(
        notebookId: String,
        pageId: String,
        repository: NotebookRepository = reopen(),
        predicate: (PageInkState) -> Boolean,
    ): PageInkState {
        val deadline = SystemClock.uptimeMillis() + 5_000
        var state = repository.loadDrawing(notebookId, pageId)
        while (!predicate(state) && SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(20)
            state = repository.loadDrawing(notebookId, pageId)
        }
        if (!predicate(state)) fail("等待日志落盘超时: $notebookId/$pageId")
        return state
    }

    private fun createAppNotebook(name: String): String {
        val meta = ServiceLocator.repository.create(name, "#3B5BEC", "dot", "课堂笔记")
        createdNotebookIds += meta.id
        return meta.id
    }

    private fun newWriterViewModel(): WriterViewModel {
        previousWorkspaceHandler = AgentBridge.workspaceHandler
        val store = ViewModelStore()
        viewModelStores += store
        lateinit var vm: WriterViewModel
        onMain {
            vm = ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = WriterViewModel() as T
                },
            )[WriterViewModel::class.java]
        }
        return vm
    }

    private fun inkPoint(x: Float, y: Float) = InkPoint(x, y, 0.6f, 0f, 0f, System.currentTimeMillis())

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private fun <T> onMainValue(block: () -> T): T {
        var value: Any? = null
        var done = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            value = block()
            done = true
        }
        check(done) { "主线程动作未执行" }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun awaitCondition(message: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMainValue(condition)) return
            Thread.sleep(20)
        }
        fail(message)
    }

    private fun withSurface(controller: InkController, block: (InkSurfaceView) -> Unit) {
        lateinit var surface: InkSurfaceView
        rule.setContent {
            AndroidView(
                factory = { context ->
                    InkSurfaceView(context).also {
                        surface = it
                        it.controller = controller
                    }
                },
                modifier = Modifier.size(600.dp).testTag("journal-surface"),
            )
        }
        rule.waitForIdle()
        rule.runOnUiThread {
            surface.setPageSizePx(600f, 600f)
            block(surface)
        }
        rule.waitForIdle()
    }

    private fun InkSurfaceView.send(action: Int, x: Float, y: Float, toolType: Int) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; this.toolType = toolType }
        val coordinates = MotionEvent.PointerCoords().apply {
            this.x = x; this.y = y; pressure = 1f; size = 1f
        }
        val event = MotionEvent.obtain(
            0L, SystemClock.uptimeMillis(), action, 1, arrayOf(properties), arrayOf(coordinates),
            0, 0, 1f, 1f, 0, 0,
            if (toolType == MotionEvent.TOOL_TYPE_FINGER) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_STYLUS,
            0,
        )
        try {
            onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
