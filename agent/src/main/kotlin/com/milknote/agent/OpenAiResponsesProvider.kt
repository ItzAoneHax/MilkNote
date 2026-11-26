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

    private class ResponseCall(
        val key: String,
        val callId: String,
        var name: String,
        val args: StringBuilder = StringBuilder(),
    )

    private val responseCalls = LinkedHashMap<String, ResponseCall>()
    private val emittedCalls = HashSet<String>()

    internal override fun resetStreamState() {
        responseCalls.clear()
        emittedCalls.clear()
    }

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
            // 工具续写:assistant 文本项 + function_call 项;tool → function_call_output
            req.continuationMessages.forEach { m ->
                when {
                    m.role == "tool" -> add(buildJsonObject {
                        put("type", "function_call_output")
                        put("call_id", m.toolCallId ?: "")
                        put("output", m.content)
                    })
                    m.role == "assistant" -> {
                        if (m.content.isNotBlank()) {
                            add(buildJsonObject {
                                put("type", "message")
                                put("role", "assistant")
                                put("content", buildJsonArray {
                                    add(buildJsonObject {
                                        put("type", "output_text")
                                        put("text", m.content)
                                    })
                                })
                            })
                        }
                        m.toolCalls.forEach { c ->
                            add(buildJsonObject {
                                put("type", "function_call")
                                put("call_id", c.id)
                                put("name", c.name)
                                put("arguments", c.argumentsJson)
                            })
                        }
                    }
                    else -> add(buildJsonObject { put("role", m.role); put("content", m.content) })
                }
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
            // 工具声明(Responses:type=function + name/description/parameters)
            if (req.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    req.tools.forEach { t ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("name", t.name)
                            put("description", t.description)
                            put("parameters", toolParameterSchema(t.parameters))
                        })
                    }
                })
            }
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
            "response.output_item.added" -> {
                val item = obj.objOrNull("item")
                if (item?.strOrNull("type") == "function_call") {
                    val acc = callFromItem(item)
                    if (acc != null) responseCalls[acc.key] = acc
                }
                emptyList()
            }
            "response.function_call_arguments.delta" -> {
                val acc = findCall(obj)
                if (acc != null) obj.nonEmptyStr("delta")?.let { acc.args.append(it) }
                emptyList()
            }
            "response.function_call_arguments.done" -> {
                val acc = findCall(obj) ?: return emptyList()
                obj.strOrNull("arguments")?.takeIf { it.isNotBlank() }?.let {
                    acc.args.clear()
                    acc.args.append(it)
                }
                emitCall(acc)
            }
            "response.output_item.done" -> {
                val item = obj.objOrNull("item")
                if (item?.strOrNull("type") == "function_call") {
                    val acc = callFromItem(item) ?: return emptyList()
                    val existing = responseCalls[acc.key] ?: responseCalls.values
                        .firstOrNull { it.callId == acc.callId }
                    if (existing != null) {
                        if (acc.name.isNotBlank()) existing.name = acc.name
                        if (acc.args.isNotEmpty()) {
                            existing.args.clear()
                            existing.args.append(acc.args)
                        }
                        responseCalls.remove(acc.key)
                        emitCall(existing)
                    } else {
                        emitCall(acc)
                    }
                } else {
                    emptyList()
                }
            }
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

    private fun callFromItem(item: kotlinx.serialization.json.JsonObject): ResponseCall? {
        val itemId = item.strOrNull("id") ?: item.strOrNull("call_id") ?: return null
        val callId = item.strOrNull("call_id") ?: itemId
        val acc = ResponseCall(itemId, callId, item.strOrNull("name") ?: "")
        item.strOrNull("arguments")?.takeIf { it.isNotBlank() }?.let { acc.args.append(it) }
        return acc
    }

    /** 参数增量帧按 item_id/call_id 定位;找不到就建一个待补名的累积器 */
    private fun findCall(obj: kotlinx.serialization.json.JsonObject): ResponseCall? {
        val key = obj.strOrNull("item_id") ?: obj.strOrNull("call_id") ?: return null
        responseCalls[key]?.let { return it }
        val callId = obj.strOrNull("call_id")
        responseCalls.values.firstOrNull { it.callId == key || (callId != null && it.callId == callId) }
            ?.let { return it }
        val acc = ResponseCall(key, callId ?: key, obj.strOrNull("name") ?: "")
        responseCalls[key] = acc
        return acc
    }

    /** 去重后发一次 ToolCallReady(参数 done 与 output_item.done 可能重复到达) */
    private fun emitCall(acc: ResponseCall): List<ChatEvent> {
        if (acc.name.isBlank() || acc.callId in emittedCalls) return emptyList()
        emittedCalls += acc.callId
        return listOf(
            ChatEvent.ToolCallReady(
                ToolCall(
                    id = acc.callId,
                    name = acc.name,
                    argumentsJson = acc.args.toString().ifBlank { "{}" },
                ),
            ),
        )
    }

    companion object {
        const val MIN_OUTPUT_TOKENS = 16
    }
}
