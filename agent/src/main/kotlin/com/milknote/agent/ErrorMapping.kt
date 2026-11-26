package com.milknote.agent

/**
 * HTTP → AgentError 映射(AGENT-BACKEND §11 错误矩阵)。
 * 文案模糊匹配集中在此一处维护(§16 风险对策)。
 *
 * 每条错误都保留服务端原文(≤8000 字,见 [withBody]):网关/中转站的真实原因
 * (参数名、模型 id、限流窗口)全在这段正文里,截断到 200 字会把排障线索切掉。
 */
object ErrorMapping {

    /** 指向「模型不支持图片输入」的 400 文案特征(§3.4 运行时兜底) */
    private val NOT_VISION_HINTS = listOf(
        "not support", "unsupported", "multimodal", "multi-modal",
        "vision", "image input", "invalid_content", "does not accept image",
        "no image", "images are not",
    )

    /** 指向「图片格式/编码问题」的文案特征(§3.5 imageStyle 自适应重试) */
    private val IMAGE_FORMAT_HINTS = listOf(
        "data uri", "data:", "base64", "image_url", "image url", "invalid image", "malformed",
    )

    /** 保留的错误正文上限(足够覆盖整段 4xx/5xx JSON,又不至于塞爆界面) */
    const val MAX_BODY_CHARS = 8000

    fun fromHttp(code: Int, body: String, retryAfterMs: Long? = null): AgentError {
        val text = body.lowercase()
        return when {
            code == 401 || code == 403 -> AgentError.Auth(withBody("Key 无效或额度不足(HTTP $code)", body))
            code == 529 -> AgentError.Server(withBody("服务过载(HTTP 529),请稍后重试", body))   // Anthropic Overloaded
            code == 404 -> AgentError.NotFound(withBody("接口或模型不存在(HTTP 404),模型 id 可能已下线,请更新", body))
            code == 429 -> AgentError.RateLimit(
                withBody("请求过快(HTTP 429)", body),
                retryAfterMs?.takeIf { it in 1..120_000 },   // 超长指示不采信(≤120s)
            )
            code in 500..599 -> AgentError.Server(withBody("服务方故障(HTTP $code)", body))
            code == 413 || text.contains("too large") || text.contains("payload too large") ->
                AgentError.ContextTooLarge
            code == 400 && NOT_VISION_HINTS.any { text.contains(it) } && !looksLikeImageFormat(text) ->
                AgentError.ModelNotVision(withBody("该模型疑似不支持图片输入(HTTP 400)", body))
            code == 400 && looksLikeImageFormat(text) ->
                AgentError.Other(withBody("图片格式不被接受(HTTP 400)", body))
            else -> AgentError.Other(withBody("请求失败(HTTP $code)", body))
        }
    }

    /** 摘要 + 服务端原文(空正文时不追加,保持纯摘要文案) */
    internal fun withBody(summary: String, body: String): String {
        val raw = body.trim().take(MAX_BODY_CHARS)
        return if (raw.isEmpty()) summary else "$summary\n$raw"
    }

    /** 图片格式/前缀问题(可换 imageStyle 重试,§3.5) */
    fun looksLikeImageFormat(loweredBody: String): Boolean =
        IMAGE_FORMAT_HINTS.any { loweredBody.contains(it) } &&
            !NOT_VISION_HINTS.any { loweredBody.contains(it) }

    fun fromException(t: Throwable): AgentError = when (t) {
        is java.net.SocketTimeoutException -> AgentError.Timeout
        is java.net.UnknownHostException,
        is java.net.ConnectException,
        is java.io.IOException -> AgentError.Network(t.message ?: "网络不可用")
        else -> AgentError.Other(t.message ?: t.javaClass.simpleName)
    }
}
