package com.milknote.app.ui.agent

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.milknote.agent.AgentMessage
import com.milknote.agent.AgentUiState
import com.milknote.agent.Attachment
import com.milknote.agent.DraftAttachment
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.agent.media.DocumentAttachmentLoader
import com.milknote.app.ui.agent.media.DocumentKind
import com.milknote.app.ui.agent.media.ImageEditEngine
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonSize
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkEmptyState
import com.milknote.app.ui.common.MilkHairlineDivider
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.MilkStatusBadge
import com.milknote.app.ui.common.MilkStatusTone
import com.milknote.app.ui.common.milkDialogSurface
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.milkPanelEnter
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 导出目录(与 `res/xml/file_paths.xml` 的 `export/` 对齐;FileProvider 只放行这一处) */
private const val EXPORT_DIR = "export"

/** 本地文档预览最多读取的页数(逐页渲染成图,防止超大 PDF 拖垮内存) */
private const val LOCAL_PREVIEW_MAX_PAGES = 60

/** 预览中心分类键 */
private const val HUB_ALL = "all"
private const val HUB_IMAGE = "image"
private const val HUB_DOC = "doc"
private const val HUB_MARKDOWN = "markdown"

/**
 * 统一预览目标(悬浮预览窗与预览中心共用):
 * 图片 / 基础文档(PDF/PPT/TXT)/ Markdown 与 AI 生成文档。
 * 只带「必要的字节或引用」,真正的落盘读取交给预览窗按需完成。
 */
sealed interface AgentPreviewTarget {
    val title: String

    data class Image(
        override val title: String,
        val subtitle: String = "",
        val bytes: ByteArray? = null,
        val notebookId: String? = null,
        val attachment: Attachment? = null,
        val draftId: String? = null,
        val canEdit: Boolean = false,
    ) : AgentPreviewTarget

    data class Document(
        override val title: String,
        val source: Attachment.Source,
        val pageRangeLabel: String = "",
        val pageBytes: List<ByteArray> = emptyList(),
        val notebookId: String? = null,
        val pageFiles: List<String> = emptyList(),
        val textContent: String = "",
        val attachment: Attachment? = null,
    ) : AgentPreviewTarget

    data class Markdown(
        override val title: String,
        val content: String,
        val docType: MarkdownLite.GeneratedDocType = MarkdownLite.GeneratedDocType.MARKDOWN,
        val canSaveToNotes: Boolean = true,
        val canSaveCard: Boolean = true,
    ) : AgentPreviewTarget
}

// ==================== 转换:任意附件/生成文档 → 预览目标 ====================

/** 草稿附件(未落盘,字节在内存里):多页 → 文档;Markdown → 渲染预览;单图 → 图片(可裁剪编辑);其余 → 文档 */
fun DraftAttachment.toPreviewTarget(pageIds: List<String> = emptyList()): AgentPreviewTarget {
    if (extraPages.isNotEmpty()) {
        return AgentPreviewTarget.Document(
            title = logicalName.ifBlank { "文档" },
            source = source,
            pageRangeLabel = pageRangeLabel,
            pageBytes = extraPages.map { it.png },
            textContent = textContent,
        )
    }
    if (png.isEmpty()) {
        if (source == Attachment.Source.MARKDOWN_DOC) {
            return AgentPreviewTarget.Markdown(
                title = logicalName.ifBlank { "Markdown 文档" },
                content = textContent,
                docType = MarkdownLite.GeneratedDocType.MARKDOWN,
                canSaveToNotes = false,
            )
        }
        return AgentPreviewTarget.Document(
            title = logicalName.ifBlank { "文档" },
            source = source,
            pageRangeLabel = pageRangeLabel,
            textContent = textContent,
        )
    }
    return AgentPreviewTarget.Image(
        title = formatAttachmentTitle(source, logicalName, pageIds),
        subtitle = buildString {
            if (widthPx > 0 && heightPx > 0) append("${widthPx}×${heightPx}")
            if (pageRangeLabel.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(pageRangeLabel)
            }
        },
        bytes = png,
        draftId = id,
        canEdit = source != Attachment.Source.MARKDOWN_DOC && source != Attachment.Source.TEXT_DOC,
    )
}

/** 已落盘附件:Markdown → 渲染预览;PDF/PPT/TXT → 文档;其余 → 图片(按需读盘) */
fun Attachment.toPreviewTarget(
    notebookId: String?,
    pageIds: List<String> = emptyList(),
): AgentPreviewTarget {
    val title = formatAttachmentTitle(source, logicalName, pageIds)
    return when (source) {
        Attachment.Source.MARKDOWN_DOC -> AgentPreviewTarget.Markdown(
            title = title,
            content = textContent,
            docType = MarkdownLite.GeneratedDocType.MARKDOWN,
            canSaveToNotes = false,
            canSaveCard = false,
        )

        Attachment.Source.PDF_DOC, Attachment.Source.PPT_DOC, Attachment.Source.TEXT_DOC ->
            AgentPreviewTarget.Document(
                title = title,
                source = source,
                pageRangeLabel = pageRangeLabel,
                notebookId = notebookId,
                pageFiles = pageFiles,
                textContent = textContent,
                attachment = this,
            )

        else -> AgentPreviewTarget.Image(
            title = title,
            subtitle = buildString {
                if (widthPx > 0 && heightPx > 0) append("${widthPx}×${heightPx}")
                if (pageRangeLabel.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(pageRangeLabel)
                }
            },
            notebookId = notebookId,
            attachment = this,
        )
    }
}

/** AI 生成文档 → Markdown 预览(LaTeX 会补 $$ 包裹后交给离线 KaTeX) */
fun MarkdownLite.Block.GeneratedDocument.toPreviewTarget(): AgentPreviewTarget =
    AgentPreviewTarget.Markdown(
        title = title.ifBlank {
            when (type) {
                MarkdownLite.GeneratedDocType.LATEX -> "LaTeX 文档"
                MarkdownLite.GeneratedDocType.TEXT -> "文本文档"
                MarkdownLite.GeneratedDocType.MARKDOWN -> "Markdown 文档"
            }
        },
        content = content,
        docType = type,
    )

/**
 * 汇总当前会话可预览的全部内容:草稿附件 → 消息附件 → AI 生成文档 → 当前页 Markdown。
 * 顺序即展示顺序(草稿在最前,便于发送前核对),完全同名的重复项只留一份。
 */
fun collectSessionPreviewItems(
    state: AgentUiState,
    pageMarkdown: String,
    pageLabel: String,
    pageIds: List<String> = emptyList(),
): List<AgentPreviewTarget> {
    val notebookId = state.boundPage?.notebookId
    val collected = ArrayList<AgentPreviewTarget>()

    state.draftAttachments.forEach { collected += it.toPreviewTarget(pageIds) }

    state.messages.asReversed().forEach { message ->
        message.attachments.forEach { collected += it.toPreviewTarget(notebookId, pageIds) }
        if (message.role == AgentMessage.Role.ASSISTANT && message.text.isNotBlank()) {
            MarkdownLite.extractGeneratedDocuments(message.text).forEach { collected += it.toPreviewTarget() }
        }
    }

    if (pageMarkdown.isNotBlank()) {
        collected += AgentPreviewTarget.Markdown(
            title = pageLabel.ifBlank { "当前页" },
            content = pageMarkdown,
            canSaveToNotes = false,
        )
    }

    return collected.distinctBy { it.previewKey() }
}

private fun AgentPreviewTarget.previewKey(): String = when (this) {
    is AgentPreviewTarget.Image -> "img:${attachment?.id ?: draftId ?: title}"
    is AgentPreviewTarget.Document ->
        "doc:${attachment?.id ?: title}:${pageFiles.joinToString()}:${pageBytes.size}:${textContent.hashCode()}"
    is AgentPreviewTarget.Markdown -> "md:$title:${content.length}:${content.hashCode()}"
}

/** LaTeX 文档若未带公式定界符,整体包成 `$$…$$`,让 KaTeX 真正排版 */
internal fun prepareMarkdownForPreview(target: AgentPreviewTarget.Markdown): String {
    if (target.docType != MarkdownLite.GeneratedDocType.LATEX) return target.content
    val body = target.content
    return if (body.contains("$$") || body.contains("$")) body else "$$\n$body\n$$"
}

// ==================== 悬浮预览窗 ====================

/**
 * 独立悬浮预览窗:图片(双指缩放/拖动/双击/旋转)、文档(PDF·PPT 逐页 + 文本)、
 * Markdown 与 LaTeX(渲染/源码双模式,复制、写入伴随笔记、存卡片、FileProvider 导出)。
 *
 * @param onBack 由预览中心打开时提供,顶部出现返回钮(回到列表)
 * @param onEditDraftImage 草稿图片可裁剪编辑时的回调(参数为 draftId)
 */
@Composable
fun AgentFloatingPreviewDialog(
    target: AgentPreviewTarget,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    onEditDraftImage: ((String) -> Unit)? = null,
    onSaveToNotes: ((AgentPreviewTarget.Markdown) -> Unit)? = null,
    onSaveCard: ((AgentPreviewTarget.Markdown) -> Unit)? = null,
    onToast: (String) -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(MilkSpace.md),
            contentAlignment = Alignment.Center,
        ) {
            val dialogWidth = minOf(820.dp, maxWidth * 0.92f)
            val dialogHeight = minOf(780.dp, maxHeight * 0.88f)
            Column(
                Modifier
                    .width(dialogWidth)
                    .height(dialogHeight)
                    .milkPanelEnter()
                    .milkDialogSurface(),
            ) {
                PreviewHeader(target = target, onBack = onBack, onDismiss = onDismiss)
                MilkHairlineDivider()
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (target) {
                        is AgentPreviewTarget.Image -> ImagePreviewBody(
                            target = target,
                            onEditDraftImage = onEditDraftImage,
                            onToast = onToast,
                            modifier = Modifier.fillMaxSize(),
                        )

                        is AgentPreviewTarget.Document -> DocumentPreviewBody(
                            target = target,
                            onToast = onToast,
                            modifier = Modifier.fillMaxSize(),
                        )

                        is AgentPreviewTarget.Markdown -> MarkdownPreviewBody(
                            target = target,
                            onSaveToNotes = onSaveToNotes,
                            onSaveCard = onSaveCard,
                            onToast = onToast,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(
    target: AgentPreviewTarget,
    onBack: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = if (onBack != null) MilkSpace.xs else MilkSpace.lg,
                top = MilkSpace.xs,
                end = MilkSpace.xs,
                bottom = MilkSpace.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            MilkIconButton(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回预览列表",
                onClick = onBack,
                touchSize = 40.dp,
                visualSize = 32.dp,
                iconSize = 18.dp,
            )
            Spacer(Modifier.width(MilkSpace.xxs))
        }
        MilkStatusBadge(
            text = previewKindLabel(target),
            tone = if (target is AgentPreviewTarget.Markdown) MilkStatusTone.ACCENT else MilkStatusTone.NEUTRAL,
            mono = true,
        )
        Spacer(Modifier.width(MilkSpace.sm))
        Column(Modifier.weight(1f)) {
            Text(
                target.title,
                style = MilkText.panelTitle,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            previewSubtitle(target)?.let {
                Text(
                    it,
                    style = MilkText.monoMicro,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(MilkSpace.sm))
        MilkIconButton(
            imageVector = Icons.Outlined.Close,
            contentDescription = "关闭预览",
            onClick = onDismiss,
            container = true,
        )
    }
}

private fun previewKindLabel(target: AgentPreviewTarget): String = when (target) {
    is AgentPreviewTarget.Image -> "图片"
    is AgentPreviewTarget.Document -> formatAttachmentBadge(target.source)
    is AgentPreviewTarget.Markdown -> when (target.docType) {
        MarkdownLite.GeneratedDocType.MARKDOWN -> "MD"
        MarkdownLite.GeneratedDocType.TEXT -> "TXT"
        MarkdownLite.GeneratedDocType.LATEX -> "LaTeX"
    }
}

private fun previewSubtitle(target: AgentPreviewTarget): String? = when (target) {
    is AgentPreviewTarget.Image -> target.subtitle.ifBlank { null }
    is AgentPreviewTarget.Document -> buildString {
        if (target.pageRangeLabel.isNotBlank()) append(target.pageRangeLabel)
        val pageCount = maxOf(target.pageBytes.size, target.pageFiles.size)
        if (pageCount > 0) {
            if (isNotEmpty()) append(" · ")
            append("$pageCount 页")
        }
    }.ifBlank { null }
    is AgentPreviewTarget.Markdown -> "${target.content.length} 字"
}

// ==================== 图片 ====================

@Composable
private fun ImagePreviewBody(
    target: AgentPreviewTarget.Image,
    onEditDraftImage: ((String) -> Unit)?,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(target) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(target) { mutableStateOf(false) }

    LaunchedEffect(target) {
        val bytes = target.bytes ?: withContext(Dispatchers.IO) {
            val notebookId = target.notebookId
            val attachment = target.attachment
            if (notebookId != null && attachment != null) {
                ServiceLocator.agentStore.readAttachment(notebookId, attachment)
            } else {
                null
            }
        }
        val decoded = bytes?.let {
            withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        if (decoded != null) bitmap = decoded.asImageBitmap() else failed = true
    }

    Column(modifier) {
        ZoomableImage(
            bitmap = bitmap,
            title = target.title,
            emptyTitle = "无法读取图片",
            loading = !failed && bitmap == null,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = MilkSpace.md),
        )

        val editableDraft = target.canEdit && target.draftId != null && onEditDraftImage != null
        MilkHairlineDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MilkButton(
                label = "导出图片",
                onClick = {
                    val image = bitmap ?: return@MilkButton
                    scope.launch {
                        val bytes = withContext(Dispatchers.IO) {
                            ByteArrayOutputStream().use { out ->
                                image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
                                out.toByteArray()
                            }
                        }
                        val ok = exportAndShare(context, "${target.title}.png", "image/png", bytes, "导出图片")
                        if (!ok) onToast("导出失败")
                    }
                },
                variant = MilkButtonVariant.SECONDARY,
                size = MilkButtonSize.SMALL,
                enabled = bitmap != null,
            )
            if (editableDraft) {
                Spacer(Modifier.weight(1f))
                MilkButton(
                    label = "裁剪 / 编辑",
                    onClick = { target.draftId?.let(onEditDraftImage) },
                    variant = MilkButtonVariant.SECONDARY,
                    size = MilkButtonSize.SMALL,
                )
            }
        }
    }
}

// ==================== 文档 ====================

@Composable
private fun DocumentPreviewBody(
    target: AgentPreviewTarget.Document,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var pages by remember(target) { mutableStateOf(target.pageBytes) }
    var text by remember(target) { mutableStateOf(target.textContent) }
    var loading by remember(target) { mutableStateOf(target.pageBytes.isEmpty()) }
    var pageIndex by remember(target) { mutableStateOf(0) }
    var showText by remember(target) { mutableStateOf(false) }
    var mono by remember(target) { mutableStateOf(false) }
    var copied by remember(target) { mutableStateOf(false) }

    val isTextDoc = target.source == Attachment.Source.TEXT_DOC

    LaunchedEffect(target) {
        if (pages.isEmpty() && !isTextDoc) {
            val notebookId = target.notebookId
            val attachment = target.attachment
            val loaded = withContext(Dispatchers.IO) {
                when {
                    notebookId != null && target.pageFiles.isNotEmpty() ->
                        target.pageFiles.mapNotNull { ServiceLocator.agentStore.readRelativeFile(notebookId, it) }

                    notebookId != null && attachment != null ->
                        listOfNotNull(ServiceLocator.agentStore.readAttachment(notebookId, attachment))

                    else -> emptyList()
                }
            }
            pages = loaded
        }
        loading = false
    }

    LaunchedEffect(target) {
        if (text.isBlank()) {
            val notebookId = target.notebookId
            val attachment = target.attachment
            if (notebookId != null && attachment != null) {
                text = withContext(Dispatchers.IO) {
                    ServiceLocator.agentStore.readAttachmentText(notebookId, attachment)
                }.orEmpty()
            }
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    val hasPages = pages.isNotEmpty() && !isTextDoc
    val hasText = text.isNotBlank()
    val textMode = showText || !hasPages

    var pageBitmap by remember(pages, pageIndex) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(pages, pageIndex) {
        val bytes = pages.getOrNull(pageIndex)
        pageBitmap = bytes?.let {
            withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()
        }
    }

    Column(modifier) {
        if (hasPages && hasText) {
            MilkSegmented(
                options = listOf(false, true),
                selected = showText,
                label = { if (it) "提取文本" else "页面视图" },
                onSelect = { showText = it },
                compact = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm),
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = MilkSpace.md),
        ) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(22.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )

                textMode -> SelectionContainer {
                    Text(
                        text = if (hasText) text else "该文档没有可提取的文字",
                        style = if (mono) MilkText.monoBody else MilkText.body,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = MilkSpace.md),
                    )
                }

                else -> ZoomableImage(
                    bitmap = pageBitmap,
                    title = target.title,
                    emptyTitle = "无法渲染该页",
                    loading = pageBitmap == null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 页码切换 + 缩略图条(仅页面模式)
        if (hasPages && !textMode) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MilkSpace.md, vertical = MilkSpace.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MilkIconButton(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "上一页",
                    onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                    enabled = pageIndex > 0,
                    touchSize = 36.dp,
                    visualSize = 30.dp,
                    iconSize = 18.dp,
                )
                Text(
                    "第 ${pageIndex + 1} / ${pages.size} 页",
                    style = MilkText.monoMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    // 定宽:页码位数变化不推动左右箭头
                    modifier = Modifier.widthIn(min = 88.dp),
                )
                MilkIconButton(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "下一页",
                    onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pages.lastIndex) },
                    enabled = pageIndex < pages.lastIndex,
                    touchSize = 36.dp,
                    visualSize = 30.dp,
                    iconSize = 18.dp,
                )
                Spacer(Modifier.weight(1f))
            }
            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MilkSpace.md),
                horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
                contentPadding = PaddingValues(vertical = MilkSpace.xxs),
            ) {
                itemsIndexed(pages) { index, bytes ->
                    PageThumb(
                        bytes = bytes,
                        index = index,
                        selected = index == pageIndex,
                        onClick = { pageIndex = index },
                    )
                }
            }
        }

        // 文本模式下的字宽切换
        if (textMode && hasText) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MilkSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                MilkSegmented(
                    options = listOf(false, true),
                    selected = mono,
                    label = { if (it) "等宽" else "正文" },
                    onSelect = { mono = it },
                    compact = true,
                    modifier = Modifier.width(132.dp),
                )
            }
        }

        MilkHairlineDivider()
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm),
            horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
            verticalArrangement = Arrangement.spacedBy(MilkSpace.xs),
        ) {
            MilkButton(
                minWidth = 96.dp,
                label = if (copied) "已复制" else "复制文本",
                onClick = {
                    if (hasText) {
                        clipboard.setText(AnnotatedString(text))
                        copied = true
                    }
                },
                variant = MilkButtonVariant.SECONDARY,
                size = MilkButtonSize.SMALL,
                enabled = hasText,
            )
            MilkButton(
                label = "导出文件",
                onClick = {
                    scope.launch {
                        val ok = if (hasText) {
                            exportAndShare(
                                context = context,
                                fileName = "${target.title}.txt",
                                mimeType = "text/plain",
                                bytes = text.toByteArray(Charsets.UTF_8),
                                chooserTitle = "导出文本",
                            )
                        } else {
                            val bytes = pages.getOrNull(pageIndex) ?: return@launch
                            exportAndShare(
                                context = context,
                                fileName = "${target.title}-${pageIndex + 1}.png",
                                mimeType = "image/png",
                                bytes = bytes,
                                chooserTitle = "导出页面",
                            )
                        }
                        if (!ok) onToast("导出失败")
                    }
                },
                variant = MilkButtonVariant.SECONDARY,
                size = MilkButtonSize.SMALL,
                enabled = hasText || hasPages,
            )
        }
    }
}

/** 页码缩略图:真图缩略(解码降采样);选中 = 强调描边 */
@Composable
private fun PageThumb(
    bytes: ByteArray,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val thumb = remember(bytes) { decodeThumbnail(bytes) }
    val shape = RoundedCornerShape(MilkRadius.sm)
    Box(
        Modifier
            .size(width = 46.dp, height = 34.dp)
            .clip(shape)
            .background(cs.surfaceVariant.copy(alpha = 0.6f))
            .then(
                if (selected) Modifier.border(1.5.dp, cs.primary, shape) else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = "第 ${index + 1} 页",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("${index + 1}", style = MilkText.monoMicro, color = cs.onSurfaceVariant)
        }
    }
}

// ==================== Markdown / LaTeX ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkdownPreviewBody(
    target: AgentPreviewTarget.Markdown,
    onSaveToNotes: ((AgentPreviewTarget.Markdown) -> Unit)?,
    onSaveCard: ((AgentPreviewTarget.Markdown) -> Unit)?,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dark = LocalMilkDark.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var rendered by remember(target) { mutableStateOf(true) }
    var copied by remember(target) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    val prepared = remember(target) { prepareMarkdownForPreview(target) }

    Column(modifier) {
        MilkSegmented(
            options = listOf(true, false),
            selected = rendered,
            label = { if (it) "渲染预览" else "源码" },
            onSelect = { rendered = it },
            compact = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm),
        )

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = MilkSpace.md),
        ) {
            if (rendered) {
                MarkdownKaTeXWebView(
                    markdown = prepared,
                    dark = dark,
                    modifier = Modifier.fillMaxSize(),
                    selfScroll = true,
                )
            } else {
                SelectionContainer {
                    Text(
                        text = target.content,
                        style = MilkText.monoBody,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = MilkSpace.md),
                    )
                }
            }
        }

        MilkHairlineDivider()
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm),
            horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
            verticalArrangement = Arrangement.spacedBy(MilkSpace.xs),
        ) {
            MilkButton(
                minWidth = 74.dp,
                label = if (copied) "已复制" else "复制",
                onClick = {
                    clipboard.setText(AnnotatedString(target.content))
                    copied = true
                },
                variant = MilkButtonVariant.SECONDARY,
                size = MilkButtonSize.SMALL,
                enabled = target.content.isNotBlank(),
            )
            if (target.canSaveToNotes && onSaveToNotes != null) {
                MilkButton(
                    label = "写入伴随笔记",
                    onClick = { onSaveToNotes(target) },
                    variant = MilkButtonVariant.SECONDARY,
                    size = MilkButtonSize.SMALL,
                )
            }
            if (target.canSaveCard && onSaveCard != null) {
                MilkButton(
                    label = "存卡片",
                    onClick = { onSaveCard(target) },
                    variant = MilkButtonVariant.SECONDARY,
                    size = MilkButtonSize.SMALL,
                )
            }
            MilkButton(
                label = "导出",
                onClick = {
                    scope.launch {
                        val ok = exportAndShare(
                            context = context,
                            fileName = "${target.title}.${extensionFor(target.docType)}",
                            mimeType = mimeFor(target.docType),
                            bytes = target.content.toByteArray(Charsets.UTF_8),
                            chooserTitle = "导出文档",
                        )
                        if (!ok) onToast("导出失败")
                    }
                },
                variant = MilkButtonVariant.SECONDARY,
                size = MilkButtonSize.SMALL,
                enabled = target.content.isNotBlank(),
            )
        }
    }
}

private fun extensionFor(type: MarkdownLite.GeneratedDocType): String = when (type) {
    MarkdownLite.GeneratedDocType.MARKDOWN -> "md"
    MarkdownLite.GeneratedDocType.TEXT -> "txt"
    MarkdownLite.GeneratedDocType.LATEX -> "tex"
}

private fun mimeFor(type: MarkdownLite.GeneratedDocType): String = when (type) {
    MarkdownLite.GeneratedDocType.MARKDOWN -> "text/markdown"
    MarkdownLite.GeneratedDocType.TEXT -> "text/plain"
    MarkdownLite.GeneratedDocType.LATEX -> "application/x-tex"
}

// ==================== 缩放图片(图片与文档页共用) ====================

@Composable
private fun ZoomableImage(
    bitmap: ImageBitmap?,
    title: String,
    emptyTitle: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    minScale: Float = 1f,
    maxScale: Float = 5f,
) {
    val dark = LocalMilkDark.current
    val scrim = if (dark) Color.Black.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.05f)
    var scale by remember(bitmap) { mutableStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var rotation by remember(bitmap) { mutableStateOf(0) }
    var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    Box(
        modifier
            .clip(RoundedCornerShape(MilkRadius.md))
            .background(scrim),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .pointerInput(minScale, maxScale) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > minScale + 0.01f) {
                                scale = minScale
                                offset = Offset.Zero
                            } else {
                                scale = (minScale * 2.5f).coerceAtMost(maxScale)
                            }
                        },
                    )
                }
                .pointerInput(minScale, maxScale) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(minScale, maxScale)
                        scale = next
                        val maxX = containerSize.width * (next - minScale) / 2f
                        val maxY = containerSize.height * (next - minScale) / 2f
                        offset = Offset(
                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                            (offset.y + pan.y).coerceIn(-maxY, maxY),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                bitmap != null -> Image(
                    bitmap = bitmap,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                            rotationZ = rotation.toFloat()
                        },
                )

                else -> {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        MilkEmptyState(title = emptyTitle, icon = Icons.Outlined.BrokenImage)
                    }
                }
            }
        }

        // 底部信息胶囊(不参与手势)
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(MilkSpace.sm),
            horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 定宽居中的等比标签:缩放数值位数变化不推动相邻徽标
            Box(Modifier.widthIn(min = 48.dp), contentAlignment = Alignment.Center) {
                MilkStatusBadge(text = "${(scale * 100).roundToInt()}%", tone = MilkStatusTone.NEUTRAL, mono = true)
            }
            bitmap?.let {
                MilkStatusBadge(text = "${it.width}×${it.height}", tone = MilkStatusTone.NEUTRAL, mono = true)
            }
            if (rotation != 0) {
                Box(Modifier.widthIn(min = 40.dp), contentAlignment = Alignment.Center) {
                    MilkStatusBadge(text = "$rotation°", tone = MilkStatusTone.NEUTRAL, mono = true)
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(MilkSpace.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MilkIconButton(
                imageVector = Icons.Outlined.Rotate90DegreesCw,
                contentDescription = "旋转 90°",
                onClick = { rotation = (rotation + 90) % 360 },
                container = true,
                iconSize = 17.dp,
                visualSize = 32.dp,
                touchSize = 40.dp,
            )
            MilkIconButton(
                imageVector = Icons.Outlined.RestartAlt,
                contentDescription = "重置缩放",
                onClick = {
                    scale = minScale
                    offset = Offset.Zero
                },
                container = true,
                iconSize = 17.dp,
                visualSize = 32.dp,
                touchSize = 40.dp,
            )
        }
    }
}

// ==================== 预览中心 ====================

/**
 * 预览中心:会话内全部图片 / 基础文档 / Markdown 与 AI 生成文档的入口列表,
 * 支持分类筛选、SAF 打开本地文件,点任一项进入悬浮预览窗(顶部返回列表)。
 */
@Composable
fun AgentPreviewHubDialog(
    items: List<AgentPreviewTarget>,
    onDismiss: () -> Unit,
    onEditDraftImage: ((String) -> Unit)? = null,
    onSaveToNotes: ((AgentPreviewTarget.Markdown) -> Unit)? = null,
    onSaveCard: ((AgentPreviewTarget.Markdown) -> Unit)? = null,
    onToast: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(HUB_ALL) }
    var opened by remember { mutableStateOf<AgentPreviewTarget?>(null) }
    var loadingFile by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loadingFile = true
        scope.launch {
            val target = withContext(Dispatchers.IO) { loadLocalPreview(context, uri) }
            loadingFile = false
            if (target != null) opened = target else onToast("无法预览该文件")
        }
    }

    val filtered = remember(items, category) {
        items.filter {
            category == HUB_ALL || it.categoryKey() == category
        }
    }

    MilkCenterPanel(
        title = "文件与文档预览",
        subtitle = "${items.size} 项",
        onDismiss = onDismiss,
        maxWidth = 560.dp,
        maxHeight = 640.dp,
        footer = {
            MilkButton(
                label = "打开本地文件预览",
                onClick = {
                    if (!loadingFile) {
                        filePicker.launch(
                            arrayOf(
                                "image/*",
                                "application/pdf",
                                "text/markdown",
                                "text/plain",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                "application/vnd.ms-powerpoint",
                                "*/*",
                            ),
                        )
                    }
                },
                variant = MilkButtonVariant.SECONDARY,
                size = MilkButtonSize.SMALL,
                busy = loadingFile,
                leading = {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${filtered.size}",
                style = MilkText.monoMeta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        MilkSegmented(
            options = listOf(HUB_ALL, HUB_IMAGE, HUB_DOC, HUB_MARKDOWN),
            selected = category,
            label = {
                when (it) {
                    HUB_IMAGE -> "图片"
                    HUB_DOC -> "文档"
                    HUB_MARKDOWN -> "Markdown"
                    else -> "全部"
                }
            },
            onSelect = { category = it },
            compact = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(MilkSpace.sm))

        if (filtered.isEmpty()) {
            MilkEmptyState(
                title = if (items.isEmpty()) "暂无可预览内容" else "该分类暂无内容",
                description = if (items.isEmpty()) "添加附件或让 AI 生成文档后会出现在这里" else null,
                icon = Icons.Outlined.Description,
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(MilkSpace.xxs),
            ) {
                items(filtered) { item ->
                    HubRow(target = item, onClick = { opened = item })
                }
            }
        }
    }

    opened?.let { target ->
        AgentFloatingPreviewDialog(
            target = target,
            onDismiss = { opened = null },
            onBack = { opened = null },
            onEditDraftImage = onEditDraftImage,
            onSaveToNotes = onSaveToNotes,
            onSaveCard = onSaveCard,
            onToast = onToast,
        )
    }
}

private fun AgentPreviewTarget.categoryKey(): String = when (this) {
    is AgentPreviewTarget.Image -> HUB_IMAGE
    is AgentPreviewTarget.Document -> HUB_DOC
    is AgentPreviewTarget.Markdown -> HUB_MARKDOWN
}

@Composable
private fun HubRow(target: AgentPreviewTarget, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MilkRadius.md))
            .clickable(onClick = onClick)
            .heightIn(min = 58.dp)
            .padding(horizontal = MilkSpace.sm, vertical = MilkSpace.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewThumb(target)
        Spacer(Modifier.width(MilkSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                target.title,
                style = MilkText.rowTitle,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            previewSubtitle(target)?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MilkText.monoMicro,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(MilkSpace.sm))
        MilkStatusBadge(text = previewKindLabel(target), tone = MilkStatusTone.NEUTRAL, mono = true)
    }
}

@Composable
private fun PreviewThumb(target: AgentPreviewTarget) {
    val cs = MaterialTheme.colorScheme
    val bytes = when (target) {
        is AgentPreviewTarget.Image -> target.bytes
        is AgentPreviewTarget.Document -> target.pageBytes.firstOrNull()
        is AgentPreviewTarget.Markdown -> null
    }
    val thumb = remember(bytes) { bytes?.let { decodeThumbnail(it) } }
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(MilkRadius.sm))
            .background(cs.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = previewIcon(target),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = cs.onSurfaceVariant,
            )
        }
    }
}

private fun previewIcon(target: AgentPreviewTarget): ImageVector = when (target) {
    is AgentPreviewTarget.Image -> Icons.Outlined.Image
    is AgentPreviewTarget.Document -> Icons.Outlined.Description
    is AgentPreviewTarget.Markdown -> Icons.Outlined.Description
}

// ==================== 本地文件 → 预览目标 ====================

private suspend fun loadLocalPreview(context: Context, uri: Uri): AgentPreviewTarget? {
    val resolver = context.contentResolver
    val mime = runCatching { resolver.getType(uri) }.getOrNull()
    val name = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "本地文件"

    if (mime?.startsWith("image/") == true) {
        val bitmap = ImageEditEngine.decodeSampled(context, uri, 2400) ?: return null
        val width = bitmap.width
        val height = bitmap.height
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        bitmap.recycle()
        return AgentPreviewTarget.Image(
            title = name,
            subtitle = "$width×$height",
            bytes = bytes,
        )
    }

    if (mime == "text/markdown" || name.endsWith(".md", ignoreCase = true)) {
        val text = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull() ?: return null
        return AgentPreviewTarget.Markdown(
            title = name,
            content = text,
            canSaveToNotes = false,
        )
    }

    val loaded = DocumentAttachmentLoader.loadFromUri(context, uri).getOrNull() ?: return null
    return try {
        val limit = minOf(loaded.pageCount, LOCAL_PREVIEW_MAX_PAGES)
        val pages = ArrayList<ByteArray>(limit)
        val texts = ArrayList<String>(limit)
        for (index in 0 until limit) {
            loaded.renderPageBitmap(index, 1600)?.let { bitmap ->
                val bytes = ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray()
                }
                pages += bytes
            }
            loaded.pageText(index).takeIf { it.isNotBlank() }?.let { texts += it }
        }
        val text = texts.joinToString("\n\n")
        if (pages.isEmpty()) {
            val type = if (loaded.kind == DocumentKind.MARKDOWN) {
                MarkdownLite.GeneratedDocType.MARKDOWN
            } else {
                MarkdownLite.GeneratedDocType.TEXT
            }
            AgentPreviewTarget.Markdown(
                title = loaded.fileName,
                content = text,
                docType = type,
                canSaveToNotes = false,
            )
        } else {
            AgentPreviewTarget.Document(
                title = loaded.fileName,
                source = when (loaded.kind) {
                    DocumentKind.PDF -> Attachment.Source.PDF_DOC
                    DocumentKind.PPTX,
                    DocumentKind.PPT,
                    -> Attachment.Source.PPT_DOC

                    else -> Attachment.Source.TEXT_DOC
                },
                pageBytes = pages,
                textContent = text,
            )
        }
    } finally {
        loaded.close()
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull()?.takeIf { it.isNotBlank() }

// ==================== 导出 / 解码 ====================

/** 写入 `cacheDir/export/` 后交给系统分享面板(FileProvider 授权只读) */
private suspend fun exportAndShare(
    context: Context,
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
    chooserTitle: String,
): Boolean {
    val file = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
            File(dir, sanitizeFileName(fileName)).apply { writeBytes(bytes) }
        }.getOrNull()
    } ?: return false

    return withContext(Dispatchers.Main) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, chooserTitle))
        }.isSuccess
    }
}

/** 去掉文件系统非法字符,保留扩展名,限制主干长度 */
private fun sanitizeFileName(raw: String): String {
    val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim()
    val dot = cleaned.lastIndexOf('.')
    val stem = if (dot in 1 until cleaned.length - 1) cleaned.substring(0, dot) else cleaned
    val extension = if (dot in 1 until cleaned.length - 1) cleaned.substring(dot) else ""
    return stem.take(48).ifBlank { "导出文件" } + extension.take(8)
}

/** 缩略图解码:先量边界再一次降采样,长边约 160px */
private fun decodeThumbnail(bytes: ByteArray): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 160 && bounds.outHeight / (sample * 2) >= 160) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}.getOrNull()
