package com.milknote.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// =====================================================================
// Skills & Tools 可扩展架构
// - AgentSkill:可按需阅读的「技能」(先看目录,再 read_skill 取全文)
// - ToolSpec / ToolConfig:工具声明与用户覆盖(内置工具只读;自定义工具的通用键值配置由此驱动)
// - AgentTool / ToolRegistry:执行单元与注册表
// 全部纯 Kotlin,不依赖 android.framework,便于 JVM 单测。
// =====================================================================

@Serializable
data class AgentSkill(
    val id: String,
    val name: String,
    val summary: String = "",
    val content: String,
    val enabled: Boolean = true,
    val builtin: Boolean = false,
    val updatedAt: Long = 0L,
)

@Serializable
enum class ToolCategory { SEARCH, PAGE, CANVAS, COMPUTE, SKILL }

@Serializable
data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = false,
    val enumValues: List<String> = emptyList(),
)

/** 工具设置项:声明即可在设置页生成通用键值控件 */
data class ToolSettingOption(
    val key: String,
    val label: String,
    val defaultValue: String,
    val choices: List<Pair<String, String>> = emptyList(),
    val placeholder: String = "",
)

data class ToolSpec(
    val id: String,
    val defaultDisplayName: String,
    val defaultDescription: String,
    val category: ToolCategory,
    val defaultEnabled: Boolean = true,
    val defaultRequireConfirmation: Boolean = false,
    val parameters: List<ToolParameter> = emptyList(),
    val settingOptions: List<ToolSettingOption> = emptyList(),
    /**
     * 应用自带工具:定义与运行策略只读 —— 用户覆盖的开关/显示名/说明/确认/设置一律忽略,
     * 危险工具的默认确认不可被关闭。自定义工具置 false 后仍由设置页通用键值驱动。
     */
    val builtin: Boolean = true,
    /**
     * 仅当用户就当前页显式申请「熟练度分析」时才对模型可见;
     * 实际执行还需 [ToolContext.elementTimestampsPage] 匹配(缺授权直接拒绝)。
     */
    val requiresUserGrant: Boolean = false,
    /** 是否把工具原始输出写入 [ToolTrace](时序类数据只进本次调用链,不落盘) */
    val recordOutputInTrace: Boolean = true,
)

@Serializable
data class ToolConfig(
    val id: String,
    val enabled: Boolean = true,
    val displayName: String? = null,
    val customDescription: String? = null,
    val requireConfirmation: Boolean? = null,
    val settings: Map<String, String> = emptyMap(),
)

/** 发给模型的工具声明(三协议各自翻译成 wire format) */
data class WireToolDef(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>,
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

/** 工具调用轨迹(UI 逐条展示;随助手消息落盘) */
@Serializable
data class ToolTrace(
    val callId: String,
    val toolName: String,
    val displayName: String,
    val argumentsJson: String,
    val status: Status = Status.RUNNING,
    val actionSummary: String = "",
    val resultSummary: String = "",
    val output: String = "",
) {
    @Serializable
    enum class Status { PENDING_CONFIRM, RUNNING, DONE, DENIED, ERROR }
}

/** 待用户确认的高危工具调用 */
data class PendingToolConfirmation(
    val callId: String,
    val toolId: String,
    val displayName: String,
    val actionSummary: String,
    val argumentsJson: String,
)

/** 工具执行上下文(装配层注入端口;每次调用按当前配置构造) */
data class ToolContext(
    val boundPage: PageRef?,
    val config: ToolConfig,
    val workspace: NotebookWorkspacePort?,
    val skillStore: SkillManagerPort,
    val webSearch: WebSearchPort,
    /**
     * 本次请求的熟练度分析授权页(null = 未申请)。
     * 只由用户显式「申请熟练度分析」产生,绑定发起时的页面,不落盘、不跨 turn。
     */
    val elementTimestampsPage: PageRef? = null,
)

data class ToolExecResult(
    val ok: Boolean,
    val output: String,
    val summary: String = "",
)

/** 可执行工具:describeAction 用于确认弹层的人话描述 */
interface AgentTool {
    val spec: ToolSpec

    fun describeAction(args: JsonObject, ctx: ToolContext): String = spec.defaultDisplayName

    suspend fun execute(args: JsonObject, ctx: ToolContext): ToolExecResult
}

/** 工具注册表:声明 → 用户覆盖 → 实际 wire 声明 */
class ToolRegistry {
    private val tools = LinkedHashMap<String, AgentTool>()

    fun register(tool: AgentTool) {
        tools[tool.spec.id] = tool
    }

    fun allSpecs(): List<ToolSpec> = tools.values.map { it.spec }

    fun get(id: String): AgentTool? = tools[id]

    /**
     * 合并声明默认值与用户覆盖(settings 也补齐默认项)。
     * 内置工具定义只读:忽略一切用户覆盖,危险工具的默认确认因此不可被关闭。
     */
    fun effectiveConfig(spec: ToolSpec, overrides: Map<String, ToolConfig>): ToolConfig {
        val saved = if (spec.builtin) null else overrides[spec.id]
        val defaults = spec.settingOptions.associate { it.key to it.defaultValue }
        return ToolConfig(
            id = spec.id,
            enabled = saved?.enabled ?: spec.defaultEnabled,
            displayName = saved?.displayName,
            customDescription = saved?.customDescription,
            requireConfirmation = saved?.requireConfirmation ?: spec.defaultRequireConfirmation,
            settings = defaults + (saved?.settings ?: emptyMap()),
        )
    }

    fun effectiveDisplayName(spec: ToolSpec, config: ToolConfig?): String =
        config?.displayName?.takeIf { it.isNotBlank() } ?: spec.defaultDisplayName

    fun requiresConfirmation(spec: ToolSpec, config: ToolConfig?): Boolean =
        config?.requireConfirmation ?: spec.defaultRequireConfirmation

    /** 工具对当前请求是否可用:已启用 + (授权类工具必须在本 turn 的授权集合内) */
    fun isVisible(spec: ToolSpec, config: ToolConfig, authorizedToolIds: Set<String>): Boolean =
        config.enabled && (!spec.requiresUserGrant || spec.id in authorizedToolIds)

    /**
     * 已启用工具 → 模型可见声明(按注册顺序,保证提示词与 wire 稳定)。
     * [authorizedToolIds] 只由用户显式申请产生,未授权时授权类工具不出现在 wire 中。
     */
    fun effectiveWireTools(
        configs: Map<String, ToolConfig>,
        authorizedToolIds: Set<String> = emptySet(),
    ): List<WireToolDef> =
        tools.values.mapNotNull { tool ->
            val spec = tool.spec
            val config = effectiveConfig(spec, configs)
            if (!isVisible(spec, config, authorizedToolIds)) return@mapNotNull null
            WireToolDef(
                name = spec.id,
                description = config.customDescription?.takeIf { it.isNotBlank() } ?: spec.defaultDescription,
                parameters = spec.parameters,
            )
        }
}

/** 工具参数 → JSON Schema object(OpenAI / Anthropic / Responses 共用) */
internal fun toolParameterSchema(parameters: List<ToolParameter>): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        parameters.forEach { p ->
            put(p.name, buildJsonObject {
                put("type", p.type)
                put("description", p.description)
                if (p.enumValues.isNotEmpty()) {
                    put("enum", buildJsonArray {
                        p.enumValues.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    })
                }
            })
        }
    })
    val required = parameters.filter { it.required }.map { it.name }
    if (required.isNotEmpty()) {
        put("required", buildJsonArray {
            required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
        })
    }
}

/** 三协议共用的工具参数名/类型读取(容错:数字与字符串都接受) */
internal object ToolArgs {
    fun string(obj: JsonObject, name: String): String? =
        (obj[name] as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it !is kotlinx.serialization.json.JsonNull }
            ?.content
            ?.takeIf { it.isNotBlank() }

    fun number(obj: JsonObject, name: String): Double? = string(obj, name)?.toDoubleOrNull()

    fun int(obj: JsonObject, name: String): Int? = number(obj, name)?.let {
        if (it.isFinite() && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE) it.toInt() else null
    }

    fun bool(obj: JsonObject, name: String): Boolean? = string(obj, name)?.toBooleanStrictOrNull()

    fun emptyObject(): JsonObject = buildJsonObject { }

    /** 宽松解析模型给出的 arguments(可能为空串/半截 JSON) */
    fun parse(argumentsJson: String): JsonObject {
        val text = argumentsJson.trim()
        if (text.isEmpty()) return emptyObject()
        return runCatching {
            BaseHttpProvider.json.parseToJsonElement(text) as? JsonObject
        }.getOrNull() ?: emptyObject()
    }

    /** JsonObject → 紧凑字符串(用于轨迹与回传模型) */
    fun encode(obj: JsonObject): String = kotlinx.serialization.json.Json
        .encodeToString(JsonObject.serializer(), obj)
}
