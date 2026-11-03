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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.ViewSidebar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.milknote.app.di.ServiceLocator
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.milknote.app.ui.agent.AgentDrawerHost
import com.milknote.app.ui.agent.SnipOverlay
import com.milknote.app.ui.common.CommandKeyCapsule
import com.milknote.app.ui.common.MonoText
import com.milknote.app.ui.common.PenBatteryCapsule
import com.milknote.app.ui.common.accentBrush
import com.milknote.app.ui.common.innerHighlight
import com.milknote.app.ui.common.paintedShadow
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.Mono
import com.milknote.app.ui.theme.PillShape
import com.milknote.device.pen.PenAction
import com.milknote.ink.input.InkAction
import com.milknote.ink.model.InkTool
import com.milknote.ink.render.InkSurfaceView

/** ③ 书写页:顶栏 / 侧栏(页面缩略) / 纸面画布 / 底部状态条(DESIGN-V0 §5.3–5.4) */
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
    val penCaps by ServiceLocator.penPort.capabilities.collectAsState()
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
    var statusBarHeight by remember { mutableStateOf(0.dp) }
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
    var sidebarVisible by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
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
        // 专注模式:顶栏换成同高背景占位(纸张不动;F-07 红线:不缩放/不移动纸面)
        if (focusMode) {
            Box(Modifier.fillMaxWidth().height(56.dp).background(MaterialTheme.colorScheme.background))
        } else {
        WriterTopBar(
            state = state,
            vm = vm,
            battery = battery,
            onBack = onBack,
            onOpenDiagnostics = onOpenDiagnostics,
            onAgent = {
                // 打开=开抽屉,已打开=真收起(A-16:按钮语义与行为一致)
                if (agentDrawerOpen) ServiceLocator.agentController.closeDrawer() else openAgentDrawer(vm)
            },
            onOpenPalette = onOpenPalette,
            sidebarVisible = sidebarVisible,
            onToggleSidebar = { sidebarVisible = !sidebarVisible },
            motionIndex = motionIndex,
            onMotion = onMotion,
            onPenDetail = { detailTool = it },
            onText = { showTextEditor = true },
            onShapes = { showShapeSettings = true },
            onImages = { showRecentImages = true },
            onExport = onOpenExport,
            onEnterFocus = { focusMode = true },
            agentOpen = agentDrawerOpen,
            agentBusy = agentBusy,
        )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // 专注模式:侧栏/竖轨换成同宽背景占位,画布 bounds 不变
            if (focusMode) {
                if (sidebarVisible) {
                    Spacer(Modifier.width(148.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background))
                }
                Spacer(Modifier.width(64.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background))
            } else {
                androidx.compose.animation.AnimatedVisibility(visible = sidebarVisible) {
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
            // ---- 纸面画布 ----
            Box(
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
                // ⑤ Agent 抽屉(画布区右缘浮层:顶栏下、底部状态条上,DESIGN-V0 §5.4)
                AgentDrawerHost(
                    onOpenSettings = onOpenSettings,
                    onSnip = vm::enterSnip,
                    onExplainPage = { quickAgentIntent(vm, com.milknote.agent.Intents.Id.EXPLAIN) },
                    onSummarizeChapter = { quickAgentIntent(vm, com.milknote.agent.Intents.Id.SUMMARIZE) },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
                // 选中操作条:套索圈选后出现(拖动 = 移动笔迹,进撤销栈);滑入+软阴影
                SelectionBar(
                    visible = state.selectionCount > 0,
                    count = state.selectionCount,
                    onDelete = vm::deleteSelection,
                    onClear = vm::clearSelection,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                )
                // 轻提示 toast:底部滑入
                var lastToast by remember { mutableStateOf("") }
                toast?.let { lastToast = it }
                ToastBar(
                    visible = toast != null,
                    text = lastToast,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                )
            }
        }

        // 状态条:专注模式换同高背景占位(高度取实测值,画布 bounds 不变)
        if (focusMode) {
            Box(Modifier.fillMaxWidth().height(statusBarHeight).background(MaterialTheme.colorScheme.background))
        } else {
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { px ->
                        statusBarHeight = with(density) { px.height.toDp() }
                    },
            ) {
                StatusBar(state, penConnected = penConnected, battery = battery, batterySource = batterySource, penSeen = penCaps != null)
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

/** 顶栏:面包屑 | 撤销重做·工具·墨色粗细 居中 | 笔电 ⌘K [Agent] */
@OptIn(ExperimentalFoundationApi::class)
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
    onEnterFocus: () -> Unit = {},
    agentOpen: Boolean = false,
    agentBusy: Boolean = false,
) {
    // ④ 框选时工具组整体禁用(半透明、不可点;DESIGN-V0 §5.3)
    val snipActive = state.snip.phase != SnipPhase.IDLE
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)   // chrome 色带(§5.1):与纸面明确分界
            .innerHighlight(strength = 0.3f)                        // 顶缘内高光
            .edgeHairline(MaterialTheme.colorScheme.outline, bottom = true)
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 面包屑
        Row(Modifier.clickable(onClick = onBack), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("牛奶笔记", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                state.notebook?.name ?: "", fontSize = 13.sp,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))

        DividerDot()

        // 工具组:写实笔形显示各笔墨色;首点选择,再点已选笔进入详细设定
        Spacer(Modifier.width(18.dp))
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
            color = 0xFFFFFFFF,
            active = !modeActive && state.toolSettings.eraserTemporary,
            enabled = !snipActive,
            squeezed = motionIndex != null && kotlin.math.abs(motionIndex - 3) == 1,
            onHoverChanged = { onMotion(3, it) },
            onClick = { vm.toggleEraser() },
        )

        Spacer(Modifier.width(10.dp))
        DividerDot()
        Spacer(Modifier.width(8.dp))

            // 粗细(底色 200ms 过渡;48dp 命中 + 框选时 38% 视觉禁用,A-09/A-14)
            listOf(0.4f, 0.7f, 1.2f).forEach { mm ->
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
                Spacer(Modifier.width(7.dp))
            }

        Spacer(Modifier.weight(1f))

        // 文档功能列表:粗细右侧、笔电量左侧;不再占用独立画布区域
        IconToggleButton(
            icon = { Icon(Icons.Outlined.ViewSidebar, if (sidebarVisible) "收起书架" else "展开书架", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            enabled = true, onClick = onToggleSidebar,
        )
        Spacer(Modifier.width(8.dp))
        // 专注模式入口(F-07):显式 48dp 按钮,不用双击纸面;框选中禁用(避免模式叠加)
        IconToggleButton(
            icon = { Icon(Icons.Outlined.CropFree, "专注模式", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            enabled = !snipActive, onClick = onEnterFocus,
        )
        Spacer(Modifier.width(8.dp))
        IconToggleButton(
            icon = { Icon(Icons.Outlined.Gesture, "框选", modifier = Modifier.size(18.dp), tint = if (state.lassoArmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
            enabled = !snipActive, active = state.lassoArmed, onClick = vm::toggleLasso,
        )
        Spacer(Modifier.width(8.dp))
        IconToggleButton(
            icon = { Icon(Icons.Outlined.Title, "文字框", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            enabled = !snipActive, onClick = onText,
        )
        Spacer(Modifier.width(8.dp))
        IconToggleButton(
            icon = { Icon(Icons.Outlined.Category, "图形绘制", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            enabled = !snipActive, onClick = onShapes,
        )
        Spacer(Modifier.width(8.dp))
        IconToggleButton(
            icon = { Icon(Icons.Outlined.Image, "插入图片", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            enabled = !snipActive, onClick = onImages,
        )
        Spacer(Modifier.width(8.dp))
        IconToggleButton(
            icon = { Icon(Icons.Outlined.FileDownload, "导出", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            enabled = !snipActive, onClick = onExport,
        )

        Spacer(Modifier.width(14.dp))
        // 笔状态胶囊:单击直达诊断(A-16;原空 onClick + 仅长按入口已废);48dp 命中
        Box(
            Modifier
                .size(48.dp)
                .clickable(onClick = onOpenDiagnostics),
            contentAlignment = Alignment.Center,
        ) {
            PenBatteryCapsule(level = battery)
        }
        Spacer(Modifier.width(14.dp))
        CommandKeyCapsule(onClick = onOpenPalette)
        Spacer(Modifier.width(14.dp))

        // Agent(M2 接框选;蓝色纪律:仅 Agent 与激活态用强调色;轻投影+按压)
        // A-16:打开=淡蓝底「收起」;后台流式=蓝点提示
        if (agentOpen) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                    .softClickable(onClick = onAgent)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (agentBusy) {
                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(Modifier.width(6.dp))
                }
                Text("收起", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Row(
                Modifier
                    .softShadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(accentBrush())
                    .softClickable(onClick = onAgent)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (agentBusy) {
                    Box(Modifier.size(6.dp).background(Color.White, CircleShape))
                    Spacer(Modifier.width(6.dp))
                }
                Text("Agent", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 选中操作条:顶部滑入 + 软阴影深胶囊;减弱动效=纯淡化(红线三.11) */
@Composable
private fun SelectionBar(
    visible: Boolean,
    count: Int,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = com.milknote.app.ui.theme.LocalMilkMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) fadeIn(tween(60))
        else fadeIn(tween(200)) + slideInVertically(tween(220)) { -it },
        exit = if (reduced) fadeOut(tween(60))
        else fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it },
    ) {
        Row(
            Modifier
                .clip(PillShape)
                .softShadow(8.dp, PillShape)
                .background(Color(0xF023221E))
                .paintedShadow(PillShape, edge = 6.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "已选 $count 笔 · 拖动移动",
                color = Color(0xFFE8E6E0),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color(0x33D2483C))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text("删除", color = Color(0xFFE06558), fontSize = 12.sp)
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text("✕", color = Color(0xFF9B978D), fontSize = 13.sp)
            }
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

@Composable
private fun DividerDot() {
    Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
}

/** 顶栏图标钮:48dp 命中(A-09),图标保持 18dp;单击语义——不注册空双击/长按(A-34) */
@Composable
private fun IconToggleButton(icon: @Composable () -> Unit, enabled: Boolean, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
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
    val (pwMm, hMm) = com.milknote.notes.model.PAGE_SIZES_MM[nb?.pageSize] ?: com.milknote.notes.model.PAGE_SIZES_MM.getValue("A4")
    val pageAspect = if (nb?.orientation == "landscape") hMm / pwMm else pwMm / hMm
    Column(
        Modifier
            .width(148.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .edgeHairline(MaterialTheme.colorScheme.outline.copy(alpha = 0.75f), right = true)
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

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            // 纸张尺寸+方向(旧笔记本缺字段 → A4 竖,几何与历史一致)
            val nb = state.notebook
            val (pw, ph) = nb?.pageMm(view.mmToPx) ?: (210f * view.mmToPx to 297f * view.mmToPx)
            view.setPageSizePx(pw, ph)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** 底部状态条(③:真实笔状态 + 手势提示 + 页码;按模式切换文案) */
@Composable
private fun StatusBar(
    state: WriterUiState,
    penConnected: Boolean,
    battery: Int,
    batterySource: String?,
    penSeen: Boolean,
) {
    // A-10:反映真实能力而非「就绪」假设;无笔时明确手指可写
    val penStatus = when {
        batterySource != null && battery >= 0 -> "笔 已连接 · $battery%"
        batterySource != null -> "笔 已连接 · 电量 --%"
        battery >= 0 -> "笔 已连接 · $battery%"
        penSeen -> "笔已识别 · 蓝牙未连接"
        else -> "未检测到笔 · 手指可写"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)   // chrome 色带:与画布明确分界
            .edgeHairline(MaterialTheme.colorScheme.outline, top = true)
            .padding(horizontal = 20.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(penStatus, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        val hint = when {
            state.snip.phase != SnipPhase.IDLE -> "书写已暂停 · 截图模式"
            state.selectionCount > 0 -> "套索:拖动移动 · ✕ 退出"
            state.lassoArmed -> "套索:圈选笔迹(圈住即选中)"
            else -> "双击笔键=笔⇄橡皮 · 长按=框选 · 手指=导航"
        }
        Text(hint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
        Spacer(Modifier.weight(1f))
        if (state.frontBufferEnabled) {
            // 灰阶元数据:蓝只留给 Agent 与激活态(蓝色纪律)
            MonoText("front-buffer", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
            Spacer(Modifier.width(10.dp))
        }
        MonoText("${state.currentPageId} / ${state.pages.size} 页", color = MaterialTheme.colorScheme.onSurface)
    }
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
