package com.milknote.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

// =============================================================================
// Skills & Tools 可扩展架构 + 三协议 Tool Calling + 确认循环
// =============================================================================

/** 纯 Kotlin 计算引擎:算术/变量/函数/阶乘/精度/非法输入 */
class MathEvaluatorTest {

    private fun eval(expression: String, precision: Int = 10) =
        MathEvaluator.evaluate(expression, precision)

    @Test
    fun `四则运算与优先级`() {
        assertEquals("7", eval("1+2*3").formatted)
        assertEquals("14", eval("2(3+4)").formatted)
        assertEquals("2", eval("(1+3)/2").formatted)
        assertEquals("1", eval("7%3").formatted)
    }

    @Test
    fun `幂右结合与一元负号`() {
        assertEquals("512", eval("2^3^2").formatted)
        assertEquals("-4", eval("-2^2").formatted)
        assertEquals("4", eval("(-2)^2").formatted)
        assertEquals("0.5", eval("2^-1").formatted)
    }

    @Test
    fun `阶乘与常量函数`() {
        assertEquals("120", eval("5!").formatted)
        assertEquals("1", eval("0!").formatted)
        assertEquals("180", eval("deg(pi)").formatted)
        assertEquals("3", eval("log(1000)").formatted)
        assertEquals("3", eval("log(8, 2)").formatted)
        assertEquals("10", eval("comb(5,2)").formatted)
        assertEquals("20", eval("perm(5,2)").formatted)
        assertEquals("6", eval("gcd(12,18)").formatted)
        assertEquals("2.5", eval("avg(1,2,3,4)").formatted)
        assertEquals("-1", eval("sign(-9)").formatted)
    }

    @Test
    fun `变量多语句返回最后表达式`() {
        val r = eval("a = 3; b = 4; sqrt(a^2 + b^2)")
        assertEquals("5", r.formatted)
        assertEquals(3.0, r.variables["a"]!!, 1e-9)
        assertEquals(4.0, r.variables["b"]!!, 1e-9)
        // 换行同样分隔语句
        assertEquals("9", eval("x=2\ny=3\nx*y+3").formatted)
    }

    @Test
    fun `精度设置与浮点尾差`() {
        assertEquals("0.3", eval("0.1+0.2").formatted)
        assertEquals("0.3333", eval("1/3", 4).formatted)
        assertEquals("6.2831853072", eval("2pi").formatted)
        assertEquals("1000000", eval("1e6").formatted)
    }

    @Test
    fun `非法表达式报错`() {
        assertThrows(MathEvaluator.EvaluationException::class.java) { eval("1+") }
        assertThrows(MathEvaluator.EvaluationException::class.java) { eval("x+1") }
        assertThrows(MathEvaluator.EvaluationException::class.java) { eval("sqrt(-1)") }
        assertThrows(MathEvaluator.EvaluationException::class.java) { eval("(-1)!") }
        assertThrows(MathEvaluator.EvaluationException::class.java) { eval("2.5!") }
        assertThrows(MathEvaluator.EvaluationException::class.java) { eval("1/0") }
        assertThrows(MathEvaluator.EvaluationException::class.java) { eval("") }
        assertThrows(MathEvaluator.EvaluationException::class.java) { eval("unknown_fn(2)") }
    }
}

/** 全局提示词 / 意图契约 / 技能目录渐进注入 */
class PromptSkillToolContractTest {

    @Test
    fun `全局提示词严格约束公式格式`() {
        val base = Intents.BASE_SYSTEM
        assertTrue(base.contains("\$...\$"))
        assertTrue(base.contains("\$\$...\$\$"))
        assertTrue(base.contains("\\text{...}"))
        assertTrue(base.contains("严禁"))
        assertTrue(base.contains("```latex"))
        assertTrue(base.contains("aligned"))
        assertFalse("不得再出现诱导围栏的「代码块」措辞", base.contains("代码块"))
    }

    @Test
    fun `转LaTeX契约禁止围栏与反引号`() {
        val contract = Intents.byId(Intents.Id.LATEX).systemContract
        assertFalse(contract.contains("代码块"))
        assertTrue(contract.contains("独占行"))
        assertTrue(contract.contains("反引号"))
        assertTrue(contract.contains("禁止"))
        assertTrue(contract.contains("```latex"))
        assertTrue(contract.contains("\$\$"))
    }

    @Test
    fun `技能目录渐进注入且关闭技能不出现`() {
        val enabled = AgentSkill("s1", "技能一", "摘要一", "正文一")
        val disabled = AgentSkill("s2", "技能二", "摘要二", "正文二", enabled = false)
        val tools = listOf(WireToolDef("read_skill", "读技能", emptyList()))
        val prompt = PromptEngine.systemPrompt(
            Intents.byId(Intents.Id.EXPLAIN),
            listOf(enabled, disabled),
            tools,
        )
        assertTrue(prompt.contains("s1"))
        assertTrue(prompt.contains("技能一"))
        assertTrue(prompt.contains("摘要一"))
        assertTrue(prompt.contains("read_skill(skill_id)"))
        assertTrue(prompt.contains("工具使用原则"))
        assertFalse("目录只给 id/name/summary,不给正文", prompt.contains("正文一"))
        assertFalse(prompt.contains("s2"))
    }

    @Test
    fun `无工具时提示词与单参版本一致`() {
        val spec = Intents.byId(Intents.Id.EXPLAIN)
        assertEquals(
            PromptEngine.systemPrompt(spec),
            PromptEngine.systemPrompt(spec, emptyList(), emptyList()),
        )
    }
}

/** skills.json / tools.json 持久化与内置技能补齐 */
class AgentStoreSkillsToolsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `首次加载补齐内置技能并落盘`() {
        val files = tmp.newFolder()
        val store = AgentStore(files, tmp.newFolder())

        val skills = store.loadSkills()

        assertEquals(BuiltinSkills.all.map { it.id }, skills.map { it.id })
        assertTrue(skills.all { it.builtin && it.enabled })
        assertTrue(File(files, "agent/skills.json").exists())
    }

    @Test
    fun `内置技能编辑与关闭在重新加载后保留`() {
        val files = tmp.newFolder()
        val store = AgentStore(files, tmp.newFolder())
        val edited = store.loadSkills().map {
            if (it.id == "math_solution_steps") it.copy(content = "自定义正文", enabled = false) else it
        }
        store.saveSkills(edited)

        val reopened = AgentStore(files, tmp.newFolder()).loadSkills()
        val math = reopened.first { it.id == "math_solution_steps" }

        assertEquals("自定义正文", math.content)
        assertFalse(math.enabled)
        assertTrue(math.builtin)
        assertEquals(3, reopened.count { it.builtin })
    }

    @Test
    fun `缺失的内置技能会重新补齐`() {
        val files = tmp.newFolder()
        val store = AgentStore(files, tmp.newFolder())
        store.saveSkills(store.loadSkills().filterNot { it.id == "formula_sheet" })

        val reopened = store.loadSkills()

        assertTrue(reopened.any { it.id == "formula_sheet" && it.builtin })
    }

    @Test
    fun `用户技能与工具配置读写`() {
        val files = tmp.newFolder()
        val store = AgentStore(files, tmp.newFolder())
        store.saveSkills(
            store.loadSkills() + AgentSkill("my_skill", "我的技能", "摘要", "正文"),
        )
        store.saveToolConfigs(
            mapOf(
                "calculate" to ToolConfig(
                    id = "calculate",
                    enabled = false,
                    displayName = "算一算",
                    settings = mapOf("precision" to "6"),
                ),
            ),
        )

        val reopened = AgentStore(files, tmp.newFolder())
        val skills = reopened.loadSkills()
        val configs = reopened.loadToolConfigs()

        assertTrue(skills.any { it.id == "my_skill" && !it.builtin })
        assertEquals(4, skills.size)
        assertFalse(configs["calculate"]!!.enabled)
        assertEquals("算一算", configs["calculate"]!!.displayName)
        assertEquals("6", configs["calculate"]!!.settings["precision"])
        assertTrue(File(files, "agent/tools.json").exists())
    }
}

/** 三协议 tools 请求体 / continuationMessages / 流式 ToolCallReady */
class ProviderToolWireTest {

    private val json = Json

    private val openAiProfile = ProviderProfile(
        id = "t", title = "t", baseUrl = "https://x.example/v1", apiKeyAlias = "a",
        models = listOf(ModelInfo("m1")), activeModelId = "m1",
    )
    private val anthropicProfile = openAiProfile.copy(
        baseUrl = "https://api.anthropic.com/v1",
        models = listOf(ModelInfo("claude-x")),
        activeModelId = "claude-x",
        protocol = Protocol.ANTHROPIC,
    )
    private val responsesProfile = openAiProfile.copy(protocol = Protocol.OPENAI_RESPONSES)

    private val calculateTool = WireToolDef(
        "calculate", "数学计算",
        listOf(ToolParameter("expression", "string", "表达式", required = true)),
    )

    // ---------------- OpenAI ChatCompletions ----------------

    @Test
    fun `OpenAI请求体带tools与tool续写消息`() {
        val provider = OpenAiCompatProvider(openAiProfile, { "k" })
        val req = ChatRequest(
            model = "m1", system = "s", newUserText = "问",
            tools = listOf(calculateTool),
            continuationMessages = listOf(
                WireMessage("assistant", "", toolCalls = listOf(ToolCall("c1", "calculate", """{"expression":"1+1"}"""))),
                WireMessage("tool", "结果: 2", toolCallId = "c1", toolName = "calculate"),
            ),
        )

        val body = json.parseToJsonElement(provider.buildRequestBody(req, ImageStyle.DATA_URI)).jsonObject

        val tool = body["tools"]!!.jsonArray[0].jsonObject
        assertEquals("function", tool["type"]!!.jsonPrimitive.content)
        val fn = tool["function"]!!.jsonObject
        assertEquals("calculate", fn["name"]!!.jsonPrimitive.content)
        val expr = fn["parameters"]!!.jsonObject["properties"]!!.jsonObject["expression"]!!.jsonObject
        assertEquals("string", expr["type"]!!.jsonPrimitive.content)
        assertEquals("expression", fn["parameters"]!!.jsonObject["required"]!!.jsonArray[0].jsonPrimitive.content)

        val messages = body["messages"]!!.jsonArray
        val assistant = messages[messages.size - 2].jsonObject
        assertEquals("assistant", assistant["role"]!!.jsonPrimitive.content)
        val call = assistant["tool_calls"]!!.jsonArray[0].jsonObject
        assertEquals("c1", call["id"]!!.jsonPrimitive.content)
        assertEquals("function", call["type"]!!.jsonPrimitive.content)
        assertEquals("calculate", call["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)

        val toolMsg = messages.last().jsonObject
        assertEquals("tool", toolMsg["role"]!!.jsonPrimitive.content)
        assertEquals("c1", toolMsg["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("结果: 2", toolMsg["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `OpenAI流式tool_calls按index聚合且flush只发一次`() {
        val provider = OpenAiCompatProvider(openAiProfile, { "k" })
        provider.parseFrame(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"calculate","arguments":"{\"expr"}}]}}]}""",
        )
        provider.parseFrame(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"ession\":\"1+1\"}"}}]},"finish_reason":"tool_calls"}]}""",
        )

        val flushed = provider.flushStreamEvents().filterIsInstance<ChatEvent.ToolCallReady>()
        assertEquals(1, flushed.size)
        assertEquals("call_1", flushed[0].call.id)
        assertEquals("calculate", flushed[0].call.name)
        assertEquals("""{"expression":"1+1"}""", flushed[0].call.argumentsJson)
        assertTrue("二次 flush 不得重复", provider.flushStreamEvents().isEmpty())

        provider.parseFrame(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_2","function":{"name":"x","arguments":"{}"}}]}}]}""",
        )
        provider.resetStreamState()
        assertTrue(provider.flushStreamEvents().isEmpty())
    }

    @Test
    fun `OpenAI并行工具调用按元素index聚合而非数组下标`() {
        val provider = OpenAiCompatProvider(openAiProfile, { "k" })
        // 并行调用时后续帧常常只含一个元素但 index=1;按下标会把参数拼进第 0 个调用
        val frames = listOf(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_a","function":{"name":"calculate","arguments":"{\"expression\":"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":1,"id":"call_b","function":{"name":"web_search","arguments":"{\"query\":"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"1+1\"}"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":1,"function":{"arguments":"\"kotlin\"}"}}]},"finish_reason":"tool_calls"}]}""",
        )
        frames.forEach { provider.parseFrame(it) }

        val calls = provider.flushStreamEvents()
            .filterIsInstance<ChatEvent.ToolCallReady>()
            .map { it.call }

        assertEquals(2, calls.size)
        assertEquals("call_a", calls[0].id)
        assertEquals("calculate", calls[0].name)
        assertEquals("""{"expression":"1+1"}""", calls[0].argumentsJson)
        assertEquals("call_b", calls[1].id)
        assertEquals("web_search", calls[1].name)
        assertEquals("""{"query":"kotlin"}""", calls[1].argumentsJson)
    }

    @Test
    fun `OpenAI端到端-工具调用排在Done之前且请求体带tools`() = runBlocking {
        val frames = listOf(
            """{"choices":[{"delta":{"content":"先算"},"finish_reason":null}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"calculate","arguments":"{\"expression\":\"1+1\"}"}}]},"finish_reason":"tool_calls"}]}""",
            "[DONE]",
        )
        val server = RecordingSseServer(frames)
        try {
            val provider = OpenAiCompatProvider(openAiProfile.copy(baseUrl = server.baseUrl), { "k" })
            val events = provider.chat(
                ChatRequest(
                    model = "m1", system = "s", newUserText = "hi",
                    tools = listOf(calculateTool),
                ),
            ).toList()

            val callIndex = events.indexOfFirst { it is ChatEvent.ToolCallReady }
            val doneIndex = events.indexOfFirst { it is ChatEvent.Done }
            assertTrue("应有工具调用:$events", callIndex >= 0)
            assertTrue("工具调用必须排在 Done 之前", doneIndex > callIndex)
            assertEquals(1, events.count { it is ChatEvent.ToolCallReady })
            val call = (events[callIndex] as ChatEvent.ToolCallReady).call
            assertEquals("call_1", call.id)
            assertEquals("calculate", call.name)
            assertTrue(server.requestBody.contains("\"tools\""))
            assertTrue(server.requestBody.contains("\"calculate\""))
        } finally {
            server.close()
        }
    }

    // ---------------- Anthropic Messages ----------------

    @Test
    fun `Anthropic请求体tools与continuation块合并`() {
        val provider = AnthropicProvider(anthropicProfile, { "k" })
        val req = ChatRequest(
            model = "claude-x", system = "s", newUserText = "问",
            tools = listOf(WireToolDef("web_search", "联网搜索", emptyList())),
            continuationMessages = listOf(
                WireMessage("assistant", "先查", toolCalls = listOf(ToolCall("tu1", "web_search", """{"query":"x"}"""))),
                WireMessage("tool", "结果A", toolCallId = "tu1"),
                WireMessage("tool", "结果B", toolCallId = "tu1"),
            ),
        )

        val body = json.parseToJsonElement(provider.buildBody(req)).jsonObject

        val tool = body["tools"]!!.jsonArray[0].jsonObject
        assertEquals("web_search", tool["name"]!!.jsonPrimitive.content)
        assertEquals("object", tool["input_schema"]!!.jsonObject["type"]!!.jsonPrimitive.content)

        val messages = body["messages"]!!.jsonArray
        val assistant = messages[messages.size - 2].jsonObject
        assertEquals("assistant", assistant["role"]!!.jsonPrimitive.content)
        val blocks = assistant["content"]!!.jsonArray
        assertEquals("text", blocks[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("tool_use", blocks[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("tu1", blocks[1].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("x", blocks[1].jsonObject["input"]!!.jsonObject["query"]!!.jsonPrimitive.content)

        val results = messages.last().jsonObject
        assertEquals("user", results["role"]!!.jsonPrimitive.content)
        val resultBlocks = results["content"]!!.jsonArray
        assertEquals("连续的 tool 结果必须合并为一条 user", 2, resultBlocks.size)
        assertEquals("tool_result", resultBlocks[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("tu1", resultBlocks[0].jsonObject["tool_use_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Anthropic流式工具块-input_json_delta不当作正文`() {
        val provider = AnthropicProvider(anthropicProfile, { "k" })

        assertTrue(
            provider.parseFrame(
                """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"tu1","name":"calculate"}}""",
            ).isEmpty(),
        )
        assertTrue(
            provider.parseFrame(
                """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"expression\":"}}""",
            ).isEmpty(),
        )
        assertTrue(
            provider.parseFrame(
                """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"\"2+2\"}"}}""",
            ).isEmpty(),
        )

        val ready = provider.parseFrame("""{"type":"content_block_stop","index":1}""")
        val call = (ready.single() as ChatEvent.ToolCallReady).call
        assertEquals("tu1", call.id)
        assertEquals("calculate", call.name)
        assertEquals("""{"expression":"2+2"}""", call.argumentsJson)
        assertTrue(
            "重复 stop 不得重复发",
            provider.parseFrame("""{"type":"content_block_stop","index":1}""").isEmpty(),
        )
    }

    // ---------------- OpenAI Responses ----------------

    @Test
    fun `Responses请求体tools与function_call续写`() {
        val provider = OpenAiResponsesProvider(responsesProfile, { "k" })
        val req = ChatRequest(
            model = "m1", system = "s", newUserText = "问",
            tools = listOf(WireToolDef("read_skill", "读技能", emptyList())),
            continuationMessages = listOf(
                WireMessage("assistant", "看技能", toolCalls = listOf(ToolCall("fc1", "read_skill", """{"skill_id":"s1"}"""))),
                WireMessage("tool", "技能正文", toolCallId = "fc1"),
            ),
        )

        val body = json.parseToJsonElement(provider.buildBody(req)).jsonObject

        val tool = body["tools"]!!.jsonArray[0].jsonObject
        assertEquals("function", tool["type"]!!.jsonPrimitive.content)
        assertEquals("read_skill", tool["name"]!!.jsonPrimitive.content)

        val input = body["input"]!!.jsonArray
        val assistantMsg = input[input.size - 3].jsonObject
        assertEquals("message", assistantMsg["type"]!!.jsonPrimitive.content)
        assertEquals("assistant", assistantMsg["role"]!!.jsonPrimitive.content)
        assertEquals("output_text", assistantMsg["content"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("看技能", assistantMsg["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)

        val fn = input[input.size - 2].jsonObject
        assertEquals("function_call", fn["type"]!!.jsonPrimitive.content)
        assertEquals("fc1", fn["call_id"]!!.jsonPrimitive.content)
        assertEquals("read_skill", fn["name"]!!.jsonPrimitive.content)

        val output = input.last().jsonObject
        assertEquals("function_call_output", output["type"]!!.jsonPrimitive.content)
        assertEquals("fc1", output["call_id"]!!.jsonPrimitive.content)
        assertEquals("技能正文", output["output"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Responses流式函数调用事件去重`() {
        val provider = OpenAiResponsesProvider(responsesProfile, { "k" })

        assertTrue(
            provider.parseFrame(
                """{"type":"response.output_item.added","item":{"id":"fc_1","type":"function_call","call_id":"call_1","name":"calculate","arguments":""}}""",
            ).isEmpty(),
        )
        assertTrue(
            provider.parseFrame(
                """{"type":"response.function_call_arguments.delta","item_id":"fc_1","delta":"{\"expression\":"}""",
            ).isEmpty(),
        )

        val done = provider.parseFrame(
            """{"type":"response.function_call_arguments.done","item_id":"fc_1","arguments":"{\"expression\":\"2+2\"}"}""",
        )
        val call = (done.single() as ChatEvent.ToolCallReady).call
        assertEquals("call_1", call.id)
        assertEquals("calculate", call.name)
        assertEquals("""{"expression":"2+2"}""", call.argumentsJson)

        assertTrue(
            "output_item.done 重复到达不得再发",
            provider.parseFrame(
                """{"type":"response.output_item.done","item":{"id":"fc_1","type":"function_call","call_id":"call_1","name":"calculate","arguments":"{\"expression\":\"2+2\"}"}}""",
            ).isEmpty(),
        )
    }
}

/** 内置工具注册表与直接执行 */
class BuiltinToolsTest {

    private val registry = BuiltinTools.createDefaultRegistry()

    @Test
    fun `注册九个工具与确认默认值`() {
        assertEquals(
            listOf(
                "web_search", "calculate", "add_page", "delete_page", "insert_markdown",
                "list_page_elements", "modify_page_element", "read_skill", "create_skill",
            ),
            registry.allSpecs().map { it.id },
        )
        assertTrue(registry.get("delete_page")!!.spec.defaultRequireConfirmation)
        assertTrue(registry.get("modify_page_element")!!.spec.defaultRequireConfirmation)
        assertFalse(registry.get("calculate")!!.spec.defaultRequireConfirmation)
        assertFalse(registry.get("web_search")!!.spec.defaultRequireConfirmation)
    }

    @Test
    fun `开关与文案覆盖生效`() {
        val spec = registry.get("calculate")!!.spec
        val off = registry.effectiveConfig(spec, mapOf("calculate" to ToolConfig("calculate", enabled = false)))
        assertFalse(off.enabled)
        assertTrue(
            registry.effectiveWireTools(mapOf("calculate" to ToolConfig("calculate", enabled = false)))
                .none { it.name == "calculate" },
        )

        val confirm = registry.effectiveConfig(
            spec,
            mapOf("calculate" to ToolConfig("calculate", requireConfirmation = true)),
        )
        assertTrue(registry.requiresConfirmation(spec, confirm))
        assertEquals(
            "算一算",
            registry.effectiveDisplayName(spec, ToolConfig("calculate", displayName = "算一算")),
        )
    }

    @Test
    fun `calculate执行成功与失败`() = runTest {
        val tool = registry.get("calculate")!!
        val ok = tool.execute(ToolArgs.parse("""{"expression":"sqrt(9)+1"}"""), ctx())
        assertTrue(ok.ok)
        assertTrue(ok.output.contains("4"))

        val bad = tool.execute(ToolArgs.parse("""{"expression":"1+"}"""), ctx())
        assertFalse(bad.ok)
    }

    @Test
    fun `delete_page解析页码并描述动作`() = runTest {
        val tool = registry.get("delete_page")!!
        val ws = FakeWorkspace()
        val context = ctx(workspace = ws)

        assertEquals("删除当前页 (p0001)", tool.describeAction(ToolArgs.parse("{}"), context))
        assertEquals("删除第 2 页", tool.describeAction(ToolArgs.parse("""{"page_number":2}"""), context))
        assertEquals("删除页面 p0009", tool.describeAction(ToolArgs.parse("""{"page_id":"p0009"}"""), context))

        val result = tool.execute(ToolArgs.parse("""{"page_number":2}"""), context)
        assertTrue(result.ok)
        assertEquals(listOf("p0002"), ws.deleted)
        assertTrue(result.output.contains("第 2 页"))
    }

    @Test
    fun `insert_markdown按比例换算像素`() = runTest {
        val ws = FakeWorkspace()
        val result = registry.get("insert_markdown")!!.execute(
            ToolArgs.parse("""{"markdown":"# 标题","x":0.1,"y":0.2,"width":0.5}"""),
            ctx(workspace = ws),
        )
        assertTrue(result.ok)
        val card = ws.lastCard!!
        assertEquals("# 标题", card.first)
        assertEquals(100f, card.second, 0.01f)
        assertEquals(280f, card.third, 0.01f)
        assertEquals(500f, ws.lastCardWidth!!, 0.01f)
    }

    @Test
    fun `list_page_elements返回元素与尺寸`() = runTest {
        val result = registry.get("list_page_elements")!!.execute(
            ToolArgs.parse("{}"),
            ctx(workspace = FakeWorkspace()),
        )
        assertTrue(result.ok)
        assertTrue(result.output.contains("1000×1400"))
        assertTrue(result.output.contains("c1"))
        assertTrue(result.output.contains("预览"))
    }

    @Test
    fun `modify_page_element按比例移动`() = runTest {
        val ws = FakeWorkspace()
        val result = registry.get("modify_page_element")!!.execute(
            ToolArgs.parse("""{"action":"move","element_id":"c1","x":0.5,"y":0.25}"""),
            ctx(workspace = ws),
        )
        assertTrue(result.ok)
        val move = ws.lastMove!!
        assertEquals("c1", move.first)
        assertEquals(500f, move.second!!, 0.01f)
        assertEquals(350f, move.third!!, 0.01f)
    }

    @Test
    fun `read_skill读正文-create_skill生成安全id`() = runTest {
        val store = FakeSkillStore()
        val context = ctx(skills = store)

        val read = registry.get("read_skill")!!.execute(
            ToolArgs.parse("""{"skill_id":"cornell_notes"}"""),
            context,
        )
        assertTrue(read.ok)
        assertTrue(read.output.contains("康奈尔笔记整理"))
        assertTrue(read.output.contains("线索栏"))

        val created = registry.get("create_skill")!!.execute(
            ToolArgs.parse("""{"name":"Review Plan","summary":"复习计划","content":"正文"}"""),
            context,
        )
        assertTrue(created.ok)
        assertTrue(store.skills.any { it.id == "review_plan" && it.name == "Review Plan" })
    }

    @Test
    fun `web_search格式化结果`() = runTest {
        val search = FakeWebSearch(listOf(WebSearchItem("标题一", "https://a.example", "摘要一")))
        val result = registry.get("web_search")!!.execute(
            ToolArgs.parse("""{"query":"kotlin","max_results":3}"""),
            ctx(search = search),
        )
        assertTrue(result.ok)
        assertEquals("kotlin", search.lastQuery)
        assertEquals(3, search.lastMax)
        assertTrue(result.output.contains("标题一"))
        assertTrue(result.output.contains("https://a.example"))
    }

    private fun ctx(
        workspace: NotebookWorkspacePort? = null,
        skills: SkillManagerPort = FakeSkillStore(),
        search: WebSearchPort = FakeWebSearch(emptyList()),
    ): ToolContext = ToolContext(
        boundPage = PageRef("nb1", "p0001"),
        config = ToolConfig("tool"),
        workspace = workspace,
        skillStore = skills,
        webSearch = search,
    )
}

/** AgentController 多轮工具循环 / 高危确认 / 关闭工具 / 摘要不带工具 */
class AgentControllerToolLoopTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun rig(
        scope: CoroutineScope,
        workspace: FakeWorkspace = FakeWorkspace(),
        webSearch: WebSearchPort = FakeWebSearch(emptyList()),
        script: (ChatRequest, Int) -> List<ChatEvent>,
    ): ToolRig {
        val files = File(tmp.newFolder(), "files").apply { mkdirs() }
        val notebooks = File(tmp.newFolder(), "notebooks").apply { mkdirs() }
        val store = AgentStore(files, notebooks)
        val provider = CountingProvider(script)
        val controller = AgentController(
            scope = scope,
            store = store,
            snipSource = null,
            annotationSink = null,
            keyFor = { "sk-test" },
            providerFactory = { _, _, _ -> provider },
            workspace = workspace,
            webSearch = webSearch,
        )
        controller.addProfile("glm", "", "", "sk-test")
        controller.setSummaryConfig(SummaryConfig(enabled = false))
        return ToolRig(controller, store, provider, workspace)
    }

    @Test
    fun `多轮工具调用-calculate后给出最终答案`() = runTest {
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler))) { _, i ->
            if (i == 0) {
                listOf(
                    ChatEvent.ToolCallReady(ToolCall("c1", "calculate", """{"expression":"2+2"}""")),
                    ChatEvent.Done("tool_calls"),
                )
            } else {
                listOf(ChatEvent.Delta("答案是 4"), ChatEvent.Usage(50, 10), ChatEvent.Done("stop"))
            }
        }
        val controller = rig.controller
        controller.openDrawer(PageRef("nb1", "p0001"))
        controller.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        assertEquals(2, rig.provider.requests.size)
        assertTrue(rig.provider.requests[0].tools.any { it.name == "calculate" })

        val second = rig.provider.requests[1]
        val assistant = second.continuationMessages.first { it.role == "assistant" }
        assertEquals("c1", assistant.toolCalls.single().id)
        val toolMsg = second.continuationMessages.first { it.role == "tool" }
        assertEquals("c1", toolMsg.toolCallId)
        assertTrue(toolMsg.content.contains("4"))

        val last = controller.state.value.messages.last()
        assertEquals("答案是 4", last.text)
        assertEquals(AgentMessage.Phase.DONE, last.phase)
        assertEquals(1, last.toolTraces.size)
        assertEquals(ToolTrace.Status.DONE, last.toolTraces[0].status)
        assertEquals("数学计算", last.toolTraces[0].displayName)
        assertEquals(50, last.promptTokens)
        assertEquals(10, last.completionTokens)
        assertNull(controller.state.value.pendingConfirmation)

        // 轨迹随消息落盘,重开会话可回看(枚举与 JSON 往返)
        controller.closeDrawer()
        controller.openDrawer(PageRef("nb1", "p0001"))
        val restored = controller.state.value.messages.last()
        assertEquals(1, restored.toolTraces.size)
        assertEquals(ToolTrace.Status.DONE, restored.toolTraces[0].status)
        assertEquals("数学计算", restored.toolTraces[0].displayName)
    }

    @Test
    fun `read_skill返回内置技能正文`() = runTest {
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler))) { _, i ->
            if (i == 0) {
                listOf(
                    ChatEvent.ToolCallReady(ToolCall("s1", "read_skill", """{"skill_id":"formula_sheet"}""")),
                    ChatEvent.Done(null),
                )
            } else {
                listOf(ChatEvent.Delta("已按技能整理"), ChatEvent.Done(null))
            }
        }
        val controller = rig.controller
        controller.openDrawer(PageRef("nb1", "p0001"))
        controller.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        val toolMsg = rig.provider.requests[1].continuationMessages.last()
        assertEquals("tool", toolMsg.role)
        assertTrue(toolMsg.content.contains("公式速查卡片"))
        assertTrue(toolMsg.content.contains("KaTeX"))
        assertEquals("已按技能整理", controller.state.value.messages.last().text)
    }

    @Test
    fun `create_skill实时刷新状态并落盘`() = runTest {
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler))) { _, i ->
            if (i == 0) {
                listOf(
                    ChatEvent.ToolCallReady(
                        ToolCall("k1", "create_skill", """{"name":"Review Plan","summary":"复盘","content":"正文"}"""),
                    ),
                    ChatEvent.Done(null),
                )
            } else {
                listOf(ChatEvent.Delta("已创建"), ChatEvent.Done(null))
            }
        }
        val controller = rig.controller
        controller.openDrawer(PageRef("nb1", "p0001"))
        controller.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        assertTrue(controller.state.value.skills.any { it.id == "review_plan" && !it.builtin })
        assertTrue(rig.store.loadSkills().any { it.id == "review_plan" })
        assertEquals("已创建", controller.state.value.messages.last().text)
    }

    @Test
    fun `delete_page挂起等待批准后执行`() = runTest {
        val ws = FakeWorkspace()
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), workspace = ws) { _, i ->
            if (i == 0) {
                listOf(
                    ChatEvent.ToolCallReady(ToolCall("d1", "delete_page", """{"page_number":2}""")),
                    ChatEvent.Done("tool_calls"),
                )
            } else {
                listOf(ChatEvent.Delta("已删除"), ChatEvent.Done(null))
            }
        }
        val controller = rig.controller
        controller.openDrawer(PageRef("nb1", "p0001"))
        controller.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        assertTrue("确认前不得执行", ws.deleted.isEmpty())
        val pending = controller.state.value.pendingConfirmation
        assertNotNull(pending)
        assertEquals("delete_page", pending!!.toolId)
        assertTrue(pending.actionSummary.contains("第 2 页"))
        assertEquals(
            ToolTrace.Status.PENDING_CONFIRM,
            controller.state.value.messages.last().toolTraces.single().status,
        )
        assertEquals(1, rig.provider.requests.size)

        controller.approvePendingTool()
        advanceUntilIdle()

        assertEquals(listOf("p0002"), ws.deleted)
        assertEquals(2, rig.provider.requests.size)
        assertEquals("已删除", controller.state.value.messages.last().text)
        assertEquals(
            ToolTrace.Status.DONE,
            controller.state.value.messages.last().toolTraces.single().status,
        )
        assertNull(controller.state.value.pendingConfirmation)
    }

    @Test
    fun `delete_page拒绝后回传取消原因`() = runTest {
        val ws = FakeWorkspace()
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), workspace = ws) { _, i ->
            if (i == 0) {
                listOf(
                    ChatEvent.ToolCallReady(ToolCall("d1", "delete_page", """{"page_id":"p0002"}""")),
                    ChatEvent.Done(null),
                )
            } else {
                listOf(ChatEvent.Delta("已取消"), ChatEvent.Done(null))
            }
        }
        val controller = rig.controller
        controller.openDrawer(PageRef("nb1", "p0001"))
        controller.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        assertNotNull(controller.state.value.pendingConfirmation)

        controller.rejectPendingTool()
        advanceUntilIdle()

        assertTrue("拒绝后不得执行", ws.deleted.isEmpty())
        val toolMsg = rig.provider.requests[1].continuationMessages.first { it.role == "tool" }
        assertTrue(toolMsg.content.contains("用户取消"))
        assertEquals(
            ToolTrace.Status.DENIED,
            controller.state.value.messages.last().toolTraces.single().status,
        )
        assertNull(controller.state.value.pendingConfirmation)
    }

    @Test
    fun `modify_page_element批准后按比例移动`() = runTest {
        val ws = FakeWorkspace()
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), workspace = ws) { _, i ->
            if (i == 0) {
                listOf(
                    ChatEvent.ToolCallReady(
                        ToolCall("m1", "modify_page_element", """{"action":"move","element_id":"c1","x":0.5,"y":0.25}"""),
                    ),
                    ChatEvent.Done(null),
                )
            } else {
                listOf(ChatEvent.Delta("已移动"), ChatEvent.Done(null))
            }
        }
        val controller = rig.controller
        controller.openDrawer(PageRef("nb1", "p0001"))
        controller.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        assertNotNull(controller.state.value.pendingConfirmation)
        assertNull(ws.lastMove)
        controller.approvePendingTool()
        advanceUntilIdle()

        val move = ws.lastMove!!
        assertEquals("c1", move.first)
        assertEquals(500f, move.second!!, 0.01f)
        assertEquals(350f, move.third!!, 0.01f)
        assertEquals(
            ToolTrace.Status.DONE,
            controller.state.value.messages.last().toolTraces.single().status,
        )
    }

    @Test
    fun `stop中断待确认工具并清理状态`() = runTest {
        val ws = FakeWorkspace()
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler)), workspace = ws) { _, _ ->
            listOf(
                ChatEvent.ToolCallReady(ToolCall("d1", "delete_page", """{"page_number":2}""")),
                ChatEvent.Done(null),
            )
        }
        val controller = rig.controller
        controller.openDrawer(PageRef("nb1", "p0001"))
        controller.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        assertNotNull(controller.state.value.pendingConfirmation)

        controller.stop()
        advanceUntilIdle()

        assertNull(controller.state.value.pendingConfirmation)
        assertTrue("中断后不得执行", ws.deleted.isEmpty())
        assertEquals(1, rig.provider.requests.size)
        assertEquals(AgentMessage.Phase.INTERRUPTED, controller.state.value.messages.last().phase)
    }

    @Test
    fun `关闭的工具回传错误并继续对话`() = runTest {
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler))) { _, i ->
            if (i == 0) {
                listOf(
                    ChatEvent.ToolCallReady(ToolCall("c1", "calculate", """{"expression":"1+1"}""")),
                    ChatEvent.Done(null),
                )
            } else {
                listOf(ChatEvent.Delta("抱歉"), ChatEvent.Done(null))
            }
        }
        val controller = rig.controller
        controller.toggleTool("calculate", false)
        assertEquals(false, controller.state.value.toolConfigs["calculate"]!!.enabled)

        controller.openDrawer(PageRef("nb1", "p0001"))
        controller.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        assertTrue(rig.provider.requests[0].tools.none { it.name == "calculate" })
        val toolMsg = rig.provider.requests[1].continuationMessages.first { it.role == "tool" }
        assertTrue("工具结果:${toolMsg.content}", toolMsg.content.contains("已被用户关闭"))
        assertEquals(
            ToolTrace.Status.ERROR,
            controller.state.value.messages.last().toolTraces.single().status,
        )
        assertEquals("抱歉", controller.state.value.messages.last().text)
    }

    @Test
    fun `技能与工具管理API-编辑关闭重置`() = runTest {
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _ ->
            listOf(ChatEvent.Delta("答"), ChatEvent.Done(null))
        }
        val controller = rig.controller

        assertEquals(3, controller.state.value.skills.size)
        assertEquals(9, controller.state.value.toolSpecs.size)

        controller.toggleSkill("cornell_notes", false)
        assertFalse(controller.state.value.skills.first { it.id == "cornell_notes" }.enabled)
        assertFalse(rig.store.loadSkills().first { it.id == "cornell_notes" }.enabled)

        controller.upsertSkill(AgentSkill("custom", "自定义技能", "摘要", "正文"))
        assertTrue(controller.state.value.skills.any { it.id == "custom" })
        controller.deleteSkill("custom")
        assertTrue(controller.state.value.skills.none { it.id == "custom" })

        controller.deleteSkill("cornell_notes")   // 内置不可删,只提示
        assertTrue(controller.state.value.skills.any { it.id == "cornell_notes" })

        controller.resetBuiltinSkill("cornell_notes")
        assertTrue(controller.state.value.skills.first { it.id == "cornell_notes" }.enabled)

        controller.updateToolConfig(ToolConfig("calculate", enabled = false, displayName = "算一算"))
        assertEquals("算一算", controller.state.value.toolConfigs["calculate"]!!.displayName)
        controller.resetToolConfig("calculate")
        assertNull(controller.state.value.toolConfigs["calculate"])
    }

    @Test
    fun `摘要请求保持tools为空`() = runTest {
        val rig = rig(CoroutineScope(StandardTestDispatcher(testScheduler))) { req, _ ->
            if (req.system == SUMMARY_SYSTEM_PROMPT) {
                listOf(ChatEvent.Delta("会话标题"), ChatEvent.Done(null))
            } else {
                listOf(ChatEvent.Delta("答"), ChatEvent.Done(null))
            }
        }
        val controller = rig.controller
        controller.openDrawer(PageRef("nb1", "p0001"))
        controller.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        val sessionId = controller.state.value.currentSessionId!!

        controller.regenerateSummary(sessionId)
        advanceUntilIdle()

        val summaryRequest = rig.provider.requests.last()
        assertEquals(SUMMARY_SYSTEM_PROMPT, summaryRequest.system)
        assertTrue(summaryRequest.tools.isEmpty())
        assertTrue(summaryRequest.continuationMessages.isEmpty())
        assertEquals("会话标题", rig.store.loadSession("nb1", sessionId)!!.summary)
    }
}

private class ToolRig(
    val controller: AgentController,
    val store: AgentStore,
    val provider: CountingProvider,
    val workspace: FakeWorkspace,
)

private class CountingProvider(
    private val script: (ChatRequest, Int) -> List<ChatEvent>,
) : AgentProvider {
    override val profileId = "counting"
    val requests = ArrayList<ChatRequest>()

    override fun chat(req: ChatRequest) = flow {
        requests.add(req)
        script(req, requests.size - 1).forEach { emit(it) }
    }

    override suspend fun fetchModels(): Result<List<ModelInfo>> = Result.success(emptyList())
    override suspend fun testConnection(): Result<Long> = Result.success(1L)
}

private class FakeSkillStore(initial: List<AgentSkill> = BuiltinSkills.all) : SkillManagerPort {
    val skills = initial.toMutableList()

    override fun listSkills(): List<AgentSkill> = skills.toList()

    override fun findEnabledSkill(idOrName: String): AgentSkill? =
        skills.firstOrNull { it.enabled && (it.id == idOrName || it.name == idOrName) }

    override fun createOrUpdateSkill(
        id: String?,
        name: String,
        summary: String,
        content: String,
        enabled: Boolean,
    ): AgentSkill {
        val skill = AgentSkill(id ?: "auto", name, summary, content, enabled)
        skills.removeAll { it.id == skill.id }
        skills += skill
        return skill
    }
}

private class FakeWebSearch(private val items: List<WebSearchItem>) : WebSearchPort {
    var lastQuery: String? = null
    var lastMax: Int = 0

    override suspend fun search(
        query: String,
        maxResults: Int,
        settings: Map<String, String>,
    ): Result<List<WebSearchItem>> {
        lastQuery = query
        lastMax = maxResults
        return Result.success(items.take(maxResults))
    }
}

private class FakeWorkspace : NotebookWorkspacePort {
    val pages = arrayListOf(PageInfo("p0001", 1, true), PageInfo("p0002", 2, false))
    val deleted = ArrayList<String>()
    val deletedElements = ArrayList<String>()
    var lastCard: Triple<String, Float, Float>? = null
    var lastCardWidth: Float? = null
    var lastMove: Triple<String, Float?, Float?>? = null
    private var next = 3

    override suspend fun listPages(notebookId: String): List<PageInfo> = pages.toList()

    override suspend fun pageDimensions(page: PageRef): PageDimensions = PageDimensions(1000f, 1400f)

    override suspend fun addPage(
        notebookId: String,
        position: String,
        anchorPageId: String?,
        switchTo: Boolean,
    ): PageInfo {
        val page = PageInfo("p%04d".format(next++), pages.size + 1, switchTo)
        pages += page
        return page
    }

    override suspend fun deletePage(notebookId: String, pageId: String): Boolean {
        deleted += pageId
        pages.removeAll { it.pageId == pageId }
        return true
    }

    override suspend fun insertMarkdownCard(
        page: PageRef,
        markdown: String,
        xPx: Float,
        yPx: Float,
        widthPx: Float,
    ): String {
        lastCard = Triple(markdown, xPx, yPx)
        lastCardWidth = widthPx
        return "card_1"
    }

    override suspend fun listElements(page: PageRef): List<PageElementSummary> = listOf(
        PageElementSummary("c1", "card", 100f, 200f, 300f, 180f, 0.1f, 0.14f, "预览"),
    )

    override suspend fun moveElement(
        page: PageRef,
        elementId: String,
        elementKind: String?,
        targetX: Float?,
        targetY: Float?,
        dx: Float?,
        dy: Float?,
    ): Boolean {
        lastMove = Triple(elementId, targetX, targetY)
        return true
    }

    override suspend fun deleteElement(page: PageRef, elementId: String, elementKind: String?): Boolean {
        deletedElements += elementId
        return true
    }
}

/** 本地 SSE 服务:录制请求体并回吐固定帧(验证 tools 请求体与事件顺序) */
private class RecordingSseServer(private val frames: List<String>) {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))

    @Volatile
    var requestBody: String = ""
        private set

    val baseUrl: String get() = "http://127.0.0.1:${server.localPort}/v1"

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { serve(socket) } }
            }
        }
    }

    private fun serve(socket: java.net.Socket) {
        socket.use { s ->
            val input = java.io.BufferedInputStream(s.getInputStream())
            readLine(input) ?: return
            var length = 0
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    length = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            if (length > 0) {
                val buffer = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val n = input.read(buffer, offset, length - offset)
                    if (n < 0) break
                    offset += n
                }
                requestBody = String(buffer, 0, offset, Charsets.UTF_8)
            }
            val body = frames.joinToString("") { "data: $it\n\n" }.toByteArray(Charsets.UTF_8)
            val head = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: text/event-stream\r\n")
                append("Content-Length: ").append(body.size).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            s.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                write(body)
                flush()
            }
        }
    }

    private fun readLine(input: java.io.InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
    }

    fun close() = server.close()
}
