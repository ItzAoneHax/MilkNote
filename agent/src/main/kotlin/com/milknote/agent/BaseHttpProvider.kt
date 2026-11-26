package com.milknote.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/** HTTP 非 200 异常(fetchModels/testConnection 用);保留完整响应体供诊断 */
class ProviderHttpException(val code: Int, val body: String) :
    Exception("HTTP $code: ${body.trim().take(MAX_ERROR_BODY_CHARS)}") {
    companion object {
        const val MAX_ERROR_BODY_CHARS = 8000
    }
}

/**
 * HTTP 流式 Provider 公共管道(§3.5/§3.7):
 * 连接生命周期 / SSE 循环 / 帧解析钩子 / 错误映射钩子 / 取消 / models+测试连接模板。
 * 子类只负责:端点、请求头、请求体、帧→事件、错误文案差异。
 */
abstract class BaseHttpProvider(
    protected val profile: ProviderProfile,
    protected val keyResolver: () -> String?,
) : AgentProvider {

    override val profileId: String get() = profile.id

    protected val active = AtomicReference<HttpURLConnection?>(null)

    /** 单请求超时/连接超时(§3.5) */
    protected open val connectTimeoutMs: Int get() = OpenAiCompatProvider.CONNECT_TIMEOUT_MS
    protected open val frameTimeoutMs: Int get() = OpenAiCompatProvider.FRAME_TIMEOUT_MS

    // ---- 子类钩子 ----

    /** 完整端点 URL(POST 对话 / GET 模型列表,按 sse 区分 Accept) */
    internal abstract fun endpoint(path: String, sse: Boolean): String

    /** 协议头(Authorization / x-api-key / anthropic-version …) */
    internal abstract fun applyHeaders(conn: HttpURLConnection, sse: Boolean)

    /** 请求体(已含 stream/max_tokens 语义) */
    internal abstract fun buildBody(req: ChatRequest): String

    /** 单帧 data 载荷 → 事件列表(空 = 忽略帧;子类可用内部字段累积 usage) */
    internal abstract fun parseFrame(payload: String): List<ChatEvent>

    /** HTTP 错误映射(Anthropic 529 等差异) */
    internal open fun mapHttpError(code: Int, body: String): AgentError = ErrorMapping.fromHttp(code, body)

    /** 图片 MIME(与 imageMediaTypes 对应,缺省 png) */
    protected fun mediaTypeOf(req: ChatRequest, index: Int): String =
        req.imageMediaTypes.getOrNull(index) ?: "image/png"

    // ---- 取消 ----

    override fun cancel() {
        active.get()?.disconnect()
    }

    // ---- 对话 ----

    override fun chat(req: ChatRequest): Flow<ChatEvent> = chatFlow(req).flowOn(Dispatchers.IO)

    /** 子类可包一层协议特有策略(OpenAI 的 imageStyle 自适应) */
    protected open fun chatFlow(req: ChatRequest): Flow<ChatEvent> = flow {
        runOnce(req).collect { emit(it) }
    }

    /**
     * 单轮请求。完成判定三分(§11,真机实证):
     * 1. `Done`(=[DONE]/message_stop)是终止符,顺手补发 pendingUsage;
     * 2. 有合法 finish_reason 即视为完成 —— 服务端发完就关流(MiMo)或被 RST 都不得误报;
     * 3. 既无 finish_reason 也无 Done 但已有内容 → StreamBroken(不伪完成)。
     */
    protected fun runOnce(req: ChatRequest): Flow<ChatEvent> = flow {
        val conn = open(endpoint("chat", sse = req.stream), "POST", req.stream, req.sessionId)
        active.set(conn)
        var pendingUsage: ChatEvent.Usage? = null
        var finishReason: String? = null
        var finishError: AgentError? = null
        try {
            conn.outputStream.use { it.write(buildBody(req).toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code != 200) {
                val errText = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }.getOrDefault("")
                val retryAfterMs = retryAfterMsOf(conn)   // 429 退避指示(retry-after-ms / Retry-After)
                val mapped = mapHttpError(code, errText)
                emit(
                    ChatEvent.Error(
                        if (mapped is AgentError.RateLimit && retryAfterMs != null) mapped.copy(retryAfterMs = retryAfterMs)
                        else mapped,
                    ),
                )
                return@flow
            }

            val parser = SseParser()
            var gotAnyDelta = false
            fun noteFinish(reason: String) {
                finishReason = reason
                // mapStopReason(pi-ai 同款):content_filter/network_error/未知 → 错误
                finishError = if (reason.lowercase() in OK_FINISH_REASONS) null
                else AgentError.Other("Provider finish_reason: $reason")
            }
            // 脏帧不得打断整条流:类型不符/半截 JSON 一律按「无事件」处理
            fun framesOf(payload: String): List<ChatEvent> =
                runCatching { parseFrame(payload) }.getOrDefault(emptyList())

            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val events = ArrayList<ChatEvent>(2)
                    for (payload in parser.feed(line + "\n")) events.addAll(framesOf(payload))
                    for (ev in events) {
                        when (ev) {
                            is ChatEvent.Finish -> noteFinish(ev.reason)   // 只记,继续吃后续帧(usage-only 帧常在 finish 后)
                            is ChatEvent.Delta -> { if (ev.text.isNotEmpty()) gotAnyDelta = true; emit(ev) }
                            is ChatEvent.Thinking -> { if (ev.text.isNotEmpty()) gotAnyDelta = true; emit(ev) }   // 思考也是已计费内容(§11 不重复计费)
                            is ChatEvent.Usage -> pendingUsage = ev
                            is ChatEvent.Done -> {
                                // [DONE]/message_stop = 流终止符;宽容差异(较 pi-ai 严判略松):
                                // 它本身也视作完成,兼容只发 [DONE] 不发 finish_reason 的懒中转
                                emit(ev)
                                pendingUsage?.let { emit(it) }
                                return@flow
                            }
                            is ChatEvent.Error -> {
                                // 真实错误原样上抛(如 content_filter / 鉴权/参数错):
                                // 是否重试由控制器按「是否已有内容」判定,这里不做信息掩埋
                                emit(ev)
                                return@flow
                            }
                        }
                    }
                }
                for (payload in parser.finish()) {
                    for (ev in framesOf(payload)) {
                        when (ev) {
                            is ChatEvent.Finish -> noteFinish(ev.reason)
                            is ChatEvent.Done -> { emit(ev); pendingUsage?.let { emit(it) }; return@flow }
                            is ChatEvent.Usage -> pendingUsage = ev
                            is ChatEvent.Error -> { emit(ev); return@flow }
                            else -> emit(ev)
                        }
                    }
                }
            }
            // 流关闭:完成与否只看 finish_reason(pi-ai 语义,真机 MiMo 实证:无 [DONE] 但有 finish_reason)
            val fin = finishError
            when {
                fin != null -> emit(ChatEvent.Error(fin))                       // content_filter 等:文本保留,标失败
                finishReason != null -> {
                    emit(ChatEvent.Done(finishReason))                          // 合法收尾
                    pendingUsage?.let { emit(it) }
                }
                gotAnyDelta -> emit(ChatEvent.Error(AgentError.StreamBroken))   // 真·中途断流
                else -> emit(ChatEvent.Error(AgentError.EmptyResponse))         // 空响应可重试,不落空气泡
            }
        } catch (t: Throwable) {
            val cancelled = t is java.io.IOException && wasCancelled(conn)
            when {
                cancelled -> emit(ChatEvent.Error(AgentError.Cancelled))
                // 服务端已发合法 finish_reason 才断(关流/RST/读超时):属于正常收尾,不是断流
                finishError != null -> emit(ChatEvent.Error(finishError!!))
                finishReason != null -> {
                    emit(ChatEvent.Done(finishReason))
                    pendingUsage?.let { emit(it) }
                }
                else -> emit(ChatEvent.Error(ErrorMapping.fromException(t)))
            }
        } finally {
            if (active.compareAndSet(conn, null)) runCatching { conn.disconnect() }
        }
    }

    private fun wasCancelled(conn: HttpURLConnection): Boolean = try {
        conn.responseCode   // disconnect 后再访问会抛 → 视为被取消
        false
    } catch (_: Throwable) {
        true
    }

    /** 429 退避指示:retry-after-ms(ms)优先,其次 Retry-After(秒/HTTP-date 不解析) */
    private fun retryAfterMsOf(conn: HttpURLConnection): Long? {
        conn.getHeaderField("retry-after-ms")?.toLongOrNull()?.let { return it }
        conn.getHeaderField("Retry-After")?.toLongOrNull()?.let { return it * 1000 }
        return null
    }

    /**
     * 建连。两个不显眼但必要的头:
     * - User-Agent:系统栈默认 `Dalvik/…`,OpenCode Go 与多家网关直接 403;固定产品 UA。
     * - x-opencode-session:OpenCode Go 用它归集会话(缺省按 profile 派生稳定值,同一家不互相踩)。
     */
    protected fun open(url: String, method: String, sse: Boolean, sessionId: String? = null): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        // 中转站 302 不静默跟随(deepseek-harness 同款):跳走会以 GET 丢请求体,宁可显式报错
        conn.instanceFollowRedirects = false
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = frameTimeoutMs   // 流式帧间超时(§3.5;整章长 prefill 可静默 >60s)
        conn.doOutput = method == "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", if (sse) "text/event-stream" else "application/json")
        conn.setRequestProperty("User-Agent", USER_AGENT)
        if (profile.presetId == ProviderRegistry.PRESET_OPENCODE_GO || EndpointResolver.isOpenCodeBaseUrl(profile.baseUrl)) {
            conn.setRequestProperty(
                "x-opencode-session",
                sessionId?.takeIf { it.isNotBlank() } ?: "milknote-${profile.id}",
            )
        }
        applyHeaders(conn, sse)
        return conn
    }

    // ---- 模型列表(GET /models;尽力而为;OpenRouter 元数据优待) ----

    override suspend fun fetchModels(): Result<List<ModelInfo>> = runCatching {
        val conn = open(endpoint("models", sse = false), "GET", sse = false)
        conn.readTimeout = 30_000   // 列表拉取不吃流式长超时
        active.set(conn)
        try {
            val code = conn.responseCode
            val text = if (code == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            if (code != 200) throw ProviderHttpException(code, text)
            parseModelsList(text, openRouter = profile.presetId == OpenAiCompatProvider.PRESET_OPENROUTER)
        } finally {
            if (active.compareAndSet(conn, null)) runCatching { conn.disconnect() }
        }
    }

    /** 连接测试:纯文本 1-token 请求,仅验鉴权可达(§3.3,无视觉探测) */
    override suspend fun testConnection(): Result<Long> = runCatching {
        val t0 = System.currentTimeMillis()
        val req = ChatRequest(
            model = profile.activeModelId.ifEmpty { profile.models.firstOrNull()?.id ?: "" },
            system = "ping",
            newUserText = "hi",
            stream = false,
            maxTokens = 1,
        )
        val conn = open(endpoint("chat", sse = false), "POST", sse = false, sessionId = "milknote-test-${profile.id}")
        conn.readTimeout = 20_000   // 鉴权探测不吃流式长超时
        active.set(conn)
        try {
            conn.outputStream.use { it.write(buildBody(req).toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code != 200) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "" }.getOrDefault("")
                throw ProviderHttpException(code, err)
            }
            conn.inputStream.close()
        } finally {
            if (active.compareAndSet(conn, null)) runCatching { conn.disconnect() }
        }
        System.currentTimeMillis() - t0
    }

    companion object {
        const val USER_AGENT = "MilkNote/1.0"

        /** 合法完成信号(小写);其余(content_filter/network_error…)按错误处理 */
        val OK_FINISH_REASONS = setOf(
            "stop", "end", "", "length", "tool_calls", "function_call",
            "end_turn", "stop_sequence", "tool_use", "max_tokens", "completed",
        )

        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * GET /models 解析。各家元数据字段差异极大,一律取到就用、取不到就退化:
         * - `type` 存在且非 `chat` 的整行丢弃(OpenCode Go 的 decision/embedding 不能对话)
         * - `displayName` 取 `name`
         * - vision:OpenRouter `architecture.input_modalities` 或 `modalities.input` 含 image(/image/xxx)
         * - 容量:`limit.context` / `limit.output`,退 `context_length` / `top_provider.max_completion_tokens`
         * - 思考档:`capabilities.reasoning.effort` 数组 + `toggle`
         * - 协议:`recommended_api` 决定该模型走哪套 wire format
         */
        fun parseModelsList(text: String, openRouter: Boolean): List<ModelInfo> {
            val root = runCatching { json.parseToJsonElement(text) }.getOrNull().asObjOrNull() ?: return emptyList()
            val data = root.arrOrNull("data") ?: return emptyList()
            return data.mapNotNull { el ->
                val o = el.asObjOrNull() ?: return@mapNotNull null
                val id = o.strOrNull("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                // 非对话模型(embedding/decision/rerank…)不进选择器
                val type = o.strOrNull("type")
                if (type != null && type != "chat") return@mapNotNull null

                val visionOpenRouter = openRouter &&
                    o.objOrNull("architecture")?.arrOrNull("input_modalities")
                        ?.any { it.strOrNull() == "image" } == true
                val visionModalities = o.objOrNull("modalities")?.arrOrNull("input")
                    ?.any { m -> m.strOrNull()?.let { it == "image" || it.startsWith("image/") } == true } == true
                val vision = visionOpenRouter || visionModalities

                val ctxTokens = o.objOrNull("limit")?.intOrNull("context") ?: o.intOrNull("context_length")
                val outTokens = o.objOrNull("limit")?.intOrNull("output")
                    ?: o.objOrNull("top_provider")?.intOrNull("max_completion_tokens")

                ModelInfo(
                    id = id,
                    displayName = o.strOrNull("name")?.takeIf { it.isNotBlank() } ?: id,
                    vision = vision,
                    visionSource = if (vision) VisionSource.METADATA.name else VisionSource.DECLARED.name,
                    maxImages = 8,
                    thinkingLevels = parseThinkingLevels(o),
                    contextWindowK = (ctxTokens ?: 0) / 1000,
                    maxTokensK = (outTokens ?: 0) / 1000,
                    protocol = protocolOfRecommended(o.strOrNull("recommended_api")),
                )
            }
        }

        /** capabilities.reasoning → 档位序(递进;toggle 可关则补 OFF 在列) */
        private fun parseThinkingLevels(o: JsonObject): List<String> {
            val caps = o.objOrNull("capabilities")?.objOrNull("reasoning") ?: return emptyList()
            val levels = LinkedHashSet<ReasoningEffort>()
            caps.arrOrNull("effort")?.forEach { el ->
                when (el.strOrNull()?.lowercase()) {
                    "none" -> levels.add(ReasoningEffort.OFF)
                    "minimal" -> levels.add(ReasoningEffort.MINIMAL)
                    "low" -> levels.add(ReasoningEffort.LOW)
                    "medium" -> levels.add(ReasoningEffort.MEDIUM)
                    "high" -> levels.add(ReasoningEffort.HIGH)
                    "xhigh" -> levels.add(ReasoningEffort.XHIGH)
                    "max" -> levels.add(ReasoningEffort.MAX)
                }
            }
            val all = if (caps.boolOrNull("toggle") == true && ReasoningEffort.OFF !in levels) {
                listOf(ReasoningEffort.OFF) + levels
            } else {
                levels.toList()
            }
            return all.map { it.name }
        }

        /**
         * recommended_api → 协议:OpenCode Go 会把 chat 模型在各协议间翻译,
         * interactions/generate-content 一律走 ChatCompletions,只有 messages/responses 需要原生格式。
         */
        private fun protocolOfRecommended(raw: String?): Protocol? = when (raw?.lowercase()) {
            "chat-completions" -> Protocol.OPENAI_COMPATIBLE
            "messages" -> Protocol.ANTHROPIC
            "responses" -> Protocol.OPENAI_RESPONSES
            "interactions", "generate-content" -> Protocol.OPENAI_COMPATIBLE
            else -> null
        }
    }
}

/** 默认工厂:按 profile.protocol 分发(OPENAI_COMPATIBLE / ANTHROPIC / OPENAI_RESPONSES) */
object ProviderFactory {
    fun create(
        profile: ProviderProfile,
        keyResolver: () -> String?,
        onPatch: (CompatPatch) -> Unit = {},
    ): AgentProvider = when (profile.protocol) {
        Protocol.ANTHROPIC -> AnthropicProvider(profile, keyResolver)
        Protocol.OPENAI_RESPONSES -> OpenAiResponsesProvider(profile, keyResolver)
        Protocol.OPENAI_COMPATIBLE -> OpenAiCompatProvider(profile, keyResolver, onPatch)
    }
}
