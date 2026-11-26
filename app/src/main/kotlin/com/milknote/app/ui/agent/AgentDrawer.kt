package com.milknote.app.ui.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.VerticalSplit
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milknote.agent.AgentError
import com.milknote.agent.AgentMessage
import com.milknote.agent.AgentUiState
import com.milknote.agent.Attachment
import com.milknote.agent.DraftAttachment
import com.milknote.agent.Intents
import com.milknote.agent.ReasoningEffort
import com.milknote.agent.UiMessage
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.cards.cardPreviewText
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonSize
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkComposerField
import com.milknote.app.ui.common.MilkEmptyState
import com.milknote.app.ui.common.MilkExpandableErrorBox
import com.milknote.app.ui.common.MilkGroupItemDivider
import com.milknote.app.ui.common.MilkHairlineDivider
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkInlineAction
import com.milknote.app.ui.common.MilkInlineStatus
import com.milknote.app.ui.common.MilkInlineTone
import com.milknote.app.ui.common.MilkModelProviderHeader
import com.milknote.app.ui.common.MilkModelSelectRow
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.MilkSurfaceGroup
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.MilkToast
import com.milknote.app.ui.common.MilkToolConfirmBar
import com.milknote.app.ui.common.MilkToolTraceList
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
    onSnipToAttachment: () -> Unit = onSnip,
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
                onSnipToAttachment = onSnipToAttachment,
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
    onSnipToAttachment: () -> Unit,
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
    // 伴随笔记合并规则:写入内容接在现有页面 Markdown 之后(两处入口共用同一口径)
    val writeToNotes: (String) -> Unit = { content ->
        onMarkdownChange(
            if (pageMarkdown.isBlank()) content else pageMarkdown.trimEnd() + "\n\n" + content,
        )
    }
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
            pageLabel = pageLabel,
            pageMarkdown = pageMarkdown,
            pageIds = pageIds,
            onWriteToNotes = writeToNotes,
            onSaveCard = onSaveMessageCard,
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
                            Messages(
                                state = state,
                                modifier = Modifier.weight(1f),
                                onSaveCard = onSaveMessageCard,
                                onWriteToNotes = writeToNotes,
                                pageIds = pageIds,
                            )
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
                        state.pendingConfirmation?.let { pending ->
                            MilkToolConfirmBar(
                                confirmation = pending,
                                onApprove = { ServiceLocator.agentController.approvePendingTool() },
                                onReject = { ServiceLocator.agentController.rejectPendingTool() },
                                modifier = Modifier
                                    .padding(horizontal = MilkSpace.md)
                                    .padding(bottom = MilkSpace.xs),
                            )
                        }
                        Composer(state, pageIds, onSnipToAttachment)
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
    var preview by remember { mutableStateOf<AgentPreviewTarget?>(null) }
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
                .heightIn(min = 46.dp)
                .padding(start = MilkSpace.md + 2.dp, end = MilkSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pageLabel,
                style = MilkText.monoMeta,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            MilkInlineAction(
                label = "悬浮预览",
                onClick = {
                    preview = AgentPreviewTarget.Markdown(
                        title = pageLabel.ifBlank { "当前页" },
                        content = markdown,
                        canSaveToNotes = false,
                    )
                },
                icon = Icons.Outlined.Description,
                enabled = markdown.isNotBlank(),
            )
            Spacer(Modifier.width(MilkSpace.xs))
            // 定宽:源码/预览切换不推动右侧「存卡片」
            MilkInlineAction(
                label = if (editSource) "预览" else "源码",
                onClick = { editSource = !editSource },
                selected = editSource,
                minWidth = 56.dp,
            )
            Spacer(Modifier.width(MilkSpace.xs))
            MilkInlineAction(
                label = "存卡片",
                onClick = onSaveCard,
                enabled = markdown.isNotBlank(),
            )
        }

        if (editSource) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(MilkSpace.md),
            ) {
                MilkTextField(
                    value = markdown,
                    onValueChange = onMarkdownChange,
                    placeholder = "记录本页要点…",
                    singleLine = false,
                    minLines = 12,
                    mono = true,
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .clickable { editSource = true }
                    .padding(MilkSpace.md),
            ) {
                if (markdown.isBlank()) {
                    Text("记录本页要点…", color = cs.onSurfaceVariant, style = MilkText.body)
                } else {
                    MilkMarkdownView(
                        text = markdown,
                        onWikilink = openWikilink,
                        onFormulaClick = { latex ->
                            preview = AgentPreviewTarget.Markdown(
                                title = "公式预览",
                                content = latex,
                                docType = MarkdownLite.GeneratedDocType.LATEX,
                                canSaveToNotes = false,
                                canSaveCard = true,
                            )
                        },
                    )
                }
            }
        }
    }
    preview?.let { target ->
        AgentFloatingPreviewDialog(
            target = target,
            onDismiss = { preview = null },
        )
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
        MilkSectionLabel(text = "双开对照", modifier = Modifier.padding(horizontal = MilkSpace.xs))
        if (notebooks.isEmpty()) {
            MilkEmptyState(title = "暂无其他笔记本", icon = Icons.Outlined.VerticalSplit)
        } else {
            MilkSurfaceGroup {
                notebooks.take(5).forEachIndexed { index, nb ->
                    if (index > 0) MilkHairlineDivider(startIndent = 40.dp, endIndent = 12.dp)
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
        MilkSectionLabel(text = "知识卡片", modifier = Modifier.padding(horizontal = MilkSpace.xs))
        if (cards.isEmpty()) {
            MilkEmptyState(title = "暂无卡片", icon = Icons.Outlined.Style)
        } else {
            MilkSurfaceGroup {
                cards.take(8).forEachIndexed { index, card ->
                    if (index > 0) MilkHairlineDivider(startIndent = 32.dp, endIndent = 12.dp)
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
    pageLabel: String = "第 1 页",
    pageMarkdown: String = "",
    pageIds: List<String> = emptyList(),
    onWriteToNotes: (String) -> Unit = {},
    onSaveCard: (String) -> Unit = {},
) {
    val agent = ServiceLocator.agentController
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val scope = rememberCoroutineScope()
    var showModels by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var showHub by remember { mutableStateOf(false) }
    var editingDraft by remember { mutableStateOf<DraftAttachment?>(null) }
    var editingBitmap by remember { mutableStateOf<Bitmap?>(null) }
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
                    .padding(start = 10.dp, end = 6.dp, top = 2.dp, bottom = MilkSpace.sm)
                    .testTag("agent-model-row"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 模型与思考选择器:占满右侧三个 36dp 动作钮之外的全部宽度,
                // 与下方分段控件、右侧图标钮共用 8dp 圆角与 36dp 高度。
                Row(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(MilkRadius.sm))
                        .background(if (dark) SurfaceElevatedDark else cs.surfaceVariant.copy(alpha = 0.55f))
                        .border(1.dp, cs.outline, RoundedCornerShape(MilkRadius.sm))
                        .softClickable { showModels = true }
                        .padding(horizontal = 10.dp)
                        .testTag("agent-model-selector"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulsingDot(color = statusColor, active = streaming, size = 7.dp)
                    Spacer(Modifier.width(8.dp))
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            activeModel?.displayName?.ifBlank { activeModel.id }
                                ?: state.activeModelId.orEmpty(),
                            style = MilkText.label.copy(fontWeight = FontWeight.Medium),
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (effort != ReasoningEffort.AUTO) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "思考·${effortLabel(effort)}",
                                style = MilkText.monoMicro,
                                color = cs.primary,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(MilkRadius.xs))
                                    .background(cs.primary.copy(alpha = 0.11f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Outlined.UnfoldMore,
                        contentDescription = "切换模型与思考设置",
                        modifier = Modifier.size(16.dp),
                        tint = cs.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(MilkSpace.xs))
                MilkIconButton(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = "文件与文档预览",
                    onClick = { showHub = true },
                    touchSize = 36.dp,
                    visualSize = 36.dp,
                    iconSize = 17.dp,
                    shape = RoundedCornerShape(MilkRadius.sm),
                )
                MilkIconButton(
                    imageVector = Icons.Outlined.History,
                    contentDescription = "历史会话",
                    onClick = { showSessions = !showSessions },
                    selected = showSessions,
                    touchSize = 36.dp,
                    visualSize = 36.dp,
                    iconSize = 17.dp,
                    shape = RoundedCornerShape(MilkRadius.sm),
                )
                MilkIconButton(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "新对话",
                    onClick = { agent.clearSession() },
                    enabled = !streaming,
                    touchSize = 36.dp,
                    visualSize = 36.dp,
                    iconSize = 18.dp,
                    shape = RoundedCornerShape(MilkRadius.sm),
                )
            }
        }
    }
    if (showModels) {
        ModelSheet(
            state = state,
            onDismiss = { showModels = false },
            onOpenSettings = onOpenSettings,
        )
    }
    SessionSwitcherMenu(
        expanded = showSessions,
        state = state,
        pageIds = pageIds,
        onDismiss = { showSessions = false },
        onSwitchSession = { id ->
            agent.switchSession(id)
            showSessions = false
        },
        onNewSession = {
            agent.clearSession()
            showSessions = false
        },
        onDeleteSession = { id -> agent.deleteSession(id) },
        onRegenerateSummary = { id -> agent.regenerateSummary(id) },
        anchorHeight = 88.dp,
    )
    if (showHub) {
        val items = remember(state, pageMarkdown, pageLabel, pageIds) {
            collectSessionPreviewItems(state, pageMarkdown, pageLabel, pageIds)
        }
        AgentPreviewHubDialog(
            items = items,
            onDismiss = { showHub = false },
            onSaveToNotes = { markdown -> onWriteToNotes(markdown.content) },
            onSaveCard = { markdown -> onSaveCard(markdown.content) },
            onEditDraftImage = { draftId ->
                val draft = state.draftAttachments.firstOrNull { it.id == draftId }
                if (draft != null) {
                    scope.launch {
                        val bitmap = withContext(Dispatchers.Default) {
                            runCatching { BitmapFactory.decodeByteArray(draft.png, 0, draft.png.size) }.getOrNull()
                        }
                        if (bitmap != null) {
                            editingDraft = draft
                            editingBitmap = bitmap
                        }
                    }
                }
            },
        )
    }
    val editing = editingBitmap
    val draftBeingEdited = editingDraft
    if (editing != null && draftBeingEdited != null) {
        ImageEditorFloatingPanel(
            source = editing,
            title = "编辑图片",
            confirmLabel = "保存修改",
            onDismiss = {
                editingBitmap = null
                editingDraft = null
                if (!editing.isRecycled) editing.recycle()
            },
            onConfirm = { encoded ->
                agent.updateDraftAttachment(
                    id = draftBeingEdited.id,
                    png = encoded.bytes,
                    jpeg = encoded.jpeg,
                    widthPx = encoded.widthPx,
                    heightPx = encoded.heightPx,
                )
                editingBitmap = null
                editingDraft = null
                if (!editing.isRecycled) editing.recycle()
            },
        )
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

/** 空会话的纸面动作:统一分组面 + hairline 分隔,位置与文案即说明,不写操作提示 */
@Composable
private fun EmptyPromptActions(
    pageLabel: String,
    onSnip: () -> Unit,
    onExplainPage: () -> Unit,
    onSummarizeChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MilkSurfaceGroup(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        EmptyActionRow(
            icon = Icons.Outlined.CropFree,
            label = "框选题目",
            onClick = onSnip,
        )
        MilkHairlineDivider(startIndent = 38.dp, endIndent = 10.dp)
        EmptyActionRow(
            icon = Icons.Outlined.Description,
            label = "讲解本页",
            trailing = pageLabel,
            onClick = onExplainPage,
        )
        MilkHairlineDivider(startIndent = 38.dp, endIndent = 10.dp)
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

// ---------------- 消息区(§8.4 流式渲染) ----------------

@Composable
private fun Messages(
    state: AgentUiState,
    modifier: Modifier = Modifier,
    onSaveCard: (String) -> Unit = {},
    onWriteToNotes: (String) -> Unit = {},
    pageIds: List<String> = emptyList(),
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduced = LocalMilkMotion.current
    val notebookId = state.boundPage?.notebookId
    var preview by remember { mutableStateOf<AgentPreviewTarget?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    // 「真的在底部」= 一像素都滚不动了;列表项自己变高变矮不再被误判成「离开底部」
    val isAtBottom by remember { derivedStateOf { !listState.canScrollForward } }
    // A-20:自动跟随由用户意图独占——上滑阅读、展开思考块都会关掉它,只有滚回真底部才恢复
    var autoFollow by remember(state.currentSessionId) { mutableStateOf(true) }
    var userScrolled by remember(state.currentSessionId) { mutableStateOf(false) }
    val nestedScrollConnection = remember(state.currentSessionId) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    userScrolled = true
                    // y > 0:手指下拖 = 往历史方向翻 → 立即停止自动跟随
                    if (available.y > 0f) autoFollow = false
                }
                return Offset.Zero
            }
        }
    }
    // 用户松手后按真实位置裁决是否恢复跟随(滚到底才恢复)
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && userScrolled) {
            userScrolled = false
            autoFollow = !listState.canScrollForward
        }
    }

    // A-20:条数增加 → 动画贴底一次;同一条流式追加 → 直接贴底,不反复起动画;
    // 流式结束且没有新消息 → 一律不动(展开思考块、复制、阅读都不能被掼回顶部)
    var prevSize by remember { mutableStateOf(-1) }
    var prevPendingCallId by remember { mutableStateOf<String?>(null) }
    val streamingLength = state.messages.lastOrNull()?.let {
        Triple(it.text.length, it.reasoning.length, it.toolTraces.size)
    }
    val pendingCallId = state.pendingConfirmation?.callId
    LaunchedEffect(state.messages.size, streamingLength, pendingCallId) {
        val n = state.messages.size
        val grew = n > prevSize
        prevSize = n
        // 工具轨迹或待确认条出现时同样贴底,避免被新增内容推出视口
        val pendingChanged = pendingCallId != prevPendingCallId
        prevPendingCallId = pendingCallId
        val last = state.messages.lastOrNull()
        // 目标永远是最末的 1dp 锚点:滚到消息本体只会把它顶到视口顶端
        val anchor = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        when {
            grew -> {
                autoFollow = true
                if (n > 0) listState.animateScrollToItem(anchor)
            }

            pendingChanged && autoFollow && !listState.isScrollInProgress ->
                listState.animateScrollToItem(anchor)

            last?.streaming == true && autoFollow && !listState.isScrollInProgress ->
                listState.scrollToItem(anchor)
        }
    }

    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                when (msg.role) {
                    AgentMessage.Role.USER -> UserMessage(
                        msg = msg,
                        notebookId = notebookId,
                        pageIds = pageIds,
                        onPreview = { preview = it },
                    )

                    AgentMessage.Role.ASSISTANT -> AssistantMessage(
                        state = state,
                        msg = msg,
                        onSaveCard = onSaveCard,
                        onWriteToNotes = onWriteToNotes,
                        onPreview = { preview = it },
                        onToast = { toast = it },
                        onToggleReasoning = { expanded -> if (expanded) autoFollow = false },
                    )
                }
            }
            if (state.error != null) {
                item(key = "err") { ErrorBanner(state.error!!) }
            }
            // 1dp 底部锚点:滚到它才是真的贴底,滚到消息本体只会把消息顶到视口顶部
            item(key = "__bottom_anchor__") { Spacer(Modifier.height(1.dp)) }
        }
        // 流式中用户上滑后:仅图标浮球「回到底部」(A-20)
        AnimatedVisibility(
            visible = !isAtBottom && state.messages.isNotEmpty(),
            enter = fadeIn(tween(if (reduced) MilkMotion.reduced else 150, easing = MilkMotion.EaseOut)) +
                if (reduced) EnterTransition.None else scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(150, easing = MilkMotion.EaseOut),
                ),
            exit = fadeOut(tween(if (reduced) MilkMotion.reduced else 110, easing = MilkMotion.EaseOut)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
        ) {
            MilkIconButton(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "回到底部",
                onClick = {
                    autoFollow = true
                    scope.launch {
                        listState.animateScrollToItem(
                            (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
                        )
                    }
                },
                container = true,
                shape = CircleShape,
                touchSize = 48.dp,
                visualSize = 34.dp,
                iconSize = 18.dp,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        MilkToast(
            visible = toast != null,
            text = toast.orEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        )
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1600)
            toast = null
        }
    }
    preview?.let { target ->
        AgentFloatingPreviewDialog(
            target = target,
            onDismiss = { preview = null },
            onSaveToNotes = { markdown -> onWriteToNotes(markdown.content) },
            onSaveCard = { markdown -> onSaveCard(markdown.content) },
            onToast = { toast = it },
        )
    }
}

/** 思考过程折叠(DESIGN-V2 msg-think):默认收起,矢量箭头 + mono 灰字「思考过程 · N 字」,展开后纯文本 + 细竖线 */
@Composable
private fun ReasoningBlock(msg: UiMessage, onExpandChanged: (Boolean) -> Unit = {}) {
    var expanded by remember(msg.id) { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val reduced = LocalMilkMotion.current
    val streamingThought = msg.streaming && msg.text.isBlank()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = if (reduced) snap() else MilkMotion.uiSpring(),
        label = "reasoningChevron",
    )
    // 高度动画只由 AnimatedVisibility 一家负责:再叠 animateContentSize 会与 LazyColumn
    // 的重测量互相追高,表现为展开瞬间整段消息被反复顶回视口顶部。
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .softClickable {
                    val next = !expanded
                    expanded = next
                    onExpandChanged(next)
                }
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
private fun UserMessage(
    msg: UiMessage,
    notebookId: String?,
    pageIds: List<String>,
    onPreview: (AgentPreviewTarget) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val bubble = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 4.dp, bottomStart = 14.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (msg.attachments.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                msg.attachments.forEach { att ->
                    MessageAttachmentChip(
                        notebookId = notebookId,
                        att = att,
                        pageIds = pageIds,
                        onClick = { onPreview(att.toPreviewTarget(notebookId, pageIds)) },
                    )
                }
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
                val annotated = remember(msg.text, cs.primary) {
                    buildMentionAnnotated(msg.text, cs.primary)
                }
                val hasMention = remember(annotated) {
                    annotated.getStringAnnotations("mention", 0, annotated.length).isNotEmpty()
                }
                if (hasMention) {
                    MentionText(
                        text = annotated,
                        style = MilkText.body.copy(color = cs.onSurface),
                        onMention = { name ->
                            val attachment = msg.attachments.firstOrNull {
                                it.mentionName == name || it.logicalName == name
                            }
                            if (attachment != null) {
                                onPreview(attachment.toPreviewTarget(notebookId, pageIds))
                            }
                        },
                    )
                } else {
                    Text(msg.text, style = MilkText.body, color = cs.onSurface)
                }
            }
        }
    }
}

private val mentionPattern = Regex("@[^\\s@]+")

/**
 * 带可点注解的正文:命中 `mention` 注解的偏移交给调用方(比已废弃的 ClickableText 更稳,
 * 且保留 Text 的全部排版能力)。
 */
@Composable
private fun MentionText(
    text: AnnotatedString,
    style: TextStyle,
    onMention: (String) -> Unit,
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style,
        onTextLayout = { layout = it },
        modifier = Modifier.pointerInput(text) {
            detectTapGestures { position ->
                val result = layout ?: return@detectTapGestures
                val offset = result.getOffsetForPosition(position)
                text.getStringAnnotations("mention", offset, offset).firstOrNull()?.let { onMention(it.item) }
            }
        },
    )
}

/** 用户消息里的 @提及:保留原文,仅给 token 上色并打上可点击注解 */
private fun buildMentionAnnotated(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    mentionPattern.findAll(text).forEach { match ->
        addStyle(
            SpanStyle(color = color, fontWeight = FontWeight.Medium),
            match.range.first,
            match.range.last + 1,
        )
        addStringAnnotation("mention", match.value.removePrefix("@"), match.range.first, match.range.last + 1)
    }
}

@Composable
private fun MessageAttachmentChip(
    notebookId: String?,
    att: Attachment,
    pageIds: List<String>,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val bitmap = rememberAttachmentBitmap(notebookId, att)
    val thumbShape = RoundedCornerShape(MilkRadius.xs)
    Row(
        Modifier
            .clip(RoundedCornerShape(MilkRadius.md))
            .background(cs.surfaceVariant.copy(alpha = if (dark) 0.85f else 0.7f))
            .border(1.dp, cs.outline, RoundedCornerShape(MilkRadius.md))
            .softClickable(onClick = onClick)
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
                if (att.pageRangeLabel.isNotBlank()) att.pageRangeLabel else "${att.widthPx}×${att.heightPx}",
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
private fun AssistantMessage(
    state: AgentUiState,
    msg: UiMessage,
    onSaveCard: (String) -> Unit,
    onWriteToNotes: (String) -> Unit,
    onPreview: (AgentPreviewTarget) -> Unit,
    onToast: (String) -> Unit,
    onToggleReasoning: (Boolean) -> Unit = {},
) {
    val agent = ServiceLocator.agentController
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
            ReasoningBlock(msg, onExpandChanged = onToggleReasoning)
            Spacer(Modifier.height(10.dp))
        }
        // 工具调用轨迹:逐条调用与结果,展开看参数与输出
        if (msg.toolTraces.isNotEmpty()) {
            MilkToolTraceList(msg.toolTraces)
            Spacer(Modifier.height(8.dp))
        }
        when {
            msg.streaming && msg.text.isBlank() && msg.reasoning.isBlank() && msg.toolTraces.isEmpty() ->
                ThinkingDots()
            else -> {
                if (msg.text.isNotBlank()) {
                    MilkMarkdownView(
                        text = msg.text,
                        streaming = msg.streaming,
                        onFormulaClick = { latex ->
                            onPreview(
                                AgentPreviewTarget.Markdown(
                                    title = "公式预览",
                                    content = latex,
                                    docType = MarkdownLite.GeneratedDocType.LATEX,
                                    canSaveToNotes = false,
                                    canSaveCard = true,
                                ),
                            )
                        },
                        onPreviewDocument = { document -> onPreview(document.toPreviewTarget()) },
                        onSaveDocumentToNotes = { document -> onWriteToNotes(document.content) },
                        onSaveDocumentCard = { document -> onSaveCard(document.content) },
                        onExportDocument = { document ->
                            scope.launch {
                                val ok = exportGeneratedDocument(context, document)
                                if (!ok) onToast("导出失败")
                            }
                        },
                    )
                }
                if (msg.streaming && msg.text.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    PulsingDot(color = cs.primary, active = true)
                }
            }
        }
        // 操作栏(DESIGN-V2 msg-bar):贴回纸面 / 预览 / 存卡片 / 复制
        when (msg.phase) {
            AgentMessage.Phase.STREAMING, AgentMessage.Phase.SENDING -> {}

            AgentMessage.Phase.FAILED -> {
                Spacer(Modifier.height(MilkSpace.xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MilkInlineStatus("失败", icon = Icons.Outlined.ErrorOutline, tone = MilkInlineTone.DANGER)
                    MilkInlineAction(
                        label = "重试",
                        onClick = { agent.retry() },
                        icon = Icons.Outlined.Refresh,
                        tone = MilkInlineTone.ACCENT,
                    )
                }
            }

            AgentMessage.Phase.INTERRUPTED -> {
                Spacer(Modifier.height(MilkSpace.xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MilkInlineStatus("已中断", showDot = true, tone = MilkInlineTone.WARNING)
                    MilkInlineAction(
                        label = "重试",
                        onClick = { agent.retry() },
                        icon = Icons.Outlined.Refresh,
                        tone = MilkInlineTone.ACCENT,
                    )
                }
            }

            AgentMessage.Phase.DONE -> {
                Spacer(Modifier.height(MilkSpace.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    if (msg.insertedAnnotationId == null) {
                        // 与「已贴回」同宽同高:贴回前后相邻控件不移动
                        MilkInlineAction(
                            label = "贴回纸面",
                            onClick = { agent.insertAnnotation(msg.id) },
                            icon = Icons.Outlined.PushPin,
                            tone = MilkInlineTone.ACCENT,
                            minWidth = 86.dp,
                        )
                    } else {
                        // 已贴回是状态不是操作:同一几何的无点击状态胶囊
                        MilkInlineStatus(
                            label = "已贴回",
                            icon = Icons.Outlined.Check,
                            tone = MilkInlineTone.SUCCESS,
                            minWidth = 86.dp,
                        )
                    }
                    MilkInlineAction(
                        label = "预览",
                        onClick = {
                            onPreview(
                                AgentPreviewTarget.Markdown(
                                    title = "AI 回复",
                                    content = msg.text,
                                    canSaveToNotes = true,
                                ),
                            )
                        },
                        icon = Icons.Outlined.Description,
                        enabled = msg.text.isNotBlank(),
                    )
                    MilkInlineAction(
                        label = "存卡片",
                        onClick = { onSaveCard(msg.text) },
                        icon = Icons.Outlined.BookmarkBorder,
                        enabled = msg.text.isNotBlank(),
                    )
                    MilkInlineAction(
                        label = if (copied) "已复制" else "复制",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("agent", msg.text))
                            copied = true
                        },
                        icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        tone = if (copied) MilkInlineTone.SUCCESS else MilkInlineTone.NEUTRAL,
                        minWidth = 74.dp,
                    )
                }
            }
        }
        // 用量元信息:模型 · prompt→completion tok · 输出速度
        val meta = formatAgentUsageMeta(
            modelId = msg.modelId,
            promptTokens = msg.promptTokens,
            completionTokens = msg.completionTokens,
            generationMs = msg.generationMs,
        )
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
                (0.3 + 0.7 * ((cos(2.0 * PI * (phase - index * 0.16f)) + 1.0) / 2.0)).toFloat()
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
    val title = errorTitle(error)
    MilkExpandableErrorBox(title = title, detail = errorDetailOrNull(title, error.detail))
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

// ---------------- 输入区(追问 / 附件 / slash / @ 提及) ----------------

@Composable
private fun Composer(
    state: AgentUiState,
    pageIds: List<String>,
    onSnipToAttachment: () -> Unit,
) {
    val agent = ServiceLocator.agentController
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val text = state.draftText
    var attachOpen by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<AgentPreviewTarget?>(null) }
    var editingDraft by remember { mutableStateOf<DraftAttachment?>(null) }
    var editingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val mentionTransform = remember(cs.primary, cs.onSurface) {
        MentionHighlightTransformation(cs.primary, cs.onSurface)
    }
    val streaming = state.isAgentStreaming()
    // 无文字且无附件 → 发送禁用(hairline 灰底弱图标、disabled 语义),不产生空请求
    val canSend = text.isNotBlank() || state.draftAttachments.isNotEmpty()

    // slash 面板(§6.4:前缀匹配意图 + 生成文档 + 清空)
    val slashSuggestions: List<Pair<String, () -> Unit>> = if (text.startsWith("/")) {
        buildList {
            Intents.all
                .filter { "/${it.label}".startsWith(text) || text == "/" }
                .forEach { spec ->
                    add(
                        "/${spec.label}" to {
                            agent.setDraftText("")
                            agent.sendWithIntent(spec.id)
                        },
                    )
                }
            if ("/生成文档".startsWith(text) || text == "/") {
                add(
                    "/生成文档" to {
                        agent.setDraftText("请根据以上内容生成一份结构化的 Markdown 文档：")
                    },
                )
            }
            if ("/清空".startsWith(text) || text == "/") {
                add(
                    "/清空" to {
                        agent.setDraftText("")
                        agent.clearSession()
                    },
                )
            }
        }
    } else {
        emptyList()
    }
    var dismissedSlash by remember { mutableStateOf<String?>(null) }

    // @ 提及补全:草稿附件优先,其次本会话已发送附件
    val mentionQuery = extractMentionQuery(text)
    val mentionCandidates: List<Pair<String, AgentPreviewTarget?>> = if (mentionQuery != null) {
        val draftNames = state.draftAttachments
            .filter { it.mentionName.isNotBlank() && it.mentionName.contains(mentionQuery, ignoreCase = true) }
            .map { it.mentionName to it.toPreviewTarget(pageIds) as AgentPreviewTarget? }
        val sentNames = state.messages
            .flatMap { it.attachments }
            .filter { it.mentionName.isNotBlank() && it.mentionName.contains(mentionQuery, ignoreCase = true) }
            .map { it.mentionName to (it.toPreviewTarget(state.boundPage?.notebookId, pageIds) as AgentPreviewTarget?) }
        (draftNames + sentNames).distinctBy { it.first }.take(6)
    } else {
        emptyList()
    }

    Box(Modifier.fillMaxWidth()) {
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
                        .milkGlassRim(RoundedCornerShape(MilkRadius.lg), LocalMilkDark.current, 10.dp)
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(4.dp),
                ) {
                    slashSuggestions.take(8).forEach { (label, action) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(MilkRadius.sm))
                                .softClickable {
                                    dismissedSlash = text
                                    action()
                                }
                                .heightIn(min = 40.dp)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, style = MilkText.monoBody, color = cs.onSurface)
                        }
                    }
                }
            }

            // @ 提及补全:同样贴在输入框上方,列出可引用的附件
            if (mentionQuery != null && mentionCandidates.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .milkGlassRim(RoundedCornerShape(MilkRadius.lg), LocalMilkDark.current, 10.dp)
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(4.dp),
                ) {
                    mentionCandidates.forEach { (name, target) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(MilkRadius.sm))
                                .softClickable {
                                    agent.setDraftText(replaceTrailingMention(text, name))
                                }
                                .heightIn(min = 40.dp)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (target is AgentPreviewTarget.Image) Icons.Outlined.Image else Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = cs.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(MilkSpace.sm))
                            Text(
                                "@$name",
                                style = MilkText.monoBody,
                                color = cs.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (target != null) {
                                Text(
                                    when (target) {
                                        is AgentPreviewTarget.Image -> "图片"
                                        is AgentPreviewTarget.Document -> formatAttachmentBadge(target.source)
                                        is AgentPreviewTarget.Markdown -> "MD"
                                    },
                                    style = MilkText.monoMicro,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // 草稿附件:真实缩略图 + ✕ 移除,随下一条消息发出;点按打开悬浮预览
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
                            onClick = { preview = draft.toPreviewTarget(pageIds) },
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

            MilkComposerField(
                value = text,
                onValueChange = {
                    if (dismissedSlash != null && !it.startsWith("/")) dismissedSlash = null
                    agent.setDraftText(it)
                },
                placeholder = "提问…",
                minLines = 1,
                maxLines = 4,
                dirty = attachOpen,
                visualTransformation = mentionTransform,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = MilkSpace.sm),
                leading = {
                    MilkIconButton(
                        imageVector = Icons.Outlined.AttachFile,
                        contentDescription = if (attachOpen) "收起附件菜单" else "添加附件",
                        onClick = { attachOpen = !attachOpen },
                        selected = attachOpen || state.draftAttachments.isNotEmpty(),
                        tint = if (state.draftAttachments.isNotEmpty() || attachOpen) {
                            cs.primary
                        } else {
                            cs.onSurfaceVariant
                        },
                        touchSize = 40.dp,
                        visualSize = 32.dp,
                        iconSize = 17.dp,
                    )
                },
                trailing = {
                    if (streaming) {
                        MilkIconButton(
                            imageVector = Icons.Outlined.Stop,
                            contentDescription = "停止",
                            onClick = { agent.stop() },
                            tint = cs.error,
                            touchSize = 40.dp,
                            visualSize = 32.dp,
                            iconSize = 17.dp,
                        )
                    } else {
                        MilkIconButton(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "发送",
                            onClick = { agent.send() },
                            enabled = canSend,
                            selected = canSend,
                            tint = if (canSend) cs.primary else cs.onSurfaceVariant,
                            touchSize = 40.dp,
                            visualSize = 32.dp,
                            iconSize = 17.dp,
                        )
                    }
                },
            )

            AttachmentFloatingMenu(
                expanded = attachOpen,
                state = state,
                pageIds = pageIds,
                onDismiss = { attachOpen = false },
                onSnipToAttachment = {
                    attachOpen = false
                    onSnipToAttachment()
                },
                onPreviewDraft = { draft ->
                    attachOpen = false
                    preview = draft.toPreviewTarget(pageIds)
                },
                anchorHeight = 56.dp,
            )
        }
        MilkToast(
            visible = toast != null,
            text = toast.orEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = MilkSpace.sm),
        )
    }

    preview?.let { target ->
        AgentFloatingPreviewDialog(
            target = target,
            onDismiss = { preview = null },
            onEditDraftImage = { draftId ->
                val draft = state.draftAttachments.firstOrNull { it.id == draftId }
                preview = null
                if (draft != null) {
                    scope.launch {
                        val bitmap = withContext(Dispatchers.Default) {
                            runCatching {
                                BitmapFactory.decodeByteArray(draft.png, 0, draft.png.size)
                            }.getOrNull()
                        }
                        if (bitmap != null) {
                            editingDraft = draft
                            editingBitmap = bitmap
                        } else {
                            toast = "无法读取该图片"
                        }
                    }
                }
            },
            onToast = { toast = it },
        )
    }

    val editing = editingBitmap
    val draftBeingEdited = editingDraft
    if (editing != null && draftBeingEdited != null) {
        ImageEditorFloatingPanel(
            source = editing,
            title = "编辑图片",
            confirmLabel = "保存修改",
            onDismiss = {
                editingBitmap = null
                editingDraft = null
                if (!editing.isRecycled) editing.recycle()
            },
            onConfirm = { encoded ->
                agent.updateDraftAttachment(
                    id = draftBeingEdited.id,
                    png = encoded.bytes,
                    jpeg = encoded.jpeg,
                    widthPx = encoded.widthPx,
                    heightPx = encoded.heightPx,
                )
                editingBitmap = null
                editingDraft = null
                if (!editing.isRecycled) editing.recycle()
            },
        )
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1600)
            toast = null
        }
    }
}

/** 草稿附件卡片:真实缩略图 + 标题 + 尺寸 + ✕ 移除;点按进入悬浮预览 */
@Composable
private fun DraftAttachmentCard(
    draft: DraftAttachment,
    pageIds: List<String>,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val bitmap = remember(draft.id, draft.png) {
        runCatching { BitmapFactory.decodeByteArray(draft.png, 0, draft.png.size)?.asImageBitmap() }.getOrNull()
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(MilkRadius.md))
            .background(cs.surfaceVariant.copy(alpha = if (dark) 0.85f else 0.7f))
            .border(1.dp, cs.outline, RoundedCornerShape(MilkRadius.md))
            .softClickable(onClick = onClick)
            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
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
                when {
                    draft.pageRangeLabel.isNotBlank() -> draft.pageRangeLabel
                    draft.widthPx > 0 || draft.heightPx > 0 -> "${draft.widthPx}×${draft.heightPx}"
                    draft.hasText -> "${draft.textContent.length} 字"
                    else -> formatAttachmentBadge(draft.source)
                },
                style = MilkText.monoMicro,
                color = cs.onSurfaceVariant,
            )
        }
        MilkIconButton(
            imageVector = Icons.Outlined.Close,
            contentDescription = "移除附件",
            onClick = onRemove,
            touchSize = 36.dp,
            visualSize = 28.dp,
            iconSize = 13.dp,
        )
    }
}

// ---------------- 模型切换 sheet(仅视觉) ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelSheet(
    state: AgentUiState,
    onDismiss: () -> Unit,
    onOpenSettings: (page: String) -> Unit,
) {
    val agent = ServiceLocator.agentController
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    // 只列有视觉模型的 Provider;搜索只在一屏放不下时出现(短列表不给多余控件)
    val profiles = remember(state.profiles) {
        state.profiles.filter { profile -> profile.models.any { it.vision } }
    }
    val visionCount = remember(profiles) { profiles.sumOf { profile -> profile.models.count { it.vision } } }
    val activeProfile = state.profiles.firstOrNull { it.id == state.activeProfileId }
    val activeModel = activeProfile?.models?.firstOrNull { it.id == state.activeModelId }
        ?: activeProfile?.activeModel()
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

    MilkCenterPanel(
        title = "模型与思考",
        onDismiss = onDismiss,
        maxWidth = 440.dp,
    ) {
        if (visionCount > 6) {
            MilkTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索模型或服务…",
                leading = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                },
                onClear = { query = "" },
            )
            Spacer(Modifier.height(MilkSpace.md))
        }
        // 模型列表:超限内部滚动(A-18,防多 Provider 长列表撑爆抽屉)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val needle = query.trim()
            profiles.forEach { profile ->
                val models = profile.models.filter {
                    it.vision && (
                        needle.isEmpty() ||
                            it.id.contains(needle, true) ||
                            it.displayName.contains(needle, true) ||
                            profile.title.contains(needle, true)
                        )
                }
                if (models.isEmpty()) return@forEach
                MilkModelProviderHeader(
                    title = profile.title,
                    protocol = profile.protocol,
                    missingKey = !agent.hasKey(profile),
                    trailingText = "${models.size} 模型",
                    modifier = Modifier.padding(top = MilkSpace.sm),
                )
                Spacer(Modifier.height(MilkSpace.xxs))
                MilkSurfaceGroup {
                    models.forEachIndexed { index, m ->
                        val selected = profile.id == state.activeProfileId && m.id == state.activeModelId
                        if (index > 0) {
                            val prevSelected = profile.id == state.activeProfileId &&
                                models[index - 1].id == state.activeModelId
                            MilkGroupItemDivider(
                                visible = !selected && !prevSelected,
                                startIndent = 44.dp,
                            )
                        }
                        MilkModelSelectRow(
                            displayName = m.displayName.ifBlank { m.id },
                            modelId = m.id,
                            selected = selected,
                            protocol = m.protocol ?: profile.protocol,
                            protocolOverridden = m.protocol != null,
                            vision = m.vision,
                            contextWindowK = m.contextWindowK,
                            onClick = {
                                agent.selectModel(profile.id, m.id)
                                onDismiss()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(MilkSpace.sm))
            }
        }
        Spacer(Modifier.height(MilkSpace.md))
        MilkHairlineDivider()
        Spacer(Modifier.height(MilkSpace.md))
        // ---- 思考强度(§3.8:每家档位不同,按当前模型声明显示) ----
        MilkSectionLabel(text = "思考强度")
        Spacer(Modifier.height(MilkSpace.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
            verticalArrangement = Arrangement.spacedBy(MilkSpace.xs),
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
        MilkHairlineDivider()
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

// ---------------- Markdown 渲染(统一交给 MilkMarkdownView) ----------------

/** 兼容旧调用点:完整 Markdown + LaTeX 渲染统一由 [MilkMarkdownView] 承担 */
@Composable
fun MarkdownBlocks(text: String, modifier: Modifier = Modifier, onWikilink: ((String) -> Unit)? = null) {
    MilkMarkdownView(text = text, modifier = modifier, onWikilink = onWikilink)
}

/**
 * 输入框 @ 提及高亮:只着色 `@token` 片段,映射保持 1:1(OffsetMapping.Identity),
 * 因此光标位置与 IME 组合输入完全不受影响。
 */
internal class MentionHighlightTransformation(
    private val highlight: Color,
    private val fallback: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        builder.addStyle(SpanStyle(color = fallback), 0, text.length)
        mentionPattern.findAll(text.text).forEach { match ->
            builder.addStyle(
                SpanStyle(color = highlight, fontWeight = FontWeight.Medium),
                match.range.first,
                match.range.last + 1,
            )
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
