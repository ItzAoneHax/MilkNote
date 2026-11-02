package com.milknote.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Anthropic Messages 原生协议(§3.7,A2):请求体/流帧/错误/端点/工厂分发 */
class AnthropicProviderTest {

    private val profile = ProviderProfile(
        id = "t", title = "t", baseUrl = "https://api.anthropic.com/v1", apiKeyAlias = "a",
        models = listOf(ModelInfo("claude-sonnet-5-5")), activeModelId = "claude-sonnet-5-5",
        protocol = Protocol.ANTHROPIC,
    )
    private val provider = AnthropicProvider(profile, { "sk-ant-test" })
    private val json = kotlinx.serialization.json.Json

    @Test
    fun `请求体-system字符串-max_tokens必填-图先文后-base64块`() = runTest {
        val req = ChatRequest(
            model = "claude-sonnet-5-5", system = "你是讲解老师",
            newUserText = "[图1] 请讲解",
            images = listOf("AAA", "QkI="),
            imageMediaTypes = listOf("image/png", "image/jpeg"),
        )
        val body = json.parseToJsonElement(provider.buildBody(req)).jsonObject

        assertEquals("claude-sonnet-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("你是讲解老师", body["system"]!!.jsonPrimitive.content)
        assertEquals(AnthropicProvider.DEFAULT_MAX_TOKENS, body["max_tokens"]!!.jsonPrimitive.content.toInt())   // 必填有默认
        assertEquals(true, body["stream"]!!.jsonPrimitive.content == "true")

        val last = body["messages"]!!.jsonArray.last().jsonObject
        assertEquals("user", last["role"]!!.jsonPrimitive.content)
        val blocks = last["content"]!!.jsonArray
        assertEquals(3, blocks.size)   // 图1、图2、文
        val img1 = blocks[0].jsonObject
        assertEquals("image", img1["type"]!!.jsonPrimitive.content)
        val src = img1["source"]!!.jsonObject
        assertEquals("base64", src["type"]!!.jsonPrimitive.content)
        assertEquals("image/png", src["media_type"]!!.jsonPrimitive.content)
        assertEquals("AAA", src["data"]!!.jsonPrimitive.content)
        // jpeg MIME 跟随真实编码字节
        assertEquals("image/jpeg", blocks[1].jsonObject["source"]!!.jsonObject["media_type"]!!.jsonPrimitive.content)
        assertEquals("text", blocks[2].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `流帧解析-delta-usage拼合-stop`() {
        assertEquals(0, provider.parseFrame("""{"type":"message_start","message":{"usage":{"input_tokens":812,"output_tokens":1}}}""").size)
        val deltas = provider.parseFrame("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"你好"}}""")
        assertEquals("你好", (deltas[0] as ChatEvent.Delta).text)

        val usage = provider.parseFrame("""{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":96}}""")
        val u = usage.filterIsInstance<ChatEvent.Usage>().first()
        assertEquals(812, u.promptTokens)   // message_start 的 input 与 message_delta 的 output 拼合
        assertEquals(96, u.completionTokens)

        val stop = provider.parseFrame("""{"type":"message_stop"}""")
        assertTrue(stop[0] is ChatEvent.Done)

        // ping/内容块起止帧忽略
        assertEquals(0, provider.parseFrame("""{"type":"ping"}""").size)
        assertEquals(0, provider.parseFrame("""{"type":"content_block_start","index":0,"content_block":{"type":"text"}}""").size)
    }

    @Test
    fun `流内错误帧映射`() {
        val overloaded = provider.parseFrame("""{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""")
        assertTrue((overloaded[0] as ChatEvent.Error).error is AgentError.Server)

        val ratelimit = provider.parseFrame("""{"type":"error","error":{"type":"rate_limit_error","message":"slow down"}}""")
        assertTrue((ratelimit[0] as ChatEvent.Error).error is AgentError.RateLimit)
    }

    @Test
    fun `HTTP错误-529过载-401鉴权`() {
        assertTrue(provider.mapHttpError(529, "") is AgentError.Server)
        assertTrue(provider.mapHttpError(401, """{"type":"error","error":{"type":"authentication_error"}}""") is AgentError.Auth)
        assertTrue(ErrorMapping.fromHttp(529, "") is AgentError.Server)
    }

    @Test
    fun `端点-base三种写法归一`() {
        val bare = AnthropicProvider(profile.copy(baseUrl = "https://api.anthropic.com"), { null })
        assertEquals("https://api.anthropic.com/v1/messages", bare.endpoint("chat", sse = true))
        // 中转站前缀(如 DeepSeek anthropic / 各家 Claude 系中转)
        val relay = AnthropicProvider(profile.copy(baseUrl = "https://api.deepseek.com/anthropic"), { null })
        assertEquals("https://api.deepseek.com/anthropic/v1/messages", relay.endpoint("chat", sse = true))
        assertEquals("https://api.anthropic.com/v1/models", provider.endpoint("models", sse = false))
    }

    @Test
    fun `工厂按protocol分发`() {
        assertTrue(ProviderFactory.create(profile, { null }) is AnthropicProvider)
        assertTrue(ProviderFactory.create(profile.copy(protocol = Protocol.OPENAI_COMPATIBLE), { null }) is OpenAiCompatProvider)
    }

    @Test
    fun `旧profiles无protocol字段解码为OpenAI兼容`() {
        val legacy = """{"profiles":[{"id":"x","title":"old","baseUrl":"https://x/v1","apiKeyAlias":"a",
            "models":[],"activeModelId":""}],"activeProfileId":"x"}""".replace("\n", "")
        val book = AgentStore(java.io.File("build/tmp/legacy-test-files"), java.io.File("build/tmp/legacy-test-nb"))
            .loadProfiles()   // 空 store,不走 legacy 字符串
        // 直接解码旧 JSON(kotlinx 对缺省字段取默认值,免迁移)
        val decoded = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<ProviderProfile>(
                """{"id":"x","title":"old","baseUrl":"https://x/v1","apiKeyAlias":"a","models":[],"activeModelId":""}""",
            )
        assertEquals(Protocol.OPENAI_COMPATIBLE, decoded.protocol)
    }
}
