package com.milknote.app.ui.bookshelf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkColorDot
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.NotebookAdvancedOptions
import com.milknote.app.ui.common.PaperKindSelector
import com.milknote.app.ui.common.coverColorName
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.notes.model.COVER_COLORS
import com.milknote.notes.model.NotebookMeta

@Composable
internal fun NotebookManagePanel(
    meta: NotebookMeta,
    folders: List<String>,
    onDismiss: () -> Unit,
    onSave: (NotebookMeta) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(meta.id) { mutableStateOf(meta.name) }
    var folder by remember(meta.id) { mutableStateOf(meta.folder) }
    var tagColor by remember(meta.id) { mutableStateOf(meta.tagColor.ifBlank { meta.coverColor }) }
    var paper by remember(meta.id) { mutableStateOf(meta.paper) }
    var paperColor by remember(meta.id) { mutableStateOf(meta.paperColor) }
    var pageSize by remember(meta.id) { mutableStateOf(meta.pageSize) }
    var orientation by remember(meta.id) { mutableStateOf(meta.orientation) }
    var purpose by remember(meta.id) { mutableStateOf(meta.purpose) }

    MilkCenterPanel(
        title = "笔记本信息",
        subtitle = if (folders.isEmpty()) null else "${folders.size} 个文件夹",
        onDismiss = onDismiss,
        maxWidth = 560.dp,
        maxHeight = 720.dp,
        footer = {
            MilkButton(
                label = if (meta.trashed) "已在回收站" else "移入回收站",
                onClick = onDelete,
                variant = MilkButtonVariant.TEXT,
                destructive = true,
                enabled = !meta.trashed,
            )
            Spacer(Modifier.weight(1f))
            MilkButton(label = "取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(MilkSpace.sm))
            MilkButton(
                label = "保存",
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        meta.copy(
                            name = name.trim(),
                            folder = folder.trim(),
                            // 换色要同时落到书脊(coverColor)与标签(tagColor),否则书架上的封面不变色
                            coverColor = tagColor,
                            tagColor = tagColor,
                            paper = paper,
                            paperColor = paperColor,
                            pageSize = pageSize,
                            orientation = orientation,
                            purpose = purpose,
                        ),
                    )
                },
            )
        },
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(MilkSpace.md))
            MilkTextField(value = name, onValueChange = { name = it }, placeholder = "名称", mono = false)
            Spacer(Modifier.height(MilkSpace.md))
            MilkTextField(
                value = folder,
                onValueChange = { folder = it },
                placeholder = "文件夹（留空为未分组）",
                mono = false,
            )
            Spacer(Modifier.height(MilkSpace.lg))
            MilkSectionLabel("标签颜色", description = coverColorName(tagColor))
            Spacer(Modifier.height(MilkSpace.xs))
            // 色点命中框 48dp、视觉圆点 16dp:整行左移 16dp,首枚色点左缘与上方输入框齐平
            Row(Modifier.offset(x = (-16).dp)) {
                COVER_COLORS.forEach { hex ->
                    MilkColorDot(
                        color = Color(android.graphics.Color.parseColor(hex)),
                        selected = tagColor.equals(hex, ignoreCase = true),
                        onClick = { tagColor = hex },
                        contentDescription = "标签颜色 ${coverColorName(hex)}",
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            MilkSectionLabel("纸纹")
            Spacer(Modifier.height(MilkSpace.sm))
            PaperKindSelector(selected = paper, onSelect = { paper = it })
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
            Spacer(Modifier.height(MilkSpace.md))
        }
    }
}
