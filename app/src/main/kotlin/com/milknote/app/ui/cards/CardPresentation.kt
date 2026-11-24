package com.milknote.app.ui.cards

import com.milknote.app.ui.agent.MarkdownLite

private val wikilink = Regex("\\[\\[([^\\]]+)\\]\\]")

internal fun cardPreviewText(title: String, body: String): String {
    val trimmed = body.trim()
    val firstLine = trimmed.lineSequence().firstOrNull()?.trim().orEmpty()
    val normalizedTitle = firstLine.trimStart('#', ' ', '*').trim()
    val content = if (title.isNotBlank() && normalizedTitle == title.trim()) {
        trimmed.lineSequence().drop(1).joinToString("\n")
    } else {
        trimmed
    }
    return MarkdownLite.parse(content).mapNotNull { block ->
        when (block) {
            is MarkdownLite.Block.Heading -> spansText(block.spans)
            is MarkdownLite.Block.ListItem -> "${block.order?.let { "$it. " } ?: "• "}${spansText(block.spans)}"
            is MarkdownLite.Block.Paragraph -> spansText(block.spans)
            is MarkdownLite.Block.MathBlock -> block.latex
            is MarkdownLite.Block.TagLine -> null
        }?.trim()?.takeIf { it.isNotEmpty() }
    }.joinToString("\n")
}

internal fun cardSourceLabel(notebookName: String?, pageIds: List<String>, sourcePageId: String?): String {
    if (notebookName == null) return "来源不可用"
    if (sourcePageId == null) return "$notebookName · 页码未知"
    val index = pageIds.indexOf(sourcePageId)
    return if (index >= 0) "$notebookName · 第 ${index + 1} 页" else "$notebookName · 页面不可用"
}

private fun spansText(spans: List<MarkdownLite.Span>): String = spans.joinToString("") { span ->
    when (span) {
        is MarkdownLite.Span.Plain -> span.text.replace(wikilink, "$1")
        is MarkdownLite.Span.Bold -> span.text
        is MarkdownLite.Span.Code -> span.text
        is MarkdownLite.Span.Math -> span.latex
    }
}
