package com.milknote.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/** Provider 抽象(§3.1;M2 唯一协议 = OpenAI 兼容 SSE) */
interface AgentProvider {
    val profileId: String
    fun chat(req: ChatRequest): Flow<ChatEvent>

    /** 拉取模型列表(尽力而为;OpenRouter 元数据自动标 vision) */
    suspend fun fetchModels(): Result<List<ModelInfo>>

    /** 连接测试:纯文本 1-token 请求,仅验鉴权可达(§3.3,无视觉探测) */
    suspend fun testConnection(): Result<Long>
}

/** HTTP 非 200 异常(fetchModels/testConnection 用) */
class ProviderHttpException(val code: Int, val body: String) : Exception("HTTP $code: ${body.take(200)}")

/**
 * OpenAI 兼容实现(§3.5):POST {base}/chat/completions,stream=true,SSE 手解析。
 * - imageStyle 自适应:图片请求 400 且文案指向图片格式 → 换另一种 style 重试一次,
 *   经 [onStyleAdopted] 回写 profile,后续不再抖动
 * - 帧间超时 = readTimeout(60s);连接 10s;脏帧(非 JSON)跳过不中断(§16)
 * - cancel():disconnect 在途连接 → Cancelled 事件
 */
class OpenAiCompatProvider(
    private val profile: ProviderProfile,
    private val keyResolver: () -> String?,
    private val onStyleAdopted: (ImageStyle) -> Unit = {},
) : AgentProvider {

    override val profileId: String get() = profile.id

    @Volatile var style: ImageStyle = profile.imageStyle
        private set

    private val active = AtomicReference<HttpURLConnection?>(null)

    /** 取消在途请求(调用方 stop()) */
    fun cancel() {
        active.get()?.disconnect()
    }

    override fun chat(req: ChatRequest): Flow<ChatEvent> = chatWithStyleAdaptation(req).flowOn(Dispatchers.IO)

    private fun chatWithStyleAdaptation(req: ChatRequest): Flow<ChatEvent> = flow {
        var styleRetry = false
        runOnce(req, style).collect { e ->
            if (!styleRetry && e is ChatEvent.Error && isImageFormatError(e.error) && req.images.isNotEmpty()) {
                styleRetry = true   // 丢弃该错误,换风格整轮重试(§3.5 只重试一次)
            } else {
                emit(e)
            }
        }
        if (styleRetry) {
            style = if (style == ImageStyle.DATA_URI) ImageStyle.RAW_BASE64 else ImageStyle.DATA_URI
            onStyleAdopted(style)
            runOnce(req.copy(imageStyle = style), style).collect { emit(it) }
        }
    }

    private fun isImageFormatError(err: AgentError): Boolean =
        err is AgentError.Other && err.detail.contains("图片格式")

    /** 单次请求的完整事件流 */
    private fun runOnce(req: ChatRequest, imageStyle: ImageStyle): Flow<ChatEvent> = flow {
        val conn = open(profile.baseUrl.trimEnd('/') + "/chat/completions", "POST", keyResolver(), sse = req.stream)
        active.set(conn)
        try {
            conn.outputStream.use { it.write(buildRequestBody(req, imageStyle).toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code != 200) {
                val errText = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }.getOrDefault("")
                emit(ChatEvent.Error(ErrorMapping.fromHttp(code, errText)))
                return@flow
            }

            val parser = SseParser()
            var gotAnyDelta = false
            var pendingUsage: ChatEvent.Usage? = null
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    for (payload in parser.feed(line + "\n")) {
                        for (ev in parseFrame(payload)) {
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
                }
                for (payload in parser.finish()) {
                    for (ev in parseFrame(payload)) emit(ev)
                }
            }
            // 流正常关闭但无 [DONE]:按完成处理(部分厂商不发)
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

    // ---- 模型列表(GET /models;OpenRouter 元数据 input_modalities 优待) ----

    override suspend fun fetchModels(): Result<List<ModelInfo>> = runCatching {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
        val conn = open(profile.baseUrl.trimEnd('/') + "/models", "GET", keyResolver(), sse = false)
        active.set(conn)
        try {
            val code = conn.responseCode
            val text = if (code == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            if (code != 200) throw ProviderHttpException(code, text)
            parseModelsList(text, openRouter = profile.presetId == PRESET_OPENROUTER)
        } finally {
            if (active.compareAndSet(conn, null)) runCatching { conn.disconnect() }
        }
        }
    }

    override suspend fun testConnection(): Result<Long> = runCatching {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val req = ChatRequest(
            model = profile.activeModelId.ifEmpty { profile.models.firstOrNull()?.id ?: "" },
            system = "ping",
            newUserText = "hi",
            stream = false,
            maxTokens = 1,
            imageStyle = style,
        )
        val conn = open(profile.baseUrl.trimEnd('/') + "/chat/completions", "POST", keyResolver(), sse = false)
        active.set(conn)
        try {
            conn.outputStream.use { it.write(buildRequestBody(req, style).toByteArray(Charsets.UTF_8)) }
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
    }

    // ---- JSON 组装/解析 ----

    internal fun buildRequestBody(req: ChatRequest, imageStyle: ImageStyle): String {
        val messages = buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", req.system) })
            req.history.forEach { m ->
                add(buildJsonObject { put("role", m.role); put("content", m.content) })
            }
            // 最后一条 user:图先文后(§3.5/§6.2)
            if (req.images.isEmpty()) {
                add(buildJsonObject { put("role", "user"); put("content", req.newUserText) })
            } else {
                val parts = buildJsonArray {
                    req.images.forEach { b64 ->
                        val url = when (imageStyle) {
                            ImageStyle.DATA_URI -> "data:image/png;base64,$b64"
                            ImageStyle.RAW_BASE64 -> b64
                        }
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject { put("url", url) })
                        })
                    }
                    if (req.newUserText.isNotBlank()) {
                        add(buildJsonObject { put("type", "text"); put("text", req.newUserText) })
                    }
                }
                add(buildJsonObject {
                    put("role", "user")
                    put("content", parts)
                })
            }
        }
        val obj = buildJsonObject {
            put("model", req.model)
            put("stream", req.stream)
            put("messages", messages)
            put("temperature", req.temperature)
            req.maxTokens?.let { put("max_tokens", it) }
        }
        return Json.encodeToString(JsonObject.serializer(), obj)
    }

    /** 单帧 data 载荷 → 事件列表;空列表 = 脏帧跳过(§16 容错) */
    internal fun parseFrame(payload: String): List<ChatEvent> {
        val p = payload.trim()
        if (p.isEmpty()) return emptyList()
        if (p == "[DONE]") return listOf(ChatEvent.Done("done"))
        val obj = runCatching { json.parseToJsonElement(p).jsonObject }.getOrNull() ?: return emptyList()

        val out = ArrayList<ChatEvent>(2)

        // 错误帧:{"error": {"message": ...}}
        obj["error"]?.let { err ->
            val msg = err.jsonObject["message"]?.jsonPrimitive?.content
                ?: err.jsonObject["code"]?.jsonPrimitive?.content
                ?: "unknown"
            val lowered = msg.lowercase()
            out += ChatEvent.Error(
                if (lowered.contains("image") && (lowered.contains("not support") || lowered.contains("unsupported")))
                    AgentError.ModelNotVision(msg)
                else AgentError.Other(msg),
            )
            return out
        }

        obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.let { choice ->
            val delta = choice["delta"]?.jsonObject ?: choice["message"]?.jsonObject
            val text = delta?.get("content")?.jsonPrimitive?.content
            if (!text.isNullOrEmpty()) out += ChatEvent.Delta(text)
        }
        obj["usage"]?.jsonObject?.let { u ->
            val pt = u["prompt_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val ct = u["completion_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            if (pt > 0 || ct > 0) out += ChatEvent.Usage(pt, ct)
        }
        return out
    }

    private fun open(url: String, method: String, key: String?, sse: Boolean): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = FRAME_TIMEOUT_MS      // 流式帧间超时(§3.5)
        conn.doOutput = method == "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", if (sse) "text/event-stream" else "application/json")
        key?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        return conn
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val FRAME_TIMEOUT_MS = 60_000
        const val PRESET_OPENROUTER = "openrouter"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** GET /models 解析(OpenRouter:architecture.input_modalities 含 image → vision) */
        fun parseModelsList(text: String, openRouter: Boolean): List<ModelInfo> {
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return emptyList()
            return data.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
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
