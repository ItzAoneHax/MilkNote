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
    private val onPatch: (CompatPatch) -> Unit = {},
) : BaseHttpProvider(profile, keyResolver) {

    @Volatile var style: ImageStyle = profile.imageStyle
        private set

    /** 流式是否带 stream_options.include_usage(端点 400 拒识时自适应关闭并回写) */
    @Volatile private var sendStreamOptions: Boolean = profile.streamOptionsEnabled

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

    // ---- 兼容性自适配(§3.5:stream_options / imageStyle 各至多一次,合并为一轮重试并回写) ----

    override fun chatFlow(req: ChatRequest) = flow {
        var dropStyle = false
        var dropStreamOptions = false
        runOnce(req).collect { e ->
            val err = (e as? ChatEvent.Error)?.error
            when {
                !dropStyle && err != null && isImageFormatError(err) && req.images.isNotEmpty() ->
                    dropStyle = true
                !dropStreamOptions && err != null && isStreamOptionsError(err) && req.stream ->
                    dropStreamOptions = true
                else -> emit(e)
            }
        }
        if (dropStyle || dropStreamOptions) {
            if (dropStyle) {
                style = if (style == ImageStyle.DATA_URI) ImageStyle.RAW_BASE64 else ImageStyle.DATA_URI
                onPatch(CompatPatch(imageStyle = style))
            }
            if (dropStreamOptions) {
                sendStreamOptions = false
                onPatch(CompatPatch(streamOptions = false))
            }
            runOnce(req.copy(imageStyle = style)).collect { emit(it) }
        }
    }

    private fun isImageFormatError(err: AgentError): Boolean =
        err is AgentError.Other && err.detail.contains("图片格式")

    private fun isStreamOptionsError(err: AgentError): Boolean =
        err is AgentError.Other && err.detail.contains("stream_options")

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
            // max_tokens 不发送:OpenAI 推理系(gpt-6.x/o 系)只认 max_completion_tokens,
            // 发错字段直接 400;不限制则走厂商默认(讲解场景回答量级远低于上限)
            if (req.stream && sendStreamOptions) {
                // 精确 usage(§7.3);仅流式路径发 —— 非流式发会被部分网关 400(opencode 同款)
                put("stream_options", buildJsonObject { put("include_usage", true) })
            }
            // 思考强度(§3.8):按厂商 wire format 翻译;AUTO 不发
            ThinkingDispatch.applyOpenAi(
                this, req.reasoningEffort,
                ThinkingDispatch.formatOf(profile.presetId),
                ThinkingDispatch.parseLevels(profile.activeModel()?.thinkingLevels ?: emptyList()),
            )
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
            // finish_reason:权威完成信号(pi-ai hasFinishReason);非终止事件,后续帧继续吃
            val finEl = choice["finish_reason"]
            if (finEl != null && finEl !is JsonNull) {
                finEl.jsonPrimitive.content.ifEmpty { null }?.let { out += ChatEvent.Finish(it) }
            }
            val delta = choice["delta"]?.jsonObject ?: choice["message"]?.jsonObject
            // JSON null 帧(role/空增量,MiMo 等大量发送)不算文本 —— JsonNull.content
            // 会返回字面量 "null",必须显式排除(真机实证:回复前缀 32 个 null)
            val contentEl = delta?.get("content")
            if (contentEl != null && contentEl !is JsonNull) {
                val text = contentEl.jsonPrimitive.content
                if (text.isNotEmpty()) out += ChatEvent.Delta(text)
            }
            // 思考过程(deepseek-harness 同款字段序:首个非空,防中转站双字段重复)
            val reasoning = listOf("reasoning_content", "reasoning", "reasoning_text")
                .firstNotNullOfOrNull { f ->
                    val el = delta?.get(f) ?: return@firstNotNullOfOrNull null
                    if (el is JsonNull) return@firstNotNullOfOrNull null
                    el.jsonPrimitive.content.ifEmpty { null }
                }
            if (reasoning != null) out += ChatEvent.Thinking(reasoning)
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
        const val FRAME_TIMEOUT_MS = 180_000   // 帧间空闲:整章长 prefill 无心跳可静默 >60s(harness 用 300s 看门狗)
        const val PRESET_OPENROUTER = "openrouter"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
