package com.milknote.app.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.milknote.agent.AgentUiState
import com.milknote.agent.SessionListItem
import com.milknote.app.ui.common.MilkActionChip
import com.milknote.app.ui.common.MilkConfirmDialog
import com.milknote.app.ui.common.MilkEmptyState
import com.milknote.app.ui.common.MilkHairlineDivider
import com.milknote.app.ui.common.MilkIcons
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.MilkStatusBadge
import com.milknote.app.ui.common.MilkStatusTone
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.formatRelative
import com.milknote.app.ui.common.milkGlassRim
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.milkPopoverEnter

/** 会话范围:只看当前页,或整本笔记 */
private const val SCOPE_PAGE = "page"
private const val SCOPE_NOTEBOOK = "notebook"

/**
 * 历史会话浮层(独立 popover,锚定 AI 教师顶栏):
 * 搜索 + 范围切换 + 新建/切换/删除/手动刷新摘要,每项展示摘要、上下文用量、平均输出速度与元信息。
 *
 * 全部状态由调用方持有([state] 来自 AgentController),本组件只负责筛选与呈现;
 * 删除走 [MilkConfirmDialog],不会误触即丢。
 */
@Composable
fun SessionSwitcherMenu(
    expanded: Boolean,
    state: AgentUiState,
    pageIds: List<String>,
    onDismiss: () -> Unit,
    onSwitchSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onRegenerateSummary: (String) -> Unit,
    anchorHeight: Dp = 40.dp,
) {
    if (!expanded) return

    var query by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(SCOPE_PAGE) }
    var pendingDelete by remember { mutableStateOf<SessionListItem?>(null) }

    val density = LocalDensity.current
    val offsetY = with(density) { anchorHeight.roundToPx() }
    val dark = LocalMilkDark.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val panelWidth = minOf(380.dp, maxOf(340.dp, screenWidth - 32.dp))
    val shape = RoundedCornerShape(MilkRadius.xl)

    val filtered = remember(state.sessions, state.boundPage, scope, query, pageIds) {
        val needle = query.trim()
        state.sessions.filter { item ->
            val inScope = scope == SCOPE_NOTEBOOK || item.pageId == state.boundPage?.pageId
            val hit = needle.isEmpty() ||
                item.summary.contains(needle, true) ||
                item.fallbackPreview.contains(needle, true) ||
                item.modelId.contains(needle, true) ||
                formatPageDisplayLabel(item.pageId, pageIds).contains(needle, true)
            inScope && hit
        }
    }

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(panelWidth)
                .heightIn(max = 520.dp)
                .milkPopoverEnter(TransformOrigin(1f, 0f))
                .milkGlassRim(shape, dark, 14.dp),
        ) {
            // ---- 顶栏:标题 · 计数 · 新会话 · 关闭 ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = MilkSpace.lg, top = MilkSpace.md, end = MilkSpace.xs, bottom = MilkSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("历史会话", style = MilkText.panelTitle, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(MilkSpace.sm))
                MilkStatusBadge(text = "${filtered.size}", tone = MilkStatusTone.NEUTRAL, mono = true)
                Spacer(Modifier.weight(1f))
                MilkActionChip(
                    label = "新会话",
                    onClick = {
                        onNewSession()
                        onDismiss()
                    },
                    small = true,
                    leading = {
                        Icon(
                            MilkIcons.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                Spacer(Modifier.width(MilkSpace.xxs))
                MilkIconButton(
                    imageVector = MilkIcons.Close,
                    contentDescription = "关闭历史会话",
                    onClick = onDismiss,
                    touchSize = 36.dp,
                    visualSize = 30.dp,
                    iconSize = 17.dp,
                )
            }

            MilkHairlineDivider(startIndent = MilkSpace.lg, endIndent = MilkSpace.lg)

            // ---- 范围 + 搜索 ----
            Column(Modifier.padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm)) {
                MilkSegmented(
                    options = listOf(SCOPE_PAGE, SCOPE_NOTEBOOK),
                    selected = scope,
                    label = { if (it == SCOPE_PAGE) "本页" else "整本笔记" },
                    onSelect = { scope = it },
                    compact = true,
                )
                Spacer(Modifier.height(MilkSpace.sm))
                MilkTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索总结、对话或模型…",
                    leading = {
                        Icon(
                            MilkIcons.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                    },
                    onClear = { query = "" },
                )
            }

            // ---- 会话列表 ----
            if (filtered.isEmpty()) {
                MilkEmptyState(
                    title = if (query.isBlank()) "暂无历史会话" else "没有匹配的会话",
                    description = when {
                        query.isNotBlank() -> null
                        scope == SCOPE_PAGE -> "本页开始提问后会自动记录"
                        else -> null
                    },
                )
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(horizontal = MilkSpace.md),
                    verticalArrangement = Arrangement.spacedBy(MilkSpace.sm),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = MilkSpace.md),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        SessionCard(
                            item = item,
                            contextWindowK = contextWindowFor(state, item),
                            showPageLabel = scope == SCOPE_NOTEBOOK,
                            pageIds = pageIds,
                            onSwitch = {
                                onSwitchSession(item.id)
                                onDismiss()
                            },
                            onRegenerate = { onRegenerateSummary(item.id) },
                            onDelete = { pendingDelete = item },
                        )
                    }
                }
            }
        }
    }

    // 确认弹窗挂在 popover 之外(独立 Dialog 窗口):弹窗期间列表状态保持不变
    pendingDelete?.let { target ->
        MilkConfirmDialog(
            title = "删除该会话？",
            body = "「${target.summary.ifBlank { target.fallbackPreview.ifBlank { "空白会话" } }}」及未被其它会话引用的附件将一并删除，无法恢复。",
            confirmLabel = "删除",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                onDeleteSession(target.id)
            },
        )
    }
}

/** 会话卡片:摘要 + 上下文/速度胶囊 + 元信息行 + 刷新/删除 */
@Composable
private fun SessionCard(
    item: SessionListItem,
    contextWindowK: Int,
    showPageLabel: Boolean,
    pageIds: List<String>,
    onSwitch: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(MilkRadius.lg)
    val headline = when {
        item.summary.isNotBlank() -> item.summary
        item.summaryGenerating -> "总结生成中…"
        else -> item.fallbackPreview.ifBlank { "空白会话" }
    }
    val meta = buildString {
        append("${item.metrics.messageCount} 条 · ${formatRelative(item.updatedAt)}")
        if (item.modelId.isNotBlank()) append(" · ${item.modelId}")
        if (showPageLabel) append(" · ${formatPageDisplayLabel(item.pageId, pageIds)}")
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (item.isCurrent) cs.primary.copy(alpha = 0.06f) else cs.surface)
            .border(1.dp, if (item.isCurrent) cs.primary.copy(alpha = 0.45f) else cs.outline, shape)
            .clickable(onClick = onSwitch)
            .padding(start = MilkSpace.md, top = 10.dp, end = MilkSpace.xs, bottom = MilkSpace.sm),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                headline,
                style = MilkText.rowTitle,
                color = cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = MilkSpace.sm),
            )
            if (item.isCurrent) {
                MilkStatusBadge(text = "当前", tone = MilkStatusTone.ACCENT)
                Spacer(Modifier.width(MilkSpace.xs))
            }
        }

        Spacer(Modifier.height(MilkSpace.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs)) {
            MilkStatusBadge(
                text = formatSessionContext(item.metrics.contextTokens, contextWindowK),
                tone = MilkStatusTone.NEUTRAL,
                mono = true,
            )
            MilkStatusBadge(
                text = "速度 ${formatAgentSpeed(item.metrics.avgTokensPerSec)}",
                tone = MilkStatusTone.NEUTRAL,
                mono = true,
            )
        }

        Spacer(Modifier.height(MilkSpace.xxs))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                meta,
                style = MilkText.monoMicro,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MilkIconButton(
                imageVector = MilkIcons.Sparkle,
                contentDescription = "生成总结",
                onClick = onRegenerate,
                enabled = !item.summaryGenerating && item.metrics.messageCount >= 2,
                iconSize = 15.dp,
                visualSize = 28.dp,
                touchSize = 32.dp,
            )
            MilkIconButton(
                imageVector = MilkIcons.Trash,
                contentDescription = "删除会话",
                onClick = onDelete,
                iconSize = 15.dp,
                visualSize = 28.dp,
                touchSize = 32.dp,
            )
        }
    }
}

/** 会话模型 → 上下文窗口(k tok);未知返回 0(只显示已用量) */
private fun contextWindowFor(state: AgentUiState, item: SessionListItem): Int =
    state.profiles
        .firstOrNull { it.id == item.providerId }
        ?.models
        ?.firstOrNull { it.id == item.modelId }
        ?.contextWindowK
        ?: 0
