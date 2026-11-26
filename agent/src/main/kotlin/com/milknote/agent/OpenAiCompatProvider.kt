package com.milknote.agent

import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Provider 抽象(§3.1;协议按 profile 选择:A2 起 OpenAI 兼容 + Anthropic 原生 + Responses 并存) */
interface AgentProvider {
    val profileId: String
    fun chat(req: ChatRequest): kotlinx.coroutines.flow.Flow<ChatEvent>

    /** 拉取模型列表(尽力而为;OpenRouter/OpenCode 元数据自动标 vision 与协议) */
    suspend fun fetchModels(): Result<List<ModelInfo>>

    /** 连接测试:纯文本 1-token 请求,仅验鉴权可达(§3.3,无视觉探测) */
    suspend fun testConnection(): Result<Long>

    /** 取消在途请求(默认空实现:Fake/测试 Provider 无需处理) */
    fun cancel() {}
}

/**
 * OpenAI 兼容实现(§3.5):POST {chatUrl},stream=true,SSE 手解析。
 * - imageStyle 自适应:图片请求 400 且文案指向图片格式 → 换另一种 style 重试一次,
 *   经 [onPatch] 回写 profile,后续不再抖动
 * - 脏帧(非 JSON / null 字段类型不符)跳过不中断(§16)
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

    // ---- 工具调用累积(增量 delta 需等流尾才完整) ----
    private class ToolCallAccum {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
    }

    private val toolCallAccum = LinkedHashMap<Int, ToolCallAccum>()

    internal override fun resetStreamState() {
        toolCallAccum.clear()
    }

    internal override fun flushStreamEvents(): List<ChatEvent> {
        if (toolCallAccum.isEmpty()) return emptyList()
        val calls = toolCallAccum.entries.sortedBy { it.key }.mapNotNull { (index, acc) ->
            if (acc.name.isBlank()) return@mapNotNull null
            ChatEvent.ToolCallReady(
                ToolCall(
                    id = acc.id.ifBlank { "call_${index}_${acc.name}" },
                    name = acc.name,
                    argumentsJson = acc.arguments.toString().ifBlank { "{}" },
                ),
            )
        }
        toolCallAccum.clear()
        return calls
    }

    private fun accumulateToolCall(index: Int, element: kotlinx.serialization.json.JsonElement) {
        val o = element.asObjOrNull() ?: return
        val acc = toolCallAccum.getOrPut(index) { ToolCallAccum() }
        o.strOrNull("id")?.takeIf { it.isNotBlank() }?.let { acc.id = it }
        // 标准位置 function.name/arguments;少数中转站直接平铺 name/arguments
        val fn = o.objOrNull("function") ?: o
        fn.strOrNull("name")?.takeIf { it.isNotBlank() }?.let { acc.name = it }
        fn.strOrNull("arguments")?.let { acc.arguments.append(it) }
    }

    internal override fun endpoint(path: String, sse: Boolean): String = when (path) {
        "chat" -> EndpointResolver.resolveChatUrl(profile.baseUrl, Protocol.OPENAI_COMPATIBLE)
        else -> EndpointResolver.resolveModelsUrl(profile.baseUrl, Protocol.OPENAI_COMPATIBLE)
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
            // 工具续写:assistant(tool_calls) + tool 结果,追加在当前 user 之后
            req.continuationMessages.forEach { m -> add(openAiWireMessage(m)) }
        }
        val obj = buildJsonObject {
            put("model", req.model)
            put("stream", req.stream)
            put("messages", messages)
            put("temperature", req.temperature)
            // 工具声明(标准 OpenAI function schema)
            if (req.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    req.tools.forEach { t ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", t.name)
                                put("description", t.description)
                                put("parameters", toolParameterSchema(t.parameters))
                            })
                        })
                    }
                })
            }
            // max_tokens 不发送:OpenAI 推理系(gpt-6.x/o 系)只认 max_completion_tokens,
            // 发错字段直接 400;不限制则走厂商默认(讲解场景回答量级远低于上限)
            if (req.stream && sendStreamOptions) {
                // 精确 usage(§7.3);仅流式路径发 —— 非流式发会被部分网关 400(opencode 同款)
                put("stream_options", buildJsonObject { put("include_usage", true) })
            }
            // 思考强度(§3.8):按厂商 wire format 翻译;AUTO 不发。
            // 档位按「本次请求的模型」查 —— 一家网关下各模型能力不同(OpenCode Go 尤甚)
            val model = profile.models.firstOrNull { it.id == req.model } ?: profile.activeModel()
            ThinkingDispatch.applyOpenAi(
                this, req.reasoningEffort,
                ThinkingDispatch.formatOf(profile.presetId),
                ThinkingDispatch.parseLevels(model?.thinkingLevels ?: emptyList()),
            )
        }
        return Json.encodeToString(JsonObject.serializer(), obj)
    }

    /** continuation 单条消息 → OpenAI ChatCompletions message */
    private fun openAiWireMessage(m: WireMessage) = buildJsonObject {
        put("role", m.role)
        if (m.toolCalls.isNotEmpty()) {
            put("tool_calls", buildJsonArray {
                m.toolCalls.forEach { c ->
                    add(buildJsonObject {
                        put("id", c.id)
                        put("type", "function")
                        put("function", buildJsonObject {
                            put("name", c.name)
                            put("arguments", c.argumentsJson)
                        })
                    })
                }
            })
            // tool_calls 存在时 content 可为空;有文字再带上
            if (m.content.isNotBlank()) put("content", m.content)
        } else {
            put("content", m.content)
        }
        m.toolCallId?.let { put("tool_call_id", it) }
        if (m.role == "tool") m.toolName?.takeIf { it.isNotBlank() }?.let { put("name", it) }
    }

    /** 单帧 data 载荷 → 事件列表;空列表 = 脏帧跳过(§16 容错) */
    internal override fun parseFrame(payload: String): List<ChatEvent> {
        val p = payload.trim()
        if (p.isEmpty()) return emptyList()
        if (p == "[DONE]") return listOf(ChatEvent.Done("done"))
        val obj = runCatching { json.parseToJsonElement(p).asObjOrNull() }.getOrNull() ?: return emptyList()

        val out = ArrayList<ChatEvent>(2)

        // 错误帧:{"error": {"message": ...}}(error 为 JSON null 时按无错误处理)
        obj.objOrNull("error")?.let { err ->
            val msg = err.strOrNull("message") ?: err.strOrNull("code") ?: "unknown"
            val lowered = msg.lowercase()
            out += ChatEvent.Error(
                if (lowered.contains("image") && (lowered.contains("not support") || lowered.contains("unsupported")))
                    AgentError.ModelNotVision(msg)
                else AgentError.Other(msg),
            )
            return out
        }

        obj.arrOrNull("choices")?.firstOrNull()?.asObjOrNull()?.let { choice ->
            // finish_reason:权威完成信号(pi-ai hasFinishReason);非终止事件,后续帧继续吃。
            // MiMo 真机:该帧常带 "usage": null / "content": null / "reasoning_content": null,
            // 一律走安全取值,JSON null 绝不当字面量 "null" 也不抛异常。
            choice.nonEmptyStr("finish_reason")?.let { out += ChatEvent.Finish(it) }
            val delta = choice.objOrNull("delta") ?: choice.objOrNull("message")
            // JSON null 帧(role/空增量,MiMo 等大量发送)不算文本 —— JsonNull.content
            // 会返回字面量 "null",必须显式排除(真机实证:回复前缀 32 个 null)
            delta?.nonEmptyStr("content")?.let { out += ChatEvent.Delta(it) }
            // 思考过程(deepseek-harness 同款字段序:首个非空,防中转站双字段重复)
            val reasoning = listOf("reasoning_content", "reasoning", "reasoning_text")
                .firstNotNullOfOrNull { f -> delta?.nonEmptyStr(f) }
            if (reasoning != null) out += ChatEvent.Thinking(reasoning)
            // 工具调用增量:优先用元素内 index 聚合(并行调用时数组可能只有一个元素但 index=1)
            choice.objOrNull("delta")?.arrOrNull("tool_calls")?.forEachIndexed { i, el ->
                accumulateToolCall(el.asObjOrNull()?.intOrNull("index") ?: i, el)
            }
            // 非流式:choices[0].message.tool_calls 一次性完整返回
            choice.objOrNull("message")?.arrOrNull("tool_calls")?.forEachIndexed { i, el ->
                val o = el.asObjOrNull() ?: return@forEachIndexed
                val fn = o.objOrNull("function") ?: o
                val acc = toolCallAccum.getOrPut(i) { ToolCallAccum() }
                o.strOrNull("id")?.takeIf { it.isNotBlank() }?.let { acc.id = it }
                fn.strOrNull("name")?.takeIf { it.isNotBlank() }?.let { acc.name = it }
                fn.strOrNull("arguments")?.let {
                    acc.arguments.clear()
                    acc.arguments.append(it)
                }
            }
        }
        // usage 帧:流式 include_usage 的末帧 choices 为空;usage 为 null 表示暂无统计
        obj.objOrNull("usage")?.let { u ->
            val pt = u.intOrNull("prompt_tokens") ?: 0
            val ct = u.intOrNull("completion_tokens") ?: 0
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
