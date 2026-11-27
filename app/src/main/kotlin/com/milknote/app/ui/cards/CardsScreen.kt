package com.milknote.app.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.LibraryBottomNav
import com.milknote.app.ui.common.MilkIconBadge
import com.milknote.app.ui.common.MilkIcons
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkColorDot
import com.milknote.app.ui.common.MilkConfirmDialog
import com.milknote.app.ui.common.MilkEmptyState
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkRadioRow
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkStatusBadge
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.MonoText
import com.milknote.app.ui.common.coverColorName
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.PillShape
import com.milknote.notes.model.COVER_COLORS
import com.milknote.notes.model.NoteCard
import com.milknote.notes.storage.CardTagFilter

@Composable
fun CardsScreen(
    vm: CardsViewModel,
    onNotes: () -> Unit,
    onInsert: (NoteCard, String, String) -> Unit,
) {
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<NoteCard?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<NoteCard?>(null) }
    var inserting by remember { mutableStateOf<NoteCard?>(null) }
    val filtered = state.query.isNotBlank() || state.tag != CardTagFilter.All || state.color != null

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
    ) {
        // ---- 顶栏:标题 + 计数 + 搜索 + 新建 ----
        Row(
            Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("卡片", style = MilkText.pageTitle, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.width(10.dp))
                    MilkStatusBadge("${state.cards.size} 张", mono = true)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "摘取即保存 · 可反复插入任意笔记",
                    style = MilkText.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(24.dp))
            MilkTextField(
                value = state.query,
                onValueChange = vm::search,
                placeholder = "搜索卡片标题和内容",
                modifier = Modifier.weight(1f).widthIn(max = 560.dp),
                mono = false,
                onClear = { vm.search("") },
                leading = {
                    Icon(
                        MilkIcons.Search, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            Spacer(Modifier.width(16.dp))
            MilkButton(
                label = "新建卡片",
                onClick = { creating = true },
                leading = {
                    Icon(
                        MilkIcons.Add, null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        // ---- 筛选:标签芯片 + 色点 ----
        CardFilterRow(
            state = state,
            onTag = vm::filterTag,
            onColor = { hex -> vm.filterColor(if (state.color.equals(hex, ignoreCase = true)) null else hex) },
            modifier = Modifier.testTag("cards-filters").padding(horizontal = 28.dp),
        )
        if (state.cards.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (filtered) {
                    MilkEmptyState(
                        title = "没有匹配的卡片",
                        description = "换一个关键词，或清除标签与颜色筛选",
                        icon = MilkIcons.Search,
                        actionLabel = "清除筛选",
                        onAction = {
                            vm.search("")
                            vm.filterTag(CardTagFilter.All)
                            vm.filterColor(null)
                        },
                    )
                } else {
                    MilkEmptyState(
                        title = "暂无卡片",
                        description = "新建一张，或在书写页把摘取保存为卡片",
                        icon = MilkIcons.Cards,
                        actionLabel = "新建卡片",
                        onAction = { creating = true },
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(280.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("cards-grid"),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(state.cards, key = { it.id }) { card ->
                    MilkNoteCard(
                        card = card,
                        onClick = { editing = card },
                        onInsert = { inserting = card },
                        onDelete = { deleting = card },
                        sourceLabel = state.sourceLabels[card.id],
                    )
                }
            }
        }
        LibraryBottomNav(selected = "卡片", onNotes = onNotes, onCards = {})
    }

    if (creating) {
        CardEditorPanel(existing = null, onDismiss = { creating = false }, onSave = { title, body, tags, color ->
            vm.save(null, title, body, tags, color); creating = false
        })
    }
    editing?.let { card ->
        CardEditorPanel(existing = card, onDismiss = { editing = null }, onSave = { title, body, tags, color ->
            vm.save(card, title, body, tags, color); editing = null
        })
    }
    deleting?.let { card ->
        MilkConfirmDialog(
            title = "删除「${card.title.ifBlank { "无标题卡片" }}」？",
            body = "卡片会从卡片库移除，已经插入到笔记里的副本不受影响。此操作不可撤销。",
            confirmLabel = "删除",
            onDismiss = { deleting = null },
            onConfirm = { vm.delete(card.id); deleting = null },
        )
    }
    inserting?.let { card ->
        InsertCardPanel(card = card, onDismiss = { inserting = null }, onInsert = { nb, page ->
            inserting = null; onInsert(card, nb, page)
        })
    }
}

/**
 * 标签芯片 + 色点筛选行(CardsScreen 与书架卡片页共用):
 * 外层横向滚动,窄屏不换行溢出;色点用于跨笔记本按颜色收拢。
 */
@Composable
internal fun CardFilterRow(
    state: CardsUiState,
    onTag: (CardTagFilter) -> Unit,
    onColor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MilkChip("全部标签", state.tag == CardTagFilter.All, onClick = { onTag(CardTagFilter.All) }, small = true)
        MilkChip("无标签", state.tag == CardTagFilter.Untagged, onClick = { onTag(CardTagFilter.Untagged) }, small = true)
        state.allTags.forEach { tag ->
            MilkChip(tag, state.tag == CardTagFilter.Tag(tag), onClick = { onTag(CardTagFilter.Tag(tag)) }, small = true)
        }
        Spacer(Modifier.width(MilkSpace.sm))
        COVER_COLORS.forEach { hex ->
            val selected = state.color.equals(hex, ignoreCase = true)
            MilkColorDot(
                color = cardColor(hex),
                selected = selected,
                dotSize = 20.dp,
                onClick = { onColor(hex) },
                contentDescription = "按颜色筛选 ${coverColorName(hex)}",
            )
        }
    }
}

/**
 * 图书卡片(CardsScreen 与书架卡片页共用的唯一卡片视觉):
 * 12dp 圆角 + 单条 1dp hairline + surface 底;标题前一枚 8dp 主题色圆点,
 * 摘要只取首段,标签与来源克制地落在页脚。右上角操作保持 48dp 舒适命中。
 */
@Composable
internal fun MilkNoteCard(
    card: NoteCard,
    onClick: () -> Unit,
    onInsert: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    sourceLabel: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(MilkRadius.lg)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surface)
            .border(1.dp, cs.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = MilkSpace.lg, end = MilkSpace.xs, top = MilkSpace.md, bottom = MilkSpace.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(cardColor(card.color)))
            Spacer(Modifier.width(10.dp))
            Text(
                card.title.ifBlank { "无标题卡片" },
                style = MilkText.cardTitle,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MilkIconButton(
                imageVector = MilkIcons.Add,
                contentDescription = "插入到笔记",
                onClick = onInsert,
                tint = cs.primary,
                iconSize = 18.dp,
                visualSize = 32.dp,
            )
            MilkIconButton(
                imageVector = MilkIcons.Trash,
                contentDescription = "删除卡片",
                onClick = onDelete,
                iconSize = 17.dp,
                visualSize = 32.dp,
            )
        }
        Column(Modifier.padding(start = 18.dp, top = MilkSpace.sm, end = MilkSpace.md)) {
            card.imageSrc?.let { CardImage(it) }
            val preview = cardPreviewText(card.title, card.body).ifBlank {
                if (card.imageSrc != null) "图片摘取" else ""
            }
            if (preview.isNotBlank()) {
                Text(
                    preview,
                    style = MilkText.body,
                    color = cs.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.tags.isNotEmpty() || !sourceLabel.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
                    ) {
                        card.tags.take(3).forEach { tag ->
                            Text(
                                "#$tag",
                                style = MilkText.micro,
                                color = cs.primary,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(PillShape)
                                    .background(cs.primary.copy(alpha = 0.10f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (!sourceLabel.isNullOrBlank()) MonoText(sourceLabel)
                }
            }
        }
    }
}

@Composable
internal fun CardEditorPanel(
    existing: NoteCard?,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>, String) -> Unit,
) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var body by remember(existing?.id) { mutableStateOf(existing?.body.orEmpty()) }
    var tags by remember(existing?.id) { mutableStateOf(existing?.tags?.joinToString("，").orEmpty()) }
    var color by remember(existing?.id) { mutableStateOf(existing?.color ?: COVER_COLORS.first()) }
    MilkCenterPanel(
        title = if (existing == null) "新建卡片" else "编辑卡片",
        subtitle = if (existing == null) "卡片可反复插入任意笔记" else "修改只影响卡片库中的这一张",
        onDismiss = onDismiss,
        maxWidth = 540.dp,
        footer = {
            Spacer(Modifier.weight(1f))
            MilkButton("取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(MilkSpace.sm))
            MilkButton(
                label = "保存",
                enabled = title.isNotBlank() || body.isNotBlank() || existing?.imageSrc != null,
                onClick = {
                    onSave(title, body, tags.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }, color)
                },
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(MilkSpace.md))
            MilkTextField(title, { title = it }, placeholder = "标题", label = "标题", mono = false, imeAction = androidx.compose.ui.text.input.ImeAction.Next)
            Spacer(Modifier.height(14.dp))
            MilkTextField(body, { body = it }, placeholder = "正文", label = "正文", mono = false, singleLine = false, minLines = 4)
            Spacer(Modifier.height(14.dp))
            MilkTextField(tags, { tags = it }, placeholder = "用逗号分隔", label = "标签", mono = false)
            Spacer(Modifier.height(18.dp))
            MilkSectionLabel("颜色")
            Spacer(Modifier.height(MilkSpace.xs))
            // 色点命中框 48dp、视觉圆点 16dp:整体左移 16dp,让首枚色点左缘与上方标题齐平
            Row(Modifier.offset(x = (-16).dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                COVER_COLORS.forEach { hex ->
                    MilkColorDot(
                        color = cardColor(hex),
                        selected = color == hex,
                        onClick = { color = hex },
                        contentDescription = "卡片颜色 ${coverColorName(hex)}",
                    )
                }
            }
            Spacer(Modifier.height(MilkSpace.md))
        }
    }
}

@Composable
internal fun InsertCardPanel(card: NoteCard, onDismiss: () -> Unit, onInsert: (String, String) -> Unit) {
    val notebooks by androidx.compose.runtime.produceState(emptyList<com.milknote.notes.model.NotebookMeta>()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ServiceLocator.repository.listNotebooks() }
    }
    var notebookId by remember { mutableStateOf(card.sourceNotebookId) }
    androidx.compose.runtime.LaunchedEffect(notebooks) {
        if (notebooks.none { it.id == notebookId }) notebookId = notebooks.firstOrNull()?.id
    }
    val pages by androidx.compose.runtime.produceState(emptyList<com.milknote.notes.model.NotebookPage>(), notebookId) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            notebookId?.let { ServiceLocator.repository.listPages(it) }.orEmpty()
        }
    }
    var pageId by remember(notebookId) { mutableStateOf(card.sourcePageId) }
    androidx.compose.runtime.LaunchedEffect(pages, notebookId) {
        if (pages.none { it.pageId == pageId }) pageId = pages.firstOrNull()?.pageId
    }
    MilkCenterPanel(
        title = "插入到笔记",
        subtitle = card.title.ifBlank { "无标题卡片" },
        onDismiss = onDismiss,
        maxWidth = 480.dp,
        icon = {
            MilkIconBadge(
                icon = MilkIcons.Cards,
                boxSize = 34.dp,
                iconSize = 18.dp,
                tint = cardColor(card.color),
                containerColor = cardColor(card.color).copy(alpha = 0.14f),
            )
        },
        footer = {
            Spacer(Modifier.weight(1f))
            MilkButton("取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(MilkSpace.sm))
            MilkButton("插入", enabled = notebookId != null && pageId != null, onClick = { onInsert(notebookId!!, pageId!!) })
        },
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(MilkSpace.md))
            MilkSectionLabel("笔记本")
            Spacer(Modifier.height(MilkSpace.xs))
            notebooks.forEach { nb ->
                MilkRadioRow(selected = nb.id == notebookId, label = nb.name, onClick = { notebookId = nb.id })
            }
            Spacer(Modifier.height(18.dp))
            MilkSectionLabel("页面")
            Spacer(Modifier.height(MilkSpace.xs))
            if (pages.isEmpty()) {
                Text(
                    "该笔记本还没有页面",
                    style = MilkText.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
            pages.forEachIndexed { index, page ->
                MilkRadioRow(selected = page.pageId == pageId, label = "第 ${index + 1} 页", onClick = { pageId = page.pageId })
            }
            Spacer(Modifier.height(MilkSpace.md))
        }
    }
}

@Composable
internal fun CardImage(relative: String) {
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, relative) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val root = java.io.File(ServiceLocator.appContext.filesDir, "cards")
                val file = java.io.File(root, relative)
                if (!file.canonicalPath.startsWith(root.canonicalPath + java.io.File.separator)) null
                else android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let {
        androidx.compose.foundation.Image(
            bitmap = it, contentDescription = "摘取图片", modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                .clip(MaterialTheme.shapes.small).padding(bottom = 10.dp),
        )
    }
}

internal fun cardColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF3B5BEC))
