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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.LibraryBottomNav
import com.milknote.app.ui.common.MilkActionPanel
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkPanelAction
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
        Row(
            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("卡片", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(16.dp))
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                BasicTextField(
                    value = state.query,
                    onValueChange = vm::search,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (state.query.isEmpty()) Text("搜索卡片标题和内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(16.dp))
            MilkButton(label = "新建卡片", onClick = { creating = true })
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip("全部标签", state.tag == CardTagFilter.All) { vm.filterTag(CardTagFilter.All) }
            FilterChip("无标签", state.tag == CardTagFilter.Untagged) { vm.filterTag(CardTagFilter.Untagged) }
            state.allTags.forEach { tag ->
                FilterChip(tag, state.tag == CardTagFilter.Tag(tag)) { vm.filterTag(CardTagFilter.Tag(tag)) }
            }
            Spacer(Modifier.width(12.dp))
            COVER_COLORS.forEach { hex ->
                val selected = state.color.equals(hex, ignoreCase = true)
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable {
                        vm.filterColor(if (selected) null else hex)
                    }.border(
                        if (selected) 3.dp else 0.dp,
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(hex))))
                }
            }
        }
        if (state.cards.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("还没有匹配的卡片", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("新建一张，或在书写页把摘取保存为卡片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                MilkButton(label = "新建卡片", onClick = { creating = true })
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(280.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(28.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(state.cards, key = { it.id }) { card ->
                    Column(
                        Modifier.softCard().clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { editing = card }.padding(18.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(cardColor(card.color)))
                            Spacer(Modifier.width(8.dp))
                            Text(card.title.ifBlank { "无标题卡片" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Box(Modifier.size(48.dp).clickable { inserting = card }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Add, "插入到笔记", modifier = Modifier.size(18.dp))
                            }
                            Box(Modifier.size(48.dp).clickable { deleting = card }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Delete, "删除卡片", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        card.imageSrc?.let { CardImage(it) }
                        Text(card.body.ifBlank { "图片摘取" }, maxLines = 6, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (card.tags.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(card.tags.joinToString(" · ") { "#$it" }, color = MaterialTheme.colorScheme.primary)
                        }
                        if (card.sourceNotebookId != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("来源：${card.sourceNotebookId} / ${card.sourcePageId ?: "页面"}", style = MaterialTheme.typography.labelSmall)
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
        MilkCenterPanel(title = "删除卡片", onDismiss = { deleting = null }, maxWidth = 440.dp) {
            Spacer(Modifier.height(16.dp))
            Text("删除「${card.title.ifBlank { "无标题卡片" }}」？此操作不可撤销。")
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MilkButton("取消", onClick = { deleting = null }, variant = MilkButtonVariant.SECONDARY)
                Spacer(Modifier.width(8.dp))
                MilkButton("删除", onClick = { vm.delete(card.id); deleting = null })
            }
        }
    }
    inserting?.let { card ->
        InsertCardPanel(card = card, onDismiss = { inserting = null }, onInsert = { nb, page ->
            inserting = null; onInsert(card, nb, page)
        })
    }
}

@Composable
private fun CardEditorPanel(
    existing: NoteCard?,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>, String) -> Unit,
) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var body by remember(existing?.id) { mutableStateOf(existing?.body.orEmpty()) }
    var tags by remember(existing?.id) { mutableStateOf(existing?.tags?.joinToString("，").orEmpty()) }
    var color by remember(existing?.id) { mutableStateOf(existing?.color ?: COVER_COLORS.first()) }
    MilkCenterPanel(title = if (existing == null) "新建卡片" else "编辑卡片", onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            CardInput(title, { title = it }, "标题", true)
            Spacer(Modifier.height(10.dp))
            CardInput(body, { body = it }, "正文", false, 150.dp)
            Spacer(Modifier.height(10.dp))
            CardInput(tags, { tags = it }, "标签（用逗号分隔）", true)
            Spacer(Modifier.height(12.dp))
            Row {
                COVER_COLORS.forEach { hex ->
                    Box(Modifier.size(48.dp).clickable { color = hex }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(cardColor(hex)).border(
                            if (color == hex) 3.dp else 1.dp,
                            if (color == hex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        ))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            MilkButton("取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(8.dp))
            MilkButton("保存", enabled = title.isNotBlank() || body.isNotBlank() || existing?.imageSrc != null, onClick = {
                onSave(title, body, tags.split(',', '，'), color)
            })
        }
    }
}

@Composable
private fun CardInput(value: String, onChange: (String) -> Unit, hint: String, singleLine: Boolean, height: androidx.compose.ui.unit.Dp? = null) {
    Box(
        Modifier.fillMaxWidth().let { if (height != null) it.height(height) else it.defaultMinSize(minHeight = 48.dp) }
            .clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        BasicTextField(value, onChange, singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() })
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
    MilkCenterPanel(title = "插入到笔记", subtitle = card.title.ifBlank { "无标题卡片" }, onDismiss = onDismiss) {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            Text("笔记本", Modifier.padding(vertical = 10.dp))
            notebooks.forEach { nb ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { notebookId = nb.id }
                    .defaultMinSize(minHeight = 48.dp).padding(12.dp)) {
                    Text(if (nb.id == notebookId) "✓ ${nb.name}" else nb.name)
                }
            }
            Text("页面", Modifier.padding(vertical = 10.dp))
            pages.forEachIndexed { index, page ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { pageId = page.pageId }
                    .defaultMinSize(minHeight = 48.dp).padding(12.dp)) {
                    Text(if (page.pageId == pageId) "✓ 第 ${index + 1} 页" else "第 ${index + 1} 页")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            MilkButton("取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(8.dp))
            MilkButton("插入", enabled = notebookId != null && pageId != null, onClick = { onInsert(notebookId!!, pageId!!) })
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick).defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp))
}

@Composable
private fun CardImage(relative: String) {
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
                .clip(RoundedCornerShape(12.dp)).padding(bottom = 10.dp),
        )
    }
}

private fun cardColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF3B5BEC))
