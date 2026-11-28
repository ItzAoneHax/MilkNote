package com.milknote.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// =============================================================================
// AI 按需调用时序工具:普通请求可见 → 调用先确认 → 同意才读取 → 拒绝/导航都不读
// =============================================================================

@OptIn(ExperimentalCoroutinesApi::class)
class TimestampsOnDemandGrantTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val page1 = PageRef("nb1", "p0001")
    private val page2 = PageRef("nb1", "p0002")

    private fun rig(
        scope: CoroutineScope,
        ws: NotebookWorkspacePort,
        provider: AgentProvider,
        clock: () -> Long = System::currentTimeMillis,
        beforeExchange: (() -> Unit)? = null,
    ): Rig {
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
            beforeExchange = beforeExchange,
        )
        controller.addProfile("glm", "", "", "sk-test")
        controller.setSummaryConfig(SummaryConfig(enabled = false))
        controller.openDrawer(page1)
        return Rig(controller, store, notebooks)
    }

    @Test
    fun `普通请求工具可见-调用先等待授权-同意后只读发起页`() = runTest {
        val ws = OnDemandTimestampWorkspace(onDemandElements(2))
        val provider = OnDemandProvider { _, i ->
            if (i == 0) {
                listOf(
                    ChatEvent.ToolCallReady(
                        ToolCall(
                            "t1",
                            BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID,
                            """{"x1":10,"y1":20,"x2":300,"y2":400,"page_id":"p0002"}""",
                        ),
                    ),
                    ChatEvent.Done(null),
                )
            } else {
                listOf(ChatEvent.Delta("读完并作答"), ChatEvent.Done(null))
            }
        }
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.setDraftText("帮我看看这套题")
        controller.send()
        advanceUntilIdle()

        val first = provider.requests.first()
        assertTrue("普通请求也必须能看到时序工具", first.tools.any { it.name == BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID })
        assertTrue(first.system.contains(BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID))
        assertTrue(first.system.contains("会先请用户授权"))
        assertFalse("不得再出现旧按钮授权模板", first.system.contains("本轮已获授权"))

        val pending = controller.state.value.pendingConfirmation
        assertNotNull("工具调用必须先进入确认条", pending)
        assertEquals(BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID, pending!!.toolId)
        assertEquals("同意读取", pending.confirmLabel)
        assertEquals("拒绝", pending.cancelLabel)
        assertTrue(pending.detail.contains("不含笔记原文"))
        assertTrue("同意前不得读取时序", ws.timestampCalls.isEmpty())

        controller.approvePendingTool()
        advanceUntilIdle()

        assertEquals(1, ws.timestampCalls.size)
        assertEquals("参数 page_id 不能改授权页", page1, ws.timestampCalls[0].first)
        assertEquals(Rect4(10f, 20f, 300f, 400f), ws.timestampCalls[0].second)
        assertEquals(2, provider.requests.size)
        val toolMsg = provider.requests[1].continuationMessages.last { it.role == "tool" }
        assertTrue(toolMsg.content.contains("kind=stroke"))
        assertFalse("不得回传笔记原文", toolMsg.content.contains("secret note"))
        assertFalse("不得回传资产路径", toolMsg.content.contains("agent/att/"))

        val trace = controller.state.value.messages.last().toolTraces.single()
        assertEquals(ToolTrace.Status.DONE, trace.status)
        assertTrue(trace.resultSummary.contains("元素时序"))
        assertEquals("轨迹不得保存原始时序", "", trace.output)

        val sessionId = controller.state.value.currentSessionId!!
        val raw = File(rig.notebooks, "nb1.milk/agent/sessions/$sessionId.json").readText()
        assertFalse("落盘不得含原始时间戳", raw.contains("17000000000"))
        assertEquals(AgentMessage.Phase.DONE, controller.state.value.messages.last().phase)
    }

    @Test
    fun `拒绝授权不执行-回传未执行提示且继续回答`() = runTest {
        val ws = OnDemandTimestampWorkspace(onDemandElements(2))
        val provider = OnDemandProvider { _, i ->
            if (i == 0) {
                listOf(
                    ChatEvent.ToolCallReady(
                        ToolCall("t1", BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID, """{"x1":10,"y1":20}"""),
                    ),
                    ChatEvent.Done(null),
                )
            } else {
                listOf(ChatEvent.Delta("那我按可见内容说"), ChatEvent.Done(null))
            }
        }
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.setDraftText("判一下这套卷子")
        controller.send()
        advanceUntilIdle()
        assertNotNull(controller.state.value.pendingConfirmation)

        controller.rejectPendingTool()
        advanceUntilIdle()

        assertTrue("拒绝后不得触达页面", ws.timestampCalls.isEmpty())
        assertEquals(2, provider.requests.size)
        val toolMsg = provider.requests[1].continuationMessages.last { it.role == "tool" }
        assertTrue(toolMsg.content, toolMsg.content.contains("未执行"))
        assertTrue(toolMsg.content.contains("不要重复"))
        val trace = controller.state.value.messages.last().toolTraces.single()
        assertEquals(ToolTrace.Status.DENIED, trace.status)
        assertEquals("用户已拒绝", trace.resultSummary)
        assertEquals("那我按可见内容说", controller.state.value.messages.last().text)
        assertEquals(AgentMessage.Phase.DONE, controller.state.value.messages.last().phase)
    }

    @Test
    fun `等待确认期间切页则撤销-同意也不会读取`() = runTest {
        val ws = OnDemandTimestampWorkspace(onDemandElements(2))
        val provider = OnDemandProvider { _, i ->
            if (i == 0) {
                listOf(
                    ChatEvent.ToolCallReady(
                        ToolCall("t1", BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID, """{"x1":10,"y1":20}"""),
                    ),
                    ChatEvent.Done(null),
                )
            } else {
                listOf(ChatEvent.Delta("不该出现"), ChatEvent.Done(null))
            }
        }
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.setDraftText("看看这页")
        controller.send()
        advanceUntilIdle()
        assertNotNull(controller.state.value.pendingConfirmation)

        controller.bindPage(page2)
        assertNull("导航后确认条立即撤销", controller.state.value.pendingConfirmation)
        controller.approvePendingTool()
        advanceUntilIdle()

        assertTrue("导航后即使点同意也不得读取旧页", ws.timestampCalls.isEmpty())
        assertEquals("撤销后不续轮", 1, provider.requests.size)
        assertTrue(controller.state.value.messages.isEmpty())
        assertTrue(controller.state.value.toast?.contains("撤销") == true)
    }

    @Test
    fun `读取过程中切页则结果作废且不发给续轮`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val ws = OnDemandTimestampWorkspace(onDemandElements(1), gate)
        val provider = OnDemandProvider { _, i ->
            if (i == 0) {
                listOf(
                    ChatEvent.ToolCallReady(
                        ToolCall("t1", BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID, """{"x1":5,"y1":6}"""),
                    ),
                    ChatEvent.Done(null),
                )
            } else {
                listOf(ChatEvent.Delta("不该续轮"), ChatEvent.Done(null))
            }
        }
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.setDraftText("看看这页")
        controller.send()
        advanceUntilIdle()
        controller.approvePendingTool()
        advanceUntilIdle()
        assertTrue("工具应已开始执行并挂起", ws.entered)

        controller.bindPage(page2)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, ws.timestampCalls.size)
        assertEquals("读取结果不得发给续轮", 1, provider.requests.size)
        assertTrue(controller.state.value.messages.isEmpty())
        assertTrue(
            "任何会话都不得承载旧页时序",
            rig.store.loadNotebookSessions("nb1").none { s ->
                s.messages.any { m -> m.text.contains("17000000000") }
            },
        )
    }

    @Test
    fun `旧熟练度意图安全降级为普通讲解且不产生授权模板`() = runTest {
        val ws = OnDemandTimestampWorkspace(onDemandElements(1))
        val provider = OnDemandProvider { _, _ ->
            listOf(ChatEvent.Delta("普通讲解"), ChatEvent.Done(null))
        }
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.sendWithIntent(Intents.Id.PROFICIENCY, "看看这页")
        advanceUntilIdle()

        val first = provider.requests.single()
        assertTrue(first.tools.any { it.name == BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID })
        assertFalse("旧入口不得恢复授权模板", first.system.contains("本轮已获授权"))
        assertFalse(first.system.contains("已就页面"))
        assertEquals(Intents.Id.EXPLAIN, controller.state.value.messages.first().intent)
        assertNull(controller.state.value.pendingConfirmation)
        assertTrue(ws.timestampCalls.isEmpty())
    }

    @Test
    fun `普通追问仍可见工具但不会自动读取`() = runTest {
        val ws = OnDemandTimestampWorkspace(onDemandElements(1))
        val provider = OnDemandProvider { _, i ->
            when (i) {
                0 -> listOf(
                    ChatEvent.ToolCallReady(
                        ToolCall("t1", BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID, """{"x1":10,"y1":20}"""),
                    ),
                    ChatEvent.Done(null),
                )
                else -> listOf(ChatEvent.Delta("继续回答"), ChatEvent.Done(null))
            }
        }
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.setDraftText("看看这页")
        controller.send()
        advanceUntilIdle()
        controller.approvePendingTool()
        advanceUntilIdle()
        assertEquals(1, ws.timestampCalls.size)

        controller.setDraftText("那第二题呢")
        controller.send()
        advanceUntilIdle()

        assertEquals("追问不得自动重读", 1, ws.timestampCalls.size)
        val followUp = provider.requests.last()
        assertTrue(followUp.tools.any { it.name == BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID })
        assertFalse(followUp.system.contains("本轮已获授权"))
        assertTrue(followUp.history.none { it.content.contains("17000000000") })
        assertNull(controller.state.value.pendingConfirmation)
    }

    @Test
    fun `重试不会自动重读时序也不继承授权模板`() = runTest {
        val ws = OnDemandTimestampWorkspace(onDemandElements(1))
        val provider = OnDemandProvider { _, i ->
            if (i == 0) {
                listOf(
                    ChatEvent.ToolCallReady(
                        ToolCall("t1", BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID, """{"x1":10,"y1":20}"""),
                    ),
                    ChatEvent.Done(null),
                )
            } else {
                listOf(ChatEvent.Delta("重试后的回答"), ChatEvent.Done(null))
            }
        }
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.setDraftText("看看这页")
        controller.send()
        advanceUntilIdle()
        controller.approvePendingTool()
        advanceUntilIdle()
        assertEquals(1, ws.timestampCalls.size)

        controller.retry()
        advanceUntilIdle()

        assertEquals("重试不得自动重读", 1, ws.timestampCalls.size)
        val retried = provider.requests.last()
        assertTrue(retried.tools.any { it.name == BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID })
        assertFalse(retried.system.contains("本轮已获授权"))
        assertTrue(retried.history.none { it.content.contains("17000000000") })
        assertNull(controller.state.value.pendingConfirmation)
    }

    @Test
    fun `同轮多次调用逐次确认-轨迹都不存原始时序`() = runTest {
        val ws = OnDemandTimestampWorkspace(onDemandElements(3))
        val provider = OnDemandProvider { _, i ->
            when (i) {
                0 -> listOf(
                    ChatEvent.ToolCallReady(
                        ToolCall("t1", BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID, """{"x1":0,"y1":0,"limit":2}"""),
                    ),
                    ChatEvent.Done(null),
                )
                1 -> listOf(
                    ChatEvent.ToolCallReady(
                        ToolCall("t2", BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID, """{"x1":0,"y1":0,"offset":2}"""),
                    ),
                    ChatEvent.Done(null),
                )
                else -> listOf(ChatEvent.Delta("整体连贯"), ChatEvent.Done(null))
            }
        }
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.setDraftText("看看这页")
        controller.send()
        advanceUntilIdle()
        assertNotNull("第一次调用等待确认", controller.state.value.pendingConfirmation)
        assertEquals(0, ws.timestampCalls.size)

        controller.approvePendingTool()
        advanceUntilIdle()
        assertEquals(1, ws.timestampCalls.size)
        assertNotNull("第二次调用必须再次确认", controller.state.value.pendingConfirmation)

        controller.approvePendingTool()
        advanceUntilIdle()
        assertEquals(2, ws.timestampCalls.size)
        assertEquals(3, provider.requests.size)

        val traces = controller.state.value.messages.last().toolTraces
        assertEquals(2, traces.size)
        assertTrue(traces.all { it.status == ToolTrace.Status.DONE })
        assertTrue(traces.all { it.output.isEmpty() })
    }

    @Test
    fun `发送后协程尚未开始就切页则不发起请求也不留消息`() = runTest {
        val ws = OnDemandTimestampWorkspace(onDemandElements(1))
        val provider = OnDemandProvider { _, _ ->
            listOf(ChatEvent.Delta("不该请求"), ChatEvent.Done(null))
        }
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.setDraftText("看看这页")
        controller.send()
        // 发送与切页落在同一帧:协程还没被调度就已经换了页
        controller.bindPage(page2)
        advanceUntilIdle()

        assertTrue("导航后不得再发请求", provider.requests.isEmpty())
        assertTrue(ws.timestampCalls.isEmpty())
        assertTrue("不得在新区会话里留下半截气泡", controller.state.value.messages.isEmpty())
        assertEquals(AgentMessage.Phase.DONE, controller.state.value.phase)
    }

    @Test
    fun `摘要请求不带工具与时序提示`() = runTest {
        val ws = OnDemandTimestampWorkspace(onDemandElements(1))
        val provider = OnDemandProvider { req, i ->
            when {
                req.system == SUMMARY_SYSTEM_PROMPT ->
                    listOf(ChatEvent.Delta("标题"), ChatEvent.Done(null))
                i == 0 -> listOf(
                    ChatEvent.ToolCallReady(
                        ToolCall("t1", BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID, """{"x1":10,"y1":20}"""),
                    ),
                    ChatEvent.Done(null),
                )
                else -> listOf(ChatEvent.Delta("答"), ChatEvent.Done(null))
            }
        }
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), ws, provider)
        val controller = rig.controller

        controller.setDraftText("看看这页")
        controller.send()
        advanceUntilIdle()
        controller.approvePendingTool()
        advanceUntilIdle()

        val sessionId = controller.state.value.currentSessionId!!
        controller.regenerateSummary(sessionId)
        advanceUntilIdle()

        val summary = provider.requests.last()
        assertEquals(SUMMARY_SYSTEM_PROMPT, summary.system)
        assertTrue("摘要不得带工具", summary.tools.isEmpty())
        assertTrue(summary.continuationMessages.isEmpty())
    }

    @Test
    fun `落盘前一刻切页也不会把时序轮写进新会话`() = runTest {
        lateinit var controller: AgentController
        var navArmed = false
        val ws = OnDemandTimestampWorkspace(onDemandElements(1))
        val provider = OnDemandProvider { _, i ->
            if (i == 0) {
                listOf(
                    ChatEvent.Delta("我先看看"),
                    ChatEvent.ToolCallReady(
                        ToolCall("t1", BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID, """{"x1":5,"y1":6}"""),
                    ),
                    ChatEvent.Done(null),
                )
            } else {
                listOf(ChatEvent.Delta("分析完成"), ChatEvent.Done(null))
            }
        }
        // 精确命中「finish 守卫已通过、persistWith 还没取会话快照」的竞态窗口:
        // 无锁/无复验的实现会把旧页时序轮写进切页后的新会话。
        val rig = rig(
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            ws,
            provider,
            clock = {
                if (navArmed) {
                    navArmed = false
                    controller.bindPage(page2)
                }
                System.currentTimeMillis()
            },
        )
        controller = rig.controller
        ws.onRead = { navArmed = true }

        controller.setDraftText("看看这页")
        controller.send()
        advanceUntilIdle()
        controller.approvePendingTool()
        advanceUntilIdle()

        assertEquals("工具读取已在授权页发生", 1, ws.timestampCalls.size)
        assertEquals("切页发生在续轮之后", 2, provider.requests.size)
        assertEquals(page2, controller.state.value.boundPage)
        assertTrue(controller.state.value.messages.isEmpty())
        assertTrue(
            "任何会话文件都不得出现时序轮结果",
            rig.store.loadNotebookSessions("nb1")
                .none { s -> s.messages.any { it.text.contains("分析完成") } },
        )
        assertTrue(rig.store.loadSessions("nb1", "p0001").isEmpty())
    }

    @Test
    fun `交换协程调度前导航则不发起请求也不留消息`() = runTest {
        lateinit var controller: AgentController
        var navigate = false
        val ws = OnDemandTimestampWorkspace(onDemandElements(1))
        val provider = OnDemandProvider { _, _ ->
            listOf(ChatEvent.Delta("不该请求"), ChatEvent.Done(null))
        }
        val rig = rig(
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            ws,
            provider,
            beforeExchange = {
                if (navigate) {
                    navigate = false
                    controller.bindPage(page2)
                }
            },
        )
        controller = rig.controller
        navigate = true

        controller.setDraftText("看看这页")
        controller.send()
        advanceUntilIdle()

        assertTrue("交换协程调度前导航后不得再发请求", provider.requests.isEmpty())
        assertTrue(controller.state.value.messages.isEmpty())
        assertEquals(page2, controller.state.value.boundPage)
        assertEquals(AgentMessage.Phase.DONE, controller.state.value.phase)
    }
}

private class Rig(
    val controller: AgentController,
    val store: AgentStore,
    val notebooks: File,
)

private fun onDemandElements(n: Int): List<ElementTimestamp> = (1..n).map { i ->
    ElementTimestamp("e$i", if (i % 2 == 0) "card" else "stroke", 1700000000000L + i, 100L + i, false)
}

private class OnDemandProvider(
    private val script: (ChatRequest, Int) -> List<ChatEvent>,
) : AgentProvider {
    override val profileId = "on-demand"
    val requests = ArrayList<ChatRequest>()

    override fun chat(req: ChatRequest) = flow {
        requests.add(req)
        script(req, requests.size - 1).forEach { emit(it) }
    }

    override suspend fun fetchModels(): Result<List<ModelInfo>> = Result.success(emptyList())
    override suspend fun testConnection(): Result<Long> = Result.success(1L)
}

private class OnDemandTimestampWorkspace(
    private val elements: List<ElementTimestamp>,
    private val gate: CompletableDeferred<Unit>? = null,
) : NotebookWorkspacePort {
    private val pages = listOf(PageInfo("p0001", 1, true), PageInfo("p0002", 2, false))
    val timestampCalls = ArrayList<Triple<PageRef, Rect4, Boolean>>()
    var entered = false
        private set
    /** 时序读取后回调(测试用来武装“下一次取时钟”的导航) */
    var onRead: (() -> Unit)? = null

    override suspend fun listPages(notebookId: String): List<PageInfo> = pages

    override suspend fun pageDimensions(page: PageRef): PageDimensions? =
        if (pages.any { it.pageId == page.pageId }) PageDimensions(1000f, 1400f) else null

    override suspend fun addPage(
        notebookId: String,
        position: String,
        anchorPageId: String?,
        switchTo: Boolean,
    ): PageInfo? = null

    override suspend fun deletePage(notebookId: String, pageId: String): Boolean = false

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
        timestampCalls += Triple(page, rect, includeErased)
        onRead?.invoke()
        gate?.await()
        return if (pages.any { it.pageId == page.pageId }) elements else null
    }
}
