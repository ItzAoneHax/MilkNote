package com.milknote.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 思考强度分发(AGENT-BACKEND §3.8,A2.3):档位语义统一、按厂商 wire format 翻译。
 * 五种 OpenAI 兼容格式 + Anthropic budget,全部照抄 pi-ai(earendil-works/pi@1.3.0
 * openai-completions.js)与 deepseek-harness(llm-deepseek)的实证行为:
 *
 * | format   | on(开启档)                                        | off            |
 * |----------|----------------------------------------------------|----------------|
 * | deepseek | thinking:{type:enabled} + reasoning_effort:<档>    | thinking:{type:disabled} |
 * | zai(GLM) | thinking:{type:enabled,clear_thinking:false} + reasoning_effort | thinking:{type:disabled} |
 * | qwen     | enable_thinking:true + reasoning_effort:<档>       | enable_thinking:false |
 * | openrouter | reasoning:{effort:<档>}                          | reasoning:{effort:"none"} |
 * | openai   | reasoning_effort:<档>                              | 省略(模型默认) |
 *
 * AUTO 一律不发参数(与 pi-ai 的「未选档默认关闭」不同:笔记场景尊重模型默认)。
 * MiniMax M3.1 无 none 档(不可关,pie catalog off:null)→ 目录不给 OFF,AUTO 即自适应。
 */
object ThinkingDispatch {

    enum class Format { OPENAI, DEEPSEEK, ZAI, QWEN, OPENROUTER }

    /** 按预设来源选 wire format(自定义/未知 → OpenAI 标准 reasoning_effort) */
    fun formatOf(presetId: String?): Format = when (presetId) {
        "deepseek" -> Format.DEEPSEEK      // api.deepseek.com(thinking+reasoning_effort;Messages 格式走 output_config.effort,见 §3.7)
        "glm" -> Format.ZAI                // 智谱 bigmodel
        "qwen" -> Format.QWEN              // 阿里 dashscope compatible-mode
        OpenAiCompatProvider.PRESET_OPENROUTER -> Format.OPENROUTER
        else -> Format.OPENAI              // openai/kimi-k3/minimax/gemini/grok/自定义(绝大多数中转按 OpenAI 标准透传)
    }

    /**
     * 档位 → 厂商参数值;null = 该模型不支持此档(目录声明决定,UI 不应展示;
     * 防御性兜底:降级到该模型已声明的最近低档)。
     */
    fun vendorLevel(level: ReasoningEffort, declared: List<ReasoningEffort>): String? {
        if (declared.isEmpty()) return level.name.lowercase()   // 未声明(自定义模型):原样透传
        if (declared.contains(level)) return level.name.lowercase()
        // 就近降档(请求的档不被支持 → 取声明中最近的更低档;都比它低则取最低档)
        val order = listOf(ReasoningEffort.OFF, ReasoningEffort.MINIMAL, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH, ReasoningEffort.XHIGH, ReasoningEffort.MAX)
        val idx = order.indexOf(level)
        for (i in idx downTo 0) {
            val cand = order[i]
            if (cand != ReasoningEffort.OFF && declared.contains(cand)) return cand.name.lowercase()
        }
        return declared.firstOrNull { it != ReasoningEffort.OFF }?.name?.lowercase()
    }

    /** OpenAI 兼容路径:把思考档位写进请求对象(不改则原样返回) */
    fun applyOpenAi(
        obj: kotlinx.serialization.json.JsonObjectBuilder,
        effort: ReasoningEffort,
        format: Format,
        declared: List<ReasoningEffort>,
    ) {
        if (effort == ReasoningEffort.AUTO) return
        val declaredActive = declared.filter { it != ReasoningEffort.OFF }
        when (format) {
            Format.DEEPSEEK -> when (effort) {
                ReasoningEffort.OFF -> obj.put("thinking", buildJsonObject { put("type", "disabled") })
                else -> {
                    obj.put("thinking", buildJsonObject { put("type", "enabled") })
                    vendorLevel(effort, declaredActive)?.let { obj.put("reasoning_effort", it) }
                }
            }
            Format.ZAI -> when (effort) {
                ReasoningEffort.OFF -> obj.put("thinking", buildJsonObject { put("type", "disabled") })
                else -> {
                    obj.put("thinking", buildJsonObject {
                        put("type", "enabled")
                        put("clear_thinking", false)   // pi-ai zai 格式带 clear_thinking:false(回放思考)
                    })
                    vendorLevel(effort, declaredActive)?.let { obj.put("reasoning_effort", it) }
                }
            }
            Format.QWEN -> {
                obj.put("enable_thinking", effort != ReasoningEffort.OFF)
                if (effort != ReasoningEffort.OFF) {
                    vendorLevel(effort, declaredActive)?.let { obj.put("reasoning_effort", it) }
                }
            }
            Format.OPENROUTER -> when (effort) {
                ReasoningEffort.OFF ->
                    // 模型声明含 OFF 才显式 none(pi-ai:off=null 时省略,如 Mercury/inception)
                    if (declared.contains(ReasoningEffort.OFF)) {
                        obj.put("reasoning", buildJsonObject { put("effort", "none") })
                    }
                else -> vendorLevel(effort, declaredActive)?.let {
                    obj.put("reasoning", buildJsonObject { put("effort", it) })
                }
            }
            Format.OPENAI -> when (effort) {
                // OpenAI 推理模型关不掉:OFF 省略参数走模型默认(pi-ai omit-only 同款)
                ReasoningEffort.OFF -> Unit
                else -> vendorLevel(effort, declaredActive)?.let { obj.put("reasoning_effort", it) }
            }
        }
    }

    /** Anthropic 原生:budget_tokens 档位表(须 < max_tokens;MAX 自动抬高输出上限) */
    fun anthropicBudget(effort: ReasoningEffort): Int? = when (effort) {
        ReasoningEffort.LOW -> 2048
        ReasoningEffort.MEDIUM -> 4096
        ReasoningEffort.HIGH -> 6144
        ReasoningEffort.XHIGH, ReasoningEffort.MAX -> 12288
        ReasoningEffort.MINIMAL -> 1024   // Anthropic 下限 1024
        else -> null                      // AUTO/OFF:不发 thinking(默认关)
    }

    fun parseLevels(raw: List<String>): List<ReasoningEffort> =
        raw.mapNotNull { runCatching { ReasoningEffort.valueOf(it) }.getOrNull() }
}
