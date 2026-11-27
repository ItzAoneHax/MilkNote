package com.milknote.app.ui.writer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.MoreHoriz
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.common.MilkActionPanel
import com.milknote.app.ui.common.MilkEmptyState
import com.milknote.app.ui.common.MilkPanelAction
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.dashedBorder
import com.milknote.app.ui.common.milkGlassRim
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.PillShape
import kotlin.math.roundToInt

internal val WriterExpandedRailWidth = 248.dp

/** P058：双列页面缩略图覆盖于固定纸面之上，页码与前后翻页固定在栏底。
 *  千本笔记本轮实测：缩略图即拖拽柄，长按后拖到目标格释放完成重排，第 1 页自动成为封面。 */
@Composable
internal fun WriterPageRail(
    state: WriterUiState,
    thumbs: Map<String, ImageBitmap>,
    onSwitch: (String) -> Unit,
    onNewPage: (String?) -> Unit,
    onDuplicatePage: (String) -> Unit,
    onClearPage: (String) -> Unit,
    onDeletePage: (String) -> Unit,
    onMovePage: (String, Int) -> Unit = { _, _ -> },
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShowSnippets: () -> Unit,
    onShowOutline: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val nb = state.notebook
    val listState = rememberLazyListState()
    var menuPageId by remember { mutableStateOf<String?>(null) }
    var clearTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    // ---- 长按拖拽重排(千本 P058):拖动显示浮动缩略影,目标格高亮,释放落位 ----
    var dragPageId by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var dragOrigin by remember { mutableStateOf(Offset.Zero) }
    var listOrigin by remember { mutableStateOf(Offset.Zero) }
    var hoveredIndex by remember { mutableStateOf(-1) }
    fun hoverIndexAt(position: Offset): Int {
        val local = position - listOrigin
        val info = listState.layoutInfo
        val row = info.visibleItemsInfo.firstOrNull {
            local.y >= it.offset && local.y <= it.offset + it.size
        } ?: return -1
        val lastPageIndex = state.pages.lastIndex
        if (row.index * 2 > lastPageIndex) return -1 // 落在新建行/空白处
        val col = if (local.x < info.viewportSize.width / 2f) 0 else 1
        return (row.index * 2 + col).coerceIn(0, lastPageIndex)
    }
    val rows = state.pages.chunked(2)
    LaunchedEffect(state.currentPageId, state.pages.size) {
        val visibleRows = listState.layoutInfo.visibleItemsInfo.map { it.index }.toSet()
        pageRailScrollTarget(state.pages.map { it.pageId }, state.currentPageId, visibleRows)?.let {
            listState.scrollToItem(it)
        }
    }
    Box(
        modifier
            .onGloballyPositioned { listOrigin = it.positionInRoot() }
            .testTag("writer-page-panel"),
    ) {
        Column(
            Modifier
                .width(WriterExpandedRailWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .edgeHairline(MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), right = true),
        ) {
        WriterRailSegmentBar(
            activeMode = WriterRailMode.PAGES,
            onPages = {},
            onSnippets = onShowSnippets,
            onOutline = onShowOutline,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("writer-page-rail")
                .semantics { contentDescription = "双列页面侧栏，可纵向滚动" },
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
        ) {
            items(rows.size, key = { row -> rows[row].first().pageId }) { rowIndex ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rows[rowIndex].forEachIndexed { column, page ->
                        val index = rowIndex * 2 + column
                        val spec = nb?.pageSpec(page.pageId)
                        val pageAspect = if (spec != null) spec.widthMm / spec.heightMm else 0.707f
                        val active = page.pageId == state.currentPageId
                        var thumbOrigin by remember { mutableStateOf(Offset.Zero) }
                        val dragAccumulator = remember(page.pageId) { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
                        val dragging = dragPageId == page.pageId
                        val hoverTarget = dragPageId != null && !dragging && hoveredIndex == index
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(MilkRadius.sm))
                                .border(
                                    width = when {
                                        hoverTarget -> 2.dp
                                        active -> 1.5.dp
                                        else -> 0.5.dp
                                    },
                                    color = when {
                                        hoverTarget -> MaterialTheme.colorScheme.primary
                                        active -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                    shape = RoundedCornerShape(MilkRadius.sm),
                                )
                                .onGloballyPositioned { thumbOrigin = it.positionInRoot() }
                                .pointerInput(page.pageId) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { pos ->
                                            dragAccumulator.value = Offset.Zero
                                            dragPageId = page.pageId
                                            dragOrigin = thumbOrigin
                                            dragPosition = thumbOrigin + pos
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragAccumulator.value += amount
                                            dragPosition = dragOrigin + dragAccumulator.value
                                            hoveredIndex = hoverIndexAt(dragPosition)
                                        },
                                        onDragEnd = {
                                            val target = hoverIndexAt(dragPosition)
                                            val current = state.pages.indexOfFirst { it.pageId == page.pageId }
                                            if (target >= 0 && target != current) onMovePage(page.pageId, target)
                                            dragPageId = null
                                            hoveredIndex = -1
                                        },
                                        onDragCancel = {
                                            dragPageId = null
                                            hoveredIndex = -1
                                        },
                                    )
                                }
                                .clickable(enabled = dragPageId == null) { onSwitch(page.pageId) }
                                .testTag("writer-page-${page.pageId}")
                                .semantics { contentDescription = "第 ${index + 1} 页 ${page.pageId}" },
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(pageAspect)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .then(Modifier.graphicsLayer { alpha = if (dragging) 0.35f else 1f }),
                            ) {
                                val bmp = thumbs[page.pageId]
                                if (bmp != null) {
                                    Image(
                                        bmp,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(pageAspect),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(48.dp)
                                        .clickable { menuPageId = page.pageId }
                                        .testTag("writer-page-more-${page.pageId}"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f))
                                            .border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Outlined.MoreHoriz,
                                            "第 ${index + 1} 页操作",
                                            modifier = Modifier.size(18.dp),
                                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Text(
                                if (index == 0) "1-封面" else "${index + 1}",
                                style = MilkText.monoMeta,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            )
                        }
                    }
                    if (rows[rowIndex].size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item(key = "__add__") {
                // 与书架「新建笔记本」卡同一语言:虚线圆角框 + 图标 + 文字,不再是直角空框
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .dashedBorder(MaterialTheme.colorScheme.outline, 1.5.dp, cornerRadius = MilkRadius.md)
                        .clip(RoundedCornerShape(MilkRadius.md))
                        .clickable { onNewPage(state.currentPageId) }
                        .testTag("writer-rail-new-page")
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "新建页面",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // 页码与翻页交给画布左下角的悬浮页码胶囊,抽屉底部不再重复一条翻页栏
        }
        // 浮动缩略影:跟随拖拽点,落在目标格中央
        dragPageId?.let { pid ->
            val spec = nb?.pageSpec(pid)
            val aspect = if (spec != null) spec.widthMm / spec.heightMm else 0.707f
            val ghostWidth = 130.dp
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (dragPosition.x - listOrigin.x).roundToInt() - ghostWidth.roundToPx() / 2,
                            (dragPosition.y - listOrigin.y).roundToInt() - (ghostWidth / aspect).roundToPx() / 2,
                        )
                    }
                    .width(ghostWidth)
                    .softShadow(10.dp, RoundedCornerShape(MilkRadius.sm))
                    .clip(RoundedCornerShape(MilkRadius.sm))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(MilkRadius.sm)),
            ) {
                thumbs[pid]?.let {
                    Image(
                        it,
                        null,
                        Modifier.fillMaxWidth().aspectRatio(aspect),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }

    menuPageId?.let { pageId ->
        MilkActionPanel(
            title = "页面 $pageId",
            subtitle = "页面操作",
            actions = listOf(
                MilkPanelAction("添加页面", { menuPageId = null; onNewPage(pageId) }, Icons.Outlined.Add),
                MilkPanelAction("复制页面", { menuPageId = null; onDuplicatePage(pageId) }, Icons.Outlined.ContentCopy),
                MilkPanelAction("清除页面", { menuPageId = null; clearTarget = pageId }, Icons.Outlined.Clear, destructive = true),
                MilkPanelAction("删除页面", { menuPageId = null; deleteTarget = pageId }, Icons.Outlined.Delete, destructive = true),
            ),
            onDismiss = { menuPageId = null },
        )
    }
    clearTarget?.let { pageId ->
        ConfirmPagePanel(
            title = "清除页面 $pageId？",
            message = if (pageId == state.currentPageId) {
                "将清除本页内容；清除会写入撤销记录，可在本页撤销恢复。"
            } else {
                "将清除本页内容；清除会写入撤销记录，打开该页后可撤销恢复。图片资产文件保留。"
            },
            confirmLabel = "清除页面",
            onConfirm = { clearTarget = null; onClearPage(pageId) },
            onDismiss = { clearTarget = null },
        )
    }
    deleteTarget?.let { pageId ->
        ConfirmPagePanel(
            title = "删除页面 $pageId？",
            message = "将删除该页的页面文件和内容，图片资产文件保留。此操作无法撤销。" +
                if (state.pages.size <= 1) "删除后会新建一张空白页。" else "",
            confirmLabel = "删除页面",
            onConfirm = { deleteTarget = null; onDeletePage(pageId) },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** 侧栏收起时的紧凑悬浮页码与翻页胶囊，不占用左侧竖条空间。 */
@Composable
internal fun WriterPagePill(
    state: WriterUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(48.dp)
            .milkGlassRim(PillShape, elevation = 8.dp)
            .padding(horizontal = 4.dp)
            .testTag("writer-page-status"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconToggleButton(
            icon = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上一页", Modifier.size(20.dp)) },
            enabled = state.canPreviousPage,
            onClick = onPrevious,
            modifier = Modifier.testTag("writer-previous-page"),
        )
        Text(
            "${state.currentPageIndex + 1}/${state.pages.size}",
            style = MilkText.monoBody,
            modifier = Modifier.padding(horizontal = MilkSpace.xs),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconToggleButton(
            icon = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下一页", Modifier.size(20.dp)) },
            enabled = state.canNextPage,
            onClick = onNext,
            modifier = Modifier.testTag("writer-next-page"),
        )
    }
}

@Composable
internal fun WriterRailSegmentBar(
    activeMode: WriterRailMode,
    onPages: () -> Unit,
    onSnippets: () -> Unit,
    onOutline: () -> Unit,
) {
    val modes = listOf(WriterRailMode.PAGES, WriterRailMode.SNIPPETS, WriterRailMode.OUTLINE)
    MilkSegmented(
        options = modes,
        selected = activeMode,
        label = { mode ->
            when (mode) {
                WriterRailMode.PAGES -> "页面"
                WriterRailMode.SNIPPETS -> "卡片"
                WriterRailMode.OUTLINE -> "大纲"
            }
        },
        onSelect = { mode ->
            when (mode) {
                WriterRailMode.PAGES -> onPages()
                WriterRailMode.SNIPPETS -> onSnippets()
                WriterRailMode.OUTLINE -> onOutline()
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = MilkSpace.sm + 2.dp, vertical = MilkSpace.sm),
        testTagFor = { mode ->
            when (mode) {
                WriterRailMode.PAGES -> "writer-page-grid-mode"
                WriterRailMode.SNIPPETS -> "writer-page-show-snippets"
                WriterRailMode.OUTLINE -> "writer-page-show-outline"
            }
        },
        compact = true,
    )
}

/** 第二代左侧抽屉「大纲」分栏：提取每页伴随 Markdown 标题与批注首行，一键跳页 */
@Composable
internal fun WriterOutlineRail(
    state: WriterUiState,
    entries: List<Pair<String, String>>,
    onSwitch: (String) -> Unit,
    onShowPages: () -> Unit,
    onShowSnippets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .width(WriterExpandedRailWidth)
            .fillMaxHeight()
            .background(cs.surface)
            .edgeHairline(cs.outline.copy(alpha = 0.65f), right = true)
            .testTag("writer-outline-panel"),
    ) {
        WriterRailSegmentBar(
            activeMode = WriterRailMode.OUTLINE,
            onPages = onShowPages,
            onSnippets = onShowSnippets,
            onOutline = {},
        )
        if (entries.isEmpty()) {
            MilkEmptyState(
                title = "暂无大纲",
                description = "在伴随笔记添加标题或批注后显示",
                modifier = Modifier.testTag("writer-outline-empty"),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = MilkSpace.sm, vertical = MilkSpace.xs),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(entries.size, key = { entries[it].first }) { index ->
                    val (pageId, title) = entries[index]
                    val active = pageId == state.currentPageId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MilkRadius.sm))
                            .background(if (active) cs.primary.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onSwitch(pageId) }
                            .defaultMinSize(minHeight = 40.dp)
                            .padding(horizontal = MilkSpace.md, vertical = MilkSpace.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            style = if (active) MilkText.bodyMedium else MilkText.body,
                            color = if (active) cs.primary else cs.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${index + 1}",
                            style = MilkText.monoMeta,
                            color = if (active) cs.primary else cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
