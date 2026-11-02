package com.milknote.agent

import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Provider 抽象(§3.1;协议按 profile 选择:A2 起 OpenAI 兼容 + Anthropic 原生并存) */
interface AgentProvider {
    val profileId: String
    fun chat(req: ChatRequest): kotlinx.coroutines.flow.Flow<ChatEvent>

    /** 拉取模型列表(尽力而为;OpenRouter 元数据自动标 vision) */
    suspend fun fetchModels(): Result<List<ModelInfo>>

    /** 连接测试:纯文本 1-token 请求,仅验鉴权可达(§3.3,无视觉探测) */
    suspend fun testConnection(): Result<Long>

    /** 取消在途请求(默认空实现:Fake/测试 Provider 无需处理) */
    fun cancel() {}
}

/**
 * OpenAI 兼容实现(§3.5):POST {base}/chat/completions,stream=true,SSE 手解析。
 * - imageStyle 自适应:图片请求 400 且文案指向图片格式 → 换另一种 style 重试一次,
 *   经 [onStyleAdopted] 回写 profile,后续不再抖动
 * - 脏帧(非 JSON)跳过不中断(§16)
 */
class OpenAiCompatProvider(
    profile: ProviderProfile,
    keyResolver: () -> String?,
    private val onStyleAdopted: (ImageStyle) -> Unit = {},
) : BaseHttpProvider(profile, keyResolver) {

    @Volatile var style: ImageStyle = profile.imageStyle
        private set

    internal override fun endpoint(path: String, sse: Boolean): String {
        val base = profile.baseUrl.trimEnd('/')
        return when (path) {
            "chat" -> "$base/chat/completions"
            else -> "$base/models"
        }
    }

    internal override fun applyHeaders(conn: java.net.HttpURLConnection, sse: Boolean) {
        keyResolver()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
    }

    internal override fun buildBody(req: ChatRequest): String = buildRequestBody(req, style)

    // ---- imageStyle 自适应(§3.5:只重试一次,回写 profile) ----

    override fun chatFlow(req: ChatRequest) = flow {
        var styleRetry = false
        runOnce(req).collect { e ->
            if (!styleRetry && e is ChatEvent.Error && isImageFormatError(e.error) && req.images.isNotEmpty()) {
                styleRetry = true   // 丢弃该错误,换风格整轮重试(§3.5 只重试一次)
            } else {
                emit(e)
            }
        }
        if (styleRetry) {
            style = if (style == ImageStyle.DATA_URI) ImageStyle.RAW_BASE64 else ImageStyle.DATA_URI
            onStyleAdopted(style)
            runOnce(req.copy(imageStyle = style)).collect { emit(it) }
        }
    }

    private fun isImageFormatError(err: AgentError): Boolean =
        err is AgentError.Other && err.detail.contains("图片格式")

    // ---- JSON 组装/解析(§3.5) ----

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
                    req.images.forEachIndexed { i, b64 ->
                        val url = when (imageStyle) {
                            ImageStyle.DATA_URI -> "data:${mediaTypeOf(req, i)};base64,$b64"
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
    internal override fun parseFrame(payload: String): List<ChatEvent> {
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
            // JSON null 帧(role/空增量,MiMo 等大量发送)不算文本 —— JsonNull.content
            // 会返回字面量 "null",必须显式排除(真机实证:回复前缀 32 个 null)
            val contentEl = delta?.get("content")
            if (contentEl != null && contentEl !is JsonNull) {
                val text = contentEl.jsonPrimitive.content
                if (text.isNotEmpty()) out += ChatEvent.Delta(text)
            }
        }
        obj["usage"]?.jsonObject?.let { u ->
            val pt = u["prompt_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val ct = u["completion_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            if (pt > 0 || ct > 0) out += ChatEvent.Usage(pt, ct)
        }
        return out
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val FRAME_TIMEOUT_MS = 60_000
        const val PRESET_OPENROUTER = "openrouter"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
