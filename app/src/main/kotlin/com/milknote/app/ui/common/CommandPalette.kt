package com.milknote.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.ui.theme.Mono
import com.milknote.app.ui.theme.MilkScrim

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
 * ⌘K 命令面板(第二代 · DESIGN-V2 dlg-pal):560dp 居中 + 搜索行(图标 + 输入 + 关闭)
 * + 44dp 结果行(标题 + mono 提示,↑↓ 选择高亮,↵ 执行);挂全屏 scrim 之上,由调用方包 AnimatedVisibility。
 */
@Composable
fun CommandPalette(
    items: List<CommandItem>,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var selected by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val filtered = remember(query.text) {
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
                    Text("搜索笔记、卡片或命令…", fontSize = 14.5.sp, color = cs.onSurfaceVariant.copy(alpha = 0.6f))
                }
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selected = 0
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.5.sp, color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        androidx.compose.material3.HorizontalDivider(color = cs.outline)

        // 结果列表(DESIGN-V2 pal-row:44dp,选中 = 强调色淡底 + 强调色字)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .padding(6.dp),
        ) {
            filtered.take(12).forEachIndexed { index, item ->
                val sel = index == selected
                val bg by animateColorAsState(if (sel) cs.primary.copy(alpha = 0.09f) else Color.Transparent, tween(120), label = "palRowBg")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(bg)
                        .clickable {
                            item.action()
                            onDismiss()
                        }
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.title,
                        fontSize = 13.5.sp,
                        fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.Medium else null,
                        color = if (sel) cs.primary else cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.hint.isNotEmpty()) {
                        Spacer(Modifier.width(12.dp))
                        Text(item.hint, fontFamily = Mono, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            if (filtered.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Outlined.SearchOff,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("无匹配结果", fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** 命令面板专用的轻遮罩(40% 黑),与 MilkScrim 一致 */
val PaletteScrim: Color = MilkScrim
