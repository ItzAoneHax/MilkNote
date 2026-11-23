package com.milknote.app.ui.bookshelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkColorDot
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.settings.TextAction
import com.milknote.notes.model.COVER_COLORS
import com.milknote.notes.model.NotebookMeta

/** 笔记本信息(第二代 dlg-rename):名称 + 文件夹 + 标签颜色;左下「移入回收站」,右下 取消 / 保存。 */
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
    MilkCenterPanel(
        title = "笔记本信息",
        subtitle = "${meta.id}.milk · ${folders.size.takeIf { it > 0 }?.let { "$it 个文件夹" } ?: "未分组"}",
        onDismiss = onDismiss,
        maxWidth = 480.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MilkTextField(value = name, onValueChange = { name = it }, placeholder = "名称", mono = false)
            MilkTextField(
                value = folder,
                onValueChange = { folder = it },
                placeholder = "文件夹（留空为未分组）",
                mono = false,
            )
            Column {
                MilkSectionLabel("标签颜色")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    COVER_COLORS.forEach { hex ->
                        MilkColorDot(
                            color = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)),
                            selected = tagColor.equals(hex, ignoreCase = true),
                            onClick = { tagColor = hex },
                            contentDescription = "标签颜色",
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextAction(
                label = if (meta.trashed) "已在回收站" else "移入回收站",
                danger = true,
                enabled = !meta.trashed,
                onClick = onDelete,
            )
            Spacer(Modifier.weight(1f))
            MilkButton(label = "取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(8.dp))
            MilkButton(
                label = "保存",
                enabled = name.isNotBlank(),
                onClick = { onSave(meta.copy(name = name.trim(), folder = folder.trim(), tagColor = tagColor)) },
            )
        }
    }
}
