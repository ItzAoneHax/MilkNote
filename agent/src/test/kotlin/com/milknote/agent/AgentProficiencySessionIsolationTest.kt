package com.milknote.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// =============================================================================
// 熟练度分析授权的「会话隔离」契约:
// 授权只属于发起那一次「页 + 会话 + 绑定代际」;后台分析期间任何页/会话导航
// 都会永久废止它(回到原页原会话也不复活):不再读旧页时序,也不把结果写进
// 当前会话或下一个会话。同页刷新不是导航,不废止。
// =============================================================================

class ProficiencySessionIsolationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val page1 = PageRef("nb1", "p0001")
    private val page2 = PageRef("nb1", "p0002")

    private fun rig(
        scope: CoroutineScope,
        ws: NotebookWorkspacePort,
        provider: AgentProvider,
        clock: () -> Long = System::currentTimeMillis,
    ): IsolationRig {
        val files = File(tmp.newFolder(), "files").apply { mkdirs() }
        val notebooks = File(tmp.newFolder(), "notebooks").apply { mkdirs() }
        val store = AgentStore(files, notebooks)
        val controller = AgentController(
            scope = scope,
            store = store,
            snipSource = null,
            annotationSink = null,
            keyFor = { "sk-test" },
            providerFactory = { _, _, _ -> provider },
            clockMs = clock,
            workspace = ws,
        )
        controller.addProfile("glm", "", "", "sk-test")
        controller.setSummaryConfig(SummaryConfig(enabled = false))
        controller.openDrawer(page1)
        return IsolationRig(controller, store)
    }

    @Test
    fun `申请后协程尚未开始就切页则不发起请求也不写任何会话`() = runTest {
        val ws = IsolationWorkspace()
        val provider = IsolationTextProvider()
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.requestProficiencyAnalysis()
        // 申请与切页落在同一帧:协程还没被调度就已经换了页
        controller.bindPage(page2)
        advanceUntilIdle()

        assertTrue("换页后授权即撤销,不得再发请求", provider.requests.isEmpty())
        assertTrue(ws.timestampCalls.isEmpty())
        assertTrue("不得在新页会话里留下半截气泡", controller.state.value.messages.isEmpty())
        assertEquals(AgentMessage.Phase.DONE, controller.state.value.phase)
        assertTrue("不得落盘任何会话", rig.store.loadNotebookSessions("nb1").isEmpty())
    }

    @Test
    fun `流式中切页后不再读旧页时序也不落任何会话`() = runTest {
        val ws = IsolationWorkspace()
        val gate = CompletableDeferred<Unit>()
        val provider = IsolationGatedToolProvider(gate)
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.requestProficiencyAnalysis()
        advanceUntilIdle()
        assertEquals(1, provider.requests.size)
        assertTrue(ws.timestampCalls.isEmpty())

        controller.bindPage(page2)
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue("撤销后的工具调用不得触达旧页", ws.timestampCalls.isEmpty())
        assertEquals("不得再发下一轮", 1, provider.requests.size)
        assertTrue(controller.state.value.messages.isEmpty())
        assertTrue(rig.store.loadNotebookSessions("nb1").isEmpty())
    }

    @Test
    fun `工具执行中切页则返回后不发下一轮也不落盘`() = runTest {
        val toolGate = CompletableDeferred<Unit>()
        val ws = IsolationWorkspace(toolGate)
        val provider = IsolationToolThenTextProvider()
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.requestProficiencyAnalysis()
        advanceUntilIdle()
        assertTrue("工具应已开始执行并挂起", ws.entered)
        assertEquals(1, ws.timestampCalls.size)

        controller.bindPage(page2)
        toolGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("工具结果不得发往新一轮", 1, provider.requests.size)
        assertTrue("新页会话不得出现分析内容", controller.state.value.messages.isEmpty())
        assertTrue(rig.store.loadNotebookSessions("nb1").isEmpty())
    }

    @Test
    fun `清空会话后旧分析不再继续也不写新会话`() = runTest {
        val ws = IsolationWorkspace()
        val gate = CompletableDeferred<Unit>()
        val provider = IsolationGatedToolProvider(gate)
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.requestProficiencyAnalysis()
        advanceUntilIdle()
        val analysisSession = controller.state.value.currentSessionId!!
        assertTrue(ws.timestampCalls.isEmpty())

        controller.clearSession()
        val freshSession = controller.state.value.currentSessionId!!
        assertNotEquals(analysisSession, freshSession)

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue("新会话下不得读旧页时序", ws.timestampCalls.isEmpty())
        assertEquals(1, provider.requests.size)
        assertTrue("新会话不得出现分析内容", controller.state.value.messages.isEmpty())
        assertEquals(freshSession, controller.state.value.currentSessionId)
        assertTrue(rig.store.loadNotebookSessions("nb1").isEmpty())
    }

    @Test
    fun `切页后再批准高危工具也不执行`() = runTest {
        val ws = IsolationWorkspace()
        val provider = IsolationDeletePageProvider()
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.requestProficiencyAnalysis()
        advanceUntilIdle()
        assertNotNull("高危工具应挂起等待确认", controller.state.value.pendingConfirmation)

        controller.bindPage(page2)
        controller.approvePendingTool()
        advanceUntilIdle()

        assertTrue("撤销后即使批准也不得执行写操作", ws.deleteCalls.isEmpty())
        assertEquals(1, provider.requests.size)
        assertTrue(controller.state.value.messages.isEmpty())
        assertTrue(rig.store.loadNotebookSessions("nb1").isEmpty())
    }

    @Test
    fun `离开后再回到同一页同一会话也不再继续`() = runTest {
        val ws = IsolationWorkspace()
        val gate = CompletableDeferred<Unit>()
        val provider = IsolationWarmThenGatedToolProvider(gate)
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.setDraftText("你好")
        controller.send()
        advanceUntilIdle()
        val originalId = controller.state.value.currentSessionId!!
        val before = rig.store.loadSession("nb1", originalId)!!.messages.size

        controller.requestProficiencyAnalysis()
        advanceUntilIdle()
        assertEquals(2, provider.requests.size)

        controller.bindPage(page2)
        controller.bindPage(page1)
        assertEquals("回到原会话", originalId, controller.state.value.currentSessionId)

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue("授权已随导航永久废止,不得再读旧页", ws.timestampCalls.isEmpty())
        assertEquals("不得再发续轮", 2, provider.requests.size)
        val saved = rig.store.loadSession("nb1", originalId)!!
        assertEquals("原会话不得写入分析", before, saved.messages.size)
        assertTrue(
            "任何会话都不得承载旧页分析",
            rig.store.loadNotebookSessions("nb1")
                .none { s -> s.messages.any { it.text.contains("分析完成") } },
        )
        assertTrue(controller.state.value.messages.none { it.text.contains("分析完成") })
    }

    @Test
    fun `同页同会话的刷新不废止授权`() = runTest {
        val ws = IsolationWorkspace()
        val gate = CompletableDeferred<Unit>()
        val provider = IsolationGatedToolProvider(gate)
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller
        val sessionId = controller.state.value.currentSessionId!!

        controller.requestProficiencyAnalysis()
        advanceUntilIdle()
        assertEquals(1, provider.requests.size)

        // 同页刷新、重开抽屉、重列会话都不是导航,不得废止在途授权
        controller.bindPage(page1)
        controller.openDrawer(page1)
        controller.closeDrawer()
        controller.refreshSessions()
        advanceUntilIdle()
        assertEquals("刷新不得另发请求", 1, provider.requests.size)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, ws.timestampCalls.size)
        assertEquals(page1, ws.timestampCalls.single())
        assertEquals(2, provider.requests.size)
        val saved = rig.store.loadSession("nb1", sessionId)!!
        assertTrue(saved.messages.any { it.text.contains("分析完成") })
    }

    @Test
    fun `落盘前一刻切页也不会把分析写进新会话`() = runTest {
        val ws = IsolationWorkspace()
        val provider = IsolationToolThenTextProvider()
        lateinit var controller: AgentController
        var navArmed = false
        // 精确命中竞态窗口:finishDone 的守卫已通过、persistWith 还没取会话快照时切页
        // (无锁/无代际复验的实现在这里会把旧页分析写进新页会话目录)
        val rig = rig(
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            ws,
            provider,
        ) {
            if (navArmed) {
                navArmed = false
                controller.bindPage(page2)
            }
            System.currentTimeMillis()
        }
        controller = rig.controller
        ws.onRead = { navArmed = true }

        controller.requestProficiencyAnalysis()
        advanceUntilIdle()

        assertEquals("工具读取已在授权页发生", 1, ws.timestampCalls.size)
        assertEquals("切页发生在续轮之后", 2, provider.requests.size)
        assertEquals(page2, controller.state.value.boundPage)

        assertTrue(
            "任何会话文件都不得出现旧页分析",
            rig.store.loadNotebookSessions("nb1")
                .none { s -> s.messages.any { it.text.contains("分析完成") } },
        )
        assertTrue(rig.store.loadSessions("nb1", "p0001").isEmpty())
        assertTrue(controller.state.value.messages.none { it.text.contains("分析完成") })
    }
}

private class IsolationRig(
    val controller: AgentController,
    val store: AgentStore,
)

/** 单轮纯文本 Provider */
private class IsolationTextProvider : AgentProvider {
    override val profileId = "text"
    val requests = ArrayList<ChatRequest>()

    override fun chat(req: ChatRequest) = flow {
        requests += req
        emit(ChatEvent.Delta("分析结果"))
        emit(ChatEvent.Done(null))
    }

    override suspend fun fetchModels(): Result<List<ModelInfo>> = Result.success(emptyList())
    override suspend fun testConnection(): Result<Long> = Result.success(1L)
}

/** 分析轮直接调用高危 delete_page(需用户确认) */
private class IsolationDeletePageProvider : AgentProvider {
    override val profileId = "delete-page"
    val requests = ArrayList<ChatRequest>()

    override fun chat(req: ChatRequest) = flow {
        requests += req
        if (requests.size == 1) {
            emit(ChatEvent.ToolCallReady(ToolCall("d1", "delete_page", """{"page_id":"p0001"}""")))
            emit(ChatEvent.Done(null))
        } else {
            emit(ChatEvent.Delta("已取消"))
            emit(ChatEvent.Done(null))
        }
    }

    override suspend fun fetchModels(): Result<List<ModelInfo>> = Result.success(emptyList())
    override suspend fun testConnection(): Result<Long> = Result.success(1L)
}

/** 首个请求放行后发出一次时序工具调用;后续请求直接给文本 */
private class IsolationGatedToolProvider(private val gate: CompletableDeferred<Unit>) : AgentProvider {
    override val profileId = "gated-tool"
    val requests = ArrayList<ChatRequest>()

    override fun chat(req: ChatRequest) = flow {
        requests += req
        if (requests.size == 1) {
            gate.await()
            emit(
                ChatEvent.ToolCallReady(
                    ToolCall("t1", BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID, """{"x1":5,"y1":6}"""),
                ),
            )
            emit(ChatEvent.Done(null))
        } else {
            emit(ChatEvent.Delta("分析完成"))
            emit(ChatEvent.Done(null))
        }
    }

    override suspend fun fetchModels(): Result<List<ModelInfo>> = Result.success(emptyList())
    override suspend fun testConnection(): Result<Long> = Result.success(1L)
}

/** 首个请求立即发工具调用,第二轮给文本(工具本身靠 workspace 挂起) */
/** 首轮先吐一段正文再发工具调用(首 token 时钟早于工具),第二轮给文本 */
private class IsolationToolThenTextProvider : AgentProvider {
    override val profileId = "tool-then-text"
    val requests = ArrayList<ChatRequest>()

    override fun chat(req: ChatRequest) = flow {
        requests += req
        if (requests.size == 1) {
            emit(ChatEvent.Delta("我先看看"))
            emit(
                ChatEvent.ToolCallReady(
                    ToolCall("t1", BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID, """{"x1":5,"y1":6}"""),
                ),
            )
            emit(ChatEvent.Done(null))
        } else {
            emit(ChatEvent.Delta("分析完成"))
            emit(ChatEvent.Done(null))
        }
    }

    override suspend fun fetchModels(): Result<List<ModelInfo>> = Result.success(emptyList())
    override suspend fun testConnection(): Result<Long> = Result.success(1L)
}

/** 首轮普通对话直接文本;之后的分析请求挂起在 gate 上再发工具调用 */
private class IsolationWarmThenGatedToolProvider(private val gate: CompletableDeferred<Unit>) : AgentProvider {
    override val profileId = "warm-then-gated"
    val requests = ArrayList<ChatRequest>()

    override fun chat(req: ChatRequest) = flow {
        requests += req
        when (requests.size) {
            1 -> {
                emit(ChatEvent.Delta("先聊两句"))
                emit(ChatEvent.Done(null))
            }
            2 -> {
                gate.await()
                emit(
                    ChatEvent.ToolCallReady(
                        ToolCall("t2", BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID, """{"x1":7,"y1":8}"""),
                    ),
                )
                emit(ChatEvent.Done(null))
            }
            else -> {
                emit(ChatEvent.Delta("分析完成"))
                emit(ChatEvent.Done(null))
            }
        }
    }

    override suspend fun fetchModels(): Result<List<ModelInfo>> = Result.success(emptyList())
    override suspend fun testConnection(): Result<Long> = Result.success(1L)
}

/** 只关心时序读取的工作区:[toolGate] 非空时让读取挂起,模拟「工具执行中」 */
private class IsolationWorkspace(
    private val toolGate: CompletableDeferred<Unit>? = null,
) : NotebookWorkspacePort {
    val timestampCalls = ArrayList<PageRef>()
    val deleteCalls = ArrayList<String>()
    var entered = false
        private set
    /** 时序读取发生后回调(测试用来武装「下一次取时钟」的导航) */
    var onRead: (() -> Unit)? = null

    override suspend fun listPages(notebookId: String): List<PageInfo> =
        listOf(PageInfo("p0001", 1, true), PageInfo("p0002", 2, false))

    override suspend fun pageDimensions(page: PageRef): PageDimensions = PageDimensions(1000f, 1400f)

    override suspend fun addPage(
        notebookId: String,
        position: String,
        anchorPageId: String?,
        switchTo: Boolean,
    ): PageInfo? = null

    override suspend fun deletePage(notebookId: String, pageId: String): Boolean {
        deleteCalls += pageId
        return true
    }

    override suspend fun insertMarkdownCard(
        page: PageRef,
        markdown: String,
        xPx: Float,
        yPx: Float,
        widthPx: Float,
    ): String? = null

    override suspend fun listElements(page: PageRef): List<PageElementSummary> = emptyList()

    override suspend fun moveElement(
        page: PageRef,
        elementId: String,
        elementKind: String?,
        targetX: Float?,
        targetY: Float?,
        dx: Float?,
        dy: Float?,
    ): Boolean = false

    override suspend fun deleteElement(page: PageRef, elementId: String, elementKind: String?): Boolean = false

    override suspend fun elementTimestamps(
        page: PageRef,
        rect: Rect4,
        includeErased: Boolean,
    ): List<ElementTimestamp>? {
        entered = true
        timestampCalls += page
        onRead?.invoke()
        toolGate?.await()
        return listOf(ElementTimestamp("s1", "stroke", 1700000000000L, 1200L, false))
    }
}
