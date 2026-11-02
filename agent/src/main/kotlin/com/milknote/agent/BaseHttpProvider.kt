package com.milknote.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/** HTTP 非 200 异常(fetchModels/testConnection 用) */
class ProviderHttpException(val code: Int, val body: String) : Exception("HTTP $code: ${body.take(200)}")

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

    protected fun runOnce(req: ChatRequest): Flow<ChatEvent> = flow {
        val conn = open(endpoint("chat", sse = req.stream), "POST", req.stream)
        active.set(conn)
        try {
            conn.outputStream.use { it.write(buildBody(req).toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code != 200) {
                val errText = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }.getOrDefault("")
                emit(ChatEvent.Error(mapHttpError(code, errText)))
                return@flow
            }

            val parser = SseParser()
            var gotAnyDelta = false
            var pendingUsage: ChatEvent.Usage? = null
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val events = ArrayList<ChatEvent>(2)
                    for (payload in parser.feed(line + "\n")) events.addAll(parseFrame(payload))
                    for (ev in events) {
                        when (ev) {
                            is ChatEvent.Delta -> { if (ev.text.isNotEmpty()) gotAnyDelta = true; emit(ev) }
                            is ChatEvent.Usage -> pendingUsage = ev
                            is ChatEvent.Done -> {
                                emit(ev)
                                pendingUsage?.let { emit(it) }
                                return@flow
                            }
                            is ChatEvent.Error -> {
                                // 已有内容后的失败按 StreamBroken 走(§11:不重复计费)
                                emit(if (gotAnyDelta) ChatEvent.Error(AgentError.StreamBroken) else ev)
                                return@flow
                            }
                        }
                    }
                }
                for (payload in parser.finish()) {
                    for (ev in parseFrame(payload)) emit(ev)
                }
            }
            // 流正常关闭但无终止帧:按完成处理(防御)
            emit(ChatEvent.Done(null))
        } catch (t: Throwable) {
            val cancelled = t is java.io.IOException && wasCancelled(conn)
            emit(ChatEvent.Error(if (cancelled) AgentError.Cancelled else ErrorMapping.fromException(t)))
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

    protected fun open(url: String, method: String, sse: Boolean): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = frameTimeoutMs   // 流式帧间超时(§3.5)
        conn.doOutput = method == "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", if (sse) "text/event-stream" else "application/json")
        applyHeaders(conn, sse)
        return conn
    }

    // ---- 模型列表(GET /models;尽力而为;OpenRouter 元数据优待) ----

    override suspend fun fetchModels(): Result<List<ModelInfo>> = runCatching {
        val conn = open(endpoint("models", sse = false), "GET", sse = false)
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
        val conn = open(endpoint("chat", sse = false), "POST", sse = false)
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
        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** GET /models 解析(OpenRouter:architecture.input_modalities 含 image → vision) */
        fun parseModelsList(text: String, openRouter: Boolean): List<ModelInfo> {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return emptyList()
            return data.mapNotNull { el ->
                val o = el.jsonObject
                val idEl = o["id"]
                if (idEl == null || idEl is kotlinx.serialization.json.JsonNull) return@mapNotNull null
                val id = idEl.jsonPrimitive.content
                val vision = if (openRouter) {
                    o["architecture"]?.jsonObject?.get("input_modalities")?.jsonArray
                        ?.any { it.jsonPrimitive.content == "image" } == true
                } else {
                    false   // 其余端点元数据不判视觉:预设/手填声明为准(§3.4)
                }
                ModelInfo(
                    id = id,
                    displayName = id,
                    vision = vision,
                    visionSource = if (openRouter && vision) VisionSource.METADATA.name else VisionSource.DECLARED.name,
                    maxImages = 8,
                )
            }
        }
    }
}

/** 默认工厂:按 profile.protocol 分发(OPENAI_COMPATIBLE / ANTHROPIC) */
object ProviderFactory {
    fun create(
        profile: ProviderProfile,
        keyResolver: () -> String?,
        onStyleAdopted: (ImageStyle) -> Unit = {},
    ): AgentProvider = if (profile.protocol == Protocol.ANTHROPIC) {
        AnthropicProvider(profile, keyResolver)
    } else {
        OpenAiCompatProvider(profile, keyResolver, onStyleAdopted)
    }
}
