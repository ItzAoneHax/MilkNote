package com.milknote.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.MonoText
import com.milknote.app.ui.theme.Mono
import com.milknote.device.pen.PenAction
import com.milknote.device.pen.PenTrigger
import com.milknote.ink.model.PressureGamma
import kotlinx.coroutines.launch

/**
 * 设置 → 笔与手势(PEN-ADAPTATION §4.4):
 * Trigger 列表(按 PenCapabilities 实际支持项显示)→ 改绑 PenAction + 恢复默认;
 * 压感曲线三档;前置缓冲出墨开关;笔电量权限。入口:书架顶栏设置图标。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings = ServiceLocator.appSettings
    val appSettings by settings.flow.collectAsState(initial = AppSettings())
    val router = ServiceLocator.penRouter
    val mapping by router.mapping.collectAsState()
    val caps by ServiceLocator.penPort.capabilities.collectAsState()
    val battery by ServiceLocator.penBattery.level.collectAsState()
    val batterySource by ServiceLocator.penBattery.sourceName.collectAsState()

    var picking by remember { mutableStateOf<PenTrigger?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) ServiceLocator.penBattery.refresh()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)   // 背景先铺满(含状态栏区域)
            .windowInsetsPadding(WindowInsets.safeDrawing),     // 内容再避开 insets
    ) {
        // ---- 顶栏 ----
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.clickable(onClick = onBack), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("设置", style = MaterialTheme.typography.titleMedium)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ---- 笔与手势 ----
            SettingsCard(title = "笔与手势", meta = "PEN & GESTURES") {
                // 系统冲突固定文案(PEN-ADAPTATION §4.4)
                Text(
                    "副键长按为系统截图、主键长按可能唤起画板——MilkNote 按键仅在书写时生效。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(8.dp))

                // 长按接管状态(实锤通道:gb_boosting,一次性 adb 授权)
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val takeoverGranted = remember { com.milknote.app.pen.PenKeyTakeover.canWrite(ctx) }
                Text(
                    if (takeoverGranted) {
                        "✓ 长按接管已启用:书写页内长按侧键不触发系统行为(无提示),完全交由上方映射。"
                    } else {
                        "长按接管未激活(长按侧键仍触发系统截图/小米创作)。激活(一次性,连接电脑执行):\n" +
                            "adb shell pm grant com.milknote.app android.permission.WRITE_SECURE_SETTINGS"
                    },
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = if (takeoverGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (takeoverGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(12.dp))

                val supported = supportedTriggers(caps?.stylusButtonCount)
                if (caps == null) {
                    MonoText("未检测到笔事件——按键探测后将按实际能力过滤", color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(6.dp))
                }
                supported.forEach { trigger ->
                    val action = mapping[trigger] ?: PenAction.NONE
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { picking = trigger }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(trigger.label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.weight(1f))
                        Text(action.label, fontSize = 12.sp, fontFamily = Mono, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("›", color = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(Modifier.height(2.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { router.resetDefault() }) { Text("恢复默认") }
                }
            }

            // ---- 外观 ----
            SettingsCard(title = "外观", meta = "APPEARANCE") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val active = appSettings.themeMode == mode
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { scope.launch { settings.setThemeMode(mode) } }
                                .padding(horizontal = 18.dp, vertical = 6.dp),
                        ) {
                            Text(
                                mode.label,
                                fontSize = 13.sp,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                MonoText("纸面与笔迹显示随主题切换;导出/缩略图恒为浅色纸", color = MaterialTheme.colorScheme.outline)
            }

            // ---- 书写 ----
            SettingsCard(title = "书写", meta = "WRITING") {
                Text("压感曲线", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PressureGamma.entries.forEach { g ->
                        val active = appSettings.pressureGamma == g
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { scope.launch { settings.setPressureGamma(g) } }
                                .padding(horizontal = 18.dp, vertical = 6.dp),
                        ) {
                            Text(
                                g.label,
                                fontSize = 13.sp,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                MonoText("轻触 0.3mm → 重按 2.2mm;柔和 γ1.4 / 标准 γ1.0 / 灵敏 γ0.7", color = MaterialTheme.colorScheme.outline)

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("前置缓冲出墨", fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        MonoText("实验性 · GL 前置缓冲,<10ms 出墨;关闭=Canvas 路径", color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = appSettings.frontBufferInk,
                        onCheckedChange = { scope.launch { settings.setFrontBufferInk(it) } },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            // 未开启态:thumb/边框用可见色(默认色在奶白卡上近乎隐形)
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                }
            }

            // ---- 笔电量 ----
            SettingsCard(title = "笔电量", meta = "BATTERY · GATT") {
                val granted = remember(battery, batterySource) {
                    ServiceLocator.penBattery.permissionGranted
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (battery < 0) "--%(未识别到笔)" else "$battery%",
                            fontSize = 20.sp, fontFamily = Mono,
                            color = if (battery in 0..19) Color(0xFFD98E04) else MaterialTheme.colorScheme.onBackground,
                        )
                        batterySource?.let { MonoText("来源 $it", color = MaterialTheme.colorScheme.outline) }
                    }
                    if (!granted) {
                        TextButton(onClick = {
                            permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }) { Text("授予权限") }
                    } else {
                        MonoText("蓝牙已授权", color = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(Modifier.height(4.dp))
                MonoText("Battery Service 0x180F 订阅;磁吸吸附时自动更新", color = MaterialTheme.colorScheme.outline)
            }

            // ---- 更新(局域网开发流:电脑 ./gradlew :app:serveUpdates) ----
            UpdateCard(appSettings = appSettings)

            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- 改绑弹窗 ----
    picking?.let { trigger ->
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(trigger.label) },
            text = {
                Column {
                    PenAction.entries.forEach { action ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    router.rebind(trigger, action)
                                    picking = null
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = mapping[trigger] == action,
                                onClick = { router.rebind(trigger, action); picking = null },
                            )
                            Text(action.label, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = null }) { Text("取消") }
            },
        )
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

@Composable
private fun SettingsCard(title: String, meta: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(meta, fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** 更新卡片:服务器地址 + 检查/下载/安装(局域网,电脑跑 ./gradlew :app:serveUpdates) */
@Composable
private fun UpdateCard(appSettings: AppSettings) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = ServiceLocator.appSettings
    var server by remember(appSettings.updateServer) { mutableStateOf(appSettings.updateServer) }
    var state by remember { mutableStateOf<AppUpdater.State>(AppUpdater.State.Idle) }

    fun fmtTime(ms: Long) =
        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(ms))

    SettingsCard(title = "更新", meta = "UPDATES · LAN") {
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("电脑地址") },
            placeholder = { Text("http://192.168.x.x:8765") },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        MonoText(
            "当前 ${com.milknote.app.BuildConfig.VERSION_NAME} · 构建 ${fmtTime(com.milknote.app.BuildConfig.BUILD_TIME)}",
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(8.dp))

        val statusText = when (val s = state) {
            is AppUpdater.State.Idle -> if (server.isBlank()) "填电脑地址后检查" else "就绪"
            is AppUpdater.State.Checking -> "检查中…"
            is AppUpdater.State.UpToDate -> "已是最新版本"
            is AppUpdater.State.Available -> "发现新版本 ${s.latest.versionName}(${fmtTime(s.latest.buildTime)})"
            is AppUpdater.State.Downloading -> "下载中 ${s.percent}%"
            is AppUpdater.State.ReadyToInstall -> "已下载,点击「安装」"
            is AppUpdater.State.Failed -> "失败:${s.reason}"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoText(statusText, color = if (state is AppUpdater.State.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            // 注意:分支值先捕获到局部 val(onClick lambda 里可变 state 已变化,不可再 cast)
            when (val s = state) {
                is AppUpdater.State.Downloading, is AppUpdater.State.Checking -> {}
                is AppUpdater.State.ReadyToInstall -> {
                    val file = s.file
                    TextButton(onClick = {
                        if (!AppUpdater.canRequestInstall(context)) {
                            context.startActivity(AppUpdater.installPermissionIntent())
                        } else {
                            AppUpdater.launchInstall(context, file)
                        }
                    }) { Text("安装") }
                }
                is AppUpdater.State.Available -> {
                    val latest = s.latest
                    TextButton(onClick = {
                        scope.launch {
                            state = AppUpdater.State.Downloading(0)
                            val f = AppUpdater.download(context, server, latest) { p ->
                                state = AppUpdater.State.Downloading(p)
                            }
                            state = if (f != null) AppUpdater.State.ReadyToInstall(f)
                            else AppUpdater.State.Failed("下载失败(服务开着吗?)")
                        }
                    }) { Text("下载") }
                }
                else -> {
                    TextButton(onClick = {
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
                    }) { Text("检查更新") }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        MonoText("电脑上运行 ./gradlew :app:serveUpdates 启动更新服务", color = MaterialTheme.colorScheme.outline)
    }
}
