package com.milknote.app.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.theme.LocalMilkWarning
import com.milknote.app.ui.theme.Mono
import com.milknote.device.pen.PenAction
import com.milknote.device.pen.PenTrigger
import com.milknote.ink.model.PressureGamma
import kotlinx.coroutines.launch

/**
 * 设置页(A2.7 Codex 风重构):左导航(分组+图标行) + 右侧 ≤560dp 居中列五页
 * (外观/书写/笔与手势/Provider 与模型/更新)。行为沿 A2.4:即时生效、DataStore、
 * penRouter 改绑、gb_boosting 接管检测、AppUpdater 局域网更新;仅视觉重建。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, initialPage: String = "appearance") {
    val scope = rememberCoroutineScope()
    val settings = ServiceLocator.appSettings
    val appSettings by settings.flow.collectAsState(initial = AppSettings())
    val router = ServiceLocator.penRouter
    val mapping by router.mapping.collectAsState()
    val caps by ServiceLocator.penPort.capabilities.collectAsState()
    val battery by ServiceLocator.penBattery.level.collectAsState()
    val batterySource by ServiceLocator.penBattery.sourceName.collectAsState()

    // A-17:Agent「管理模型」直达 agent 页;key 随入口变化重初始化
    var page by rememberSaveable(initialPage) { mutableStateOf(initialPage) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) ServiceLocator.penBattery.refresh()
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)   // 背景先铺满(含状态栏区域)
            .windowInsetsPadding(WindowInsets.safeDrawing),     // 内容再避开 insets
    ) {
        SettingsNavRail(
            selected = page,
            onSelect = { page = it },
            versionLine = "v${com.milknote.app.BuildConfig.VERSION_NAME} · ${fmtTime(com.milknote.app.BuildConfig.BUILD_TIME)}",
            onBack = onBack,
        )
        // 导航/内容分隔:上下渐隐 hairline(去生硬实线)
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to androidx.compose.ui.graphics.Color.Transparent,
                        0.05f to MaterialTheme.colorScheme.outline,
                        0.95f to MaterialTheme.colorScheme.outline,
                        1f to androidx.compose.ui.graphics.Color.Transparent,
                    ),
                ),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 560.dp)   // 先限宽再 fill(顺序反了会通栏)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                when (page) {
                    "appearance" -> AppearancePage(appSettings = appSettings, setThemeMode = { scope.launch { settings.setThemeMode(it) } })
                    "writing" -> WritingPage(appSettings = appSettings, setPressure = { scope.launch { settings.setPressureGamma(it) } }, setFrontBuffer = { scope.launch { settings.setFrontBufferInk(it) } })
                    "pen" -> PenPage(mapping = mapping, caps = caps, battery = battery, batterySource = batterySource, permLauncher = permLauncher)
                    "agent" -> {
                        SettingsPageHeader(
                            "Provider 与模型",
                            "Key 经 Android Keystore 加密仅存本机,不随笔记本备份;整个 Agent 仅在视觉模型上工作",
                        )
                        AgentSettingsSection()
                    }
                    "updates" -> UpdatesPage(appSettings = appSettings)
                }
            }
        }
    }
}

// ==================== 外观 ====================

@Composable
private fun AppearancePage(appSettings: AppSettings, setThemeMode: (ThemeMode) -> Unit) {
    SettingsPageHeader("外观", "纸面、点阵与笔迹颜色;更改即时生效")
    SettingsSectionHeader("主题")
    SettingsCard {
        SelectRow(
            title = "主题模式",
            subtitle = "纸面与笔迹显示随主题切换",
            items = ThemeMode.entries,
            selected = appSettings.themeMode,
            label = { it.label },
            onPick = setThemeMode,
        )
    }
    FooterHint("导出与缩略图恒为浅色纸")
}

// ==================== 书写 ====================

@Composable
private fun WritingPage(
    appSettings: AppSettings,
    setPressure: (PressureGamma) -> Unit,
    setFrontBuffer: (Boolean) -> Unit,
) {
    SettingsPageHeader("书写", "压感与出墨路径;更改即时生效")
    SettingsSectionHeader("压感曲线", "轻触 0.3mm → 重按 2.2mm")
    SettingsCard {
        SelectRow(
            title = "曲线",
            items = PressureGamma.entries,
            selected = appSettings.pressureGamma,
            label = { it.label },
            onPick = setPressure,
        )
    }
    FooterHint("柔和 γ1.4 / 标准 γ1.0 / 灵敏 γ0.7", mono = true)

    SettingsSectionHeader("出墨")
    SettingsCard {
        SettingsRow(
            title = "前置缓冲出墨",
            subtitle = "实验性 · GL 前置缓冲,<10ms 出墨;关闭 = Canvas 路径",
            onClick = { setFrontBuffer(!appSettings.frontBufferInk) },   // 整行可点(A-9);开关本体亦可点
            trailing = { MilkSwitch(checked = appSettings.frontBufferInk, onChange = setFrontBuffer) },
        )
    }
}

// ==================== 笔与手势 ====================

@Composable
private fun PenPage(
    mapping: Map<PenTrigger, PenAction>,
    caps: com.milknote.device.pen.PenCapabilities?,
    battery: Int,
    batterySource: String?,
    permLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val router = ServiceLocator.penRouter
    val takeoverGranted = remember { com.milknote.app.pen.PenKeyTakeover.canWrite(ctx) }

    SettingsPageHeader("笔与手势", "按键映射与长按接管")

    SettingsSectionHeader("长按接管", "接管后书写页内长按侧键不触发系统行为(无提示)")
    SettingsCard {
        if (takeoverGranted) {
            StatusLine("长按接管已启用,长按侧键完全交由下方映射", tone = StatusTone.OK)
        } else {
            StatusLine("未激活:长按侧键仍触发系统截图/小米创作。一次性激活,连接电脑执行", tone = StatusTone.WARN)
            StatusLine(
                "adb shell pm grant com.milknote.app android.permission.WRITE_SECURE_SETTINGS",
                tone = StatusTone.WARN, mono = true,
            )
        }
    }

    SettingsSectionHeader("按键映射", "MilkNote 按键仅在书写时生效")
    SettingsCard {
        val supported = supportedTriggers(caps?.stylusButtonCount)
        if (caps == null) {
            StatusLine("未检测到笔事件——按键探测后将按实际能力过滤", tone = StatusTone.NEUTRAL)
        }
        supported.forEachIndexed { idx, trigger ->
            if (idx > 0 || caps == null) Hairline()
            SelectRow(
                title = trigger.label,
                items = PenAction.entries,
                selected = mapping[trigger] ?: PenAction.NONE,
                label = { it.label },
                onPick = { router.rebind(trigger, it) },
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextAction("恢复默认映射", onClick = { router.resetDefault() })
    }

    SettingsSectionHeader("笔电量")
    SettingsCard {
        SettingsRow(
            title = "电量",
            subtitle = batterySource?.let { "来源 $it · Battery 0x180F 订阅,磁吸吸附自动更新" }
                ?: "未识别到笔 · Battery 0x180F 订阅,磁吸吸附自动更新",
            trailing = {
                Text(
                    if (battery < 0) "--%" else "$battery%",
                    fontFamily = Mono, fontSize = 18.sp,
                    color = if (battery in 0..19) LocalMilkWarning.current else MaterialTheme.colorScheme.onBackground,
                )
            },
        )
        if (!ServiceLocator.penBattery.permissionGranted) {
            Hairline()
            SettingsRow(
                title = "蓝牙权限",
                subtitle = "读取笔电量需要蓝牙连接权限",
                trailing = {
                    TextAction("授予权限", primary = true) {
                        permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                },
            )
        }
    }
}

/** Trigger 列表按实际能力过滤(PEN-ADAPTATION §3.5:焦点键/Pro 待 P1/硬件,不显示) */
private fun supportedTriggers(buttonCount: Int?): List<PenTrigger> = buildList {
    if (buttonCount == null || buttonCount >= 1) {
        add(PenTrigger.PRIMARY_TAP)
        add(PenTrigger.PRIMARY_DOUBLE)
        add(PenTrigger.PRIMARY_LONG)
        add(PenTrigger.PRIMARY_HOLD_WRITE)
    }
    if (buttonCount == null || buttonCount >= 2) {
        add(PenTrigger.SECONDARY_TAP)
        add(PenTrigger.SECONDARY_HOLD_WRITE)
    }
}

// ==================== 更新(局域网开发流:电脑 ./gradlew :app:serveUpdates) ====================

@Composable
private fun UpdatesPage(appSettings: AppSettings) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = ServiceLocator.appSettings
    var server by remember(appSettings.updateServer) { mutableStateOf(appSettings.updateServer) }
    var state by remember { mutableStateOf<AppUpdater.State>(AppUpdater.State.Idle) }

    SettingsPageHeader("更新", "局域网更新:电脑运行更新服务后即可检查新版本")

    SettingsSectionHeader("更新服务器")
    SettingsCard {
        StatusLine(
            "当前 v${com.milknote.app.BuildConfig.VERSION_NAME} · 构建 ${fmtTime(com.milknote.app.BuildConfig.BUILD_TIME)}",
            tone = StatusTone.NEUTRAL, mono = true,
        )
        Hairline()
        // 地址编辑即时生效:点「检查更新」时归一化并持久化(与旧行为一致)
        SettingsInputField(
            value = server,
            onValueChange = { server = it },
            placeholder = "http://192.168.x.x:8765",
            label = "电脑地址",
        )
        Hairline()
        val statusText = when (val s = state) {
            is AppUpdater.State.Idle -> if (server.isBlank()) "填电脑地址后检查" else "就绪"
            is AppUpdater.State.Checking -> "检查中…"
            is AppUpdater.State.UpToDate -> "已是最新版本"
            is AppUpdater.State.Available -> "发现新版本 ${s.latest.versionName}(${fmtTime(s.latest.buildTime)})"
            is AppUpdater.State.Downloading -> "下载中 ${s.percent}%"
            is AppUpdater.State.ReadyToInstall -> "已下载,点击「安装」"
            is AppUpdater.State.Failed -> "失败:${s.reason}"
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                statusText,
                fontSize = 12.sp, lineHeight = 17.sp,
                color = when (state) {
                    is AppUpdater.State.Failed -> MaterialTheme.colorScheme.error
                    is AppUpdater.State.UpToDate -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            // 注意:分支值先捕获到局部 val(onClick lambda 里可变 state 已变化,不可再 cast)
            when (val s = state) {
                is AppUpdater.State.Downloading, is AppUpdater.State.Checking -> {}
                is AppUpdater.State.ReadyToInstall -> {
                    val file = s.file
                    TextAction("安装", primary = true, onClick = {
                        if (!AppUpdater.canRequestInstall(context)) {
                            context.startActivity(AppUpdater.installPermissionIntent())
                        } else {
                            AppUpdater.launchInstall(context, file)
                        }
                    })
                }
                is AppUpdater.State.Available -> {
                    val latest = s.latest
                    TextAction("下载", primary = true, onClick = {
                        scope.launch {
                            state = AppUpdater.State.Downloading(0)
                            val f = AppUpdater.download(context, server, latest) { p ->
                                state = AppUpdater.State.Downloading(p)
                            }
                            state = if (f != null) AppUpdater.State.ReadyToInstall(f)
                            else AppUpdater.State.Failed("下载失败(服务开着吗?)")
                        }
                    })
                }
                else -> {
                    TextAction("检查更新", primary = true, enabled = server.isNotBlank(), onClick = {
                        scope.launch { settings.setUpdateServer(AppUpdater.normalizeServer(server)) }
                        scope.launch {
                            state = AppUpdater.State.Checking
                            val latest = AppUpdater.check(server)
                            state = when {
                                latest == null -> AppUpdater.State.Failed("连不上更新服务")
                                AppUpdater.isNewer(
                                    latest,
                                    com.milknote.app.BuildConfig.VERSION_NAME,
                                    com.milknote.app.BuildConfig.BUILD_TIME,
                                ) -> AppUpdater.State.Available(latest)
                                else -> AppUpdater.State.UpToDate
                            }
                        }
                    })
                }
            }
        }
    }
    FooterHint("电脑上运行 ./gradlew :app:serveUpdates 启动更新服务")
}

private fun fmtTime(ms: Long) =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(ms))
