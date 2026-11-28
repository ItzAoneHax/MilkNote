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
    /** type="array" 时每项的 JSON 类型(生成合法的 items schema) */
    val itemType: String? = null,
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

/** 待用户确认的工具调用(高危操作或需显式授权的数据读取) */
data class PendingToolConfirmation(
    val callId: String,
    val toolId: String,
    val displayName: String,
    val actionSummary: String,
    val argumentsJson: String,
    /** 授权类确认的数据范围说明(空 = 沿用 [actionSummary]) */
    val detail: String = "",
    val confirmLabel: String = "确认",
    val cancelLabel: String = "取消",
)

/** 工具执行上下文(装配层注入端口;每次调用按当前配置构造) */
data class ToolContext(
    val boundPage: PageRef?,
    val config: ToolConfig,
    val workspace: NotebookWorkspacePort?,
    val skillStore: SkillManagerPort,
    val webSearch: WebSearchPort,
    /**
     * 本次工具调用读取时序的授权页(null = 未授权)。
     * 只在该次调用经用户同意后由控制器注入,绑定调用发起页;不落盘、不跨调用。
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

    /** 工具对当前请求是否可用:用户开关启用即可;敏感工具靠确认门禁,不靠隐藏 */
    fun isVisible(spec: ToolSpec, config: ToolConfig): Boolean = config.enabled

    /**
     * 已启用工具 → 模型可见声明(按注册顺序,保证提示词与 wire 稳定)。
     * 需要授权的工具同样出现在声明中,由执行前的确认 UI 逐次取得用户同意。
     */
    fun effectiveWireTools(configs: Map<String, ToolConfig>): List<WireToolDef> =
        tools.values.mapNotNull { tool ->
            val spec = tool.spec
            val config = effectiveConfig(spec, configs)
            if (!isVisible(spec, config)) return@mapNotNull null
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
                if (p.type == "array" && p.itemType != null) {
                    put("items", buildJsonObject { put("type", p.itemType) })
                }
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

    /** 数组参数容错读取:真数组、单个字符串(按换行/分号拆分)、JSON 数组字符串都接受 */
    fun stringList(obj: JsonObject, name: String): List<String> {
        val element = obj[name] ?: return emptyList()
        val array = when (element) {
            is kotlinx.serialization.json.JsonArray -> element
            is kotlinx.serialization.json.JsonPrimitive -> {
                if (element is kotlinx.serialization.json.JsonNull) {
                    null
                } else {
                    val text = element.content.trim()
                    if (text.startsWith("[") && text.endsWith("]")) {
                        runCatching {
                            BaseHttpProvider.json.parseToJsonElement(text)
                                as? kotlinx.serialization.json.JsonArray
                        }.getOrNull()
                    } else {
                        null
                    }
                }
            }
            else -> null
        }
        if (array != null) {
            // 真数组/JSON 数组字符串:每个元素就是一条函数,保留元素内的分号与换行
            return array.mapNotNull { item ->
                (item as? kotlinx.serialization.json.JsonPrimitive)
                    ?.takeIf { p -> p !is kotlinx.serialization.json.JsonNull }
                    ?.content
                    ?.trim()
            }.filter { it.isNotEmpty() }
        }
        val single = (element as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it !is kotlinx.serialization.json.JsonNull }
            ?.content
            ?: return emptyList()
        // 只有单个普通字符串 fallback 才按换行/分号拆成多条
        return single.split(Regex("[\\n;]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** 颜色数组容错:保留 null 以维持与表达式的下标对齐(缺失项用调色板) */
    fun colorList(obj: JsonObject, name: String): List<Long?> {
        val element = obj[name] ?: return emptyList()
        val raw = when (element) {
            is kotlinx.serialization.json.JsonArray -> element.map {
                (it as? kotlinx.serialization.json.JsonPrimitive)
                    ?.takeIf { p -> p !is kotlinx.serialization.json.JsonNull }
                    ?.content
            }
            is kotlinx.serialization.json.JsonPrimitive ->
                if (element is kotlinx.serialization.json.JsonNull) emptyList() else listOf(element.content)
            else -> emptyList()
        }
        return raw.flatMap { value ->
            value?.split(',', '\n', ';') ?: listOf<String?>(null)
        }.map { parseColor(it) }
    }

    private fun parseColor(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val hex = when {
            text.startsWith("#") -> text.removePrefix("#")
            text.startsWith("0x", ignoreCase = true) -> text.substring(2)
            else -> null
        }
        if (hex != null) {
            return when (hex.length) {
                6 -> ("FF$hex").toLongOrNull(16)
                8 -> hex.toLongOrNull(16)
                else -> null
            }
        }
        val numeric = text.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
            ?: text.toLongOrNull()
            ?: return null
        val argb = numeric and 0xFFFFFFFFL
        return if (argb ushr 24 == 0L) argb or 0xFF000000L else argb
    }

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
