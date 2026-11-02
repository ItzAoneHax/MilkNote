package com.milknote.agent

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 思考强度分发(§3.8,A2.3):五种 OpenAI 兼容 wire format + Anthropic budget,
 * 全部按 pi-ai / deepseek-harness 实证行为断言。
 */
class ThinkingDispatchTest {

    private val json = kotlinx.serialization.json.Json

    private fun body(presetId: String?, effort: ReasoningEffort, levels: List<ReasoningEffort>): kotlinx.serialization.json.JsonObject {
        val profile = ProviderProfile(
            id = "t", title = "t", baseUrl = "https://x/v1", apiKeyAlias = "a", presetId = presetId,
            models = listOf(ModelInfo("m1", thinkingLevels = levels.map { it.name })), activeModelId = "m1",
        )
        val provider = OpenAiCompatProvider(profile, { "k" })
        return json.parseToJsonElement(
            provider.buildRequestBody(
                ChatRequest(model = "m1", system = "s", newUserText = "hi", reasoningEffort = effort),
                ImageStyle.DATA_URI,
            ),
        ).jsonObject
    }

    @Test
    fun `AUTO 一律不发思考参数`() {
        for (preset in listOf("deepseek", "glm", "qwen", "openrouter", "openai", null)) {
            val b = body(preset, ReasoningEffort.AUTO, listOf(ReasoningEffort.OFF, ReasoningEffort.HIGH))
            assertNull("$preset thinking", b["thinking"])
            assertNull("$preset reasoning", b["reasoning"])
            assertNull("$preset reasoning_effort", b["reasoning_effort"])
            assertNull("$preset enable_thinking", b["enable_thinking"])
        }
    }

    @Test
    fun `DeepSeek格式-thinking开关加effort-medium就近降档`() {
        val ds = listOf(ReasoningEffort.OFF, ReasoningEffort.LOW, ReasoningEffort.HIGH, ReasoningEffort.MAX)
        // off:仅 disabled,无 reasoning_effort
        val off = body("deepseek", ReasoningEffort.OFF, ds)
        assertEquals("disabled", off["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertNull(off["reasoning_effort"])
        // high:enabled + reasoning_effort high
        val high = body("deepseek", ReasoningEffort.HIGH, ds)
        assertEquals("enabled", high["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("high", high["reasoning_effort"]!!.jsonPrimitive.content)
        // medium(pi-ai 目录 medium=null):就近降到 low(flash 档)而非硬发 medium
        val med = body("deepseek", ReasoningEffort.MEDIUM, ds)
        assertEquals("low", med["reasoning_effort"]!!.jsonPrimitive.content)
    }

    @Test
    fun `GLM zai格式-clear_thinking与disabled`() {
        val glm = listOf(ReasoningEffort.OFF, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)
        val on = body("glm", ReasoningEffort.MEDIUM, glm)
        assertEquals("enabled", on["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("false", on["thinking"]!!.jsonObject["clear_thinking"]!!.jsonPrimitive.content)
        assertEquals("medium", on["reasoning_effort"]!!.jsonPrimitive.content)
        val off = body("glm", ReasoningEffort.OFF, glm)
        assertEquals("disabled", off["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Qwen格式-enable_thinking开关`() {
        val qw = listOf(ReasoningEffort.OFF, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)
        val off = body("qwen", ReasoningEffort.OFF, qw)
        assertEquals("false", off["enable_thinking"]!!.jsonPrimitive.content)
        assertNull(off["reasoning_effort"])
        val high = body("qwen", ReasoningEffort.HIGH, qw)
        assertEquals("true", high["enable_thinking"]!!.jsonPrimitive.content)
        assertEquals("high", high["reasoning_effort"]!!.jsonPrimitive.content)
    }

    @Test
    fun `OpenRouter格式-reasoning effort对象-off显式none`() {
        val or = listOf(ReasoningEffort.OFF, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)
        val high = body("openrouter", ReasoningEffort.HIGH, or)
        assertEquals("high", high["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        val off = body("openrouter", ReasoningEffort.OFF, or)
        assertEquals("none", off["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        // 声明无 OFF 的模型(如 Mercury/MiniMax):off 省略
        val noOff = body("openrouter", ReasoningEffort.OFF, listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH))
        assertNull(noOff["reasoning"])
    }

    @Test
    fun `OpenAI默认格式-仅reasoning_effort-off省略`() {
        val oa = listOf(ReasoningEffort.MINIMAL, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)
        val min = body("openai", ReasoningEffort.MINIMAL, oa)
        assertEquals("minimal", min["reasoning_effort"]!!.jsonPrimitive.content)
        assertNull(min["thinking"])
        val off = body("openai", ReasoningEffort.OFF, oa)
        assertNull(off["reasoning_effort"])
        assertNull(off["thinking"])
    }

    @Test
    fun `Anthropic budget档位表与温度省略与MAX抬上限`() {
        val profile = ProviderProfile(
            id = "t", title = "t", baseUrl = "https://x/v1", apiKeyAlias = "a", presetId = "anthropic",
            models = listOf(ModelInfo("claude-x")), activeModelId = "claude-x", protocol = Protocol.ANTHROPIC,
        )
        val provider = AnthropicProvider(profile, { "k" })

        fun build(effort: ReasoningEffort) = json.parseToJsonElement(
            provider.buildBody(ChatRequest(model = "claude-x", system = "s", newUserText = "hi", reasoningEffort = effort)),
        ).jsonObject

        // AUTO/OFF:无 thinking,温度照发
        val auto = build(ReasoningEffort.AUTO)
        assertNull(auto["thinking"])
        assertEquals("0.3", auto["temperature"]!!.jsonPrimitive.content)
        // HIGH:预算 6144,温度省略(思考与温度不兼容)
        val high = build(ReasoningEffort.HIGH)
        assertEquals("enabled", high["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("6144", high["thinking"]!!.jsonObject["budget_tokens"]!!.jsonPrimitive.content)
        assertNull("温度应省略", high["temperature"])
        // MAX:预算 12288 且 max_tokens 抬到 >预算
        val max = build(ReasoningEffort.MAX)
        assertEquals("12288", max["thinking"]!!.jsonObject["budget_tokens"]!!.jsonPrimitive.content)
        assertTrue(max["max_tokens"]!!.jsonPrimitive.content.toInt() > 12288)
    }

    @Test
    fun `注册表各模型声明了正确档位`() {
        val glm = ProviderRegistry.byId("glm")!!.models
        assertTrue(glm.all { it.thinkingLevels.contains(ReasoningEffort.OFF.name) })
        // DeepSeek flash:medium 不在列(pi-ai medium=null)
        val ds = ProviderRegistry.byId("deepseek")!!.models.first()
        assertTrue(ds.thinkingLevels.containsAll(listOf("OFF", "LOW", "HIGH", "MAX")))
        assertFalse(ds.thinkingLevels.contains("MEDIUM"))
        // MiMo:仅 OFF/HIGH
        val mimo = ProviderRegistry.byId("mimo")!!.models.first()
        assertEquals(listOf("OFF", "HIGH"), mimo.thinkingLevels)
        // MiniMax:无 OFF(不可关)
        val mm = ProviderRegistry.byId("minimax")!!.models.first()
        assertFalse(mm.thinkingLevels.contains("OFF"))
        // Grok:视觉内置不可调,无档位
        assertTrue(ProviderRegistry.byId("grok")!!.models.all { it.thinkingLevels.isEmpty() })
    }

    @Test
    fun `就近降档兜底`() {
        // 请求 XHIGH 而声明只到 HIGH → high
        val v = ThinkingDispatch.vendorLevel(ReasoningEffort.XHIGH, listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH))
        assertEquals("high", v)
        // 请求 MINIMAL 而声明从 LOW 起 → low
        assertEquals("low", ThinkingDispatch.vendorLevel(ReasoningEffort.MINIMAL, listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH)))
    }
}
