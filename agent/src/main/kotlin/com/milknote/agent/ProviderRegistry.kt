package com.milknote.agent

/**
 * 厂商注册表(§3.3,快照 2026-10-31,全部经各家官方文档核验)。
 * 预设仅是省事的默认值,不做厂商绑定;快照会过期 —— 运行时以「拉取列表 + 手填」为准,
 * 死 id 由 404/ModelNotVision 错误兜底提示更新。
 */
data class ProviderPreset(
    val id: String,
    val title: String,
    val baseUrl: String,
    val models: List<ModelInfo>,
    val imageStyle: ImageStyle,
    val note: String = "",
    val protocol: Protocol = Protocol.OPENAI_COMPATIBLE,
)

object ProviderRegistry {

    private fun m(
        id: String,
        maxImages: Int = 8,
        thinking: List<ReasoningEffort> = emptyList(),
        ctxK: Int = 0,
        outK: Int = 0,
    ) = ModelInfo(
        id = id, displayName = id, vision = true,
        visionSource = VisionSource.PRESET.name, maxImages = maxImages,
        // 思考档位按 pi-ai 目录核验值声明(每家不同;§3.8);空=该模型不可调(仅 AUTO)
        thinkingLevels = thinking.map { it.name },
        contextWindowK = ctxK,   // 千 tokens(§3.3/各家公开规格;0=未知)
        maxTokensK = outK,
    )

    // DeepSeek V4.1-Flash:low/high/max(pi-ai DEEPSEEK_V4_FLASH_THINKING_LEVEL_MAP,medium=null)
    private val DS_FLASH = listOf(ReasoningEffort.OFF, ReasoningEffort.LOW, ReasoningEffort.HIGH, ReasoningEffort.MAX)
    // GLM(zai 格式):none/low/medium/high(glm-5.2 另有 xhigh;5.3 保守不含)
    private val GLM = listOf(ReasoningEffort.OFF, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)
    // Qwen:enable_thinking 开关 + reasoning_effort
    private val QWEN = listOf(ReasoningEffort.OFF, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)
    // Kimi K3 系:reasoning_effort(models 列表上报档位)
    private val KIMI_K3 = listOf(ReasoningEffort.OFF, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)
    // MiMo(xiaomi,deepseek 格式):仅 high(pi-ai xiaomi 目录)
    private val MIMO = listOf(ReasoningEffort.OFF, ReasoningEffort.HIGH)
    // MiniMax M3.1:自适应思考,无 none 档不可关(pi-ai off:null;AUTO=自适应默认)
    private val MINIMAX = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)
    // OpenAI gpt-6.1 推理系:minimal..high,关不掉(OFF 省略=默认)
    private val OPENAI_R = listOf(ReasoningEffort.MINIMAL, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)
    // Claude:budget 档
    private val CLAUDE = listOf(ReasoningEffort.OFF, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)
    // Gemini 兼容层 reasoning_effort
    private val GEMINI = listOf(ReasoningEffort.OFF, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)
    // 自定义模型默认按 OpenAI 标准透传(声明四档)
    private val CUSTOM_DEFAULT = listOf(ReasoningEffort.OFF, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)

    val presets: List<ProviderPreset> = listOf(
        ProviderPreset(
            id = "glm", title = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            models = listOf(m("glm-5.3-flash", thinking = GLM, ctxK = 1000, outK = 128), m("glm-5.3-flashx", thinking = GLM, ctxK = 1000, outK = 128)),
            imageStyle = ImageStyle.RAW_BASE64,   // 智谱端点裸 base64(§3.5 已知差异)
            note = "GLM-5 系首个原生多模态;文本旗舰无视觉不预置",
        ),
        ProviderPreset(
            id = "deepseek", title = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            models = listOf(m("deepseek-flash", thinking = DS_FLASH, ctxK = 1000, outK = 128)),
            imageStyle = ImageStyle.DATA_URI,
            note = "官方唯一视觉模型;峰谷计费非高峰半价",
        ),
        ProviderPreset(
            id = "kimi", title = "Kimi(月之暗面)",
            baseUrl = "https://api.moonshot.cn/v1",
            models = listOf(m("kimi-k3.1-pro", thinking = KIMI_K3, ctxK = 1000, outK = 131), m("kimi-k3.1-pro-highspeed", thinking = KIMI_K3, ctxK = 1000, outK = 131), m("kimi-k3", thinking = KIMI_K3, ctxK = 1000, outK = 131)),
            imageStyle = ImageStyle.DATA_URI,
            note = "K3 系原生视觉;图片仅接受 base64(本应用天然兼容)",
        ),
        ProviderPreset(
            id = "minimax", title = "MiniMax(国内)",
            baseUrl = "https://api.minimaxi.com/v1",
            models = listOf(m("MiniMax-M3.1-Flash", thinking = MINIMAX, ctxK = 1000, outK = 64), m("MiniMax-M3", thinking = MINIMAX, ctxK = 1000, outK = 64)),
            imageStyle = ImageStyle.DATA_URI,
            note = "国内/国际账号与端点不互通",
        ),
        ProviderPreset(
            id = "minimax_intl", title = "MiniMax(国际)",
            baseUrl = "https://api.minimax.io/v1",
            models = listOf(m("MiniMax-M3.1-Flash", thinking = MINIMAX, ctxK = 1000, outK = 64), m("MiniMax-M3", thinking = MINIMAX, ctxK = 1000, outK = 64)),
            imageStyle = ImageStyle.DATA_URI,
            note = "国内/国际账号与端点不互通",
        ),
        ProviderPreset(
            id = "mimo", title = "MiMo(小米)",
            baseUrl = "https://api.xiaomimimo.com/v1",
            models = listOf(m("mimo-v2.6-pro", thinking = MIMO, ctxK = 256, outK = 32), m("mimo-v2.6-flash", thinking = MIMO, ctxK = 256, outK = 32)),
            imageStyle = ImageStyle.DATA_URI,
            note = "V2.6 系全模态;具体视觉 id 以平台模型列表为准",
        ),
        ProviderPreset(
            id = "qwen", title = "通义 Qwen",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            models = listOf(m("qwen3.8-max", maxImages = 10, thinking = QWEN, ctxK = 1000, outK = 64), m("qwen3-vl-plus", thinking = QWEN, ctxK = 128, outK = 32), m("qwen3-vl-flash", thinking = QWEN, ctxK = 128, outK = 32)),
            imageStyle = ImageStyle.DATA_URI,
            note = "新工作空间为专属域名形态,以百炼控制台为准",
        ),
        ProviderPreset(
            id = "openai", title = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            models = listOf(m("gpt-6.1-terra", thinking = OPENAI_R, ctxK = 400, outK = 128)),
            imageStyle = ImageStyle.DATA_URI,
        ),
        ProviderPreset(
            id = "anthropic", title = "Anthropic Claude",
            baseUrl = "https://api.anthropic.com/v1",
            models = listOf(m("claude-opus-5-5", thinking = CLAUDE, ctxK = 1000, outK = 64), m("claude-sonnet-5-5", thinking = CLAUDE, ctxK = 1000, outK = 64), m("claude-fable-5-1", thinking = CLAUDE, ctxK = 1000, outK = 64), m("claude-haiku-5-5", thinking = CLAUDE, ctxK = 1000, outK = 64)),
            imageStyle = ImageStyle.DATA_URI,
            note = "原生 /v1/messages(A2);Claude 系中转站选「自定义 + Anthropic 协议」",
            protocol = Protocol.ANTHROPIC,
        ),
        ProviderPreset(
            id = "grok", title = "xAI Grok",
            baseUrl = "https://api.x.ai/v1",
            models = listOf(m("grok-4.7"), m("grok-4.6")),
            imageStyle = ImageStyle.DATA_URI,
        ),
        ProviderPreset(
            id = "gemini", title = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
            models = listOf(m("gemini-4-argon", thinking = GEMINI, ctxK = 1000, outK = 64), m("gemini-3.8-flash", thinking = GEMINI, ctxK = 1000, outK = 64)),
            imageStyle = ImageStyle.DATA_URI,
        ),
        ProviderPreset(
            id = OpenAiCompatProvider.PRESET_OPENROUTER, title = "OpenRouter(中转)",
            baseUrl = "https://openrouter.ai/api/v1",
            models = listOf(
                m("z-ai/glm-5.3-flash"), m("deepseek/deepseek-flash"),
                m("anthropic/claude-opus-5-5"), m("google/gemini-4-argon"),
            ),
            imageStyle = ImageStyle.DATA_URI,
            note = "一个 Key 通吃全部厂商;拉取列表自动标视觉(元数据)",
        ),
        ProviderPreset(
            id = "custom", title = "自定义(OpenAI 兼容)",
            baseUrl = "",
            models = emptyList(),
            imageStyle = ImageStyle.DATA_URI,
            note = "任意兼容代理;视觉=用户声明 + 运行时兜底",
        ),
    )

    fun byId(id: String): ProviderPreset? = presets.firstOrNull { it.id == id }

    /** 预设 → 初始 profile(apiKeyAlias 占位,保存 Key 时落 vault) */
    fun newProfile(preset: ProviderPreset, alias: String): ProviderProfile = ProviderProfile(
        id = java.util.UUID.randomUUID().toString().substring(0, 8),
        presetId = preset.id,
        title = preset.title,
        baseUrl = preset.baseUrl,
        apiKeyAlias = alias,
        models = preset.models,
        activeModelId = preset.models.firstOrNull()?.id ?: "",
        imageStyle = preset.imageStyle,
        protocol = preset.protocol,
    )
}
