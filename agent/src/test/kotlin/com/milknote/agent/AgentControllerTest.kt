package com.milknote.agent

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import java.io.File

/** 脚本化假 Provider(§14 FakeAgentProvider:立即完成/429/断流/取消) */
class FakeProvider(
    private val script: (ChatRequest) -> List<ChatEvent>,
) : AgentProvider {
    override val profileId = "fake"
    val requests = ArrayList<ChatRequest>()

    override fun chat(req: ChatRequest) = flow {
        requests.add(req)
        script(req).forEach { emit(it) }
    }

    override suspend fun fetchModels(): Result<List<ModelInfo>> = Result.success(emptyList())
    override suspend fun testConnection(): Result<Long> = Result.success(42)
}

/** 假页面渲染源:固定字节 PNG */
private class FakeSnipSource(private val pageCount: Int = 3) : SnipSource {
    override suspend fun captureSnip(page: PageRef, rect: Rect4): SnipResult =
        SnipResult(byteArrayOf(1, 2, 3), false, rect.width.toInt(), rect.height.toInt(), ImageBudget.logicalNameSnip(rect.width.toInt(), rect.height.toInt()))

    override suspend fun capturePage(page: PageRef): SnipResult =
        SnipResult(byteArrayOf(4, 5, 6), false, 100, 100, ImageBudget.logicalNamePage(page.pageId))

    override suspend fun pagesOf(notebookId: String): List<PageRef> =
        (1..pageCount).map { PageRef(notebookId, "p%04d".format(it)) }
}

private class RecordingSink : AnnotationSink {
    val inserted = ArrayList<Triple<PageRef, Rect4?, Boolean>>()
    override suspend fun insertCard(page: PageRef, anchor: Rect4?, text: String, latex: Boolean, cardWidth: Float): String? {
        inserted.add(Triple(page, anchor, latex))
        return "card_${inserted.size}"
    }
}

/** 测试环境:显式持有目录与 fake 引用 */
private class Env(
    val controller: AgentController,
    val filesRoot: File,
    val notebooksRoot: File,
    val provider: FakeProvider,
    val sink: RecordingSink,
)

/** AgentController 状态机(§14:全链路核心逻辑,不依赖网络) */
class AgentControllerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun env(
        scope: kotlinx.coroutines.CoroutineScope,
        key: String? = "sk-x",
        pageCount: Int = 3,
        script: (ChatRequest) -> List<ChatEvent>,
    ): Env {
        val filesRoot = tmp.newFolder()
        val notebooksRoot = tmp.newFolder()
        val sink = RecordingSink()
        val fake = FakeProvider(script)
        val c = AgentController(
            scope = scope,
            store = AgentStore(filesRoot, notebooksRoot),
            snipSource = FakeSnipSource(pageCount),
            annotationSink = sink,
            keyFor = { key },
            providerFactory = { _, _, _ -> fake },
        )
        c.addProfile("glm", "", "", key ?: "")
        return Env(c, filesRoot, notebooksRoot, fake, sink)
    }

    @Test
    fun `无配置时setupRequired引导不崩溃`() = runTest {
        val store = AgentStore(tmp.newFolder(), tmp.newFolder())
        val c = AgentController(backgroundScope, store, null, null, { null })
        assertTrue(c.state.value.setupRequired)
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        assertTrue(c.state.value.setupRequired)
        assertTrue(c.state.value.messages.isEmpty())
    }

    @Test
    fun `框选讲解全链路-流式与落盘`() = runTest {
        val e = env(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))) { listOf(
            ChatEvent.Delta("第一"),
            ChatEvent.Delta("段"),
            ChatEvent.Usage(100, 5),
            ChatEvent.Done("stop"),
        ) }
        val c = e.controller
        c.openDrawer(PageRef("nb1", "p0001"))
        advanceUntilIdle()

        val name = c.addSnip(Rect4(10f, 10f, 500f, 300f))
        assertNotNull(name)
        assertEquals(1, c.state.value.draftAttachments.size)

        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        val s = c.state.value
        assertTrue(s.drawerOpen)
        assertEquals(2, s.messages.size)
        val asst = s.messages.last()
        assertEquals("第一段", asst.text)
        assertEquals(AgentMessage.Phase.DONE, asst.phase)
        assertEquals(100, asst.promptTokens)
        assertEquals(Intents.Id.EXPLAIN, s.messages.first().intent)

        // 讲解默认带当前页 → 2 图,图在最后一条 user(§6.2)
        val req = e.provider.requests.last()
        assertEquals(2, req.images.size)
        assertTrue(req.newUserText.startsWith("[图1] [图2]"))

        // 附件与会话落盘(.milk/agent/,§7.2)
        assertTrue(File(e.notebooksRoot, "nb1.milk/agent/att").listFiles()!!.isNotEmpty())
        assertTrue(File(e.notebooksRoot, "nb1.milk/agent/sessions").listFiles()!!.isNotEmpty())
    }

    @Test
    fun `追问轮默认无新图`() = runTest {
        val e = env(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))) { listOf(ChatEvent.Delta("答"), ChatEvent.Done(null)) }
        val c = e.controller
        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        assertEquals(1, e.provider.requests.last().images.size)

        c.setDraftText("第二条是什么?")
        c.send()
        advanceUntilIdle()
        val follow = e.provider.requests.last()
        assertEquals(0, follow.images.size)
        assertEquals("第二条是什么?", follow.newUserText)
        // 历史窗口包含上一轮
        assertTrue(follow.history.any { it.content.contains("讲解") || it.role == "user" })
    }

    @Test
    fun `空响应自动重试一次`() = runTest {
        var calls = 0
        val e = env(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))) {
            calls++
            if (calls == 1) listOf(ChatEvent.Error(AgentError.EmptyResponse))
            else listOf(ChatEvent.Delta("答"), ChatEvent.Done(null))
        }
        e.controller.openDrawer(PageRef("nb1", "p0001"))
        e.controller.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        assertEquals(2, calls)   // 空响应不落空气泡:静默重发一次
        assertEquals("答", e.controller.state.value.messages.last().text)
        assertEquals(AgentMessage.Phase.DONE, e.controller.state.value.messages.last().phase)
    }

    @Test
    fun `模型行编辑与整表替换(harness 选择列表数据面)`() = runTest {
        val e = env(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))) {
            listOf(ChatEvent.Done(null))
        }
        val c = e.controller
        val pid = c.state.value.activeProfileId!!
        val first = c.state.value.profiles.first().models.first().id

        // 行编辑:勾「图片」= 视觉声明(来源 DECLARED);思考档位/容量改写
        c.updateModel(pid, first, vision = true, thinkingLevels = listOf(ReasoningEffort.OFF, ReasoningEffort.HIGH), contextWindowK = 256, maxTokensK = 32)
        val edited = c.state.value.profiles.first().models.first()
        assertTrue(edited.vision)
        assertEquals(VisionSource.DECLARED.name, edited.visionSource)
        assertEquals(listOf("OFF", "HIGH"), edited.thinkingLevels)
        assertEquals(256, edited.contextWindowK)
        assertEquals(32, edited.maxTokensK)

        // 整表替换(拉取勾选器应用):保留已配置行声明,并入新行;activeModel 失效回退
        val kept = edited
        val fresh = ModelInfo(id = "new-vlm", vision = true)
        c.setModels(pid, listOf(kept, fresh))
        val p2 = c.state.value.profiles.first()
        assertEquals(listOf(kept.id, fresh.id), p2.models.map { it.id })
        assertEquals(kept.id, p2.activeModelId)   // 保留仍勾选的原默认
        // 默认被取消勾选 → 回退首个视觉模型
        c.setModels(pid, listOf(fresh))
        assertEquals("new-vlm", c.state.value.profiles.first().activeModelId)
    }

    @Test
    fun `思考过程累积展示并随会话恢复`() = runTest {
        val e = env(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))) {
            listOf(
                ChatEvent.Thinking("先想"),
                ChatEvent.Thinking("清楚"),
                ChatEvent.Delta("答案"),
                ChatEvent.Usage(50, 20),
                ChatEvent.Done(null),
            )
        }
        val c = e.controller
        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        val msg = c.state.value.messages.last()
        assertEquals("先想清楚", msg.reasoning)
        assertEquals("答案", msg.text)

        // 落盘 → 重开抽屉恢复
        c.closeDrawer()
        c.openDrawer(PageRef("nb1", "p0001"))
        assertEquals("先想清楚", c.state.value.messages.last().reasoning)
    }

    @Test
    fun `RateLimit自动退避重试成功`() = runTest {
        var calls = 0
        val e = env(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))) {
            calls++
            if (calls == 1) listOf(ChatEvent.Error(AgentError.RateLimit("429")))
            else listOf(ChatEvent.Delta("ok"), ChatEvent.Done(null))
        }
        e.controller.openDrawer(PageRef("nb1", "p0001"))
        e.controller.sendWithIntent(Intents.Id.SUMMARIZE)
        advanceUntilIdle()
        assertEquals(2, calls)
        val m = e.controller.state.value.messages.last()
        assertEquals("ok", m.text)
        assertEquals(AgentMessage.Phase.DONE, m.phase)
    }

    @Test
    fun `已有内容后不自动重试`() = runTest {
        var calls = 0
        val e = env(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))) {
            calls++
            listOf(ChatEvent.Delta("半截"), ChatEvent.Error(AgentError.Server("boom")))
        }
        e.controller.openDrawer(PageRef("nb1", "p0001"))
        e.controller.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        assertEquals(1, calls)
        val m = e.controller.state.value.messages.last()
        assertEquals(AgentMessage.Phase.FAILED, m.phase)
        assertEquals("半截", m.text)
    }

    @Test
    fun `取消保留半截置INTERRUPTED`() = runTest {
        val e = env(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))) { listOf(ChatEvent.Delta("前半"), ChatEvent.Error(AgentError.Cancelled)) }
        e.controller.openDrawer(PageRef("nb1", "p0001"))
        e.controller.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        val m = e.controller.state.value.messages.last()
        assertEquals(AgentMessage.Phase.INTERRUPTED, m.phase)
        assertEquals("前半", m.text)
    }

    @Test
    fun `插入批注幂等`() = runTest {
        val e = env(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))) { listOf(ChatEvent.Delta("讲解内容"), ChatEvent.Done(null)) }
        val c = e.controller
        c.openDrawer(PageRef("nb1", "p0001"))
        c.addSnip(Rect4(0f, 0f, 200f, 100f))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        val msgId = c.state.value.messages.last().id
        c.insertAnnotation(msgId)
        advanceUntilIdle()
        assertEquals(1, e.sink.inserted.size)
        assertNotNull(c.state.value.messages.last().insertedAnnotationId)

        c.insertAnnotation(msgId)
        advanceUntilIdle()
        assertEquals(1, e.sink.inserted.size)
    }

    @Test
    fun `LaTeX意图贴回走公式卡`() = runTest {
        val e = env(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))) { listOf(ChatEvent.Delta("\$\$x^2\$\$"), ChatEvent.Done(null)) }
        val c = e.controller
        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.LATEX)
        advanceUntilIdle()
        c.insertAnnotation(c.state.value.messages.last().id)
        advanceUntilIdle()
        assertTrue(e.sink.inserted.first().third)
    }

    @Test
    fun `整章chips多图截断`() = runTest {
        val e = env(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)), pageCount = 12) { listOf(ChatEvent.Delta("归纳"), ChatEvent.Done(null)) }
        val c = e.controller
        c.openDrawer(PageRef("nb1", "p0003"))
        c.toggleContext("chapter")
        c.sendWithIntent(Intents.Id.SUMMARIZE)
        advanceUntilIdle()
        val req = e.provider.requests.last()
        assertTrue("images=${req.images.size}", req.images.size in 2..10)
    }

    @Test
    fun `自定义模型需声明视觉`() = runTest {
        val e = env(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))) { listOf(ChatEvent.Done(null)) }
        val c = e.controller
        val pid = c.state.value.activeProfileId!!
        c.addModel(pid, "no-vision-yet", declaredVision = false)
        assertTrue(c.state.value.toast?.contains("已确认支持图片") == true)
        assertTrue(c.state.value.profiles.first().models.none { it.id == "no-vision-yet" })
        c.addModel(pid, "my-vlm", declaredVision = true)
        assertTrue(c.state.value.profiles.first().models.any { it.id == "my-vlm" })
    }

    @Test
    fun `ModelNotVision两次后本地标记`() = runTest {
        val e = env(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))) { listOf(ChatEvent.Error(AgentError.ModelNotVision("no image"))) }
        val c = e.controller
        c.openDrawer(PageRef("nb1", "p0001"))
        repeat(2) {
            c.sendWithIntent(Intents.Id.EXPLAIN)
            advanceUntilIdle()
        }
        val model = c.state.value.profiles.first().activeModel()
        assertNotNull(model)
        assertFalse(model!!.vision)
        assertTrue(c.state.value.setupRequired)
    }

    @Test
    fun `重新打开抽屉恢复会话`() = runTest {
        val e = env(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))) { listOf(ChatEvent.Delta("回答"), ChatEvent.Done(null)) }
        val c = e.controller
        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        c.closeDrawer()
        c.openDrawer(PageRef("nb1", "p0001"))
        assertEquals(2, c.state.value.messages.size)
        assertEquals("回答", c.state.value.messages.last().text)
    }
}
