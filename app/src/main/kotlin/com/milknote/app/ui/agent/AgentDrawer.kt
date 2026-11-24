package com.milknote.app.ui.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.agent.AgentError
import com.milknote.agent.AgentMessage
import com.milknote.agent.AgentUiState
import com.milknote.agent.Intents
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.PillShape
import kotlin.math.abs
import kotlinx.coroutines.launch
import com.milknote.app.ui.cards.cardPreviewText
import com.milknote.app.ui.common.MilkActionChip
import com.milknote.app.ui.common.MilkActionPanel
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkPanelAction
import com.milknote.app.ui.common.MilkRadioRow
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.milkEdgeHairline
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.common.softShadow

/**
 * ⑤ Agent 抽屉:右缘 360dp 浮层(DESIGN-V0 §5.4;AGENT-BACKEND §8)。
 * R2 触控复审:左缘 48dp 命中带 320–480dp 连续调宽、头部右滑关闭(手势只属于抽屉,
 * 不侵入纸面);拖动直写位置,抬起弹簧继承速度(A-35/WWDC24);减弱动效=淡化/即时。
 */
@Composable
fun AgentDrawerHost(
    onOpenSettings: (page: String) -> Unit,
    onSnip: () -> Unit,
    onExplainPage: () -> Unit,
    onSummarizeChapter: () -> Unit,
    modifier: Modifier = Modifier,
    pageLabel: String = "第 1 页",
    pageMarkdown: String = "",
    onMarkdownChange: (String) -> Unit = {},
    onSaveMarkdownCard: () -> Unit = {},
    relatedNotebooks: List<com.milknote.notes.model.NotebookMeta> = emptyList(),
    relatedCards: List<com.milknote.notes.model.NoteCard> = emptyList(),
    onOpenSplitNotebook: (String) -> Unit = {},
    onInsertCard: (com.milknote.notes.model.NoteCard) -> Unit = {},
    onSaveMessageCard: (String) -> Unit = {},
    allNotebooks: List<com.milknote.notes.model.NotebookMeta> = emptyList(),
) {
    val agent = ServiceLocator.agentController
    val state by agent.state.collectAsState()
    BackHandler(enabled = state.drawerOpen) { agent.closeDrawer() }
    val scope = rememberCoroutineScope()
    val reduced = LocalMilkMotion.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // ---- 宽度状态:默认 360dp,可拖 320–480dp(A-35);rawWidth 记录手指原始累计 ----
    val minW = with(density) { 320.dp.toPx() }
    val defaultW = with(density) { 360.dp.toPx() }
    val maxW = with(density) { 480.dp.toPx() }
    val widthElasticity = with(density) { 140.dp.toPx() }
    val widthAnim = remember { Animatable(defaultW) }
    var rawWidth by remember { mutableFloatStateOf(defaultW) }
    // ---- 头部右滑关闭位移(px);rawOffset 允许越界以驱动橡皮筋 ----
    val offsetAnim = remember { Animatable(0f) }
    var rawOffset by remember { mutableFloatStateOf(0f) }
    var swipedClosePending by remember { mutableStateOf(false) }

    fun nearestWidthTier(value: Float): Float =
        listOf(minW, defaultW, maxW).minBy { abs(it - value) }

    val widthDragState = rememberDraggableState { delta ->
        // 抽屉右锚定:左缘向左拖(delta<0)=加宽;越界用 Apple 软边界,永不硬撞墙
        rawWidth += -delta
        val target = when {
            rawWidth < minW -> minW + MilkMotion.rubberband(rawWidth - minW, widthElasticity)
            rawWidth > maxW -> maxW + MilkMotion.rubberband(rawWidth - maxW, widthElasticity)
            else -> rawWidth
        }
        scope.launch { widthAnim.snapTo(target) }
    }
    val headerDragState = rememberDraggableState { delta ->
        rawOffset += delta
        val target = when {
            // 向左拖越过 0:渐进阻尼而不是撞死墙
            rawOffset < 0f -> MilkMotion.rubberband(rawOffset, with(density) { 120.dp.toPx() })
            else -> rawOffset.coerceAtMost(widthAnim.value)
        }
        scope.launch { offsetAnim.snapTo(target) }
    }

    AnimatedVisibility(
        visible = state.drawerOpen,
        enter = if (reduced) fadeIn(tween(MilkMotion.reduced))
        else slideInHorizontally(tween(MilkMotion.drawerEnter, easing = MilkMotion.EaseDrawer)) { it } +
            fadeIn(tween(MilkMotion.enter, easing = MilkMotion.EaseOut)),
        exit = if (swipedClosePending) ExitTransition.None   // 滑关:已随手指离屏,不播退出动画(避免二次位移)
        else if (reduced) fadeOut(tween(MilkMotion.reduced))
        else slideOutHorizontally(tween(MilkMotion.drawerExit, easing = MilkMotion.EaseDrawer)) { it } +
            fadeOut(tween(MilkMotion.exit, easing = MilkMotion.EaseOut)),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxHeight()) {
            AgentDrawer(
                state = state,
                widthPx = widthAnim.value,
                dragOffsetPx = offsetAnim.value,
                onOpenSettings = onOpenSettings,
                onSnip = onSnip,
                onExplainPage = onExplainPage,
                onSummarizeChapter = onSummarizeChapter,
                pageLabel = pageLabel,
                pageMarkdown = pageMarkdown,
                onMarkdownChange = onMarkdownChange,
                onSaveMarkdownCard = onSaveMarkdownCard,
                relatedNotebooks = relatedNotebooks,
                relatedCards = relatedCards,
                onOpenSplitNotebook = onOpenSplitNotebook,
                onInsertCard = onInsertCard,
                onSaveMessageCard = onSaveMessageCard,
                allNotebooks = allNotebooks,
                headerDragModifier = Modifier.draggable(
                    orientation = Orientation.Horizontal,
                    state = headerDragState,
                    onDragStarted = { rawOffset = offsetAnim.value },
                    onDragStopped = { velocity ->
                        // 苹果动量投影:松手点 + 继续滑行距离;满足任一条件即随速度滑出
                        val projected = rawOffset + MilkMotion.project(velocity, 0.995f)
                        val closeByProjection = projected > widthAnim.value * 0.45f
                        val closeByDistance = rawOffset > widthAnim.value * 0.4f
                        val closeByVelocity = velocity > with(density) { 1100.dp.toPx() }
                        if (closeByProjection || closeByDistance || closeByVelocity) {
                            scope.launch {
                                if (reduced) offsetAnim.snapTo(widthAnim.value)
                                else offsetAnim.animateTo(
                                    widthAnim.value,
                                    MilkMotion.uiSpring(),
                                    initialVelocity = velocity,
                                )
                                swipedClosePending = true
                                agent.closeDrawer()
                                offsetAnim.snapTo(0f)   // 已不可见,复位不影响视觉
                                rawOffset = 0f
                                swipedClosePending = false
                            }
                        } else {
                            scope.launch {
                                if (reduced) {
                                    offsetAnim.snapTo(0f)
                                    rawOffset = 0f
                                } else {
                                    offsetAnim.animateTo(
                                        0f,
                                        MilkMotion.momentumSpring(),
                                        initialVelocity = velocity,
                                    )
                                    rawOffset = 0f
                                }
                            }
                        }
                    },
                ),
            )
            // 左缘 48dp 透明命中带:拖动调宽(手势归属抽屉,不碰纸面)
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(48.dp)
                    .fillMaxHeight()
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = widthDragState,
                        onDragStarted = { rawWidth = widthAnim.value },
                        onDragStopped = { velocity ->
                            // 松手位置 + 动量投影 → 最近档位;换档给一次轻触感
                            val projected = widthAnim.value + MilkMotion.project(-velocity, 0.992f)
                            val settle = nearestWidthTier(projected)
                            if (nearestWidthTier(widthAnim.value) != settle) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            scope.launch {
                                if (reduced) widthAnim.snapTo(settle)
                                else widthAnim.animateTo(
                                    settle,
                                    MilkMotion.momentumSpring(),
                                    initialVelocity = -velocity,
                                )
                            }
                            rawWidth = settle
                        },
                    ),
            )
        }
    }
}

@Composable
private fun AgentDrawer(
    state: AgentUiState,
    widthPx: Float,
    dragOffsetPx: Float,
    onOpenSettings: (page: String) -> Unit,
    onSnip: () -> Unit,
    onExplainPage: () -> Unit,
    onSummarizeChapter: () -> Unit,
    pageLabel: String = "第 1 页",
    pageMarkdown: String = "",
    onMarkdownChange: (String) -> Unit = {},
    onSaveMarkdownCard: () -> Unit = {},
    relatedNotebooks: List<com.milknote.notes.model.NotebookMeta> = emptyList(),
    relatedCards: List<com.milknote.notes.model.NoteCard> = emptyList(),
    onOpenSplitNotebook: (String) -> Unit = {},
    onInsertCard: (com.milknote.notes.model.NoteCard) -> Unit = {},
    onSaveMessageCard: (String) -> Unit = {},
    allNotebooks: List<com.milknote.notes.model.NotebookMeta> = emptyList(),
    headerDragModifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var studioTab by remember { mutableStateOf("ai") }
    Column(
        Modifier
            .width(with(density) { widthPx.toDp() })
            .fillMaxHeight()
            .graphicsLayer { translationX = dragOffsetPx }
            .softShadow(12.dp, RectangleShape)
            .background(MaterialTheme.colorScheme.surface)
            .milkEdgeHairline(MaterialTheme.colorScheme.outline, left = true),
    ) {
        DrawerHeader(
            state = state,
            studioTab = studioTab,
            onSelectTab = { studioTab = it },
            headerDragModifier = headerDragModifier,
            onOpenSettings = onOpenSettings,
        )
        when (studioTab) {
            "md" -> StudioMarkdownPane(
                pageLabel = pageLabel,
                markdown = pageMarkdown,
                onMarkdownChange = onMarkdownChange,
                onSaveCard = onSaveMarkdownCard,
                allNotebooks = allNotebooks,
                onOpenSplit = onOpenSplitNotebook,
                modifier = Modifier.weight(1f),
            )
            "links" -> StudioLinksPane(
                notebooks = relatedNotebooks,
                cards = relatedCards,
                onOpenSplit = onOpenSplitNotebook,
                onInsertCard = onInsertCard,
                modifier = Modifier.weight(1f),
            )
            else -> {
                if (state.setupRequired) {
                    SetupGuide(onOpenSettings)
                } else {
                    // 空会话保留输入区，快捷动作位于 composer 上方。
                    if (state.messages.isNotEmpty()) {
                        Messages(state, Modifier.weight(1f), onSaveCard = onSaveMessageCard)
                    } else {
                        Box(Modifier.weight(1f))
                    }
                    Composer(
                        state = state,
                        showQuickActions = state.messages.isEmpty(),
                        onSnip = onSnip,
                        onExplainPage = onExplainPage,
                        onSummarizeChapter = onSummarizeChapter,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioMarkdownPane(
    pageLabel: String,
    markdown: String,
    onMarkdownChange: (String) -> Unit,
    onSaveCard: () -> Unit,
    allNotebooks: List<com.milknote.notes.model.NotebookMeta> = emptyList(),
    onOpenSplit: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var editSource by remember { mutableStateOf(false) }
    // [[线性代数讲义.pdf#p14]] → 按名称匹配笔记本(忽略 .pdf / #页码 后缀),命中则双开分屏
    val openWikilink: (String) -> Unit = { raw ->
        val key = raw.substringBefore('#').removeSuffix(".pdf").trim()
        allNotebooks.firstOrNull { it.name.removeSuffix(".pdf").equals(key, ignoreCase = true) }
            ?.let { onOpenSplit(it.id) }
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .milkEdgeHairline(cs.outline, bottom = true)
                .padding(horizontal = MilkSpace.md + 2.dp, vertical = MilkSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pageLabel,
                style = MilkText.monoMeta,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            MilkActionChip(
                label = if (editSource) "预览" else "源码",
                onClick = { editSource = !editSource },
                small = true,
            )
            Spacer(Modifier.width(6.dp))
            MilkActionChip(
                label = "存卡片",
                enabled = markdown.isNotBlank(),
                onClick = onSaveCard,
                small = true,
            )
        }

        if (editSource) {
            BasicTextField(
                value = markdown,
                onValueChange = onMarkdownChange,
                textStyle = MilkText.monoBody.copy(color = cs.onSurface),
                cursorBrush = SolidColor(cs.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (markdown.isBlank()) {
                            Text("记录本页要点…", color = cs.onSurfaceVariant, style = MilkText.monoBody)
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(MilkSpace.lg - 2.dp),
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .clickable { editSource = true }
                    .padding(MilkSpace.lg - 2.dp),
            ) {
                if (markdown.isBlank()) {
                    Text("记录本页要点…", color = cs.onSurfaceVariant, style = MilkText.body)
                } else {
                    MarkdownBlocks(markdown, onWikilink = openWikilink)
                }
            }
        }
    }
}

@Composable
private fun StudioLinksPane(
    notebooks: List<com.milknote.notes.model.NotebookMeta>,
    cards: List<com.milknote.notes.model.NoteCard>,
    onOpenSplit: (String) -> Unit,
    onInsertCard: (com.milknote.notes.model.NoteCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(MilkSpace.md),
        verticalArrangement = Arrangement.spacedBy(MilkSpace.xs),
    ) {
        Text(
            "双开对照与反向链接",
            style = MilkText.eyebrow,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MilkSpace.sm, vertical = 2.dp),
        )
        if (notebooks.isEmpty()) {
            Text(
                "书架中有其他笔记本时，点击即可在右侧双开分屏对照",
                style = MilkText.caption,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MilkSpace.sm, vertical = MilkSpace.xxs),
            )
        } else {
            notebooks.take(5).forEach { nb ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MilkRadius.sm))
                        .clickable { onOpenSplit(nb.id) }
                        .padding(horizontal = MilkSpace.sm + 2.dp, vertical = MilkSpace.sm),
                ) {
                    Text(nb.name, style = MilkText.rowTitle, color = cs.onSurface)
                    Text(
                        "点击双开分屏对照${if (nb.folder.isBlank()) "" else " · ${nb.folder}"}",
                        style = MilkText.caption,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(MilkSpace.xs))
        Text(
            "知识卡片",
            style = MilkText.eyebrow,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MilkSpace.sm, vertical = 2.dp),
        )
        if (cards.isEmpty()) {
            Text(
                "框选或伴随笔记存为卡片后，可在此一键贴回当前页",
                style = MilkText.caption,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MilkSpace.sm, vertical = MilkSpace.xxs),
            )
        } else {
            cards.take(8).forEach { card ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MilkRadius.sm))
                        .clickable { onInsertCard(card) }
                        .padding(horizontal = MilkSpace.sm + 2.dp, vertical = MilkSpace.sm),
                ) {
                    Text(
                        card.title.ifBlank { "无标题卡片" },
                        style = MilkText.rowTitle,
                        color = cs.primary,
                    )
                    Text(
                        cardPreviewText(card.title, card.body).ifBlank { "点击贴回当前页" },
                        style = MilkText.caption,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(
    state: AgentUiState,
    studioTab: String = "ai",
    onSelectTab: (String) -> Unit = {},
    headerDragModifier: Modifier = Modifier,
    onOpenSettings: (page: String) -> Unit = {},
) {
    val agent = ServiceLocator.agentController
    val cs = MaterialTheme.colorScheme
    var showModels by remember { mutableStateOf(false) }
    val tabs = remember { listOf("ai" to "AI", "md" to "Markdown", "links" to "关联") }
    Column(
        Modifier
            .fillMaxWidth()
            .background(cs.surface)
            .milkEdgeHairline(cs.outline, bottom = true)
            .then(headerDragModifier),
    ) {
        // 第二代右侧 Studio 顶栏:三段标签切换 (AI | Markdown | 关联) + 关闭钮
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MilkSegmented(
                options = tabs,
                selected = tabs.firstOrNull { it.first == studioTab },
                label = { it.second },
                onSelect = { onSelectTab(it.first) },
                modifier = Modifier.weight(1f),
                compact = true,
            )
            Spacer(Modifier.width(MilkSpace.xs))
            MilkIconButton(
                imageVector = Icons.Outlined.Close,
                contentDescription = "收起",
                onClick = { agent.closeDrawer() },
                visualSize = 32.dp,
                iconSize = 16.dp,
            )
        }
        // AI 页签下显示当前视觉模型与思考档位胶囊
        if (studioTab == "ai" && !state.setupRequired) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .border(1.dp, cs.outline, CircleShape)
                        .clickable { showModels = !showModels }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.activeModelId ?: "未配置视觉模型",
                        style = MilkText.monoBody,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                    )
                    state.profiles.firstOrNull { it.id == state.activeProfileId }?.let { pf ->
                        val eff = runCatching { com.milknote.agent.ReasoningEffort.valueOf(pf.reasoningEffort) }
                            .getOrDefault(com.milknote.agent.ReasoningEffort.AUTO)
                        if (eff != com.milknote.agent.ReasoningEffort.AUTO) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "思考·${effortLabel(eff)}",
                                style = MilkText.monoMeta,
                                color = cs.primary,
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (showModels) {
        MilkCenterPanel(
            title = "模型与思考",
            subtitle = "仅显示已声明支持视觉输入的模型",
            onDismiss = { showModels = false },
        ) {
            ModelSheet(
                state,
                onDismiss = { showModels = false },
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@Composable
private fun SetupGuide(onOpenSettings: (page: String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("尚未设置模型", style = MilkText.panelTitle, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(MilkSpace.sm))
        Text(
            "设置模型后，AI 教师即可讲解页面、归纳章节和回答提问。",
            style = MilkText.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(MilkSpace.lg + 2.dp))
        MilkButton("设置模型", onClick = { onOpenSettings("agent") })
    }
}

// ---------------- 消息区(§8.4 流式渲染) ----------------

@Composable
private fun Messages(state: AgentUiState, modifier: Modifier = Modifier, onSaveCard: (String) -> Unit = {}) {
    val listState = rememberLazyListState()
    val agent = ServiceLocator.agentController
    val scope = rememberCoroutineScope()

    // 贴底跟随;用户上滑即解除(A-20)
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            last == null || last.index >= listState.layoutInfo.totalItemsCount - 2
        }
    }
    // A-20:新消息(条数变化)→ 动画滚动一次;同一条流式追加 → 直接贴底,不反复起动画
    var prevSize by remember { mutableStateOf(-1) }
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        val n = state.messages.size
        val animate = n != prevSize
        prevSize = n
        if (atBottom && n > 0) {
            if (animate) listState.animateScrollToItem(n - 1)
            else listState.scrollToItem(n - 1)
        }
    }

    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // composer 位于正常流布局下方,仅保留常规内容间距(A-22,原 96dp 预留与布局重复)
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                when (msg.role) {
                    AgentMessage.Role.USER -> UserMessage(msg)
                    AgentMessage.Role.ASSISTANT -> AssistantMessage(state, msg, onSaveCard)
                }
            }
            if (state.error != null) {
                item(key = "err") { ErrorBanner(state.error!!) }
            }
        }
        // 流式中用户上滑后:显示「回到底部」(A-20)
        if (!atBottom && state.messages.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable {
                        scope.launch { listState.animateScrollToItem(state.messages.size - 1) }
                    }
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("回到底部", style = MilkText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 思考过程折叠(DESIGN-V2 msg-think):默认收起,矢量箭头 + mono 灰字「思考过程 · N 字」,展开后无底色纯文本 */
@Composable
private fun ReasoningBlock(msg: com.milknote.agent.UiMessage) {
    var expanded by remember(msg.id) { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val streamingThought = msg.streaming && msg.text.isBlank()
    Column(Modifier.fillMaxWidth().animateContentSize(tween(MilkMotion.enter))) {
        Row(
            Modifier
                .clip(RoundedCornerShape(MilkRadius.xs))
                .clickable { expanded = !expanded }
                .defaultMinSize(minHeight = 32.dp)
                .padding(horizontal = MilkSpace.xxs, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (streamingThought) Icons.Outlined.AutoAwesome
                else if (expanded) Icons.Outlined.KeyboardArrowDown
                else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = cs.onSurfaceVariant,
            )
            Spacer(Modifier.width(MilkSpace.xxs))
            Text(
                if (streamingThought) "思考中…" else "思考过程 · ${msg.reasoning.length} 字",
                style = MilkText.monoMeta,
                color = cs.onSurfaceVariant,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(MilkSpace.xxs))
            Text(
                msg.reasoning,
                style = MilkText.monoMeta,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MilkSpace.xxs),
            )
        }
    }
}

@Composable
private fun UserMessage(msg: com.milknote.agent.UiMessage) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        // 附件卡:逻辑名 + 尺寸 mono(§1.2)
        msg.attachments.forEach { att ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(MilkRadius.md))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = MilkSpace.sm + 2.dp, vertical = MilkSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Description, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(att.logicalName, style = MilkText.monoMeta, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${att.widthPx}×${att.heightPx}px · 已含手写层与批注",
                        style = MilkText.micro,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (msg.text.isNotBlank()) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(MilkRadius.lg))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm),
            ) {
                Text(msg.text, style = MilkText.body, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun AssistantMessage(state: AgentUiState, msg: com.milknote.agent.UiMessage, onSaveCard: (String) -> Unit) {
    val agent = ServiceLocator.agentController
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        // 思考过程:默认折叠;流式中(答案未出)显示「思考中…」,完成后显示字数
        if (msg.reasoning.isNotBlank()) {
            ReasoningBlock(msg)
            Spacer(Modifier.height(6.dp))
        }
        MarkdownBlocks(msg.text + if (msg.streaming) " ▍" else "")
        Spacer(Modifier.height(6.dp))
        // meta 行:模型 · tokens(mono 小字)
        val meta = buildString {
            append(msg.modelId ?: "")
            if (msg.promptTokens > 0 || msg.completionTokens > 0) {
                append(" · ${(msg.promptTokens / 100.0).let { if (it >= 10) it.toInt() else String.format("%.1f", it).toFloat() }}k→")
                append("${(msg.completionTokens / 100.0).let { if (it >= 10) it.toInt() else String.format("%.1f", it).toFloat() }}k tok")
            }
        }
        if (meta.isNotBlank()) {
            Text(meta, style = MilkText.monoMeta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
        }
        // 操作 chips(DESIGN-V2 msg-bar):贴回纸面 / 存卡片 / 复制
        when (msg.phase) {
            AgentMessage.Phase.STREAMING -> {}
            AgentMessage.Phase.FAILED -> ActionChip("重试", accent = true) { agent.retry() }
            AgentMessage.Phase.INTERRUPTED -> Row(horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs)) {
                Text("已中断", style = MilkText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionChip("重试", accent = true) { agent.retry() }
            }
            AgentMessage.Phase.DONE -> Row(horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs)) {
                if (msg.insertedAnnotationId == null) {
                    ActionChip("贴回纸面", accent = true) { agent.insertAnnotation(msg.id) }
                } else {
                    // 已贴回是状态不是操作:静态胶囊,不注册点击
                    StaticChip("已贴回 ✓")
                }
                ActionChip("存卡片") { onSaveCard(msg.text) }
                ActionChip("复制") {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("agent", msg.text))
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    accent: Boolean = false,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    MilkActionChip(
        label = label,
        selected = accent,
        enabled = enabled,
        onClick = onClick,
        small = true,
        leading = icon?.let { { Icon(it, null, modifier = Modifier.size(14.dp)) } },
    )
}

/** 状态展示胶囊(无点击语义):与 MilkChip 同尺寸/同底色,但不可点 */
@Composable
private fun StaticChip(label: String) {
    Row(
        Modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outline, PillShape)
            .defaultMinSize(minHeight = 34.dp)
            .padding(horizontal = MilkSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MilkText.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorBanner(error: AgentError) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MilkRadius.md))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            .padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(errorTitle(error), style = MilkText.label, color = MaterialTheme.colorScheme.error)
            Text(error.detail, style = MilkText.micro, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

private fun errorTitle(e: AgentError): String = when (e) {
    is AgentError.Auth -> "Key 无效或额度不足"
    is AgentError.RateLimit -> "请求过快,已自动退避"
    is AgentError.Server -> "服务方故障"
    is AgentError.Network -> "网络不可用"
    is AgentError.Timeout -> "网络超时"
    is AgentError.ModelNotVision -> "当前模型疑似不支持图片输入,请更换视觉模型"
    is AgentError.NotFound -> "模型或端点不存在(可能已下线)"
    is AgentError.ContextTooLarge -> "上下文过大,请减少页数"
    is AgentError.StreamBroken -> "流式传输中断"
    is AgentError.NoProvider, is AgentError.NotVision -> "未配置视觉模型"
    else -> "请求失败"
}

// ---------------- 输入区(追问 / slash) ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Composer(
    state: AgentUiState,
    showQuickActions: Boolean,
    onSnip: () -> Unit,
    onExplainPage: () -> Unit,
    onSummarizeChapter: () -> Unit,
) {
    val agent = ServiceLocator.agentController
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    var showAttach by remember { mutableStateOf(false) }
    var composerFocused by remember { mutableStateOf(false) }
    val streaming = state.phase == AgentMessage.Phase.STREAMING || state.phase == AgentMessage.Phase.SENDING
    // 无文字且无附件 → 发送禁用(hairline 灰底弱图标、disabled 语义),不产生空请求
    val canSend = text.isNotBlank() || state.draftAttachments.isNotEmpty()

    // slash 面板(§6.4:前缀匹配六意图 + /清空)
    val slashSuggestions = if (text.startsWith("/")) {
        listOfNotNull(
            *Intents.all.filter { "/${it.label}".startsWith(text) || text == "/" }
                .map { "/${it.label}" to it.id }.toTypedArray(),
            if ("/清空".startsWith(text)) "/清空" to null else null,
        )
    } else emptyList()
    var dismissedSlash by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth()) {
        // 快捷动作(仅空会话时显示)
        if (showQuickActions) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
                verticalArrangement = Arrangement.spacedBy(MilkSpace.xs),
            ) {
                MilkActionChip(label = "框选题目", onClick = onSnip, small = true)
                MilkActionChip(label = "讲解本页", onClick = onExplainPage, small = true)
                MilkActionChip(label = "归纳本章", onClick = onSummarizeChapter, small = true)
            }
        }
        // 附件卡片行:挂在输入框上方,✕ 可移除,随下一条消息发出
        if (state.draftAttachments.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = MilkSpace.md),
                horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
            ) {
                state.draftAttachments.forEach { d ->
                    DraftAttachmentCard(d) { agent.removeDraftAttachment(d.id) }
                }
            }
            Text(
                "${state.draftAttachments.size} 个附件 · ≈${agent.estTokensNow()} tok",
                style = MilkText.monoMeta,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MilkSpace.md, vertical = MilkSpace.xxs),
            )
        }
        if (slashSuggestions.isNotEmpty() && dismissedSlash != text) {
            MilkActionPanel(
                title = "命令",
                actions = slashSuggestions.take(7).map { (label, intent) ->
                    MilkPanelAction(
                        label = label,
                        onClick = {
                            text = ""
                            dismissedSlash = null
                            if (intent != null) agent.sendWithIntent(intent) else agent.clearSession()
                        },
                    )
                },
                onDismiss = { dismissedSlash = text },
            )
        }
        val focusBorder by animateColorAsState(
            if (composerFocused) cs.primary else cs.outline.copy(alpha = 0.55f),
            MilkMotion.colorSpec(), label = "composerBorder",
        )
        val focusWidth by animateDpAsState(
            if (composerFocused) 1.5.dp else 1.dp,
            MilkMotion.colorSpec(), label = "composerBorderWidth",
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm + 2.dp)
                .clip(RoundedCornerShape(MilkRadius.lg))
                .background(cs.surfaceVariant.copy(alpha = 0.6f))
                .border(focusWidth, focusBorder, RoundedCornerShape(MilkRadius.lg))
                .padding(horizontal = MilkSpace.xs, vertical = MilkSpace.xxs),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 回形针:附件面板
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { showAttach = !showAttach },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.AttachFile,
                    if (showAttach) "收起附件面板" else "附件",
                    modifier = Modifier.size(17.dp),
                    tint = if (showAttach) cs.primary else cs.onSurfaceVariant,
                )
            }
            Box(Modifier.weight(1f).padding(top = 7.dp, bottom = 7.dp)) {
                if (text.isEmpty()) {
                    Text("提问…", style = MilkText.body, color = cs.onSurfaceVariant.copy(alpha = 0.6f))
                }
                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        dismissedSlash = null
                    },
                    textStyle = MilkText.body.copy(color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { composerFocused = it.isFocused },
                )
            }
            Spacer(Modifier.width(4.dp))
            // 发送 / 停止:32dp 圆点;不可发送 = hairline 灰底灰图标
            if (streaming) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(cs.surfaceVariant)
                        .clickable { agent.stop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Stop, "停止", modifier = Modifier.size(14.dp), tint = cs.error)
                }
            } else {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (canSend) cs.primary else cs.outline)
                        .clickable(enabled = canSend) {
                            agent.setDraftText(text)
                            agent.send()
                            text = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send, "发送",
                        modifier = Modifier.size(14.dp),
                        tint = if (canSend) cs.onPrimary else cs.surface,
                    )
                }
            }
        }
    }
    if (showAttach) {
        MilkCenterPanel(
            title = "附件",
            subtitle = "可附加当前页、整章或本笔记本的任意页面",
            onDismiss = { showAttach = false },
        ) {
            AttachmentPanel(
                boundPage = state.boundPage,
                onPickPage = { ref -> scope.launch { agent.addPageAttachment(ref) } },
                onPickCurrent = {
                    state.boundPage?.let { ref -> scope.launch { agent.addPageAttachment(ref) } }
                },
                onPickChapter = {
                    scope.launch {
                        val n = agent.addChapterAttachments()
                        if (n == 0) agent.setDraftText(state.draftText)
                    }
                },
            )
        }
    }
}

/** 📎 附件面板:当前页 / 整章 / 本笔记本任意页(可多点累积;中央面板内容) */
@Composable
private fun AttachmentPanel(
    boundPage: com.milknote.agent.PageRef?,
    onPickPage: (com.milknote.agent.PageRef) -> Unit,
    onPickCurrent: () -> Unit,
    onPickChapter: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pages by remember { mutableStateOf<List<com.milknote.agent.PageRef>>(emptyList()) }
    LaunchedEffect(boundPage?.notebookId) {
        boundPage?.let {
            pages = runCatching { ServiceLocator.agentSnipSource.pagesOf(it.notebookId) }.getOrDefault(emptyList())
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = MilkSpace.md)) {
        // 快捷:当前页 / 整章
        Row(horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs)) {
            AttachPick("当前页 ${boundPage?.pageId ?: ""}") { onPickCurrent() }
            AttachPick("整章 · 其余 ${maxOf(pages.size - 1, 0)} 页") { onPickChapter() }
        }
        Spacer(Modifier.height(MilkSpace.lg - 2.dp))
        Text(
            "本笔记本页面(点按附加,可多选)",
            style = MilkText.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MilkSpace.xs))
        // 页列表(排除当前页——上面已有快捷);超限内部滚动(A-18)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            pages.filter { it.pageId != boundPage?.pageId }.forEach { pg ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MilkRadius.md))
                        .clickable { onPickPage(pg) }
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = MilkSpace.sm + 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(pg.pageId, style = MilkText.monoBody, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(2.dp))
                    Text("附加", style = MilkText.label, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun AttachPick(label: String, onClick: () -> Unit) {
    MilkActionChip(label = label, onClick = onClick)
}

/** 草稿附件卡片(logicalName + 尺寸 + ✕ 移除;移除命中扩大) */
@Composable
private fun DraftAttachmentCard(d: com.milknote.agent.DraftAttachment, onRemove: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(MilkRadius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(MilkRadius.md))
            .padding(start = MilkSpace.sm + 2.dp, end = MilkSpace.xxs, top = MilkSpace.xs, bottom = MilkSpace.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Description, null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(d.logicalName, style = MilkText.monoMeta, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, "移除附件", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "${d.widthPx}×${d.heightPx}px" + if (d.source == com.milknote.agent.Attachment.Source.SNIP) " · 选区" else "",
            style = MilkText.micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = MilkSpace.xs),
        )
    }
}

// ---------------- 模型切换 sheet(仅视觉) ----------------

@Composable
private fun ModelSheet(
    state: AgentUiState,
    onDismiss: () -> Unit,
    onOpenSettings: (page: String) -> Unit,
) {
    val agent = ServiceLocator.agentController
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        // 模型列表:超限内部滚动(A-18,防多 Provider 长列表撑爆抽屉);行 = 共享单选行
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            state.profiles.forEach { profile ->
                Text(
                    profile.title,
                    style = MilkText.eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = MilkSpace.md, top = MilkSpace.sm + 2.dp, bottom = MilkSpace.xxs),
                )
                profile.models.filter { it.vision }.forEach { m ->
                    val active = profile.id == state.activeProfileId && m.id == state.activeModelId
                    MilkRadioRow(
                        selected = active,
                        label = m.id,
                        onClick = {
                            agent.selectModel(profile.id, m.id)
                            onDismiss()
                        },
                    )
                }
            }
        }
        // ---- 思考强度(§3.8:每家档位不同,按当前模型声明显示) ----
        val activeProfile = state.profiles.firstOrNull { it.id == state.activeProfileId }
        val activeModel = activeProfile?.activeModel()
        val declared = activeModel?.thinkingLevels
            ?.mapNotNull { runCatching { com.milknote.agent.ReasoningEffort.valueOf(it) }.getOrNull() }
            ?: listOf(
                // 自定义模型未声明:按 OpenAI 标准给通用四档
                com.milknote.agent.ReasoningEffort.OFF,
                com.milknote.agent.ReasoningEffort.LOW,
                com.milknote.agent.ReasoningEffort.MEDIUM,
                com.milknote.agent.ReasoningEffort.HIGH,
            )
        val current = runCatching { com.milknote.agent.ReasoningEffort.valueOf(activeProfile?.reasoningEffort ?: "AUTO") }
            .getOrDefault(com.milknote.agent.ReasoningEffort.AUTO)
        Spacer(Modifier.height(16.dp))
        Text(
            "思考强度 · ${activeModel?.id ?: state.activeModelId ?: ""}",
            style = MilkText.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MilkSpace.md),
        )
        Spacer(Modifier.height(MilkSpace.sm))
        Row(
            Modifier
                .padding(horizontal = MilkSpace.md)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
        ) {
            EffortChip("自动", current == com.milknote.agent.ReasoningEffort.AUTO) {
                agent.setReasoningEffort(com.milknote.agent.ReasoningEffort.AUTO)
            }
            declared.forEach { level ->
                EffortChip(effortLabel(level), current == level) {
                    agent.setReasoningEffort(level)
                }
            }
        }
        Spacer(Modifier.height(MilkSpace.sm))
        Text(
            "档位映射因厂商而异;自动=不发参数。完整说明见 设置 · Agent。",
            style = MilkText.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MilkSpace.md),
        )
        Spacer(Modifier.height(MilkSpace.sm))
        // A-17:真正进入 设置 · Agent(此前只关面板不导航)
        Row(
            Modifier
                .padding(horizontal = MilkSpace.md, vertical = MilkSpace.xxs)
                .clip(RoundedCornerShape(MilkRadius.md))
                .softClickable {
                    onDismiss()
                    onOpenSettings("agent")
                }
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = MilkSpace.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Settings, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(MilkSpace.sm + 2.dp))
            Text(
                "管理模型 → 设置 · Agent",
                style = MilkText.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun effortLabel(e: com.milknote.agent.ReasoningEffort): String = when (e) {
    com.milknote.agent.ReasoningEffort.AUTO -> "自动"
    com.milknote.agent.ReasoningEffort.OFF -> "关"
    com.milknote.agent.ReasoningEffort.MINIMAL -> "最低"
    com.milknote.agent.ReasoningEffort.LOW -> "低"
    com.milknote.agent.ReasoningEffort.MEDIUM -> "中"
    com.milknote.agent.ReasoningEffort.HIGH -> "高"
    com.milknote.agent.ReasoningEffort.XHIGH -> "超高"
    com.milknote.agent.ReasoningEffort.MAX -> "最高"
}

@Composable
private fun EffortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    MilkChip(label = label, selected = selected, onClick = onClick, small = true)
}

// ---------------- Markdown 渲染(§6.3 容错子集) ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkdownBlocks(text: String, modifier: Modifier = Modifier, onWikilink: ((String) -> Unit)? = null) {
    val blocks = remember(text) { MarkdownLite.parse(text) }
    val linkColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { i, b ->
            when (b) {
                is MarkdownLite.Block.Heading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    InlineText(b.spans, linkColor, onWikilink, fontWeight = FontWeight.SemiBold, fontSize = (18 - b.level * 2).coerceIn(11, 16).sp)
                }
                is MarkdownLite.Block.ListItem -> Row {
                    Text(
                        b.order?.let { "$it. " } ?: "•  ",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(22.dp),
                    )
                    InlineText(b.spans, linkColor, onWikilink, fontSize = 13.sp, lineHeight = 21.sp)
                }
                is MarkdownLite.Block.Paragraph -> InlineText(b.spans, linkColor, onWikilink, fontSize = 13.5.sp, lineHeight = 22.sp)
                is MarkdownLite.Block.MathBlock -> Box(
                    Modifier
                        .padding(vertical = 3.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MilkRadius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = MilkSpace.sm + 2.dp, vertical = MilkSpace.xs),
                ) {
                    Text(b.latex, style = MilkText.monoBody, color = MaterialTheme.colorScheme.onSurface)
                }
                is MarkdownLite.Block.TagLine -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
                    verticalArrangement = Arrangement.spacedBy(MilkSpace.xxs),
                ) {
                    b.tags.take(8).forEach { tag ->
                        Text(
                            "#$tag",
                            style = MilkText.caption,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (i != blocks.lastIndex) Spacer(Modifier.height(4.dp))
        }
    }
}

/** 行内富文本:[[双链]] 渲染为强调色下划线,提供 onWikilink 时可点击跳转 */
@Composable
private fun InlineText(
    spans: List<MarkdownLite.Span>,
    linkColor: Color,
    onWikilink: ((String) -> Unit)?,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
) {
    val codeBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val annotated = remember(spans, linkColor, codeBg) { spansToAnnotated(spans, linkColor, codeBg) }
    val style = androidx.compose.ui.text.TextStyle(fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight)
    if (onWikilink != null && annotated.getStringAnnotations("wiki", 0, annotated.length).isNotEmpty()) {
        androidx.compose.foundation.text.ClickableText(
            text = annotated,
            style = style.copy(color = MaterialTheme.colorScheme.onSurface),
            onClick = { offset ->
                annotated.getStringAnnotations("wiki", offset, offset).firstOrNull()?.let { onWikilink(it.item) }
            },
        )
    } else {
        Text(annotated, style = style.copy(color = MaterialTheme.colorScheme.onSurface))
    }
}

private val wikilinkRegex = Regex("\\[\\[([^\\]]+)\\]\\]")

private fun spansToAnnotated(spans: List<MarkdownLite.Span>, linkColor: Color, codeBg: Color): AnnotatedString {
    val builder = AnnotatedString.Builder()
    for (s in spans) {
        when (s) {
            is MarkdownLite.Span.Plain -> {
                // [[双链]] 在纯文本段内切分为可点击链接
                var last = 0
                wikilinkRegex.findAll(s.text).forEach { m ->
                    if (m.range.first > last) builder.append(s.text.substring(last, m.range.first))
                    builder.pushStyle(SpanStyle(color = linkColor, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
                    builder.pushStringAnnotation("wiki", m.groupValues[1])
                    builder.append(m.groupValues[1])
                    builder.pop()
                    builder.pop()
                    last = m.range.last + 1
                }
                if (last < s.text.length) builder.append(s.text.substring(last))
            }
            is MarkdownLite.Span.Bold -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                builder.append(s.text)
                builder.pop()
            }
            is MarkdownLite.Span.Code -> {
                builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg))
                builder.append(s.text)
                builder.pop()
            }
            is MarkdownLite.Span.Math -> {
                builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                builder.append(s.latex)
                builder.pop()
            }
        }
    }
    return builder.toAnnotatedString()
}
