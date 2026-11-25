package com.milknote.app.ui.agent

import com.milknote.agent.Attachment
import java.util.Locale
import kotlin.math.round

/**
 * AI 面板的纯展示格式化: token 计数 / 元信息行 / 页码与附件标题 / 速度与上下文 / @提及补全 / 错误详情去重。
 * 全部无 Compose 依赖,便于 JVM 单测;UI 只消费结果,不再各自拼字符串。
 */
internal fun formatAgentTokenCount(tokens: Int): String = when {
    tokens < 1000 -> "$tokens"
    tokens < 10_000 -> String.format(Locale.US, "%.1f", tokens / 1000.0).removeSuffix(".0") + "k"
    else -> "${tokens / 1000}k"
}

internal fun formatAgentUsageMeta(
    modelId: String?,
    promptTokens: Int,
    completionTokens: Int,
    generationMs: Long = 0L,
): String {
    val parts = buildList {
        modelId?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        if (promptTokens > 0 || completionTokens > 0) {
            add("${formatAgentTokenCount(promptTokens)}→${formatAgentTokenCount(completionTokens)} tok")
        }
        if (generationMs > 0L && completionTokens > 0) {
            val speed = completionTokens / (generationMs / 1000.0)
            add(formatAgentSpeed(speed))
        }
    }
    return parts.joinToString(" · ")
}

/**
 * 平均输出速度:<100 保留 1 位小数(`8.2 tok/s` / `42.5 tok/s`),
 * ≥100 取整(`120 tok/s`);未知/零/异常值统一一条短横,避免数字跳动误导。
 */
internal fun formatAgentSpeed(tokensPerSec: Double?): String {
    if (tokensPerSec == null || tokensPerSec.isNaN() || tokensPerSec.isInfinite() || tokensPerSec <= 0.0) return "—"
    val rounded = round(tokensPerSec * 10.0) / 10.0
    return if (rounded >= 100.0) {
        String.format(Locale.US, "%.0f tok/s", rounded)
    } else {
        String.format(Locale.US, "%.1f tok/s", rounded)
    }
}

/** 上下文用量:`上下文 3.2k tok`;已知窗口时补上分母 `上下文 3.2k / 128k` */
internal fun formatSessionContext(contextTokens: Int, contextWindowK: Int = 0): String {
    val used = formatAgentTokenCount(contextTokens.coerceAtLeast(0))
    return if (contextWindowK > 0) "上下文 $used / ${contextWindowK}k" else "上下文 $used tok"
}

/** 附件类型角标(卡片右上角/输入栏):图片型统一「图片」,文档按格式缩写 */
internal fun formatAttachmentBadge(source: Attachment.Source): String = when (source) {
    Attachment.Source.SNIP -> "选区"
    Attachment.Source.FULL_PAGE, Attachment.Source.EXTRA_PAGE, Attachment.Source.PROJECTION -> "笔记"
    Attachment.Source.SYSTEM_IMAGE -> "图片"
    Attachment.Source.MARKDOWN_DOC -> "MD"
    Attachment.Source.PDF_DOC -> "PDF"
    Attachment.Source.PPT_DOC -> "PPT"
    Attachment.Source.TEXT_DOC -> "TXT"
}

private val pageIdPattern = Regex("^p0*([1-9]\\d*)$", RegexOption.IGNORE_CASE)
private val pageNumberPattern = Regex("\\d+")

internal fun formatPageDisplayLabel(pageId: String, pageIds: List<String> = emptyList()): String {
    val index = pageIds.indexOf(pageId)
    if (index >= 0) return "第 ${index + 1} 页"
    val number = pageIdPattern.find(pageId)?.groupValues?.getOrNull(1)
    return if (number != null) "第 $number 页" else pageId
}

/** 多页附件名形如 `pages_1-3.png` / `pages_1_3.png` → `第 1-3 页` */
internal fun formatPageRangeLabel(logicalName: String): String {
    val stem = logicalName.substringAfter("pages_").substringBeforeLast('.')
    val numbers = pageNumberPattern.findAll(stem).mapNotNull { it.value.toIntOrNull() }.toList()
    return when {
        numbers.size >= 2 -> "第 ${numbers.first()}-${numbers.last()} 页"
        numbers.size == 1 -> "第 ${numbers.first()} 页"
        else -> logicalName
    }
}

internal fun formatAttachmentTitle(
    source: Attachment.Source,
    logicalName: String,
    pageIds: List<String> = emptyList(),
): String = when (source) {
    Attachment.Source.SNIP -> "选区"
    Attachment.Source.FULL_PAGE, Attachment.Source.EXTRA_PAGE ->
        if (logicalName.startsWith("pages_")) {
            formatPageRangeLabel(logicalName)
        } else {
            formatPageDisplayLabel(pageIdOf(logicalName), pageIds)
        }
    Attachment.Source.PROJECTION -> logicalName.substringBeforeLast('.')
    Attachment.Source.SYSTEM_IMAGE -> logicalName.substringBeforeLast('.').ifBlank { "图片" }
    Attachment.Source.MARKDOWN_DOC, Attachment.Source.PDF_DOC,
    Attachment.Source.PPT_DOC, Attachment.Source.TEXT_DOC -> logicalName.ifBlank { "文档" }
}

private fun pageIdOf(logicalName: String): String {
    val stem = logicalName.removePrefix("page_")
    val extension = listOf(".png", ".jpg", ".jpeg").firstOrNull { stem.endsWith(it, ignoreCase = true) }
    return if (extension != null) stem.dropLast(extension.length) else stem
}

/** 行尾 `@前缀`(前缀不含空白):返回前缀(刚打出 `@` 时为空串),否则 null */
private val trailingMentionPattern = Regex("(^|\\s)@(\\S*)$")

internal fun extractMentionQuery(text: String): String? =
    trailingMentionPattern.find(text)?.groupValues?.get(2)

/** 把行尾 `@前缀` 整体替换为 `@选中名 `(保留其前的空白与分隔) */
internal fun replaceTrailingMention(text: String, mentionName: String): String {
    val match = trailingMentionPattern.find(text) ?: return text
    val head = text.substring(0, match.range.first) + match.groupValues[1]
    return "$head@$mentionName "
}

internal fun errorDetailOrNull(title: String, detail: String): String? =
    detail.trim().takeIf { it.isNotEmpty() && it != title.trim() }
