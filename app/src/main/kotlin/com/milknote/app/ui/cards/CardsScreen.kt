package com.milknote.app.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Style
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.LibraryBottomNav
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkColorDot
import com.milknote.app.ui.common.MilkConfirmDialog
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkPanelHeader
import com.milknote.app.ui.common.MilkRadioRow
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.softCard
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

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
    ) {
        // ---- 顶栏:标题 + 搜索(≤560dp,与书架顶栏同高 76dp)+ 新建 ----
        Row(
            Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("卡片", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${state.cards.size} 张",
                        style = com.milknote.app.ui.theme.MonoMeta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "摘取即保存 · 可反复插入任意笔记",
                    style = MaterialTheme.typography.labelMedium,
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
                leading = {
                    Icon(
                        Icons.Outlined.Search, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            Spacer(Modifier.width(16.dp))
            MilkButton(
                label = "新建卡片",
                onClick = { creating = true },
                leading = { Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
            )
        }
        // ---- 筛选:标签芯片 + 色点 ----
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("cards-filters").padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MilkChip("全部标签", state.tag == CardTagFilter.All, onClick = { vm.filterTag(CardTagFilter.All) }, small = true)
            MilkChip("无标签", state.tag == CardTagFilter.Untagged, onClick = { vm.filterTag(CardTagFilter.Untagged) }, small = true)
            state.allTags.forEach { tag ->
                MilkChip(tag, state.tag == CardTagFilter.Tag(tag), onClick = { vm.filterTag(CardTagFilter.Tag(tag)) }, small = true)
            }
            Spacer(Modifier.width(8.dp))
            COVER_COLORS.forEach { hex ->
                val selected = state.color.equals(hex, ignoreCase = true)
                MilkColorDot(
                    color = cardColor(hex),
                    selected = selected,
                    dotSize = 22.dp,
                    onClick = { vm.filterColor(if (selected) null else hex) },
                    contentDescription = "按颜色筛选",
                )
            }
        }
        if (state.cards.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Style, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("还没有匹配的卡片", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "新建一张，或在书写页把摘取保存为卡片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                MilkButton(label = "新建卡片", onClick = { creating = true })
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(280.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("cards-grid"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(state.cards, key = { it.id }) { card ->
                    Column(
                        Modifier.softCard().clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                            .clickable { editing = card }.padding(18.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(cardColor(card.color)))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                card.title.ifBlank { "无标题卡片" },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            MilkIconButton(Icons.Outlined.Add, "插入到笔记", onClick = { inserting = card }, iconSize = 20.dp)
                            MilkIconButton(Icons.Outlined.Delete, "删除卡片", onClick = { deleting = card }, iconSize = 20.dp, tint = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(8.dp))
                        card.imageSrc?.let { CardImage(it) }
                        Text(
                            card.body.ifBlank { "图片摘取" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (card.tags.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                card.tags.take(4).forEach { tag ->
                                    Text(
                                        "#$tag",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                        if (card.sourceNotebookId != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "来源 ${card.sourceNotebookId} · ${card.sourcePageId ?: "页面"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            MilkTextField(title, { title = it }, placeholder = "标题", label = "标题", mono = false, imeAction = androidx.compose.ui.text.input.ImeAction.Next)
            Spacer(Modifier.height(14.dp))
            MilkTextField(body, { body = it }, placeholder = "正文", label = "正文", mono = false, singleLine = false, minLines = 4)
            Spacer(Modifier.height(14.dp))
            MilkTextField(tags, { tags = it }, placeholder = "用逗号分隔", label = "标签", mono = false)
            Spacer(Modifier.height(18.dp))
            MilkSectionLabel("颜色")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                COVER_COLORS.forEach { hex ->
                    MilkColorDot(
                        color = cardColor(hex),
                        selected = color == hex,
                        onClick = { color = hex },
                        contentDescription = "卡片颜色",
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            MilkButton("取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(10.dp))
            MilkButton("保存", enabled = title.isNotBlank() || body.isNotBlank() || existing?.imageSrc != null, onClick = {
                onSave(title, body, tags.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }, color)
            })
        }
    }
}

@Composable
private fun InsertCardPanel(card: NoteCard, onDismiss: () -> Unit, onInsert: (String, String) -> Unit) {
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
            Box(
                Modifier
                    .size(44.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(cardColor(card.color).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Style, null, tint = cardColor(card.color), modifier = Modifier.size(22.dp))
            }
        },
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            MilkSectionLabel("笔记本")
            Spacer(Modifier.height(6.dp))
            notebooks.forEach { nb ->
                MilkRadioRow(selected = nb.id == notebookId, label = nb.name, onClick = { notebookId = nb.id })
            }
            Spacer(Modifier.height(18.dp))
            MilkSectionLabel("页面")
            Spacer(Modifier.height(6.dp))
            if (pages.isEmpty()) {
                Text("该笔记本还没有页面", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
            }
            pages.forEachIndexed { index, page ->
                MilkRadioRow(selected = page.pageId == pageId, label = "第 ${index + 1} 页", onClick = { pageId = page.pageId })
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            MilkButton("取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(10.dp))
            MilkButton("插入", enabled = notebookId != null && pageId != null, onClick = { onInsert(notebookId!!, pageId!!) })
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
