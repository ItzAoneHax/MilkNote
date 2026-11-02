package com.milknote.agent

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Anthropic Messages 原生协议(AGENT-BACKEND §3.7,A2 增补):
 * 部分 Claude 系端点/中转站仅有 Anthropic 格式 —— POST {base}/v1/messages,
 * `x-api-key` + `anthropic-version` 头,图片走 content block `source.base64`。
 * baseUrl 兼容三种写法:`…/v1`、裸域、`…/anthropic`(中转前缀),末尾自动补 /v1/messages。
 */
class AnthropicProvider(
    profile: ProviderProfile,
    keyResolver: () -> String?,
) : BaseHttpProvider(profile, keyResolver) {

    /** Anthropic 强制 max_tokens;讲解类回答 ≤2k tok,4096 保守且各家全兼容 */
    private val defaultMaxTokens: Int get() = DEFAULT_MAX_TOKENS

    private var stashedInputTokens = 0   // message_start 带 input,message_delta 带 output,拼合

    internal override fun endpoint(path: String, sse: Boolean): String {
        val base = profile.baseUrl.trimEnd('/')
        val prefix = if (base.endsWith("/v1")) base else "$base/v1"
        return when (path) {
            "chat" -> "$prefix/messages"
            else -> "$prefix/models"
        }
    }

    internal override fun applyHeaders(conn: java.net.HttpURLConnection, sse: Boolean) {
        keyResolver()?.let { key ->
            conn.setRequestProperty("x-api-key", key)
            // 部分中转站只认 Bearer;Anthropic 官方两者皆收,并存最稳
            conn.setRequestProperty("Authorization", "Bearer $key")
        }
        conn.setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
    }

    internal override fun buildBody(req: ChatRequest): String {
        val messages = buildJsonArray {
            req.history.forEach { m -> add(buildJsonObject { put("role", m.role); put("content", m.content) }) }
            // 最后一条 user:图先文后(与 OpenAI 路径同语义序)
            if (req.images.isEmpty()) {
                add(buildJsonObject { put("role", "user"); put("content", req.newUserText) })
            } else {
                val blocks = buildJsonArray {
                    req.images.forEachIndexed { i, b64 ->
                        add(buildJsonObject {
                            put("type", "image")
                            put("source", buildJsonObject {
                                put("type", "base64")
                                put("media_type", mediaTypeOf(req, i))
                                put("data", b64)
                            })
                        })
                    }
                    if (req.newUserText.isNotBlank()) {
                        add(buildJsonObject { put("type", "text"); put("text", req.newUserText) })
                    }
                }
                add(buildJsonObject {
                    put("role", "user")
                    put("content", blocks)
                })
            }
        }
        val obj = buildJsonObject {
            put("model", req.model)
            put("stream", req.stream)
            put("max_tokens", req.maxTokens ?: defaultMaxTokens)   // Anthropic 必填
            put("system", req.system)
            put("messages", messages)
            put("temperature", req.temperature)
        }
        return BaseHttpProvider.json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(), obj,
        )
    }

    /** Anthropic SSE:每个 data 载荷自带 type 字段(event: 行被 SseParser 丢弃,天然兼容) */
    internal override fun parseFrame(payload: String): List<ChatEvent> {
        val p = payload.trim()
        if (p.isEmpty()) return emptyList()
        val obj = runCatching { BaseHttpProvider.json.parseToJsonElement(p).jsonObject }.getOrNull() ?: return emptyList()
        return when (obj["type"]?.jsonPrimitive?.content) {
            "content_block_delta" -> {
                // JsonNull 守卫(与 OpenAI 路径同源问题):null 值不得变字面量 "null"
                fun txt(field: String): String? {
                    val el = obj["delta"]?.jsonObject?.get(field) ?: return null
                    if (el is kotlinx.serialization.json.JsonNull) return null
                    return el.jsonPrimitive.content
                }
                val text = txt("text") ?: txt("partial_json")  // thinking/tool 帧按文本容错
                if (text.isNullOrEmpty()) emptyList() else listOf(ChatEvent.Delta(text))
            }
            "message_start" -> {
                stashedInputTokens = obj["message"]?.jsonObject?.get("usage")?.jsonObject
                    ?.get("input_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                emptyList()
            }
            "message_delta" -> {
                val out = obj["usage"]?.jsonObject?.get("output_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                if (stashedInputTokens > 0 || out > 0) listOf(ChatEvent.Usage(stashedInputTokens, out)) else emptyList()
            }
            "message_stop" -> listOf(ChatEvent.Done("end_turn"))
            "error" -> {
                val err = obj["error"]?.jsonObject
                val type = err?.get("type")?.jsonPrimitive?.content ?: "api_error"
                val msg = err?.get("message")?.jsonPrimitive?.content ?: type
                listOf(
                    ChatEvent.Error(
                        when {
                            type.contains("overloaded") -> AgentError.Server("服务过载,请稍后重试")
                            type.contains("rate_limit") -> AgentError.RateLimit(msg)
                            type.contains("authentication") || type.contains("permission") -> AgentError.Auth(msg)
                            msg.lowercase().contains("image") && msg.lowercase().contains("not") ->
                                AgentError.ModelNotVision(msg)
                            else -> AgentError.Other(msg)
                        },
                    ),
                )
            }
            else -> emptyList()   // ping / content_block_start / stop / message_start 前置帧
        }
    }

    internal override fun mapHttpError(code: Int, body: String): AgentError = when (code) {
        529 -> AgentError.Server("服务过载(HTTP 529),请稍后重试")   // Anthropic 独有过载码
        else -> ErrorMapping.fromHttp(code, body)
    }

    companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val DEFAULT_MAX_TOKENS = 4096
    }
}
