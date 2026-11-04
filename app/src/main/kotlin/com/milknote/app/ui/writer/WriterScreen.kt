package com.milknote.app.ui.writer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.milknote.app.di.ServiceLocator
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.milknote.app.ui.agent.AgentDrawerHost
import com.milknote.app.ui.agent.SnipOverlay
import com.milknote.app.ui.common.paintedShadow
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.Mono
import com.milknote.app.ui.theme.PillShape
import com.milknote.device.pen.PenAction
import com.milknote.ink.input.InkAction
import com.milknote.ink.model.InkTool
import com.milknote.ink.render.InkSurfaceView

internal val WriterTopBarHeight = 72.dp

/** ③ 书写页:顶栏 / 侧栏(页面缩略) / 纸面画布(DESIGN-V0 §5.3–5.4) */
@Composable
fun WriterScreen(
    vm: WriterViewModel,
    dark: Boolean,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenPalette: () -> Unit = {},
    onOpenSettings: (page: String) -> Unit = {},
    onOpenExport: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val thumbs by vm.thumbs.collectAsState()
    val battery by ServiceLocator.penBattery.level.collectAsState()
    val batterySource by ServiceLocator.penBattery.sourceName.collectAsState()
    val penConnected = batterySource != null || battery >= 0   // GATT 已连笔 → 手指不写
    // Agent 顶栏按钮状态(A-16):只订阅派生布尔流,流式 token 不触发书写页重组
    val agentDrawerOpen by remember {
        ServiceLocator.agentController.state.map { it.drawerOpen }.distinctUntilChanged()
    }.collectAsState(false)
    val agentBusy by remember {
        ServiceLocator.agentController.state.map {
            it.phase == com.milknote.agent.AgentMessage.Phase.STREAMING ||
                it.phase == com.milknote.agent.AgentMessage.Phase.SENDING
        }.distinctUntilChanged()
    }.collectAsState(false)
    // 专注模式(F-07):chrome 以「同尺寸背景占位」退出,纸张 bounds/坐标严格不变
    var focusMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.toasts.collect { toast = it.text }
    }

    // 笔键接管兜底:应用内更新(PackageInstaller)会清掉 adb 授予的
    // WRITE_SECURE_SETTINGS —— 检测并补写/提示,避免静默降级
    val writerCtx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if (com.milknote.app.pen.PenKeyTakeover.canWrite(writerCtx)) {
            com.milknote.app.pen.PenKeyTakeover.set(writerCtx, true)
        } else {
            vm.toast("笔键长按接管未激活(更新后需重新 adb 授权),长按将弹系统提示")
        }
    }

    // 接管生命周期闭环:MainActivity.onStop(退后台)会把 gb_boosting 写回 0 恢复系统行为;
    // 直接恢复到书写页(不走页面导航)时在此补写,不依赖 DisposableEffect(screen) 重跑
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                com.milknote.app.pen.PenKeyTakeover.canWrite(writerCtx)
            ) {
                com.milknote.app.pen.PenKeyTakeover.set(writerCtx, true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(toast) {
        if (toast != null) { kotlinx.coroutines.delay(2000); toast = null }
    }

    // Agent toast 并入书写页轻提示(chips 截断/插入批注等)
    LaunchedEffect(Unit) {
        var last: String? = null
        ServiceLocator.agentController.state.collect { st ->
            val t = st.toast
            if (t != null && t != last) { last = t; toast = t; ServiceLocator.agentController.consumeToast() }
        }
    }

    // 画布引用(④ 框选遮罩需要视口↔页坐标变换)
    val surfaceRef = remember { mutableStateOf<InkSurfaceView?>(null) }
    var motionIndex by remember { mutableStateOf<Int?>(null) }
    var detailTool by remember { mutableStateOf<InkTool?>(null) }
    var detailColor by remember { mutableStateOf<Int?>(null) }
    var showTextEditor by remember { mutableStateOf(false) }
    var showShapeSettings by remember { mutableStateOf(false) }
    var shapeConfig by remember { mutableStateOf<ShapeConfig?>(null) }
    var showRecentImages by remember { mutableStateOf(false) }
    val onMotion: (Int, Boolean) -> Unit = { index, hovered ->
        if (hovered) motionIndex = index else if (motionIndex == index) motionIndex = null
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)   // 背景先铺满(含状态栏区域)
            .windowInsetsPadding(WindowInsets.safeDrawing),     // 内容再避开 insets
    ) {
        // 画布延伸至顶部,工具岛仅作为覆盖层悬浮;隐藏 chrome 不会改变纸面 bounds。
        Box(Modifier.fillMaxWidth().weight(1f)) {
        Row(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            // 专注模式:侧栏/竖轨换成同宽背景占位,画布 bounds 不变
            if (focusMode) {
                if (state.sidebarVisible) {
                    Spacer(Modifier.width(148.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background))
                }
                Spacer(Modifier.width(64.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background))
            } else {
                androidx.compose.animation.AnimatedVisibility(visible = state.sidebarVisible) {
                    PageSidebar(
                        state = state,
                        thumbs = thumbs,
                        onBack = onBack,
                        onSwitch = vm::switchPage,
                        onNewPage = vm::newPage,
                    )
                }
            WriterLeftRail(
                state = state,
                enabled = state.snip.phase == SnipPhase.IDLE,
                motionIndex = motionIndex,
                onMotion = onMotion,
                onUndo = vm::undo,
                onRedo = vm::redo,
                onColor = vm::setColor,
                onColorDetail = { detailColor = it },
            )
            }
            // ---- 纸面画布 ----
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            ) {
                PaperCanvas(vm, state, dark, penConnected, surfaceRef)
                // 纸面暗角(纯显示层,不参与导出)
                Box(Modifier.matchParentSize().paperVignette(dark))
                shapeConfig?.let { config ->
                    if (state.snip.phase == SnipPhase.IDLE) {
                        ShapeOverlay(
                            config = config,
                            onComplete = { start, end ->
                                surfaceRef.value?.let { surface ->
                                    vm.addShape(config, surface.viewToPage(start.x, start.y), surface.viewToPage(end.x, end.y))
                                }
                            },
                            onCancel = { shapeConfig = null },
                        )
                    }
                }
                // ④ 框选遮罩(冻结画布+压暗;AGENT-BACKEND §4.2)
                if (state.snip.phase != com.milknote.app.ui.writer.SnipPhase.IDLE) {
                    SnipOverlay(
                        state = state.snip,
                        surface = { surfaceRef.value },
                        onDrag = vm::snipDrag,
                        onRelease = vm::snipRelease,
                        onRectChanged = vm::snipRectChanged,
                        onResetDrag = vm::snipResetDrag,
                        onFullPage = vm::snipFullPage,
                        onCancel = vm::exitSnip,
                        onIntent = vm::snipPickIntent,
                        onSend = vm::snipSend,
                    )
                }
                // ⑤ Agent 抽屉(画布区右缘浮层,DESIGN-V0 §5.4)
                AgentDrawerHost(
                    onOpenSettings = onOpenSettings,
                    onSnip = vm::enterSnip,
                    onExplainPage = { quickAgentIntent(vm, com.milknote.agent.Intents.Id.EXPLAIN) },
                    onSummarizeChapter = { quickAgentIntent(vm, com.milknote.agent.Intents.Id.SUMMARIZE) },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(top = WriterTopBarHeight),
                )
                // 混合选区操作条:锚定当前选框,空间不足时自动换到下方。
                SelectionBar(
                    visible = state.selectionCount > 0 && state.snip.phase == SnipPhase.IDLE,
                    box = state.selectionBox,
                    surface = surfaceRef.value,
                    availableWidthPx = constraints.maxWidth,
                    availableHeightPx = constraints.maxHeight,
                    canMoveUp = state.selectionCanMoveUp,
                    canMoveDown = state.selectionCanMoveDown,
                    snapEnabled = state.selectionSnapEnabled,
                    onToggleSnap = vm::toggleSelectionSnap,
                    onMoveUp = { vm.reorderSelection(true) },
                    onMoveDown = { vm.reorderSelection(false) },
                    onSend = vm::sendSelectionToAgent,
                    onDelete = vm::deleteSelection,
                    onClear = vm::clearSelection,
                )
                if (state.snip.phase == SnipPhase.IDLE) {
                    PageTurnControls(
                        state = state,
                        onPrevious = vm::previousPage,
                        onNext = vm::nextPage,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                    )
                }
                // 轻提示 toast:底部滑入
                var lastToast by remember { mutableStateOf("") }
                toast?.let { lastToast = it }
                ToastBar(
                    visible = toast != null,
                    text = lastToast,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 82.dp),
                )
            }
        }

        if (!focusMode) {
            WriterTopBar(
                state = state,
                vm = vm,
                battery = battery,
                onBack = onBack,
                onOpenDiagnostics = onOpenDiagnostics,
                onAgent = {
                    if (agentDrawerOpen) ServiceLocator.agentController.closeDrawer() else openAgentDrawer(vm)
                },
                onOpenPalette = onOpenPalette,
                sidebarVisible = state.sidebarVisible,
                onToggleSidebar = vm::toggleSidebar,
                motionIndex = motionIndex,
                onMotion = onMotion,
                onPenDetail = { detailTool = it },
                onText = { showTextEditor = true },
                onShapes = { showShapeSettings = true },
                onImages = { showRecentImages = true },
                onExport = onOpenExport,
                onCustomize = { onOpenSettings("writing") },
                onEnterFocus = { focusMode = true },
                agentOpen = agentDrawerOpen,
                agentBusy = agentBusy,
            )
        }
        }
    }

    // 专注模式退出:显式 48dp 触控按钮(F-07:不把双击纸面当主路径);系统 BACK 同效
    if (focusMode) {
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 14.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .softClickable { focusMode = false }
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("专注中", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { focusMode = false },
                contentAlignment = Alignment.Center,
            ) {
                Text("退出", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        androidx.activity.compose.BackHandler(enabled = true) { focusMode = false }
    }

    detailTool?.let { tool ->
        PenSettingsDialog(
            tool = tool,
            settings = state.toolSettings,
            onDismiss = { detailTool = null },
            onWidth = vm::setWidth,
            onSelectColor = vm::setColor,
            onColorDetail = { detailColor = it },
        )
    }
    detailColor?.let { index ->
        state.toolSettings.palettes[state.tool.tool]?.getOrNull(index)?.let { color ->
            ColorSettingsDialog(
                initialColor = color,
                onDismiss = { detailColor = null },
                onColorChange = { vm.setPaletteColor(index, it) },
            )
        }
    }
    if (showTextEditor) {
        TextBoxDialog(
            onDismiss = { showTextEditor = false },
            onCreate = {
                vm.createTextCard(it)
                showTextEditor = false
            },
        )
    }
    if (showShapeSettings) {
        ShapeSettingsDialog(
            initialColor = state.tool.color,
            initialWidth = state.tool.baseWidthMm,
            onDismiss = { showShapeSettings = false },
            onStart = {
                shapeConfig = it
                showShapeSettings = false
            },
        )
    }
    if (showRecentImages) {
        RecentImagesDialog(
            onDismiss = { showRecentImages = false },
            onSelect = vm::insertImage,
        )
    }
    }
}

/** 顶栏:浅色悬浮导航岛 / 原笔具与粗细书写岛 / 创作·状态·Agent。 */
@Composable
private fun WriterTopBar(
    state: WriterUiState,
    vm: WriterViewModel,
    battery: Int,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onAgent: () -> Unit,
    onOpenPalette: () -> Unit = {},
    sidebarVisible: Boolean,
    onToggleSidebar: () -> Unit,
    motionIndex: Int?,
    onMotion: (Int, Boolean) -> Unit,
    onPenDetail: (InkTool) -> Unit,
    onText: () -> Unit,
    onShapes: () -> Unit,
    onImages: () -> Unit,
    onExport: () -> Unit,
    onCustomize: () -> Unit,
    onEnterFocus: () -> Unit = {},
    agentOpen: Boolean = false,
    agentBusy: Boolean = false,
) {
    val snipActive = state.snip.phase != SnipPhase.IDLE
    var moreMenuOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(WriterTopBarHeight)
            .padding(horizontal = 16.dp),
    ) {
        val showNotebookName = maxWidth >= 1080.dp
        val leftWidth = 113.dp + (if (showNotebookName) 192.dp else 0.dp)
        val pinCapacity = (state.toolbarPins.size downTo 0).firstOrNull { count ->
            val rightWidth = 108.dp + 52.dp * count
            393.dp + maxOf(leftWidth, rightWidth) * 2 <= maxWidth
        } ?: 0
        val visibleActions = state.toolbarPins.take(pinCapacity)
        val rightWidth = 108.dp + 52.dp * pinCapacity
        val balance = rightWidth - leftWidth
        val menuActions = WriterToolbarAction.entries.filter { it !in visibleActions }
        val performAction: (WriterToolbarAction) -> Unit = { action ->
            when (action) {
                WriterToolbarAction.LASSO -> vm.toggleLasso()
                WriterToolbarAction.TEXT -> onText()
                WriterToolbarAction.SHAPES -> onShapes()
                WriterToolbarAction.IMAGE -> onImages()
                WriterToolbarAction.FOCUS -> onEnterFocus()
                WriterToolbarAction.NEW_PAGE -> vm.newPage()
                WriterToolbarAction.EXPORT -> onExport()
                WriterToolbarAction.COMMAND -> onOpenPalette()
                WriterToolbarAction.DIAGNOSTICS -> onOpenDiagnostics()
            }
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            WriterToolIsland(Modifier.height(48.dp)) {
                IconToggleButton(
                    icon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回书架", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    enabled = true, onClick = onBack,
                )
                Spacer(Modifier.width(4.dp))
                WriterTopBarDivider()
                Spacer(Modifier.width(4.dp))
                IconToggleButton(
                    icon = { Icon(Icons.Outlined.ViewSidebar, if (sidebarVisible) "收起页面侧栏" else "展开页面侧栏", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    enabled = true, onClick = onToggleSidebar,
                )
                // 本名收进左侧导航岛,返回/侧栏/本名同处一个悬浮元素。
                if (showNotebookName) {
                    Spacer(Modifier.width(4.dp))
                    WriterTopBarDivider()
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.notebook?.name ?: "",
                        modifier = Modifier.widthIn(max = 168.dp).padding(end = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (balance > 0.dp) Spacer(Modifier.width(balance))

            Spacer(Modifier.width(8.dp))
            Spacer(Modifier.weight(1f))

            // 原笔具组件与交互不变,只增加悬浮托盘;粗细仍保持三个直接入口。
            WriterToolIsland(Modifier.height(60.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                val modeActive = state.lassoArmed || state.selectionCount > 0
                writingTools.forEachIndexed { index, tool ->
                    val active = !modeActive && !state.toolSettings.eraserTemporary && state.tool.tool == tool
                    val label = when (tool) {
                        InkTool.PEN -> "钢笔"
                        InkTool.BALL -> "圆珠笔"
                        InkTool.HIGHLIGHTER -> "荧光笔"
                        else -> ""
                    }
                    PenToolButton(
                        tool = tool,
                        label = label,
                        color = state.toolSettings.configs.getValue(tool).color,
                        active = active,
                        enabled = !snipActive,
                        squeezed = motionIndex != null && kotlin.math.abs(motionIndex - index) == 1,
                        onHoverChanged = { onMotion(index, it) },
                        onClick = { if (active) onPenDetail(tool) else vm.setTool(tool) },
                    )
                }
                PenToolButton(
                    tool = InkTool.ERASER,
                    label = "橡皮",
                    color = if (com.milknote.app.ui.theme.LocalMilkDark.current) 0xFFFFFFFF else 0xFF626262,
                    active = !modeActive && state.toolSettings.eraserTemporary,
                    enabled = !snipActive,
                    squeezed = motionIndex != null && kotlin.math.abs(motionIndex - 3) == 1,
                    onHoverChanged = { onMotion(3, it) },
                    onClick = { vm.toggleEraser() },
                )
                Spacer(Modifier.width(4.dp))
                WriterTopBarDivider()
                Spacer(Modifier.width(4.dp))
                listOf(0.4f, 0.7f, 1.2f).forEachIndexed { index, mm ->
                    val active = (state.tool.baseWidthMm - mm).let { it > -0.05f && it < 0.05f }
                    val wBg by animateColorAsState(
                        if (active) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                        tween(200), label = "widthBg",
                    )
                    Box(
                        Modifier
                            .size(48.dp)
                            .clickable(enabled = !snipActive) { vm.setWidth(mm) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(wBg)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                                .alpha(if (snipActive) 0.38f else 1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(width = if (mm > 0.9f) 14.dp else if (mm > 0.5f) 10.dp else 7.dp, height = (mm * 4).dp.coerceAtLeast(2.dp))
                                    .background(if (active) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground, CircleShape),
                            )
                        }
                    }
                    if (index < 2) Spacer(Modifier.width(4.dp))
                }
            }
            if (balance < 0.dp) Spacer(Modifier.width(-balance))

            Spacer(Modifier.width(8.dp))
            Spacer(Modifier.weight(1f))

            WriterToolIsland(Modifier.height(48.dp)) {
                visibleActions.forEach { action ->
                    WriterToolbarActionButton(
                        action = action,
                        state = state,
                        battery = battery,
                        snipActive = snipActive,
                        onClick = { performAction(action) },
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Box {
                    IconToggleButton(
                        icon = { Icon(Icons.Outlined.MoreHoriz, "更多功能", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        enabled = true, onClick = { moreMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = moreMenuOpen,
                        onDismissRequest = { moreMenuOpen = false },
                        modifier = Modifier.width(208.dp),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                    ) {
                        menuActions.forEach { action ->
                            WriterTopMenuItem(
                                label = action.label,
                                icon = action.icon(),
                                trailingText = when (action) {
                                    WriterToolbarAction.COMMAND -> "⌘K"
                                    WriterToolbarAction.DIAGNOSTICS -> if (battery < 0) "--%" else "$battery%"
                                    else -> null
                                },
                                enabled = action.isEnabled(snipActive),
                            ) {
                                moreMenuOpen = false
                                performAction(action)
                            }
                        }
                        WriterTopMenuItem("自定义工具栏", Icons.Outlined.Settings) {
                            moreMenuOpen = false
                            onCustomize()
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                IconToggleButton(
                    icon = { Icon(Icons.Outlined.AutoAwesome, if (agentOpen) "收起 Agent" else "打开 Agent", modifier = Modifier.size(20.dp), tint = if (agentOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                    enabled = true,
                    active = agentOpen,
                    onClick = onAgent,
                    badge = agentBusy,
                )
            }
        }
    }
}

private fun WriterToolbarAction.icon(): ImageVector = when (this) {
    WriterToolbarAction.LASSO -> Icons.Outlined.Gesture
    WriterToolbarAction.TEXT -> Icons.Outlined.Title
    WriterToolbarAction.SHAPES -> Icons.Outlined.Category
    WriterToolbarAction.IMAGE -> Icons.Outlined.Image
    WriterToolbarAction.FOCUS -> Icons.Outlined.CropFree
    WriterToolbarAction.NEW_PAGE -> Icons.Outlined.Add
    WriterToolbarAction.EXPORT -> Icons.Outlined.FileDownload
    WriterToolbarAction.COMMAND -> Icons.Outlined.Search
    WriterToolbarAction.DIAGNOSTICS -> Icons.Outlined.Edit
}

private fun WriterToolbarAction.isEnabled(snipActive: Boolean): Boolean =
    this == WriterToolbarAction.COMMAND || this == WriterToolbarAction.DIAGNOSTICS || !snipActive

@Composable
private fun WriterToolbarActionButton(
    action: WriterToolbarAction,
    state: WriterUiState,
    battery: Int,
    snipActive: Boolean,
    onClick: () -> Unit,
) {
    val active = action == WriterToolbarAction.LASSO && state.lassoArmed
    val tint = when {
        action == WriterToolbarAction.DIAGNOSTICS && battery in 0..19 -> com.milknote.app.ui.theme.LocalMilkWarning.current
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    IconToggleButton(
        icon = { Icon(action.icon(), action.label, modifier = Modifier.size(19.dp), tint = tint) },
        enabled = action.isEnabled(snipActive),
        active = active,
        onClick = onClick,
    )
}

@Composable
private fun WriterToolIsland(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .softShadow(4.dp, shape)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun WriterTopBarDivider() {
    Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
private fun WriterTopMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailingText: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
        trailingIcon = if (trailingText == null) null else {
            { Text(trailingText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
    )
}

/** 选区悬浮菜单:48dp 图标钮 + 深胶囊;上方优先、下方回退、横向边界钳制。 */
@Composable
private fun SelectionBar(
    visible: Boolean,
    box: com.milknote.ink.model.StrokeGeom.Box?,
    surface: InkSurfaceView?,
    availableWidthPx: Int,
    availableHeightPx: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    snapEnabled: Boolean,
    onToggleSnap: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    val reduced = com.milknote.app.ui.theme.LocalMilkMotion.current
    val density = LocalDensity.current
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    var position by remember { mutableStateOf(IntOffset.Zero) }
    if (visible && box != null && surface != null) {
        val (l, t) = surface.pageToView(box.l, box.t)
        val (r, b) = surface.pageToView(box.r, box.b)
        val gap = with(density) { 12.dp.roundToPx() }
        val edge = with(density) { 8.dp.roundToPx() }
        val topSafe = with(density) { (WriterTopBarHeight + 8.dp).roundToPx() }
        val x = ((l + r) / 2f - menuSize.width / 2f).toInt()
            .coerceIn(edge, (availableWidthPx - menuSize.width - edge).coerceAtLeast(edge))
        val rotationClearance = with(density) { 64.dp.roundToPx() }
        val above = t.toInt() - menuSize.height - gap - rotationClearance
        val below = b.toInt() + gap
        val y = (if (above >= topSafe) above else below)
            .coerceIn(edge, (availableHeightPx - menuSize.height - edge).coerceAtLeast(edge))
        position = IntOffset(x, y)
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .offset { position }
            .onSizeChanged { menuSize = it },
        enter = if (reduced) fadeIn(tween(60))
        else fadeIn(tween(160)) + slideInVertically(tween(180)) { -it / 3 },
        exit = if (reduced) fadeOut(tween(60)) else fadeOut(tween(140)),
    ) {
        Row(
            Modifier
                .clip(PillShape)
                .softShadow(8.dp, PillShape)
                .background(Color(0xF023221E))
                .paintedShadow(PillShape, edge = 6.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = Color(0xFFE8E6E0)
            IconToggleButton(
                icon = { Icon(Icons.Outlined.KeyboardArrowUp, "上移一层", tint = tint) },
                enabled = canMoveUp, onClick = onMoveUp,
            )
            IconToggleButton(
                icon = { Icon(Icons.Outlined.KeyboardArrowDown, "下移一层", tint = tint) },
                enabled = canMoveDown, onClick = onMoveDown,
            )
            IconToggleButton(
                icon = {
                    Icon(
                        Icons.Outlined.MyLocation,
                        if (snapEnabled) "关闭吸附" else "开启吸附",
                        tint = if (snapEnabled) Color(0xFF9DAFFF) else Color(0xFF9B978D),
                    )
                },
                enabled = true, active = snapEnabled, onClick = onToggleSnap,
            )
            IconToggleButton(
                icon = { Icon(Icons.Outlined.AutoAwesome, "发送给 Agent", tint = Color(0xFF9DAFFF)) },
                enabled = true, onClick = onSend,
            )
            IconToggleButton(
                icon = { Icon(Icons.Outlined.Delete, "删除选中元素", tint = Color(0xFFE06558)) },
                enabled = true, onClick = onDelete,
            )
            IconToggleButton(
                icon = { Icon(Icons.Outlined.Close, "取消选择", tint = tint) },
                enabled = true, onClick = onClear,
            )
        }
    }
}

/** 轻提示 toast:底部滑入 + 软阴影;减弱动效=纯淡化 */
@Composable
private fun ToastBar(visible: Boolean, text: String, modifier: Modifier = Modifier) {
    val reduced = com.milknote.app.ui.theme.LocalMilkMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) fadeIn(tween(60))
        else fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 2 },
        exit = if (reduced) fadeOut(tween(60))
        else fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 2 },
    ) {
        Box(
            Modifier
                .clip(PillShape)
                .softShadow(8.dp, PillShape)
                .background(Color(0xE623221E))
                .paintedShadow(PillShape, edge = 6.dp)
                .padding(horizontal = 18.dp, vertical = 9.dp),
        ) {
            Text(text, color = Color(0xFFFAF9F5), fontSize = 13.sp)
        }
    }
}

/** 纸面四周暗角(§5.1「柔和的皮」):极淡 radial 渐变向边缘压暗(亮 5.5%/暗 6% 黑);
 *  半径 0.42×长边(亮色纸角 4-7% 量级),纯显示层不参与导出 */
@Composable
private fun Modifier.paperVignette(dark: Boolean): Modifier {
    val edge = if (dark) Color.Black.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
    return drawBehind {
        val r = maxOf(size.width, size.height) * 0.42f
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to Color.Transparent, 0.6f to Color.Transparent, 1f to edge),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = r,
            ),
        )
    }
}

/** chrome 带边缘 hairline(1px,随主题;§5.1 顶栏/状态条/侧栏分界) */
private fun Modifier.edgeHairline(color: Color, left: Boolean = false, top: Boolean = false, right: Boolean = false, bottom: Boolean = false): Modifier = drawBehind {
    val t = 1.dp.toPx()
    if (left) drawRect(color, topLeft = Offset(0f, 0f), size = Size(t, size.height))
    if (top) drawRect(color, topLeft = Offset(0f, 0f), size = Size(size.width, t))
    if (right) drawRect(color, topLeft = Offset(size.width - t, 0f), size = Size(t, size.height))
    if (bottom) drawRect(color, topLeft = Offset(0f, size.height - t), size = Size(size.width, t))
}

/** 顶栏图标钮:48dp 命中(A-09),图标保持 18dp;单击语义——不注册空双击/长按(A-34) */
@Composable
private fun IconToggleButton(
    icon: @Composable () -> Unit,
    enabled: Boolean,
    active: Boolean = false,
    onClick: () -> Unit,
    badge: Boolean = false,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

/** 画布翻页:侧栏收起/专注模式也保留，首尾页禁用对应方向。 */
@Composable
private fun PageTurnControls(
    state: WriterUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WriterToolIsland(modifier.height(48.dp), containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
        IconToggleButton(
            icon = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上一页", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            enabled = state.canPreviousPage,
            onClick = onPrevious,
        )
        Text(
            "${state.currentPageIndex + 1} / ${state.pages.size}",
            modifier = Modifier.width(64.dp),
            fontFamily = Mono,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconToggleButton(
            icon = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下一页", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            enabled = state.canNextPage,
            onClick = onNext,
        )
    }
}

/** 侧栏:‹ 书架 + 本章页面缩略图(DESIGN-V0 §5.3:只放页面缩略,不放笔记本列表) */
@Composable
private fun PageSidebar(
    state: WriterUiState,
    thumbs: Map<String, androidx.compose.ui.graphics.ImageBitmap>,
    onBack: () -> Unit,
    onSwitch: (String) -> Unit,
    onNewPage: () -> Unit,
) {
    val nb = state.notebook
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentPageId, state.pages.size) {
        val index = state.currentPageIndex
        if (index >= 0) listState.scrollToItem(index)
    }
    val (pwMm, hMm) = com.milknote.notes.model.PAGE_SIZES_MM[nb?.pageSize] ?: com.milknote.notes.model.PAGE_SIZES_MM.getValue("A4")
    val pageAspect = if (nb?.orientation == "landscape") hMm / pwMm else pwMm / hMm
    Column(
        Modifier
            .width(148.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .edgeHairline(MaterialTheme.colorScheme.outline.copy(alpha = 0.75f), right = true)
            .padding(top = WriterTopBarHeight)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text("书架", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))

        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.pages, key = { it.pageId }) { p ->
                val active = p.pageId == state.currentPageId
                val thumbBg by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                    tween(200), label = "thumbBg",
                )
                val thumbBorder by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    tween(200), label = "thumbBorder",
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .clip(RoundedCornerShape(10.dp))
                        .background(thumbBg)
                        .border(
                            width = if (active) 1.5.dp else 1.dp,
                            color = thumbBorder,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onSwitch(p.pageId) }
                        .padding(5.dp),
                ) {
                    val bmp = thumbs[p.pageId]
                    if (bmp != null) {
                        Image(
                            bmp, contentDescription = p.pageId,
                            modifier = Modifier.fillMaxWidth().aspectRatio(pageAspect),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().aspectRatio(pageAspect).background(MaterialTheme.colorScheme.surface))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        p.pageId, fontFamily = Mono, fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
            item(key = "__add__") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .clickable(onClick = onNewPage)
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Add, "新页", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 纸面画布:InkSurfaceView(device-mi 拦截器 + 按住修饰接线在此) */
@Composable
private fun PaperCanvas(
    vm: WriterViewModel,
    state: WriterUiState,
    dark: Boolean,
    penConnected: Boolean,
    surfaceRef: androidx.compose.runtime.MutableState<InkSurfaceView?>,
) {
    AndroidView(
        factory = { ctx ->
            InkSurfaceView(ctx).apply {
                surfaceRef.value = this
                inputInterceptor = ServiceLocator.penPort::onDispatchTouchEvent
                onViewportChanged = vm::refreshSelectionOverlay
                onPreviousPage = vm::previousPage
                onNextPage = vm::nextPage
                // 按住+书写修饰(PEN-ADAPTATION §4.3):落笔瞬间同步查询
                // 「主/副键按住」触发器 → 映射动作 → 套索圈选/临时橡皮
                holdActionProvider = {
                    val trigger = ServiceLocator.penPort.holdWriteTrigger
                    when (trigger?.let { ServiceLocator.penRouter.actionOf(it) }) {
                        PenAction.LASO -> InkAction.LASO
                        PenAction.TEMP_ERASER -> InkAction.TEMP_ERASER
                        else -> null
                    }
                }
            }
        },
        update = { view ->
            view.controller = vm.controller
            view.paperStyle = state.notebook?.paper ?: "dot"
            view.imageDir = state.notebook?.id?.let { ServiceLocator.repository.notebookDir(it) }
            view.frontBufferEnabled = state.frontBufferEnabled
            view.darkTheme = dark
            view.penConnected = penConnected
            view.canTurnPrevious = state.canPreviousPage
            view.canTurnNext = state.canNextPage
            // 纸张尺寸+方向(旧笔记本缺字段 → A4 竖,几何与历史一致)
            val nb = state.notebook
            val (pw, ph) = nb?.pageMm(view.mmToPx) ?: (210f * view.mmToPx to 297f * view.mmToPx)
            view.setPageSizePx(pw, ph)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** 顶栏 Agent / ⌥A:直接开 ⑤ 抽屉(当前页上下文,无选区;AGENT-BACKEND §8.3) */
private fun openAgentDrawer(vm: WriterViewModel) {
    val st = vm.state.value
    val nb = st.notebook ?: return
    ServiceLocator.agentController.openDrawer(com.milknote.agent.PageRef(nb.id, st.currentPageId))
}

/** Agent 空态三动作(A-19):绑定当前页并直接执行意图(与 ⌘K 同管线) */
private fun quickAgentIntent(vm: WriterViewModel, intent: com.milknote.agent.Intents.Id) {
    val st = vm.state.value
    val nb = st.notebook ?: return
    ServiceLocator.agentController.let {
        it.bindPage(com.milknote.agent.PageRef(nb.id, st.currentPageId))
        it.quickIntent(intent)
    }
}
