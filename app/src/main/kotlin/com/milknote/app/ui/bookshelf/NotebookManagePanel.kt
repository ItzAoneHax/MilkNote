package com.milknote.app.ui.bookshelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkColorDot
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkTextField
import com.milknote.notes.model.COVER_COLORS
import com.milknote.notes.model.NotebookMeta

/** 重命名、标签颜色与文件夹集中在同一中央面板,避免多层 Dialog。 */
@OptIn(ExperimentalLayoutApi::class)
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
        title = "管理笔记本",
        subtitle = "${meta.name} · ${meta.id}",
        onDismiss = onDismiss,
        maxWidth = 520.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            MilkTextField(value = name, onValueChange = { name = it }, placeholder = "笔记本名称", label = "名称", mono = false)
            Column {
                MilkSectionLabel("标签颜色")
                Spacer(Modifier.height(8.dp))
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
            Column {
                MilkSectionLabel("移动到文件夹")
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MilkChip("未分组", folder.isBlank(), onClick = { folder = "" })
                    folders.filter { it != folder }.take(3).forEach { item ->
                        MilkChip(item, false, onClick = { folder = item })
                    }
                }
                Spacer(Modifier.height(12.dp))
                MilkTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    placeholder = "输入新文件夹名，留空为未分组",
                    label = "文件夹",
                    mono = false,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MilkButton(
                label = if (meta.trashed) "彻底删除" else "移入回收站",
                onClick = onDelete,
                variant = MilkButtonVariant.TEXT,
                destructive = true,
            )
            Spacer(Modifier.weight(1f))
            MilkButton(label = "取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(10.dp))
            MilkButton(
                label = "保存",
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(meta.copy(name = name.trim(), folder = folder.trim(), tagColor = tagColor))
                },
            )
        }
    }
}
