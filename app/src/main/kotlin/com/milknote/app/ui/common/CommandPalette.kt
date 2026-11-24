package com.milknote.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkScrim
import com.milknote.app.ui.theme.MilkText

/** 命令面板条目(⌘K) */
data class CommandItem(
    val id: String,
    val title: String,
    val hint: String = "",      // mono 右侧提示(如 ⌥A / 8 页)
    val section: String = "",
    val icon: ImageVector? = null,
    val action: () -> Unit,
)

/**
 * ⌘K 命令面板(第二代 · DESIGN-V2 dlg-pal):560dp 居中 + 搜索行(图标 + 输入 + 清除 + 关闭)
 * + 44dp 结果行(标题 + mono 提示,↑↓ 选择高亮,↵ 执行);结果区可滚动,不再静默裁切。
 * 未输入时按 [CommandItem.section] 分组显示眉标;挂全屏 scrim 之上,由调用方包 AnimatedVisibility。
 */
@Composable
fun CommandPalette(
    items: List<CommandItem>,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var selected by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val searching = query.text.isNotBlank()
    val filtered = remember(query.text, items) {
        val q = query.text.trim()
        if (q.isEmpty()) items else items.filter { it.title.contains(q, true) || it.section.contains(q, true) }
    }
    LaunchedEffect(filtered.size) { selected = selected.coerceIn(0, (filtered.size - 1).coerceAtLeast(0)) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val cs = MaterialTheme.colorScheme

    Column(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .milkDialogSurface()
            .onKeyEvent { ev ->
                when {
                    ev.key == Key.Escape && ev.type == KeyEventType.KeyUp -> { onDismiss(); true }
                    ev.key == Key.DirectionDown && ev.type == KeyEventType.KeyUp && filtered.isNotEmpty() -> {
                        selected = (selected + 1) % filtered.size; true
                    }
                    ev.key == Key.DirectionUp && ev.type == KeyEventType.KeyUp && filtered.isNotEmpty() -> {
                        selected = (selected - 1 + filtered.size) % filtered.size; true
                    }
                    ev.key == Key.Enter && ev.type == KeyEventType.KeyUp -> {
                        filtered.getOrNull(selected)?.let { it.action(); onDismiss() } ?: onDismiss()
                        true
                    }
                    else -> false
                }
            },
    ) {
        // 搜索行
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.text.isEmpty()) {
                    Text(
                        "搜索笔记、卡片或命令…",
                        style = MilkText.rowTitle.copy(fontWeight = FontWeight.Normal),
                        color = cs.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selected = 0
                    },
                    singleLine = true,
                    textStyle = MilkText.rowTitle.copy(fontWeight = FontWeight.Normal, color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.text.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable {
                            query = TextFieldValue("", TextRange.Zero)
                            selected = 0
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "清除搜索",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(MilkRadius.sm))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = cs.outline)

        // 结果列表(DESIGN-V2 pal-row:44dp,选中 = 强调色淡底 + 强调色字;可滚动)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState())
                .padding(6.dp),
        ) {
            if (filtered.isEmpty()) {
                MilkEmptyState(
                    title = "无匹配结果",
                    description = if (searching) "换一个关键词试试" else "当前没有可用命令",
                    icon = Icons.Outlined.SearchOff,
                )
            } else {
                filtered.take(12).forEachIndexed { index, item ->
                    val previousSection = filtered.getOrNull(index - 1)?.section
                    if (!searching && item.section.isNotBlank() && item.section != previousSection) {
                        Text(
                            item.section,
                            style = MilkText.eyebrow,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 3.dp),
                        )
                    }
                    val sel = index == selected
                    val rowBg by animateColorAsState(
                        if (sel) cs.primary.copy(alpha = 0.09f) else Color.Transparent,
                        MilkMotion.colorSpec(), label = "palRowBg",
                    )
                    val rowFg = if (sel) cs.primary else cs.onSurface
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MilkRadius.sm))
                            .background(rowBg)
                            .clickable {
                                item.action()
                                onDismiss()
                            }
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item.icon?.let {
                            Icon(
                                it,
                                contentDescription = null,
                                tint = rowFg,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            item.title,
                            style = MilkText.body.copy(fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal),
                            color = rowFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.hint.isNotEmpty()) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                item.hint,
                                style = MilkText.monoMeta,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 命令面板专用的轻遮罩(40% 黑),与 MilkScrim 一致 */
val PaletteScrim: Color = MilkScrim
