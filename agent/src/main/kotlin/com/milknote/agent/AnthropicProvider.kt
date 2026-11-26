package com.milknote.agent

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Anthropic Messages 原生协议(AGENT-BACKEND §3.7,A2 增补):
 * 部分 Claude 系端点/中转站仅有 Anthropic 格式 —— POST {base}/v1/messages,
 * `x-api-key` + `anthropic-version` 头,图片走 content block `source.base64`。
 * baseUrl 兼容三种写法:`…/v1`、裸域、`…/anthropic`(中转前缀),末尾自动补 /v1/messages
 * (OpenCode Go 的 `/inference/go/anthropic/v1/messages` 也由 [EndpointResolver] 归一)。
 */
class AnthropicProvider(
    profile: ProviderProfile,
    keyResolver: () -> String?,
) : BaseHttpProvider(profile, keyResolver) {

    /** Anthropic 强制 max_tokens;8192:归纳整章长回答不截断,Claude 3+ 全支持 */
    private val defaultMaxTokens: Int get() = DEFAULT_MAX_TOKENS

    private var stashedInputTokens = 0   // message_start 带 input,message_delta 带 output,拼合
    private var stashedStopReason: String? = null

    private class ToolUseBlock(
        val index: Int,
        val id: String,
        val name: String,
        val input: StringBuilder = StringBuilder(),
    )

    private val toolBlocks = LinkedHashMap<Int, ToolUseBlock>()

    internal override fun resetStreamState() {
        stashedInputTokens = 0
        stashedStopReason = null
        toolBlocks.clear()
    }

    internal override fun endpoint(path: String, sse: Boolean): String = when (path) {
        "chat" -> EndpointResolver.resolveChatUrl(profile.baseUrl, Protocol.ANTHROPIC)
        else -> EndpointResolver.resolveModelsUrl(profile.baseUrl, Protocol.ANTHROPIC)
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
            // 工具续写:assistant 的 text/tool_use blocks + 连续的 tool 结果合并为一条 user
            anthropicContinuation(req.continuationMessages).forEach { add(it) }
        }
        // 思考档位 → budget_tokens(AUTO/OFF 不发 thinking,即默认关)
        val budget = ThinkingDispatch.anthropicBudget(req.reasoningEffort)
        val obj = buildJsonObject {
            put("model", req.model)
            put("stream", req.stream)
            // budget 必须严格小于 max_tokens:MAX 档自动抬高输出上限
            val cap = req.maxTokens ?: if (budget != null && budget >= defaultMaxTokens) budget + 4096 else defaultMaxTokens
            put("max_tokens", cap)   // Anthropic 必填
            put("system", req.system)
            put("messages", messages)
            // 工具声明(Anthropic:input_schema)
            if (req.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    req.tools.forEach { t ->
                        add(buildJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("input_schema", toolParameterSchema(t.parameters))
                        })
                    }
                })
            }
            if (budget != null) {
                put("thinking", buildJsonObject {
                    put("type", "enabled")
                    put("budget_tokens", budget)
                })
            }
            // 温度与思考不兼容:开启思考时省略(pi-ai anthropic-messages 同款,省略=默认 1)
            if (budget == null) put("temperature", req.temperature)
        }
        return BaseHttpProvider.json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(), obj,
        )
    }

    /** Anthropic SSE:每个 data 载荷自带 type 字段(event: 行被 SseParser 丢弃,天然兼容) */
    internal override fun parseFrame(payload: String): List<ChatEvent> {
        val p = payload.trim()
        if (p.isEmpty()) return emptyList()
        val obj = runCatching { BaseHttpProvider.json.parseToJsonElement(p).asObjOrNull() }.getOrNull()
            ?: return emptyList()
        return when (obj.strOrNull("type")) {
            "content_block_start" -> {
                val index = obj.intOrNull("index") ?: 0
                val block = obj.objOrNull("content_block")
                if (block?.strOrNull("type") == "tool_use") {
                    val id = block.strOrNull("id") ?: ""
                    val name = block.strOrNull("name") ?: ""
                    val acc = ToolUseBlock(index, id, name)
                    // 少数实现直接在 start 里给完整 input;空对象视为无初始参数
                    block.objOrNull("input")?.takeIf { it.isNotEmpty() }?.let { input ->
                        acc.input.append(ToolArgs.encode(input))
                    }
                    toolBlocks[index] = acc
                }
                emptyList()
            }
            "content_block_delta" -> {
                val index = obj.intOrNull("index") ?: 0
                val delta = obj.objOrNull("delta")
                val deltaType = delta?.strOrNull("type")
                // 工具参数增量:绝不能当正文输出
                if (deltaType == "input_json_delta") {
                    val block = toolBlocks[index]
                    delta?.strOrNull("partial_json")?.let { block?.input?.append(it) }
                    return emptyList()
                }
                // 思考块(extended thinking):thinking_delta.thinking;redacted/签名帧忽略
                if (deltaType == "thinking_delta" || delta?.nonEmptyStr("thinking") != null) {
                    delta?.nonEmptyStr("thinking")?.let { return listOf(ChatEvent.Thinking(it)) }
                    return emptyList()
                }
                val text = delta?.nonEmptyStr("text")
                if (text.isNullOrEmpty()) emptyList() else listOf(ChatEvent.Delta(text))
            }
            "content_block_stop" -> {
                val index = obj.intOrNull("index") ?: 0
                val block = toolBlocks.remove(index) ?: return emptyList()
                if (block.name.isBlank()) return emptyList()
                listOf(
                    ChatEvent.ToolCallReady(
                        ToolCall(
                            id = block.id.ifBlank { "tool_$index" },
                            name = block.name,
                            argumentsJson = block.input.toString().ifBlank { "{}" },
                        ),
                    ),
                )
            }
            "message_start" -> {
                stashedInputTokens = obj.objOrNull("message")?.objOrNull("usage")?.intOrNull("input_tokens") ?: 0
                emptyList()
            }
            "message_delta" -> {
                // stop_reason(end_turn/stop_sequence/max_tokens/tool_use)权威完成信号
                obj.objOrNull("delta")?.nonEmptyStr("stop_reason")?.let { stashedStopReason = it }
                val out = obj.objOrNull("usage")?.intOrNull("output_tokens") ?: 0
                if (stashedInputTokens > 0 || out > 0) listOf(ChatEvent.Usage(stashedInputTokens, out)) else emptyList()
            }
            "message_stop" -> listOf(ChatEvent.Done(stashedStopReason ?: "end_turn"))
            "error" -> {
                val type = obj.objOrNull("error")?.strOrNull("type") ?: "api_error"
                val msg = obj.objOrNull("error")?.strOrNull("message") ?: type
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
            else -> emptyList()   // ping / text block start / message_stop 前置帧
        }
    }

    /**
     * continuation → Anthropic messages:
     * - assistant(toolCalls) → content blocks(text + tool_use, input 为解析后的对象)
     * - 连续 role == "tool" → 合并成单个 user 的 tool_result blocks(Anthropic 要求)
     */
    private fun anthropicContinuation(messages: List<WireMessage>): List<kotlinx.serialization.json.JsonObject> {
        val out = ArrayList<kotlinx.serialization.json.JsonObject>()
        var i = 0
        while (i < messages.size) {
            val m = messages[i]
            when {
                m.role == "tool" -> {
                    val blocks = buildJsonArray {
                        while (i < messages.size && messages[i].role == "tool") {
                            val t = messages[i]
                            add(buildJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", t.toolCallId ?: "")
                                put("content", t.content)
                            })
                            i++
                        }
                    }
                    out.add(buildJsonObject { put("role", "user"); put("content", blocks) })
                }
                m.role == "assistant" && m.toolCalls.isNotEmpty() -> {
                    val blocks = buildJsonArray {
                        if (m.content.isNotBlank()) {
                            add(buildJsonObject { put("type", "text"); put("text", m.content) })
                        }
                        m.toolCalls.forEach { c ->
                            add(buildJsonObject {
                                put("type", "tool_use")
                                put("id", c.id)
                                put("name", c.name)
                                put("input", parseArgumentsObject(c.argumentsJson))
                            })
                        }
                    }
                    out.add(buildJsonObject { put("role", "assistant"); put("content", blocks) })
                    i++
                }
                else -> {
                    out.add(buildJsonObject { put("role", m.role); put("content", m.content) })
                    i++
                }
            }
        }
        return out
    }

    private fun parseArgumentsObject(json: String): kotlinx.serialization.json.JsonObject =
        runCatching {
            BaseHttpProvider.json.parseToJsonElement(json).asObjOrNull()
        }.getOrNull() ?: ToolArgs.emptyObject()

    internal override fun mapHttpError(code: Int, body: String): AgentError = when (code) {
        529 -> AgentError.Server(ErrorMapping.withBody("服务过载(HTTP 529),请稍后重试", body))   // Anthropic 独有过载码
        else -> ErrorMapping.fromHttp(code, body)
    }

    companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val DEFAULT_MAX_TOKENS = 8192
    }
}
