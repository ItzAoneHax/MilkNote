package com.milknote.app.ui.bookshelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.common.MilkBookCover
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkColorDot
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.NotebookAdvancedOptions
import com.milknote.app.ui.common.PaperKindSelector
import com.milknote.app.ui.common.coverColorName
import com.milknote.app.ui.common.paperColorName
import com.milknote.app.ui.theme.CoverPalette
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.notes.model.PAGE_ORIENTATIONS

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
    val reducedMotion = LocalMilkMotion.current

    fun submit() {
        onCreate(name.trim().ifBlank { "未命名笔记" }, CoverPalette[coverIdx].toArgbHex(), paper, purpose, pageSize, orientation, paperColor)
    }

    val previewName = name.trim().ifBlank { "未命名" }
    val sheetColor = runCatching { Color(android.graphics.Color.parseColor(paperColor)) }.getOrDefault(Color.White)

    MilkCenterPanel(
        title = "新建笔记本",
        onDismiss = onDismiss,
        maxWidth = 640.dp,
        maxHeight = 720.dp,
        footer = {
            MilkButton(
                label = if (showAdvanced) "收起更多设置" else "更多设置",
                onClick = { showAdvanced = !showAdvanced },
                variant = MilkButtonVariant.TEXT,
            )
            Spacer(Modifier.weight(1f))
            MilkButton(label = "取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(MilkSpace.sm))
            MilkButton(label = "创建", onClick = { submit() })
        },
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(MilkSpace.md))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(164.dp)) {
                    Text(
                        "书架封面预览",
                        style = MilkText.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(MilkSpace.sm))
                    MilkBookCover(
                        name = previewName,
                        cover = CoverPalette[coverIdx],
                        paperColor = sheetColor,
                        paper = paper,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.76f),
                        shadowElevation = 4.dp,
                    )
                }
                Spacer(Modifier.width(MilkSpace.xxl))
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
                    Spacer(Modifier.height(MilkSpace.lg))
                    MilkSectionLabel("封面色", description = coverColorName(CoverPalette[coverIdx].toArgbHex()))
                    Spacer(Modifier.height(MilkSpace.xs))
                    // 色点命中框 48dp、视觉圆点 16dp:整行左移 16dp,首枚色点左缘与上方输入框齐平
                    Row(Modifier.offset(x = (-16).dp)) {
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
                    Spacer(Modifier.height(MilkSpace.sm))
                    PaperKindSelector(selected = paper, onSelect = { paper = it })
                    Spacer(Modifier.height(MilkSpace.sm))
                    if (!showAdvanced) {
                        val direction = PAGE_ORIENTATIONS.firstOrNull { it.first == orientation }?.second ?: orientation
                        Text(
                            "$pageSize · $direction · ${paperColorName(paperColor)} · $purpose",
                            style = MilkText.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
            // 更多设置:不对称强 ease-out——展开慢而稳(220ms),收起干脆(150ms),
            // 空间位移与淡化各自计时,避免「整块同时弹一下」的机械感。
            AnimatedVisibility(
                visible = showAdvanced,
                enter = if (reducedMotion) {
                    fadeIn(tween(MilkMotion.reduced))
                } else {
                    expandVertically(
                        animationSpec = tween(220, easing = MilkMotion.EaseOut),
                        expandFrom = Alignment.Top,
                    ) + fadeIn(tween(180, easing = MilkMotion.EaseOut))
                },
                exit = if (reducedMotion) {
                    fadeOut(tween(MilkMotion.reduced))
                } else {
                    shrinkVertically(
                        animationSpec = tween(150, easing = MilkMotion.EaseOut),
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(tween(110, easing = MilkMotion.EaseOut))
                },
            ) {
                Column(Modifier.fillMaxWidth().padding(top = MilkSpace.xl)) {
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
            Spacer(Modifier.height(MilkSpace.xs))
        }
    }
}

internal fun androidx.compose.ui.graphics.Color.toArgbHex(): String =
    String.format("#%06X", 0xFFFFFF and toArgb())
