package com.milknote.app.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.MilkConfirmDialog
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.MilkSwitch
import com.milknote.app.ui.common.MilkToast
import com.milknote.app.ui.common.PaperColorSelector
import com.milknote.app.ui.common.PaperKindSelector
import com.milknote.app.ui.theme.ChromeDark
import com.milknote.app.ui.theme.ChromeLight
import com.milknote.app.ui.theme.InkDark
import com.milknote.app.ui.theme.InkLight
import com.milknote.app.ui.theme.LineDark
import com.milknote.app.ui.theme.LineLight
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.LocalMilkWarning
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.PaperDark
import com.milknote.app.ui.theme.PaperLight
import com.milknote.device.pen.PenAction
import com.milknote.device.pen.PenTrigger
import com.milknote.ink.input.PressureCurve
import com.milknote.ink.model.PressureGamma
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页(第二代 · DESIGN-V2):
 * - ≥680dp:224dp 通栏左导航(底色铺满全高、延伸进系统栏)+ 右侧 ≤640dp 居中列
 * - <680dp:紧凑顶部栏(返回 + 标题 + 分组徽标 + 横向胶囊条)+ 全宽内容列
 * 六个分区:外观 / 书写 / 触控笔 / 手势与快捷键 / 模型与隐私 / 存储与更新。所有开关即时生效并持久化(DataStore)。
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

    var page by rememberSaveable(initialPage) { mutableStateOf(initialPage) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchNoMatch by remember { mutableStateOf(false) }
    var toastText by remember { mutableStateOf<String?>(null) }
    var toastNonce by remember { mutableStateOf(0) }
    var toastVisible by remember { mutableStateOf(false) }
    val reducedMotion = LocalMilkMotion.current

    // 窄屏内容列的滚动位置:顶栏底边 hairline 只在离开顶部后浮现(宽度/断点切换时复用同一状态)
    val narrowScrollState = rememberScrollState()
    val narrowScrolled by remember { derivedStateOf { narrowScrollState.value > 0 } }

    // 以递增序号驱动:同一句提示连续触发两次也会重新弹出
    LaunchedEffect(toastNonce) {
        if (toastNonce > 0) {
            toastVisible = true
            delay(1800)
            toastVisible = false
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) ServiceLocator.penBattery.refresh()
    }

    val onSearch: (String) -> Unit = onSearch@{ q ->
        searchQuery = q
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) {
            searchNoMatch = false
            return@onSearch
        }
        val target = when {
            needle.contains("深色") || needle.contains("浅色") || needle.contains("主题") || needle.contains("纸纹") ||
                needle.contains("纸色") || needle.contains("外观") || needle.contains("书架") || needle.contains("列表") -> "appearance"
            needle.contains("压感") || needle.contains("出墨") || needle.contains("前置") || needle.contains("书写") ||
                needle.contains("右滑") || needle.contains("新建页") -> "writing"
            needle.contains("笔") || needle.contains("按键") || needle.contains("蓝牙") || needle.contains("电量") || needle.contains("接管") -> "pen"
            needle.contains("快捷") || needle.contains("键盘") || needle.contains("手势") -> "shortcuts"
            needle.contains("模型") || needle.contains("ai") || needle.contains("key") || needle.contains("provider") ||
                needle.contains("视觉") || needle.contains("隐私") ||
                needle.contains("总结") || needle.contains("摘要") || needle.contains("会话") ||
                needle.contains("协议") || needle.contains("opencode") || needle.contains("responses") ||
                needle.contains("anthropic") ||
                needle.contains("附件") || needle.contains("引用") || needle.contains("@") -> "agent"
            needle.contains("更新") || needle.contains("回收站") || needle.contains("存储") -> "storage"
            else -> null
        }
        if (target != null) page = target
        searchNoMatch = target == null
    }
    val clearSearch = { searchQuery = ""; searchNoMatch = false }
    val showToast: (String) -> Unit = { msg -> toastText = msg; toastNonce++ }

    // 页面内容:分区组内紧凑 8dp、组间 22dp(间距必须落在 AnimatedContent 内部的 Column 上)
    val pageBody: @Composable () -> Unit = {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                MilkMotion.pageEnter(reducedMotion).togetherWith(MilkMotion.pageExit(reducedMotion))
            },
            label = "settingsPage",
        ) { p ->
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                when (p) {
                    "appearance" -> AppearancePage(
                        appSettings = appSettings,
                        setThemeMode = { scope.launch { settings.setThemeMode(it) } },
                        setDefaultPaper = { scope.launch { settings.setDefaultPaper(it) } },
                        setDefaultPaperColor = { scope.launch { settings.setDefaultPaperColor(it) } },
                        setBookshelfList = { scope.launch { settings.setBookshelfListView(it) } },
                    )
                    "writing" -> WritingPage(
                        appSettings = appSettings,
                        setPressure = { scope.launch { settings.setPressureGamma(it) } },
                        setFrontBuffer = { scope.launch { settings.setFrontBufferInk(it) } },
                        setSwipeNewPage = { scope.launch { settings.setSwipeNewPage(it) } },
                    )
                    "pen" -> PenPage(
                        mapping = mapping,
                        caps = caps,
                        battery = battery,
                        batterySource = batterySource,
                        permLauncher = permLauncher,
                    )
                    "shortcuts" -> ShortcutsPage()
                    "agent" -> {
                        SettingsPageHeader("模型与隐私", "配置 AI 教师使用的模型服务、会话总结，以及内容发送范围")
                        SettingsGroup {
                            SettingsSectionHeader("模型服务", "预设或自定义端点；Key 只写不读")
                            AgentSettingsSection()
                        }
                        AgentBehaviorSettingsSection()
                        SettingsGroup {
                            SettingsSectionHeader("隐私")
                            SettingsCard {
                                SettingsRow(
                                    title = "API Key 存储",
                                    subtitle = "Android Keystore 加密",
                                    trailing = {
                                        Text(
                                            "仅本机",
                                            style = MilkText.monoBody,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                )
                                Hairline()
                                SettingsRow(
                                    title = "上下文边界",
                                    subtitle = "只上传显式框选的切片或手动附加的页面，不扫描整本笔记",
                                )
                            }
                        }
                    }
                    "storage" -> StoragePage(appSettings = appSettings, onToast = showToast)
                }
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val wide = maxWidth >= 680.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                SettingsNavRail(
                    selected = page,
                    onSelect = { page = it; clearSearch() },
                    versionLine = "v${com.milknote.app.BuildConfig.VERSION_NAME} · ${fmtTime(com.milknote.app.BuildConfig.BUILD_TIME)}",
                    onBack = onBack,
                    searchQuery = searchQuery,
                    onSearchChange = onSearch,
                    searchNoMatch = searchNoMatch,
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // 侧栏自己吃 Start 侧 safe area,内容只吃 Vertical + End,避免双份内边距
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End),
                        )
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 28.dp),
                    ) { pageBody() }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                SettingsCompactBar(
                    selected = page,
                    onSelect = { page = it; clearSearch() },
                    onBack = onBack,
                    searchQuery = searchQuery,
                    onSearchChange = onSearch,
                    searchNoMatch = searchNoMatch,
                    scrolled = narrowScrolled,
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        )
                        .verticalScroll(narrowScrollState),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MilkSpace.xl, vertical = MilkSpace.xl),
                    ) { pageBody() }
                }
            }
        }

        MilkToast(
            visible = toastVisible,
            text = toastText.orEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(bottom = MilkSpace.xxl),
        )
    }
}

// ==================== 外观 ====================

@Composable
private fun AppearancePage(
    appSettings: AppSettings,
    setThemeMode: (ThemeMode) -> Unit,
    setDefaultPaper: (String) -> Unit,
    setDefaultPaperColor: (String) -> Unit,
    setBookshelfList: (Boolean) -> Unit,
) {
    SettingsPageHeader("外观", "纸面、点阵与界面明暗；更改即时生效")
    SettingsGroup {
        SettingsSectionHeader("主题模式", "导出与缩略图恒为浅色纸")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MilkSpace.md)) {
            ThemeMode.entries.forEach { mode ->
                ThemeModeCard(
                    mode = mode,
                    selected = appSettings.themeMode == mode,
                    onClick = { setThemeMode(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    SettingsGroup {
        SettingsSectionHeader("默认纸张")
        SettingsCard {
            Column(Modifier.padding(horizontal = MilkSpace.lg, vertical = MilkSpace.md)) {
                Text("默认纸纹", style = MilkText.rowTitle, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(10.dp))
                PaperKindSelector(selected = appSettings.defaultPaper, onSelect = setDefaultPaper)
            }
            Hairline()
            Column(Modifier.padding(horizontal = MilkSpace.lg, vertical = MilkSpace.md)) {
                Text("默认纸色", style = MilkText.rowTitle, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(10.dp))
                PaperColorSelector(selected = appSettings.defaultPaperColor, onSelect = setDefaultPaperColor)
            }
        }
    }
    SettingsGroup {
        SettingsSectionHeader("界面")
        SettingsCard {
            SettingsRow(
                title = "书架默认视图",
                trailing = {
                    MilkSegmented(
                        options = listOf(false, true),
                        selected = appSettings.bookshelfListView,
                        label = { if (it) "列表" else "网格" },
                        onSelect = setBookshelfList,
                        modifier = Modifier.width(168.dp),
                        compact = true,
                    )
                },
            )
        }
    }
}

/** 主题预览卡:缩小的「纸面 + chrome」小样,选中 = 蓝描边 + 蓝字;跟随系统 = 左亮右暗对半 */
@Composable
private fun ThemeModeCard(mode: ThemeMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalMilkMotion.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.975f else 1f,
        animationSpec = tween(MilkMotion.press, easing = MilkMotion.EaseOut),
        label = "themeCardPress",
    )
    val borderColor by animateColorAsState(
        if (selected) cs.primary else cs.outline,
        MilkMotion.colorSpec(),
        label = "themeCardBorder",
    )
    val borderWidth by animateDpAsState(
        if (selected) 1.5.dp else 1.dp,
        MilkMotion.colorSpec(),
        label = "themeCardBorderWidth",
    )
    Column(
        modifier
            // 按压瞬态:只缩 2.5%,100ms 强 ease-out;减弱动效时完全不缩
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(MilkRadius.lg))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.RadioButton,
            ) {
                // 同帧触感:只有真的换到另一个主题才震
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .background(cs.surface)
            .border(borderWidth, borderColor, RoundedCornerShape(MilkRadius.lg))
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .clip(RoundedCornerShape(MilkRadius.sm)),
        ) {
            when (mode) {
                ThemeMode.LIGHT -> ThemePreviewHalf(PaperLight, ChromeLight, LineLight, InkLight, Modifier.fillMaxSize())
                ThemeMode.DARK -> ThemePreviewHalf(PaperDark, ChromeDark, LineDark, InkDark, Modifier.fillMaxSize())
                ThemeMode.FOLLOW -> Row(Modifier.fillMaxSize()) {
                    ThemePreviewHalf(PaperLight, ChromeLight, LineLight, InkLight, Modifier.weight(1f).fillMaxHeight())
                    ThemePreviewHalf(PaperDark, ChromeDark, LineDark, InkDark, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (selected) cs.primary else Color.Transparent)
                    .border(1.5.dp, borderColor, CircleShape),
            )
            Spacer(Modifier.width(MilkSpace.sm))
            Text(
                mode.label,
                style = MilkText.label.copy(
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                ),
                color = if (selected) cs.primary else cs.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** 主题小样:chrome 条 + 纸面 + 两行示意文字 */
@Composable
private fun ThemePreviewHalf(paper: Color, chrome: Color, line: Color, ink: Color, modifier: Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().weight(0.28f).background(chrome).padding(horizontal = 8.dp, vertical = 5.dp)) {
            Box(
                Modifier
                    .width(28.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(MilkRadius.xs / 2))
                    .background(ink.copy(alpha = 0.55f)),
            )
        }
        Column(Modifier.fillMaxWidth().weight(0.72f).background(paper).padding(horizontal = 8.dp, vertical = 7.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(MilkRadius.xs / 2))
                    .background(ink.copy(alpha = 0.7f)),
            )
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.95f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(MilkRadius.xs / 2))
                    .background(line),
            )
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(MilkRadius.xs / 2))
                    .background(line),
            )
        }
    }
}

// ==================== 书写 ====================

@Composable
private fun WritingPage(
    appSettings: AppSettings,
    setPressure: (PressureGamma) -> Unit,
    setFrontBuffer: (Boolean) -> Unit,
    setSwipeNewPage: (Boolean) -> Unit,
) {
    SettingsPageHeader("书写", "压感与出墨路径；更改即时生效")
    SettingsGroup {
        SettingsSectionHeader("压感曲线", "轻触 0.3mm → 重按 2.2mm；示意线左轻右重")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PressureGamma.entries.forEach { gamma ->
                PressureCurveCard(
                    gamma = gamma,
                    selected = appSettings.pressureGamma == gamma,
                    onClick = { setPressure(gamma) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    SettingsGroup {
        SettingsSectionHeader("出墨与翻页")
        SettingsCard {
            SettingsRow(
                title = "前置缓冲出墨",
                subtitle = "实验性 · GL 前置缓冲，<10ms 出墨；关闭 = Canvas 路径",
                onClick = { setFrontBuffer(!appSettings.frontBufferInk) },
                trailing = { MilkSwitch(checked = appSettings.frontBufferInk, onChange = setFrontBuffer) },
            )
            Hairline()
            SettingsRow(
                title = "末页右滑自动新建页",
                subtitle = "滑过最后一页继续右滑时追加空白页；关闭后仅按钮/菜单新建",
                onClick = { setSwipeNewPage(!appSettings.swipeNewPage) },
                trailing = { MilkSwitch(checked = appSettings.swipeNewPage, onChange = setSwipeNewPage) },
            )
        }
    }
}

/** 压感曲线卡:左轻右重的示意笔迹(走真实 PressureCurve),选中 = 蓝描边 */
@Composable
private fun PressureCurveCard(gamma: PressureGamma, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalMilkMotion.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.975f else 1f,
        animationSpec = tween(MilkMotion.press, easing = MilkMotion.EaseOut),
        label = "pressureCardPress",
    )
    val borderColor by animateColorAsState(
        if (selected) cs.primary else cs.outline,
        MilkMotion.colorSpec(),
        label = "pressureCardBorder",
    )
    val borderWidth by animateDpAsState(
        if (selected) 1.5.dp else 1.dp,
        MilkMotion.colorSpec(),
        label = "pressureCardBorderWidth",
    )
    Column(
        modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(MilkRadius.lg))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.RadioButton,
            ) {
                // 同帧触感:只有真的换到另一条曲线才震
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .background(cs.surface)
            .border(borderWidth, borderColor, RoundedCornerShape(MilkRadius.lg))
            .padding(horizontal = MilkSpace.md, vertical = 10.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 2.dp)) {
            val ink = if (selected) cs.primary else cs.onSurface
            val steps = 48
            val maxR = size.height * 0.32f
            var prev: Offset? = null
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = 4f + (size.width - 8f) * t
                val y = size.height / 2f
                val r = PressureCurve.MIN_RATIO + (PressureCurve.MAX_RATIO - PressureCurve.MIN_RATIO) * PressureCurve.curve(t, gamma)
                val radius = maxR * r / PressureCurve.MAX_RATIO
                val p = Offset(x, y)
                prev?.let { drawLine(ink, it, p, strokeWidth = radius * 2f, cap = StrokeCap.Round) }
                prev = p
            }
        }
        Spacer(Modifier.height(MilkSpace.sm))
        Text(
            gamma.label,
            style = MilkText.label.copy(
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            color = if (selected) cs.primary else cs.onSurface,
            maxLines = 1,
        )
        Text("γ ${gamma.gamma}", style = MilkText.monoMeta, color = cs.onSurfaceVariant)
    }
}

// ==================== 触控笔 ====================

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

    SettingsPageHeader("触控笔", "小米焦点触控笔的按键映射、长按接管与电量")
    SettingsGroup {
        SettingsSectionHeader("长按接管", "接管后书写页内长按侧键不触发系统行为（无提示）")
        SettingsCard {
            if (takeoverGranted) {
                StatusLine("长按接管已启用，长按侧键完全交由下方映射", tone = StatusTone.OK)
            } else {
                StatusLine("未激活：长按侧键仍触发系统截图 / 小米创作。一次性激活，连接电脑执行", tone = StatusTone.WARN)
                StatusLine(
                    "adb shell pm grant com.milknote.app android.permission.WRITE_SECURE_SETTINGS",
                    tone = StatusTone.WARN, mono = true,
                )
            }
        }
    }
    SettingsGroup {
        SettingsSectionHeader("按键映射", "MilkNote 按键仅在书写时生效")
        SettingsCard {
            if (caps == null) {
                StatusLine("未检测到笔事件——按键探测后将按实际能力过滤", tone = StatusTone.NEUTRAL)
            }
            supportedTriggers(caps?.stylusButtonCount).forEachIndexed { idx, trigger ->
                if (idx > 0 || caps == null) Hairline()
                SelectRow(
                    title = trigger.label,
                    items = PenAction.entries,
                    selected = mapping[trigger] ?: PenAction.NONE,
                    label = { it.label },
                    onPick = { router.rebind(trigger, it) },
                )
            }
            Hairline()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MilkSpace.md),
                horizontalArrangement = Arrangement.End,
            ) {
                TextAction("恢复默认映射", onClick = { router.resetDefault() })
            }
        }
    }
    SettingsGroup {
        SettingsSectionHeader("笔电量")
        SettingsCard {
            SettingsRow(
                title = "电量",
                subtitle = batterySource?.let { "来源 $it · Battery 0x180F 订阅，磁吸吸附自动更新" }
                    ?: "未识别到笔 · Battery 0x180F 订阅，磁吸吸附自动更新",
                trailing = {
                    Text(
                        if (battery < 0) "--%" else "$battery%",
                        style = MilkText.pageTitle,
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
}

/** Trigger 列表按实际能力过滤 */
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

// ==================== 手势与快捷键 ====================

@Composable
private fun ShortcutsPage() {
    SettingsPageHeader("手势与快捷键", "外接键盘快捷键；触控笔按键映射见「触控笔」")
    SettingsGroup {
        SettingsSectionHeader("外接键盘")
        SettingsCard {
            ShortcutRow("搜索与命令面板", "⌘ K")
            Hairline()
            ShortcutRow("右侧 AI / 伴随笔记面板", "⌥ A")
            Hairline()
            ShortcutRow("框选提问", "笔键长按 / 笔匣框选")
        }
    }
    SettingsGroup {
        SettingsSectionHeader("触控手势")
        SettingsCard {
            SettingsRow(title = "手指平移与缩放", subtitle = "书写时手指仅用于平移与双指缩放，不落墨")
            Hairline()
            SettingsRow(title = "缘滑翻页", subtitle = "在页面左右边缘滑动翻页；末页继续右滑可在「书写」中关闭自动新建")
        }
    }
}

// ==================== 存储与更新 ====================

@Composable
private fun StoragePage(appSettings: AppSettings, onToast: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = ServiceLocator.appSettings
    val updater = ServiceLocator.appUpdater
    val scope = rememberCoroutineScope()
    val state by updater.state.collectAsState()
    var server by remember(appSettings.updateServer) { mutableStateOf(appSettings.updateServer) }
    var trashedCount by remember { mutableStateOf(ServiceLocator.repository.listTrashedNotebooks().size) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }

    SettingsPageHeader("存储与更新", "本地 .milk 目录存储、回收站清理与局域网更新")
    SettingsGroup {
        SettingsSectionHeader("本地数据")
        SettingsCard {
            SettingsRow(
                title = "存储目录",
                subtitle = "本地零账号优先 · notebooks/*.milk",
                trailing = {
                    Text(
                        "本地",
                        style = MilkText.monoBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            Hairline()
            SettingsRow(
                title = "回收站（$trashedCount 本）",
                subtitle = "书架删除的笔记本会先移入回收站，可随时恢复或永久清空",
                trailing = {
                    TextAction(
                        label = "清空回收站",
                        danger = true,
                        enabled = trashedCount > 0,
                        onClick = { confirmEmptyTrash = true },
                    )
                },
            )
            Hairline()
            // 三个互斥档位:独占一行排布,窄屏也不挤压标题列
            SettingsRow(
                title = "回收站保留时长",
                subtitle = "超过天数的笔记本在下次打开书架时自动清理；永久保留则不自动清理",
            )
            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                MilkSegmented(
                    options = listOf(7, 30, 0),
                    selected = appSettings.trashDays,
                    label = { days ->
                        when (days) {
                            7 -> "7 天"
                            30 -> "30 天"
                            else -> "永久"
                        }
                    },
                    onSelect = { days -> scope.launch { settings.setTrashDays(days) } },
                    modifier = Modifier.width(204.dp),
                )
            }
        }
    }

    SettingsGroup {
        SettingsSectionHeader("更新服务器")
        SettingsCard {
            StatusLine(
                "当前 v${com.milknote.app.BuildConfig.VERSION_NAME} · 构建 ${fmtTime(com.milknote.app.BuildConfig.BUILD_TIME)}",
                tone = StatusTone.NEUTRAL, mono = true,
            )
            Hairline()
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
                is AppUpdater.State.Available -> "发现新版本 ${s.latest.versionName}（${fmtTime(s.latest.buildTime)}）"
                is AppUpdater.State.Downloading -> "下载中 ${s.percent}%"
                is AppUpdater.State.ReadyToInstall -> "已下载，点击「安装」"
                is AppUpdater.State.Failed -> "失败：${s.reason}"
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MilkSpace.lg, vertical = MilkSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    statusText,
                    style = MilkText.caption,
                    color = when (state) {
                        is AppUpdater.State.Failed -> MaterialTheme.colorScheme.error
                        is AppUpdater.State.UpToDate -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
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
                        TextAction("下载", primary = true, onClick = { updater.download(server, latest) })
                    }
                    else -> {
                        TextAction("检查更新", primary = true, enabled = server.isNotBlank(), onClick = {
                            ServiceLocator.appScope.launch { settings.setUpdateServer(AppUpdater.normalizeServer(server)) }
                            updater.check(server, com.milknote.app.BuildConfig.VERSION_NAME, com.milknote.app.BuildConfig.BUILD_TIME)
                        })
                    }
                }
            }
        }
        FooterHint("电脑上运行 ./gradlew :app:serveUpdates 启动更新服务", mono = true)
    }

    if (confirmEmptyTrash) {
        MilkConfirmDialog(
            title = "清空回收站？",
            body = "回收站内的 $trashedCount 本笔记本将被永久删除，无法恢复。",
            confirmLabel = "清空",
            onDismiss = { confirmEmptyTrash = false },
            onConfirm = {
                confirmEmptyTrash = false
                scope.launch(Dispatchers.IO) {
                    ServiceLocator.repository.emptyTrash()
                    val remaining = ServiceLocator.repository.listTrashedNotebooks().size
                    withContext(Dispatchers.Main) {
                        trashedCount = remaining
                        onToast("回收站已清空")
                    }
                }
            },
        )
    }
}

private fun fmtTime(ms: Long) =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(ms))
