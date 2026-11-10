package com.milknote.app.ui.bookshelf

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.notes.model.COVER_COLORS
import com.milknote.notes.model.NotebookMeta
import androidx.compose.ui.unit.dp

/** 重命名、标签颜色与文件夹集中在同一中央面板，避免多层 Dialog。 */
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
    MilkCenterPanel(title = "管理笔记本", subtitle = meta.id, onDismiss = onDismiss, maxWidth = 520.dp) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            PanelInput(value = name, onValueChange = { name = it }, hint = "笔记本名称")
            Text("标签颜色", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                COVER_COLORS.forEach { hex ->
                    val selected = tagColor.equals(hex, ignoreCase = true)
                    Box(
                        Modifier.size(48.dp).clickable { tagColor = hex },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
            Text("移动到文件夹", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FolderChip("未分组", folder.isBlank()) { folder = "" }
                folders.filter { it != folder }.take(3).forEach { item ->
                    FolderChip(item, false) { folder = item }
                }
            }
            PanelInput(value = folder, onValueChange = { folder = it }, hint = "新文件夹名称（留空为未分组）")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                MilkButton(label = "删除笔记本", onClick = onDelete, variant = MilkButtonVariant.SECONDARY)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            MilkButton(label = "取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(8.dp))
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

@Composable
private fun PanelInput(value: String, onValueChange: (String) -> Unit, hint: String) {
    Box(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 13.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            },
        )
    }
}

@Composable
private fun FolderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick).defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    )
}
