package com.milknote.app.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.bookshelf.BookshelfScreen
import com.milknote.app.ui.bookshelf.BookshelfViewModel
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

    BackHandler(enabled = screen is MilkScreen.Writer || screen is MilkScreen.Settings) {
        when (screen) {
            is MilkScreen.Writer -> { bookshelfVm.refresh(); screen = MilkScreen.Bookshelf }
            is MilkScreen.Settings -> screen = MilkScreen.Bookshelf
            else -> {}
        }
    }

    MilkTheme(darkTheme = dark) {
        when (val s = screen) {
            is MilkScreen.Bookshelf -> BookshelfScreen(
                vm = bookshelfVm,
                onOpenNotebook = { id -> writerVm.open(id); screen = MilkScreen.Writer(id) },
                onOpenDiagnostics = { showDiagnostics = true },
                onOpenSettings = { screen = MilkScreen.Settings },
            )
            is MilkScreen.Writer -> WriterScreen(
                vm = writerVm,
                dark = dark,
                onBack = { bookshelfVm.refresh(); screen = MilkScreen.Bookshelf },
                onOpenDiagnostics = { showDiagnostics = true },
            )
            is MilkScreen.Settings -> SettingsScreen(onBack = { screen = MilkScreen.Bookshelf })
        }

        if (showDiagnostics) {
            PenDiagnosticsSheet(onDismiss = { showDiagnostics = false })
        }
    }
}
