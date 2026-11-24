package com.milknote.app.ui.bookshelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkColorDot
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.NotebookAdvancedOptions
import com.milknote.app.ui.common.NotebookPaperPattern
import com.milknote.app.ui.common.PaperKindSelector
import com.milknote.app.ui.common.coverColorName
import com.milknote.app.ui.common.paperColorName
import com.milknote.app.ui.theme.CoverPalette
import com.milknote.app.ui.theme.MonoMeta
import com.milknote.notes.model.PAGE_ORIENTATIONS
import com.milknote.notes.model.PAPER_KINDS

@Composable
fun NewNotebookDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, coverHex: String, paper: String, purpose: String, pageSize: String, orientation: String, paperColor: String) -> Unit,
    defaultPaper: String = "dot",
    defaultPaperColor: String = "#FFFFFF",
) {
    var name by rememberSaveable { mutableStateOf("") }
    var coverIdx by rememberSaveable { mutableStateOf(0) }
    var paper by rememberSaveable { mutableStateOf(defaultPaper) }
    var paperColor by rememberSaveable { mutableStateOf(defaultPaperColor) }
    var pageSize by rememberSaveable { mutableStateOf("B5") }
    var orientation by rememberSaveable { mutableStateOf("landscape") }
    var purpose by rememberSaveable { mutableStateOf(com.milknote.notes.model.PURPOSE_TAGS.first()) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        onCreate(name.trim().ifBlank { "未命名笔记" }, CoverPalette[coverIdx].toArgbHex(), paper, purpose, pageSize, orientation, paperColor)
    }

    MilkCenterPanel(title = "新建笔记本", onDismiss = onDismiss, maxWidth = 640.dp, maxHeight = 720.dp) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(164.dp)) {
                    Text(
                        "书架封面预览",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LiveBookCover(
                        name = name.trim().ifBlank { "未命名" },
                        cover = CoverPalette[coverIdx],
                        paperColor = paperColor,
                        paper = paper,
                    )
                }
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    MilkTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "笔记本名称",
                        mono = false,
                        leading = {
                            Icon(
                                Icons.Outlined.AutoStories,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        imeAction = ImeAction.Done,
                        onDone = { submit() },
                    )
                    Spacer(Modifier.height(16.dp))
                    MilkSectionLabel("封面色", description = coverColorName(CoverPalette[coverIdx].toArgbHex()))
                    Spacer(Modifier.height(6.dp))
                    Row {
                        CoverPalette.forEachIndexed { i, color ->
                            val hex = color.toArgbHex()
                            MilkColorDot(
                                color = color,
                                selected = i == coverIdx,
                                onClick = { coverIdx = i },
                                contentDescription = "封面色 ${coverColorName(hex)}",
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    MilkSectionLabel("纸纹")
                    Spacer(Modifier.height(8.dp))
                    PaperKindSelector(selected = paper, onSelect = { paper = it })
                    Spacer(Modifier.height(8.dp))
                    if (!showAdvanced) {
                        val direction = PAGE_ORIENTATIONS.firstOrNull { it.first == orientation }?.second ?: orientation
                        Text(
                            "$pageSize · $direction · ${paperColorName(paperColor)} · $purpose",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
            AnimatedVisibility(visible = showAdvanced) {
                Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
                    Spacer(Modifier.height(18.dp))
                    NotebookAdvancedOptions(
                        pageSize = pageSize,
                        onPageSize = { pageSize = it },
                        orientation = orientation,
                        onOrientation = { orientation = it },
                        paperColor = paperColor,
                        onPaperColor = { paperColor = it },
                        purpose = purpose,
                        onPurpose = { purpose = it },
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MilkButton(
                label = if (showAdvanced) "收起更多设置" else "更多设置",
                onClick = { showAdvanced = !showAdvanced },
                variant = MilkButtonVariant.TEXT,
            )
            Spacer(Modifier.weight(1f))
            MilkButton(label = "取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(8.dp))
            MilkButton(label = "创建 ↵", onClick = { submit() })
        }
    }
}

@Composable
private fun LiveBookCover(
    name: String,
    cover: Color,
    paperColor: String,
    paper: String,
    modifier: Modifier = Modifier,
) {
    val sheet = runCatching { Color(android.graphics.Color.parseColor(paperColor)) }.getOrDefault(Color.White)
    val bookShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(0.76f)
            .clip(bookShape)
            .background(sheet)
            .border(1.dp, Color.Black.copy(alpha = 0.09f), bookShape),
    ) {
        NotebookPaperPattern(kind = paper, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(11.dp)
                .background(cover)
                .drawBehind {
                    val crease = 2.dp.toPx()
                    drawRect(Color.Black.copy(alpha = 0.14f), topLeft = Offset(size.width - crease, 0f), size = Size(crease, size.height))
                },
        )
        Text(
            name,
            fontFamily = FontFamily.Serif,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF18191B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 20.dp),
        )
        Text(
            PAPER_KINDS.firstOrNull { it.first == paper }?.second.orEmpty(),
            style = MonoMeta.copy(fontSize = 10.sp),
            color = Color(0xFF757B84),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 8.dp),
        )
    }
}

internal fun androidx.compose.ui.graphics.Color.toArgbHex(): String =
    String.format("#%06X", 0xFFFFFF and toArgb())
