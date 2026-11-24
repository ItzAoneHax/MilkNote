package com.milknote.app.ui.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkEmptyState
import com.milknote.app.ui.common.MilkFilterChip
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.ink.model.LassoContent
import com.milknote.ink.model.PageImageKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 套索内容筛选:多选芯片,选中即生效 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LassoFilterPanel(
    selected: Set<LassoContent>,
    onToggle: (LassoContent) -> Unit,
    onDismiss: () -> Unit,
) {
    MilkCenterPanel(title = "套索内容筛选", subtitle = "默认混合选择；取消的类型不会被圈入", onDismiss = onDismiss, maxWidth = 480.dp) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                .testTag("writer-panel-scroll").padding(top = 12.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MilkSpace.sm),
                verticalArrangement = Arrangement.spacedBy(MilkSpace.sm),
            ) {
                LassoContent.entries.forEach { filter ->
                    MilkFilterChip(label = filter.label, selected = filter in selected, onClick = { onToggle(filter) })
                }
            }
        }
    }
}

data class LocalMaterial(val file: File, val label: String)

@Composable
internal fun MaterialLibraryPanel(
    onDismiss: () -> Unit,
    onImportImage: () -> Unit,
    onDoodle: () -> Unit,
    onBuiltin: (PageImageKind, Int) -> Unit,
    onMaterial: (File, PageImageKind) -> Unit,
) {
    val materials by produceState(emptyList<LocalMaterial>()) {
        value = withContext(Dispatchers.IO) {
            val root = File(ServiceLocator.appContext.filesDir, "materials")
            root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }
                .sortedByDescending { it.lastModified() }.take(60)
                .map { LocalMaterial(it, it.nameWithoutExtension) }.toList()
        }
    }
    val cs = MaterialTheme.colorScheme
    MilkCenterPanel(title = "本地素材", subtitle = "图片、图案笔、胶带与贴纸；无商店或云端下载", onDismiss = onDismiss, maxWidth = 520.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()).testTag("writer-panel-scroll")) {
            Spacer(Modifier.height(MilkSpace.md))
            Row(horizontalArrangement = Arrangement.spacedBy(MilkSpace.sm)) {
                MaterialAction("导入图片", Icons.Outlined.Image, onImportImage)
                MaterialAction("图案笔", Icons.Outlined.Brush, onDoodle)
            }
            Spacer(Modifier.height(MilkSpace.xl))
            MilkSectionLabel("胶带")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).testTag("writer-material-tapes"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..3).forEach { MaterialAction("胶带 ${it + 1}", null) { onBuiltin(PageImageKind.TAPE, it) } }
            }
            Spacer(Modifier.height(MilkSpace.lg + 2.dp))
            MilkSectionLabel("贴纸")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).testTag("writer-material-stickers"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..3).forEach { MaterialAction("星形贴纸 ${it + 1}", null) { onBuiltin(PageImageKind.STICKER, it) } }
            }
            Spacer(Modifier.height(MilkSpace.lg + 2.dp))
            MilkSectionLabel("我的素材")
            Spacer(Modifier.height(6.dp))
            if (materials.isEmpty()) {
                MilkEmptyState(
                    title = "还没有素材",
                    description = "导入图片或插入胶带 / 贴纸后，会保留在这里",
                )
            }
            materials.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .clickable {
                            val kind = when {
                                item.file.name.startsWith("tape-") -> PageImageKind.TAPE
                                item.file.name.startsWith("sticker-") -> PageImageKind.STICKER
                                else -> PageImageKind.IMAGE
                            }
                            onMaterial(item.file, kind)
                        }
                        .heightIn(min = 48.dp).padding(horizontal = MilkSpace.sm + 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.label, style = MilkText.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("插入", style = MilkText.label, color = cs.primary, modifier = Modifier.padding(start = MilkSpace.md))
                }
            }
        }
    }
}

/** 素材动作按钮:淡灰圆角块 + 可选图标,48dp 命中 */
@Composable
private fun MaterialAction(label: String, icon: ImageVector?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(MilkRadius.lg))
            .background(cs.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, cs.outline.copy(alpha = 0.8f), RoundedCornerShape(MilkRadius.lg))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = MilkSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(MilkSpace.sm))
        }
        Text(label, style = MilkText.body, color = cs.onSurface)
    }
}
