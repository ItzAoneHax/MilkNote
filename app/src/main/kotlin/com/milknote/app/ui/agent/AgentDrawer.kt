package com.milknote.app.ui.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.milknote.app.ui.theme.Mono
import kotlinx.coroutines.launch
import com.milknote.app.ui.common.accentBrush
import com.milknote.app.ui.common.innerHighlight
import com.milknote.app.ui.common.paintedShadow
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
) {
    val agent = ServiceLocator.agentController
    val state by agent.state.collectAsState()
    val scope = rememberCoroutineScope()
    val reduced = LocalMilkMotion.current
    val density = LocalDensity.current

    // ---- 宽度状态:默认 360dp,可拖 320–480dp(A-35) ----
    val minW = with(density) { 320.dp.toPx() }
    val maxW = with(density) { 480.dp.toPx() }
    val widthAnim = remember { Animatable(with(density) { 360.dp.toPx() }) }
    // ---- 头部右滑关闭位移(px);判定关闭后随速度滑出,收抽屉时不再播退出动画 ----
    val offsetAnim = remember { Animatable(0f) }
    var swipedClosePending by remember { mutableStateOf(false) }

    val widthDragState = rememberDraggableState { delta ->
        // 拖动中直接更新宽度,无动画(红线三.7)。抽屉右锚定:左缘向左拖(delta<0)=加宽
        scope.launch { widthAnim.snapTo((widthAnim.value - delta).coerceIn(minW, maxW)) }
    }
    val headerDragState = rememberDraggableState { delta ->
        scope.launch { offsetAnim.snapTo((offsetAnim.value + delta).coerceIn(0f, widthAnim.value)) }
    }

    AnimatedVisibility(
        visible = state.drawerOpen,
        enter = if (reduced) fadeIn(tween(60))
        else slideInHorizontally(tween(220)) { it } + fadeIn(tween(160)),
        exit = if (swipedClosePending) ExitTransition.None   // 滑关:已随手指离屏,不播退出动画(避免二次位移)
        else if (reduced) fadeOut(tween(60))
        else slideOutHorizontally(tween(200)) { it } + fadeOut(tween(140)),
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
                headerDragModifier = Modifier.draggable(
                    orientation = Orientation.Horizontal,
                    state = headerDragState,
                    onDragStopped = { velocity ->
                        val closeByDistance = offsetAnim.value > widthAnim.value * 0.4f
                        val closeByVelocity = velocity > with(density) { 1200.dp.toPx() }
                        if (closeByDistance || closeByVelocity) {
                            scope.launch {
                                if (reduced) offsetAnim.snapTo(widthAnim.value)
                                else offsetAnim.animateTo(
                                    widthAnim.value,
                                    spring(dampingRatio = 1f, stiffness = 550f),
                                    initialVelocity = velocity,
                                )
                                swipedClosePending = true
                                agent.closeDrawer()
                                offsetAnim.snapTo(0f)   // 已不可见,复位不影响视觉
                                swipedClosePending = false
                            }
                        } else {
                            scope.launch {
                                if (reduced) offsetAnim.snapTo(0f)
                                else offsetAnim.animateTo(0f, spring(dampingRatio = 1f, stiffness = 550f), initialVelocity = velocity)
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
                        onDragStopped = { velocity ->
                            val settle = sequenceOf(minW, with(density) { 360.dp.toPx() }, maxW)
                                .minBy { kotlin.math.abs(it - widthAnim.value) }
                            scope.launch {
                                if (reduced) widthAnim.snapTo(settle)
                                else widthAnim.animateTo(settle, spring(dampingRatio = 1f, stiffness = 500f), initialVelocity = velocity)
                            }
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
    headerDragModifier: Modifier = Modifier,
) {
    // 宿主在书写页画布区(顶栏下/底部状态条上,DESIGN-V0 §5.4);容器已吃 safeDrawing
    // (含 IME)inset,此处不再重复 padding,否则顶进状态栏/盖住底条(真机实证)
    val density = LocalDensity.current
    Column(
        Modifier
            .width(with(density) { widthPx.toDp() })
            .fillMaxHeight()
            .graphicsLayer { translationX = dragOffsetPx }
            .softShadow(12.dp, RectangleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, MaterialTheme.colorScheme.outline)
            .paintedShadow(RectangleShape, edge = 14.dp),
    ) {
        DrawerHeader(state, headerDragModifier, onOpenSettings)
        if (state.setupRequired) {
            SetupGuide(onOpenSettings)
        } else {
            if (state.messages.isEmpty()) {
                EmptyAgentActions(
                    onSnip = onSnip,
                    onExplainPage = onExplainPage,
                    onSummarizeChapter = onSummarizeChapter,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Messages(state, Modifier.weight(1f))
            }
            Composer(state)
            DrawerHints(state)
        }
    }
}

/** 空会话引导(A-19):只给三个真实动作 + 一句最小上传说明;composer 仍可自定义提问 */
@Composable
private fun EmptyAgentActions(
    onSnip: () -> Unit,
    onExplainPage: () -> Unit,
    onSummarizeChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("随写随问", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "动作会把对应页面快照(含手写层)发给已连接的视觉模型",
            fontSize = 11.sp, lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        EmptyAction("框选讲解", primary = true, onSnip)
        Spacer(Modifier.height(8.dp))
        EmptyAction("讲解本页", primary = false, onExplainPage)
        Spacer(Modifier.height(8.dp))
        EmptyAction("归纳本章", primary = false, onSummarizeChapter)
    }
}

@Composable
private fun EmptyAction(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (primary) Modifier.background(accentBrush())
                else Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) Color.White else MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun DrawerHeader(
    state: AgentUiState,
    headerDragModifier: Modifier = Modifier,
    onOpenSettings: (page: String) -> Unit = {},
) {
    val agent = ServiceLocator.agentController
    var showModels by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .innerHighlight(strength = 0.25f)
            .then(headerDragModifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Agent", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(10.dp))
        // 模型胶囊:模型名 mono + › 切换
        Row(
            Modifier
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { showModels = !showModels }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.activeModelId ?: "未配置",
                fontFamily = Mono, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            // 思考档位徽标(AUTO 不显示)
            state.profiles.firstOrNull { it.id == state.activeProfileId }?.let { pf ->
                val eff = runCatching { com.milknote.agent.ReasoningEffort.valueOf(pf.reasoningEffort) }
                    .getOrDefault(com.milknote.agent.ReasoningEffort.AUTO)
                if (eff != com.milknote.agent.ReasoningEffort.AUTO) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "思考·${effortLabel(eff)}",
                        fontFamily = Mono, fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        // 关闭:48dp 命中(A-9),图标 16dp
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable { agent.closeDrawer() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, "收起", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    AnimatedVisibility(
        visible = showModels,
        enter = if (LocalMilkMotion.current) fadeIn(tween(60))
        else expandVertically(tween(200)) + fadeIn(tween(200)),
        exit = if (LocalMilkMotion.current) fadeOut(tween(60))
        else shrinkVertically(tween(180)) + fadeOut(tween(140)),
    ) {
        ModelSheet(
            state,
            onDismiss = { showModels = false },
            onOpenSettings = onOpenSettings,
        )
    }
}

/** 无可用视觉模型 → 引导卡(§3.4:入口即视觉,不显示空对话) */
@Composable
private fun SetupGuide(onOpenSettings: (page: String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("配置视觉模型后开始", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "MilkNote 的全部 Agent 能力依赖多模态视觉模型。\n设置 → Agent:选厂商预设,填入你的 API Key。",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(accentBrush())
                .clickable(onClick = { onOpenSettings("agent") })
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("去设置", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** 上下文 chips:选区 N / 当前页 p.X / +整章 + 预估(§5.2);颜色 200ms 过渡 */
@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "chipBg",
    )
    val borderC by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
        animationSpec = tween(200),
        label = "chipBorder",
    )
    Box(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, borderC, CircleShape)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

// ---------------- 消息区(§8.4 流式渲染) ----------------

@Composable
private fun Messages(state: AgentUiState, modifier: Modifier = Modifier) {
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
                    AgentMessage.Role.ASSISTANT -> AssistantMessage(state, msg)
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
                Text("↓ 回到底部", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 思考过程折叠胶囊(默认收起;mono 灰,展开后内衬浅底;展开 200ms 过渡) */
@Composable
private fun ReasoningBlock(msg: com.milknote.agent.UiMessage) {
    var expanded by remember(msg.id) { mutableStateOf(false) }
    val streamingThought = msg.streaming && msg.text.isBlank()
    Column(Modifier.fillMaxWidth().animateContentSize(tween(200))) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (streamingThought) "✻ 思考中…" else "✻ 思考过程 · ${msg.reasoning.length} 字",
                fontFamily = Mono, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(if (expanded) "▾" else "▸", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            Text(
                msg.reasoning,
                fontFamily = Mono, fontSize = 10.sp, lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Description, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(att.logicalName, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${att.widthPx}×${att.heightPx}px · 已含手写层与批注",
                        fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (msg.text.isNotBlank()) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(msg.text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun AssistantMessage(state: AgentUiState, msg: com.milknote.agent.UiMessage) {
    val agent = ServiceLocator.agentController
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        // 思考过程:默认折叠;流式中(答案未出)显示「思考中…」,完成后显示字数
        if (msg.reasoning.isNotBlank()) {
            ReasoningBlock(msg)
            Spacer(Modifier.height(6.dp))
        }
        MarkdownBlocks(msg.text + if (msg.streaming) " ▍" else "")
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // meta 行:模型 · tokens(Codex 风 mono)
            val meta = buildString {
                append(msg.modelId ?: "")
                if (msg.promptTokens > 0 || msg.completionTokens > 0) {
                    append(" · ${(msg.promptTokens / 100.0).let { if (it >= 10) it.toInt() else String.format("%.1f", it).toFloat() }}k→")
                    append("${(msg.completionTokens / 100.0).let { if (it >= 10) it.toInt() else String.format("%.1f", it).toFloat() }}k tok")
                }
            }
            if (meta.isNotBlank()) {
                Text(meta, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
        }
        // 状态与操作 chips(§9):复制 / 插入批注层(幂等)
        when (msg.phase) {
            AgentMessage.Phase.STREAMING -> {}
            AgentMessage.Phase.FAILED -> ActionChip("重试", accent = true) { agent.retry() }
            AgentMessage.Phase.INTERRUPTED -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("已中断", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionChip("重试", accent = true) { agent.retry() }
            }
            AgentMessage.Phase.DONE -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip("复制", icon = Icons.Outlined.ContentCopy) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("agent", msg.text))
                }
                if (msg.insertedAnnotationId == null) {
                    ActionChip("插入批注层", accent = true) { agent.insertAnnotation(msg.id) }
                } else {
                    // 已插入是状态不是操作:静态胶囊,不注册点击(空回调红线)
                    StaticChip("已插入批注层 ✓")
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
    val bg by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            accent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        },
        animationSpec = tween(200),
        label = "actionChipBg",
    )
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Spacer(Modifier.width(10.dp))
            Icon(it, null, modifier = Modifier.size(12.dp), tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline)
        }
        Text(
            label, fontSize = 11.sp,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                accent -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(start = if (icon != null) 4.dp else 10.dp, end = 10.dp),
        )
    }
}

/** 状态展示胶囊(无点击语义) */
@Composable
private fun StaticChip(label: String) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun ErrorBanner(error: AgentError) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(errorTitle(error), fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
            Text(error.detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
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

@Composable
private fun Composer(state: AgentUiState) {
    val agent = ServiceLocator.agentController
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var showAttach by remember { mutableStateOf(false) }
    val streaming = state.phase == AgentMessage.Phase.STREAMING || state.phase == AgentMessage.Phase.SENDING
    // A-23:无文字且无附件 → 发送禁用(灰底弱图标、disabled 语义),不产生空请求;48dp 命中保留
    val canSend = text.isNotBlank() || state.draftAttachments.isNotEmpty()

    // slash 面板(§6.4:前缀匹配六意图 + /清空)
    val slashSuggestions = if (text.startsWith("/")) {
        listOfNotNull(
            *Intents.all.filter { "/${it.label}".startsWith(text) || text == "/" }
                .map { "/${it.label}" to it.id }.toTypedArray(),
            if ("/清空".startsWith(text)) "/清空" to null else null,
        )
    } else emptyList()

    Column(Modifier.fillMaxWidth()) {
        // 📎 附件面板(ZCode 式:选当前页/整章/任意页,多点累积)
        if (showAttach) {
            AttachmentPanel(
                boundPage = state.boundPage,
                onPickPage = { ref ->
                    scope.launch { agent.addPageAttachment(ref) }
                },
                onPickCurrent = {
                    state.boundPage?.let { ref -> scope.launch { agent.addPageAttachment(ref) } }
                },
                onPickChapter = {
                    scope.launch {
                        val n = agent.addChapterAttachments()
                        if (n == 0) agent.setDraftText(state.draftText)   // 触发重组提示
                    }
                },
                onClose = { showAttach = false },
            )
            Spacer(Modifier.height(6.dp))
        }
        // 附件卡片行:挂在输入框上方,✕ 可移除,随下一条消息发出
        if (state.draftAttachments.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.draftAttachments.forEach { d ->
                    DraftAttachmentCard(d) { agent.removeDraftAttachment(d.id) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(
                    "${state.draftAttachments.size} 个附件 · ≈${agent.estTokensNow()} tok",
                    fontFamily = Mono, fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        if (slashSuggestions.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                slashSuggestions.take(7).forEach { (label, intent) ->
                    Chip(label, selected = false) {
                        if (intent != null) {
                            text = ""
                            agent.sendWithIntent(intent)
                        } else {
                            text = ""
                            agent.clearSession()
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        var composerFocused by remember { mutableStateOf(false) }
        val composerBorder by animateColorAsState(
            targetValue = if (composerFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outline,
            animationSpec = tween(200),
            label = "composerBorder",
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, composerBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 回形针:附件面板(48dp 命中,图标 20dp)
            Box(
                Modifier
                    .size(48.dp)
                    .clickable { showAttach = !showAttach },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.AttachFile,
                    if (showAttach) "收起附件面板" else "附件",
                    modifier = Modifier.size(20.dp),
                    tint = if (showAttach) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(
                        "继续追问,或输入 / 唤起命令…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { composerFocused = it.isFocused },
                )
            }
            Spacer(Modifier.width(4.dp))
            if (streaming) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { agent.stop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Stop, "停止", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            } else {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .then(
                            if (canSend) Modifier.background(accentBrush())
                            else Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                        )
                        .clickable(enabled = canSend) {
                            agent.setDraftText(text)
                            agent.send()
                            text = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send, "发送",
                        modifier = Modifier.size(15.dp),
                        tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/** 📎 附件面板:当前页 / 整章 / 本笔记本任意页(可多点累积;ZCode 式选择列表) */
@Composable
private fun AttachmentPanel(
    boundPage: com.milknote.agent.PageRef?,
    onPickPage: (com.milknote.agent.PageRef) -> Unit,
    onPickCurrent: () -> Unit,
    onPickChapter: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pages by remember { mutableStateOf<List<com.milknote.agent.PageRef>>(emptyList()) }
    LaunchedEffect(boundPage?.notebookId) {
        boundPage?.let {
            pages = runCatching { ServiceLocator.agentSnipSource.pagesOf(it.notebookId) }.getOrDefault(emptyList())
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .innerHighlight(strength = 0.25f)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("附件", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.weight(1f))
            // 关闭:48dp 命中(A-9)
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        // 快捷:当前页 / 整章
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AttachPick("当前页 ${boundPage?.pageId ?: ""}") { onPickCurrent() }
            AttachPick("整章 · 其余 ${maxOf(pages.size - 1, 0)} 页") { onPickChapter() }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "本笔记本页面(点按附加,可多选)",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(4.dp))
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
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPickPage(pg) }
                        .defaultMinSize(minHeight = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(pg.pageId, fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 8.dp))
                    Spacer(Modifier.weight(1f))
                    Text("+ 附加", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun AttachPick(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

/** 草稿附件卡片(logicalName + 尺寸 + ✕ 移除;移除命中扩大) */
@Composable
private fun DraftAttachmentCard(d: com.milknote.agent.DraftAttachment, onRemove: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Description, null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(d.logicalName, fontFamily = Mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(32.dp)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "${d.widthPx}×${d.heightPx}px" + if (d.source == com.milknote.agent.Attachment.Source.SNIP) " · 选区" else "",
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun DrawerHints(state: AgentUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "笔键长按 再次框选 · 结果可贴回批注层",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.weight(1f))
        Text(
            state.boundPage?.pageId ?: "",
            fontFamily = Mono, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .softShadow(8.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .innerHighlight(strength = 0.25f)
            .paintedShadow(RoundedCornerShape(14.dp), edge = 10.dp)
            .padding(vertical = 8.dp),
    ) {
        // 模型列表:超限内部滚动(A-18,防多 Provider 长列表撑爆抽屉)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            state.profiles.forEach { profile ->
                Text(
                    profile.title,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                profile.models.filter { it.vision }.forEach { m ->
                    val active = profile.id == state.activeProfileId && m.id == state.activeModelId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                agent.selectModel(profile.id, m.id)
                                onDismiss()
                            }
                            .defaultMinSize(minHeight = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .padding(start = 12.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(m.id, fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                    }
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
        Spacer(Modifier.height(8.dp))
        Text(
            "思考强度 · ${activeModel?.id ?: state.activeModelId ?: ""}",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
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
        Text(
            "档位映射因厂商而异;自动=不发参数。完整说明见 设置 · Agent。",
            fontSize = 10.sp, lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(4.dp))
        // A-17:真正进入 设置 · Agent(此前只关面板不导航)
        Box(
            Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    onDismiss()
                    onOpenSettings("agent")
                }
                .defaultMinSize(minHeight = 48.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "管理模型 → 设置 · Agent",
                fontSize = 10.sp,
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
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        animationSpec = tween(200),
        label = "effortChipBg",
    )
    Box(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

// ---------------- Markdown 渲染(§6.3 容错子集) ----------------

@Composable
fun MarkdownBlocks(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { MarkdownLite.parse(text) }
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { i, b ->
            when (b) {
                is MarkdownLite.Block.Heading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(remember(b) { spansToAnnotated(b.spans) }, fontWeight = FontWeight.SemiBold, fontSize = (18 - b.level * 2).coerceIn(11, 16).sp)
                }
                is MarkdownLite.Block.ListItem -> Row {
                    Text(
                        b.order?.let { "$it. " } ?: "•  ",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(remember(b) { spansToAnnotated(b.spans) }, fontSize = 13.sp, lineHeight = 19.sp)
                }
                is MarkdownLite.Block.Paragraph -> Text(
                    remember(b) { spansToAnnotated(b.spans) },
                    fontSize = 13.sp, lineHeight = 19.sp,
                )
                is MarkdownLite.Block.MathBlock -> Box(
                    Modifier
                        .padding(vertical = 3.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(b.latex, fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                is MarkdownLite.Block.TagLine -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    b.tags.take(8).forEach { tag ->
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text("#$tag", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (i != blocks.lastIndex) Spacer(Modifier.height(4.dp))
        }
    }
}

private fun spansToAnnotated(spans: List<MarkdownLite.Span>): AnnotatedString {
    val builder = AnnotatedString.Builder()
    for (s in spans) {
        when (s) {
            is MarkdownLite.Span.Plain -> builder.append(s.text)
            is MarkdownLite.Span.Bold -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                builder.append(s.text)
                builder.pop()
            }
            is MarkdownLite.Span.Code -> {
                builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x14000000)))
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
