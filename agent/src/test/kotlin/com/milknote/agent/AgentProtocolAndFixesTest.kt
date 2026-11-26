package com.milknote.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * 协议与网关修正的聚焦用例(A2/B 收尾):
 * - MiMo 的 `usage: null` finish 帧不得打断流(真机实证的 StreamBroken 误判)
 * - Responses API 具名事件流与请求体(instructions / input_image / min 16 / reasoning.effort / 无 temperature)
 * - OpenCode Go 一家多协议的端点展开、产品 UA 与会话头
 * - `/v1/models` 元数据(过滤非对话模型、vision、容量、思考档、per-model 协议)
 * - 错误正文不截断(排障线索完整保留)
 * - MiMo 思考开关;AgentController 的协议保留 / per-model 分发 / 跨 Provider 切模型 / 摘要清洗
 */
class AgentProtocolAndFixesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ==================== 极简 HTTP 服务(录制请求头/体,零依赖) ====================

    private class Resp(
        val status: String,
        val body: ByteArray,
        val headers: List<Pair<String, String>> = emptyList(),
    )

    private class Recorded(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    )

    /** 读掉请求头+体并录制,按队列回吐响应(耗尽后重复最后一个;Connection: close) */
    private class MiniHttp(vararg responses: Resp) {
        private val queue = ConcurrentLinkedQueue(responses.toList())
        private val last = responses.last()
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val recorded = CopyOnWriteArrayList<Recorded>()
        val baseUrl: String get() = "http://127.0.0.1:${server.localPort}/v1"

        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val sock = runCatching { server.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { runCatching { sock.use { serve(it) } } }
                }
            }
        }

        private fun serve(sock: java.net.Socket) {
            val input = BufferedInputStream(sock.getInputStream())
            val requestLine = readLine(input) ?: return
            val headers = LinkedHashMap<String, String>()
            var contentLength = 0
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val name = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                headers[name] = value
                if (name == "content-length") contentLength = value.toIntOrNull() ?: 0
            }
            val body = if (contentLength > 0) {
                val buf = ByteArray(contentLength)
                var off = 0
                while (off < contentLength) {
                    val n = input.read(buf, off, contentLength - off)
                    if (n < 0) break
                    off += n
                }
                String(buf, 0, off, Charsets.UTF_8)
            } else {
                ""
            }
            val parts = requestLine.split(' ')
            recorded.add(
                Recorded(
                    method = parts.getOrElse(0) { "" },
                    path = parts.getOrElse(1) { "" },
                    headers = headers,
                    body = body,
                ),
            )

            val r = queue.poll() ?: last
            val head = buildString {
                append("HTTP/1.1 ").append(r.status).append("\r\n")
                append("Content-Type: text/event-stream\r\n")
                for ((k, v) in r.headers) append(k).append(": ").append(v).append("\r\n")
                append("Content-Length: ").append(r.body.size).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            sock.getOutputStream().apply {
                write(head.toByteArray())
                write(r.body)
                flush()
            }
        }

        fun close() = server.close()

        private fun readLine(input: InputStream): String? {
            val sb = StringBuilder()
            while (true) {
                val c = input.read()
                if (c < 0) return if (sb.isEmpty()) null else sb.toString()
                if (c == '\n'.code) return sb.toString().trimEnd('\r')
                sb.append(c.toChar())
            }
        }
    }

    private fun sse(vararg frames: String): ByteArray =
        frames.joinToString("") { "data: $it\n\n" }.toByteArray()

    // ==================== 公共装配 ====================

    private fun profileOf(
        baseUrl: String,
        protocol: Protocol = Protocol.OPENAI_COMPATIBLE,
        presetId: String? = null,
        modelId: String = "m1",
        thinkingLevels: List<String> = emptyList(),
    ) = ProviderProfile(
        id = "t", presetId = presetId, title = "t", baseUrl = baseUrl, apiKeyAlias = "a",
        models = listOf(ModelInfo(modelId, thinkingLevels = thinkingLevels)),
        activeModelId = modelId,
        protocol = protocol,
    )

    private fun chatOf(provider: AgentProvider, sessionId: String? = null) = runBlocking {
        provider.chat(
            ChatRequest(model = "m1", system = "s", newUserText = "hi", sessionId = sessionId),
        ).toList()
    }

    private fun deltas(events: List<ChatEvent>) =
        events.filterIsInstance<ChatEvent.Delta>().joinToString("") { it.text }

    private fun thinkings(events: List<ChatEvent>) =
        events.filterIsInstance<ChatEvent.Thinking>().joinToString("") { it.text }

    // ==================== 1. MiMo:usage:null 的 finish 帧 ====================

    @Test
    fun `MiMo finish帧带usage null-有DONE时干净完成`() {
        val srv = MiniHttp(
            Resp(
                "200 OK",
                sse(
                    """{"choices":[{"delta":{"role":"assistant","content":"答"},"finish_reason":null}],"usage":null}""",
                    """{"choices":[{"delta":{"content":null,"reasoning_content":null,"role":null},"finish_reason":"stop"}],"usage":null}""",
                    """{"choices":[],"usage":{"prompt_tokens":21,"completion_tokens":7}}""",
                    "[DONE]",
                ),
            ),
        )
        try {
            val events = chatOf(OpenAiCompatProvider(profileOf(srv.baseUrl), { "k" }))
            assertEquals("答", deltas(events))
            assertEquals("", thinkings(events))                       // JSON null 不得变字面量 "null"
            assertTrue("不该有错误事件:$events", events.none { it is ChatEvent.Error })
            val done = events.filterIsInstance<ChatEvent.Done>()
            assertEquals(1, done.size)
            val usage = events.filterIsInstance<ChatEvent.Usage>()
            assertEquals(1, usage.size)                               // finish 帧的 null usage 不产生统计
            assertEquals(21, usage.first().promptTokens)
            assertEquals(7, usage.first().completionTokens)
            assertTrue(events.last() is ChatEvent.Usage)              // Done 后补发 pendingUsage
        } finally {
            srv.close()
        }
    }

    @Test
    fun `MiMo finish帧带usage null-无DONE时按finish_reason收尾`() {
        val srv = MiniHttp(
            Resp(
                "200 OK",
                sse(
                    """{"choices":[{"delta":{"content":"答"},"finish_reason":null}],"usage":null}""",
                    """{"choices":[{"delta":{},"finish_reason":"stop"}],"usage":null}""",
                    """{"choices":[],"usage":{"prompt_tokens":5,"completion_tokens":2}}""",
                    // 服务端发完即关流:无 [DONE](MiMo 真机行为)
                ),
            ),
        )
        try {
            val events = chatOf(OpenAiCompatProvider(profileOf(srv.baseUrl), { "k" }))
            assertEquals("答", deltas(events))
            assertTrue("不该有错误事件:$events", events.none { it is ChatEvent.Error })
            assertEquals("stop", events.filterIsInstance<ChatEvent.Done>().single().finishReason)
            val usage = events.filterIsInstance<ChatEvent.Usage>().single()
            assertEquals(5, usage.promptTokens)
            assertEquals(2, usage.completionTokens)
        } finally {
            srv.close()
        }
    }

    // ==================== 2. Responses API ====================

    @Test
    fun `Responses流-思考摘要与正文增量-completed带input(output)tokens`() {
        val srv = MiniHttp(
            Resp(
                "200 OK",
                sse(
                    """{"type":"response.created","response":{"id":"r1"}}""",
                    """{"type":"response.reasoning_summary_text.delta","delta":"先想"}""",
                    """{"type":"response.output_text.delta","delta":"答"}""",
                    """{"type":"response.output_item.done","item":{"type":"message"}}""",
                    """{"type":"response.completed","response":{"usage":{"input_tokens":9,"output_tokens":4}}}""",
                ),
            ),
        )
        try {
            val events = chatOf(
                OpenAiResponsesProvider(profileOf(srv.baseUrl, Protocol.OPENAI_RESPONSES), { "k" }),
            )
            assertEquals("先想", thinkings(events))
            assertEquals("答", deltas(events))
            assertTrue("不该有错误事件:$events", events.none { it is ChatEvent.Error })
            val usage = events.filterIsInstance<ChatEvent.Usage>().single()
            assertEquals(9, usage.promptTokens)
            assertEquals(4, usage.completionTokens)
            assertEquals("completed", events.filterIsInstance<ChatEvent.Done>().single().finishReason)
            assertTrue(events.last() is ChatEvent.Usage)   // usage 在 Done 之后补发
        } finally {
            srv.close()
        }
    }

    @Test
    fun `Responses流-response failed按错误上报`() {
        val srv = MiniHttp(
            Resp(
                "200 OK",
                sse("""{"type":"response.failed","response":{"error":{"message":"upstream overloaded"}}}"""),
            ),
        )
        try {
            val events = chatOf(
                OpenAiResponsesProvider(profileOf(srv.baseUrl, Protocol.OPENAI_RESPONSES), { "k" }),
            )
            val err = events.filterIsInstance<ChatEvent.Error>().single().error
            assertTrue(err is AgentError.Other && err.detail.contains("upstream overloaded"))
            assertTrue(events.none { it is ChatEvent.Delta })
            assertTrue(events.none { it is ChatEvent.Done })
        } finally {
            srv.close()
        }
    }

    @Test
    fun `Responses请求体-instructions与input_image与min16与reasoning-effort且省略temperature`() {
        val provider = OpenAiResponsesProvider(
            profileOf(
                "https://opencode.ai/inference/go",
                Protocol.OPENAI_RESPONSES,
                presetId = ProviderRegistry.PRESET_OPENCODE_GO,
                thinkingLevels = listOf("OFF", "LOW", "MEDIUM", "HIGH", "XHIGH", "MAX"),
            ),
            { "k" },
        )
        val base = ChatRequest(
            model = "m1",
            system = "你是老师",
            newUserText = "讲解这一页",
            images = listOf("QUJD"),
            imageMediaTypes = listOf("image/png"),
            maxTokens = 1,
        )

        val body = json.parseToJsonElement(provider.buildBody(base.copy(reasoningEffort = ReasoningEffort.HIGH)))
            .jsonObject
        assertEquals("你是老师", body["instructions"]!!.jsonPrimitive.content)
        assertFalse("Responses 不用 messages", body.containsKey("messages"))
        // 输出上限字段是 max_output_tokens,且硬性 ≥16(测试连接传 1 也不能原样发)
        assertEquals(16, body["max_output_tokens"]!!.jsonPrimitive.int)
        // 图先文后:input_image + data URI
        val userItem = body["input"]!!.jsonArray.last().jsonObject
        assertEquals("user", userItem["role"]!!.jsonPrimitive.content)
        val parts = userItem["content"]!!.jsonArray
        assertEquals("input_image", parts.first().jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "data:image/png;base64,QUJD",
            parts.first().jsonObject["image_url"]!!.jsonPrimitive.content,
        )
        assertEquals("input_text", parts.last().jsonObject["type"]!!.jsonPrimitive.content)
        // 思考档走 reasoning.effort,且推理开启时不发温度
        assertEquals("high", body["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertFalse("推理模型拒收 temperature", body.containsKey("temperature"))

        // AUTO:跟随模型默认 → 带温度、不带 reasoning
        val auto = json.parseToJsonElement(provider.buildBody(base.copy(reasoningEffort = ReasoningEffort.AUTO)))
            .jsonObject
        assertTrue(auto.containsKey("temperature"))
        assertFalse(auto.containsKey("reasoning"))
    }

    // ==================== 3. 端点解析 ====================

    @Test
    fun `OpenCode Go 端点-三种baseUrl三种协议与models端点`() {
        val protocols = listOf(
            Protocol.OPENAI_COMPATIBLE,
            Protocol.ANTHROPIC,
            Protocol.OPENAI_RESPONSES,
        )
        val go = "https://opencode.ai/inference/go"
        assertEquals("$go/openai/v1/chat/completions", EndpointResolver.resolveChatUrl(go, Protocol.OPENAI_COMPATIBLE))
        assertEquals("$go/openai/v1/responses", EndpointResolver.resolveChatUrl(go, Protocol.OPENAI_RESPONSES))
        assertEquals("$go/anthropic/v1/messages", EndpointResolver.resolveChatUrl(go, Protocol.ANTHROPIC))
        for (p in protocols) assertEquals("$go/v1/models", EndpointResolver.resolveModelsUrl(go, p))

        // 用户把协议段也粘进 baseUrl:协议段丢弃后按同一根展开
        val withPrefix = "https://opencode.ai/inference/go/openai/v1"
        assertEquals("$go/openai/v1/chat/completions", EndpointResolver.resolveChatUrl(withPrefix, Protocol.OPENAI_COMPATIBLE))
        assertEquals("$go/openai/v1/responses", EndpointResolver.resolveChatUrl(withPrefix, Protocol.OPENAI_RESPONSES))
        assertEquals("$go/anthropic/v1/messages", EndpointResolver.resolveChatUrl(withPrefix, Protocol.ANTHROPIC))
        for (p in protocols) assertEquals("$go/v1/models", EndpointResolver.resolveModelsUrl(withPrefix, p))

        // 旧版 Zen 路径:协议段统一 /v1
        val zen = "https://opencode.ai/zen/go/v1"
        val zenRoot = "https://opencode.ai/zen/go"
        assertEquals("$zenRoot/v1/chat/completions", EndpointResolver.resolveChatUrl(zen, Protocol.OPENAI_COMPATIBLE))
        assertEquals("$zenRoot/v1/responses", EndpointResolver.resolveChatUrl(zen, Protocol.OPENAI_RESPONSES))
        assertEquals("$zenRoot/v1/messages", EndpointResolver.resolveChatUrl(zen, Protocol.ANTHROPIC))
        for (p in protocols) assertEquals("$zenRoot/v1/models", EndpointResolver.resolveModelsUrl(zen, p))

        // 标准端点不受 OpenCode 分支影响
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            EndpointResolver.resolveChatUrl("https://api.anthropic.com/v1", Protocol.ANTHROPIC),
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            EndpointResolver.resolveChatUrl("https://api.openai.com/v1", Protocol.OPENAI_COMPATIBLE),
        )
    }

    @Test
    fun `isOpenCodeBaseUrl 判定只认自有域名`() {
        assertTrue(EndpointResolver.isOpenCodeBaseUrl("https://opencode.ai/inference/go"))
        assertTrue(EndpointResolver.isOpenCodeBaseUrl("https://opencode.ai/zen/go/v1"))
        assertTrue(EndpointResolver.isOpenCodeBaseUrl("https://api.opencode.ai/v1"))
        assertFalse(EndpointResolver.isOpenCodeBaseUrl("https://opencode.ai.evil.example/v1"))
        assertFalse(EndpointResolver.isOpenCodeBaseUrl("https://api.openai.com/v1"))
        assertFalse(EndpointResolver.isOpenCodeBaseUrl(""))
    }

    // ==================== 4. 网关请求头 ====================

    @Test
    fun `OpenCode Go 请求带产品UA与会话头-非OpenCode端点不带`() {
        val ok = Resp("200 OK", sse("""{"choices":[{"delta":{"content":"ok"}}]}""", "[DONE]"))
        val srv = MiniHttp(ok, ok, ok)
        try {
            val go = profileOf(
                srv.baseUrl,
                presetId = ProviderRegistry.PRESET_OPENCODE_GO,
            ).copy(id = "ocg")
            chatOf(OpenAiCompatProvider(go, { "k" }), sessionId = "sess-abc")
            chatOf(OpenAiCompatProvider(go, { "k" }))
            // 走系统 HttpURLConnection 时默认 UA 是 Dalvik,多家网关直接 403 → 固定产品 UA
            assertEquals("MilkNote/1.0", srv.recorded[0].headers["user-agent"])
            assertEquals("sess-abc", srv.recorded[0].headers["x-opencode-session"])
            assertEquals("Bearer k", srv.recorded[0].headers["authorization"])
            assertEquals("/v1/chat/completions", srv.recorded[0].path)
            // 无会话 id:按 profile 派生稳定值,不与其他家互相踩
            assertEquals("milknote-ocg", srv.recorded[1].headers["x-opencode-session"])

            chatOf(OpenAiCompatProvider(profileOf(srv.baseUrl), { "k" }), sessionId = "sess-abc")
            assertEquals("MilkNote/1.0", srv.recorded[2].headers["user-agent"])
            assertNull(srv.recorded[2].headers["x-opencode-session"])
        } finally {
            srv.close()
        }
    }

    // ==================== 5. /v1/models 元数据 ====================

    private val openCodeModelsJson = """
        {
          "data": [
            {
              "id": "gpt-6-luna",
              "name": "GPT-6 Luna",
              "type": "chat",
              "modalities": { "input": ["text", "image"] },
              "limit": { "context": 1050000, "output": 128000 },
              "capabilities": { "reasoning": { "effort": ["none", "low", "medium", "high", "xhigh", "max"], "toggle": true } },
              "recommended_api": "responses"
            },
            {
              "id": "claude-haiku-5-5",
              "name": "Claude Haiku 5.5",
              "type": "chat",
              "modalities": { "input": ["text", "image"] },
              "limit": { "context": 1000000, "output": 64000 },
              "recommended_api": "messages"
            },
            {
              "id": "glm-5.3-flash",
              "name": "GLM-5.3 Flash",
              "type": "chat",
              "modalities": { "input": ["text"] },
              "limit": { "context": 1000000, "output": 128000 },
              "recommended_api": "chat-completions"
            },
            {
              "id": "text-embedding-4",
              "type": "embedding",
              "limit": { "context": 8192, "output": 0 }
            },
            {
              "id": "router-decision",
              "type": "decision"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `models列表解析-过滤非对话模型-视觉与容量与思考档与per-model协议`() {
        val models = BaseHttpProvider.parseModelsList(openCodeModelsJson, openRouter = false)

        // decision / embedding 不进选择器
        assertEquals(listOf("gpt-6-luna", "claude-haiku-5-5", "glm-5.3-flash"), models.map { it.id })

        val luna = models.first { it.id == "gpt-6-luna" }
        assertEquals("GPT-6 Luna", luna.displayName)
        assertTrue(luna.vision)
        assertEquals(VisionSource.METADATA.name, luna.visionSource)
        assertEquals(1050, luna.contextWindowK)
        assertEquals(128, luna.maxTokensK)
        assertEquals(
            listOf("OFF", "LOW", "MEDIUM", "HIGH", "XHIGH", "MAX"),
            luna.thinkingLevels,
        )
        assertEquals(Protocol.OPENAI_RESPONSES, luna.protocol)

        val claude = models.first { it.id == "claude-haiku-5-5" }
        assertEquals("Claude Haiku 5.5", claude.displayName)
        assertTrue(claude.vision)
        assertEquals(1000, claude.contextWindowK)
        assertEquals(64, claude.maxTokensK)
        assertTrue(claude.thinkingLevels.isEmpty())
        assertEquals(Protocol.ANTHROPIC, claude.protocol)

        // 无图片来源 → 非视觉;无元数据 → 用户声明兜底
        val glm = models.first { it.id == "glm-5.3-flash" }
        assertFalse(glm.vision)
        assertEquals(VisionSource.DECLARED.name, glm.visionSource)
        assertEquals(Protocol.OPENAI_COMPATIBLE, glm.protocol)
    }

    // ==================== 6. 错误正文不截断 ====================

    @Test
    fun `错误正文保留完整服务端响应-不200字截断`() {
        val payload = buildString {
            append("""{"error":{"message":"upstream failure","trace":" """)
            repeat(30) { append("segment-$it|") }
            append("""","detail":"${"z".repeat(400)}TAIL_MARKER"}}""")
        }
        assertTrue("样本需超过 500 字", payload.length > 500)

        val err = ErrorMapping.fromHttp(500, payload)
        assertTrue(err is AgentError.Server)
        val detail = err.detail
        assertTrue("正文长度应保留:${detail.length}", detail.length > 500)
        assertTrue(detail.contains("segment-29"))
        assertTrue(detail.endsWith(payload.trim()))
        assertTrue(detail.contains("TAIL_MARKER"))
        assertFalse("不得用省略号截断", detail.contains("…"))
    }

    // ==================== 7. MiMo 思考开关 ====================

    private fun thinkingBody(effort: ReasoningEffort): JsonObject = buildJsonObject {
        ThinkingDispatch.applyOpenAi(
            this,
            effort,
            ThinkingDispatch.formatOf("mimo"),
            listOf(ReasoningEffort.OFF, ReasoningEffort.HIGH),
        )
    }

    @Test
    fun `MiMo thinking开关-OFF为disabled-HIGH为enabled`() {
        val off = thinkingBody(ReasoningEffort.OFF)
        assertEquals("disabled", off["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertNull(off["reasoning_effort"])

        val high = thinkingBody(ReasoningEffort.HIGH)
        assertEquals("enabled", high["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("high", high["reasoning_effort"]!!.jsonPrimitive.content)
    }

    // ==================== 8. AgentController ====================

    private class Rig(val controller: AgentController, val store: AgentStore)

    private fun rig(
        scope: CoroutineScope,
        fake: FakeProvider,
        captured: MutableList<ProviderProfile> = ArrayList(),
    ): Rig {
        val store = AgentStore(tmp.newFolder(), tmp.newFolder())
        val controller = AgentController(
            scope = scope,
            store = store,
            snipSource = null,
            annotationSink = null,
            keyFor = { "sk-test" },
            providerFactory = { p, _, _ ->
                captured.add(p)
                fake
            },
        )
        controller.setSummaryConfig(SummaryConfig(enabled = false))
        return Rig(controller, store)
    }

    @Test
    fun `自定义与Anthropic预设都保留Anthropic协议`() = runTest {
        val fake = FakeProvider { listOf(ChatEvent.Done(null)) }
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), fake)
        val c = rig.controller

        c.addProfile("custom", "中转站", "https://proxy.example/v1", "sk-1", Protocol.ANTHROPIC)
        val custom = c.state.value.profiles.last()
        assertEquals(Protocol.ANTHROPIC, custom.protocol)
        assertEquals("https://proxy.example/v1", custom.baseUrl)

        // 预设自带 ANTHROPIC:不传 protocol 时不得被默认值冲成 OPENAI_COMPATIBLE
        c.addProfile("anthropic", "", "", "sk-2")
        val claude = c.state.value.profiles.last()
        assertEquals("anthropic", claude.presetId)
        assertEquals(Protocol.ANTHROPIC, claude.protocol)
        assertEquals("https://api.anthropic.com/v1", claude.baseUrl)
        assertTrue(claude.models.any { it.id == "claude-opus-5-5" })

        // 落盘往返仍保持
        val saved = rig.store.loadProfiles()
        val savedCustom = saved.profiles.first { it.id == custom.id }
        val savedClaude = saved.profiles.first { it.id == claude.id }
        assertEquals(Protocol.ANTHROPIC, savedCustom.protocol)
        assertEquals(Protocol.ANTHROPIC, savedClaude.protocol)
    }

    @Test
    fun `一家多协议按模型切换有效协议`() = runTest {
        val captured = ArrayList<ProviderProfile>()
        val fake = FakeProvider { listOf(ChatEvent.Delta("答"), ChatEvent.Done(null)) }
        val c = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), fake, captured).controller

        c.addProfile("opencode_go", "", "", "sk-go")
        val pid = c.state.value.profiles.last().id
        c.openDrawer(PageRef("nb1", "p0001"))

        // profile 默认是 ChatCompletions;模型级覆盖决定实际协议
        assertEquals(Protocol.OPENAI_COMPATIBLE, c.state.value.profiles.last().protocol)
        assertEquals(
            Protocol.ANTHROPIC,
            c.state.value.profiles.last().effectiveProtocol("claude-haiku-5-5"),
        )

        c.selectModel(pid, "claude-haiku-5-5")
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        assertEquals(Protocol.ANTHROPIC, captured.last().protocol)
        assertEquals("claude-haiku-5-5", fake.requests.last().model)

        c.selectModel(pid, "gpt-6-luna")
        c.setDraftText("再讲讲")
        c.send()
        advanceUntilIdle()
        assertEquals(Protocol.OPENAI_RESPONSES, captured.last().protocol)
        assertEquals("gpt-6-luna", fake.requests.last().model)

        c.selectModel(pid, "deepseek-v4.1-flash")
        c.setDraftText("继续")
        c.send()
        advanceUntilIdle()
        assertEquals(Protocol.OPENAI_COMPATIBLE, captured.last().protocol)
        assertEquals("deepseek-v4.1-flash", fake.requests.last().model)
    }

    @Test
    fun `selectModel跨Provider会同步激活目标profile`() = runTest {
        val fake = FakeProvider { listOf(ChatEvent.Done(null)) }
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), fake)
        val c = rig.controller

        c.addProfile("opencode_go", "A", "", "sk-a")
        val a = c.state.value.profiles.last().id
        c.addProfile("anthropic", "B", "", "sk-b")
        val b = c.state.value.profiles.last().id
        c.setActiveProfile(a)
        assertEquals(a, c.state.value.activeProfileId)

        c.selectModel(b, "claude-sonnet-5-5")

        // 选了别家的模型就必须切过去,否则端点与 Key 全对不上
        assertEquals(b, c.state.value.activeProfileId)
        assertEquals("claude-sonnet-5-5", c.state.value.activeModelId)
        val persisted = rig.store.loadProfiles()
        assertEquals(b, persisted.activeProfileId)
        assertEquals("claude-sonnet-5-5", persisted.profiles.first { it.id == b }.activeModelId)
    }

    @Test
    fun `摘要清洗剥离thinking标签与Markdown`() = runTest {
        val fake = FakeProvider { req ->
            if (req.system == SUMMARY_SYSTEM_PROMPT) {
                listOf(
                    ChatEvent.Delta("<thinking>先想想这段对话在讲什么</thinking>\n# **二次方程求解**"),
                    ChatEvent.Done(null),
                )
            } else {
                listOf(ChatEvent.Delta("答"), ChatEvent.Done(null))
            }
        }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))
        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        val sessionId = c.state.value.currentSessionId!!
        assertTrue(e.store.loadSession("nb1", sessionId)!!.summary.isBlank())

        c.regenerateSummary(sessionId)
        advanceUntilIdle()

        val saved = e.store.loadSession("nb1", sessionId)!!
        assertEquals("二次方程求解", saved.summary)
        assertEquals("二次方程求解", c.state.value.sessions.single().summary)
        assertTrue(c.state.value.summarizingSessionIds.isEmpty())
    }

    @Test
    fun `手动摘要失败给出toast原因`() = runTest {
        val fake = FakeProvider { req ->
            if (req.system == SUMMARY_SYSTEM_PROMPT) {
                listOf(ChatEvent.Error(AgentError.Server("服务方故障(HTTP 503)\nupstream down")))
            } else {
                listOf(ChatEvent.Delta("答"), ChatEvent.Done(null))
            }
        }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))
        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        val sessionId = c.state.value.currentSessionId!!

        c.regenerateSummary(sessionId)
        advanceUntilIdle()

        val toast = c.state.value.toast
        assertNotNull("手动摘要失败必须有反馈", toast)
        assertTrue(toast!!.startsWith("总结生成失败"))
        assertTrue(toast.contains("服务方故障"))          // 取错误首行,不塞整段正文
        assertTrue(e.store.loadSession("nb1", sessionId)!!.summary.isBlank())
        assertTrue(c.state.value.summarizingSessionIds.isEmpty())
    }
}
