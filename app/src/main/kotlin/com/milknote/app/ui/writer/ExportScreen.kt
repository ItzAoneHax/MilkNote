package com.milknote.app.ui.writer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image as ImageIcon
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.agent.AgentBridge
import com.milknote.app.agent.PageRenderer
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.innerHighlight
import com.milknote.app.ui.settings.Hairline
import com.milknote.app.ui.settings.SettingsCard
import com.milknote.app.ui.settings.SettingsPageHeader
import com.milknote.app.ui.settings.SettingsSectionHeader
import com.milknote.app.ui.settings.TextAction
import com.milknote.app.ui.theme.Mono
import com.milknote.notes.model.NotebookMeta
import com.milknote.notes.model.NotebookPage
import com.milknote.notes.model.PAGE_SIZES_MM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ExportOutcome(
    val message: String,
    val mimeType: String,
    val files: List<SavedExport> = emptyList(),
)

/**
 * 独立导出页(MilkScreen.Export):顶部返回条 + 居中 ≤560dp 内容列(视觉沿设置页/§5.1「柔和的皮」)。
 * 配置项:PDF/JPG/PNG/ZIP(MilkNote .milk 备份)、文件名、页面范围(全部/当前/缩略图多选)、导出与系统分享。
 * 渲染统一走 PageRenderer;数据经 ServiceLocator.repository 只读加载,不修改 WriterViewModel。
 */
@Composable
fun ExportScreen(
    vm: WriterViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val thumbs by vm.thumbs.collectAsState()
    val nb = state.notebook
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var format by remember(nb?.id) { mutableStateOf(ExportFormat.PDF) }
    var fileName by remember(nb?.id) { mutableStateOf(nb?.name.orEmpty()) }
    var rangeMode by remember(nb?.id) { mutableStateOf(ExportRangeMode.ALL) }
    var selectedPages by remember(nb?.id) { mutableStateOf<Set<String>>(emptySet()) }
    var exporting by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<ExportOutcome?>(null) }

    val pages = state.pages
    val pageIds = pages.map { it.pageId }
    val validSelected = selectedPages.filterTo(linkedSetOf()) { it in pageIds }
    val rangeIds = resolveExportPageIds(pageIds, state.currentPageId, rangeMode, validSelected)
    val canExport = nb != null && !exporting && (format.isBackup || rangeIds.isNotEmpty())

    val preview = exportPreview(fileName, format, if (format.isImage) rangeIds else emptyList())

    val pageAspect = remember(nb?.pageSize, nb?.orientation) {
        val (pwMm, phMm) = PAGE_SIZES_MM[nb?.pageSize] ?: PAGE_SIZES_MM.getValue("A4")
        if (nb?.orientation == "landscape") hMmRatio(phMm, pwMm) else hMmRatio(pwMm, phMm)
    }

    val startExport: () -> Unit = {
        val notebook = nb
        if (notebook != null && canExport) {
            val ids = if (format.isBackup) emptyList() else rangeIds
            val exportFormat = format
            val baseName = fileName
            focusManager.clearFocus()
            exporting = true
            outcome = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { performExport(notebook, exportFormat, baseName, ids) }
                        .getOrElse { t ->
                            ExportOutcome(
                                message = "导出失败：${t.message ?: "未知错误"}",
                                mimeType = exportFormat.mimeType,
                            )
                        }
                }
                exporting = false
                outcome = result
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)   // 背景先铺满(含状态栏区域)
            .windowInsetsPadding(WindowInsets.safeDrawing),     // 内容再避开 insets
    ) {
        ExportTopBar(notebookName = nb?.name ?: "", onBack = onBack)

        // 顶栏/内容分隔:左右渐隐 hairline(与设置页导航分隔同语言)
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.05f to MaterialTheme.colorScheme.outline,
                        0.95f to MaterialTheme.colorScheme.outline,
                        1f to Color.Transparent,
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 560.dp)   // 先限宽再 fill(顺序反了会通栏;平板横屏不溢出)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                SettingsPageHeader(
                    title = "导出",
                    subtitle = if (nb == null) {
                        "未打开笔记本"
                    } else {
                        "「${nb.name}」 · ${state.pages.size} 页 · 当前 ${state.currentPageId}"
                    },
                )

                // ---- 格式 ----
                SettingsSectionHeader("导出格式", "均使用与缩略图相同的页面渲染，含笔迹、图片与批注卡")
                SettingsCard {
                    ExportFormat.entries.forEachIndexed { index, item ->
                        ExportFormatRow(
                            format = item,
                            selected = format == item,
                            enabled = !exporting,
                            onSelect = {
                                if (format != item) {
                                    format = item
                                    outcome = null
                                }
                            },
                        )
                        if (index < ExportFormat.entries.lastIndex) Hairline()
                    }
                }

                // ---- 文件名 ----
                SettingsSectionHeader("文件名", "不含扩展名，导出时自动补全；非法字符会替换为下划线")
                SettingsCard {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        var nameFocused by remember { mutableStateOf(false) }
                        val borderColor by animateColorAsState(
                            if (nameFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            tween(180),
                            label = "exportNameBorder",
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            if (fileName.isEmpty()) {
                                Text(
                                    "如：高数·第三章",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                            BasicTextField(
                                value = fileName,
                                onValueChange = { fileName = it },
                                singleLine = true,
                                enabled = !exporting,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onBackground,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { nameFocused = it.isFocused },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "保存为：$preview",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontFamily = Mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ---- 页面范围 / 备份内容 ----
                if (format.isBackup) {
                    SettingsSectionHeader("备份内容", "整本打包，不提供部分页面选择")
                    SettingsCard {
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "包含 notebook.json、全部 ${state.pages.size} 页的笔迹、批注卡、图片元素与 images 附件。",
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "这是 MilkNote 自有的 .milk 备份容器，不是千本笔记 ZIP，也不冒充其格式。",
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    SettingsSectionHeader("页面范围", "按笔记本页序导出，不改变页面语义")
                    SettingsCard {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ExportRangeMode.entries.forEach { mode ->
                                    RangeChip(
                                        label = mode.label,
                                        selected = rangeMode == mode,
                                        enabled = !exporting,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            if (rangeMode != mode) {
                                                rangeMode = mode
                                                outcome = null
                                                if (mode == ExportRangeMode.SELECTED && selectedPages.isEmpty()) {
                                                    selectedPages = pageIds.toSet()
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                when (rangeMode) {
                                    ExportRangeMode.ALL -> "共 ${pageIds.size} 页，全部导出。"
                                    ExportRangeMode.CURRENT -> if (state.currentPageIndex >= 0) {
                                        "仅导出第 ${state.currentPageIndex + 1} 页（${state.currentPageId}）。"
                                    } else {
                                        "当前页不在页面列表中。"
                                    }
                                    ExportRangeMode.SELECTED -> "点选缩略图，已选页面按笔记本页序导出。"
                                },
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            if (rangeMode == ExportRangeMode.SELECTED) {
                                Spacer(Modifier.height(10.dp))
                                PageRangePicker(
                                    pages = pages,
                                    thumbs = thumbs,
                                    selected = validSelected,
                                    currentPageId = state.currentPageId,
                                    pageAspect = pageAspect,
                                    enabled = !exporting,
                                    onToggle = { pageId ->
                                        selectedPages = if (pageId in selectedPages) {
                                            selectedPages - pageId
                                        } else {
                                            selectedPages + pageId
                                        }
                                        outcome = null
                                    },
                                    onSelectAll = {
                                        selectedPages = pageIds.toSet()
                                        outcome = null
                                    },
                                    onClear = {
                                        selectedPages = emptySet()
                                        outcome = null
                                    },
                                )
                            }
                        }
                    }
                }

                // ---- 操作 ----
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MilkButton(
                        label = if (exporting) "导出中…" else actionLabel(format),
                        busy = exporting,
                        enabled = canExport,
                        onClick = startExport,
                    )
                    val files = outcome?.files.orEmpty()
                    if (files.isNotEmpty()) {
                        MilkButton(
                            label = "分享",
                            variant = MilkButtonVariant.SECONDARY,
                            enabled = !exporting,
                            onClick = {
                                ExportShare.share(
                                    ServiceLocator.appContext,
                                    files.map { it.uri },
                                    outcome?.mimeType ?: format.mimeType,
                                )
                            },
                        )
                    }
                }

                outcome?.let { ResultLine(it.message) }

                Text(
                    "图片保存到「图片/MilkNote」，PDF/ZIP 保存到「文档/MilkNote」；无需存储权限。",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 文件名预览:图片多页时显示首个文件名与数量。 */
private fun exportPreview(baseName: String, format: ExportFormat, pageIds: List<String>): String {
    if (format.isImage && pageIds.isEmpty()) return "请选择至少一页"
    val names = exportFileNames(baseName, format, pageIds)
    return when {
        names.isEmpty() -> "请选择至少一页"
        names.size == 1 -> names.first()
        else -> "${names.first()} 等 ${names.size} 个文件"
    }
}

/** 在 IO 线程执行:按格式读取仓库并调用 PageRenderer/PageExporter。 */
private fun performExport(
    notebook: NotebookMeta,
    format: ExportFormat,
    baseName: String,
    pageIds: List<String>,
): ExportOutcome {
    val context = ServiceLocator.appContext
    val repo = ServiceLocator.repository
    val dir = repo.notebookDir(notebook.id)
    val names = exportFileNames(baseName, format, pageIds)
    if (!format.isBackup && (pageIds.isEmpty() || names.isEmpty())) {
        return ExportOutcome("请选择至少一页", format.mimeType)
    }

    return when (format) {
        ExportFormat.PDF -> {
            val pages = pageIds.map { pageId ->
                Triple(
                    pageId,
                    repo.loadPage(notebook.id, pageId),
                    repo.loadAnnotations(notebook.id, pageId),
                )
            }
            val saved = PageExporter.savePdfFile(
                context = context,
                pages = pages,
                paper = notebook.paper,
                fileName = names.first(),
                pageSize = notebook.pageSize,
                orientation = notebook.orientation,
                imageDir = dir.absolutePath,
                imagesOf = { pageId -> repo.loadImages(notebook.id, pageId) },
                mediaOf = { pageId -> repo.loadMedia(notebook.id, pageId) },
                pageSpecOf = { pageId -> notebook.pageSpec(pageId) },
            ) ?: return ExportOutcome("PDF 保存失败，请检查存储空间", format.mimeType)
            ExportOutcome("已导出 PDF（${pageIds.size} 页）→ ${saved.location}", format.mimeType, listOf(saved))
        }

        ExportFormat.JPG, ExportFormat.PNG -> {
            var failed = 0
            val saved = ArrayList<SavedExport>(pageIds.size)
            for ((index, pageId) in pageIds.withIndex()) {
                val file = runCatching {
                    val spec = notebook.pageSpec(pageId)
                    val data = AgentBridge.PageData(
                        strokes = repo.loadPage(notebook.id, pageId),
                        cards = repo.loadAnnotations(notebook.id, pageId),
                        paper = spec.paper,
                        pageSize = notebook.pageSize,
                        orientation = notebook.orientation,
                        images = repo.loadImages(notebook.id, pageId),
                        media = repo.loadMedia(notebook.id, pageId),
                        imageDir = dir.absolutePath,
                        paperColor = spec.paperColor,
                        pageWidthMm = spec.widthMm,
                        pageHeightMm = spec.heightMm,
                    )
                    val bitmap = PageRenderer.renderFull(data)
                    try {
                        PageExporter.saveImageFile(context, bitmap, names[index], format)
                    } finally {
                        bitmap.recycle()
                    }
                }.getOrNull()
                if (file == null) failed++ else saved += file
            }
            if (saved.isEmpty()) {
                ExportOutcome("图片保存失败，请检查存储空间", format.mimeType)
            } else {
                val suffix = if (failed > 0) "，${failed} 页失败" else ""
                ExportOutcome(
                    message = "已导出 ${saved.size} 张 ${format.label}$suffix → ${saved.first().location}",
                    mimeType = format.mimeType,
                    files = saved,
                )
            }
        }

        ExportFormat.ZIP -> {
            val saved = PageExporter.saveZipBackup(
                context = context,
                notebookDir = dir,
                fileName = names.first(),
                rootDirName = exportZipRootDir(notebook.name),
            ) ?: return ExportOutcome("备份保存失败，请检查存储空间", format.mimeType)
            ExportOutcome("已导出 MilkNote 备份 → ${saved.location}", format.mimeType, listOf(saved))
        }
    }
}

private fun actionLabel(format: ExportFormat): String = when (format) {
    ExportFormat.PDF -> "导出 PDF"
    ExportFormat.JPG -> "导出 JPG"
    ExportFormat.PNG -> "导出 PNG"
    ExportFormat.ZIP -> "备份整本"
}

private fun hMmRatio(width: Float, height: Float): Float =
    if (height <= 0f) 0.707f else width / height

/** 顶部返回条:surface 底 + 底部 hairline;返回钮 40dp + 标题;右侧笔记本名 mono */
@Composable
private fun ExportTopBar(notebookName: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .edgeHairline(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), bottom = true)
            .height(60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBack)
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack, "返回书写",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("导出", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.weight(1f))
        Text(
            notebookName,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = Mono),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExportFormatRow(
    format: ExportFormat,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val iconTint by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(180),
        label = "exportFormatTint",
    )
    val iconBg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tween(180),
        label = "exportFormatBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 68.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconBg)
                .border(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                    else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(11.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(formatIcon(format), null, tint = iconTint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                format.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                format.description,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "当前格式",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Box(
                    Modifier
                        .size(16.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
        }
    }
}

private fun formatIcon(format: ExportFormat): ImageVector = when (format) {
    ExportFormat.PDF -> Icons.Outlined.PictureAsPdf
    ExportFormat.JPG -> Icons.Outlined.ImageIcon
    ExportFormat.PNG -> Icons.Outlined.Photo
    ExportFormat.ZIP -> Icons.Outlined.Archive
}

/** 范围胶囊:复用共享 MilkChip,三等分 */
@Composable
private fun RangeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    com.milknote.app.ui.common.MilkChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(42.dp),
        enabled = enabled,
    )
}

/** 缩略图多选:首行计数/全选/取消全选,下方自适应网格。 */
@Composable
private fun PageRangePicker(
    pages: List<NotebookPage>,
    thumbs: Map<String, ImageBitmap>,
    selected: Set<String>,
    currentPageId: String,
    pageAspect: Float,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "已选 ${selected.size}/${pages.size}",
                fontSize = 12.sp,
                fontFamily = Mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextAction(
                label = "全选",
                primary = true,
                enabled = enabled && selected.size < pages.size,
                onClick = onSelectAll,
            )
            TextAction(
                label = "取消全选",
                enabled = enabled && selected.isNotEmpty(),
                onClick = onClear,
            )
        }
        Spacer(Modifier.height(6.dp))

        if (pages.isEmpty()) {
            Text(
                "没有可导出的页面",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(84.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(pages, key = { _, page -> page.pageId }) { index, page ->
                    val isSelected = page.pageId in selected
                    val isCurrent = page.pageId == currentPageId
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else MaterialTheme.colorScheme.surface,
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable(enabled = enabled) { onToggle(page.pageId) }
                            .padding(5.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(pageAspect)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        ) {
                            val thumb = thumbs[page.pageId]
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb,
                                    contentDescription = "第 ${index + 1} 页",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    page.pageId,
                                    fontSize = 9.sp,
                                    fontFamily = Mono,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                            if (isSelected) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = "已选择",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (isCurrent) "第 ${index + 1} 页 · 当前" else "第 ${index + 1} 页",
                            fontSize = 10.sp,
                            maxLines = 1,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        Text(
                            page.pageId,
                            fontSize = 8.sp,
                            fontFamily = Mono,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }
    }
}

/** 结果状态行:✓ 淡蓝(已导出…) / ✗ 淡红(保存失败/没有页面),就地展示不弹窗 */
@Composable
private fun ResultLine(msg: String) {
    val ok = msg.startsWith("已导出")
    val tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = if (ok) "成功" else "失败",
            tint = tint,
            modifier = Modifier.size(18.dp).padding(top = 0.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(msg, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = tint)
    }
}
