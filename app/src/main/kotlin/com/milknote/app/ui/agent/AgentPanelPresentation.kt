package com.milknote.app.ui.agent

import com.milknote.agent.Attachment
import java.util.Locale

/**
 * AI 面板的纯展示格式化: token 计数 / 元信息行 / 页码与附件标题 / 错误详情去重。
 * 全部无 Compose 依赖,便于 JVM 单测;UI 只消费结果,不再各自拼字符串。
 */
internal fun formatAgentTokenCount(tokens: Int): String = when {
    tokens < 1000 -> "$tokens"
    tokens < 10_000 -> String.format(Locale.US, "%.1f", tokens / 1000.0).removeSuffix(".0") + "k"
    else -> "${tokens / 1000}k"
}

internal fun formatAgentUsageMeta(modelId: String?, promptTokens: Int, completionTokens: Int): String {
    val parts = buildList {
        modelId?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        if (promptTokens > 0 || completionTokens > 0) {
            add("${formatAgentTokenCount(promptTokens)}→${formatAgentTokenCount(completionTokens)} tok")
        }
    }
    return parts.joinToString(" · ")
}

private val pageIdPattern = Regex("^p0*([1-9]\\d*)$", RegexOption.IGNORE_CASE)

internal fun formatPageDisplayLabel(pageId: String, pageIds: List<String> = emptyList()): String {
    val index = pageIds.indexOf(pageId)
    if (index >= 0) return "第 ${index + 1} 页"
    val number = pageIdPattern.find(pageId)?.groupValues?.getOrNull(1)
    return if (number != null) "第 $number 页" else pageId
}

internal fun formatAttachmentTitle(
    source: Attachment.Source,
    logicalName: String,
    pageIds: List<String> = emptyList(),
): String = when (source) {
    Attachment.Source.SNIP -> "选区"
    Attachment.Source.FULL_PAGE, Attachment.Source.EXTRA_PAGE ->
        formatPageDisplayLabel(pageIdOf(logicalName), pageIds)
    Attachment.Source.PROJECTION -> logicalName.substringBeforeLast('.')
}

private fun pageIdOf(logicalName: String): String {
    val stem = logicalName.removePrefix("page_")
    val extension = listOf(".png", ".jpg", ".jpeg").firstOrNull { stem.endsWith(it, ignoreCase = true) }
    return if (extension != null) stem.dropLast(extension.length) else stem
}

internal fun errorDetailOrNull(title: String, detail: String): String? =
    detail.trim().takeIf { it.isNotEmpty() && it != title.trim() }
