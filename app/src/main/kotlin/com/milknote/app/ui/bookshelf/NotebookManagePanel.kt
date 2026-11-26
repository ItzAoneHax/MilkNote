package com.milknote.app.ui.bookshelf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkColorDot
import com.milknote.app.ui.common.MilkDropdownField
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.NotebookAdvancedOptions
import com.milknote.app.ui.common.PaperKindSelector
import com.milknote.app.ui.common.coverColorName
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.notes.model.COVER_COLORS
import com.milknote.notes.model.NotebookMeta

/**
 * 笔记本信息面板:名称 / 文件夹 / 标签颜色 / 纸纹 / 规格。
 * 文件夹走 [MilkDropdownField] 单选(未分组 / 既有文件夹 / 新建),不再要求用户手打字符串——
 * 手打拼错会凭空造出第三类「看起来同名」的文件夹。
 */
@Composable
internal fun NotebookManagePanel(
    meta: NotebookMeta,
    folders: List<String>,
    onDismiss: () -> Unit,
    onSave: (NotebookMeta) -> Unit,
    onDelete: () -> Unit,
    onCreateFolder: (String) -> Unit = {},
) {
    var name by remember(meta.id) { mutableStateOf(meta.name) }
    var folder by remember(meta.id) { mutableStateOf(meta.folder) }
    var tagColor by remember(meta.id) { mutableStateOf(meta.tagColor.ifBlank { meta.coverColor }) }
    var paper by remember(meta.id) { mutableStateOf(meta.paper) }
    var paperColor by remember(meta.id) { mutableStateOf(meta.paperColor) }
    var pageSize by remember(meta.id) { mutableStateOf(meta.pageSize) }
    var orientation by remember(meta.id) { mutableStateOf(meta.orientation) }
    var purpose by remember(meta.id) { mutableStateOf(meta.purpose) }
    var creatingFolder by remember(meta.id) { mutableStateOf(false) }
    var newFolderName by remember(meta.id) { mutableStateOf("") }

    // 未分组(空串)恒在首位;当前值即使不在 folders 里也保留,避免开面板就丢掉已存文件夹
    val folderOptions = remember(folders, folder) {
        listOf("") + (folders + listOfNotNull(folder.takeIf { it.isNotBlank() })).distinct().sorted()
    }
    fun createFolder() {
        val trimmed = newFolderName.trim()
        if (trimmed.isBlank()) return
        onCreateFolder(trimmed)
        folder = trimmed
        newFolderName = ""
        creatingFolder = false
    }

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
            MilkTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "名称",
                label = "名称",
                mono = false,
            )
            Spacer(Modifier.height(MilkSpace.md))
            MilkDropdownField(
                value = folder,
                options = folderOptions,
                labelFor = { it.ifBlank { "未分组" } },
                onSelect = { folder = it },
                label = "文件夹",
                leadingIcon = Icons.Outlined.Folder,
                optionIcon = { if (it.isBlank()) Icons.Outlined.FolderOff else Icons.Outlined.Folder },
                footerActionLabel = "新建文件夹",
                onFooterAction = { creatingFolder = true },
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

    // 新建文件夹:叠在信息面板之上的第二层浮层,创建后直接选中,不必再回下拉里找
    if (creatingFolder) {
        MilkCenterPanel(
            title = "新建文件夹",
            onDismiss = { creatingFolder = false },
            maxWidth = 380.dp,
            footer = {
                Spacer(Modifier.weight(1f))
                MilkButton("取消", onClick = { creatingFolder = false }, variant = MilkButtonVariant.SECONDARY)
                Spacer(Modifier.width(MilkSpace.sm))
                MilkButton(
                    label = "创建",
                    enabled = newFolderName.isNotBlank(),
                    onClick = { createFolder() },
                )
            },
        ) {
            Spacer(Modifier.height(MilkSpace.md))
            MilkTextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                placeholder = "如：高数与线代",
                label = "文件夹名称",
                mono = false,
                imeAction = ImeAction.Done,
                onDone = { createFolder() },
                onClear = { newFolderName = "" },
            )
            Spacer(Modifier.height(MilkSpace.md))
        }
    }
}
