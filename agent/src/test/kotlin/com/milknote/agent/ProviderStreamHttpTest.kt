package com.milknote.agent

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * 流式管线集成测试(裸 ServerSocket 模拟 SSE 脏流,零依赖):
 * 对照成熟 harness(deepseek-harness / opencode)的边界——断流半截/空响应/
 * 思考模型帧/429 Retry-After/302 不跟随/stream_options 自适应。
 */
class ProviderStreamHttpTest {

    private class Resp(
        val status: String,
        val body: ByteArray,
        val headers: List<Pair<String, String>> = emptyList(),
    )

    /** 极简 HTTP 服务:读掉请求头+体,按序回吐响应(队列耗尽后重复最后一个;Connection: close) */
    private class MiniHttp(vararg responses: Resp) {
        private val queue = java.util.concurrent.ConcurrentLinkedQueue(responses.toList())
        private val last = responses.last()
        private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val baseUrl: String get() = "http://127.0.0.1:${server.localPort}/v1"

        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val sock = runCatching { server.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) {
                        runCatching {
                            sock.use { s ->
                                val input = BufferedInputStream(s.getInputStream())
                                var contentLength = 0
                                while (true) {
                                    val line = readLine(input) ?: break
                                    if (line.isEmpty()) break
                                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                        contentLength = line.substringAfter(':').trim().toInt()
                                    }
                                }
                                if (contentLength > 0) {
                                    val body = ByteArray(contentLength)
                                    var off = 0
                                    while (off < contentLength) {
                                        val n = input.read(body, off, contentLength - off)
                                        if (n < 0) break
                                        off += n
                                    }
                                }
                                val r = queue.poll() ?: last
                                val head = buildString {
                                    append("HTTP/1.1 ").append(r.status).append("\r\n")
                                    append("Content-Type: text/event-stream\r\n")
                                    for ((k, v) in r.headers) append(k).append(": ").append(v).append("\r\n")
                                    append("Content-Length: ").append(r.body.size).append("\r\n")
                                    append("Connection: close\r\n\r\n")
                                }
                                s.getOutputStream().apply {
                                    write(head.toByteArray())
                                    write(r.body)
                                    flush()
                                }
                            }
                        }
                    }
                }
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

    private fun sse(vararg frames: String) = frames.joinToString("") { "data: $it\n\n" }.toByteArray()

    private fun providerOf(base: String) = ProviderProfile(
        id = "t", title = "t", baseUrl = base, apiKeyAlias = "a",
        models = listOf(ModelInfo("m1")), activeModelId = "m1",
    )

    private fun chat(p: AgentProvider) = runBlocking {
        p.chat(ChatRequest(model = "m1", system = "s", newUserText = "hi", images = listOf("QQ"))).toList()
    }

    @Test
    fun `正常流-delta-usage帧-DONE`() {
        val srv = MiniHttp(
            Resp("200 OK", sse(
                """{"choices":[{"delta":{"role":"assistant","content":null}}]}""",
                """{"choices":[{"delta":{"content":"你"}}]}""",
                """{"choices":[{"delta":{"content":"好"}}]}""",
                """{"choices":[{"delta":{}}],"usage":{"prompt_tokens":10,"completion_tokens":2}}""",
                "[DONE]",
            )),
        )
        try {
            val events = chat(OpenAiCompatProvider(providerOf(srv.baseUrl), { "k" }))
            assertEquals("你好", events.filterIsInstance<ChatEvent.Delta>().joinToString("") { it.text })
            assertTrue(events.any { it is ChatEvent.Usage && it.promptTokens == 10 && it.completionTokens == 2 })
            assertTrue(events.dropLast(1).last() is ChatEvent.Done)   // 尾帧 Usage 随后补发
            assertTrue(events.none { it is ChatEvent.Error })
        } finally { srv.close() }
    }

    @Test
    fun `半截断流无DONE-标StreamBroken不伪完成`() {
        val srv = MiniHttp(
            Resp("200 OK", sse(
                """{"choices":[{"delta":{"content":"半"}}]}""",
                """{"choices":[{"delta":{"content":"截"}}]}""",
                // 始终没有 [DONE]
            )),
        )
        try {
            val events = chat(OpenAiCompatProvider(providerOf(srv.baseUrl), { "k" }))
            assertTrue(events.any { it is ChatEvent.Error && (it as ChatEvent.Error).error is AgentError.StreamBroken })
            assertTrue(events.none { it is ChatEvent.Done })
            assertEquals("半截", events.filterIsInstance<ChatEvent.Delta>().joinToString("") { it.text })
        } finally { srv.close() }
    }

    @Test
    fun `空响应是错误不是空气泡`() {
        val srv = MiniHttp(Resp("200 OK", ByteArray(0)))
        try {
            val events = chat(OpenAiCompatProvider(providerOf(srv.baseUrl), { "k" }))
            assertTrue(events.none { it is ChatEvent.Delta })
            val err = events.filterIsInstance<ChatEvent.Error>().firstOrNull()?.error
            assertTrue(err is AgentError.EmptyResponse)
        } finally { srv.close() }
    }

    @Test
    fun `思考模型帧-reasoning_content与content null不进正文`() {
        val srv = MiniHttp(
            Resp("200 OK", sse(
                """{"choices":[{"delta":{"role":"assistant","content":null}}]}""",
                """{"choices":[{"delta":{"reasoning_content":"让我想想…"}}]}""",
                """{"choices":[{"delta":{"content":null,"reasoning":null}}]}""",
                """{"choices":[{"delta":{"content":"答案"}}]}""",
                "[DONE]",
            )),
        )
        try {
            val events = chat(OpenAiCompatProvider(providerOf(srv.baseUrl), { "k" }))
            assertEquals("答案", events.filterIsInstance<ChatEvent.Delta>().joinToString("") { it.text })
        } finally { srv.close() }
    }

    @Test
    fun `HTTP429读取Retry-After毫秒头`() {
        val srv = MiniHttp(
            Resp(
                "429 Too Many Requests",
                """{"error":{"message":"rate limited"}}""".toByteArray(),
                headers = listOf("retry-after-ms" to "8000"),
            ),
        )
        try {
            val events = chat(OpenAiCompatProvider(providerOf(srv.baseUrl), { "k" }))
            val err = events.filterIsInstance<ChatEvent.Error>().firstOrNull()?.error
            assertTrue(err is AgentError.RateLimit)
            assertEquals(8000L, (err as AgentError.RateLimit).retryAfterMs)
        } finally { srv.close() }
    }

    @Test
    fun `HTTP429读取Retry-After秒头`() {
        val srv = MiniHttp(
            Resp(
                "429 Too Many Requests",
                "no json".toByteArray(),
                headers = listOf("Retry-After" to "3"),
            ),
        )
        try {
            val events = chat(OpenAiCompatProvider(providerOf(srv.baseUrl), { "k" }))
            val err = events.filterIsInstance<ChatEvent.Error>().firstOrNull()?.error as AgentError.RateLimit
            assertEquals(3000L, err.retryAfterMs)
        } finally { srv.close() }
    }

    @Test
    fun `302中转重定向不静默跟随`() {
        val srv = MiniHttp(
            Resp("302 Found", ByteArray(0), headers = listOf("Location" to "https://evil.example/x")),
            Resp("200 OK", sse("""{"choices":[{"delta":{"content":"never"}}]}""", "[DONE]")),
        )
        try {
            val events = chat(OpenAiCompatProvider(providerOf(srv.baseUrl), { "k" }))
            // 不跟随:302 原样返回 → 报错;不把重定向目标的内容当流吃进来
            assertTrue(events.none { it is ChatEvent.Delta })
            assertTrue(events.filterIsInstance<ChatEvent.Error>().isNotEmpty())
        } finally { srv.close() }
    }

    @Test
    fun `stream_options被拒-自适应去参重试成功`() {
        val srv = MiniHttp(
            Resp("400 Bad Request", """{"error":{"message":"Unknown parameter: stream_options"}}""".toByteArray()),
            Resp("200 OK", sse(
                """{"choices":[{"delta":{"content":"ok"}}]}""",
                """{"choices":[{"delta":{}}],"usage":{"prompt_tokens":5,"completion_tokens":1}}""",
                "[DONE]",
            )),
        )
        try {
            var patched: CompatPatch? = null
            val provider = OpenAiCompatProvider(providerOf(srv.baseUrl), { "k" }) { patched = it }
            val events = chat(provider)
            assertEquals("ok", events.filterIsInstance<ChatEvent.Delta>().joinToString("") { it.text })
            assertTrue(events.none { it is ChatEvent.Error })
            assertNotNull(patched)
            assertEquals(false, patched!!.streamOptions)   // 回写:后续不再携带
        } finally { srv.close() }
    }

    @Test
    fun `请求体-流式带include_usage非流式不带-max_tokens不发送`() {
        val provider = OpenAiCompatProvider(providerOf("https://x.example"), { "k" })
        val json = kotlinx.serialization.json.Json

        val streamBody = json.parseToJsonElement(
            provider.buildRequestBody(ChatRequest(model = "m1", system = "s", newUserText = "hi", stream = true, maxTokens = 1), ImageStyle.DATA_URI),
        ).jsonObject
        assertTrue(streamBody["stream_options"]!!.jsonObject["include_usage"]!!.jsonPrimitive.content == "true")
        assertFalse(streamBody.containsKey("max_tokens"))   // OpenAI 推理系模型发 max_tokens 会 400

        val nonStream = json.parseToJsonElement(
            provider.buildRequestBody(ChatRequest(model = "m1", system = "s", newUserText = "hi", stream = false, maxTokens = 1), ImageStyle.DATA_URI),
        ).jsonObject
        // 仅流式路径携带 stream_options(opencode 同款:非流式发会被部分网关 400)
        assertFalse(nonStream.containsKey("stream_options"))
    }

    @Test
    fun `finish_reason收尾无DONE-合法完成不误报中断`() {
        // 真机 MiMo 实证:发完 finish_reason 即关流,无 [DONE](pi-ai hasFinishReason 语义)
        val srv = MiniHttp(
            Resp("200 OK", sse(
                """{"choices":[{"delta":{"content":"答"}}]}""",
                """{"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":8,"completion_tokens":3}}""",
                // 到此为止,服务端关闭
            )),
        )
        try {
            val events = chat(OpenAiCompatProvider(providerOf(srv.baseUrl), { "k" }))
            assertEquals("答", events.filterIsInstance<ChatEvent.Delta>().joinToString("") { it.text })
            assertTrue(events.none { it is ChatEvent.Error })          // 不得误报中断
            val done = events.filterIsInstance<ChatEvent.Done>().first()
            assertEquals("stop", done.finishReason)
            assertTrue(events.any { it is ChatEvent.Usage && it.promptTokens == 8 && it.completionTokens == 3 })
        } finally { srv.close() }
    }

    @Test
    fun `finish_reason后usage-only帧再关流-usage不丢`() {
        val srv = MiniHttp(
            Resp("200 OK", sse(
                """{"choices":[{"delta":{"content":"好"}}]}""",
                """{"choices":[{"delta":{},"finish_reason":"stop"}]}""",
                """{"choices":[],"usage":{"prompt_tokens":99,"completion_tokens":9}}""",
                // 无 [DONE] 关流
            )),
        )
        try {
            val events = chat(OpenAiCompatProvider(providerOf(srv.baseUrl), { "k" }))
            assertTrue(events.none { it is ChatEvent.Error })
            assertTrue(events.any { it is ChatEvent.Usage && it.promptTokens == 99 })
            assertTrue(events.filterIsInstance<ChatEvent.Done>().isNotEmpty())
        } finally { srv.close() }
    }

    @Test
    fun `content_filter收尾-标失败文本保留`() {
        val srv = MiniHttp(
            Resp("200 OK", sse(
                """{"choices":[{"delta":{"content":"部分"}}]}""",
                """{"choices":[{"delta":{},"finish_reason":"content_filter"}]}""",
            )),
        )
        try {
            val events = chat(OpenAiCompatProvider(providerOf(srv.baseUrl), { "k" })
            )
            val err = events.filterIsInstance<ChatEvent.Error>().firstOrNull()?.error
            assertTrue(err is AgentError.Other && err.detail.contains("content_filter"))
            assertTrue(events.none { it is ChatEvent.Done })
        } finally { srv.close() }
    }

    @Test
    fun `思考帧流过-先思考后作答-断流含思考不自动重试`() {
        // a) 正常:reasoning_content 流 → Thinking;content → Delta
        val srv = MiniHttp(
            Resp("200 OK", sse(
                """{"choices":[{"delta":{"reasoning_content":"盘算"}}]}""",
                """{"choices":[{"delta":{"reasoning_content":"一下"}}]}""",
                """{"choices":[{"delta":{"content":"答"}}]}""",
                "[DONE]",
            )),
        )
        try {
            val events = chat(OpenAiCompatProvider(providerOf(srv.baseUrl), { "k" }))
            assertEquals("盘算一下", events.filterIsInstance<ChatEvent.Thinking>().joinToString("") { it.text })
            assertEquals("答", events.filterIsInstance<ChatEvent.Delta>().joinToString("") { it.text })
        } finally { srv.close() }

        // b) 只有思考就断流:思考=已计费内容 → StreamBroken(不伪完成)
        val srv2 = MiniHttp(
            Resp("200 OK", sse(
                """{"choices":[{"delta":{"reasoning_content":"想了一半"}}]}""",
                // 断
            )),
        )
        try {
            val events = chat(OpenAiCompatProvider(providerOf(srv2.baseUrl), { "k" }))
            assertTrue(events.any { it is ChatEvent.Error && (it as ChatEvent.Error).error is AgentError.StreamBroken })
            assertTrue(events.none { it is ChatEvent.Done })
        } finally { srv2.close() }
    }

    @Test
    fun `Anthropic thinking_delta进Thinking`() {
        val srv = MiniHttp(
            Resp("200 OK", sse(
                """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"先想"}}""",
                """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"答"}}""",
                """{"type":"message_stop"}""",
            )),
        )
        try {
            val p = providerOf(srv.baseUrl).copy(protocol = Protocol.ANTHROPIC, activeModelId = "claude-x")
            val events = chat(AnthropicProvider(p, { "k" }))
            assertEquals("先想", events.filterIsInstance<ChatEvent.Thinking>().joinToString("") { it.text })
            assertEquals("答", events.filterIsInstance<ChatEvent.Delta>().joinToString("") { it.text })
        } finally { srv.close() }
    }

    @Test
    fun `Anthropic原生流-事件帧与usage拼合`() {
        val srv = MiniHttp(
            Resp("200 OK", sse(
                """{"type":"message_start","message":{"usage":{"input_tokens":7,"output_tokens":1}}}""",
                """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"题"}}""",
                """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":5}}""",
                """{"type":"message_stop"}""",
            )),
        )
        try {
            val p = providerOf(srv.baseUrl).copy(protocol = Protocol.ANTHROPIC, activeModelId = "claude-x")
            val events = chat(AnthropicProvider(p, { "k" }))
            assertEquals("题", events.filterIsInstance<ChatEvent.Delta>().joinToString("") { it.text })
            val u = events.filterIsInstance<ChatEvent.Usage>().first()
            assertEquals(7 to 5, u.promptTokens to u.completionTokens)
            assertTrue(events.dropLast(1).last() is ChatEvent.Done)
        } finally { srv.close() }
    }
}
