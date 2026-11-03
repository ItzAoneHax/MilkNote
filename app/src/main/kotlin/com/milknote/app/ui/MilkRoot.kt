package com.milknote.app.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.bookshelf.BookshelfScreen
import com.milknote.app.ui.bookshelf.BookshelfViewModel
import com.milknote.app.ui.common.CommandItem
import com.milknote.app.ui.common.CommandPalette
import com.milknote.app.ui.pen.PenDiagnosticsSheet
import com.milknote.app.ui.settings.AppSettings
import com.milknote.app.ui.settings.SettingsScreen
import com.milknote.app.ui.settings.ThemeMode
import com.milknote.app.ui.theme.MilkTheme
import com.milknote.app.ui.writer.ExportScreen
import com.milknote.app.ui.writer.WriterScreen
import com.milknote.app.ui.writer.WriterViewModel
import com.milknote.device.pen.PenAction
import com.milknote.device.pen.PenTrigger
import com.milknote.ink.input.InkAction
import kotlinx.coroutines.flow.first

/** IA 仅两层 + 设置(DESIGN-V0 §5.3:书架 ↔ 书写页;设置为覆盖层);导出页挂书写会话之下 */
private sealed interface MilkScreen {
    data object Bookshelf : MilkScreen
    data class Writer(val notebookId: String) : MilkScreen
    data class Export(val notebookId: String) : MilkScreen
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
    var settingsPage by remember { mutableStateOf("appearance") }
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

    val openSettings: (fromWriter: Boolean, page: String) -> Unit = { fromWriter, page ->
        settingsFromWriter = fromWriter
        settingsPage = page
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
                CommandItem("id_export", "导出笔记", hint = "PNG · PDF", section = "笔记") {
                    screen = MilkScreen.Export(s.notebookId)
                },
                CommandItem("id_newpage", "新建页", section = "笔记") { writerVm.newPage() },
                CommandItem("id_settings", "设置", section = "通用") { openSettings(true, "appearance") },
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
                CommandItem("id_settings", "设置", section = "通用") { openSettings(false, "appearance") },
            )
        }
    }

    BackHandler(
        enabled = screen is MilkScreen.Writer ||
            screen is MilkScreen.Export ||
            screen is MilkScreen.Settings ||
            showPalette,
    ) {
        when {
            showPalette -> showPalette = false
            screen is MilkScreen.Writer -> { bookshelfVm.refresh(); screen = MilkScreen.Bookshelf }
            screen is MilkScreen.Export -> {
                screen = MilkScreen.Writer((screen as MilkScreen.Export).notebookId)
            }
            screen is MilkScreen.Settings -> {
                screen = if (settingsFromWriter) {
                    writerVm.state.value.notebook?.let { MilkScreen.Writer(it.id) } ?: MilkScreen.Bookshelf
                } else MilkScreen.Bookshelf
            }
            else -> {}
        }
    }

    MilkTheme(darkTheme = dark) {
        // A-02:系统栏跟随「最终生效主题」(手动深色 ≠ 系统值)——底色与背景一致、
        // 图标外观随主题反色;不依赖 launch 时按系统 uiMode 定型的 themes.xml
        val sysBarView = androidx.compose.ui.platform.LocalView.current
        val reducedMotion = remember(context) {
            runCatching {
                android.provider.Settings.Global.getFloat(
                    context.contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) == 0f
            }.getOrDefault(false)
        }
        androidx.compose.runtime.DisposableEffect(dark) {
            val window = (sysBarView.context as? android.app.Activity)?.window
                ?: com.milknote.app.di.ServiceLocator.activity?.window
            if (window != null) {
                val bg = if (dark) com.milknote.app.ui.theme.PaperDark else com.milknote.app.ui.theme.PaperLight
                @Suppress("DEPRECATION") // 35 仍生效;enableEdgeToEdge 下我们自绘全屏背景,系统栏只需同色
                window.statusBarColor = bg.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = bg.toArgb()
                // API 29+:系统对透明导航栏强制加对比纱罩(实测残白 #F9F9FF),须显式关闭
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    window.isNavigationBarContrastEnforced = false
                }
                val insets = androidx.core.view.WindowCompat.getInsetsController(window, sysBarView)
                insets.isAppearanceLightStatusBars = !dark
                insets.isAppearanceLightNavigationBars = !dark
            }
            onDispose {}
        }
        androidx.compose.runtime.CompositionLocalProvider(
            com.milknote.app.ui.theme.LocalMilkMotion provides reducedMotion,
        ) {
        Box(Modifier.fillMaxSize()) {
            when (val s = screen) {
                is MilkScreen.Bookshelf -> BookshelfScreen(
                    vm = bookshelfVm,
                    onOpenNotebook = { id -> writerVm.open(id); screen = MilkScreen.Writer(id) },
                    onOpenDiagnostics = { showDiagnostics = true },
                    onOpenSettings = { openSettings(false, "appearance") },
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
                    onOpenSettings = { page -> openSettings(true, page) },
                    // 主线程已在 WriterScreen 增加 onOpenExport 参数(顶栏导出入口接通)
                    onOpenExport = { screen = MilkScreen.Export(s.notebookId) },
                )
                is MilkScreen.Export -> {
                    // 导出页复用书写页 VM(open 幂等:同 id 早退);返回钮走 onBack → Writer
                    LaunchedEffect(s.notebookId) { writerVm.open(s.notebookId) }
                    ExportScreen(
                        vm = writerVm,
                        onBack = { screen = MilkScreen.Writer(s.notebookId) },
                    )
                }
                is MilkScreen.Settings -> SettingsScreen(
                    initialPage = settingsPage,
                    onBack = {
                        screen = if (settingsFromWriter) {
                            writerVm.state.value.notebook?.let { MilkScreen.Writer(it.id) } ?: MilkScreen.Bookshelf
                        } else MilkScreen.Bookshelf
                    },
                )
            }

            // ⌘K 命令面板(顶层;scrim 淡入,面板本体 0.97→1 缩放入场)
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
                        // 减弱动效:取消缩放,仅 ≤80ms 淡化(红线三.11)
                        val reduced = com.milknote.app.ui.theme.LocalMilkMotion.current
                        AnimatedVisibility(
                            visible = showPalette,
                            enter = if (reduced) fadeIn(animationSpec = tween(60))
                            else fadeIn(animationSpec = tween(180)) +
                                scaleIn(initialScale = 0.97f, animationSpec = tween(200)),
                            exit = fadeOut(animationSpec = tween(if (reduced) 60 else 140)),
                        ) {
                            CommandPalette(items = paletteItems, onDismiss = { showPalette = false })
                        }
                    }
                }
            }

            if (showDiagnostics) {
                PenDiagnosticsSheet(onDismiss = { showDiagnostics = false })
            }
        }
        }
    }
}
