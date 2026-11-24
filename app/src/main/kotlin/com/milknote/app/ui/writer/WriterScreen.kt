package com.milknote.app.ui.writer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.Videocam
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.milknote.app.di.ServiceLocator
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.milknote.app.ui.agent.AgentDrawerHost
import com.milknote.app.ui.agent.SnipOverlay
import com.milknote.app.ui.cards.CardsViewModel
import com.milknote.app.ui.common.MilkActionPanel
import com.milknote.app.ui.common.MilkConfirmDialog
import com.milknote.app.ui.common.MilkPanelAction
import com.milknote.app.ui.common.MilkToast
import com.milknote.app.ui.common.milkEdgeHairline
import com.milknote.app.ui.common.milkGlassRim
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.AccentBlueDark
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.OverlayBorderDark
import com.milknote.app.ui.theme.OverlayMuted
import com.milknote.app.ui.theme.OverlaySurfaceDark
import com.milknote.app.ui.theme.OverlaySurfaceLight
import com.milknote.app.ui.theme.OverlayText
import com.milknote.app.ui.theme.PillShape
import com.milknote.device.pen.PenAction
import com.milknote.ink.input.InkAction
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.MediaElement
import com.milknote.ink.render.InkSurfaceView
import com.milknote.notes.storage.PageInsertPosition

/** 书写页：固定纸面坐标 + P016 两级 chrome + 覆盖式页面/摘取栏。 */
@Composable
fun WriterScreen(
    vm: WriterViewModel,
    cardsVm: CardsViewModel,
    dark: Boolean,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenPalette: () -> Unit = {},
    onOpenSettings: (page: String) -> Unit = {},
    onOpenExport: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val thumbs by vm.thumbs.collectAsState()
    val penPresets by vm.penPresets.collectAsState()
    val appSettings by ServiceLocator.appSettings.flow.collectAsState(initial = com.milknote.app.ui.settings.AppSettings())
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
    var selectedMajorTool by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(WriterMajorTool.BRUSH)
    }
    var railMode by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf<WriterRailMode?>(null)
    }
    var minorCollapsed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var minorDock by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(MinorDock.TOP) }
    var dockPreview by remember { mutableStateOf<MinorDock?>(null) }
    var centralPanelState by remember { mutableStateOf(WriterPanelState()) }
    var detailTool by remember { mutableStateOf<InkTool?>(null) }
    var detailColor by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf<com.milknote.ink.model.AnnotationCard?>(null) }
    var textToolStyle by remember { mutableStateOf(com.milknote.ink.model.CardTextStyle()) }
    var shapeConfig by remember { mutableStateOf<ShapeConfig?>(null) }
    var shapeTool by remember { mutableStateOf(InkTool.RECT) }
    var shapeColor by remember { mutableStateOf(state.tool.color) }
    var shapeWidth by remember { mutableStateOf(state.tool.baseWidthMm) }
    var newPageAnchor by remember { mutableStateOf<String?>(null) }
    var playbackElement by remember { mutableStateOf<MediaElement?>(null) }

    fun setCentralPanel(panel: WriterPanel, visible: Boolean) {
        centralPanelState = if (visible) centralPanelState.open(panel) else centralPanelState.dismiss()
        if (!visible) detailColor = null
    }
    fun closeCentralPanel() {
        centralPanelState = centralPanelState.dismiss()
        detailColor = null
    }
    fun openToolPanel(tool: InkTool, colorIndex: Int? = null) {
        detailTool = tool
        detailColor = colorIndex
        setCentralPanel(WriterPanel.TOOL, true)
    }
    fun openTextPanel(card: com.milknote.ink.model.AnnotationCard? = null) {
        editingText = card
        setCentralPanel(WriterPanel.TEXT, true)
    }
    val openNewPage: (String?) -> Unit = { anchor ->
        newPageAnchor = anchor
        setCentralPanel(WriterPanel.NEW_PAGE, true)
    }
    androidx.activity.compose.BackHandler(enabled = centralPanelState.open != null) {
        closeCentralPanel()
    }
    LaunchedEffect(railMode) {
        if (railMode == WriterRailMode.SNIPPETS) cardsVm.refresh()
    }
    LaunchedEffect(state.toolSettings.currentTool, state.toolSettings.eraserTemporary) {
        if (state.toolSettings.eraserTemporary) {
            selectedMajorTool = WriterMajorTool.ERASER
        } else {
            when (state.toolSettings.currentTool) {
                InkTool.HIGHLIGHTER -> selectedMajorTool = WriterMajorTool.HIGHLIGHTER
                InkTool.DOODLE -> selectedMajorTool = WriterMajorTool.DOODLE
                InkTool.LASER -> selectedMajorTool = WriterMajorTool.LASER
                InkTool.PEN, InkTool.BALL -> if (selectedMajorTool in listOf(
                    WriterMajorTool.BRUSH, WriterMajorTool.HIGHLIGHTER, WriterMajorTool.ERASER,
                    WriterMajorTool.DOODLE, WriterMajorTool.LASER,
                )) selectedMajorTool = WriterMajorTool.BRUSH
                else -> Unit
            }
        }
    }
    LaunchedEffect(state.lassoArmed) {
        if (state.lassoArmed) selectedMajorTool = WriterMajorTool.LASSO
    }
    var recordPermission by remember {
        mutableStateOf(
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    writerCtx, android.Manifest.permission.RECORD_AUDIO,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) RecordPermission.GRANTED else RecordPermission.UNKNOWN,
        )
    }
    val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> recordPermission = if (granted) RecordPermission.GRANTED else RecordPermission.DENIED }
    val videoLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) vm.insertVideo(uri) }
    // 从系统设置返回后刷新权限(永久拒绝仅能由用户去设置页开启)。
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    writerCtx, android.Manifest.permission.RECORD_AUDIO,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                recordPermission = RecordPermission.GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val openRecord: () -> Unit = {
        playbackElement = null
        setCentralPanel(WriterPanel.RECORD, true)
        if (recordPermission != RecordPermission.GRANTED) {
            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
    val openVideo: () -> Unit = { setCentralPanel(WriterPanel.VIDEO, true) }
    val materialImageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) vm.insertMaterial(uri) }
    val onMajorTool: (WriterMajorTool) -> Unit = { tool ->
        val active = isMajorToolActive(tool, selectedMajorTool, state)
        val settingsOpen = when (tool) {
            WriterMajorTool.BRUSH, WriterMajorTool.HIGHLIGHTER, WriterMajorTool.ERASER,
            WriterMajorTool.DOODLE, WriterMajorTool.LASER -> centralPanelState.open == WriterPanel.TOOL
            WriterMajorTool.LASSO -> centralPanelState.open == WriterPanel.LASSO
            WriterMajorTool.IMAGE -> centralPanelState.open == WriterPanel.IMAGE
            WriterMajorTool.TEXT -> centralPanelState.open == WriterPanel.TEXT
            WriterMajorTool.SHAPE -> centralPanelState.open == WriterPanel.SHAPE
            WriterMajorTool.TAPE -> centralPanelState.open == WriterPanel.MATERIAL
            else -> false
        }
        when (resolveToolTap(active, settingsOpen)) {
            ToolTapOutcome.CLOSE_SETTINGS -> closeCentralPanel()
            ToolTapOutcome.OPEN_SETTINGS -> {
                closeCentralPanel()
                when (tool) {
                    WriterMajorTool.BRUSH, WriterMajorTool.HIGHLIGHTER, WriterMajorTool.ERASER,
                    WriterMajorTool.DOODLE, WriterMajorTool.LASER -> openToolPanel(writerInkTool(tool, state.toolSettings))
                    WriterMajorTool.LASSO -> setCentralPanel(WriterPanel.LASSO, true)
                    WriterMajorTool.IMAGE -> setCentralPanel(WriterPanel.IMAGE, true)
                    WriterMajorTool.TEXT -> openTextPanel()
                    WriterMajorTool.SHAPE -> setCentralPanel(WriterPanel.SHAPE, true)
                    WriterMajorTool.SNIP -> if (state.snip.phase == SnipPhase.IDLE) vm.enterSnip()
                    WriterMajorTool.TAPE -> setCentralPanel(WriterPanel.MATERIAL, true)
                    WriterMajorTool.READING -> {
                        selectedMajorTool = WriterMajorTool.BRUSH
                        minorCollapsed = false
                    }
                }
            }
            ToolTapOutcome.ACTIVATE -> {
                closeCentralPanel()
                detailTool = null
                if (selectedMajorTool == WriterMajorTool.READING) minorCollapsed = false
                selectedMajorTool = tool
                if (tool != WriterMajorTool.SHAPE) shapeConfig = null
                if (tool != WriterMajorTool.LASSO && state.lassoArmed) vm.toggleLasso()
                when (tool) {
                    WriterMajorTool.BRUSH, WriterMajorTool.HIGHLIGHTER,
                    WriterMajorTool.DOODLE, WriterMajorTool.LASER -> vm.setTool(writerInkTool(tool, state.toolSettings))
                    WriterMajorTool.ERASER -> if (!state.toolSettings.eraserTemporary) vm.toggleEraser()
                    WriterMajorTool.LASSO -> if (!state.lassoArmed) vm.toggleLasso()
                    WriterMajorTool.SNIP -> vm.enterSnip()
                    WriterMajorTool.READING -> {
                        railMode = null
                        minorCollapsed = true
                        vm.toast("阅读工具：拖动画布平移，双指缩放")
                    }
                    else -> Unit
                }
            }
        }
    }
    val showRail: (WriterRailMode) -> Unit = { target ->
        closeCentralPanel()
        railMode = toggleWriterRail(railMode, target)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // 第二代:chrome 仅 44dp 顶栏;笔匣悬浮在画布之上,不占布局高度
        val chromeHeight = if (focusMode) 0.dp else WriterMajorToolbarHeight
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(if (dark) com.milknote.app.ui.theme.CanvasDark else com.milknote.app.ui.theme.CanvasLight),
        ) {
                val splitNb = state.splitNotebook
                if (state.splitEnabled && splitNb != null) {
                    val splitStartPad = (if (!focusMode && railMode != null) WriterExpandedRailWidth else 0.dp) + 12.dp
                    val splitEndPad = (if (!focusMode && agentDrawerOpen) 360.dp else 0.dp) + 12.dp
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(top = chromeHeight, start = splitStartPad, end = splitEndPad, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    state.notebook?.name ?: "",
                                    style = MilkText.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${state.currentPageIndex + 1} / ${state.pages.size.coerceAtLeast(1)}",
                                    style = MilkText.monoMeta,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(6.dp))) {
                                PaperCanvas(vm, state, dark, penConnected, surfaceRef, selectedMajorTool == WriterMajorTool.READING, swipeNewPage = appSettings.swipeNewPage)
                            }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    splitNb.name,
                                    style = MilkText.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SplitPageStep(
                                        icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                        label = "上一页",
                                        enabled = state.splitPageIndex > 0,
                                        onClick = { vm.stepSplitPage(-1) },
                                    )
                                    Text(
                                        "${(state.splitPageIndex + 1).coerceAtLeast(1)} / ${state.splitPages.size.coerceAtLeast(1)}",
                                        style = MilkText.monoMeta,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 2.dp),
                                    )
                                    SplitPageStep(
                                        icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                        label = "下一页",
                                        enabled = state.splitPageIndex >= 0 &&
                                            state.splitPageIndex < state.splitPages.lastIndex,
                                        onClick = { vm.stepSplitPage(1) },
                                    )
                                }
                            }
                            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(6.dp))) {
                                SplitPaperCanvas(vm, state, dark, penConnected, selectedMajorTool == WriterMajorTool.READING)
                            }
                        }
                    }
                } else {
                    PaperCanvas(vm, state, dark, penConnected, surfaceRef, selectedMajorTool == WriterMajorTool.READING, swipeNewPage = appSettings.swipeNewPage)
                }
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
                        onSaveCard = vm::snipSaveCard,
                    )
                }
                // ⑤ 右侧三合一 Studio 抽屉(AI / Markdown / 关联，默认收起)
                val cardsState by cardsVm.state.collectAsState()
                val relatedNotebooks = remember(state.notebook?.id, state.openTabs) {
                    ServiceLocator.repository.listNotebooks(includeTrashed = false)
                        .filter { it.id != state.notebook?.id }
                }
                AgentDrawerHost(
                    onOpenSettings = onOpenSettings,
                    onSnip = vm::enterSnip,
                    onExplainPage = { quickAgentIntent(vm, com.milknote.agent.Intents.Id.EXPLAIN) },
                    onSummarizeChapter = { quickAgentIntent(vm, com.milknote.agent.Intents.Id.SUMMARIZE) },
                    pageLabel = "第 ${state.currentPageIndex + 1} 页",
                    pageMarkdown = state.pageMarkdown,
                    onMarkdownChange = vm::updatePageMarkdown,
                    onSaveMarkdownCard = vm::saveMarkdownAsCard,
                    relatedNotebooks = relatedNotebooks,
                    relatedCards = cardsState.cards,
                    onOpenSplitNotebook = { vm.toggleSplit(it) },
                    onInsertCard = { vm.insertLibraryCard(it, state.currentPageId) },
                    onSaveMessageCard = vm::saveTextAsCard,
                    allNotebooks = remember(state.notebook?.id) { ServiceLocator.repository.listNotebooks(includeTrashed = false) },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(top = chromeHeight),
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
                    onEdit = if (state.selectionCount == 1 && vm.selectedTextCard() != null) {
                        { vm.selectedTextCard()?.let { openTextPanel(it) } }
                    } else null,
                    onDelete = vm::deleteSelection,
                    onClear = vm::clearSelection,
                    onPlay = if (state.selectionCount == 1) {
                        vm.selectedMedia()?.let { element ->
                            {
                                playbackElement = element
                                setCentralPanel(WriterPanel.PLAYBACK, true)
                            }
                        }
                    } else null,
                )
                // 轻提示 toast:底部滑入
                var lastToast by remember { mutableStateOf("") }
                toast?.let { lastToast = it }
                MilkToast(
                    visible = toast != null,
                    text = lastToast,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                )
            }

        if (!focusMode) {
            val reduced = LocalMilkMotion.current
            val railWidth = if (railMode == null) 0.dp else WriterExpandedRailWidth
            val minorVisible = !minorCollapsed && selectedMajorTool != WriterMajorTool.READING
            // 记住最近非空页签:退出动画期间继续渲染旧内容,不做空切
            var lastRailMode by remember { mutableStateOf(WriterRailMode.PAGES) }
            LaunchedEffect(railMode) { railMode?.let { lastRailMode = it } }
            AnimatedVisibility(
                visible = railMode != null,
                modifier = Modifier.align(Alignment.TopStart).padding(top = chromeHeight),
                enter = if (reduced) fadeIn(tween(MilkMotion.reduced))
                else slideInHorizontally(tween(MilkMotion.drawerEnter, easing = MilkMotion.EaseDrawer)) { -it } +
                    fadeIn(tween(MilkMotion.enter, easing = MilkMotion.EaseOut)),
                exit = if (reduced) fadeOut(tween(MilkMotion.reduced))
                else slideOutHorizontally(tween(MilkMotion.drawerExit, easing = MilkMotion.EaseDrawer)) { -it } +
                    fadeOut(tween(MilkMotion.exit, easing = MilkMotion.EaseOut)),
            ) {
                when (railMode ?: lastRailMode) {
                    WriterRailMode.PAGES -> WriterPageRail(
                        state = state,
                        thumbs = thumbs,
                        onSwitch = vm::switchPage,
                        onNewPage = openNewPage,
                        onDuplicatePage = vm::duplicatePage,
                        onClearPage = vm::clearPage,
                        onDeletePage = vm::deletePage,
                        onMovePage = vm::movePage,
                        onPrevious = vm::previousPage,
                        onNext = vm::nextPage,
                        onShowSnippets = { railMode = WriterRailMode.SNIPPETS },
                        onShowOutline = { railMode = WriterRailMode.OUTLINE },
                    )
                    WriterRailMode.SNIPPETS -> WriterSnippetRail(
                        vm = cardsVm,
                        currentNotebookId = state.notebook?.id,
                        currentPageId = state.currentPageId,
                        onInsert = { vm.insertLibraryCard(it, state.currentPageId) },
                        onShowPages = { railMode = WriterRailMode.PAGES },
                        onShowOutline = { railMode = WriterRailMode.OUTLINE },
                    )
                    WriterRailMode.OUTLINE -> WriterOutlineRail(
                        state = state,
                        entries = remember(state.currentPageId, state.pages, state.pageMarkdown) { vm.outlineEntries() },
                        onSwitch = vm::switchPage,
                        onShowPages = { railMode = WriterRailMode.PAGES },
                        onShowSnippets = { railMode = WriterRailMode.SNIPPETS },
                    )
                }
            }
            // 笔匣四边停靠(拖拽柄):松手按最近边吸附;拖动中显示落位预览条
            val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
            fun nearestDock(x: Float, y: Float): MinorDock = listOf(
                MinorDock.TOP to y,
                MinorDock.BOTTOM to windowSize.height - y,
                MinorDock.LEFT to x,
                MinorDock.RIGHT to windowSize.width - x,
            ).minByOrNull { it.second }?.first ?: MinorDock.TOP
            val haptic = LocalHapticFeedback.current
            val onDockDrag: (Offset, Offset) -> Unit = { dragged, handleCenter ->
                val target = nearestDock(handleCenter.x + dragged.x, handleCenter.y + dragged.y)
                // 预览切换停靠边时给一次轻触感,提示"会停到哪"
                if (dockPreview != null && target != dockPreview) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                dockPreview = target
            }
            val onDockDrop: (Offset, Offset) -> Unit = { dragged, handleCenter ->
                val target = nearestDock(handleCenter.x + dragged.x, handleCenter.y + dragged.y)
                if (target != minorDock) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    minorDock = target
                }
                dockPreview = null
            }
            val onDockCancel: () -> Unit = { dockPreview = null }
            val railWidthNow = railWidth
            val studioInset = if (agentDrawerOpen) 360.dp else 0.dp
            // 笔设置入口:按当前工具打开对应面板(钢笔/荧光笔/图案笔/激光 → 笔设置;其余 → 各自面板)
            val openTune: () -> Unit = {
                when (selectedMajorTool) {
                    WriterMajorTool.BRUSH, WriterMajorTool.HIGHLIGHTER, WriterMajorTool.DOODLE, WriterMajorTool.LASER ->
                        openToolPanel(writerInkTool(selectedMajorTool, state.toolSettings))
                    WriterMajorTool.ERASER -> openToolPanel(com.milknote.ink.model.InkTool.ERASER)
                    WriterMajorTool.LASSO -> setCentralPanel(WriterPanel.LASSO, true)
                    WriterMajorTool.IMAGE -> setCentralPanel(WriterPanel.IMAGE, true)
                    WriterMajorTool.TEXT -> openTextPanel()
                    WriterMajorTool.SHAPE -> setCentralPanel(WriterPanel.SHAPE, true)
                    WriterMajorTool.TAPE -> setCentralPanel(WriterPanel.MATERIAL, true)
                    else -> openToolPanel(state.toolSettings.currentTool)
                }
            }
            val minorBar: @Composable (MinorDock) -> Unit = { dock ->
                WriterMinorToolbar(
                    state = state,
                    selectedTool = selectedMajorTool,
                    textStyle = textToolStyle,
                    shapeTool = shapeTool,
                    shapeColor = shapeColor,
                    shapeWidth = shapeWidth,
                    dock = dock,
                    maxWidth = maxWidth - railWidthNow - studioInset - 24.dp,
                    maxHeight = maxHeight - chromeHeight - 24.dp,
                    onDockDrag = onDockDrag,
                    onDockDrop = onDockDrop,
                    onDockCancel = onDockCancel,
                    onUndo = vm::undo,
                    onRedo = vm::redo,
                    onCollapse = { minorCollapsed = true },
                    onTool = onMajorTool,
                    onColor = vm::setColor,
                    onWidth = vm::setWidth,
                    onEraserWidth = vm::setEraserWidth,
                    onLassoMode = vm::setLassoMode,
                    onLassoFilter = vm::toggleLassoFilter,
                    onLassoSettings = { setCentralPanel(WriterPanel.LASSO, true) },
                    onImage = { setCentralPanel(WriterPanel.IMAGE, true) },
                    onTextEditor = { openTextPanel() },
                    onTextStyle = { textToolStyle = it },
                    onShapeTool = { shapeTool = it },
                    onShapeColor = { shapeColor = it },
                    onShapeWidth = { shapeWidth = it },
                    onStartShape = { shapeConfig = ShapeConfig(shapeTool, shapeColor, shapeWidth) },
                    onSnip = vm::enterSnip,
                    onTape = { vm.insertBuiltinMaterial(com.milknote.ink.model.PageImageKind.TAPE, it) },
                    onMaterials = { setCentralPanel(WriterPanel.MATERIAL, true) },
                    onTune = openTune,
                )
            }
            var moreExpanded by remember { mutableStateOf(false) }
            val moreContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
                val cs = MaterialTheme.colorScheme
                val spec = state.notebook?.pageSpec(state.currentPageId)
                Text(
                    "纸纹",
                    style = MilkText.eyebrow,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    com.milknote.notes.model.PAPER_KINDS.forEach { (id, label) ->
                        com.milknote.app.ui.common.MilkChip(
                            label = label, selected = (spec?.paper ?: "dot") == id, small = true,
                            onClick = { vm.setPagePaper(id); moreExpanded = false },
                        )
                    }
                }
                Text(
                    "纸色",
                    style = MilkText.eyebrow,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 6.dp),
                )
                Row(Modifier.widthIn(max = 288.dp).padding(horizontal = 8.dp)) {
                    com.milknote.notes.model.PAPER_COLORS.forEach { (hex, label) ->
                        com.milknote.app.ui.common.MilkColorDot(
                            color = Color(android.graphics.Color.parseColor(hex)),
                            selected = (spec?.paperColor ?: "#FFFFFF").equals(hex, ignoreCase = true),
                            dotSize = 16.dp,
                            contentDescription = "纸色$label",
                            onClick = { vm.setPagePaperColor(hex); moreExpanded = false },
                        )
                    }
                }
                com.milknote.app.ui.common.MilkMenuDivider()
                com.milknote.app.ui.common.MilkMenuRow(com.milknote.app.ui.common.MilkPanelAction(label = "素材", icon = Icons.Outlined.Category, onClick = { moreExpanded = false; setCentralPanel(WriterPanel.MATERIAL, true) }))
                com.milknote.app.ui.common.MilkMenuRow(com.milknote.app.ui.common.MilkPanelAction(label = "学习视频", icon = Icons.Outlined.Videocam, onClick = { moreExpanded = false; openVideo() }))
                com.milknote.app.ui.common.MilkMenuRow(com.milknote.app.ui.common.MilkPanelAction(label = "新建页面", icon = Icons.Outlined.Add, onClick = { moreExpanded = false; openNewPage(state.currentPageId) }))
                com.milknote.app.ui.common.MilkMenuRow(
                    com.milknote.app.ui.common.MilkPanelAction(
                        label = if (state.splitEnabled) "关闭双开分屏" else "双开分屏",
                        icon = Icons.Outlined.VerticalSplit,
                        onClick = { moreExpanded = false; vm.toggleSplit() },
                    ),
                )
                com.milknote.app.ui.common.MilkMenuRow(com.milknote.app.ui.common.MilkPanelAction(label = "清空本页手绘", icon = Icons.Outlined.Clear, onClick = { moreExpanded = false; vm.clearPage(state.currentPageId) }))
                com.milknote.app.ui.common.MilkMenuRow(com.milknote.app.ui.common.MilkPanelAction(label = "导出", icon = Icons.Outlined.FileDownload, onClick = { moreExpanded = false; onOpenExport() }))
                com.milknote.app.ui.common.MilkMenuRow(com.milknote.app.ui.common.MilkPanelAction(label = "命令面板", icon = Icons.Outlined.Search, onClick = { moreExpanded = false; onOpenPalette() }))
                com.milknote.app.ui.common.MilkMenuRow(com.milknote.app.ui.common.MilkPanelAction(label = "笔诊断", icon = Icons.Outlined.Edit, onClick = { moreExpanded = false; onOpenDiagnostics() }))
            }
            WriterMajorToolbar(
                state = state,
                railMode = railMode,
                onBack = onBack,
                onToggleRail = { railMode = if (railMode == null) WriterRailMode.PAGES else null },
                onRecord = openRecord,
                recording = centralPanelState.open == WriterPanel.RECORD,
                onAgent = {
                    if (agentDrawerOpen) ServiceLocator.agentController.closeDrawer() else openAgentDrawer(vm)
                },
                agentOpen = agentDrawerOpen,
                agentBusy = agentBusy,
                onSelectTab = { vm.open(it) },
                onCloseTab = vm::closeTab,
                onToggleSplit = { vm.toggleSplit() },
                onEnterFocus = { focusMode = true },
                moreExpanded = moreExpanded,
                onMoreToggle = { moreExpanded = !moreExpanded },
                onMoreDismiss = { moreExpanded = false },
                moreContent = moreContent,
            )
            // 悬浮笔匣:居中浮动于画布之上(避开左侧抽屉与右侧 Studio),按停靠边落位
            if (minorVisible) {
                val dockModifier = when (minorDock) {
                    MinorDock.TOP -> Modifier
                        .align(Alignment.TopCenter)
                        .offset(x = railWidthNow / 2 - studioInset / 2)
                        .padding(top = chromeHeight + 10.dp)
                    MinorDock.BOTTOM -> Modifier
                        .align(Alignment.BottomCenter)
                        .offset(x = railWidthNow / 2 - studioInset / 2)
                        .padding(bottom = 14.dp)
                    MinorDock.LEFT -> Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 10.dp, top = chromeHeight + 12.dp, bottom = 60.dp)
                    MinorDock.RIGHT -> Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = studioInset + 10.dp, top = chromeHeight + 12.dp, bottom = 60.dp)
                }
                Box(dockModifier) { minorBar(minorDock) }
            }
            // 拖拽中的停靠预览:目标边显示半透明落位条(松手前可见"会停到哪")
            dockPreview?.let { preview ->
                val previewShape = RoundedCornerShape(999.dp)
                val previewModifier = when (preview) {
                    MinorDock.TOP -> Modifier
                        .align(Alignment.TopCenter)
                        .offset(x = railWidthNow / 2 - studioInset / 2)
                        .padding(top = chromeHeight + 10.dp)
                        .width(320.dp)
                        .height(WriterMinorToolbarHeight + 8.dp)
                    MinorDock.BOTTOM -> Modifier
                        .align(Alignment.BottomCenter)
                        .offset(x = railWidthNow / 2 - studioInset / 2)
                        .padding(bottom = 14.dp)
                        .width(320.dp)
                        .height(WriterMinorToolbarHeight + 8.dp)
                    MinorDock.LEFT -> Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 10.dp, top = chromeHeight + 12.dp)
                        .width(WriterMinorBarThickness)
                        .height(220.dp)
                    MinorDock.RIGHT -> Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = studioInset + 10.dp, top = chromeHeight + 12.dp)
                        .width(WriterMinorBarThickness)
                        .height(220.dp)
                }
                Box(
                    previewModifier
                        .padding(6.dp)
                        .clip(previewShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .border(1.dp, MaterialTheme.colorScheme.primary, previewShape)
                        .testTag("writer-dock-preview"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DragIndicator,
                        "停靠预览",
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val showRestoreMinor = minorCollapsed && selectedMajorTool != WriterMajorTool.READING
            val showPagePill = railMode == null
            if (showRestoreMinor || showPagePill) {
                val startInset = railWidth + if (minorVisible && minorDock == MinorDock.LEFT) WriterMinorBarThickness else 0.dp
                val bottomInset = if (minorVisible && minorDock == MinorDock.BOTTOM) WriterMinorToolbarHeight + 22.dp else 12.dp
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = startInset + 12.dp, bottom = bottomInset),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showRestoreMinor) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                                .border(1.dp, MaterialTheme.colorScheme.outline, PillShape)
                                .clickable { minorCollapsed = false }
                                .testTag("writer-restore-minor"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.DragIndicator, "展开笔匣", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (showPagePill) {
                        WriterPagePill(
                            state = state,
                            onPrevious = vm::previousPage,
                            onNext = vm::nextPage,
                        )
                    }
                }
            }
        }

        if (focusMode) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp)
                    .milkGlassRim(PillShape, dark = dark, elevation = 8.dp)
                    .clickable { focusMode = false }
                    .defaultMinSize(minHeight = 44.dp)
                    .padding(horizontal = 18.dp)
                    .testTag("writer-exit-focus"),
                contentAlignment = Alignment.Center,
            ) {
                Text("退出专注", style = MilkText.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            androidx.activity.compose.BackHandler(enabled = true) { focusMode = false }
        }

    when (centralPanelState.open) {
        WriterPanel.TOOL -> {
            val colorIndex = detailColor
            if (colorIndex == null) {
                detailTool?.let { tool ->
                    PenSettingsDialog(
                        tool = tool,
                        settings = state.toolSettings,
                        onDismiss = ::closeCentralPanel,
                        onWidth = vm::setWidth,
                        onSelectColor = vm::setColor,
                        onColorDetail = { detailColor = it },
                        onEraserWidth = vm::setEraserWidth,
                        onEraseTarget = vm::setEraserTarget,
                        onAutoRevert = vm::setEraserAutoRevert,
                        onGamma = vm::setPenGamma,
                        presets = penPresets,
                        onSavePreset = vm::saveCurrentPenPreset,
                        onApplyPreset = vm::applyPenPreset,
                        onClearPresets = vm::clearPenPresets,
                    )
                }
            } else {
                val tool = detailTool
                state.toolSettings.palettes[tool]?.getOrNull(colorIndex)?.let { color ->
                    ColorSettingsDialog(
                        initialColor = color,
                        onDismiss = { detailColor = null },
                        onColorChange = { vm.setPaletteColor(colorIndex, it) },
                    )
                }
            }
        }
        WriterPanel.TEXT -> {
            val existing = editingText
            TextBoxDialog(
                existing = existing,
                initialStyle = textToolStyle,
                onDismiss = ::closeCentralPanel,
                onCreate = { text, style ->
                    textToolStyle = style
                    if (existing == null) vm.createTextCard(text, style) else vm.updateTextCard(existing, text, style)
                    closeCentralPanel()
                },
            )
        }
        WriterPanel.SHAPE -> ShapeSettingsDialog(
            initialTool = shapeTool,
            initialColor = shapeColor,
            initialWidth = shapeWidth,
            onDismiss = ::closeCentralPanel,
            onStart = {
                shapeConfig = it
                shapeTool = it.tool
                shapeColor = it.color
                shapeWidth = it.widthMm
                closeCentralPanel()
            },
        )
        WriterPanel.IMAGE -> RecentImagesDialog(
            onDismiss = ::closeCentralPanel,
            onSelect = { vm.insertImage(it); closeCentralPanel() },
        )
        WriterPanel.LASSO -> LassoFilterPanel(
            selected = state.lassoFilters,
            onToggle = vm::toggleLassoFilter,
            onDismiss = ::closeCentralPanel,
        )
        WriterPanel.MATERIAL -> MaterialLibraryPanel(
            onDismiss = ::closeCentralPanel,
            onImportImage = { closeCentralPanel(); materialImageLauncher.launch("image/*") },
            onDoodle = {
                closeCentralPanel()
                selectedMajorTool = WriterMajorTool.DOODLE
                vm.setTool(InkTool.DOODLE)
            },
            onBuiltin = { kind, variant -> closeCentralPanel(); vm.insertBuiltinMaterial(kind, variant) },
            onMaterial = { file, kind -> closeCentralPanel(); vm.insertMaterialFile(file, kind) },
        )
        WriterPanel.NEW_PAGE -> NewPagePanel(
            anchorPageId = newPageAnchor,
            paperDescription = paperDescription(state),
            onDismiss = ::closeCentralPanel,
            onInsert = { position ->
                closeCentralPanel()
                vm.insertPage(position, newPageAnchor)
            },
        )
        WriterPanel.RECORD -> AudioRecordPanel(
            permission = recordPermission,
            onRequestPermission = { audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
            onInserted = vm::insertRecordedAudio,
            onDismiss = ::closeCentralPanel,
        )
        WriterPanel.VIDEO -> VideoInsertPanel(
            onChoose = {
                closeCentralPanel()
                videoLauncher.launch("video/*")
            },
            onDismiss = ::closeCentralPanel,
        )
        WriterPanel.PLAYBACK -> {
            val element = playbackElement
            val notebook = state.notebook
            if (element == null || notebook == null) {
                LaunchedEffect(Unit) { closeCentralPanel() }
            } else {
                MediaPlaybackPanel(
                    element = element,
                    notebookDir = ServiceLocator.repository.notebookDir(notebook.id),
                    onDismiss = {
                        playbackElement = null
                        closeCentralPanel()
                    },
                )
            }
        }
        WriterPanel.MORE -> Unit   // 「更多」已改为顶栏锚定菜单,不再走中央面板
        null -> Unit
    }
    }
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
    onEdit: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    val reduced = LocalMilkMotion.current
    val dark = LocalMilkDark.current
    val density = LocalDensity.current
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    var position by remember { mutableStateOf(IntOffset.Zero) }
    if (visible && box != null && surface != null) {
        val (l, t) = surface.pageToView(box.l, box.t)
        val (r, b) = surface.pageToView(box.r, box.b)
        val gap = with(density) { 12.dp.roundToPx() }
        val edge = with(density) { 8.dp.roundToPx() }
        val topSafe = with(density) { (WriterChromeHeight + 8.dp).roundToPx() }
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
        enter = if (reduced) fadeIn(tween(MilkMotion.reduced))
        else fadeIn(tween(MilkMotion.enter, easing = MilkMotion.EaseOut)) +
            slideInVertically(tween(MilkMotion.enter, easing = MilkMotion.EaseOut)) { -it / 3 },
        exit = if (reduced) fadeOut(tween(MilkMotion.reduced))
        else fadeOut(tween(MilkMotion.exit, easing = MilkMotion.EaseOut)) +
            slideOutVertically(tween(MilkMotion.exit, easing = MilkMotion.EaseOut)) { -it / 3 },
    ) {
        Row(
            Modifier
                .softShadow(10.dp, PillShape)
                .clip(PillShape)
                .background(if (dark) OverlaySurfaceDark else OverlaySurfaceLight)
                .border(1.dp, OverlayBorderDark, PillShape)
                .padding(horizontal = 4.dp)
                .testTag("writer-selection-bar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = OverlayText
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
                        tint = if (snapEnabled) AccentBlueDark else OverlayMuted,
                    )
                },
                enabled = true, active = snapEnabled, onClick = onToggleSnap,
            )
            if (onEdit != null) {
                IconToggleButton(
                    icon = { Icon(Icons.Outlined.Edit, "编辑文字", tint = tint) },
                    enabled = true, onClick = onEdit,
                )
            }
            if (onPlay != null) {
                IconToggleButton(
                    icon = { Icon(Icons.Outlined.PlayArrow, "播放媒体", tint = AccentBlueDark) },
                    enabled = true, onClick = onPlay,
                )
            }
            IconToggleButton(
                icon = { Icon(Icons.Outlined.AutoAwesome, "发送给 Agent", tint = AccentBlueDark) },
                enabled = true, onClick = onSend,
            )
            IconToggleButton(
                icon = { Icon(Icons.Outlined.Delete, "删除选中元素", tint = MaterialTheme.colorScheme.error) },
                enabled = true, onClick = onDelete,
            )
            IconToggleButton(
                icon = { Icon(Icons.Outlined.Close, "取消选择", tint = tint) },
                enabled = true, onClick = onClear,
            )
        }
    }
}

/** chrome 带边缘 hairline(1px,随主题;§5.1 顶栏/状态条/侧栏分界) —— 委托共享实现 */
internal fun Modifier.edgeHairline(
    color: Color,
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = false,
): Modifier = milkEdgeHairline(color, left = left, top = top, right = right, bottom = bottom)

/** 顶栏图标钮:48dp 命中(A-09),图标保持 18dp;单击语义——不注册空双击/长按(A-34) */
@Composable
internal fun IconToggleButton(
    icon: @Composable () -> Unit,
    enabled: Boolean,
    active: Boolean = false,
    onClick: () -> Unit,
    badge: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(MilkRadius.sm))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .alpha(if (enabled) 1f else 0.38f)
            .softClickable(enabled = enabled, onClick = onClick),
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

/** 新建页中央面板:位置 + 复用当前纸张;替换页面暂不实现。 */
@Composable
private fun NewPagePanel(
    anchorPageId: String?,
    paperDescription: String,
    onDismiss: () -> Unit,
    onInsert: (PageInsertPosition) -> Unit,
) {
    MilkActionPanel(
        title = "新建页面",
        subtitle = "添加位置 · $paperDescription",
        actions = listOf(
            MilkPanelAction(
                label = "页面之前",
                icon = Icons.Outlined.KeyboardArrowUp,
                trailingText = anchorPageId,
                enabled = anchorPageId != null,
                onClick = { onInsert(PageInsertPosition.BEFORE) },
            ),
            MilkPanelAction(
                label = "页面之后",
                icon = Icons.Outlined.KeyboardArrowDown,
                trailingText = anchorPageId,
                enabled = anchorPageId != null,
                onClick = { onInsert(PageInsertPosition.AFTER) },
            ),
            MilkPanelAction(
                label = "最后一页",
                icon = Icons.Outlined.Add,
                onClick = { onInsert(PageInsertPosition.END) },
            ),
        ),
        onDismiss = onDismiss,
    )
}

/** 中央二次确认(删除/跨页清除);不使用系统 AlertDialog,保持统一表面。 */
@Composable
internal fun ConfirmPagePanel(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    MilkConfirmDialog(
        title = title,
        body = message,
        confirmLabel = confirmLabel,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        destructive = true,
    )
}

/** 当前笔记本纸张摘要(新建页面板副标题;不逐页改纸张)。 */
private fun paperDescription(state: WriterUiState): String {
    val nb = state.notebook
    val paper = com.milknote.notes.model.PAPER_KINDS
        .firstOrNull { it.first == (nb?.paper ?: "dot") }
        ?.second ?: "点阵"
    val orientation = if (nb?.orientation == "landscape") "横向" else "竖向"
    return "沿用本书纸张:$paper · ${nb?.pageSize ?: "A4"} $orientation"
}

/** 双开分屏页码步进:32dp 点击区、禁用态明确,替代裸字符 */
@Composable
private fun SplitPageStep(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(MilkRadius.xs))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = cs.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
        )
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
    readingMode: Boolean,
    swipeNewPage: Boolean = true,
) {
    AndroidView(
        factory = { ctx ->
            InkSurfaceView(ctx).apply {
                surfaceRef.value = this
                inputInterceptor = ServiceLocator.penPort::onDispatchTouchEvent
                onViewportChanged = vm::refreshSelectionOverlay
                onPreviousPage = vm::previousPage
                onNextPage = vm::nextPage
                // 末页继续右滑自动新建页(设置 → 书写 可关闭)
                onNextPageAtEnd = if (swipeNewPage) vm::appendPageAtEnd else null
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
            val spec = state.notebook?.pageSpec(state.currentPageId)
            view.paperStyle = spec?.paper ?: "dot"
            view.imageDir = state.notebook?.id?.let { ServiceLocator.repository.notebookDir(it) }
            view.frontBufferEnabled = state.frontBufferEnabled
            view.readingMode = readingMode
            view.darkTheme = dark
            if (!dark && spec != null) {
                view.paperColor = runCatching { android.graphics.Color.parseColor(spec.paperColor) }
                    .getOrDefault(0xFFFFFFFF.toInt())
            }
            view.penConnected = penConnected
            view.canTurnPrevious = state.canPreviousPage
            view.canTurnNext = state.canNextPage
            // 纸张尺寸+方向(旧笔记本缺字段 → A4 竖,几何与历史一致)
            val nb = state.notebook
            val (pw, ph) = nb?.pageMm(state.currentPageId, view.mmToPx) ?: (210f * view.mmToPx to 297f * view.mmToPx)
            view.setPageSizePx(pw, ph)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** 双开分屏右侧独立纸面画布 */
@Composable
private fun SplitPaperCanvas(
    vm: WriterViewModel,
    state: WriterUiState,
    dark: Boolean,
    penConnected: Boolean,
    readingMode: Boolean,
) {
    val splitNb = state.splitNotebook ?: return
    AndroidView(
        factory = { ctx ->
            InkSurfaceView(ctx).apply {
                inputInterceptor = ServiceLocator.penPort::onDispatchTouchEvent
                onPreviousPage = { vm.stepSplitPage(-1) }
                onNextPage = { vm.stepSplitPage(1) }
            }
        },
        update = { view ->
            view.controller = vm.splitController
            val spec = splitNb.pageSpec(state.splitPageId)
            view.paperStyle = spec.paper
            view.imageDir = ServiceLocator.repository.notebookDir(splitNb.id)
            view.frontBufferEnabled = state.frontBufferEnabled
            view.readingMode = readingMode
            view.darkTheme = dark
            if (!dark) {
                view.paperColor = runCatching { android.graphics.Color.parseColor(spec.paperColor) }
                    .getOrDefault(0xFFFFFFFF.toInt())
            }
            view.penConnected = penConnected
            view.canTurnPrevious = state.splitPageIndex > 0
            view.canTurnNext = state.splitPageIndex >= 0 && state.splitPageIndex < state.splitPages.lastIndex
            val (pw, ph) = splitNb.pageMm(state.splitPageId, view.mmToPx)
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
