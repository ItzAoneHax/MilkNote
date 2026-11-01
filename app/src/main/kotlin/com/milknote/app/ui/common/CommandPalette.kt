package com.milknote.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.ui.theme.Mono

/** 命令面板条目(⌘K;Codex 式居中搜索) */
data class CommandItem(
    val id: String,
    val title: String,
    val hint: String = "",      // mono 右侧提示(如 ⌘A / 8 页)
    val section: String = "",
    val action: () -> Unit,
)

/**
 * ⌘K 命令面板(DESIGN-V0 §5.4):居中搜索框 + 分组结果;
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

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 90.dp)
            .shadow(18.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
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
        // 搜索框
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⌘K", fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.text.isEmpty()) {
                    Text(
                        "输入命令或搜索…",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }

        // 分组结果
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            var lastSection = ""
            filtered.take(12).forEach { item ->
                if (item.section != lastSection && item.section.isNotEmpty()) {
                    lastSection = item.section
                    Text(
                        item.section,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            item.action()
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    if (item.hint.isNotEmpty()) {
                        Text(item.hint, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (filtered.isEmpty()) {
                Text(
                    "没有匹配的命令",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}
