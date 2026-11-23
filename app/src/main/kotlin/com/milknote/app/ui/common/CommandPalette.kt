package com.milknote.app.ui.common

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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import com.milknote.app.ui.theme.MilkEyebrow
import com.milknote.app.ui.theme.MonoMeta

/** 命令面板条目(⌘K;Codex 式居中搜索) */
data class CommandItem(
    val id: String,
    val title: String,
    val hint: String = "",      // mono 右侧提示(如 ⌘A / 8 页;≤4 字符时按键帽样式渲染)
    val section: String = "",
    val icon: ImageVector? = null,
    val action: () -> Unit,
)

/**
 * ⌘K 命令面板(2026-11 重设计):560dp 居中 + 搜索行(Esc 键帽)+ 眉标分组 + 52dp 结果行
 * (36dp 图标容器 + 标题 + 键帽/mono 提示)+ 底部键盘说明条。
 * ↵ 执行第一条,Esc 关闭。挂全屏 scrim 之上,由调用方包 AnimatedVisibility。
 */
@Composable
fun CommandPalette(
    items: List<CommandItem>,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    val filtered = remember(query.text) {
        val q = query.text.trim()
        if (q.isEmpty()) items else items.filter { it.title.contains(q, true) || it.section.contains(q, true) }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val cs = MaterialTheme.colorScheme

    Column(
        Modifier
            .widthIn(max = 560.dp)   // A-25:先限宽再 fill(顺序反了会通栏)
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .milkDialogSurface()
            .onKeyEvent { ev ->
                when {
                    ev.key == Key.Escape && ev.type == KeyEventType.KeyUp -> { onDismiss(); true }
                    ev.key == Key.Enter && ev.type == KeyEventType.KeyUp -> {
                        filtered.firstOrNull()?.let { it.action(); onDismiss() } ?: onDismiss()
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
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.text.isEmpty()) {
                    Text(
                        "搜索或输入命令…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(cs.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Spacer(Modifier.width(12.dp))
            MilkKeyCap("Esc")
        }
        HorizontalDivider(color = cs.outline.copy(alpha = 0.7f))

        // 分组结果
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
        ) {
            var lastSection = ""
            filtered.take(12).forEach { item ->
                if (item.section != lastSection && item.section.isNotEmpty()) {
                    lastSection = item.section
                    Text(
                        item.section,
                        style = MilkEyebrow,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            item.action()
                            onDismiss()
                        }
                        .heightIn(min = 52.dp)   // A-25:命令行 ≥52dp,整行点按
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(cs.surfaceVariant.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        item.icon?.let {
                            Icon(it, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.hint.isNotEmpty()) {
                        if (item.hint.length <= 4) {
                            MilkKeyCap(item.hint)
                        } else {
                            Text(item.hint, style = MonoMeta, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Outlined.SearchOff,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("没有匹配的命令", style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text("换个关键词，或清空后浏览全部", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
        // 底部键盘说明
        HorizontalDivider(color = cs.outline.copy(alpha = 0.7f))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MilkKeyCap("↵")
            Text("执行", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            MilkKeyCap("Esc")
            Text("关闭", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("${filtered.size.coerceAtMost(12)} 条", style = MonoMeta.copy(fontSize = 11.sp), color = cs.onSurfaceVariant)
        }
    }
}
