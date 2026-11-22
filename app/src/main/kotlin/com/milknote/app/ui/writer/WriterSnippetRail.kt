package com.milknote.app.ui.writer

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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.cards.CardEditorPanel
import com.milknote.app.ui.cards.CardImage
import com.milknote.app.ui.cards.CardsViewModel
import com.milknote.app.ui.cards.cardColor
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.notes.model.COVER_COLORS
import com.milknote.notes.model.NoteCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 摘取侧栏的标签范围：全部 / 来源于当前笔记 / 无标签 / 某个具体标签。 */
private sealed interface SnippetTagFilter {
    data object All : SnippetTagFilter
    data object CurrentNotebook : SnippetTagFilter
    data object Untagged : SnippetTagFilter
    data class Tag(val name: String) : SnippetTagFilter
}

/**
 * 书写页摘取侧栏（P061）：颜色、搜索、标签三级筛选 + 竖向卡片列表。
 *
 * - 扁平 surface 贴在书写区一侧，右缘 hairline 与纸面分界，不做悬浮岛。
 * - 卡片回插当前页走 [onInsert]；编辑 / 删除分别走 [CardsViewModel.save] /
 *   [CardsViewModel.delete]，与卡片页是同一条持久化链路。
 * - 读列表始终读取完整卡片表（不沿用卡片页的筛选状态），筛选条件由本侧栏独立维护。
 */
@Composable
internal fun WriterSnippetRail(
    vm: CardsViewModel,
    currentNotebookId: String?,
    currentPageId: String,
    onInsert: (NoteCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardsState by vm.state.collectAsState()
    // 卡片页或本侧栏写入后 vm.refresh() 会产出新 state；借它触发一次完整表重读，
    // 避免直接复用卡片页的筛选结果而漏掉其它卡片。
    val allCards by produceState(initialValue = emptyList<NoteCard>(), key1 = cardsState) {
        value = withContext(Dispatchers.IO) { ServiceLocator.cardRepository.listCards() }
    }

    var query by remember { mutableStateOf("") }
    var color by remember { mutableStateOf<String?>(null) }
    var tagFilter by remember { mutableStateOf<SnippetTagFilter>(SnippetTagFilter.All) }
    var editing by remember { mutableStateOf<NoteCard?>(null) }
    var deleting by remember { mutableStateOf<NoteCard?>(null) }
    // 千本摘取侧栏(本轮实测 P060):卡片长按后可拖入右侧画布,松手即回插当前页
    var dragCard by remember { mutableStateOf<NoteCard?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var railOrigin by remember { mutableStateOf(Offset.Zero) }
    var railWidthPx by remember { mutableStateOf(0f) }

    val allTags = remember(allCards) {
        allCards.flatMap { it.tags }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sorted()
    }
    val filtered = remember(allCards, query, color, tagFilter, currentNotebookId) {
        val needle = query.trim()
        val colorKey = color
        val activeFilter = tagFilter
        allCards.filter { card ->
            (colorKey == null || card.color.trim().equals(colorKey, ignoreCase = true)) &&
                (needle.isEmpty() ||
                    card.title.contains(needle, ignoreCase = true) ||
                    card.body.contains(needle, ignoreCase = true)) &&
                when (activeFilter) {
                    SnippetTagFilter.All -> true
                    SnippetTagFilter.CurrentNotebook ->
                        currentNotebookId != null && card.sourceNotebookId == currentNotebookId
                    SnippetTagFilter.Untagged -> card.tags.none { it.isNotBlank() }
                    is SnippetTagFilter.Tag ->
                        card.tags.any { it.trim().equals(activeFilter.name.trim(), ignoreCase = true) }
                }
        }
    }

    Box(
        modifier
            .onGloballyPositioned {
                railOrigin = it.positionInRoot()
                railWidthPx = it.size.width.toFloat()
            }
            .testTag("writer-snippet-rail")
            .semantics { contentDescription = "摘取侧栏，当前页 $currentPageId" },
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(310.dp)
                .background(MaterialTheme.colorScheme.surface)
                .edgeHairline(MaterialTheme.colorScheme.outline.copy(alpha = 0.75f), right = true),
        ) {
        // P061：颜色与搜索共用顶行；颜色带独立横滑，搜索保持可见。
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f).testTag("writer-snippet-colors"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item(key = "__all__") {
                    ColorFilterDot(
                        selected = color == null,
                        label = "全部颜色",
                        brush = rainbowBrush,
                        onClick = { color = null },
                        testTag = "writer-snippet-color-all",
                    )
                }
                items(COVER_COLORS, key = { it }) { hex ->
                    ColorFilterDot(
                        selected = color.equals(hex, ignoreCase = true),
                        label = "颜色 $hex",
                        brush = SolidColor(cardColor(hex)),
                        onClick = { color = if (color.equals(hex, ignoreCase = true)) null else hex },
                        testTag = "writer-snippet-color-$hex",
                    )
                }
            }
            Row(
                Modifier
                    .width(112.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 10.dp)
                    .testTag("writer-snippet-search"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(5.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "搜索摘取卡片" },
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("搜索", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    },
                )
            }
        }

        // ---- 标签范围 ----
        LazyRow(
            modifier = Modifier.fillMaxWidth().testTag("writer-snippet-tags"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item(key = "__all__") {
                TagFilterChip("全部", tagFilter == SnippetTagFilter.All) {
                    tagFilter = SnippetTagFilter.All
                }
            }
            item(key = "__current__") {
                TagFilterChip("当前笔记", tagFilter == SnippetTagFilter.CurrentNotebook) {
                    tagFilter = SnippetTagFilter.CurrentNotebook
                }
            }
            item(key = "__untagged__") {
                TagFilterChip("无标签", tagFilter == SnippetTagFilter.Untagged) {
                    tagFilter = SnippetTagFilter.Untagged
                }
            }
            items(allTags, key = { it }) { tag ->
                TagFilterChip(tag, tagFilter == SnippetTagFilter.Tag(tag)) {
                    tagFilter = SnippetTagFilter.Tag(tag)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        )

        if (filtered.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(24.dp).testTag("writer-snippet-empty"),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("没有匹配的摘取卡片", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (allCards.isEmpty()) {
                            "在书写页框选内容并保存为卡片后，会出现在这里。"
                        } else {
                            "换个颜色、标签或搜索词试试。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (allCards.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        MilkButton(
                            label = "清除筛选",
                            onClick = {
                                query = ""
                                color = null
                                tagFilter = SnippetTagFilter.All
                            },
                            variant = MilkButtonVariant.SECONDARY,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("writer-snippet-list"),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.id }) { card ->
                    val cardOrigin = remember(card.id) { mutableStateOf(Offset.Zero) }
                    val dragAmount = remember(card.id) { mutableStateOf(Offset.Zero) }
                    Box(
                        Modifier
                            .onGloballyPositioned { cardOrigin.value = it.positionInRoot() }
                            .pointerInput(card.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { pos ->
                                        dragAmount.value = Offset.Zero
                                        dragCard = card
                                        dragPos = cardOrigin.value + pos
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragAmount.value += amount
                                        dragPos = cardOrigin.value + dragAmount.value
                                    },
                                    onDragEnd = {
                                        val target = dragCard
                                        if (target != null && dragPos.x - railOrigin.x > railWidthPx) {
                                            onInsert(target)
                                        }
                                        dragCard = null
                                    },
                                    onDragCancel = { dragCard = null },
                                )
                            },
                    ) {
                        SnippetCard(
                            card = card,
                            dimmed = dragCard?.id == card.id,
                            onInsert = { onInsert(card) },
                            onEdit = { editing = card },
                            onDelete = { deleting = card },
                        )
                    }
                }
            }
        }
        }

        // 拖拽影子:小卡片片名跟随手指,越过侧栏右缘即视为拖入画布
        dragCard?.let { card ->
            Box(
                Modifier
                    .offset { IntOffset((dragPos - railOrigin).x.roundToInt() - 60.dp.roundToPx() / 2, (dragPos - railOrigin).y.roundToInt() - 18.dp.roundToPx()) }
                    .width(120.dp)
                    .shadow(6.dp, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    card.title.ifBlank { "无标题卡片" },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    editing?.let { card ->
        CardEditorPanel(
            existing = card,
            onDismiss = { editing = null },
            onSave = { title, body, tags, savedColor ->
                vm.save(card, title, body, tags, savedColor)
                editing = null
            },
        )
    }

    deleting?.let { card ->
        MilkCenterPanel(title = "删除卡片", onDismiss = { deleting = null }, maxWidth = 440.dp) {
            Spacer(Modifier.height(16.dp))
            Text("删除「${card.title.ifBlank { "无标题卡片" }}」？此操作不可撤销。")
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MilkButton("取消", onClick = { deleting = null }, variant = MilkButtonVariant.SECONDARY)
                Spacer(Modifier.width(8.dp))
                MilkButton(
                    "删除",
                    onClick = {
                        vm.delete(card.id)
                        deleting = null
                    },
                )
            }
        }
    }
}

/** 颜色筛选圆点：48dp 命中，选中时用 accent 描边（P061 顶部颜色条）。 */
@Composable
private fun ColorFilterDot(
    selected: Boolean,
    label: String,
    brush: Brush,
    onClick: () -> Unit,
    testTag: String,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape,
            )
            .testTag(testTag)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(brush)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
        )
    }
}

/** 标签筛选胶囊：48dp 命中，选中态使用 accent 淡底 + 描边。 */
@Composable
private fun TagFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface,
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                CircleShape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** 摘取卡片：颜色标题条 + 图片 / 正文 + 回插、编辑、删除动作（每个动作 48dp 命中）。 */
@Composable
private fun SnippetCard(
    card: NoteCard,
    onInsert: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dimmed: Boolean = false,
) {
    val accent = remember(card.color) { cardColor(card.color) }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (dimmed) 0.35f else 1f }
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .testTag("writer-snippet-card-${card.id}"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.18f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                card.title.ifBlank { "无标题卡片" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Column(Modifier.padding(12.dp)) {
            card.imageSrc?.let { CardImage(it) }
            if (card.body.isNotBlank() || card.imageSrc != null) {
                Text(
                    card.body.ifBlank { "图片摘取" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    card.tags.joinToString(" · ") { "#$it" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SnippetCardAction(
                    icon = Icons.Outlined.Add,
                    description = "回插到当前页",
                    testTag = "writer-snippet-insert-${card.id}",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onInsert,
                )
                SnippetCardAction(
                    icon = Icons.Outlined.Edit,
                    description = "编辑卡片",
                    testTag = "writer-snippet-edit-${card.id}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onEdit,
                )
                SnippetCardAction(
                    icon = Icons.Outlined.Delete,
                    description = "删除卡片",
                    testTag = "writer-snippet-delete-${card.id}",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun SnippetCardAction(
    icon: ImageVector,
    description: String,
    testTag: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp), tint = tint)
    }
}

private val rainbowBrush: Brush = Brush.sweepGradient(
    listOf(
        Color(0xFFD2483C),
        Color(0xFFD98E04),
        Color(0xFF2E9E6B),
        Color(0xFF2C8CC8),
        Color(0xFF3B5BEC),
        Color(0xFF7C5CE0),
        Color(0xFFD2483C),
    ),
)
