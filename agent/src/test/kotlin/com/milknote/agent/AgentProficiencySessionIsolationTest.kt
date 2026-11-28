package com.milknote.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// =============================================================================
// 按需时序读取的「导航失效」契约:每次调用都绑定发起页 + 会话 + 绑定代际;
// 真实切页/切会话/清空/删除当前会话后永久失效(回到原页原会话也不复活)。
// =============================================================================

@OptIn(ExperimentalCoroutinesApi::class)
class TimestampsNavigationRevocationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val page1 = PageRef("nb1", "p0001")
    private val page2 = PageRef("nb1", "p0002")

    private fun rig(
        scope: CoroutineScope,
        ws: NotebookWorkspacePort,
        provider: AgentProvider,
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
            workspace = ws,
        )
        controller.addProfile("glm", "", "", "sk-test")
        controller.setSummaryConfig(SummaryConfig(enabled = false))
        controller.openDrawer(page1)
        return IsolationRig(controller, store)
    }

    @Test
    fun `流式期间切页后模型再调用时序则不弹确认也不读取`() = runTest {
        val ws = IsolationWorkspace()
        val gate = CompletableDeferred<Unit>()
        val provider = IsolationGatedToolProvider(gate)
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.setDraftText("看看这页")
        controller.send()
        advanceUntilIdle()
        assertEquals(1, provider.requests.size)
        assertNull("工具还没发出,不应有确认", controller.state.value.pendingConfirmation)

        controller.bindPage(page2)
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull("导航后不得为旧页弹确认", controller.state.value.pendingConfirmation)
        assertTrue("授权已随导航失效,不得读取", ws.timestampCalls.isEmpty())
        assertEquals("不得再发续轮", 1, provider.requests.size)
        assertTrue(controller.state.value.messages.isEmpty())
    }

    @Test
    fun `等待确认后清空会话则同意也不读取`() = runTest {
        val ws = IsolationWorkspace()
        val provider = IsolationToolThenTextProvider()
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.setDraftText("看看这页")
        controller.send()
        advanceUntilIdle()
        val analysisSession = controller.state.value.currentSessionId!!
        assertNotNull(controller.state.value.pendingConfirmation)

        controller.clearSession()
        val freshSession = controller.state.value.currentSessionId!!
        assertNotEquals(analysisSession, freshSession)
        assertNull("清空后确认条必须撤销", controller.state.value.pendingConfirmation)

        controller.approvePendingTool()
        advanceUntilIdle()

        assertTrue("清空后不得读旧页", ws.timestampCalls.isEmpty())
        assertEquals(1, provider.requests.size)
        assertTrue(controller.state.value.messages.isEmpty())
        assertEquals(freshSession, controller.state.value.currentSessionId)
    }

    @Test
    fun `等待确认后删除当前会话则同意也不读取`() = runTest {
        val ws = IsolationWorkspace()
        val provider = IsolationToolThenTextProvider()
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.setDraftText("看看这页")
        controller.send()
        advanceUntilIdle()
        val analysisSession = controller.state.value.currentSessionId!!
        assertNotNull(controller.state.value.pendingConfirmation)

        controller.deleteSession(analysisSession)
        assertNull("删除后确认条必须撤销", controller.state.value.pendingConfirmation)
        controller.approvePendingTool()
        advanceUntilIdle()

        assertTrue("删除后不得读旧页", ws.timestampCalls.isEmpty())
        assertEquals(1, provider.requests.size)
        assertTrue(controller.state.value.messages.isEmpty())
        assertTrue(rig.store.loadNotebookSessions("nb1").isEmpty())
    }

    @Test
    fun `离开再回到同一页同一会话也不复活`() = runTest {
        val ws = IsolationWorkspace()
        val provider = IsolationWarmThenPendingToolProvider()
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.setDraftText("你好")
        controller.send()
        advanceUntilIdle()
        val originalId = controller.state.value.currentSessionId!!
        val before = rig.store.loadSession("nb1", originalId)!!.messages.size
        assertEquals(2, before)

        controller.setDraftText("看看这页")
        controller.send()
        advanceUntilIdle()
        assertEquals(2, provider.requests.size)
        assertNotNull(controller.state.value.pendingConfirmation)

        controller.bindPage(page2)
        controller.bindPage(page1)
        assertEquals("回到原会话", originalId, controller.state.value.currentSessionId)
        assertNull("回到原页也不得复活确认", controller.state.value.pendingConfirmation)

        controller.approvePendingTool()
        advanceUntilIdle()

        assertTrue("授权已永久失效,不得再读旧页", ws.timestampCalls.isEmpty())
        assertEquals("不得再发续轮", 2, provider.requests.size)
        val saved = rig.store.loadSession("nb1", originalId)!!
        assertEquals("原会话不得写入分析", before, saved.messages.size)
        assertTrue(controller.state.value.messages.none { it.text.contains("分析完成") })
    }

    @Test
    fun `同页刷新不撤销等待中的确认`() = runTest {
        val ws = IsolationWorkspace()
        val provider = IsolationToolThenTextProvider()
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller
        val sessionId = controller.state.value.currentSessionId!!

        controller.setDraftText("看看这页")
        controller.send()
        advanceUntilIdle()
        assertNotNull(controller.state.value.pendingConfirmation)

        // 同页刷新、重开抽屉、重列会话都不是导航,不得废止本次授权
        controller.bindPage(page1)
        controller.openDrawer(page1)
        controller.closeDrawer()
        controller.refreshSessions()
        assertNotNull("同页刷新后确认仍应保留", controller.state.value.pendingConfirmation)

        controller.approvePendingTool()
        advanceUntilIdle()

        assertEquals(1, ws.timestampCalls.size)
        assertEquals(page1, ws.timestampCalls.single())
        assertEquals(2, provider.requests.size)
        val saved = rig.store.loadSession("nb1", sessionId)!!
        assertTrue(saved.messages.any { it.text.contains("分析完成") })
    }
}

private class IsolationRig(
    val controller: AgentController,
    val store: AgentStore,
)

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
            emit(ChatEvent.Delta("不该续轮"))
            emit(ChatEvent.Done(null))
        }
    }

    override suspend fun fetchModels(): Result<List<ModelInfo>> = Result.success(emptyList())
    override suspend fun testConnection(): Result<Long> = Result.success(1L)
}

/** 首轮先吐一段正文再发工具调用,第二轮给文本 */
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

/** 首轮普通对话直接文本;第二轮发一次时序调用;之后给文本 */
private class IsolationWarmThenPendingToolProvider : AgentProvider {
    override val profileId = "warm-then-pending"
    val requests = ArrayList<ChatRequest>()

    override fun chat(req: ChatRequest) = flow {
        requests += req
        when (requests.size) {
            1 -> {
                emit(ChatEvent.Delta("先聊两句"))
                emit(ChatEvent.Done(null))
            }
            2 -> {
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

/** 只关心时序读取的工作区 */
private class IsolationWorkspace : NotebookWorkspacePort {
    val timestampCalls = ArrayList<PageRef>()

    override suspend fun listPages(notebookId: String): List<PageInfo> =
        listOf(PageInfo("p0001", 1, true), PageInfo("p0002", 2, false))

    override suspend fun pageDimensions(page: PageRef): PageDimensions = PageDimensions(1000f, 1400f)

    override suspend fun addPage(
        notebookId: String,
        position: String,
        anchorPageId: String?,
        switchTo: Boolean,
    ): PageInfo? = null

    override suspend fun deletePage(notebookId: String, pageId: String): Boolean = true

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
        timestampCalls += page
        return listOf(ElementTimestamp("s1", "stroke", 1700000000000L, 1200L, false))
    }
}
