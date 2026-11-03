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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.milkDialogSurface
import com.milknote.app.ui.common.accentBrush
import com.milknote.app.ui.common.innerHighlight
import com.milknote.app.ui.common.paintedShadow
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.CoverPalette
import com.milknote.app.ui.theme.PillShape
import com.milknote.notes.model.PAGE_ORIENTATIONS
import com.milknote.notes.model.PAGE_SIZES_MM
import com.milknote.notes.model.PAPER_KINDS
import com.milknote.notes.model.PURPOSE_TAGS

/**
 * ② 新建笔记本弹窗:名称 / 封面色 6 / 纸张尺寸·方向 / 纸张模板 / 用途标签;「创建 ↵」直接进入第 1 页。
 * R2 复审:≤600dp 限宽(A-08);小窗/IME 可滚动、取消与创建始终可达;
 * 色点/chips 48dp 命中,视觉尺寸不变(A-09)。
 */
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
                .widthIn(max = 600.dp)   // 先限宽再 fill(顺序反了会通栏,实测像素验证)
                .fillMaxWidth()
                .imePadding()
                .heightIn(max = 620.dp)
                .milkDialogSurface()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("新建笔记本", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            // 内容区可滚动:小窗口/IME 打开时不裁切(A-08);CTA 固定在底部
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CoverPalette.forEachIndexed { i, c ->
                        val sel = i == coverIdx
                        val ring by animateDpAsState(if (sel) 3.dp else 1.dp, tween(200), label = "coverRing")
                        // 48dp 命中,视觉色点 30dp(A-9)
                        Box(
                            Modifier
                                .size(48.dp)
                                .clickable { coverIdx = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = ring,
                                        color = if (sel) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape,
                                    ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))

                Text("尺寸 · 方向", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PAGE_SIZES_MM.keys.forEach { id ->
                        OptionChip(
                            label = id,
                            selected = pageSize == id,
                        ) { pageSize = id }
                    }
                    Spacer(Modifier.width(8.dp))
                    PAGE_ORIENTATIONS.forEach { (id, label) ->
                        OptionChip(
                            label = label,
                            selected = orientation == id,
                        ) { orientation = id }
                    }
                }
                Spacer(Modifier.height(18.dp))

                Text("纸张", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PAPER_KINDS.forEach { (id, label) ->
                        OptionChip(
                            label = label,
                            selected = paper == id,
                        ) { paper = id }
                    }
                }
                Spacer(Modifier.height(18.dp))

                Text("用途", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PURPOSE_TAGS.forEach { tag ->
                        OptionChip(
                            label = tag,
                            selected = purpose == tag,
                        ) { purpose = tag }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            // CTA 行:取消 + 创建,均 ≥48dp 命中,始终可达(A-08)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MilkButton(
                    label = "取消",
                    onClick = onDismiss,
                    variant = MilkButtonVariant.SECONDARY,
                )
                Spacer(Modifier.width(8.dp))
                MilkButton(label = "创建 ↵", onClick = { submit() })
            }
        }
    }
}

/** 表单选项 chip:48dp 命中;所有分组共用 activeSoft 蓝底、蓝字与蓝色描边。 */
@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        tween(200), label = "optionChipBg",
    )
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline,
        tween(200), label = "optionChipBorder",
    )
    Box(
        Modifier
            .clip(PillShape)
            .background(bg)
            .border(1.dp, borderColor, PillShape)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}

/** Color → #RRGGBB */
internal fun androidx.compose.ui.graphics.Color.toArgbHex(): String =
    String.format("#%06X", 0xFFFFFF and toArgb())
