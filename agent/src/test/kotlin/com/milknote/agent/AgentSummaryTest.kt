package com.milknote.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test

/** AI 会话摘要引擎:自动触发时机、模型解析、手动刷新、并发安全 */
class AgentSummaryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun isSummary(req: ChatRequest): Boolean = req.system == SUMMARY_SYSTEM_PROMPT

    @Test
    fun `首轮完成自动生成摘要`() = runTest {
        val fake = FakeProvider { req ->
            if (isSummary(req)) {
                listOf(ChatEvent.Delta("「二次方程求解」"), ChatEvent.Done(null))
            } else {
                listOf(ChatEvent.Delta("答"), ChatEvent.Done(null))
            }
        }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller

        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        val sessionId = c.state.value.currentSessionId!!
        val saved = e.store.loadSession("nb1", sessionId)!!
        // 引号被清洗,长度 ≤80
        assertEquals("二次方程求解", saved.summary)
        assertEquals(2, saved.summaryMessageCount)
        assertEquals(c.state.value.activeModelId, saved.summaryModelId)
        assertTrue(saved.summaryUpdatedAt > 0)
        assertEquals(2, fake.requests.size)   // 1 聊天 + 1 摘要
        assertEquals("二次方程求解", c.state.value.sessions.single().summary)
        assertTrue(c.state.value.summarizingSessionIds.isEmpty())

        // 摘要请求参数:低温度、短输出、glm 支持 OFF → 显式关思考
        val summaryReq = fake.requests.last()
        assertEquals(0.2, summaryReq.temperature, 0.0001)
        assertEquals(160, summaryReq.maxTokens)
        assertEquals(ReasoningEffort.OFF, summaryReq.reasoningEffort)
        assertTrue(summaryReq.stream)
        assertTrue(summaryReq.images.isEmpty())
        assertTrue(summaryReq.newUserText.length <= 3000)
    }

    @Test
    fun `每三轮增量刷新摘要`() = runTest {
        var summaryCalls = 0
        val fake = FakeProvider { req ->
            if (isSummary(req)) {
                summaryCalls++
                listOf(ChatEvent.Delta("摘要$summaryCalls"), ChatEvent.Done(null))
            } else {
                listOf(ChatEvent.Delta("答"), ChatEvent.Done(null))
            }
        }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        assertEquals(1, summaryCalls)

        // 第 2、3 轮:增量 2/4 < 6 → 不刷新
        repeat(2) { i ->
            c.setDraftText("追问${i + 1}")
            c.send()
            advanceUntilIdle()
        }
        assertEquals(1, summaryCalls)

        // 第 4 轮:已完成 8 条,增量 6 → 刷新
        c.setDraftText("追问3")
        c.send()
        advanceUntilIdle()
        assertEquals(2, summaryCalls)

        val saved = e.store.loadSession("nb1", c.state.value.currentSessionId!!)!!
        assertEquals("摘要2", saved.summary)
        assertEquals(8, saved.summaryMessageCount)
    }

    @Test
    fun `关闭自动摘要时不额外发请求`() = runTest {
        val fake = FakeProvider { listOf(ChatEvent.Delta("答"), ChatEvent.Done(null)) }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))
        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        assertEquals(1, fake.requests.size)
        assertTrue(e.store.loadSession("nb1", c.state.value.currentSessionId!!)!!.summary.isBlank())
    }

    @Test
    fun `手动重新生成摘要不受开关限制`() = runTest {
        val fake = FakeProvider { req ->
            if (isSummary(req)) listOf(ChatEvent.Delta("手动摘要"), ChatEvent.Done(null))
            else listOf(ChatEvent.Delta("答"), ChatEvent.Done(null))
        }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))
        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        val sessionId = c.state.value.currentSessionId!!
        assertEquals(1, fake.requests.size)

        c.regenerateSummary(sessionId)
        advanceUntilIdle()

        assertEquals(2, fake.requests.size)
        assertEquals("手动摘要", e.store.loadSession("nb1", sessionId)!!.summary)
        assertEquals("手动摘要", c.state.value.sessions.single().summary)
    }

    @Test
    fun `自定义摘要模型可为非视觉模型`() = runTest {
        val fake = FakeProvider { req ->
            if (isSummary(req)) listOf(ChatEvent.Delta("小模型摘要"), ChatEvent.Done(null))
            else listOf(ChatEvent.Delta("答"), ChatEvent.Done(null))
        }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.openDrawer(PageRef("nb1", "p0001"))

        c.addProfile("deepseek", "第二家", "", "sk-y")
        val textProfileId = c.state.value.profiles.last().id
        c.addModel(textProfileId, "deepseek-text", declaredVision = true)
        c.updateModel(textProfileId, "deepseek-text", vision = false)
        c.setSummaryConfig(
            SummaryConfig(
                enabled = true,
                mode = SummaryModelMode.CUSTOM,
                profileId = textProfileId,
                modelId = "deepseek-text",
            ),
        )
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        val summaryReq = fake.requests.last()
        assertTrue(isSummary(summaryReq))
        assertEquals("deepseek-text", summaryReq.model)   // 非视觉模型也可作摘要
        val saved = e.store.loadSession("nb1", c.state.value.currentSessionId!!)!!
        assertEquals("deepseek-text", saved.summaryModelId)
        assertEquals("小模型摘要", saved.summary)
    }

    @Test
    fun `自定义摘要模型失效时回退聊天模型`() = runTest {
        val fake = FakeProvider { req ->
            if (isSummary(req)) listOf(ChatEvent.Delta("回退摘要"), ChatEvent.Done(null))
            else listOf(ChatEvent.Delta("答"), ChatEvent.Done(null))
        }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.openDrawer(PageRef("nb1", "p0001"))
        c.setSummaryConfig(
            SummaryConfig(
                enabled = true,
                mode = SummaryModelMode.CUSTOM,
                profileId = "ghost-profile",
                modelId = "ghost-model",
            ),
        )
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        val summaryReq = fake.requests.last()
        assertTrue(isSummary(summaryReq))
        assertEquals(c.state.value.activeModelId, summaryReq.model)
        assertEquals("回退摘要", e.store.loadSession("nb1", c.state.value.currentSessionId!!)!!.summary)
    }

    @Test
    fun `无可用Key时静默跳过摘要且不报错`() = runTest {
        val fake = FakeProvider { listOf(ChatEvent.Delta("答"), ChatEvent.Done(null)) }
        val e = agentHarness(
            tmp.newFolder(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            key = null,
        ) { _, _, _ -> fake }
        val c = e.controller
        // 无 Key 时发送会被 setupRequired 拦住;这里直接调摘要入口验证静默跳过
        c.openDrawer(PageRef("nb1", "p0001"))
        c.regenerateSummary(c.state.value.currentSessionId!!)
        advanceUntilIdle()
        assertTrue(fake.requests.isEmpty())
        assertNull(c.state.value.error)
        assertTrue(c.state.value.summarizingSessionIds.isEmpty())
    }

    @Test
    fun `离开会话时补一次摘要`() = runTest {
        var summaryCalls = 0
        val fake = FakeProvider { req ->
            if (isSummary(req)) {
                summaryCalls++
                if (summaryCalls == 1) {
                    listOf(ChatEvent.Done(null))   // 空摘要 → 不写入
                } else {
                    listOf(ChatEvent.Delta("离开时补的摘要"), ChatEvent.Done(null))
                }
            } else {
                listOf(ChatEvent.Delta("答"), ChatEvent.Done(null))
            }
        }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        assertEquals(1, summaryCalls)
        val oldId = c.state.value.currentSessionId!!
        assertTrue(e.store.loadSession("nb1", oldId)!!.summary.isBlank())

        c.clearSession()
        advanceUntilIdle()

        assertEquals(2, summaryCalls)
        assertEquals("离开时补的摘要", e.store.loadSession("nb1", oldId)!!.summary)
        // 新会话尚未落盘(空会话不写文件)
        assertNull(e.store.loadSession("nb1", c.state.value.currentSessionId!!))
    }

    @Test
    fun `摘要不覆盖并发新增消息`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val provider = GatedProvider(gate)
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> provider }
        val c = e.controller
        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        // 摘要请求已挂起
        val sessionId = c.state.value.currentSessionId!!
        assertEquals(setOf(sessionId), c.state.value.summarizingSessionIds)
        // 列表项立即反映「摘要生成中」
        assertTrue(c.state.value.sessions.single().summaryGenerating)

        // 摘要未返回期间继续追问
        c.setDraftText("第二问")
        c.send()
        advanceUntilIdle()
        assertEquals(4, c.state.value.messages.size)
        assertTrue(c.state.value.sessions.single().summaryGenerating)

        gate.complete(Unit)
        advanceUntilIdle()

        val saved = e.store.loadSession("nb1", sessionId)!!
        assertEquals(4, saved.messages.size)       // 消息未被摘要覆盖
        assertEquals("摘要文本", saved.summary)
        assertEquals(4, saved.summaryMessageCount)
        assertTrue(c.state.value.summarizingSessionIds.isEmpty())
        // 完成后列表不得残留 summaryGenerating=true
        assertFalse(c.state.value.sessions.single().summaryGenerating)
    }

    @Test
    fun `只改baseUrl不会清掉摘要与提及配置`() = runTest {
        val fake = FakeProvider { listOf(ChatEvent.Done(null)) }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        val pid = c.state.value.activeProfileId!!
        c.setSummaryConfig(SummaryConfig(enabled = false, mode = SummaryModelMode.CUSTOM, profileId = "p2", modelId = "m2"))
        c.setAutoMentionAttachments(false)

        c.updateBaseUrl(pid, "  https://proxy.example/v1  ")

        val book = AgentStore(e.filesRoot, e.notebooksRoot).loadProfiles()
        assertEquals("https://proxy.example/v1", book.profiles.first { it.id == pid }.baseUrl)
        assertFalse(book.summaryConfig.enabled)
        assertEquals(SummaryModelMode.CUSTOM, book.summaryConfig.mode)
        assertEquals("p2", book.summaryConfig.profileId)
        assertEquals("m2", book.summaryConfig.modelId)
        assertFalse(book.autoMentionAttachments)
        assertEquals("https://proxy.example/v1", c.state.value.profiles.first { it.id == pid }.baseUrl)

        // 未知 profile:无副作用
        c.updateBaseUrl("ghost", "https://elsewhere/v1")
        assertEquals(
            "https://proxy.example/v1",
            AgentStore(e.filesRoot, e.notebooksRoot).loadProfiles().profiles.first { it.id == pid }.baseUrl,
        )
    }

    @Test
    fun `摘要配置与自动提及开关落盘`() = runTest {
        val fake = FakeProvider { listOf(ChatEvent.Done(null)) }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller

        c.setSummaryConfig(SummaryConfig(enabled = false, mode = SummaryModelMode.CUSTOM, profileId = "p", modelId = "m"))
        c.setAutoMentionAttachments(false)

        val book = AgentStore(e.filesRoot, e.notebooksRoot).loadProfiles()
        assertFalse(book.summaryConfig.enabled)
        assertEquals(SummaryModelMode.CUSTOM, book.summaryConfig.mode)
        assertEquals("p", book.summaryConfig.profileId)
        assertEquals("m", book.summaryConfig.modelId)
        assertFalse(book.autoMentionAttachments)

        assertFalse(c.state.value.summaryConfig.enabled)
        assertFalse(c.state.value.autoMentionAttachments)

        // 重新装配的控制器从同一磁盘目录恢复
        val restored = AgentController(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            store = AgentStore(e.filesRoot, e.notebooksRoot),
            snipSource = null,
            annotationSink = null,
            keyFor = { "sk-test" },
            providerFactory = { _, _, _ -> fake },
        )
        assertFalse(restored.state.value.summaryConfig.enabled)
        assertFalse(restored.state.value.autoMentionAttachments)
        assertEquals("p", restored.state.value.summaryConfig.profileId)
    }
}
