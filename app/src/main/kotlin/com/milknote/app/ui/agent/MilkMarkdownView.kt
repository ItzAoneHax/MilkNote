package com.milknote.app.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.common.MilkInlineAction
import com.milknote.app.ui.common.MilkInlineTone
import com.milknote.app.ui.common.MilkStatusBadge
import com.milknote.app.ui.common.MilkStatusTone
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * 标准 Markdown + LaTeX 渲染器(AI 回复 / 笔记预览 / 生成文档共用一个实现)。
 *
 * 覆盖:H1–H6、段落行内富文本(粗/斜/粗斜/删除线/代码/`$行内公式$`/链接/`[[双链]]`/`@提及`)、
 * GFM 表格(列对齐 + 横向滚动)、围栏代码块(语言角标 + 一键复制)、引用、嵌套/任务列表、
 * 分隔线、块级公式([LatexDisplayBlock])、生成文档卡(预览/写入/导出/存卡片)。
 *
 * 样式只取 MilkNote Gen2 token(MilkText/MilkRadius/MilkSpace/MilkInlineAction/MilkStatusBadge),
 * 不再出现页面里散写的字号与裸胶囊。
 */
@Composable
fun MilkMarkdownView(
    text: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    onWikilink: ((String) -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null,
    onFormulaClick: ((String) -> Unit)? = null,
    onPreviewDocument: ((MarkdownLite.Block.GeneratedDocument) -> Unit)? = null,
    onSaveDocumentToNotes: ((MarkdownLite.Block.GeneratedDocument) -> Unit)? = null,
    onSaveDocumentCard: ((MarkdownLite.Block.GeneratedDocument) -> Unit)? = null,
    onExportDocument: ((MarkdownLite.Block.GeneratedDocument) -> Unit)? = null,
) {
    val blocks = remember(text) { MarkdownLite.parse(text) }
    val actions = MarkdownActions(
        onWikilink = onWikilink,
        onMentionClick = onMentionClick,
        onFormulaClick = onFormulaClick,
        onPreviewDocument = onPreviewDocument,
        onSaveDocumentToNotes = onSaveDocumentToNotes,
        onSaveDocumentCard = onSaveDocumentCard,
        onExportDocument = onExportDocument,
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MilkSpace.xs),
    ) {
        blocks.forEach { block -> MilkMarkdownBlock(block, streaming, actions) }
    }
}

private class MarkdownActions(
    val onWikilink: ((String) -> Unit)? = null,
    val onMentionClick: ((String) -> Unit)? = null,
    val onFormulaClick: ((String) -> Unit)? = null,
    val onPreviewDocument: ((MarkdownLite.Block.GeneratedDocument) -> Unit)? = null,
    val onSaveDocumentToNotes: ((MarkdownLite.Block.GeneratedDocument) -> Unit)? = null,
    val onSaveDocumentCard: ((MarkdownLite.Block.GeneratedDocument) -> Unit)? = null,
    val onExportDocument: ((MarkdownLite.Block.GeneratedDocument) -> Unit)? = null,
)

@Composable
private fun MilkMarkdownBlock(
    block: MarkdownLite.Block,
    streaming: Boolean,
    actions: MarkdownActions,
) {
    when (block) {
        is MarkdownLite.Block.Heading -> HeadingBlock(block, actions)
        is MarkdownLite.Block.Paragraph -> InlineRichText(block.spans, MilkText.body, actions)
        is MarkdownLite.Block.ListItem -> ListItemBlock(block, actions)
        is MarkdownLite.Block.TaskItem -> TaskItemBlock(block, actions)
        is MarkdownLite.Block.MathBlock -> LatexDisplayBlock(
            latex = block.latex,
            streaming = streaming,
            onOpenZoom = actions.onFormulaClick?.let { click -> { click(block.latex) } },
        )
        is MarkdownLite.Block.TagLine -> TagLineBlock(block.tags)
        is MarkdownLite.Block.Table -> TableBlock(block, actions)
        is MarkdownLite.Block.CodeBlock -> CodeBlockView(block)
        is MarkdownLite.Block.Quote -> QuoteBlock(block, streaming, actions)
        MarkdownLite.Block.HorizontalRule -> HorizontalDivider(
            Modifier.padding(vertical = MilkSpace.xxs),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
        )
        is MarkdownLite.Block.GeneratedDocument -> GeneratedDocumentCard(block, actions)
    }
}

// ---------------- 文本类 ----------------

@Composable
private fun HeadingBlock(block: MarkdownLite.Block.Heading, actions: MarkdownActions) {
    val style = when (block.level.coerceIn(1, 6)) {
        1 -> MilkText.sectionTitle
        2 -> MilkText.panelTitle
        3 -> MilkText.cardTitle
        4 -> MilkText.rowTitle
        else -> MilkText.rowTitle
    }
    Box(Modifier.padding(top = if (block.level <= 2) MilkSpace.xxs else 0.dp)) {
        InlineRichText(block.spans, style, actions)
    }
}

@Composable
private fun ListItemBlock(block: MarkdownLite.Block.ListItem, actions: MarkdownActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = (block.indent.coerceIn(0, 8) * 14).dp),
    ) {
        Text(
            text = block.order?.let { "$it." } ?: "•",
            style = MilkText.body,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(min = 18.dp),
        )
        Spacer(Modifier.width(MilkSpace.xxs))
        InlineRichText(block.spans, MilkText.body, actions, Modifier.weight(1f))
    }
}

@Composable
private fun TaskItemBlock(block: MarkdownLite.Block.TaskItem, actions: MarkdownActions) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = (block.indent.coerceIn(0, 8) * 14).dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (block.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription = if (block.checked) "已完成" else "未完成",
            tint = if (block.checked) cs.primary else cs.onSurfaceVariant,
            modifier = Modifier.size(17.dp).padding(top = 1.dp),
        )
        Spacer(Modifier.width(MilkSpace.xs))
        InlineRichText(
            spans = block.spans,
            style = MilkText.body,
            actions = actions,
            modifier = Modifier.weight(1f),
            strike = block.checked,
            color = if (block.checked) cs.onSurfaceVariant else cs.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagLineBlock(tags: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
        verticalArrangement = Arrangement.spacedBy(MilkSpace.xxs),
    ) {
        tags.take(12).forEach { tag ->
            MilkStatusBadge(text = "#$tag", tone = MilkStatusTone.ACCENT, mono = true)
        }
    }
}

/**
 * 行内富文本:一次性拼 AnnotatedString(双链/链接/提及带注解并可点),`$…$` 转成 Unicode 数学体,
 * 保证段落内不会出现 `\frac{a}{b}` 这类未渲染源码。
 */
@Composable
private fun InlineRichText(
    spans: List<MarkdownLite.Span>,
    style: TextStyle,
    actions: MarkdownActions,
    modifier: Modifier = Modifier,
    strike: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val cs = MaterialTheme.colorScheme
    val codeBg = cs.onSurface.copy(alpha = if (LocalMilkDark.current) 0.10f else 0.06f)
    val uriHandler = LocalUriHandler.current
    val annotated = remember(spans, cs.primary, cs.onSurface, codeBg, strike) {
        buildInlineAnnotated(spans, cs.primary, cs.onSurface, codeBg, strike)
    }
    val interactive = remember(annotated) {
        annotated.getStringAnnotations("wiki", 0, annotated.length).isNotEmpty() ||
            annotated.getStringAnnotations("mention", 0, annotated.length).isNotEmpty() ||
            annotated.getStringAnnotations("url", 0, annotated.length).isNotEmpty()
    }
    val resolvedStyle = style.copy(color = color)
    if (!interactive) {
        Text(text = annotated, modifier = modifier, style = resolvedStyle)
        return
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = resolvedStyle,
        onClick = { offset ->
            annotated.getStringAnnotations("wiki", offset, offset).firstOrNull()
                ?.let { actions.onWikilink?.invoke(it.item) }
                ?: annotated.getStringAnnotations("mention", offset, offset).firstOrNull()
                    ?.let { actions.onMentionClick?.invoke(it.item) }
                ?: annotated.getStringAnnotations("url", offset, offset).firstOrNull()
                    ?.let { runCatching { uriHandler.openUri(it.item) } }
        },
    )
}

private fun buildInlineAnnotated(
    spans: List<MarkdownLite.Span>,
    accent: Color,
    ink: Color,
    codeBg: Color,
    strike: Boolean,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    spans.forEach { span ->
        when (span) {
            is MarkdownLite.Span.Plain -> builder.append(span.text)
            is MarkdownLite.Span.Bold -> builder.styled(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(span.text)
            }
            is MarkdownLite.Span.Italic -> builder.styled(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(span.text)
            }
            is MarkdownLite.Span.BoldItalic -> builder.styled(
                SpanStyle(fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic),
            ) { append(span.text) }
            is MarkdownLite.Span.Strike -> builder.styled(
                SpanStyle(textDecoration = TextDecoration.LineThrough, color = ink.copy(alpha = 0.75f)),
            ) { append(span.text) }
            is MarkdownLite.Span.Code -> builder.styled(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg),
            ) { append(span.text) }
            is MarkdownLite.Span.Math -> builder.styled(
                SpanStyle(fontFamily = FontFamily.Serif, color = accent),
            ) { append(LatexUnicode.inline(span.latex)) }
            is MarkdownLite.Span.Link -> {
                builder.pushStyle(
                    SpanStyle(color = accent, textDecoration = TextDecoration.Underline),
                )
                builder.pushStringAnnotation("url", span.url)
                builder.append(span.label.ifBlank { span.url })
                builder.pop()
                builder.pop()
            }
            is MarkdownLite.Span.Image -> builder.styled(
                SpanStyle(color = ink.copy(alpha = 0.7f), fontStyle = FontStyle.Italic),
            ) { append("［图：${span.alt.ifBlank { "图片" }}］") }
            is MarkdownLite.Span.Wikilink -> {
                builder.pushStyle(
                    SpanStyle(
                        color = accent,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                builder.pushStringAnnotation("wiki", span.target)
                builder.append(span.target)
                builder.pop()
                builder.pop()
            }
            is MarkdownLite.Span.Mention -> {
                builder.pushStyle(
                    SpanStyle(color = accent, background = accent.copy(alpha = 0.10f), fontWeight = FontWeight.Medium),
                )
                builder.pushStringAnnotation("mention", span.name)
                builder.append("@${span.name}")
                builder.pop()
                builder.pop()
            }
        }
    }
    val raw = builder.toAnnotatedString()
    if (!strike) return raw
    return buildAnnotatedString {
        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
        append(raw)
        pop()
    }
}

private inline fun AnnotatedString.Builder.styled(style: SpanStyle, block: AnnotatedString.Builder.() -> Unit) {
    pushStyle(style)
    block()
    pop()
}

// ---------------- 表格 ----------------

@Composable
private fun TableBlock(block: MarkdownLite.Block.Table, actions: MarkdownActions) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val widths = remember(block) { columnWidths(block) }
    val shape = RoundedCornerShape(MilkRadius.md)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier
                .clip(shape)
                .border(1.dp, cs.outline.copy(alpha = 0.65f), shape),
        ) {
            Row(Modifier.background(cs.surfaceVariant.copy(alpha = if (dark) 0.55f else 0.7f))) {
                block.headers.forEachIndexed { idx, cell ->
                    TableCell(
                        spans = cell,
                        widthDp = widths.getOrElse(idx) { 80 },
                        align = block.alignments.getOrNull(idx),
                        header = true,
                        actions = actions,
                    )
                }
            }
            block.rows.forEachIndexed { rowIndex, row ->
                val shade = if (rowIndex % 2 == 1 && block.rows.size > 1) {
                    cs.onSurface.copy(alpha = if (dark) 0.035f else 0.022f)
                } else {
                    Color.Transparent
                }
                Row(Modifier.background(shade)) {
                    block.headers.indices.forEach { idx ->
                        TableCell(
                            spans = row.getOrNull(idx).orEmpty(),
                            widthDp = widths.getOrElse(idx) { 80 },
                            align = block.alignments.getOrNull(idx),
                            header = false,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    spans: List<MarkdownLite.Span>,
    widthDp: Int,
    align: MarkdownLite.TableAlign?,
    header: Boolean,
    actions: MarkdownActions,
) {
    val cs = MaterialTheme.colorScheme
    val alignment = when (align) {
        MarkdownLite.TableAlign.CENTER -> Alignment.Center
        MarkdownLite.TableAlign.RIGHT -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }
    Box(
        Modifier
            .width(widthDp.dp)
            .background(Color.Transparent)
            .border(0.5.dp, cs.outline.copy(alpha = 0.45f))
            .padding(horizontal = MilkSpace.sm, vertical = MilkSpace.xs),
        contentAlignment = alignment,
    ) {
        if (spans.isEmpty()) {
            Spacer(Modifier.height(1.dp))
        } else {
            InlineRichText(
                spans = spans,
                style = if (header) MilkText.label else MilkText.caption,
                actions = actions,
                color = cs.onSurface,
            )
        }
    }
}

/** 列宽按字符数估算(等宽数格子),夹在 64–240dp;宽表整体横向滚动 */
private fun columnWidths(block: MarkdownLite.Block.Table): IntArray {
    val cols = block.headers.size.coerceAtLeast(1)
    return IntArray(cols) { idx ->
        var longest = block.headers.getOrNull(idx)?.spanLength() ?: 0
        block.rows.forEach { row ->
            longest = max(longest, row.getOrNull(idx)?.spanLength() ?: 0)
        }
        (longest * 8 + 26).coerceIn(64, 240)
    }
}

private fun List<MarkdownLite.Span>.spanLength(): Int {
    var total = 0
    forEach { span ->
        total += when (span) {
            is MarkdownLite.Span.Code -> span.text.length
            is MarkdownLite.Span.Math -> span.latex.length
            else -> span.plainText().length
        }
    }
    return total
}

// ---------------- 代码 / 引用 ----------------

@Composable
private fun CodeBlockView(block: MarkdownLite.Block.CodeBlock) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val clipboard = LocalClipboardManager.current
    var copied by remember(block.code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    val shape = RoundedCornerShape(MilkRadius.md)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, cs.outline.copy(alpha = 0.6f), shape)
            .background(cs.surfaceVariant.copy(alpha = if (dark) 0.3f else 0.45f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = MilkSpace.md, end = MilkSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = block.language.ifBlank { "text" }.uppercase(),
                style = MilkText.monoMicro,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            MilkInlineAction(
                label = if (copied) "已复制" else "复制",
                onClick = {
                    clipboard.setText(AnnotatedString(block.code))
                    copied = true
                },
                icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                tone = if (copied) MilkInlineTone.SUCCESS else MilkInlineTone.NEUTRAL,
                minWidth = 74.dp,
            )
        }
        HorizontalDivider(color = cs.outline.copy(alpha = 0.5f))
        Text(
            text = block.code,
            style = MilkText.monoBody,
            color = cs.onSurface,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm),
        )
    }
}

@Composable
private fun QuoteBlock(block: MarkdownLite.Block.Quote, streaming: Boolean, actions: MarkdownActions) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(2.5.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(MilkRadius.pill))
                .background(cs.primary.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.width(MilkSpace.md))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MilkSpace.xxs),
        ) {
            block.blocks.forEach { inner -> MilkMarkdownBlock(inner, streaming, actions) }
        }
    }
}

// ---------------- 生成文档卡 ----------------

@Composable
private fun GeneratedDocumentCard(
    doc: MarkdownLite.Block.GeneratedDocument,
    actions: MarkdownActions,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(MilkRadius.lg)
    val lines = remember(doc.content) { if (doc.content.isEmpty()) 0 else doc.content.count { it == '\n' } + 1 }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, cs.outline.copy(alpha = 0.75f), shape)
            .background(cs.surface)
            .padding(MilkSpace.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Article,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(MilkSpace.sm))
            Text(
                text = doc.title,
                style = MilkText.rowTitle,
                color = cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(MilkSpace.xs))
            MilkStatusBadge(
                text = when (doc.type) {
                    MarkdownLite.GeneratedDocType.MARKDOWN -> "MD"
                    MarkdownLite.GeneratedDocType.TEXT -> "TXT"
                    MarkdownLite.GeneratedDocType.LATEX -> "LaTeX"
                },
                tone = MilkStatusTone.ACCENT,
                mono = true,
            )
        }
        Spacer(Modifier.height(MilkSpace.xxs))
        Text(
            text = "$lines 行 · ${doc.content.length} 字符",
            style = MilkText.monoMeta,
            color = cs.onSurfaceVariant,
        )
        if (actions.onPreviewDocument != null || actions.onSaveDocumentToNotes != null ||
            actions.onSaveDocumentCard != null || actions.onExportDocument != null
        ) {
            Spacer(Modifier.height(MilkSpace.sm))
            FlowRowCompact {
                actions.onPreviewDocument?.let { preview ->
                    MilkInlineAction(
                        label = "悬浮预览",
                        onClick = { preview(doc) },
                        icon = Icons.Outlined.OpenInFull,
                        tone = MilkInlineTone.ACCENT,
                    )
                }
                actions.onSaveDocumentToNotes?.let { save ->
                    MilkInlineAction(
                        label = "写入笔记",
                        onClick = { save(doc) },
                        icon = Icons.Outlined.PostAdd,
                    )
                }
                actions.onExportDocument?.let { export ->
                    MilkInlineAction(
                        label = "导出",
                        onClick = { export(doc) },
                        icon = Icons.Outlined.FileDownload,
                    )
                }
                actions.onSaveDocumentCard?.let { card ->
                    MilkInlineAction(
                        label = "存卡片",
                        onClick = { card(doc) },
                        icon = Icons.Outlined.BookmarkBorder,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCompact(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
        verticalArrangement = Arrangement.spacedBy(MilkSpace.xs),
        content = { content() },
    )
}
