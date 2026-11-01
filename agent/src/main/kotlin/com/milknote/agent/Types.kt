package com.milknote.agent

import kotlinx.serialization.Serializable

// =====================================================================
// 核心类型(AGENT-BACKEND.md §2.3 / §3 / §4.3 / §7)
// 本模块不依赖 ink-engine / note-domain:页面渲染走 SnipSource 端口,
// 批注写入走 AnnotationSink 端口(装配在 app 层)。
// =====================================================================

/** 视觉能力来源(§3.4 声明式约束) */
enum class VisionSource { PRESET, METADATA, DECLARED }

/** 候选模型(预设=已验证视觉;自定义=用户声明;OpenRouter=元数据) */
@Serializable
data class ModelInfo(
    val id: String,
    val displayName: String = id,
    val vision: Boolean = true,
    val visionSource: String = VisionSource.PRESET.name,
    val maxImages: Int = 8,
    val priceInPerM: Double? = null,   // ¥/1M tokens(可空)
    val priceOutPerM: Double? = null,
)

/** 图片 base64 组装风格(§3.5:GLM 端点裸 base64,其余 data URI) */
@Serializable
enum class ImageStyle { DATA_URI, RAW_BASE64 }

/** 用户自填的配置单元(§3.2):一家端点 + 一个 Key 引用 + N 模型 */
@Serializable
data class ProviderProfile(
    val id: String,
    val presetId: String? = null,      // 预设来源(自定义=null)
    val title: String,
    val baseUrl: String,
    val apiKeyAlias: String,           // KeyVault 引用,明文 Key 不落此表
    val models: List<ModelInfo> = emptyList(),
    val activeModelId: String = "",
    val imageStyle: ImageStyle = ImageStyle.DATA_URI,
) {
    fun activeModel(): ModelInfo? = models.firstOrNull { it.id == activeModelId }
        ?: models.firstOrNull { it.vision }
}

/** 页引用(PageBound;Global 为 M3 Projection 预留) */
@Serializable
data class PageRef(val notebookId: String, val pageId: String)

/** 附件(§4.3):内容寻址落盘 <nb>.milk/agent/att/<sha1>.png */
@Serializable
data class Attachment(
    val id: String,
    val file: String,                 // 相对 notebook .milk 目录:agent/att/<sha1>.png
    val sha1: String,
    val widthPx: Int,
    val heightPx: Int,
    val source: Source,
    val logicalName: String,          // snip_1246×742.png(附件卡显示名)
    val redacted: Int = 0,            // 脱敏涂抹处数(M2 恒 0,ML Kit 在 M3 边界外)
) {
    enum class Source { SNIP, FULL_PAGE, EXTRA_PAGE, PROJECTION }
}

// ---- 会话模型(§7.1) ----

@Serializable
data class AgentMessage(
    val id: String,
    val role: Role,
    val sentAt: Long,
    val text: String,                            // 用户补充文字 / 助手累积文本
    val attachments: List<Attachment> = emptyList(),
    val intent: String? = null,                  // 首条用户消息记录
    val modelId: String? = null,                 // 助手侧:实际使用的模型
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val phase: String = Phase.DONE.name,         // SENDING/STREAMING/DONE/FAILED/INTERRUPTED
    val insertedAnnotationId: String? = null,    // 插入批注幂等标记(§9)
) {
    enum class Role { USER, ASSISTANT }

    enum class Phase { SENDING, STREAMING, DONE, FAILED, INTERRUPTED }
}

@Serializable
data class AgentSession(
    val id: String,
    val notebookId: String,
    val pageId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val providerId: String,
    val modelId: String,
    val messages: List<AgentMessage> = emptyList(),
)

// ---- 对话协议(§3.1 / §3.5 OpenAI 兼容) ----

/** 请求 wire 消息(历史仅文字;图片永远只进最后一条 user,§6.2 图不重发) */
data class WireMessage(val role: String, val content: String)

data class ChatRequest(
    val model: String,
    val system: String,
    val history: List<WireMessage> = emptyList(),
    val newUserText: String,
    val images: List<String> = emptyList(),   // base64(无前缀),语义序:选区在前
    val imageStyle: ImageStyle = ImageStyle.DATA_URI,
    val temperature: Double = 0.3,
    val maxTokens: Int? = null,               // 测试连接 =1
    val stream: Boolean = true,
)

sealed interface ChatEvent {
    data class Delta(val text: String) : ChatEvent
    data class Usage(val promptTokens: Int, val completionTokens: Int) : ChatEvent
    data class Done(val finishReason: String?) : ChatEvent
    data class Error(val error: AgentError) : ChatEvent
}

/** 结构化错误(§11 错误矩阵) */
sealed interface AgentError {
    val detail: String

    data object NoProvider : AgentError { override val detail = "未配置视觉模型" }
    data object NotVision : AgentError { override val detail = "该模型非视觉模型,不可选" }
    data class Auth(override val detail: String) : AgentError
    data class RateLimit(override val detail: String) : AgentError
    data class Server(override val detail: String) : AgentError
    data class Network(override val detail: String) : AgentError
    data object Timeout : AgentError { override val detail = "网络超时" }
    data class ModelNotVision(override val detail: String) : AgentError
    data class NotFound(override val detail: String) : AgentError
    data object ContextTooLarge : AgentError { override val detail = "上下文过大" }
    data object StreamBroken : AgentError { override val detail = "流式传输中断" }
    data object Cancelled : AgentError { override val detail = "已取消" }
    data class Other(override val detail: String) : AgentError
}

/** 纯 Kotlin 矩形(页坐标 px;不引 android.graphics 以保 JVM 单测) */
data class Rect4(val l: Float, val t: Float, val r: Float, val b: Float) {
    val width: Float get() = r - l
    val height: Float get() = b - t
    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    fun normalized(): Rect4 = Rect4(minOf(l, r), minOf(t, b), maxOf(l, r), maxOf(t, b))

    companion object {
        /** 手指拖出的任意方向矩形 → 正向 */
        fun fromDrag(x0: Float, y0: Float, x1: Float, y1: Float): Rect4 =
            Rect4(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1))
    }
}
