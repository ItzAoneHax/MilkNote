package com.milknote.app.ui.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.ink.model.InkTool
import com.milknote.notes.model.NotebookMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** P066–P069 融合：单个中央操作台，复用已有选区/Agent/页面能力。 */
@Composable
internal fun WriterConsole(
    state: WriterUiState,
    onDismiss: () -> Unit,
    onTool: (InkTool) -> Unit,
    onEraser: () -> Unit,
    onDeleteSelection: () -> Unit,
    onSendSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onNotebook: (String) -> Unit,
    onPage: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRotate: () -> Unit,
    onReading: () -> Unit,
) {
    var help by remember { mutableStateOf(true) }
    val notebooks by produceState(emptyList<NotebookMeta>()) {
        value = withContext(Dispatchers.IO) { ServiceLocator.repository.listNotebooks() }
    }
    MilkCenterPanel(title = "K 操作台", subtitle = "内容、画笔、笔记本与页面", onDismiss = onDismiss, maxWidth = 600.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().clickable { help = !help }.padding(vertical = 12.dp)) {
                Text("使用说明", Modifier.weight(1f))
                Text(if (help) "收起" else "展开", color = MaterialTheme.colorScheme.primary)
            }
            if (help) {
                Text(
                    "点选元素后可删除或发送 Agent；画笔与页面操作立即生效。阅读工具隐藏辅助 chrome，专注模式仍可单独进入。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConsoleHeader("选中内容 · ${state.selectionCount}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ConsoleButton("删除内容", state.selectionCount > 0, onDeleteSelection)
                ConsoleButton("发送 Agent", state.selectionCount > 0, onSendSelection)
                ConsoleButton("取消选择", state.selectionCount > 0, onClearSelection)
            }
            ConsoleHeader("画笔")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(InkTool.PEN to "钢笔", InkTool.BALL to "圆珠笔", InkTool.HIGHLIGHTER to "荧光笔", InkTool.DOODLE to "图案笔").forEach { (tool, label) ->
                    ConsoleButton(label, state.tool.tool != tool || state.toolSettings.eraserTemporary) { onTool(tool) }
                }
                ConsoleButton("橡皮", !state.toolSettings.eraserTemporary, onEraser)
            }
            ConsoleHeader("笔记本")
            notebooks.forEach { nb ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onNotebook(nb.id) }
                        .defaultMinSize(minHeight = 48.dp).padding(12.dp),
                ) {
                    Text(if (nb.id == state.notebook?.id) "✓ ${nb.name}" else nb.name)
                    Spacer(Modifier.weight(1f))
                    Text(nb.folder.ifBlank { "未分组" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ConsoleHeader("页面 · ${state.currentPageIndex + 1}/${state.pages.size}")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ConsoleButton("上一页", state.canPreviousPage, onPrevious)
                ConsoleButton("下一页", state.canNextPage, onNext)
                ConsoleButton("旋转 90°", true, onRotate)
                ConsoleButton("阅读工具", true, onReading)
            }
            state.pages.forEachIndexed { index, page ->
                Text(
                    if (page.pageId == state.currentPageId) "✓ 第 ${index + 1} 页 · ${page.pageId}" else "第 ${index + 1} 页 · ${page.pageId}",
                    modifier = Modifier.fillMaxWidth().clickable { onPage(page.pageId) }.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun ConsoleHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
private fun ConsoleButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick).defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}
