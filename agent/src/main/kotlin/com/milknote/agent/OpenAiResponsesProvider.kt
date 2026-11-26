package com.milknote.agent

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * OpenAI Responses API(AGENT-BACKEND §3.6,B 增补):
 * POST {base}/responses,`instructions` + `input[]` 取代 system/messages,
 * 流式为一串具名事件(`response.output_text.delta` / `response.completed` …)。
 * 少数模型(OpenCode Go 的 gpt-6-luna / grok-4.7)只认这套 wire format。
 *
 * 与 ChatCompletions 的三处关键差异:
 * 1. 输出上限字段是 `max_output_tokens` 且最小 16;
 * 2. 推理模型基本拒收 `temperature`(仅 AUTO/OFF 时发送);
 * 3. 思考档位走 `reasoning.effort`(关=只在模型声明 OFF 时发 "none")。
 */
class OpenAiResponsesProvider(
    profile: ProviderProfile,
    keyResolver: () -> String?,
) : BaseHttpProvider(profile, keyResolver) {

    internal override fun endpoint(path: String, sse: Boolean): String = when (path) {
        "chat" -> EndpointResolver.resolveChatUrl(profile.baseUrl, Protocol.OPENAI_RESPONSES)
        else -> EndpointResolver.resolveModelsUrl(profile.baseUrl, Protocol.OPENAI_RESPONSES)
    }

    internal override fun applyHeaders(conn: java.net.HttpURLConnection, sse: Boolean) {
        keyResolver()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
    }

    internal override fun buildBody(req: ChatRequest): String {
        val input = buildJsonArray {
            // 历史只带文字(图不重发,§6.2):Responses 的 input item 比 messages 更扁平
            req.history.forEach { m ->
                add(buildJsonObject { put("role", m.role); put("content", m.content) })
            }
            if (req.images.isEmpty()) {
                add(buildJsonObject { put("role", "user"); put("content", req.newUserText) })
            } else {
                val parts = buildJsonArray {
                    // 图先文后:与其它两种协议同语义序
                    req.images.forEachIndexed { i, b64 ->
                        add(buildJsonObject {
                            put("type", "input_image")
                            put("image_url", "data:${mediaTypeOf(req, i)};base64,$b64")
                        })
                    }
                    if (req.newUserText.isNotBlank()) {
                        add(buildJsonObject { put("type", "input_text"); put("text", req.newUserText) })
                    }
                }
                add(buildJsonObject {
                    put("role", "user")
                    put("content", parts)
                })
            }
        }
        val declared = ThinkingDispatch.parseLevels(
            (profile.models.firstOrNull { it.id == req.model } ?: profile.activeModel())
                ?.thinkingLevels ?: emptyList(),
        )
        val obj = buildJsonObject {
            put("model", req.model)
            put("stream", req.stream)
            if (req.system.isNotBlank()) put("instructions", req.system)
            put("input", input)
            when (req.reasoningEffort) {
                ReasoningEffort.AUTO -> Unit   // 跟随模型默认
                ReasoningEffort.OFF ->
                    // 模型声明可关才显式 none(与 OpenRouter 格式同款判断)
                    if (declared.contains(ReasoningEffort.OFF)) {
                        put("reasoning", buildJsonObject { put("effort", "none") })
                    }
                else -> ThinkingDispatch
                    .vendorLevel(req.reasoningEffort, declared.filter { it != ReasoningEffort.OFF })
                    ?.let { level -> put("reasoning", buildJsonObject { put("effort", level) }) }
            }
            // 推理模型拒收温度:只在未开思考时发送
            if (req.reasoningEffort == ReasoningEffort.AUTO || req.reasoningEffort == ReasoningEffort.OFF) {
                put("temperature", req.temperature)
            }
            // Responses API 硬性要求 ≥16(测试连接用 1 会被 400)
            req.maxTokens?.let { put("max_output_tokens", maxOf(it, MIN_OUTPUT_TOKENS)) }
        }
        return BaseHttpProvider.json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(), obj,
        )
    }

    /** 具名事件流:每种事件一个 type,自带增量载荷 */
    internal override fun parseFrame(payload: String): List<ChatEvent> {
        val p = payload.trim()
        if (p.isEmpty()) return emptyList()
        if (p == "[DONE]") return listOf(ChatEvent.Done("completed"))
        val obj = runCatching { BaseHttpProvider.json.parseToJsonElement(p).asObjOrNull() }.getOrNull()
            ?: return emptyList()
        val type = obj.strOrNull("type")
        return when (type) {
            "response.output_text.delta" ->
                obj.nonEmptyStr("delta")?.let { listOf(ChatEvent.Delta(it)) } ?: emptyList()
            "response.reasoning_summary_text.delta", "response.reasoning_text.delta" ->
                obj.nonEmptyStr("delta")?.let { listOf(ChatEvent.Thinking(it)) } ?: emptyList()
            "response.completed" -> {
                val out = ArrayList<ChatEvent>(3)
                out += ChatEvent.Finish("completed")
                // usage 必须排在 Done 之前:BaseHttpProvider 以 Done 收尾并补发 pendingUsage
                usageOf(obj.objOrNull("response")?.objOrNull("usage"))?.let { out += it }
                out += ChatEvent.Done("completed")
                out
            }
            "response.incomplete" -> {
                val resp = obj.objOrNull("response")
                val reason = resp?.objOrNull("incomplete_details")?.strOrNull("reason") ?: "length"
                if (reason == "content_filter") {
                    listOf(ChatEvent.Error(AgentError.Other("Provider finish_reason: content_filter")))
                } else {
                    val out = ArrayList<ChatEvent>(3)
                    out += ChatEvent.Finish(reason)
                    usageOf(resp?.objOrNull("usage"))?.let { out += it }
                    out += ChatEvent.Done(reason)
                    out
                }
            }
            "response.failed" -> {
                val msg = obj.objOrNull("response")?.objOrNull("error")?.strOrNull("message")
                    ?: "Response failed"
                listOf(ChatEvent.Error(AgentError.Other(msg)))
            }
            "error" -> {
                val msg = obj.objOrNull("error")?.strOrNull("message")
                    ?: obj.strOrNull("message")
                    ?: "unknown"
                listOf(ChatEvent.Error(AgentError.Other(msg)))
            }
            // 兜底:个别网关直接回 {"error": {...}} (无 type 字段)
            null -> obj.objOrNull("error")?.let { err ->
                listOf(ChatEvent.Error(AgentError.Other(err.strOrNull("message") ?: "unknown")))
            } ?: emptyList()
            else -> emptyList()   // response.created / output_item.* 等无增量事件
        }
    }

    /** Responses 的 usage 字段名与 ChatCompletions 不同(input/output_tokens) */
    private fun usageOf(u: kotlinx.serialization.json.JsonObject?): ChatEvent.Usage? {
        if (u == null) return null
        val pt = u.intOrNull("input_tokens") ?: 0
        val ct = u.intOrNull("output_tokens") ?: 0
        return if (pt > 0 || ct > 0) ChatEvent.Usage(pt, ct) else null
    }

    companion object {
        const val MIN_OUTPUT_TOKENS = 16
    }
}
