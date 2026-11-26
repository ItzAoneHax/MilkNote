package com.milknote.app.ui.agent

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.FileProvider
import com.milknote.agent.AgentMessage
import com.milknote.agent.AgentUiState
import com.milknote.agent.Attachment
import com.milknote.agent.DraftAttachment
import com.milknote.agent.PageRef
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.agent.media.CropAspect
import com.milknote.app.ui.agent.media.DocumentAttachmentLoader
import com.milknote.app.ui.agent.media.DocumentKind
import com.milknote.app.ui.agent.media.DoodlePoint
import com.milknote.app.ui.agent.media.DoodleStroke
import com.milknote.app.ui.agent.media.EncodedImage
import com.milknote.app.ui.agent.media.ImageEditEngine
import com.milknote.app.ui.agent.media.ImageEditState
import com.milknote.app.ui.agent.media.LoadedDocument
import com.milknote.app.ui.agent.media.NormalizedCropRect
import com.milknote.app.ui.agent.media.PageRangeParser
import com.milknote.app.ui.agent.media.attachDocument
import com.milknote.app.ui.common.MilkActionChip
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonSize
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkColorDot
import com.milknote.app.ui.common.MilkEmptyState
import com.milknote.app.ui.common.MilkFilterChip
import com.milknote.app.ui.common.MilkHairlineDivider
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkInlineAction
import com.milknote.app.ui.common.MilkInlineTone
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.MilkSlider
import com.milknote.app.ui.common.MilkStatusBadge
import com.milknote.app.ui.common.MilkStatusTone
import com.milknote.app.ui.common.MilkSwitch
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.milkDialogSurface
import com.milknote.app.ui.common.milkGlassRim
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.PillShape
import com.milknote.app.ui.theme.SurfaceElevatedDark
import com.milknote.app.ui.theme.milkPopoverEnter
import com.milknote.app.ui.writer.RecentImageThumbnails
import com.milknote.app.ui.writer.RecentImages
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** 导出目录(与 `res/xml/file_paths.xml` 的 `export/` 对齐) */
private const val ATTACH_EXPORT_DIR = "export"

/** 系统相册一次查询上限:足够覆盖整机图片,又不会因超大相册拖垮解码 */
private const val SYSTEM_IMAGE_LIMIT = 500

/** 相册缩略图边长(px) */
private const val ALBUM_THUMB_PX = 240

/** 笔记页缩略图边长(px) */
private const val PAGE_THUMB_PX = 200

/** 文档页缩略图边长(px) */
private const val DOC_THUMB_PX = 180

/** 一级入口可进入的二级面板 */
private enum class AttachPane { ROOT, IMAGES, PAGES, DOCS }

/** 图片编辑器的三种工作模式 */
private enum class EditMode { CROP, ADJUST, DOODLE }

/** 裁剪框命中部位 */
private enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY }

/** 涂鸦笔迹可选颜色(高亮黄 / 红 / 蓝 / 墨黑 / 白) */
private val DOODLE_COLORS = listOf(
    0xFFFFD54FL,
    0xFFEF5350L,
    0xFF42A5F5L,
    0xFF1B1B1BL,
    0xFFFFFFFFL,
)

/** 涂鸦笔宽(占图宽比例) */
private val DOODLE_WIDTHS = listOf(0.004f, 0.008f, 0.014f)

/** SAF 文档过滤:末尾追加通配 MIME 兜底,避免部分文件选择器把 `.md` 置灰 */
private val DOC_PICKER_MIME = arrayOf(
    "text/markdown",
    "text/x-markdown",
    "text/plain",
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.ms-powerpoint",
    "application/octet-stream",
    "*/*",
)

private val DOC_SOURCES = setOf(
    Attachment.Source.MARKDOWN_DOC,
    Attachment.Source.PDF_DOC,
    Attachment.Source.PPT_DOC,
    Attachment.Source.TEXT_DOC,
)

/**
 * 附件浮层(多级):一级四选一 → 二级(系统图片 / 笔记页面 / 导入文档)→ 三级(图片编辑 / 文档逐页)。
 *
 * 全部层级都在同一个浮层内切换,面板本身就是说明:标题 + 计数 + 明确的动作胶囊,不写操作提示。
 * 图片编辑与文档逐页需要大面积,改为独立浮窗;打开浮窗时收起 popover,关闭后回到原层级。
 */
@Composable
fun AttachmentFloatingMenu(
    expanded: Boolean,
    state: AgentUiState,
    pageIds: List<String>,
    onDismiss: () -> Unit,
    onSnipToAttachment: () -> Unit,
    onPreviewDraft: (DraftAttachment) -> Unit,
    anchorHeight: Dp = 40.dp,
) {
    if (!expanded) return
    val context = LocalContext.current
    val agent = ServiceLocator.agentController
    val scope = rememberCoroutineScope()
    val dark = LocalMilkDark.current
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val panelWidth = minOf(400.dp, maxOf(300.dp, screenWidth - 28.dp))
    val offsetY = with(density) { -anchorHeight.roundToPx() }
    val shape = RoundedCornerShape(MilkRadius.xl)

    var pane by remember { mutableStateOf(AttachPane.ROOT) }
    var editorSource by remember { mutableStateOf<Bitmap?>(null) }
    var editorTitle by remember { mutableStateOf("编辑图片") }
    var doc by remember { mutableStateOf<LoadedDocument?>(null) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    // 页面/抽屉在编辑中被销毁时兜底回收源图(正常关闭路径已回收,这里只处理意外卸载)
    DisposableEffect(editorSource) {
        val current = editorSource
        onDispose { if (current != null && !current.isRecycled) current.recycle() }
    }
    val closeAll: () -> Unit = {
        busyMessage = null
        onDismiss()
    }

    if (editorSource == null && doc == null) {
        Popup(
            alignment = Alignment.BottomEnd,
            offset = IntOffset(0, offsetY),
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true),
        ) {
            Column(
                Modifier
                    .width(panelWidth)
                    .heightIn(max = 560.dp)
                    .milkPopoverEnter(androidx.compose.ui.graphics.TransformOrigin(1f, 1f))
                    .milkGlassRim(shape, dark, 16.dp),
            ) {
                AttachPanelHeader(
                    title = when (pane) {
                        AttachPane.ROOT -> "添加附件"
                        AttachPane.IMAGES -> "系统图片"
                        AttachPane.PAGES -> "笔记页面"
                        AttachPane.DOCS -> "导入文档"
                    },
                    subtitle = when (pane) {
                        AttachPane.IMAGES -> if (state.draftAttachments.isEmpty()) null
                        else "${state.draftAttachments.size} 项待发送"
                        else -> null
                    },
                    onBack = if (pane == AttachPane.ROOT) null else ({ pane = AttachPane.ROOT }),
                    onDismiss = onDismiss,
                )
                MilkHairlineDivider()
                when (pane) {
                    AttachPane.ROOT -> RootMenu(
                        onSystemImages = { pane = AttachPane.IMAGES },
                        onDocuments = { pane = AttachPane.DOCS },
                        onPages = { pane = AttachPane.PAGES },
                        onSnip = {
                            closeAll()
                            onSnipToAttachment()
                        },
                    )

                    AttachPane.IMAGES -> SystemImagesPanel(
                        busyMessage = busyMessage,
                        onBusy = { busyMessage = it },
                        onPicked = { bitmap, title ->
                            busyMessage = null
                            editorTitle = title
                            editorSource = bitmap
                        },
                    )

                    AttachPane.DOCS -> DocumentImportPanel(
                        drafts = state.draftAttachments,
                        busy = busyMessage != null,
                        onLoading = { busyMessage = if (it) "正在读取文档…" else null },
                        onLoaded = { loaded ->
                            busyMessage = null
                            doc = loaded
                        },
                        onError = { busyMessage = null; pane = AttachPane.DOCS },
                        onPreviewDraft = { draft ->
                            closeAll()
                            onPreviewDraft(draft)
                        },
                    )

                    AttachPane.PAGES -> NotebookPagesPanel(
                        boundPage = state.boundPage,
                        pageIds = pageIds,
                        onSnip = {
                            closeAll()
                            onSnipToAttachment()
                        },
                        onAdded = { count ->
                            if (count > 0) closeAll()
                        },
                    )
                }
            }
        }
    }

    editorSource?.let { source ->
        ImageEditorFloatingPanel(
            source = source,
            title = editorTitle,
            confirmLabel = "加入附件",
            onDismiss = {
                editorSource = null
                if (source.isRecycled.not()) source.recycle()
            },
            onConfirm = { encoded ->
                agent.addSystemImageAttachment(
                    png = encoded.bytes,
                    jpeg = encoded.jpeg,
                    widthPx = encoded.widthPx,
                    heightPx = encoded.heightPx,
                    logicalName = if (encoded.jpeg) "image.jpg" else "image.png",
                    mentionName = "图片",
                )
                editorSource = null
                if (source.isRecycled.not()) source.recycle()
                onDismiss()
            },
        )
    }

    doc?.let { loaded ->
        DocumentPagePreviewFloatingPanel(
            doc = loaded,
            onDismiss = {
                doc = null
                pane = AttachPane.DOCS
            },
            onAdded = {
                doc = null
                onDismiss()
            },
        )
    }
}

// ==================== 浮层骨架 ====================

@Composable
private fun AttachPanelHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = if (onBack != null) MilkSpace.xs else MilkSpace.lg,
                top = MilkSpace.md,
                end = MilkSpace.xs,
                bottom = MilkSpace.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            MilkIconButton(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回附件菜单",
                onClick = onBack,
                touchSize = 36.dp,
                visualSize = 30.dp,
                iconSize = 17.dp,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MilkText.panelTitle, color = cs.onSurface)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MilkText.monoMicro, color = cs.onSurfaceVariant)
            }
        }
        MilkIconButton(
            imageVector = Icons.Outlined.Close,
            contentDescription = "收起附件菜单",
            onClick = onDismiss,
            touchSize = 36.dp,
            visualSize = 30.dp,
            iconSize = 16.dp,
        )
    }
}

// ==================== 一级:四个入口 ====================

@Composable
private fun RootMenu(
    onSystemImages: () -> Unit,
    onDocuments: () -> Unit,
    onPages: () -> Unit,
    onSnip: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(MilkSpace.xs)) {
        AttachChoiceRow(
            icon = Icons.Outlined.PhotoLibrary,
            title = "系统图片",
            trailing = "相册",
            onClick = onSystemImages,
        )
        AttachChoiceRow(
            icon = Icons.Outlined.Description,
            title = "导入文档",
            trailing = "MD · PDF · PPT",
            onClick = onDocuments,
        )
        AttachChoiceRow(
            icon = Icons.Outlined.AutoStories,
            title = "笔记页面",
            trailing = "选页 / 页码范围",
            onClick = onPages,
        )
        AttachChoiceRow(
            icon = Icons.Outlined.CropFree,
            title = "画布框选",
            trailing = "拖框选区",
            onClick = onSnip,
        )
    }
}

@Composable
private fun AttachChoiceRow(
    icon: ImageVector,
    title: String,
    trailing: String?,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MilkRadius.md))
            .softClickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = MilkSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = cs.primary)
        Spacer(Modifier.width(MilkSpace.md))
        Text(title, style = MilkText.rowTitle, color = cs.onSurface, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MilkText.monoMicro, color = cs.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.width(MilkSpace.xs))
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = cs.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

// ==================== 二级 A:系统图片 ====================

/** 相册一条记录(带文件名与相册名,供分组筛选与卡片角标) */
private data class SystemImageItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val bucket: String,
)

@Composable
private fun SystemImagesPanel(
    busyMessage: String?,
    onBusy: (String?) -> Unit,
    onPicked: (Bitmap, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var granted by remember { mutableStateOf(RecentImages.hasPermission(context)) }
    var images by remember { mutableStateOf<List<SystemImageItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var bucket by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok -> granted = ok }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        error = null
        onBusy("正在读取图片…")
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { ImageEditEngine.decodeSampled(context, uri, 2048) }
            if (bitmap == null) {
                onBusy(null)
                error = "无法读取该图片"
            } else {
                onPicked(bitmap, uri.displayName(context) ?: "图片")
            }
        }
    }

    LaunchedEffect(granted) {
        if (!granted) {
            images = emptyList()
            return@LaunchedEffect
        }
        loading = true
        images = querySystemImages(context, SYSTEM_IMAGE_LIMIT)
        loading = false
    }

    val buckets = remember(images) {
        images.map { it.bucket }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val filtered = remember(images, bucket) {
        if (bucket == null) images else images.filter { it.bucket == bucket }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = MilkSpace.sm)) {
        if (!granted) {
            MilkEmptyState(
                title = "需要相册访问权限",
                description = "也可以直接从文件中选择图片",
                icon = Icons.Outlined.PhotoLibrary,
                actionLabel = "允许访问相册",
                onAction = { permissionLauncher.launch(RecentImages.requiredPermission()) },
            )
            Box(Modifier.fillMaxWidth().padding(horizontal = MilkSpace.lg)) {
                MilkButton(
                    label = "从文件选择",
                    onClick = { filePicker.launch("image/*") },
                    variant = MilkButtonVariant.SECONDARY,
                    size = MilkButtonSize.SMALL,
                    leading = {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = cs.onSurfaceVariant,
                        )
                    },
                )
            }
            return@Column
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MilkSpace.md, vertical = MilkSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MilkStatusBadge(text = "${images.size}", tone = MilkStatusTone.NEUTRAL, mono = true)
            Spacer(Modifier.width(MilkSpace.sm))
            Text("最近添加", style = MilkText.monoMicro, color = cs.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            MilkActionChip(
                label = "从文件选择",
                small = true,
                onClick = { filePicker.launch("image/*") },
                leading = {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = cs.primary,
                    )
                },
            )
        }

        if (buckets.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = MilkSpace.md),
                horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
            ) {
                MilkFilterChip(
                    label = "全部",
                    selected = bucket == null,
                    small = true,
                    onClick = { bucket = null },
                )
                buckets.take(16).forEach { name ->
                    MilkFilterChip(
                        label = name,
                        selected = bucket == name,
                        small = true,
                        onClick = { bucket = if (bucket == name) null else name },
                    )
                }
            }
            Spacer(Modifier.height(MilkSpace.xs))
        }

        Box(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 320.dp)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = cs.primary,
                    )
                }

                filtered.isEmpty() -> MilkEmptyState(
                    title = if (images.isEmpty()) "相册里还没有图片" else "该相册没有图片",
                    icon = Icons.Outlined.PhotoLibrary,
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = MilkSpace.md, vertical = MilkSpace.xxs),
                    horizontalArrangement = Arrangement.spacedBy(MilkSpace.xxs),
                    verticalArrangement = Arrangement.spacedBy(MilkSpace.xxs),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        SystemImageCell(
                            item = item,
                            onClick = {
                                error = null
                                onBusy("正在读取图片…")
                                scope.launch {
                                    val bitmap = withContext(Dispatchers.IO) {
                                        ImageEditEngine.decodeSampled(context, item.uri, 2048)
                                    }
                                    if (bitmap == null) {
                                        onBusy(null)
                                        error = "无法读取该图片"
                                    } else {
                                        onPicked(bitmap, item.displayName.ifBlank { "图片" })
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        if (busyMessage != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MilkSpace.md, vertical = MilkSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.8.dp,
                    color = cs.primary,
                )
                Spacer(Modifier.width(MilkSpace.xs))
                Text(busyMessage, style = MilkText.monoMicro, color = cs.onSurfaceVariant)
            }
        }
        error?.let { message ->
            Text(
                message,
                style = MilkText.monoMicro,
                color = cs.error,
                modifier = Modifier.padding(horizontal = MilkSpace.md, vertical = MilkSpace.xxs),
            )
        }
    }
}

@Composable
private fun SystemImageCell(item: SystemImageItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var thumb by remember(item.uri) { mutableStateOf<ImageBitmap?>(null) }
    val shape = RoundedCornerShape(MilkRadius.sm)
    LaunchedEffect(item.uri) {
        if (thumb != null) return@LaunchedEffect
        val cached = RecentImageThumbnails.peek(item.uri)
        thumb = cached?.asImageBitmap()
        if (thumb == null) {
            thumb = RecentImageThumbnails.load(context, item.uri, ALBUM_THUMB_PX)?.asImageBitmap()
        }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(cs.surfaceVariant.copy(alpha = 0.6f))
            .border(0.5.dp, cs.outline.copy(alpha = 0.7f), shape)
            .clickable(onClick = onClick),
    ) {
        val bitmap = thumb
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Outlined.PhotoLibrary,
                contentDescription = item.displayName,
                modifier = Modifier.size(18.dp).align(Alignment.Center),
                tint = cs.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

private suspend fun querySystemImages(context: Context, limit: Int): List<SystemImageItem> =
    withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val bundle = Bundle().apply {
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Images.Media.DATE_ADDED),
            )
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val out = ArrayList<SystemImageItem>(min(limit, 128))
        runCatching {
            context.contentResolver
                .query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, bundle, null)
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        out += SystemImageItem(
                            id = id,
                            uri = android.content.ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id,
                            ),
                            displayName = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else "",
                            bucket = if (bucketCol >= 0) cursor.getString(bucketCol).orEmpty() else "",
                        )
                    }
                }
        }
        out
    }

private fun Uri.displayName(context: Context): String? = runCatching {
    context.contentResolver.query(this, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
}.getOrNull()

// ==================== 三级 A:图片裁剪 / 翻转 / 调色 / 涂鸦 ====================

/**
 * 图片编辑器浮窗(新建附件与编辑已有草稿图片共用)。
 *
 * 预览顺序与 [ImageEditEngine.apply] 完全一致:先旋转翻转(重建预览位图)、再涂鸦(0..1 落在预览图上)、
 * 最后才裁剪与色彩(裁剪框同坐标系,色彩用等价 ColorMatrix 实时预览)。所见即所得。
 */
@Composable
fun ImageEditorFloatingPanel(
    source: Bitmap,
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (EncodedImage) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val scope = rememberCoroutineScope()
    var edits by remember(source) { mutableStateOf(ImageEditState()) }
    var mode by remember { mutableStateOf(EditMode.CROP) }
    var aspect by remember { mutableStateOf(CropAspect.FREE) }
    var strokeColor by remember { mutableStateOf(DOODLE_COLORS.first()) }
    var strokeWidth by remember { mutableStateOf(DOODLE_WIDTHS[1]) }
    var liveStroke by remember { mutableStateOf<DoodleStroke?>(null) }
    var encoding by remember { mutableStateOf(false) }

    val preview = remember(source, edits.rotationDeg, edits.flipHorizontal, edits.flipVertical) {
        transformPreview(source, edits.rotationDeg, edits.flipHorizontal, edits.flipVertical)
    }
    DisposableEffect(preview) {
        onDispose { if (preview !== source && !preview.isRecycled) preview.recycle() }
    }
    val previewImage = remember(preview) { preview.asImageBitmap() }
    val previewAspect = preview.width.toFloat() / preview.height.toFloat().coerceAtLeast(1f)
    val filter = remember(edits.brightness, edits.contrast, edits.documentEnhance) {
        previewColorFilter(edits)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(MilkSpace.sm),
            contentAlignment = Alignment.Center,
        ) {
            val panelWidth = minOf(720.dp, maxWidth * 0.94f)
            val panelHeight = minOf(820.dp, maxHeight * 0.94f)
            Column(
                Modifier
                    .width(panelWidth)
                    .height(panelHeight)
                    .milkDialogSurface(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = MilkSpace.lg, top = MilkSpace.sm, end = MilkSpace.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MilkText.panelTitle,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${preview.width} × ${preview.height}" +
                                if (edits.hasEdits()) " · 已调整" else "",
                            style = MilkText.monoMicro,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    MilkIconButton(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭图片编辑",
                        onClick = onDismiss,
                        touchSize = 40.dp,
                        visualSize = 32.dp,
                        iconSize = 17.dp,
                    )
                }
                MilkHairlineDivider()

                ImageEditPreview(
                    image = previewImage,
                    imageAspect = previewAspect,
                    edits = edits,
                    mode = mode,
                    aspect = aspect,
                    strokeArgb = strokeColor,
                    strokeWidthFraction = strokeWidth,
                    liveStroke = liveStroke,
                    filter = filter,
                    onCropChange = { edits = edits.copy(crop = it) },
                    onStroke = { edits = edits.copy(strokes = edits.strokes + it) },
                    onLiveStroke = { liveStroke = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

                MilkHairlineDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm)) {
                    MilkSegmented(
                        options = EditMode.entries.toList(),
                        selected = mode,
                        label = {
                            when (it) {
                                EditMode.CROP -> "裁剪"
                                EditMode.ADJUST -> "调整"
                                EditMode.DOODLE -> "涂鸦"
                            }
                        },
                        onSelect = { mode = it },
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(MilkSpace.sm))
                    // 固定高度容器:裁剪 / 调整 / 涂鸦切换时预览图尺寸零变化
                    Box(Modifier.fillMaxWidth().height(132.dp)) {
                        when (mode) {
                            EditMode.CROP -> CropControls(
                                aspect = aspect,
                                onAspect = { picked ->
                                    aspect = picked
                                    edits = edits.copy(
                                        crop = edits.crop.withAspect(picked, previewAspect),
                                    )
                                },
                                onRotateLeft = {
                                    edits = edits.rotateLeft()
                                    val rotatedAspect = if (edits.rotationDeg % 180 == 0) previewAspect else 1f / previewAspect
                                    edits = edits.copy(crop = NormalizedCropRect().withAspect(aspect, rotatedAspect))
                                },
                                onRotateRight = {
                                    edits = edits.rotateRight()
                                    val rotatedAspect = if (edits.rotationDeg % 180 == 0) previewAspect else 1f / previewAspect
                                    edits = edits.copy(crop = NormalizedCropRect().withAspect(aspect, rotatedAspect))
                                },
                                onFlipH = { edits = edits.toggleFlipH() },
                                onFlipV = { edits = edits.toggleFlipV() },
                            )

                            EditMode.ADJUST -> AdjustControls(
                                edits = edits,
                                onBrightness = { edits = edits.copy(brightness = it) },
                                onContrast = { edits = edits.copy(contrast = it) },
                                onEnhance = { edits = edits.copy(documentEnhance = it) },
                            )

                            EditMode.DOODLE -> DoodleControls(
                                color = strokeColor,
                                width = strokeWidth,
                                canUndo = edits.strokes.isNotEmpty(),
                                onColor = { strokeColor = it },
                                onWidth = { strokeWidth = it },
                                onUndo = { edits = edits.undoStroke() },
                                onClear = { edits = edits.clearStrokes() },
                            )
                        }
                    }
                }

                MilkHairlineDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MilkButton(
                        label = "重置",
                        onClick = {
                            edits = ImageEditState()
                            aspect = CropAspect.FREE
                        },
                        variant = MilkButtonVariant.TEXT,
                        size = MilkButtonSize.SMALL,
                        enabled = edits.hasEdits() && !encoding,
                        leading = {
                            Icon(
                                Icons.Outlined.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (edits.hasEdits()) cs.onSurfaceVariant else cs.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    MilkButton(
                        label = "取消",
                        onClick = onDismiss,
                        variant = MilkButtonVariant.SECONDARY,
                        size = MilkButtonSize.SMALL,
                        enabled = !encoding,
                    )
                    Spacer(Modifier.width(MilkSpace.sm))
                    MilkButton(
                        label = confirmLabel,
                        onClick = {
                            if (encoding) return@MilkButton
                            encoding = true
                            scope.launch {
                                val encoded = withContext(Dispatchers.Default) {
                                    runCatching { ImageEditEngine.applyAndEncode(source, edits) }.getOrNull()
                                }
                                encoding = false
                                if (encoded != null) onConfirm(encoded)
                            }
                        },
                        size = MilkButtonSize.SMALL,
                        busy = encoding,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageEditPreview(
    image: ImageBitmap,
    imageAspect: Float,
    edits: ImageEditState,
    mode: EditMode,
    aspect: CropAspect,
    strokeArgb: Long,
    strokeWidthFraction: Float,
    liveStroke: DoodleStroke?,
    filter: ColorFilter?,
    onCropChange: (NormalizedCropRect) -> Unit,
    onStroke: (DoodleStroke) -> Unit,
    onLiveStroke: (DoodleStroke?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val currentEdits = rememberUpdatedState(edits)
    val currentAspect = rememberUpdatedState(aspect)
    val scrim = if (dark) Color.Black.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.06f)
    val handleTol = with(LocalDensity.current) { 26.dp.toPx() }

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(MilkRadius.md))
            .background(scrim)
            .padding(MilkSpace.sm),
        contentAlignment = Alignment.Center,
    ) {
        val boxAspect = maxWidth.value / maxHeight.value.coerceAtLeast(0.001f)
        val fittedWidth = if (imageAspect >= boxAspect) maxWidth else (maxHeight.value * imageAspect).dp
        val fittedHeight = if (imageAspect >= boxAspect) (maxWidth.value / imageAspect).dp else maxHeight
        Box(
            Modifier
                .size(width = fittedWidth, height = fittedHeight)
                .clip(RoundedCornerShape(MilkRadius.xs)),
        ) {
            Image(
                bitmap = image,
                contentDescription = "图片预览",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = filter,
            )

            Canvas(Modifier.fillMaxSize()) {
                val crop = currentEdits.value.crop
                val s = Size(size.width, size.height)
                val previewStrokes = buildList {
                    addAll(currentEdits.value.strokes)
                    liveStroke?.let { add(it) }
                }
                previewStrokes.forEach { stroke ->
                    val color = Color(stroke.colorArgb.toInt())
                    val w = (stroke.widthFraction * size.width).coerceAtLeast(1f)
                    val points = stroke.points
                    if (points.size == 1) {
                        drawCircle(
                            color = color,
                            radius = w / 2f,
                            center = Offset(points[0].x * s.width, points[0].y * s.height),
                            colorFilter = filter,
                        )
                    } else if (points.size > 1) {
                        val path = Path()
                        path.moveTo(points[0].x * s.width, points[0].y * s.height)
                        for (i in 1 until points.size) {
                            path.lineTo(points[i].x * s.width, points[i].y * s.height)
                        }
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            colorFilter = filter,
                        )
                    }
                }
                if (mode == EditMode.CROP) {
                    val l = crop.left * s.width
                    val t = crop.top * s.height
                    val r = crop.right * s.width
                    val b = crop.bottom * s.height
                    val dim = if (dark) Color.Black.copy(alpha = 0.52f) else Color.Black.copy(alpha = 0.38f)
                    drawRect(dim, size = Size(s.width, t))
                    drawRect(dim, topLeft = Offset(0f, b), size = Size(s.width, s.height - b))
                    drawRect(dim, topLeft = Offset(0f, t), size = Size(l, b - t))
                    drawRect(dim, topLeft = Offset(r, t), size = Size(s.width - r, b - t))
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(l, t),
                        size = Size(r - l, b - t),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    // 三分构图参考线
                    val guide = Color.White.copy(alpha = 0.35f)
                    for (i in 1..2) {
                        val gx = l + (r - l) * i / 3f
                        val gy = t + (b - t) * i / 3f
                        drawLine(guide, Offset(gx, t), Offset(gx, b), strokeWidth = 0.75.dp.toPx())
                        drawLine(guide, Offset(l, gy), Offset(r, gy), strokeWidth = 0.75.dp.toPx())
                    }
                    val radius = 7.dp.toPx()
                    for (corner in listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))) {
                        drawCircle(Color.White, radius = radius, center = corner)
                        drawCircle(cs.primary, radius = radius, center = corner, style = Stroke(2.dp.toPx()))
                    }
                }
            }

            when (mode) {
                EditMode.CROP -> Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(currentAspect.value) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val start = currentEdits.value.crop
                                val handle = cropHandleAt(
                                    start,
                                    down.position.x,
                                    down.position.y,
                                    Size(size.width.toFloat(), size.height.toFloat()),
                                    handleTol,
                                ) ?: return@awaitEachGesture
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                drag(down.id) { change ->
                                    val dx = (change.position.x - down.position.x) / w
                                    val dy = (change.position.y - down.position.y) / h
                                    val next = applyCropDrag(start, handle, dx, dy)
                                    val locked = currentAspect.value
                                    onCropChange(
                                        if (locked == CropAspect.FREE) next else next.withAspect(locked, imageAspect),
                                    )
                                }
                            }
                        },
                )

                EditMode.DOODLE -> Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val points = mutableListOf(
                                    DoodlePoint(
                                        (down.position.x / w).coerceIn(0f, 1f),
                                        (down.position.y / h).coerceIn(0f, 1f),
                                    ),
                                )
                                onLiveStroke(DoodleStroke(strokeArgb, strokeWidthFraction, points.toList()))
                                drag(down.id) { change ->
                                    points += DoodlePoint(
                                        (change.position.x / w).coerceIn(0f, 1f),
                                        (change.position.y / h).coerceIn(0f, 1f),
                                    )
                                    onLiveStroke(DoodleStroke(strokeArgb, strokeWidthFraction, points.toList()))
                                }
                                onStroke(DoodleStroke(strokeArgb, strokeWidthFraction, points.toList()))
                                onLiveStroke(null)
                            }
                        },
                )

                EditMode.ADJUST -> Unit
            }
        }
    }
}

@Composable
private fun CropControls(
    aspect: CropAspect,
    onAspect: (CropAspect) -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
        ) {
            CropAspect.entries.forEach { entry ->
                // 单选胶囊不带勾选图标:切换纵横比时同一行胶囊宽度不变
                MilkChip(
                    label = entry.label,
                    selected = entry == aspect,
                    small = true,
                    onClick = { onAspect(entry) },
                )
            }
        }
        Spacer(Modifier.height(MilkSpace.xxs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MilkIconButton(
                imageVector = Icons.AutoMirrored.Outlined.RotateLeft,
                contentDescription = "向左旋转 90°",
                onClick = onRotateLeft,
                touchSize = 40.dp,
                visualSize = 32.dp,
                iconSize = 18.dp,
                tint = cs.onSurfaceVariant,
            )
            MilkIconButton(
                imageVector = Icons.AutoMirrored.Outlined.RotateRight,
                contentDescription = "向右旋转 90°",
                onClick = onRotateRight,
                touchSize = 40.dp,
                visualSize = 32.dp,
                iconSize = 18.dp,
                tint = cs.onSurfaceVariant,
            )
            Spacer(Modifier.width(MilkSpace.xs))
            MilkIconButton(
                imageVector = Icons.Outlined.Flip,
                contentDescription = "水平翻转",
                onClick = onFlipH,
                touchSize = 40.dp,
                visualSize = 32.dp,
                iconSize = 18.dp,
                tint = cs.onSurfaceVariant,
            )
            // 水平翻转图标的 90° 旋转 = 语义清晰的垂直翻转(不再借用「置顶」图标)
            MilkIconButton(
                imageVector = Icons.Outlined.Flip,
                contentDescription = "垂直翻转",
                onClick = onFlipV,
                modifier = Modifier.graphicsLayer { rotationZ = 90f },
                touchSize = 40.dp,
                visualSize = 32.dp,
                iconSize = 18.dp,
                tint = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdjustControls(
    edits: ImageEditState,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onEnhance: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        // 双列排布:滑条保持原生命中高度,整体仍能装进 132dp 固定容器
        Row(horizontalArrangement = Arrangement.spacedBy(MilkSpace.lg)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Brightness6,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(MilkSpace.xs))
                    Text("亮度", style = MilkText.monoMeta, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(
                        formatSignedPercent(edits.brightness),
                        style = MilkText.monoMicro,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = 44.dp),
                    )
                }
                MilkSlider(
                    value = edits.brightness,
                    onValueChange = onBrightness,
                    valueRange = -50f..50f,
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("对比度", style = MilkText.monoMeta, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(
                        String.format(java.util.Locale.US, "%.2f", edits.contrast),
                        style = MilkText.monoMicro,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = 44.dp),
                    )
                }
                MilkSlider(
                    value = edits.contrast,
                    onValueChange = onContrast,
                    valueRange = 0.5f..1.5f,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Crop,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = cs.onSurfaceVariant,
            )
            Spacer(Modifier.width(MilkSpace.xs))
            Text("文稿增强", style = MilkText.monoMeta, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            MilkSwitch(checked = edits.documentEnhance, onChange = onEnhance)
        }
    }
}

@Composable
private fun DoodleControls(
    color: Long,
    width: Float,
    canUndo: Boolean,
    onColor: (Long) -> Unit,
    onWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Brush,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = cs.onSurfaceVariant,
            )
            Spacer(Modifier.width(MilkSpace.xs))
            // 统一色点选择器:16dp 色点 + 选中光环,与设置页纸色/墨色同一画法
            DOODLE_COLORS.forEach { argb ->
                MilkColorDot(
                    color = Color(argb.toInt()),
                    selected = color == argb,
                    onClick = { onColor(argb) },
                    dotSize = 18.dp,
                    contentDescription = "笔迹色 ${doodleColorLabel(argb)}",
                )
            }
            Spacer(Modifier.weight(1f))
            MilkInlineAction(
                label = "撤销",
                onClick = onUndo,
                icon = Icons.AutoMirrored.Outlined.Undo,
                enabled = canUndo,
            )
            Spacer(Modifier.width(MilkSpace.xs))
            MilkInlineAction(
                label = "清空",
                onClick = onClear,
                icon = Icons.Outlined.DeleteSweep,
                enabled = canUndo,
            )
        }
        Spacer(Modifier.height(MilkSpace.xxs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DOODLE_WIDTHS.forEachIndexed { index, fraction ->
                // 单选胶囊不带勾选图标:切换笔宽时同一行胶囊宽度不变
                MilkChip(
                    label = listOf("细", "中", "粗")[index],
                    selected = width == fraction,
                    small = true,
                    onClick = { onWidth(fraction) },
                    modifier = Modifier.padding(end = MilkSpace.xs),
                )
            }
        }
    }
}

/** 涂鸦色点的读屏名称(与色值一一对应,顺序即面板顺序) */
private fun doodleColorLabel(argb: Long): String = when (argb) {
    0xFFFFD54FL -> "高亮黄"
    0xFFEF5350L -> "红"
    0xFF42A5F5L -> "蓝"
    0xFF1B1B1BL -> "墨黑"
    else -> "白"
}

private fun formatSignedPercent(value: Float): String =
    if (value > 0f) "+${value.toInt()}" else "${value.toInt()}"

private fun transformPreview(
    source: Bitmap,
    rotationDeg: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
): Bitmap {
    val angle = ((rotationDeg % 360) + 360) % 360
    if (angle == 0 && !flipHorizontal && !flipVertical) return source
    val matrix = Matrix().apply {
        if (angle != 0) postRotate(angle.toFloat())
        if (flipHorizontal) postScale(-1f, 1f)
        if (flipVertical) postScale(1f, -1f)
    }
    if (source.isRecycled) return source
    return runCatching {
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }.getOrDefault(source)
}

/** 与 [ImageEditEngine] 的色彩链等价的 Compose 预览滤镜(实时生效,无需重编码) */
private fun previewColorFilter(edits: ImageEditState): ColorFilter? {
    val brightness = edits.brightness
    val contrast = edits.contrast
    if (brightness == 0f && contrast == 1f && !edits.documentEnhance) return null
    val matrix = ColorMatrix()
    if (edits.documentEnhance) {
        matrix.setToSaturation(0f)
        matrix *= previewContrastMatrix(1.45f, 16f)
        if (brightness != 0f || contrast != 1f) {
            matrix *= previewContrastMatrix(contrast, brightness / 50f * 60f)
        }
    } else {
        matrix *= previewContrastMatrix(contrast, brightness / 50f * 50f)
    }
    return ColorFilter.colorMatrix(matrix)
}

private fun previewContrastMatrix(scale: Float, offset: Float): ColorMatrix {
    val translate = 128f * (1f - scale) + offset
    return ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

private fun cropHandleAt(
    crop: NormalizedCropRect,
    x: Float,
    y: Float,
    size: Size,
    tolPx: Float,
): CropHandle? {
    val l = crop.left * size.width
    val r = crop.right * size.width
    val t = crop.top * size.height
    val b = crop.bottom * size.height
    val corners = listOf(
        CropHandle.TOP_LEFT to Offset(l, t),
        CropHandle.TOP_RIGHT to Offset(r, t),
        CropHandle.BOTTOM_LEFT to Offset(l, b),
        CropHandle.BOTTOM_RIGHT to Offset(r, b),
    )
    var best: CropHandle? = null
    var bestDistance = Float.MAX_VALUE
    for ((handle, corner) in corners) {
        val distance = kotlin.math.abs(x - corner.x) + kotlin.math.abs(y - corner.y)
        if (distance <= tolPx && distance < bestDistance) {
            bestDistance = distance
            best = handle
        }
    }
    if (best != null) return best
    return if (x in l..r && y in t..b) CropHandle.BODY else null
}

private fun applyCropDrag(
    start: NormalizedCropRect,
    handle: CropHandle,
    dx: Float,
    dy: Float,
): NormalizedCropRect {
    var l = start.left
    var t = start.top
    var r = start.right
    var b = start.bottom
    when (handle) {
        CropHandle.TOP_LEFT -> {
            l += dx
            t += dy
        }

        CropHandle.TOP_RIGHT -> {
            r += dx
            t += dy
        }

        CropHandle.BOTTOM_LEFT -> {
            l += dx
            b += dy
        }

        CropHandle.BOTTOM_RIGHT -> {
            r += dx
            b += dy
        }

        CropHandle.BODY -> {
            val width = start.right - start.left
            val height = start.bottom - start.top
            l = (start.left + dx).coerceIn(0f, (1f - width).coerceAtLeast(0f))
            r = l + width
            t = (start.top + dy).coerceIn(0f, (1f - height).coerceAtLeast(0f))
            b = t + height
        }
    }
    return NormalizedCropRect(l, t, r, b).clamped()
}

// ==================== 二级 B:导入文档(MD / PDF / PPT) ====================

@Composable
private fun DocumentImportPanel(
    drafts: List<DraftAttachment>,
    busy: Boolean,
    onLoading: (Boolean) -> Unit,
    onLoaded: (LoadedDocument) -> Unit,
    onPreviewDraft: (DraftAttachment) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        error = null
        onLoading(true)
        scope.launch {
            val result = DocumentAttachmentLoader.loadFromUri(context, uri)
            onLoading(false)
            result.fold(
                onSuccess = { loaded -> onLoaded(loaded) },
                onFailure = { failure ->
                    error = failure.message ?: "无法读取该文档"
                    onError(error.orEmpty())
                },
            )
        }
    }

    val openedDocs = drafts.filter { it.source in DOC_SOURCES }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MilkSpace.xs, vertical = MilkSpace.xs),
    ) {
        AttachChoiceRow(
            icon = Icons.Outlined.Description,
            title = "Markdown",
            trailing = "MD · TXT",
            onClick = {
                picker.launch(
                    arrayOf(
                        "text/markdown",
                        "text/x-markdown",
                        "text/plain",
                        "application/octet-stream",
                        "*/*",
                    ),
                )
            },
        )
        AttachChoiceRow(
            icon = Icons.Outlined.Description,
            title = "PDF",
            trailing = "逐页预览",
            onClick = {
                picker.launch(arrayOf("application/pdf", "*/*"))
            },
        )
        AttachChoiceRow(
            icon = Icons.Outlined.Description,
            title = "演示文稿",
            trailing = "PPT · PPTX",
            onClick = {
                picker.launch(
                    arrayOf(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "application/vnd.ms-powerpoint",
                        "*/*",
                    ),
                )
            },
        )
        AttachChoiceRow(
            icon = Icons.Outlined.Add,
            title = "全部支持的文档",
            trailing = "MD · PDF · PPT",
            onClick = {
                picker.launch(DOC_PICKER_MIME)
            },
        )

        if (busy) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MilkSpace.md, vertical = MilkSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.8.dp,
                    color = cs.primary,
                )
                Spacer(Modifier.width(MilkSpace.xs))
                Text("正在读取文档…", style = MilkText.monoMicro, color = cs.onSurfaceVariant)
            }
        }
        error?.let { message ->
            Text(
                message,
                style = MilkText.monoMicro,
                color = cs.error,
                modifier = Modifier.padding(horizontal = MilkSpace.md, vertical = MilkSpace.xxs),
            )
        }

        if (openedDocs.isNotEmpty()) {
            Spacer(Modifier.height(MilkSpace.sm))
            MilkHairlineDivider(startIndent = MilkSpace.md, endIndent = MilkSpace.md)
            Spacer(Modifier.height(MilkSpace.sm))
            MilkSectionLabel(
                text = "本次会话",
                modifier = Modifier.padding(horizontal = MilkSpace.md),
            )
            Spacer(Modifier.height(MilkSpace.xxs))
            openedDocs.take(6).forEach { draft ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MilkRadius.md))
                        .softClickable { onPreviewDraft(draft) }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = MilkSpace.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatAttachmentBadge(draft.source),
                        style = MilkText.monoMicro,
                        color = cs.primary,
                        modifier = Modifier
                            .clip(PillShape)
                            .background(cs.primary.copy(alpha = 0.09f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(MilkSpace.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            draft.logicalName.ifBlank { "文档" },
                            style = MilkText.rowTitle,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (draft.pageRangeLabel.isNotBlank()) {
                            Text(
                                draft.pageRangeLabel,
                                style = MilkText.monoMicro,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = cs.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

// ==================== 三级 B:文档逐页预览 + 页码范围 ====================

private const val DOC_PREVIEW_SLIDE = "slide"
private const val DOC_PREVIEW_TEXT = "text"

/**
 * 文档逐页预览浮窗:大图/文字聚焦预览 + 缩略页条 + 双向页码区间。
 * 退出时通过 [DisposableEffect] 释放 [LoadedDocument](临时缓存与 PdfRenderer)。
 */
@Composable
fun DocumentPagePreviewFloatingPanel(
    doc: LoadedDocument,
    onDismiss: () -> Unit,
    onAdded: (String) -> Unit,
) {
    DisposableEffect(doc) {
        onDispose { doc.close() }
    }
    val agent = ServiceLocator.agentController
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val rendered = doc.kind == DocumentKind.PDF ||
        doc.kind == DocumentKind.PPTX ||
        doc.kind == DocumentKind.PPT
    val slideCapable = doc.kind == DocumentKind.PPTX || doc.kind == DocumentKind.PPT

    var selected by remember(doc) {
        mutableStateOf(if (rendered) setOf(1) else (1..doc.pageCount).toSet())
    }
    var focused by remember(doc) { mutableStateOf(1) }
    var rangeText by remember(doc) { mutableStateOf(PageRangeParser.format(selected)) }
    var rangeError by remember { mutableStateOf<String?>(null) }
    var previewMode by remember { mutableStateOf(DOC_PREVIEW_SLIDE) }
    var pageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loadingPage by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    val applySelection: (Set<Int>) -> Unit = { pages ->
        selected = pages
        rangeText = PageRangeParser.format(pages)
        rangeError = null
    }

    LaunchedEffect(doc, focused, previewMode) {
        if (!rendered || previewMode == DOC_PREVIEW_TEXT) {
            pageBitmap = null
            return@LaunchedEffect
        }
        loadingPage = true
        pageBitmap = doc.renderPageBitmap(focused - 1, 1400)?.asImageBitmap()
        loadingPage = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(MilkSpace.sm),
            contentAlignment = Alignment.Center,
        ) {
            val panelWidth = minOf(760.dp, maxWidth * 0.94f)
            val panelHeight = minOf(880.dp, maxHeight * 0.94f)
            Column(
                Modifier
                    .width(panelWidth)
                    .height(panelHeight)
                    .milkDialogSurface(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = MilkSpace.lg, top = MilkSpace.sm, end = MilkSpace.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            doc.fileName,
                            style = MilkText.panelTitle,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MilkStatusBadge(
                                text = when (doc.kind) {
                                    DocumentKind.MARKDOWN -> "MD"
                                    DocumentKind.TEXT -> "TXT"
                                    DocumentKind.PDF -> "PDF"
                                    DocumentKind.PPTX, DocumentKind.PPT -> "PPT"
                                },
                                tone = MilkStatusTone.ACCENT,
                                mono = true,
                            )
                            Spacer(Modifier.width(MilkSpace.xs))
                            Text(
                                "${doc.pageCount} 页",
                                style = MilkText.monoMicro,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                    MilkIconButton(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭文档预览",
                        onClick = onDismiss,
                        touchSize = 40.dp,
                        visualSize = 32.dp,
                        iconSize = 17.dp,
                    )
                }

                if (slideCapable) {
                    MilkSegmented(
                        options = listOf(DOC_PREVIEW_SLIDE, DOC_PREVIEW_TEXT),
                        selected = previewMode,
                        label = { if (it == DOC_PREVIEW_SLIDE) "幻灯片" else "文字" },
                        onSelect = { previewMode = it },
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MilkSpace.md),
                    )
                    Spacer(Modifier.height(MilkSpace.xs))
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = MilkSpace.md)
                        .clip(RoundedCornerShape(MilkRadius.md))
                        .background(if (dark) SurfaceElevatedDark.copy(alpha = 0.5f) else cs.surfaceVariant.copy(alpha = 0.35f)),
                ) {
                    when {
                        !rendered || previewMode == DOC_PREVIEW_TEXT -> {
                            val text = doc.pageText(focused - 1)
                            if (text.isBlank()) {
                                MilkEmptyState(
                                    title = "本页没有可提取文字",
                                    icon = Icons.Outlined.Description,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            } else if (doc.kind == DocumentKind.MARKDOWN || doc.kind == DocumentKind.TEXT) {
                                MilkMarkdownView(
                                    text = text,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(MilkSpace.md),
                                )
                            } else {
                                Text(
                                    text,
                                    style = MilkText.body,
                                    color = cs.onSurface,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(MilkSpace.md),
                                )
                            }
                        }

                        loadingPage -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = cs.primary,
                            )
                        }

                        else -> {
                            val bitmap = pageBitmap
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "第 $focused 页",
                                    modifier = Modifier.fillMaxSize().padding(MilkSpace.xs),
                                    contentScale = ContentScale.Fit,
                                )
                            } else {
                                MilkEmptyState(
                                    title = "本页无法渲染",
                                    icon = Icons.Outlined.Description,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MilkSpace.md, vertical = MilkSpace.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MilkIconButton(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "上一页",
                        onClick = { focused = (focused - 1).coerceAtLeast(1) },
                        enabled = focused > 1,
                        touchSize = 40.dp,
                        visualSize = 32.dp,
                        iconSize = 18.dp,
                    )
                    Text(
                        "第 $focused / ${doc.pageCount} 页",
                        style = MilkText.monoMeta,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        // 定宽:页码位数变化(9 → 10)不推动左右箭头
                        modifier = Modifier.widthIn(min = 96.dp),
                    )
                    MilkIconButton(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "下一页",
                        onClick = { focused = (focused + 1).coerceAtMost(doc.pageCount) },
                        enabled = focused < doc.pageCount,
                        touchSize = 40.dp,
                        visualSize = 32.dp,
                        iconSize = 18.dp,
                    )
                    Spacer(Modifier.weight(1f))
                    // 与「取消本页」同宽:选中/取消本页不推动左侧页码与右侧留白
                    MilkInlineAction(
                        label = if (focused in selected) "取消本页" else "选中本页",
                        onClick = {
                            applySelection(
                                if (focused in selected) selected - focused else selected + focused,
                            )
                        },
                        icon = if (focused in selected) Icons.Outlined.Check else Icons.Outlined.Add,
                        tone = if (focused in selected) MilkInlineTone.ACCENT else MilkInlineTone.NEUTRAL,
                        minWidth = 96.dp,
                    )
                }

                LazyRow(
                    Modifier.fillMaxWidth().padding(horizontal = MilkSpace.md),
                    horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
                ) {
                    items((1..doc.pageCount).toList(), key = { it }) { page ->
                        DocumentPageStripItem(
                            doc = doc,
                            page = page,
                            rendered = rendered,
                            landscape = slideCapable,
                            selected = page in selected,
                            focused = page == focused,
                            onClick = {
                                focused = page
                                applySelection(
                                    if (page in selected) selected - page else selected + page,
                                )
                            },
                        )
                    }
                }

                Column(Modifier.fillMaxWidth().padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm)) {
                    MilkTextField(
                        value = rangeText,
                        onValueChange = { text ->
                            rangeText = text
                            PageRangeParser.parse(text, doc.pageCount).fold(
                                onSuccess = { pages ->
                                    selected = pages.toSet()
                                    rangeError = null
                                },
                                onFailure = { failure ->
                                    rangeError = failure.message ?: "页码格式无效"
                                },
                            )
                        },
                        placeholder = "1-3, 5",
                        label = "页码",
                        mono = true,
                        onClear = { applySelection(emptySet()) },
                    )
                    // 常驻 18dp 错误行:输入 "1-" → "1-5" 期间面板高度零变化
                    Box(
                        Modifier.fillMaxWidth().height(18.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        val error = rangeError
                        if (error != null) {
                            Text(
                                error,
                                style = MilkText.monoMicro,
                                color = cs.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(MilkSpace.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs)) {
                        MilkInlineAction(
                            label = "当前页",
                            onClick = { applySelection(setOf(focused)) },
                            minWidth = 58.dp,
                        )
                        MilkInlineAction(
                            label = "全部",
                            onClick = { applySelection((1..doc.pageCount).toSet()) },
                            minWidth = 58.dp,
                        )
                        MilkInlineAction(
                            label = "反选",
                            onClick = { applySelection((1..doc.pageCount).toSet() - selected) },
                            minWidth = 58.dp,
                        )
                    }
                }

                MilkHairlineDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        PageRangeParser.formatChineseLabel(selected).ifBlank { "未选择页码" },
                        style = MilkText.monoMicro,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    MilkButton(
                        label = "取消",
                        onClick = onDismiss,
                        variant = MilkButtonVariant.SECONDARY,
                        size = MilkButtonSize.SMALL,
                        enabled = !adding,
                    )
                    Spacer(Modifier.width(MilkSpace.sm))
                    MilkButton(
                        label = "加入附件",
                        onClick = {
                            if (adding || selected.isEmpty()) return@MilkButton
                            adding = true
                            scope.launch {
                                val built = doc.buildAttachmentPayload(selected.sorted())
                                adding = false
                                if (built == null) {
                                    rangeError = "所选页面没有可发送内容"
                                } else {
                                    val name = agent.attachDocument(built)
                                    if (name != null) onAdded(name) else rangeError = "无法生成附件"
                                }
                            }
                        },
                        size = MilkButtonSize.SMALL,
                        enabled = selected.isNotEmpty(),
                        busy = adding,
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentPageStripItem(
    doc: LoadedDocument,
    page: Int,
    rendered: Boolean,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    landscape: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    var thumb by remember(doc, page) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(doc, page, rendered) {
        if (!rendered) return@LaunchedEffect
        thumb = doc.renderPageBitmap(page - 1, DOC_THUMB_PX)?.asImageBitmap()
    }
    val shape = RoundedCornerShape(MilkRadius.xs)
    // 横版演示文稿用 16:10 取景框,纵向文档用竖版取景框——缩略图一律 Fit,不裁掉内容
    val frameWidth = if (landscape) 86.dp else 56.dp
    val frameHeight = if (landscape) 54.dp else 72.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = frameWidth, height = frameHeight)
                .clip(shape)
                .background(
                    if (thumb != null) {
                        Color.White
                    } else if (dark) {
                        SurfaceElevatedDark
                    } else {
                        cs.surfaceVariant.copy(alpha = 0.5f)
                    },
                )
                .border(
                    width = if (focused) 1.5.dp else 0.75.dp,
                    color = when {
                        focused -> cs.primary
                        selected -> cs.primary.copy(alpha = 0.55f)
                        else -> cs.outline
                    },
                    shape = shape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = thumb
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "第 $page 页",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("$page", style = MilkText.monoMeta, color = cs.onSurfaceVariant)
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(cs.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = cs.onPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "$page",
            style = MilkText.monoMicro,
            color = if (focused) cs.primary else cs.onSurfaceVariant,
        )
    }
}

// ==================== 二级 C:笔记页面范围 ====================

@Composable
private fun NotebookPagesPanel(
    boundPage: PageRef?,
    pageIds: List<String>,
    onSnip: () -> Unit,
    onAdded: (Int) -> Unit,
) {
    val agent = ServiceLocator.agentController
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    var pages by remember { mutableStateOf<List<PageRef>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selection by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var rangeText by remember { mutableStateOf("") }
    var rangeError by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val thumbs = remember { mutableStateMapOf<String, ImageBitmap>() }
    // 整页渲染很重:限制并发 2,避免网格首次铺开时同时渲染十几页
    val thumbGate = remember { Semaphore(2) }

    LaunchedEffect(boundPage?.notebookId) {
        val notebookId = boundPage?.notebookId
        loading = true
        pages = if (notebookId == null) {
            emptyList()
        } else {
            runCatching { ServiceLocator.agentSnipSource.pagesOf(notebookId) }.getOrDefault(emptyList())
        }
        loading = false
        val current = pages.indexOfFirst { it.pageId == boundPage?.pageId }
        if (current >= 0) {
            selection = setOf(current + 1)
            rangeText = PageRangeParser.format(selection)
        }
    }

    val applySelection: (Set<Int>) -> Unit = { picked ->
        val bounded = picked.filter { it in 1..pages.size }.toSet()
        selection = bounded
        rangeText = PageRangeParser.format(bounded)
        rangeError = null
    }
    val currentIndex = pages.indexOfFirst { it.pageId == boundPage?.pageId }

    Column(Modifier.fillMaxWidth().padding(bottom = MilkSpace.sm)) {
        if (loading) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = cs.primary,
                )
            }
            return@Column
        }
        if (pages.isEmpty()) {
            MilkEmptyState(
                title = "当前没有可附加的页面",
                icon = Icons.Outlined.AutoStories,
            )
            return@Column
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MilkSpace.md),
            horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MilkInlineAction(
                label = if (currentIndex >= 0) "当前页 · 第 ${currentIndex + 1} 页" else "当前页",
                onClick = {
                    if (currentIndex >= 0) applySelection(setOf(currentIndex + 1))
                },
                enabled = currentIndex >= 0,
                selected = currentIndex >= 0 && selection == setOf(currentIndex + 1),
            )
            MilkInlineAction(
                label = "全部页",
                onClick = { applySelection((1..pages.size).toSet()) },
                minWidth = 58.dp,
            )
            MilkInlineAction(
                label = "清空",
                onClick = { applySelection(emptySet()) },
                enabled = selection.isNotEmpty(),
                minWidth = 58.dp,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 240.dp)
                .padding(horizontal = MilkSpace.md),
            horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
            verticalArrangement = Arrangement.spacedBy(MilkSpace.sm),
            contentPadding = PaddingValues(vertical = MilkSpace.xs),
        ) {
            items(pages.size, key = { pages[it].pageId }) { index ->
                val page = pages[index]
                val number = index + 1
                val selected = number in selection
                val shape = RoundedCornerShape(MilkRadius.sm)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(shape)
                            .background(if (dark) SurfaceElevatedDark else cs.surfaceVariant.copy(alpha = 0.45f))
                            .border(
                                width = if (selected) 1.5.dp else 0.75.dp,
                                color = if (selected) cs.primary else cs.outline,
                                shape = shape,
                            )
                            .clickable {
                                applySelection(
                                    if (selected) selection - number else selection + number,
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val thumb = thumbs[page.pageId]
                        if (thumb != null) {
                            Image(
                                bitmap = thumb,
                                contentDescription = formatPageDisplayLabel(page.pageId, pages.map { it.pageId }),
                                modifier = Modifier.fillMaxSize().background(Color.White),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            NotebookPageThumb(
                                page = page,
                                gate = thumbGate,
                                onLoaded = { bitmap -> thumbs[page.pageId] = bitmap },
                            )
                        }
                        if (selected) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(cs.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = cs.onPrimary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "$number",
                        style = MilkText.monoMicro,
                        color = if (selected) cs.primary else cs.onSurfaceVariant,
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = MilkSpace.md)) {
            MilkTextField(
                value = rangeText,
                onValueChange = { text ->
                    rangeText = text
                    PageRangeParser.parse(text, pages.size).fold(
                        onSuccess = { picked ->
                            selection = picked.toSet()
                            rangeError = null
                        },
                        onFailure = { failure ->
                            rangeError = failure.message ?: "页码格式无效"
                        },
                    )
                },
                placeholder = "1-3, 5",
                label = "页码范围",
                mono = true,
                onClear = { applySelection(emptySet()) },
            )
            // 常驻 18dp 错误行:输入过程中面板高度零变化
            Box(
                Modifier.fillMaxWidth().height(18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                val error = rangeError
                if (error != null) {
                    Text(
                        error,
                        style = MilkText.monoMicro,
                        color = cs.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(MilkSpace.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MilkButton(
                    label = "画布框选",
                    onClick = onSnip,
                    variant = MilkButtonVariant.SECONDARY,
                    size = MilkButtonSize.SMALL,
                    leading = {
                        Icon(
                            Icons.Outlined.CropFree,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = cs.onSurfaceVariant,
                        )
                    },
                )
                Spacer(Modifier.weight(1f))
                MilkButton(
                    label = "加入附件",
                    onClick = {
                        if (adding || selection.isEmpty()) return@MilkButton
                        val picked = selection.sorted().mapNotNull { pages.getOrNull(it - 1) }
                        if (picked.isEmpty()) return@MilkButton
                        adding = true
                        scope.launch {
                            val name = if (picked.size == 1) {
                                agent.addPageAttachment(picked.first())
                            } else {
                                agent.addPageRangeAttachment(
                                    picked,
                                    PageRangeParser.formatChineseLabel(selection),
                                )
                            }
                            adding = false
                            onAdded(if (name != null) picked.size else 0)
                        }
                    },
                    size = MilkButtonSize.SMALL,
                    enabled = selection.isNotEmpty(),
                    busy = adding,
                )
            }
            // 常驻 16dp 汇总行:选择变化只换文案,popover 高度不变
            Spacer(Modifier.height(MilkSpace.xxs))
            Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
                if (selection.isNotEmpty()) {
                    Text(
                        PageRangeParser.formatChineseLabel(selection),
                        style = MilkText.monoMicro,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotebookPageThumb(page: PageRef, gate: Semaphore, onLoaded: (ImageBitmap) -> Unit) {
    LaunchedEffect(page.pageId) {
        val bitmap = loadPageThumbnail(page, gate) ?: return@LaunchedEffect
        onLoaded(bitmap)
    }
    Icon(
        Icons.Outlined.AutoStories,
        contentDescription = null,
        modifier = Modifier.size(15.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
    )
}

internal suspend fun loadPageThumbnail(page: PageRef, gate: Semaphore? = null): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val load: suspend () -> ImageBitmap? = {
            runCatching {
                val captured = ServiceLocator.agentSnipSource.capturePage(page) ?: return@runCatching null
                decodeSampledBitmap(captured.png, PAGE_THUMB_PX)?.asImageBitmap()
            }.getOrNull()
        }
        if (gate != null) gate.withPermit { load() } else load()
    }

private fun decodeSampledBitmap(bytes: ByteArray, maxEdgePx: Int): Bitmap? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxEdgePx && bounds.outHeight / (sample * 2) >= maxEdgePx) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
}

// ==================== 文档导出(与预览窗共用同一套规则) ====================

/** 把生成文档写成 `cache/export` 下的文件并交给系统 Sharesheet;失败返回 false */
internal suspend fun exportGeneratedDocument(
    context: Context,
    document: MarkdownLite.Block.GeneratedDocument,
): Boolean {
    val extension = when (document.type) {
        MarkdownLite.GeneratedDocType.MARKDOWN -> "md"
        MarkdownLite.GeneratedDocType.TEXT -> "txt"
        MarkdownLite.GeneratedDocType.LATEX -> "tex"
    }
    val mimeType = when (document.type) {
        MarkdownLite.GeneratedDocType.MARKDOWN -> "text/markdown"
        MarkdownLite.GeneratedDocType.TEXT -> "text/plain"
        MarkdownLite.GeneratedDocType.LATEX -> "application/x-tex"
    }
    val fileName = "${document.title.ifBlank { "文档" }}.$extension"
    val file = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, ATTACH_EXPORT_DIR).apply { mkdirs() }
            File(dir, sanitizeExportName(fileName)).apply {
                writeBytes(document.content.toByteArray(Charsets.UTF_8))
            }
        }.getOrNull()
    } ?: return false

    return withContext(Dispatchers.Main) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出文档"))
        }.isSuccess
    }
}

private fun sanitizeExportName(raw: String): String {
    val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim()
    val dot = cleaned.lastIndexOf('.')
    val stem = if (dot in 1 until cleaned.length - 1) cleaned.substring(0, dot) else cleaned
    val extension = if (dot in 1 until cleaned.length - 1) cleaned.substring(dot) else ""
    return stem.take(48).ifBlank { "导出文件" } + extension.take(8)
}

/** 供输入框判定的流式状态 */
internal fun AgentUiState.isAgentStreaming(): Boolean =
    phase == AgentMessage.Phase.STREAMING || phase == AgentMessage.Phase.SENDING
