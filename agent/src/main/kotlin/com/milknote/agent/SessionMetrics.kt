package com.milknote.agent

/**
 * 会话指标(会话列表/详情页展示):
 * - contextTokens:最近一次成功交换的真实用量(prompt+completion);无用量时按图/文本估算
 * - avgTokensPerSec:全部已计时助手消息的加权平均(Σ completion / Σ generationMs)
 */
data class SessionMetrics(
    val contextTokens: Int,
    val avgTokensPerSec: Double?,
    val messageCount: Int,
    val userTurns: Int,
)

/** 会话列表项(抽屉「历史会话」;按 updatedAt 倒序) */
data class SessionListItem(
    val id: String,
    val notebookId: String,
    val pageId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val providerId: String,
    val modelId: String,
    val summary: String,
    val summaryGenerating: Boolean,
    val fallbackPreview: String,
    val metrics: SessionMetrics,
    val isCurrent: Boolean,
)

/** 摘要预览的最大字符数(列表单行) */
private const val PREVIEW_CHARS = 60

private fun AgentMessage.hasUsage(): Boolean = promptTokens > 0 || completionTokens > 0

private fun AgentMessage.countsAsTimedCompletion(): Boolean =
    role == AgentMessage.Role.ASSISTANT &&
        completionTokens > 0 &&
        generationMs > 0 &&
        (phase == AgentMessage.Phase.DONE.name ||
            (phase == AgentMessage.Phase.INTERRUPTED.name && (text.isNotBlank() || reasoning.isNotBlank())))

private fun AgentMessage.estimatedImages(): Int {
    if (phase == AgentMessage.Phase.FAILED.name) return 0
    // 多页文档的封面也在 pageFiles 里:只按逐页图计数,避免重复
    val inline = attachments.count { it.isImage && it.pageFiles.isEmpty() }
    val pages = attachments.sumOf { it.pageFiles.size }
    return inline + pages
}

private fun AgentMessage.estimatedTextChars(): Int {
    if (phase == AgentMessage.Phase.FAILED.name) return 0
    return text.length + reasoning.length + attachments.sumOf { it.textContent.length }
}

/** 上下文规模:优先真实用量,退化到图×1.1k + 文本/1.6 的估算(§7.3) */
fun AgentSession.computeMetrics(): SessionMetrics {
    val live = messages.filter { it.phase != AgentMessage.Phase.FAILED.name }
    val lastUsage = messages.lastOrNull {
        it.role == AgentMessage.Role.ASSISTANT && it.hasUsage()
    }
    val contextTokens = if (lastUsage != null) {
        lastUsage.promptTokens + lastUsage.completionTokens
    } else {
        ImageBudget.estTokens(
            live.sumOf { it.estimatedImages() },
            live.sumOf { it.estimatedTextChars() },
        )
    }

    val timed = messages.filter { it.countsAsTimedCompletion() }
    val avg = if (timed.isEmpty()) {
        null
    } else {
        val tokens = timed.sumOf { it.completionTokens }
        val ms = timed.sumOf { it.generationMs }
        if (ms > 0) tokens.toDouble() / (ms.toDouble() / 1000.0) else null
    }

    return SessionMetrics(
        contextTokens = contextTokens,
        avgTokensPerSec = avg,
        messageCount = messages.size,
        userTurns = messages.count { it.role == AgentMessage.Role.USER },
    )
}

/** 列表兜底预览:最后一条有文字的已完成消息(摘要为空时展示) */
fun AgentSession.fallbackPreview(): String {
    val last = messages.lastOrNull {
        it.phase != AgentMessage.Phase.FAILED.name && it.text.isNotBlank()
    } ?: return title
    val line = last.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return title
    return if (line.length <= PREVIEW_CHARS) line else line.take(PREVIEW_CHARS).trimEnd() + "…"
}

/** 会话 → 列表项(isCurrent/summaryGenerating 由控制器注入) */
fun AgentSession.toListItem(
    isCurrent: Boolean,
    summaryGenerating: Boolean = false,
): SessionListItem = SessionListItem(
    id = id,
    notebookId = notebookId,
    pageId = pageId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    providerId = providerId,
    modelId = modelId,
    summary = summary,
    summaryGenerating = summaryGenerating,
    fallbackPreview = fallbackPreview(),
    metrics = computeMetrics(),
    isCurrent = isCurrent,
)
