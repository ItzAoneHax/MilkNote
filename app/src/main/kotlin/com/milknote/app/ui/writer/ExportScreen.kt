package com.milknote.app.ui.writer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milknote.app.agent.AgentBridge
import com.milknote.app.agent.PageRenderer
import com.milknote.app.ui.common.MilkIcons
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.milkEdgeHairline
import com.milknote.app.ui.settings.Hairline
import com.milknote.app.ui.settings.SettingsCard
import com.milknote.app.ui.settings.SettingsGroup
import com.milknote.app.ui.settings.SettingsPageHeader
import com.milknote.app.ui.settings.SettingsSectionHeader
import com.milknote.app.ui.settings.TextAction
import com.milknote.app.ui.theme.LocalMilkSuccess
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
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

    // 内容滚动位置:顶栏底边 hairline 只在离开顶部后浮现
    val scrollState = rememberScrollState()
    val scrolled by remember { derivedStateOf { scrollState.value > 0 } }

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
        // 顶栏底边 hairline 只在内容滚动后浮现(顶部静止时保持干净)
        ExportTopBar(notebookName = nb?.name ?: "", onBack = onBack, scrolled = scrolled)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            Column(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 560.dp)   // 先限宽再 fill(顺序反了会通栏;平板横屏不溢出)
                    .fillMaxWidth()
                    .padding(horizontal = MilkSpace.xxl + 8.dp, vertical = MilkSpace.xxl),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                SettingsPageHeader(
                    title = "导出",
                    subtitle = if (nb == null) {
                        "未打开笔记本"
                    } else {
                        val current = state.currentPageIndex
                        val currentLabel = if (current in pages.indices) " · 当前第 ${current + 1} 页" else ""
                        "「${nb.name}」 · ${state.pages.size} 页$currentLabel"
                    },
                )

                // ---- 格式 ----
                SettingsGroup {
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
                }

                // ---- 文件名 ----
                SettingsGroup {
                    SettingsSectionHeader("文件名", "不含扩展名，导出时自动补全；非法字符会替换为下划线")
                    SettingsCard {
                        Column(Modifier.padding(horizontal = MilkSpace.lg, vertical = 14.dp)) {
                            MilkTextField(
                                value = fileName,
                                onValueChange = { fileName = it },
                                placeholder = "如：高数·第三章",
                                enabled = !exporting,
                                imeAction = ImeAction.Done,
                                onDone = { focusManager.clearFocus() },
                                onClear = { fileName = "" },
                            )
                            Spacer(Modifier.height(MilkSpace.sm))
                            Text(
                                "保存为：$preview",
                                style = MilkText.monoMeta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ---- 页面范围 / 备份内容 ----
                if (format.isBackup) {
                    SettingsGroup {
                        SettingsSectionHeader("备份内容", "整本打包，不提供部分页面选择")
                        SettingsCard {
                            Column(
                                Modifier.padding(horizontal = MilkSpace.lg, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(MilkSpace.xs),
                            ) {
                                Text(
                                    "包含 notebook.json、全部 ${state.pages.size} 页的笔迹、批注卡、图片元素与 images 附件。",
                                    style = MilkText.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "这是 MilkNote 自有的 .milk 备份容器，不是千本笔记 ZIP，也不冒充其格式。",
                                    style = MilkText.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    SettingsGroup {
                        SettingsSectionHeader("页面范围", "按笔记本页序导出，不改变页面语义")
                        SettingsCard {
                            Column(Modifier.padding(horizontal = MilkSpace.lg, vertical = 14.dp)) {
                                MilkSegmented(
                                    options = ExportRangeMode.entries,
                                    selected = rangeMode,
                                    label = { it.label },
                                    onSelect = { mode ->
                                        if (rangeMode != mode) {
                                            rangeMode = mode
                                            outcome = null
                                            if (mode == ExportRangeMode.SELECTED && selectedPages.isEmpty()) {
                                                selectedPages = pageIds.toSet()
                                            }
                                        }
                                    },
                                    enabled = !exporting,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    when (rangeMode) {
                                        ExportRangeMode.ALL -> "共 ${pageIds.size} 页，全部导出。"
                                        ExportRangeMode.CURRENT -> if (state.currentPageIndex >= 0) {
                                            "仅导出第 ${state.currentPageIndex + 1} 页。"
                                        } else {
                                            "当前页不在页面列表中。"
                                        }
                                        ExportRangeMode.SELECTED -> "点选缩略图，已选页面按笔记本页序导出。"
                                    },
                                    style = MilkText.caption,
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
                }

                // ---- 操作 ----
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MilkSpace.md),
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
                    style = MilkText.caption,
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
            // 每页只回放一次操作日志,再分发给 PDF(页/图片/媒体共用同一份快照)
            val drawings = pageIds.associateWith { pageId ->
                repo.loadDrawing(notebook.id, pageId)
            }
            val pages = pageIds.map { pageId ->
                val drawing = drawings.getValue(pageId)
                Triple(pageId, drawing.strokes, drawing.cards)
            }
            val saved = PageExporter.savePdfFile(
                context = context,
                pages = pages,
                paper = notebook.paper,
                fileName = names.first(),
                pageSize = notebook.pageSize,
                orientation = notebook.orientation,
                imageDir = dir.absolutePath,
                imagesOf = { pageId -> drawings[pageId]?.images ?: emptyList() },
                mediaOf = { pageId -> drawings[pageId]?.media ?: emptyList() },
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
                    // 单次 loadDrawing:笔迹/批注/图片/媒体来自同一份回放结果
                    val drawing = repo.loadDrawing(notebook.id, pageId)
                    val data = AgentBridge.PageData(
                        strokes = drawing.strokes,
                        cards = drawing.cards,
                        paper = spec.paper,
                        pageSize = notebook.pageSize,
                        orientation = notebook.orientation,
                        images = drawing.images,
                        media = drawing.media,
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

/**
 * 顶部返回条:surface 底 + 返回钮 48dp 命中 + 面板标题;右侧笔记本名 mono(超长省略)。
 * 底边 hairline 跟随 [scrolled]:停在顶部时无线,向下滚动后 150ms 强 ease-out 淡入,把层次交给内容本身。
 */
@Composable
private fun ExportTopBar(notebookName: String, onBack: () -> Unit, scrolled: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val edgeAlpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(MilkMotion.color, easing = MilkMotion.EaseOut),
        label = "exportTopBarEdge",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(cs.surface)
            .milkEdgeHairline(cs.outline.copy(alpha = 0.6f * edgeAlpha), bottom = true)
            .height(60.dp)
            .padding(start = MilkSpace.xs, end = MilkSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MilkIconButton(
            imageVector = MilkIcons.ArrowLeft,
            contentDescription = "返回书写",
            onClick = onBack,
        )
        Spacer(Modifier.width(MilkSpace.xxs))
        Text("导出", style = MilkText.panelTitle, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
        Spacer(Modifier.width(MilkSpace.md))
        Text(
            notebookName,
            style = MilkText.monoMeta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
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
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val iconTint by animateColorAsState(
        if (selected) cs.primary else cs.onSurfaceVariant,
        MilkMotion.colorSpec(),
        label = "exportFormatTint",
    )
    val iconBg by animateColorAsState(
        if (selected) cs.primary.copy(alpha = 0.12f) else cs.surfaceVariant.copy(alpha = 0.5f),
        MilkMotion.colorSpec(),
        label = "exportFormatBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.RadioButton) {
                // 同帧触感:只有真的切换到另一种格式才震
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelect()
            }
            .heightIn(min = 68.dp)
            .padding(horizontal = MilkSpace.lg, vertical = MilkSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(MilkRadius.md))
                .background(iconBg)
                .border(
                    1.dp,
                    if (selected) cs.primary.copy(alpha = 0.32f) else cs.outline,
                    RoundedCornerShape(MilkRadius.md),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(formatIcon(format), null, tint = iconTint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(MilkSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                format.title,
                style = MilkText.cardTitle,
                color = if (selected) cs.primary else cs.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                format.description,
                style = MilkText.caption,
                color = cs.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    MilkIcons.Check,
                    contentDescription = "当前格式",
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Box(
                    Modifier
                        .size(16.dp)
                        .border(1.5.dp, cs.outline, CircleShape),
                )
            }
        }
    }
}

private fun formatIcon(format: ExportFormat): ImageVector = when (format) {
    ExportFormat.PDF -> MilkIcons.FilePdf
    ExportFormat.JPG -> MilkIcons.Image
    ExportFormat.PNG -> MilkIcons.PhotoLibrary
    ExportFormat.ZIP -> MilkIcons.FileZip
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
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "已选 ${selected.size}/${pages.size}",
                style = MilkText.monoMeta,
                color = cs.onSurfaceVariant,
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
        Spacer(Modifier.height(MilkSpace.xs))

        if (pages.isEmpty()) {
            Text(
                "没有可导出的页面",
                style = MilkText.caption,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(vertical = MilkSpace.md),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(84.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(MilkSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(MilkSpace.sm),
            ) {
                itemsIndexed(pages, key = { _, page -> page.pageId }) { index, page ->
                    val isSelected = page.pageId in selected
                    val isCurrent = page.pageId == currentPageId
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MilkRadius.md))
                            .background(if (isSelected) cs.primary.copy(alpha = 0.08f) else cs.surface)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) cs.primary else cs.outline,
                                shape = RoundedCornerShape(MilkRadius.md),
                            )
                            .clickable(enabled = enabled) { onToggle(page.pageId) }
                            .padding(5.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(pageAspect)
                                .clip(RoundedCornerShape(MilkRadius.sm))
                                .background(cs.surfaceVariant.copy(alpha = 0.35f)),
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
                                    "无预览",
                                    style = MilkText.micro,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                            if (isSelected) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(MilkSpace.xxs)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(cs.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        MilkIcons.Check,
                                        contentDescription = "已选择",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(MilkSpace.xxs))
                        // 只展示可读页码(内部 pageId 不外露)
                        Text(
                            if (isCurrent) "第 ${index + 1} 页 · 当前" else "第 ${index + 1} 页",
                            style = MilkText.micro,
                            maxLines = 1,
                            color = if (isSelected) cs.primary else cs.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }
    }
}

/** 结果状态行:成功色(已导出…) / 错误色(保存失败/没有页面),就地展示不弹窗 */
@Composable
private fun ResultLine(msg: String) {
    val ok = msg.startsWith("已导出")
    val tint = if (ok) LocalMilkSuccess.current else MaterialTheme.colorScheme.error
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MilkRadius.lg))
            .background(tint.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = MilkSpace.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (ok) MilkIcons.CheckCircle else MilkIcons.ErrorCircle,
            contentDescription = if (ok) "成功" else "失败",
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(msg, style = MilkText.caption, color = tint)
    }
}
