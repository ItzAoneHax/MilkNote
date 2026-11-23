package com.milknote.app.ui.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.VerticalSplit
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
import com.milknote.app.ui.common.MilkAnchoredMenu

internal val WriterMajorToolbarHeight = 44.dp

/**
 * 第二代书写页 44dp 单行顶栏（DESIGN-V2 writer-bar）：
 * 返回 | 侧栏开关 | 已打开文档多标签 | 双开分屏 | 录音 | 专注 | 右侧 Studio | 更多（锚定弹出）。
 * 工具全部收纳到悬浮笔匣（[WriterMinorToolbar]）。
 */
@Composable
internal fun WriterMajorToolbar(
    state: WriterUiState,
    railMode: WriterRailMode?,
    onBack: () -> Unit,
    onToggleRail: () -> Unit,
    onRecord: () -> Unit,
    recording: Boolean,
    onAgent: () -> Unit,
    agentOpen: Boolean,
    agentBusy: Boolean,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onToggleSplit: () -> Unit,
    onEnterFocus: () -> Unit,
    moreExpanded: Boolean,
    onMoreToggle: () -> Unit,
    onMoreDismiss: () -> Unit,
    moreContent: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(WriterMajorToolbarHeight)
            .background(cs.surface)
            .edgeHairline(cs.outline.copy(alpha = 0.65f), bottom = true)
            .semantics { contentDescription = "书写页主工具栏" },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回书架", false, onBack, "writer-back")
            BarIcon(
                Icons.AutoMirrored.Outlined.ViewSidebar,
                if (railMode != null) "收起侧栏" else "展开侧栏",
                railMode != null,
                onToggleRail,
                "writer-page-toggle",
            )

            // 多文档标签条
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.openTabs.forEach { tab ->
                    val selected = tab.id == state.notebook?.id
                    val dotColor = runCatching { Color(android.graphics.Color.parseColor(tab.coverColor)) }
                        .getOrDefault(cs.primary)
                    Row(
                        Modifier
                            .height(30.dp)
                            .widthIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) cs.surfaceVariant else Color.Transparent)
                            .clickable { onSelectTab(tab.id) }
                            .padding(start = 10.dp, end = if (state.openTabs.size > 1) 5.dp else 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(dotColor))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            tab.name,
                            fontSize = 12.5.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            color = if (selected) cs.onBackground else cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.openTabs.size > 1) {
                            Spacer(Modifier.width(3.dp))
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onCloseTab(tab.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "关闭标签",
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
            }

            BarIcon(
                Icons.Outlined.VerticalSplit,
                if (state.splitEnabled) "关闭双开分屏" else "双开分屏",
                state.splitEnabled,
                onToggleSplit,
                "writer-split",
            )
            // 录音胶囊:录制中 = 红色淡底 + 红字
            Row(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (recording) cs.error.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(onClick = onRecord)
                    .testTag("writer-record")
                    .semantics { contentDescription = "录音" }
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = if (recording) cs.error else cs.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                if (recording) {
                    Spacer(Modifier.width(5.dp))
                    Text("录音中", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.error)
                }
            }
            BarIcon(Icons.Outlined.CropFree, "专注模式", false, onEnterFocus, "writer-focus")
            BarIcon(
                Icons.Outlined.AutoAwesome,
                if (agentOpen) "收起右侧面板" else "AI 与伴随笔记",
                agentOpen,
                onAgent,
                "writer-ai",
                badge = agentBusy,
            )
            Box {
                BarIcon(Icons.Outlined.MoreHoriz, "更多", moreExpanded, onMoreToggle, "writer-more")
                MilkAnchoredMenu(expanded = moreExpanded, onDismiss = onMoreDismiss, anchorHeight = WriterMajorToolbarHeight, minWidth = 224.dp) {
                    moreContent()
                }
            }
        }
    }
}

@Composable
private fun BarIcon(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    tag: String,
    badge: Boolean = false,
) {
    Box(
        Modifier
            .size(36.dp, WriterMajorToolbarHeight)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
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
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
            )
        }
    }
}
