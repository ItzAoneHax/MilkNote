package com.milknote.app.ui.bookshelf

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.milknote.app.ui.common.accentBrush
import com.milknote.app.ui.common.innerHighlight
import com.milknote.app.ui.common.paintedShadow
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.CoverPalette
import com.milknote.notes.model.PAGE_ORIENTATIONS
import com.milknote.notes.model.PAGE_SIZES_MM
import com.milknote.notes.model.PAPER_KINDS
import com.milknote.notes.model.PURPOSE_TAGS

/** ② 新建笔记本弹窗:名称 / 封面色 6 / 纸张尺寸·方向 / 纸张模板 / 用途标签;「创建 ↵」直接进入第 1 页 */
@Composable
fun NewNotebookDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, coverHex: String, paper: String, purpose: String, pageSize: String, orientation: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var coverIdx by rememberSaveable { mutableStateOf(0) }
    var paper by rememberSaveable { mutableStateOf("dot") }
    var pageSize by rememberSaveable { mutableStateOf("B5") }        // 默认 B5
    var orientation by rememberSaveable { mutableStateOf("landscape") } // 默认横向
    var purpose by rememberSaveable { mutableStateOf(PURPOSE_TAGS.first()) }
    val focus = remember { FocusRequester() }

    fun submit() {
        onCreate(name.trim().ifBlank { "未命名笔记" }, CoverPalette[coverIdx].toArgbHex(), paper, purpose, pageSize, orientation)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 120.dp)
                .softShadow(14.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .innerHighlight()
                .paintedShadow(RoundedCornerShape(20.dp))
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("新建笔记本", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))

            // 名称输入(App 输入框语言:10dp 圆角 + focus 描边,替换 M3 OutlinedTextField)
            var nameFocused by remember { mutableStateOf(false) }
            val nameBorder by animateColorAsState(
                if (nameFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                tween(200), label = "nameBorder",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, nameBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (name.isEmpty()) {
                    Text("如:高数·第三章", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { nameFocused = it.isFocused },
                )
            }
            Spacer(Modifier.height(18.dp))

            Text("封面色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoverPalette.forEachIndexed { i, c ->
                    val sel = i == coverIdx
                    val ring by animateDpAsState(if (sel) 3.dp else 1.dp, tween(200), label = "coverRing")
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                width = ring,
                                color = if (sel) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable { coverIdx = i },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            Text("尺寸 · 方向", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PAGE_SIZES_MM.keys.forEach { id ->
                    val sel = pageSize == id
                    val bg by animateColorAsState(
                        if (sel) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface,
                        tween(200), label = "sizeBg",
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(bg)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                            .clickable { pageSize = id }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                    ) {
                        Text(
                            id,
                            fontSize = 13.sp,
                            color = if (sel) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                PAGE_ORIENTATIONS.forEach { (id, label) ->
                    val sel = orientation == id
                    val bg by animateColorAsState(
                        if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        tween(200), label = "orientBg",
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(bg)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                            .clickable { orientation = id }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            Text("纸张", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PAPER_KINDS.forEach { (id, label) ->
                    val sel = paper == id
                    val bg by animateColorAsState(
                        if (sel) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface,
                        tween(200), label = "paperBg",
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(bg)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                            .clickable { paper = id }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            color = if (sel) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            Text("用途", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PURPOSE_TAGS.forEach { tag ->
                    val sel = purpose == tag
                    val bg by animateColorAsState(
                        if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                        tween(200), label = "purposeBg",
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(bg)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                            .clickable { purpose = tag }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            tag,
                            fontSize = 12.sp,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(26.dp))

            // 创建 ↵
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accentBrush())
                    .softClickable { submit() }
                    .padding(horizontal = 30.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("创建", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                Text("↵", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
    }
}

/** Color → #RRGGBB */
internal fun androidx.compose.ui.graphics.Color.toArgbHex(): String =
    String.format("#%06X", 0xFFFFFF and toArgb())
