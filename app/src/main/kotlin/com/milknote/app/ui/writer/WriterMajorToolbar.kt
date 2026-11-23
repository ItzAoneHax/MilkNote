package com.milknote.app.ui.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkColorDot
import com.milknote.app.ui.common.MilkPanelAction
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.notes.model.PAPER_COLORS
import com.milknote.notes.model.PAPER_KINDS

internal val WriterMajorToolbarHeight = 44.dp
private val WriterMajorToolSlotWidth = 46.dp

/**
 * 牛奶笔记第二代 44dp 单行多标签顶栏（DESIGN-V2 §1）：
 * 左侧返回 + 页面/卡片侧栏 + 已打开文档多标签页；中部/右侧完整工具带、双开分屏、录音、专注、右侧 Studio（AI/MD/关联）与更多菜单。
 */
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
    onSelectTab: (String) -> Unit = {},
    onCloseTab: (String) -> Unit = {},
    onToggleSplit: () -> Unit = {},
    onEnterFocus: () -> Unit = {},
) {
    val toolsEnabled = state.snip.phase == SnipPhase.IDLE
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(WriterMajorToolbarHeight)
            .background(cs.surface)
            .edgeHairline(cs.outline.copy(alpha = 0.65f), bottom = true)
            .semantics { contentDescription = "书写页主工具栏" },
    ) {
        val wide = maxWidth >= 960.dp
        Row(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = if (wide) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WriterMajorButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回书架", true, false, onBack, "writer-back")
            WriterMajorButton(
                Icons.Outlined.ViewSidebar,
                if (railMode == WriterRailMode.PAGES) "收起页面栏" else "展开页面栏",
                true,
                railMode == WriterRailMode.PAGES || railMode == WriterRailMode.OUTLINE,
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

            // 多文档标签条（宽屏平板下内联展示打开的笔记本标签）
            if (wide && state.openTabs.isNotEmpty()) {
                Row(
                    Modifier
                        .widthIn(max = 320.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.openTabs.forEach { tab ->
                        val selected = tab.id == state.notebook?.id
                        val dotColor = runCatching { Color(android.graphics.Color.parseColor(tab.coverColor)) }
                            .getOrDefault(cs.primary)
                        Row(
                            Modifier
                                .height(30.dp)
                                .widthIn(max = 176.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) cs.surfaceVariant else Color.Transparent)
                                .clickable { onSelectTab(tab.id) }
                                .padding(start = 10.dp, end = if (state.openTabs.size > 1) 6.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                tab.name,
                                fontSize = 12.5.sp,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                color = if (selected) cs.onSurface else cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (state.openTabs.size > 1) {
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .clickable { onCloseTab(tab.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "关闭标签",
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

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

            if (wide) {
                WriterMajorButton(
                    Icons.Outlined.VerticalSplit,
                    if (state.splitEnabled) "关闭双开分屏" else "双开分屏",
                    true,
                    state.splitEnabled,
                    onToggleSplit,
                    "writer-split",
                )
            }
            WriterMajorButton(Icons.Outlined.Mic, "录音", toolsEnabled, false, onRecord, "writer-record")
            WriterMajorButton(Icons.Outlined.Videocam, "学习视频", toolsEnabled, false, onVideo, "writer-video")
            if (wide) {
                WriterMajorButton(Icons.Outlined.CropFree, "专注模式", true, false, onEnterFocus, "writer-focus")
            }
            WriterMajorButton(
                Icons.Outlined.AutoAwesome,
                if (agentOpen) "收起右侧面板" else "AI 与伴随笔记",
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
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    active -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * 第二代书写页「更多」面板（对齐 design-review/gen2/#pop-w-more）：
 * 内联 6 种纸纹切换 + 纸色切换 + 双开分屏 / 清空手绘 / 导出 / 专注模式 / 命令面板 / 笔诊断。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WriterMorePanel(
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onEnterFocus: () -> Unit,
    onOpenPalette: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    currentPaper: String = "dot",
    currentPaperColor: String = "#FFFFFF",
    splitEnabled: Boolean = false,
    onSelectPaper: (String) -> Unit = {},
    onSelectPaperColor: (String) -> Unit = {},
    onToggleSplit: () -> Unit = {},
    onClearPage: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    MilkCenterPanel(
        title = "纸张与页面操作",
        onDismiss = onDismiss,
        maxWidth = 440.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MilkSectionLabel("纸纹")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PAPER_KINDS.forEach { (id, label) ->
                    MilkChip(
                        label = label,
                        selected = currentPaper == id,
                        onClick = { onSelectPaper(id) },
                        small = true,
                    )
                }
            }

            MilkSectionLabel("纸色")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PAPER_COLORS.forEach { (hex, label) ->
                    MilkColorDot(
                        color = Color(android.graphics.Color.parseColor(hex)),
                        selected = currentPaperColor.equals(hex, ignoreCase = true),
                        onClick = { onSelectPaperColor(hex) },
                        dotSize = 24.dp,
                        contentDescription = label,
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outline))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MoreRowItem(
                    label = if (splitEnabled) "关闭双开分屏" else "双开分屏",
                    icon = Icons.Outlined.VerticalSplit,
                    onClick = { onDismiss(); onToggleSplit() },
                )
                MoreRowItem(
                    label = "清空本页手绘",
                    icon = Icons.Outlined.Clear,
                    onClick = { onDismiss(); onClearPage() },
                )
                MoreRowItem(
                    label = "导出",
                    icon = Icons.Outlined.FileDownload,
                    onClick = onExport,
                )
                MoreRowItem(
                    label = "专注模式",
                    icon = Icons.Outlined.CropFree,
                    onClick = onEnterFocus,
                )
                MoreRowItem(
                    label = "搜索与命令面板",
                    icon = Icons.Outlined.Search,
                    onClick = onOpenPalette,
                )
                MoreRowItem(
                    label = "笔诊断",
                    icon = Icons.Outlined.Edit,
                    onClick = onOpenDiagnostics,
                )
            }
        }
    }
}

@Composable
private fun MoreRowItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
    }
}
