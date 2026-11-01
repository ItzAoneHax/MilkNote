package com.milknote.app.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.agent.AgentDrawerHost
import com.milknote.app.ui.bookshelf.BookshelfScreen
import com.milknote.app.ui.bookshelf.BookshelfViewModel
import com.milknote.app.ui.common.CommandItem
import com.milknote.app.ui.common.CommandPalette
import com.milknote.app.ui.pen.PenDiagnosticsSheet
import com.milknote.app.ui.settings.AppSettings
import com.milknote.app.ui.settings.SettingsScreen
import com.milknote.app.ui.settings.ThemeMode
import com.milknote.app.ui.theme.MilkTheme
import com.milknote.app.ui.writer.WriterScreen
import com.milknote.app.ui.writer.WriterViewModel
import com.milknote.device.pen.PenAction
import com.milknote.device.pen.PenTrigger
import com.milknote.ink.input.InkAction
import kotlinx.coroutines.flow.first

/** IA 仅两层 + 设置(DESIGN-V0 §5.3:书架 ↔ 书写页;设置为覆盖层) */
private sealed interface MilkScreen {
    data object Bookshelf : MilkScreen
    data class Writer(val notebookId: String) : MilkScreen
    data object Settings : MilkScreen
}

private fun PenAction.toInkAction(): InkAction = when (this) {
    PenAction.TOGGLE_ERASER -> InkAction.TOGGLE_ERASER
    PenAction.LASO -> InkAction.LASO
    PenAction.SNIP -> InkAction.SNIP
    PenAction.UNDO -> InkAction.UNDO
    PenAction.REDO -> InkAction.REDO
    PenAction.NEW_PAGE -> InkAction.NEW_PAGE
    PenAction.TEMP_ERASER -> InkAction.TEMP_ERASER
    PenAction.NONE -> InkAction.TOGGLE_ERASER // 不应出现
}

@Composable
fun MilkRoot() {
    var screen by remember { mutableStateOf<MilkScreen>(MilkScreen.Bookshelf) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showPalette by remember { mutableStateOf(false) }
    var showNewNotebook by remember { mutableStateOf(false) }
    var settingsFromWriter by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val bookshelfVm: BookshelfViewModel = viewModel()
    val writerVm: WriterViewModel = viewModel()

    // 外观:跟随系统 / 浅色 / 深色(设置页可调)
    val appSettings by ServiceLocator.appSettings.flow.collectAsState(initial = AppSettings())
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (appSettings.themeMode) {
        ThemeMode.FOLLOW -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // ---- 笔电量权限:仅自动申请一次(拒绝后走 设置→笔电量 手动授权) ----
    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) ServiceLocator.penBattery.refresh() }
    LaunchedEffect(Unit) {
        val settings = ServiceLocator.appSettings
        val s = settings.flow.first()
        if (!ServiceLocator.penBattery.permissionGranted) {
            if (!s.btPermissionAsked) {
                settings.setBtPermissionAsked()
                btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            ServiceLocator.penBattery.refresh()
        }
    }

    // 笔键消费只在书写页启用(消费会拦截 PAGE_UP/DOWN,防止书架/设置页吞外接键盘翻页)
    LaunchedEffect(screen) {
        ServiceLocator.penPort.keyRoutingEnabled = screen is MilkScreen.Writer
    }

    // 笔键长按接管(2026-11-01 真机实锤):
    // 首选 PenKeyTakeover(gb_boosting 标志,MIUI 官方通道,一次性 adb 授权
    // WRITE_SECURE_SETTINGS 后应用可自写)——上下键长按系统行为完全静默;
    // 未授权时回退 FLAG_SECURE(拦得住但系统弹「无法截屏」提示)。
    DisposableEffect(screen) {
        val window = ServiceLocator.activity?.window
        val inWriter = screen is MilkScreen.Writer
        if (inWriter && com.milknote.app.pen.PenKeyTakeover.canWrite(context)) {
            com.milknote.app.pen.PenKeyTakeover.set(context, true)
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            com.milknote.app.pen.PenKeyTakeover.set(context, false)
            if (inWriter) {
                window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        onDispose { com.milknote.app.pen.PenKeyTakeover.set(context, false) }
    }

    // 笔键动作只在书写页生效(DESIGN-V0 §3.2:任何界面状态生效——M1 先接书写页)。
    // 「按住+书写」修饰模式不走路由(落笔瞬间由 InkSurfaceView 经 holdActionProvider
    // 同步处理,绕开协程投递的时序竞态),这里跳过避免二次触发。
    LaunchedEffect(Unit) {
        ServiceLocator.penPort.actions.collect { e ->
            val holdWrite = e.trigger == PenTrigger.PRIMARY_HOLD_WRITE ||
                e.trigger == PenTrigger.SECONDARY_HOLD_WRITE
            if (!holdWrite && screen is MilkScreen.Writer) {
                writerVm.onPenAction(e.action.toInkAction())
            }
        }
    }

    // ---- 外接键盘快捷键(⌘K 命令面板 / ⌥A Agent;MainActivity.dispatchKeyEvent 发射) ----
    LaunchedEffect(Unit) {
        ServiceLocator.uiEvents.collect { ev ->
            when (ev) {
                ServiceLocator.UiEvent.OpenPalette -> showPalette = true
                ServiceLocator.UiEvent.OpenAgent -> {
                    if (screen is MilkScreen.Writer) {
                        val st = writerVm.state.value
                        st.notebook?.let {
                            ServiceLocator.agentController.openDrawer(
                                com.milknote.agent.PageRef(it.id, st.currentPageId),
                            )
                        }
                    } else {
                        showPalette = true   // 书架/设置页:⌥A 降级为面板
                    }
                }
            }
        }
    }

    val openSettings: (fromWriter: Boolean) -> Unit = { fromWriter ->
        settingsFromWriter = fromWriter
        screen = MilkScreen.Settings
    }

    // ---- ⌘K 面板条目(按当前屏幕;DESIGN-V0 §5.4) ----
    val paletteItems: List<CommandItem> = when (val s = screen) {
        is MilkScreen.Writer -> {
            val st = writerVm.state.value
            listOfNotNull(
                CommandItem("id_snip", "框选讲解", hint = "笔键长按", section = "Agent") {
                    writerVm.enterSnip()
                },
                CommandItem("id_explain", "讲解本页", hint = st.currentPageId, section = "Agent") {
                    val nb = st.notebook ?: return@CommandItem
                    ServiceLocator.agentController.let {
                        it.bindPage(com.milknote.agent.PageRef(nb.id, st.currentPageId))
                        it.quickIntent(com.milknote.agent.Intents.Id.EXPLAIN)
                    }
                },
                CommandItem("id_chapter", "归纳本章", hint = "${st.pages.size} 页", section = "Agent") {
                    val nb = st.notebook ?: return@CommandItem
                    ServiceLocator.agentController.let {
                        it.bindPage(com.milknote.agent.PageRef(nb.id, st.currentPageId))
                        it.quickIntent(com.milknote.agent.Intents.Id.SUMMARIZE)
                    }
                },
                CommandItem("id_export", "全部导出 PDF", hint = "${st.pages.size} 页", section = "笔记") {
                    writerVm.exportPdf { writerVm.toast(it) }
                },
                CommandItem("id_newpage", "新建页", section = "笔记") { writerVm.newPage() },
                CommandItem("id_settings", "设置", section = "通用") { openSettings(true) },
            )
        }
        else -> {
            val notebooks = bookshelfVm.notebooks.value
            listOf(
                CommandItem("id_new", "新建笔记本", hint = "↵", section = "笔记本") { showNewNotebook = true },
            ) + notebooks.take(6).map { nb ->
                CommandItem("nb_${nb.id}", nb.name, hint = "${bookshelfVm.pageCountOf(nb.id)} 页", section = "切换笔记本") {
                    writerVm.open(nb.id)
                    screen = MilkScreen.Writer(nb.id)
                }
            } + listOf(
                CommandItem("id_settings", "设置", section = "通用") { openSettings(false) },
            )
        }
    }

    BackHandler(enabled = screen is MilkScreen.Writer || screen is MilkScreen.Settings || showPalette) {
        when {
            showPalette -> showPalette = false
            screen is MilkScreen.Writer -> { bookshelfVm.refresh(); screen = MilkScreen.Bookshelf }
            screen is MilkScreen.Settings -> {
                screen = if (settingsFromWriter) {
                    writerVm.state.value.notebook?.let { MilkScreen.Writer(it.id) } ?: MilkScreen.Bookshelf
                } else MilkScreen.Bookshelf
            }
            else -> {}
        }
    }

    MilkTheme(darkTheme = dark) {
        Box(Modifier.fillMaxSize()) {
            when (val s = screen) {
                is MilkScreen.Bookshelf -> BookshelfScreen(
                    vm = bookshelfVm,
                    onOpenNotebook = { id -> writerVm.open(id); screen = MilkScreen.Writer(id) },
                    onOpenDiagnostics = { showDiagnostics = true },
                    onOpenSettings = { openSettings(false) },
                    onOpenPalette = { showPalette = true },
                    showNewNotebook = showNewNotebook,
                    onNewNotebookHandled = { showNewNotebook = false },
                )
                is MilkScreen.Writer -> WriterScreen(
                    vm = writerVm,
                    dark = dark,
                    onBack = { bookshelfVm.refresh(); screen = MilkScreen.Bookshelf },
                    onOpenDiagnostics = { showDiagnostics = true },
                    onOpenPalette = { showPalette = true },
                    onOpenSettings = { openSettings(true) },
                )
                is MilkScreen.Settings -> SettingsScreen(
                    onBack = {
                        screen = if (settingsFromWriter) {
                            writerVm.state.value.notebook?.let { MilkScreen.Writer(it.id) } ?: MilkScreen.Bookshelf
                        } else MilkScreen.Bookshelf
                    },
                )
            }

            // ⑤ Agent 抽屉(浮层,仅书写页;收起时流不中断)
            if (screen is MilkScreen.Writer) {
                Box(Modifier.fillMaxSize()) {
                    AgentDrawerHost(
                        onOpenSettings = { openSettings(true) },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = 0.dp),
                    )
                }
            }

            // ⌘K 命令面板(顶层)
            AnimatedVisibility(
                visible = showPalette,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x52000000))
                        .clickable { showPalette = false },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(Modifier.padding(top = 90.dp).clickable(enabled = false) {}) {
                        CommandPalette(items = paletteItems, onDismiss = { showPalette = false })
                    }
                }
            }

            if (showDiagnostics) {
                PenDiagnosticsSheet(onDismiss = { showDiagnostics = false })
            }
        }
    }
}
