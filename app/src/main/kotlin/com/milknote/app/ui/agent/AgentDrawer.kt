package com.milknote.app.ui.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.agent.AgentError
import com.milknote.agent.AgentMessage
import com.milknote.agent.AgentUiState
import com.milknote.agent.Attachment
import com.milknote.agent.ImageBudget
import com.milknote.agent.Intents
import com.milknote.agent.ReasoningEffort
import com.milknote.agent.UiMessage
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.cards.cardPreviewText
import com.milknote.app.ui.common.MilkActionChip
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkEmptyState
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkRadioRow
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.MilkStatusBadge
import com.milknote.app.ui.common.MilkStatusTone
import com.milknote.app.ui.common.milkEdgeHairline
import com.milknote.app.ui.common.milkGlassRim
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.LocalMilkSuccess
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.PaperBorder
import com.milknote.app.ui.theme.PillShape
import com.milknote.app.ui.theme.SurfaceElevatedDark
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val cs = MaterialTheme.colorScheme

    // ---- 宽度状态:默认 360dp,可拖 320–480dp(A-35);rawWidth 记录手指原始累计 ----
    val minW = with(density) { 320.dp.toPx() }
    val defaultW = with(density) { 360.dp.toPx() }
    val maxW = with(density) { 480.dp.toPx() }
    val widthElasticity = with(density) { 140.dp.toPx() }
    val widthAnim = remember { Animatable(defaultW) }
    var rawWidth by remember { mutableFloatStateOf(defaultW) }
    var draggingWidth by remember { mutableStateOf(false) }
    // ---- 头部右滑关闭位移(px);rawOffset 允许越界以驱动橡皮筋 ----
    val offsetAnim = remember { Animatable(0f) }
    var rawOffset by remember { mutableFloatStateOf(0f) }
    var swipedClosePending by remember { mutableStateOf(false) }

    fun nearestWidthTier(value: Float): Float =
        listOf(minW, defaultW, maxW).minBy { abs(it - value) }

    val reducedNow by rememberUpdatedState(reduced)

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
        // 调宽手势挂在父级:只观察左缘、永不消费点按,子级按钮(附件/页签/模型胶囊)照常收到点击
        Box(
            Modifier
                .fillMaxHeight()
                .graphicsLayer { translationX = offsetAnim.value }
                .pointerInput(Unit) {
                    val edgePx = 48.dp.toPx()
                    val headerPx = 84.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.x > edgePx || down.position.y < headerPx) {
                            return@awaitEachGesture
                        }
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        val slopChange = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            change.consume()
                            rawWidth = widthAnim.value
                            rawWidth += -over
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val target = when {
                                rawWidth < minW -> minW + MilkMotion.rubberband(rawWidth - minW, widthElasticity)
                                rawWidth > maxW -> maxW + MilkMotion.rubberband(rawWidth - maxW, widthElasticity)
                                else -> rawWidth
                            }
                            scope.launch { widthAnim.snapTo(target) }
                        }
                        if (slopChange == null) return@awaitEachGesture
                        draggingWidth = true
                        tracker.addPosition(slopChange.uptimeMillis, slopChange.position)
                        try {
                            horizontalDrag(slopChange.id) { change ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                rawWidth += -change.positionChange().x
                                val target = when {
                                    rawWidth < minW -> minW + MilkMotion.rubberband(rawWidth - minW, widthElasticity)
                                    rawWidth > maxW -> maxW + MilkMotion.rubberband(rawWidth - maxW, widthElasticity)
                                    else -> rawWidth
                                }
                                scope.launch { widthAnim.snapTo(target) }
                            }
                        } finally {
                            draggingWidth = false
                        }
                        val velocity = tracker.calculateVelocity().x
                        val projected = widthAnim.value + MilkMotion.project(-velocity, 0.992f)
                        val settle = nearestWidthTier(projected)
                        if (nearestWidthTier(widthAnim.value) != settle) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        scope.launch {
                            if (reducedNow) widthAnim.snapTo(settle)
                            else widthAnim.animateTo(
                                settle,
                                MilkMotion.momentumSpring(),
                                initialVelocity = -velocity,
                            )
                        }
                        rawWidth = settle
                    }
                },
        ) {
            AgentDrawer(
                state = state,
                widthPx = widthAnim.value,
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
            // 左缘触感握把:纯视觉,无任何指针输入(调宽手势由父级 pointerInput 观察)
            val gripWidth by animateDpAsState(
                targetValue = if (draggingWidth) 4.dp else 3.dp,
                animationSpec = if (reduced) snap() else MilkMotion.uiSpring(),
                label = "agentGripWidth",
            )
            val gripHeight by animateDpAsState(
                targetValue = if (draggingWidth) 48.dp else 36.dp,
                animationSpec = if (reduced) snap() else MilkMotion.uiSpring(),
                label = "agentGripHeight",
            )
            val gripColor by animateColorAsState(
                targetValue = if (draggingWidth) cs.primary else cs.outline.copy(alpha = 0.85f),
                animationSpec = if (reduced) snap() else MilkMotion.colorSpec(),
                label = "agentGripColor",
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 3.dp)
                    .size(width = gripWidth, height = gripHeight)
                    .clip(CircleShape)
                    .background(gripColor),
            )
        }
    }
}

@Composable
private fun AgentDrawer(
    state: AgentUiState,
    widthPx: Float,
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
    val reduced = LocalMilkMotion.current
    var studioTab by remember { mutableStateOf("ai") }
    var pageIds by remember { mutableStateOf<List<String>>(emptyList()) }
    // 页码顺序由当前笔记本页序决定;拖动调宽与页码加载互不影响
    LaunchedEffect(state.boundPage?.notebookId) {
        val notebookId = state.boundPage?.notebookId
        pageIds = if (notebookId == null) {
            emptyList()
        } else {
            runCatching {
                ServiceLocator.agentSnipSource.pagesOf(notebookId).map { it.pageId }
            }.getOrDefault(emptyList())
        }
    }
    Column(
        Modifier
            .width(with(density) { widthPx.toDp() })
            .fillMaxHeight()
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
        Crossfade(
            targetState = studioTab,
            animationSpec = tween(if (reduced) MilkMotion.reduced else 150, easing = MilkMotion.EaseOut),
            label = "studioTab",
        ) { tab ->
            Column(Modifier.fillMaxSize()) {
                when (tab) {
                    "md" -> StudioMarkdownPane(
                        pageLabel = pageLabel,
                        markdown = pageMarkdown,
                        onMarkdownChange = onMarkdownChange,
                        onSaveCard = onSaveMarkdownCard,
                        allNotebooks = allNotebooks,
                        onOpenSplit = onOpenSplitNotebook,
                        modifier = Modifier.fillMaxSize(),
                    )
                    "links" -> StudioLinksPane(
                        notebooks = relatedNotebooks,
                        cards = relatedCards,
                        onOpenSplit = onOpenSplitNotebook,
                        onInsertCard = onInsertCard,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> if (state.setupRequired) {
                        SetupGuide(onOpenSettings, Modifier.weight(1f))
                    } else {
                        if (state.messages.isNotEmpty()) {
                            Messages(state, Modifier.weight(1f), onSaveCard = onSaveMessageCard, pageIds = pageIds)
                        } else {
                            Spacer(Modifier.weight(1f))
                            EmptyPromptActions(
                                pageLabel = pageLabel,
                                onSnip = onSnip,
                                onExplainPage = onExplainPage,
                                onSummarizeChapter = onSummarizeChapter,
                            )
                            Spacer(Modifier.height(MilkSpace.sm))
                        }
                        Composer(state, pageIds)
                    }
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
                .heightIn(min = 44.dp)
                .padding(start = MilkSpace.md + 2.dp, end = MilkSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pageLabel,
                style = MilkText.monoMeta,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            StudioPill(
                label = if (editSource) "预览" else "源码",
                onClick = { editSource = !editSource },
            )
            Spacer(Modifier.width(MilkSpace.xs))
            StudioPill(
                label = "存卡片",
                enabled = markdown.isNotBlank(),
                onClick = onSaveCard,
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
private fun StudioPill(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val reduced = LocalMilkMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduced) 0.96f else 1f,
        animationSpec = tween(MilkMotion.press, easing = MilkMotion.EaseOut),
        label = "studioPillScale",
    )
    val wash = if (pressed && enabled) {
        cs.onSurface.copy(alpha = if (dark) 0.09f else 0.06f)
    } else {
        Color.Transparent
    }
    Box(
        Modifier
            .height(40.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .height(28.dp)
                .clip(PillShape)
                .background(wash)
                .border(1.dp, if (enabled) cs.outline else cs.outline.copy(alpha = 0.55f), PillShape)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MilkText.label,
                color = if (enabled) cs.onSurfaceVariant else cs.onSurfaceVariant.copy(alpha = 0.38f),
                maxLines = 1,
            )
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
    val dark = LocalMilkDark.current
    val groupShape = RoundedCornerShape(MilkRadius.lg)
    val groupBg = if (dark) SurfaceElevatedDark.copy(alpha = 0.65f) else cs.surface
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(MilkSpace.md),
        verticalArrangement = Arrangement.spacedBy(MilkSpace.xs),
    ) {
        Text(
            "双开对照",
            style = MilkText.eyebrow,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MilkSpace.sm, vertical = 2.dp),
        )
        if (notebooks.isEmpty()) {
            MilkEmptyState(title = "暂无其他笔记本", icon = Icons.Outlined.VerticalSplit)
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(groupShape)
                    .background(groupBg)
                    .border(1.dp, cs.outline, groupShape),
            ) {
                notebooks.take(5).forEachIndexed { index, nb ->
                    if (index > 0) InnerHairline(startIndent = 40.dp, endIndent = 12.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .softClickable { onOpenSplit(nb.id) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = MilkSpace.md, vertical = MilkSpace.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NotebookSwatch(nb.coverColor, nb.paperColor)
                        Spacer(Modifier.width(MilkSpace.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                nb.name,
                                style = MilkText.rowTitle,
                                color = cs.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (nb.folder.isNotBlank()) {
                                Text(
                                    nb.folder,
                                    style = MilkText.caption,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.width(MilkSpace.sm))
                        Icon(
                            Icons.Outlined.VerticalSplit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = cs.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
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
            MilkEmptyState(title = "暂无卡片", icon = Icons.Outlined.Style)
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(groupShape)
                    .background(groupBg)
                    .border(1.dp, cs.outline, groupShape),
            ) {
                cards.take(8).forEachIndexed { index, card ->
                    if (index > 0) InnerHairline(startIndent = 32.dp, endIndent = 12.dp)
                    val preview = cardPreviewText(card.title, card.body)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .softClickable { onInsertCard(card) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = MilkSpace.md, vertical = MilkSpace.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(card.color, cs.primary)),
                        )
                        Spacer(Modifier.width(MilkSpace.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                card.title.ifBlank { "无标题卡片" },
                                style = MilkText.rowTitle,
                                color = cs.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (preview.isNotBlank()) {
                                Text(
                                    preview,
                                    style = MilkText.caption,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.width(MilkSpace.sm))
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = cs.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InnerHairline(startIndent: Dp = 0.dp, endIndent: Dp = 0.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startIndent, end = endIndent)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
    )
}

@Composable
private fun NotebookSwatch(coverColor: String, paperColor: String) {
    val cover = parseHexColor(coverColor, MaterialTheme.colorScheme.primary)
    val paper = parseHexColor(paperColor, Color.White)
    val shape = RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    Box(
        Modifier
            .size(width = 16.dp, height = 22.dp)
            .clip(shape)
            .background(paper)
            .border(0.5.dp, PaperBorder, shape),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(cover),
        )
    }
}

private fun parseHexColor(hex: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

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
    val dark = LocalMilkDark.current
    var showModels by remember { mutableStateOf(false) }
    val tabs = remember { listOf("ai" to "AI", "md" to "Markdown", "links" to "关联") }
    val streaming = state.phase == AgentMessage.Phase.STREAMING || state.phase == AgentMessage.Phase.SENDING
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
                .padding(start = 10.dp, end = 2.dp),
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
        // AI 页签下显示当前视觉模型与思考档位胶囊;未配置时不出现(SetupGuide 独占该状态)
        if (studioTab == "ai" && !state.setupRequired) {
            val activeProfile = state.profiles.firstOrNull { it.id == state.activeProfileId }
            val activeModel = activeProfile?.models?.firstOrNull { it.id == state.activeModelId }
                ?: activeProfile?.activeModel()
            val effort = runCatching { ReasoningEffort.valueOf(activeProfile?.reasoningEffort ?: "AUTO") }
                .getOrDefault(ReasoningEffort.AUTO)
            val statusColor = when {
                state.phase == AgentMessage.Phase.FAILED || state.error != null -> cs.error
                streaming -> cs.primary
                else -> LocalMilkSuccess.current
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .padding(start = 12.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    Modifier
                        .weight(1f, fill = false)
                        .heightIn(min = 30.dp)
                        .clip(PillShape)
                        .background(if (dark) SurfaceElevatedDark else cs.surfaceVariant.copy(alpha = 0.6f))
                        .border(0.75.dp, cs.outline, PillShape)
                        .softClickable { showModels = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulsingDot(color = statusColor, active = streaming)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        activeModel?.displayName?.ifBlank { activeModel.id } ?: state.activeModelId.orEmpty(),
                        style = MilkText.monoMeta,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (effort != ReasoningEffort.AUTO) {
                        Text(
                            " · ${effortLabel(effort)}",
                            style = MilkText.monoMicro,
                            color = cs.primary,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = cs.onSurfaceVariant,
                    )
                }
                if (state.messages.isNotEmpty()) {
                    Spacer(Modifier.width(MilkSpace.xxs))
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .softClickable(enabled = !streaming) { agent.clearSession() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = "新对话",
                                modifier = Modifier.size(17.dp),
                                tint = if (streaming) cs.onSurfaceVariant.copy(alpha = 0.38f) else cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showModels) {
        MilkCenterPanel(
            title = "模型与思考",
            subtitle = null,
            onDismiss = { showModels = false },
            maxWidth = 400.dp,
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
private fun SetupGuide(onOpenSettings: (page: String) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    Column(
        modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(MilkRadius.lg))
                .background(if (dark) SurfaceElevatedDark else cs.surfaceVariant.copy(alpha = 0.7f))
                .border(1.dp, cs.outline, RoundedCornerShape(MilkRadius.lg)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = cs.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("尚未设置模型", style = MilkText.panelTitle, color = cs.onSurface)
        Spacer(Modifier.height(16.dp))
        MilkButton(
            label = "设置模型",
            onClick = { onOpenSettings("agent") },
            leading = {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = cs.onPrimary,
                )
            },
        )
    }
}

/** 空会话的纸面动作:三行扁平 hairline 卡,位置与文案即说明,不写操作提示 */
@Composable
private fun EmptyPromptActions(
    pageLabel: String,
    onSnip: () -> Unit,
    onExplainPage: () -> Unit,
    onSummarizeChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val shape = RoundedCornerShape(MilkRadius.lg)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(shape)
            .background(if (dark) SurfaceElevatedDark.copy(alpha = 0.7f) else cs.surface)
            .border(1.dp, cs.outline, shape),
    ) {
        EmptyActionRow(
            icon = Icons.Outlined.CropFree,
            label = "框选题目",
            onClick = onSnip,
        )
        ActionHairline()
        EmptyActionRow(
            icon = Icons.Outlined.Description,
            label = "讲解本页",
            trailing = pageLabel,
            onClick = onExplainPage,
        )
        ActionHairline()
        EmptyActionRow(
            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
            label = "归纳本章",
            onClick = onSummarizeChapter,
        )
    }
}

@Composable
private fun EmptyActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    trailing: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .softClickable(onClick = onClick)
            .heightIn(min = 46.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = cs.primary)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MilkText.rowTitle, color = cs.onSurface, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MilkText.monoMeta, color = cs.onSurfaceVariant)
            Spacer(Modifier.width(MilkSpace.xs))
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = cs.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun ActionHairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 38.dp, end = 10.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
    )
}

// ---------------- 消息区(§8.4 流式渲染) ----------------

@Composable
private fun Messages(
    state: AgentUiState,
    modifier: Modifier = Modifier,
    onSaveCard: (String) -> Unit = {},
    pageIds: List<String> = emptyList(),
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val reduced = LocalMilkMotion.current
    val notebookId = state.boundPage?.notebookId

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
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                when (msg.role) {
                    AgentMessage.Role.USER -> UserMessage(msg, notebookId, pageIds)
                    AgentMessage.Role.ASSISTANT -> AssistantMessage(state, msg, onSaveCard)
                }
            }
            if (state.error != null) {
                item(key = "err") { ErrorBanner(state.error!!) }
            }
        }
        // 流式中用户上滑后:仅图标浮球「回到底部」(A-20)
        AnimatedVisibility(
            visible = !atBottom && state.messages.isNotEmpty(),
            enter = fadeIn(tween(if (reduced) MilkMotion.reduced else 150, easing = MilkMotion.EaseOut)) +
                if (reduced) EnterTransition.None else scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(150, easing = MilkMotion.EaseOut),
                ),
            exit = fadeOut(tween(if (reduced) MilkMotion.reduced else 110, easing = MilkMotion.EaseOut)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        ) {
            val scrollInteraction = remember { MutableInteractionSource() }
            val scrollPressed by scrollInteraction.collectIsPressedAsState()
            val scrollScale by animateFloatAsState(
                targetValue = if (scrollPressed && !reduced) 0.92f else 1f,
                animationSpec = tween(MilkMotion.press, easing = MilkMotion.EaseOut),
                label = "agentScrollScale",
            )
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = scrollInteraction,
                        indication = null,
                        role = Role.Button,
                    ) {
                        scope.launch { listState.animateScrollToItem(state.messages.size - 1) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .graphicsLayer { scaleX = scrollScale; scaleY = scrollScale }
                        .size(34.dp)
                        .milkGlassRim(CircleShape, dark, 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (scrollPressed) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(cs.onSurface.copy(alpha = if (dark) 0.09f else 0.06f)),
                        )
                    }
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "回到底部",
                        modifier = Modifier.size(18.dp),
                        tint = cs.onSurface,
                    )
                }
            }
        }
    }
}

/** 思考过程折叠(DESIGN-V2 msg-think):默认收起,矢量箭头 + mono 灰字「思考过程 · N 字」,展开后纯文本 + 细竖线 */
@Composable
private fun ReasoningBlock(msg: UiMessage) {
    var expanded by remember(msg.id) { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val reduced = LocalMilkMotion.current
    val streamingThought = msg.streaming && msg.text.isBlank()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = if (reduced) snap() else MilkMotion.uiSpring(),
        label = "reasoningChevron",
    )
    Column(Modifier.fillMaxWidth().animateContentSize(tween(if (reduced) MilkMotion.reduced else MilkMotion.enter))) {
        Row(
            Modifier
                .fillMaxWidth()
                .softClickable { expanded = !expanded }
                .defaultMinSize(minHeight = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (streamingThought) {
                PulsingDot(color = cs.primary, active = true)
                Spacer(Modifier.width(MilkSpace.xs))
                Text("思考中", style = MilkText.monoMeta, color = cs.onSurfaceVariant)
            } else {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = rotation },
                    tint = cs.onSurfaceVariant,
                )
                Spacer(Modifier.width(MilkSpace.xs))
                Text(
                    "思考过程 · ${msg.reasoning.length} 字",
                    style = MilkText.monoMeta,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(if (reduced) MilkMotion.reduced else 150, easing = MilkMotion.EaseOut)) +
                if (reduced) EnterTransition.None else expandVertically(
                    animationSpec = tween(180, easing = MilkMotion.EaseOut),
                ),
            exit = fadeOut(tween(if (reduced) MilkMotion.reduced else MilkMotion.exit, easing = MilkMotion.EaseOut)) +
                if (reduced) ExitTransition.None else shrinkVertically(
                    animationSpec = tween(MilkMotion.exit, easing = MilkMotion.EaseOut),
                ),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(top = MilkSpace.xs),
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (streamingThought) cs.primary.copy(alpha = 0.45f) else cs.outline),
                )
                Spacer(Modifier.width(10.dp))
                Text(msg.reasoning, style = MilkText.caption, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UserMessage(msg: UiMessage, notebookId: String?, pageIds: List<String>) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val bubble = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 4.dp, bottomStart = 14.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (msg.attachments.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                msg.attachments.forEach { att -> MessageAttachmentChip(notebookId, att, pageIds) }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (msg.text.isNotBlank()) {
            Box(
                Modifier
                    .padding(start = 36.dp)
                    .clip(bubble)
                    .background(cs.primary.copy(alpha = if (dark) 0.16f else 0.09f))
                    .border(
                        0.75.dp,
                        cs.primary.copy(alpha = if (dark) 0.28f else 0.16f),
                        bubble,
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(msg.text, style = MilkText.body, color = cs.onSurface)
            }
        }
    }
}

@Composable
private fun MessageAttachmentChip(notebookId: String?, att: Attachment, pageIds: List<String>) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val bitmap = rememberAttachmentBitmap(notebookId, att)
    val thumbShape = RoundedCornerShape(MilkRadius.xs)
    Row(
        Modifier
            .clip(RoundedCornerShape(MilkRadius.md))
            .background(cs.surfaceVariant.copy(alpha = if (dark) 0.85f else 0.7f))
            .border(1.dp, cs.outline, RoundedCornerShape(MilkRadius.md))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AttachmentThumbnail(bitmap, 34.dp, thumbShape)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                formatAttachmentTitle(att.source, att.logicalName, pageIds),
                style = MilkText.label,
                color = cs.onSurface,
                maxLines = 1,
            )
            Text(
                "${att.widthPx}×${att.heightPx}",
                style = MilkText.monoMicro,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttachmentThumbnail(bitmap: ImageBitmap?, size: Dp, shape: RoundedCornerShape) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(if (bitmap != null) Color.White else cs.surfaceVariant)
            .border(0.5.dp, if (dark) Color.White.copy(alpha = 0.14f) else PaperBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = cs.primary,
            )
        }
    }
}

@Composable
private fun rememberAttachmentBitmap(notebookId: String?, att: Attachment): ImageBitmap? {
    var bitmap by remember(notebookId, att.file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(notebookId, att.file) {
        if (notebookId == null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching { ServiceLocator.agentStore.readAttachment(notebookId, att) }
                .getOrNull()
                ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        }
    }
    return bitmap
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantMessage(state: AgentUiState, msg: UiMessage, onSaveCard: (String) -> Unit) {
    val agent = ServiceLocator.agentController
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var copied by remember(msg.id) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Column(Modifier.fillMaxWidth()) {
        // 思考过程:默认折叠;流式中(答案未出)显示「思考中」,完成后显示字数
        if (msg.reasoning.isNotBlank()) {
            ReasoningBlock(msg)
            Spacer(Modifier.height(10.dp))
        }
        when {
            msg.streaming && msg.text.isBlank() && msg.reasoning.isBlank() -> ThinkingDots()
            else -> {
                if (msg.text.isNotBlank()) MarkdownBlocks(msg.text)
                if (msg.streaming && msg.text.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    PulsingDot(color = cs.primary, active = true)
                }
            }
        }
        // 操作栏(DESIGN-V2 msg-bar):贴回纸面 / 存卡片 / 复制
        when (msg.phase) {
            AgentMessage.Phase.STREAMING, AgentMessage.Phase.SENDING -> {}
            AgentMessage.Phase.FAILED -> {
                Spacer(Modifier.height(MilkSpace.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AgentActionPill(
                        label = "重试",
                        icon = Icons.Outlined.Refresh,
                        fg = cs.primary,
                        background = cs.primary.copy(alpha = 0.10f),
                        borderColor = cs.primary.copy(alpha = 0.30f),
                        onClick = { agent.retry() },
                    )
                }
            }
            AgentMessage.Phase.INTERRUPTED -> {
                Spacer(Modifier.height(MilkSpace.xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MilkStatusBadge("已中断", MilkStatusTone.NEUTRAL)
                    AgentActionPill(
                        label = "重试",
                        icon = Icons.Outlined.Refresh,
                        fg = cs.primary,
                        background = cs.primary.copy(alpha = 0.10f),
                        borderColor = cs.primary.copy(alpha = 0.30f),
                        onClick = { agent.retry() },
                    )
                }
            }
            AgentMessage.Phase.DONE -> {
                Spacer(Modifier.height(MilkSpace.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (msg.insertedAnnotationId == null) {
                        AgentActionPill(
                            label = "贴回纸面",
                            icon = Icons.Outlined.PushPin,
                            fg = cs.primary,
                            background = cs.primary.copy(alpha = 0.10f),
                            borderColor = cs.primary.copy(alpha = 0.30f),
                            onClick = { agent.insertAnnotation(msg.id) },
                        )
                    } else {
                        // 已贴回是状态不是操作:静态胶囊,不注册点击
                        AgentActionPill(
                            label = "已贴回",
                            icon = Icons.Outlined.Check,
                            fg = LocalMilkSuccess.current,
                            background = LocalMilkSuccess.current.copy(alpha = 0.10f),
                            borderColor = LocalMilkSuccess.current.copy(alpha = 0.24f),
                        )
                    }
                    AgentActionPill(
                        label = "存卡片",
                        icon = Icons.Outlined.BookmarkBorder,
                        fg = cs.onSurfaceVariant,
                        background = Color.Transparent,
                        borderColor = cs.outline,
                        onClick = { onSaveCard(msg.text) },
                    )
                    AgentActionPill(
                        label = if (copied) "已复制" else "复制",
                        icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        fg = if (copied) LocalMilkSuccess.current else cs.onSurfaceVariant,
                        background = if (copied) LocalMilkSuccess.current.copy(alpha = 0.10f) else Color.Transparent,
                        borderColor = if (copied) LocalMilkSuccess.current.copy(alpha = 0.24f) else cs.outline,
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("agent", msg.text))
                            copied = true
                        },
                    )
                }
            }
        }
        // 用量元信息:模型 · prompt→completion tok
        val meta = formatAgentUsageMeta(msg.modelId, msg.promptTokens, msg.completionTokens)
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(MilkSpace.sm))
            Text(
                meta,
                style = MilkText.monoMicro,
                color = cs.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
    }
}

/** 紧凑药丸:28dp 视觉胶囊 + 36–40dp 命中高,按下 0.96 微缩;无点击时不注册语义 */
@Composable
private fun AgentActionPill(
    label: String,
    icon: ImageVector,
    fg: Color,
    background: Color,
    borderColor: Color,
    onClick: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val reduced = LocalMilkMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null && !reduced) 0.96f else 1f,
        animationSpec = tween(MilkMotion.press, easing = MilkMotion.EaseOut),
        label = "agentPillScale",
    )
    val wash = if (pressed && onClick != null) {
        cs.onSurface.copy(alpha = if (dark) 0.09f else 0.06f)
    } else {
        Color.Transparent
    }
    Box(
        Modifier
            .heightIn(min = 38.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .height(28.dp)
                .clip(PillShape)
                .background(background)
                .background(wash)
                .border(0.75.dp, borderColor, PillShape)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = fg)
            Spacer(Modifier.width(5.dp))
            Text(label, style = MilkText.label, color = fg, maxLines = 1)
        }
    }
}

@Composable
private fun PulsingDot(
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 6.dp,
) {
    val reduced = LocalMilkMotion.current
    val alpha = if (active && !reduced) {
        val transition = rememberInfiniteTransition(label = "agentPulse")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = MilkMotion.EaseInOut),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "agentPulseAlpha",
        ).value
    } else {
        1f
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun ThinkingDots(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalMilkMotion.current
    val phase = if (reduced) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "agentThinking")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "agentThinkingPhase",
        ).value
    }
    Row(
        modifier.heightIn(min = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha = if (reduced) {
                0.4f
            } else {
                (0.3f + 0.7f * ((cos(2.0 * PI * (phase - index * 0.16f)) + 1.0) / 2.0)).toFloat()
            }
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .clip(CircleShape)
                    .background(cs.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun ErrorBanner(error: AgentError) {
    val cs = MaterialTheme.colorScheme
    val title = errorTitle(error)
    val detail = errorDetailOrNull(title, error.detail)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MilkRadius.md))
            .background(cs.error.copy(alpha = 0.08f))
            .border(0.75.dp, cs.error.copy(alpha = 0.24f), RoundedCornerShape(MilkRadius.md))
            .padding(horizontal = 10.dp, vertical = MilkSpace.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = cs.error,
        )
        Spacer(Modifier.width(MilkSpace.sm))
        Column(Modifier.weight(1f)) {
            Text(title, style = MilkText.label, color = cs.error)
            if (detail != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    style = MilkText.micro,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun errorTitle(e: AgentError): String = when (e) {
    is AgentError.Auth -> "Key 无效或额度不足"
    is AgentError.RateLimit -> "请求过快，已自动退避"
    is AgentError.Server -> "服务方故障"
    is AgentError.Network -> "网络不可用"
    is AgentError.Timeout -> "网络超时"
    is AgentError.ModelNotVision -> "当前模型疑似不支持图片输入，请更换视觉模型"
    is AgentError.NotFound -> "模型或端点不存在（可能已下线）"
    is AgentError.ContextTooLarge -> "上下文过大，请减少页数"
    is AgentError.StreamBroken -> "流式传输中断"
    is AgentError.NoProvider, is AgentError.NotVision -> "未配置视觉模型"
    else -> "请求失败"
}

// ---------------- 输入区(追问 / slash) ----------------

@Composable
private fun Composer(
    state: AgentUiState,
    pageIds: List<String>,
) {
    val agent = ServiceLocator.agentController
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val reduced = LocalMilkMotion.current
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
    } else {
        emptyList()
    }
    var dismissedSlash by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .milkEdgeHairline(cs.outline.copy(alpha = 0.7f), top = true),
    ) {
        // 内联 slash 建议:直接贴在输入框上方,不再弹居中面板
        if (slashSuggestions.isNotEmpty() && dismissedSlash != text) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .milkGlassRim(RoundedCornerShape(MilkRadius.lg), dark, 10.dp)
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(4.dp),
            ) {
                slashSuggestions.take(7).forEach { (label, intent) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MilkRadius.sm))
                            .softClickable {
                                text = ""
                                dismissedSlash = null
                                if (intent != null) agent.sendWithIntent(intent) else agent.clearSession()
                            }
                            .heightIn(min = 36.dp)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MilkText.monoBody, color = cs.onSurface)
                    }
                }
            }
        }
        // 草稿附件:真实缩略图 + ✕ 移除,随下一条消息发出
        if (state.draftAttachments.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = MilkSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(MilkSpace.sm),
            ) {
                state.draftAttachments.forEach { draft ->
                    DraftAttachmentCard(
                        draft = draft,
                        pageIds = pageIds,
                        onRemove = { agent.removeDraftAttachment(draft.id) },
                    )
                }
            }
            Text(
                "${state.draftAttachments.size} 项 · ~${formatAgentTokenCount(agent.estTokensNow())} tok",
                style = MilkText.monoMicro,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = MilkSpace.xs),
            )
        }

        val capsuleShape = RoundedCornerShape(MilkRadius.xl)
        val capsuleBg by animateColorAsState(
            targetValue = if (composerFocused) cs.surface
            else cs.surfaceVariant.copy(alpha = if (dark) 0.85f else 0.7f),
            animationSpec = if (reduced) snap() else MilkMotion.colorSpec(),
            label = "composerBg",
        )
        val borderColor by animateColorAsState(
            targetValue = if (composerFocused) cs.primary else cs.outline.copy(alpha = 0.6f),
            animationSpec = if (reduced) snap() else MilkMotion.colorSpec(),
            label = "composerBorder",
        )
        val borderWidth by animateDpAsState(
            targetValue = if (composerFocused) 1.5.dp else 1.dp,
            animationSpec = if (reduced) snap() else MilkMotion.colorSpec(),
            label = "composerBorderWidth",
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = MilkSpace.sm)
                .clip(capsuleShape)
                .background(capsuleBg)
                .border(borderWidth, borderColor, capsuleShape)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 回形针:附件面板
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .softClickable { showAttach = !showAttach },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.AttachFile,
                    contentDescription = if (showAttach) "收起附件面板" else "附件",
                    modifier = Modifier.size(17.dp),
                    tint = if (showAttach || state.draftAttachments.isNotEmpty()) cs.primary else cs.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = MilkSpace.xs, vertical = 7.dp),
            ) {
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
            Spacer(Modifier.width(MilkSpace.xxs))
            if (streaming) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(cs.error.copy(alpha = 0.14f))
                        .softClickable { agent.stop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Stop,
                        contentDescription = "停止",
                        modifier = Modifier.size(15.dp),
                        tint = cs.error,
                    )
                }
            } else {
                val sendBg by animateColorAsState(
                    targetValue = when {
                        canSend -> cs.primary
                        dark -> SurfaceElevatedDark
                        else -> cs.outline.copy(alpha = 0.75f)
                    },
                    animationSpec = if (reduced) snap() else MilkMotion.colorSpec(),
                    label = "sendBg",
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(sendBg)
                        .softClickable(enabled = canSend) {
                            agent.setDraftText(text)
                            agent.send()
                            text = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "发送",
                        modifier = Modifier.size(14.dp),
                        tint = when {
                            canSend -> cs.onPrimary
                            dark -> cs.onSurfaceVariant.copy(alpha = 0.55f)
                            else -> cs.surface
                        },
                    )
                }
            }
        }
    }
    if (showAttach) {
        MilkCenterPanel(
            title = "附加页面",
            subtitle = null,
            onDismiss = { showAttach = false },
            maxWidth = 380.dp,
        ) {
            AttachmentPanel(
                boundPage = state.boundPage,
                attachedNames = state.draftAttachments.map { it.logicalName }.toSet(),
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachmentPanel(
    boundPage: com.milknote.agent.PageRef?,
    attachedNames: Set<String>,
    onPickPage: (com.milknote.agent.PageRef) -> Unit,
    onPickCurrent: () -> Unit,
    onPickChapter: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var pages by remember { mutableStateOf<List<com.milknote.agent.PageRef>>(emptyList()) }
    LaunchedEffect(boundPage?.notebookId) {
        boundPage?.let {
            pages = runCatching { ServiceLocator.agentSnipSource.pagesOf(it.notebookId) }.getOrDefault(emptyList())
        }
    }
    val pageIds = pages.map { it.pageId }
    val currentLabel = boundPage?.let { formatPageDisplayLabel(it.pageId, pageIds) } ?: "—"
    val restPages = pages.filter { it.pageId != boundPage?.pageId }
    val currentAttached = boundPage != null &&
        ImageBudget.logicalNamePage(boundPage.pageId) in attachedNames
    val groupShape = RoundedCornerShape(MilkRadius.lg)
    Column(Modifier.fillMaxWidth().padding(top = MilkSpace.md)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
            verticalArrangement = Arrangement.spacedBy(MilkSpace.xxs),
        ) {
            MilkActionChip(
                label = "当前页 · $currentLabel",
                selected = currentAttached,
                leading = {
                    Icon(
                        if (currentAttached) Icons.Outlined.Check else Icons.Outlined.Description,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                },
                small = true,
                onClick = onPickCurrent,
            )
            MilkActionChip(
                label = "整章 · ${restPages.size} 页",
                enabled = restPages.isNotEmpty(),
                leading = { Icon(Icons.Outlined.Layers, null, modifier = Modifier.size(15.dp)) },
                small = true,
                onClick = onPickChapter,
            )
        }
        if (restPages.isNotEmpty()) {
            Spacer(Modifier.height(MilkSpace.lg - 2.dp))
            Text("全部页面", style = MilkText.eyebrow, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(MilkSpace.xs))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(groupShape)
                    .border(1.dp, cs.outline, groupShape)
                    .verticalScroll(rememberScrollState()),
            ) {
                restPages.forEachIndexed { index, page ->
                    if (index > 0) InnerHairline(startIndent = 12.dp, endIndent = 12.dp)
                    val attached = ImageBudget.logicalNamePage(page.pageId) in attachedNames
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .softClickable { onPickPage(page) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            formatPageDisplayLabel(page.pageId, pageIds),
                            style = MilkText.rowTitle,
                            color = cs.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (attached) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = LocalMilkSuccess.current,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = cs.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 草稿附件卡片:真实缩略图 + 标题 + 尺寸 + ✕ 移除 */
@Composable
private fun DraftAttachmentCard(
    draft: com.milknote.agent.DraftAttachment,
    pageIds: List<String>,
    onRemove: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val bitmap = remember(draft.id) {
        runCatching { BitmapFactory.decodeByteArray(draft.png, 0, draft.png.size)?.asImageBitmap() }.getOrNull()
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(MilkRadius.md))
            .background(cs.surfaceVariant.copy(alpha = if (dark) 0.85f else 0.7f))
            .border(1.dp, cs.outline, RoundedCornerShape(MilkRadius.md))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AttachmentThumbnail(bitmap, 38.dp, RoundedCornerShape(MilkRadius.xs))
        Spacer(Modifier.width(MilkSpace.sm))
        Column(Modifier.widthIn(max = 120.dp)) {
            Text(
                formatAttachmentTitle(draft.source, draft.logicalName, pageIds),
                style = MilkText.label,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${draft.widthPx}×${draft.heightPx}",
                style = MilkText.monoMicro,
                color = cs.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(MilkSpace.xxs))
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .softClickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "移除附件",
                modifier = Modifier.size(13.dp),
                tint = cs.onSurfaceVariant,
            )
        }
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
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = MilkSpace.md)) {
        // 模型列表:超限内部滚动(A-18,防多 Provider 长列表撑爆抽屉);行 = 共享单选行
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            state.profiles.filter { profile -> profile.models.any { it.vision } }.forEach { profile ->
                Text(
                    profile.title,
                    style = MilkText.eyebrow,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = MilkSpace.sm + 2.dp, bottom = MilkSpace.xxs),
                )
                profile.models.filter { it.vision }.forEach { m ->
                    val active = profile.id == state.activeProfileId && m.id == state.activeModelId
                    MilkRadioRow(
                        selected = active,
                        label = m.displayName.ifBlank { m.id },
                        onClick = {
                            agent.selectModel(profile.id, m.id)
                            onDismiss()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(MilkSpace.md))
        HorizontalDivider(color = cs.outline.copy(alpha = 0.7f))
        Spacer(Modifier.height(MilkSpace.md))
        // ---- 思考强度(§3.8:每家档位不同,按当前模型声明显示) ----
        val activeProfile = state.profiles.firstOrNull { it.id == state.activeProfileId }
        val activeModel = activeProfile?.activeModel()
        val declared = activeModel?.thinkingLevels
            ?.mapNotNull { runCatching { ReasoningEffort.valueOf(it) }.getOrNull() }
            ?: listOf(
                // 自定义模型未声明:按 OpenAI 标准给通用四档
                ReasoningEffort.OFF,
                ReasoningEffort.LOW,
                ReasoningEffort.MEDIUM,
                ReasoningEffort.HIGH,
            )
        val current = runCatching { ReasoningEffort.valueOf(activeProfile?.reasoningEffort ?: "AUTO") }
            .getOrDefault(ReasoningEffort.AUTO)
        Text("思考强度", style = MilkText.eyebrow, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(MilkSpace.sm))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
        ) {
            EffortChip("自动", current == ReasoningEffort.AUTO) {
                agent.setReasoningEffort(ReasoningEffort.AUTO)
            }
            declared.forEach { level ->
                EffortChip(effortLabel(level), current == level) {
                    agent.setReasoningEffort(level)
                }
            }
        }
        Spacer(Modifier.height(MilkSpace.md))
        HorizontalDivider(color = cs.outline.copy(alpha = 0.7f))
        // A-17:真正进入 设置 · Agent(此前只关面板不导航)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MilkRadius.sm))
                .softClickable {
                    onDismiss()
                    onOpenSettings("agent")
                }
                .heightIn(min = 46.dp)
                .padding(horizontal = MilkSpace.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = cs.primary,
            )
            Spacer(Modifier.width(MilkSpace.sm + 2.dp))
            Text(
                "管理模型",
                style = MilkText.bodyMedium,
                color = cs.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = cs.onSurfaceVariant,
            )
        }
    }
}

private fun effortLabel(e: ReasoningEffort): String = when (e) {
    ReasoningEffort.AUTO -> "自动"
    ReasoningEffort.OFF -> "关"
    ReasoningEffort.MINIMAL -> "最低"
    ReasoningEffort.LOW -> "低"
    ReasoningEffort.MEDIUM -> "中"
    ReasoningEffort.HIGH -> "高"
    ReasoningEffort.XHIGH -> "超高"
    ReasoningEffort.MAX -> "最高"
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
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val linkColor = cs.primary
    val bodyStyle = MilkText.body.copy(lineHeight = 21.sp)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { i, b ->
            when (b) {
                is MarkdownLite.Block.Heading -> {
                    val style = when {
                        b.level <= 2 -> MilkText.panelTitle
                        b.level == 3 -> MilkText.cardTitle
                        else -> MilkText.rowTitle.copy(fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.padding(top = if (i > 0) 6.dp else 0.dp)) {
                        InlineText(b.spans, linkColor, onWikilink, style)
                    }
                }
                is MarkdownLite.Block.ListItem -> Row {
                    Text(
                        b.order?.let { "$it." } ?: "•",
                        style = bodyStyle,
                        color = cs.primary,
                        modifier = Modifier.widthIn(min = 18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    InlineText(b.spans, linkColor, onWikilink, bodyStyle)
                }
                is MarkdownLite.Block.Paragraph -> InlineText(b.spans, linkColor, onWikilink, bodyStyle)
                is MarkdownLite.Block.MathBlock -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MilkRadius.sm))
                        .background(cs.surfaceVariant.copy(alpha = if (dark) 0.85f else 0.7f))
                        .border(1.dp, cs.outline.copy(alpha = 0.55f), RoundedCornerShape(MilkRadius.sm))
                        .drawBehind {
                            drawRect(
                                color = cs.primary.copy(alpha = 0.45f),
                                size = Size(2.5.dp.toPx(), size.height),
                            )
                        },
                ) {
                    Text(
                        b.latex,
                        style = MilkText.monoBody,
                        color = cs.onSurface,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 12.dp, end = 12.dp, top = MilkSpace.sm, bottom = MilkSpace.sm),
                    )
                }
                is MarkdownLite.Block.TagLine -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
                    verticalArrangement = Arrangement.spacedBy(MilkSpace.xxs),
                ) {
                    b.tags.take(8).forEach { tag ->
                        Text(
                            "#$tag",
                            style = MilkText.monoMicro,
                            color = cs.primary,
                            modifier = Modifier
                                .clip(PillShape)
                                .background(cs.primary.copy(alpha = 0.09f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 行内富文本:[[双链]] 渲染为强调色下划线,提供 onWikilink 时可点击跳转 */
@Composable
private fun InlineText(
    spans: List<MarkdownLite.Span>,
    linkColor: Color,
    onWikilink: ((String) -> Unit)?,
    style: TextStyle,
) {
    val codeBg = MaterialTheme.colorScheme.onSurface.copy(alpha = if (LocalMilkDark.current) 0.10f else 0.06f)
    val annotated = remember(spans, linkColor, codeBg) { spansToAnnotated(spans, linkColor, codeBg) }
    val resolved = style.copy(color = MaterialTheme.colorScheme.onSurface)
    if (onWikilink != null && annotated.getStringAnnotations("wiki", 0, annotated.length).isNotEmpty()) {
        ClickableText(
            text = annotated,
            style = resolved,
            onClick = { offset ->
                annotated.getStringAnnotations("wiki", offset, offset).firstOrNull()?.let { onWikilink(it.item) }
            },
        )
    } else {
        Text(annotated, style = resolved)
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
                builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg))
                builder.append(s.latex)
                builder.pop()
            }
        }
    }
    return builder.toAnnotatedString()
}
