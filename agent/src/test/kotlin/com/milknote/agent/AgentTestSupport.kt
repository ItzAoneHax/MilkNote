package com.milknote.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flow
import java.io.File

/** 测试用页面渲染源:每页字节唯一(内容寻址去重/级联删除可区分) */
internal class TestSnipSource(private val pageCount: Int = 3) : SnipSource {
    override suspend fun captureSnip(page: PageRef, rect: Rect4): SnipResult {
        val bytes = "snip:${page.pageId}:${rect.l},${rect.t},${rect.r},${rect.b}".toByteArray()
        return SnipResult(
            png = bytes,
            jpeg = false,
            widthPx = rect.width.toInt(),
            heightPx = rect.height.toInt(),
            logicalName = ImageBudget.logicalNameSnip(rect.width.toInt(), rect.height.toInt()),
        )
    }

    override suspend fun capturePage(page: PageRef): SnipResult =
        SnipResult("page:${page.pageId}".toByteArray(), false, 100, 100, ImageBudget.logicalNamePage(page.pageId))

    override suspend fun pagesOf(notebookId: String): List<PageRef> =
        (1..pageCount).map { PageRef(notebookId, "p%04d".format(it)) }
}

internal class TestSink : AnnotationSink {
    val inserted = ArrayList<Triple<PageRef, Rect4?, Boolean>>()
    override suspend fun insertCard(page: PageRef, anchor: Rect4?, text: String, latex: Boolean, cardWidth: Float): String? {
        inserted.add(Triple(page, anchor, latex))
        return "card_${inserted.size}"
    }
}

internal class AgentHarness(
    val controller: AgentController,
    val store: AgentStore,
    val filesRoot: File,
    val notebooksRoot: File,
    val snipSource: TestSnipSource,
    val sink: TestSink,
)

/** 统一装配:临时目录 + 一个 glm profile + 测试端口 */
internal fun agentHarness(
    root: File,
    scope: CoroutineScope,
    pageCount: Int = 3,
    clock: () -> Long = System::currentTimeMillis,
    key: String? = "sk-test",
    providerFactory: (ProviderProfile, () -> String?, (CompatPatch) -> Unit) -> AgentProvider,
): AgentHarness {
    val filesRoot = File(root, "files").apply { mkdirs() }
    val notebooksRoot = File(root, "notebooks").apply { mkdirs() }
    val store = AgentStore(filesRoot, notebooksRoot)
    val snip = TestSnipSource(pageCount)
    val sink = TestSink()
    val controller = AgentController(
        scope = scope,
        store = store,
        snipSource = snip,
        annotationSink = sink,
        keyFor = { key },
        providerFactory = providerFactory,
        clockMs = clock,
    )
    controller.addProfile("glm", "", "", key ?: "")
    return AgentHarness(controller, store, filesRoot, notebooksRoot, snip, sink)
}

/** 按时间线推进假时钟的 Provider(确定性测 generationMs) */
internal class TimelineProvider(
    private val timeline: List<Pair<Long, ChatEvent>>,
    private val setClock: (Long) -> Unit,
) : AgentProvider {
    override val profileId = "timeline"
    val requests = ArrayList<ChatRequest>()

    override fun chat(req: ChatRequest) = flow {
        requests.add(req)
        for ((at, event) in timeline) {
            setClock(at)
            emit(event)
        }
    }

    override suspend fun fetchModels(): Result<List<ModelInfo>> = Result.success(emptyList())
    override suspend fun testConnection(): Result<Long> = Result.success(1L)
}

/** 聊天请求可挂起的 Provider(测「流式中拒绝切换会话」) */
internal class ChatGateProvider(private val gate: CompletableDeferred<Unit>) : AgentProvider {
    override val profileId = "chat-gate"
    val requests = ArrayList<ChatRequest>()

    override fun chat(req: ChatRequest) = flow {
        requests.add(req)
        gate.await()
        emit(ChatEvent.Delta("答"))
        emit(ChatEvent.Done(null))
    }

    override suspend fun fetchModels(): Result<List<ModelInfo>> = Result.success(emptyList())
    override suspend fun testConnection(): Result<Long> = Result.success(1L)
}

/** 摘要请求可挂起的 Provider(测「摘要不覆盖并发新增消息」) */
internal class GatedProvider(private val gate: CompletableDeferred<Unit>) : AgentProvider {
    override val profileId = "gated"
    val requests = ArrayList<ChatRequest>()

    override fun chat(req: ChatRequest) = flow {
        requests.add(req)
        if (req.system == SUMMARY_SYSTEM_PROMPT) {
            gate.await()
            emit(ChatEvent.Delta("摘要文本"))
            emit(ChatEvent.Done(null))
        } else {
            emit(ChatEvent.Delta("答"))
            emit(ChatEvent.Done(null))
        }
    }

    override suspend fun fetchModels(): Result<List<ModelInfo>> = Result.success(emptyList())
    override suspend fun testConnection(): Result<Long> = Result.success(1L)
}
