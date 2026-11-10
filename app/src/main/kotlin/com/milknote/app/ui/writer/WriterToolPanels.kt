package com.milknote.app.ui.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.MilkActionPanel
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkPanelAction
import com.milknote.ink.model.LassoContent
import com.milknote.ink.model.PageImageKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LassoFilterPanel(
    selected: Set<LassoContent>,
    onToggle: (LassoContent) -> Unit,
    onDismiss: () -> Unit,
) {
    MilkCenterPanel(title = "套索内容筛选", subtitle = "默认混合选择；取消的类型不会被圈入", onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LassoContent.entries.chunked(3).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.forEach { filter ->
                        val active = filter in selected
                        Text(
                            filter.label,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clip(CircleShape)
                                .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { onToggle(filter) }.defaultMinSize(minHeight = 48.dp)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
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
    MilkCenterPanel(title = "本地素材", subtitle = "图片、图案笔、胶带与贴纸；无商店或云端下载", onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MaterialAction("导入图片", onImportImage)
                MaterialAction("图案笔", onDoodle)
            }
            Spacer(Modifier.height(16.dp))
            Text("胶带", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                (0..3).forEach { MaterialAction("胶带 ${it + 1}") { onBuiltin(PageImageKind.TAPE, it) } }
            }
            Text("贴纸", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                (0..3).forEach { MaterialAction("星形贴纸 ${it + 1}") { onBuiltin(PageImageKind.STICKER, it) } }
            }
            Text("我的素材", style = MaterialTheme.typography.labelLarge)
            if (materials.isEmpty()) {
                Text("导入图片或插入胶带/贴纸后会保留在这里", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            materials.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val kind = when {
                                item.file.name.startsWith("tape-") -> PageImageKind.TAPE
                                item.file.name.startsWith("sticker-") -> PageImageKind.STICKER
                                else -> PageImageKind.IMAGE
                            }
                            onMaterial(item.file, kind)
                        }
                        .defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Text(item.label, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text("插入", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun MaterialAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}
