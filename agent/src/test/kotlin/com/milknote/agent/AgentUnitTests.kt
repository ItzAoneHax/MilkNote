package com.milknote.agent

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SSE 解析(§14:正常帧/[DONE]/半行/多 data 行/粘包) */
class SseParserTest {

    @Test
    fun `基本帧与DONE`() {
        val p = SseParser()
        val out = p.feed("data: {\"a\":1}\n\ndata: [DONE]\n\n")
        assertEquals(listOf("{\"a\":1}", "[DONE]"), out)
    }

    @Test
    fun `跨chunk半行`() {
        val p = SseParser()
        val a = p.feed("data: {\"ch")
        assertEquals(0, a.size)
        val b = p.feed("unk\":1}\n")
        assertEquals(0, b.size)   // 未遇空行,事件未闭合
        val c = p.feed("\ndata: [DONE]\n\n")
        assertEquals(listOf("{\"chunk\":1}", "[DONE]"), c)
    }

    @Test
    fun `多data行拼接`() {
        val p = SseParser()
        val out = p.feed("data: line1\ndata: line2\n\ndata: [DONE]\n\n")
        assertEquals(listOf("line1\nline2", "[DONE]"), out)
    }

    @Test
    fun `CRLF与注释与event行`() {
        val p = SseParser()
        val out = p.feed(": keep-alive\r\nevent: message\r\ndata: x\r\n\r\ndata: [DONE]\r\n\r\n")
        assertEquals(listOf("x", "[DONE]"), out)
    }

    @Test
    fun `流尾无空行finish兜底`() {
        val p = SseParser()
        assertEquals(0, p.feed("data: tail").size)
        assertEquals(listOf("tail"), p.finish())
    }

    @Test
    fun `厂商脏行跳过`() {
        val p = SseParser()
        val out = p.feed("\n\nsome garbage\n\ndata: ok\n\n")
        // 非 data 行不产生事件;垃圾行被忽略
        assertEquals(listOf("ok"), out)
    }
}

/** HTTP → AgentError 映射(§14 错误矩阵全覆盖) */
class ErrorMappingTest {

    @Test
    fun `鉴权失败`() {
        val e = ErrorMapping.fromHttp(401, "unauthorized") as AgentError.Auth
        assertTrue(e.detail.contains("Key"))
        assertTrue(ErrorMapping.fromHttp(403, "") is AgentError.Auth)
    }

    @Test
    fun `限流与服务器`() {
        assertTrue(ErrorMapping.fromHttp(429, "rate") is AgentError.RateLimit)
        assertTrue(ErrorMapping.fromHttp(500, "") is AgentError.Server)
        assertTrue(ErrorMapping.fromHttp(503, "") is AgentError.Server)
    }

    @Test
    fun `404模型下线`() {
        assertTrue(ErrorMapping.fromHttp(404, "model not found") is AgentError.NotFound)
    }

    @Test
    fun `非视觉模型兜底`() {
        val e = ErrorMapping.fromHttp(400, "This model does not support image input")
        assertTrue(e is AgentError.ModelNotVision)
    }

    @Test
    fun `图片格式可重试场景`() {
        // image_url 格式问题而非能力问题 → Other(触发 imageStyle 自适应)
        val e = ErrorMapping.fromHttp(400, "invalid image_url format: must be data uri")
        assertTrue(e is AgentError.Other)
        assertTrue(ErrorMapping.looksLikeImageFormat("must be data uri with base64"))
        assertTrue(!ErrorMapping.looksLikeImageFormat("model does not support image input"))
    }

    @Test
    fun `上下文过大`() {
        assertTrue(ErrorMapping.fromHttp(413, "") is AgentError.ContextTooLarge)
        assertTrue(ErrorMapping.fromHttp(400, "request payload too large") is AgentError.ContextTooLarge)
    }

    @Test
    fun `网络异常`() {
        assertTrue(ErrorMapping.fromException(java.net.SocketTimeoutException()) is AgentError.Timeout)
        assertTrue(ErrorMapping.fromException(java.net.UnknownHostException("x")) is AgentError.Network)
    }
}

/** 上下文预算(§14:多图截断顺序/缩图决策/token 估算) */
class ImageBudgetTest {

    @Test
    fun `截断保前`() {
        val (kept, dropped) = ImageBudget.truncate(listOf(1, 2, 3, 4, 5), 3)
        assertEquals(listOf(1, 2, 3), kept)
        assertEquals(2, dropped)
    }

    @Test
    fun `模型上限与绝对上限取小`() {
        assertEquals(4, ImageBudget.maxImagesFor(ModelInfo("x", maxImages = 4)))
        assertEquals(10, ImageBudget.maxImagesFor(ModelInfo("x", maxImages = 32)))
        assertEquals(4, ImageBudget.maxImagesFor(null))   // 无模型信息 → 保守 4
    }

    @Test
    fun `整章截断封顶8`() {
        assertEquals(8, ImageBudget.chapterPageCount(totalPages = 20, imagesUsed = 1, max = 10))
        assertEquals(4, ImageBudget.chapterPageCount(totalPages = 5, imagesUsed = 1, max = 10))   // 除当前页外 4 张
        assertEquals(0, ImageBudget.chapterPageCount(totalPages = 5, imagesUsed = 10, max = 10))
    }

    @Test
    fun `token估算与JPEG降级`() {
        assertEquals(1100, ImageBudget.estTokens(1, 0))
        assertTrue(ImageBudget.estTokens(2, 320) >= 2300)
        assertTrue(ImageBudget.shouldFallbackToJpeg(ImageBudget.TARGET_BYTES + 1))
        assertTrue(!ImageBudget.shouldFallbackToJpeg(1024))
    }

    @Test
    fun `逻辑名`() {
        assertEquals("snip_1246×742.png", ImageBudget.logicalNameSnip(1246, 742))
    }
}

/** 意图/提示词(§14:意图模板、历史窗口截断保首条、图先文后) */
class PromptEngineTest {

    @Test
    fun `六意图齐备且各有契约`() {
        assertEquals(6, Intents.all.size)
        Intents.all.forEach { s ->
            assertTrue(PromptEngine.systemPrompt(s).contains(Intents.BASE_SYSTEM))
            assertTrue(PromptEngine.systemPrompt(s).contains(s.systemContract))
        }
    }

    @Test
    fun `模板占位替换`() {
        val t = PromptEngine.userText(Intents.byId(Intents.Id.EXPLAIN), "第二问也讲下")
        assertTrue(t.contains("讲解"))
        assertTrue(t.contains("第二问也讲下"))
        val bare = PromptEngine.userText(Intents.byId(Intents.Id.EXPLAIN), "")
        assertTrue(!bare.contains("用户补充"))
    }

    @Test
    fun `历史窗口截断保首条user`() {
        val msgs = (1..10).map { if (it == 1) "user" to "题目本体" else (if (it % 2 == 0) "assistant" else "user") to "m$it" }
        val w = PromptEngine.windowedHistory(msgs)
        assertTrue(w.size <= PromptEngine.HISTORY_WINDOW)
        assertEquals("题目本体", w.first().content)
    }

    @Test
    fun `图先文后`() {
        assertEquals("[图1] [图2] 请讲解", PromptEngine.assembleUserContent("请讲解", 2))
        assertEquals("请讲解", PromptEngine.assembleUserContent("请讲解", 0))
    }
}

/** SSE 帧 JSON 解析 + 请求体组装(§3.5) */
class ProviderWireTest {

    private val profile = ProviderProfile(
        id = "t", title = "t", baseUrl = "https://x.example/v1", apiKeyAlias = "a",
        models = listOf(ModelInfo("m1")), activeModelId = "m1",
    )
    private val provider = OpenAiCompatProvider(profile, { "sk-test" })
    private val json = kotlinx.serialization.json.Json

    @Test
    fun `delta与usage帧`() {
        val frames = provider.parseFrame("""{"choices":[{"delta":{"content":"你好"}}]}""")
        assertEquals(1, frames.size)
        assertEquals("你好", (frames[0] as ChatEvent.Delta).text)

        val usage = provider.parseFrame("""{"choices":[],"usage":{"prompt_tokens":120,"completion_tokens":8}}""")
        val u = usage.filterIsInstance<ChatEvent.Usage>().first()
        assertEquals(120, u.promptTokens)
        assertEquals(8, u.completionTokens)
    }

    @Test
    fun `DONE与脏帧`() {
        assertEquals(1, provider.parseFrame("[DONE]").size)
        assertEquals(0, provider.parseFrame("not-json").size)
        assertEquals(0, provider.parseFrame("").size)
    }

    @Test
    fun `错误帧映射ModelNotVision`() {
        val frames = provider.parseFrame("""{"error":{"message":"model does not support image input"}}""")
        val e = frames.filterIsInstance<ChatEvent.Error>().first()
        assertTrue(e.error is AgentError.ModelNotVision)
    }

    @Test
    fun `请求体图先文后与两种imageStyle`() = kotlinx.coroutines.test.runTest {
        val req = ChatRequest(
            model = "m1", system = "sys", history = listOf(WireMessage("user", "h1")),
            newUserText = "问", images = listOf("AAA", "BBB"),
        )
        val parse: (String) -> kotlinx.serialization.json.JsonObject =
            { json.parseToJsonElement(it).jsonObject }

        val dataUri = parse(provider.buildRequestBody(req, ImageStyle.DATA_URI))
        assertEquals("m1", dataUri["model"]!!.jsonPrimitive.content)
        assertEquals("true", dataUri["stream"]!!.jsonPrimitive.content)

        val messages = dataUri["messages"]!!.jsonArray
        assertEquals(3, messages.size)   // system + history + 新 user
        val parts = messages.last().jsonObject["content"]!!.jsonArray
        // 图先文后:前两个 image_url,最后 text
        assertEquals("image_url", parts[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("text", parts[2].jsonObject["type"]!!.jsonPrimitive.content)
        val url1 = parts[0].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
        assertEquals("data:image/png;base64,AAA", url1)

        val raw = parse(provider.buildRequestBody(req, ImageStyle.RAW_BASE64))
        val rawParts = raw["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray
        val rawUrl = rawParts[0].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
        assertEquals("AAA", rawUrl)
    }
}
