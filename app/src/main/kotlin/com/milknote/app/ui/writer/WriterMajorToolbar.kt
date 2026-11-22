package com.milknote.app.ui.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.common.MilkActionPanel
import com.milknote.app.ui.common.MilkPanelAction

internal val WriterMajorToolbarHeight = 42.dp
private val WriterMajorToolSlotWidth = 49.dp

/** P016：固定入口、完整工具带和文档动作共用一条无分段的全宽工具栏。 */
@Composable
internal fun WriterMajorToolbar(
    state: WriterUiState,
    selectedTool: WriterMajorTool,
    railMode: WriterRailMode?,
    onBack: () -> Unit,
    onShowPages: () -> Unit,
    onShowSnippets: () -> Unit,
    onTool: (WriterMajorTool) -> Unit,
    onRecord: () -> Unit,
    onVideo: () -> Unit,
    onAgent: () -> Unit,
    onMaterials: () -> Unit,
    onNewPage: () -> Unit,
    onMore: () -> Unit,
    agentOpen: Boolean,
    agentBusy: Boolean,
) {
    val toolsEnabled = state.snip.phase == SnipPhase.IDLE
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(WriterMajorToolbarHeight)
            .background(MaterialTheme.colorScheme.surface)
            .edgeHairline(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), bottom = true)
            .semantics { contentDescription = "书写页主工具栏" },
    ) {
        val wide = maxWidth >= 1000.dp
        Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.padding(start = if (wide) 10.dp else 0.dp)) {
                WriterMajorButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回书架", true, false, onBack, "writer-back")
                WriterMajorButton(
                    Icons.Outlined.GridOn,
                    if (railMode == WriterRailMode.PAGES) "收起页面栏" else "展开页面栏",
                    true,
                    railMode == WriterRailMode.PAGES,
                    onShowPages,
                    "writer-page-toggle",
                )
                WriterMajorButton(
                    Icons.Outlined.ContentCopy,
                    if (railMode == WriterRailMode.SNIPPETS) "收起摘取栏" else "展开摘取栏",
                    true,
                    railMode == WriterRailMode.SNIPPETS,
                    onShowSnippets,
                    "writer-snippets",
                )
            }
            Spacer(Modifier.width(if (wide) 72.dp else 8.dp))
            val listState = rememberLazyListState()
            LazyRow(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("writer-major-tools")
                    .semantics { contentDescription = "主工具带，可横向滚动" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(writerMajorToolOrder, key = { it.id }) { tool ->
                    val active = isMajorToolActive(tool, selectedTool, state)
                    WriterMajorButton(
                        icon = majorToolIcon(tool),
                        label = if (active) "${tool.label}，再次点击切换设置" else tool.label,
                        enabled = toolsEnabled,
                        active = active,
                        onClick = { onTool(tool) },
                        tag = "writer-major-tool-${tool.id}",
                    )
                }
            }
            Spacer(Modifier.width(if (wide) 26.dp else 0.dp))
            WriterMajorButton(Icons.Outlined.Mic, "录音", toolsEnabled, false, onRecord, "writer-record")
            if (wide) Spacer(Modifier.weight(0.25f))
            WriterMajorButton(Icons.Outlined.Videocam, "学习视频", toolsEnabled, false, onVideo, "writer-video")
            WriterMajorButton(
                Icons.Outlined.AutoAwesome,
                if (agentOpen) "收起 AI" else "AI",
                true,
                agentOpen,
                onAgent,
                "writer-ai",
                badge = agentBusy,
            )
            WriterMajorButton(Icons.Outlined.Category, "素材", toolsEnabled, false, onMaterials, "writer-materials")
            WriterMajorButton(Icons.Outlined.Add, "新建页", toolsEnabled, false, onNewPage, "writer-new-page")
            WriterMajorButton(Icons.Outlined.MoreHoriz, "更多", true, false, onMore, "writer-more")
        }
    }
}

internal fun isMajorToolActive(
    tool: WriterMajorTool,
    selected: WriterMajorTool,
    state: WriterUiState,
): Boolean = tool == selected

private fun majorToolIcon(tool: WriterMajorTool): ImageVector = when (tool) {
    WriterMajorTool.BRUSH -> Icons.Outlined.Edit
    WriterMajorTool.HIGHLIGHTER -> Icons.Outlined.Brush
    WriterMajorTool.ERASER -> Icons.Outlined.Backspace
    WriterMajorTool.LASSO -> Icons.Outlined.Gesture
    WriterMajorTool.IMAGE -> Icons.Outlined.Image
    WriterMajorTool.TEXT -> Icons.Outlined.Title
    WriterMajorTool.DOODLE -> Icons.Outlined.InvertColors
    WriterMajorTool.SHAPE -> Icons.Outlined.Category
    WriterMajorTool.SNIP -> Icons.Outlined.ContentCopy
    WriterMajorTool.TAPE -> Icons.Outlined.Sell
    WriterMajorTool.READING -> Icons.Outlined.PanTool
    WriterMajorTool.LASER -> Icons.Outlined.Brush
}

@Composable
private fun WriterMajorButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    badge: Boolean = false,
) {
    Box(
        modifier
            .width(WriterMajorToolSlotWidth)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    active -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
internal fun WriterMorePanel(
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onEnterFocus: () -> Unit,
    onOpenPalette: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    MilkActionPanel(
        title = "更多",
        subtitle = "文档与窗口操作",
        actions = listOf(
            MilkPanelAction("导出", onExport, Icons.Outlined.FileDownload),
            MilkPanelAction("专注模式", onEnterFocus, Icons.Outlined.CropFree),
            MilkPanelAction("命令面板", onOpenPalette, Icons.Outlined.Search),
            MilkPanelAction("笔诊断", onOpenDiagnostics, Icons.Outlined.Edit),
        ),
        onDismiss = onDismiss,
    )
}
