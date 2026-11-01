package com.milknote.app.ui.writer

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
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.Image as ImageIcon
import androidx.compose.material.icons.outlined.PictureAsPdf
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.CommandKeyCapsule
import com.milknote.app.ui.common.MonoText
import com.milknote.app.ui.common.PenBatteryCapsule
import com.milknote.app.ui.theme.InkPalette
import com.milknote.app.ui.theme.Mono
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
) {
    val state by vm.state.collectAsState()
    val thumbs by vm.thumbs.collectAsState()
    val battery by ServiceLocator.penBattery.level.collectAsState()
    val batterySource by ServiceLocator.penBattery.sourceName.collectAsState()
    val penConnected = batterySource != null || battery >= 0   // GATT 已连笔 → 手指不写
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
    LaunchedEffect(toast) {
        if (toast != null) { kotlinx.coroutines.delay(2000); toast = null }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)   // 背景先铺满(含状态栏区域)
            .windowInsetsPadding(WindowInsets.safeDrawing),     // 内容再避开 insets
    ) {
        WriterTopBar(
            state = state,
            vm = vm,
            battery = battery,
            onBack = onBack,
            onOpenDiagnostics = onOpenDiagnostics,
            onExportPng = { vm.exportPng { toast = it } },
            onExportPdf = { vm.exportPdf { toast = it } },
            onAgent = { toast = "Agent 框选:M2 交付" },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            PageSidebar(
                state = state,
                thumbs = thumbs,
                onBack = onBack,
                onSwitch = vm::switchPage,
                onNewPage = vm::newPage,
            )
            // ---- 纸面画布 ----
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            ) {
                PaperCanvas(vm, state, dark, penConnected)
                // 选中操作条:套索圈选后出现(拖动 = 移动笔迹,进撤销栈)
                if (state.selectionCount > 0) {
                    Row(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 14.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xF023221E))
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "已选 ${state.selectionCount} 笔 · 拖动移动",
                            color = Color(0xFFE8E6E0),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(Color(0x33D2483C))
                                .clickable(onClick = vm::deleteSelection)
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text("删除", color = Color(0xFFE06558), fontSize = 12.sp)
                        }
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .clickable(onClick = vm::clearSelection)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text("✕", color = Color(0xFF9B978D), fontSize = 13.sp)
                        }
                    }
                }
                toast?.let {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xE623221E))
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                    ) {
                        Text(it, color = Color(0xFFFAF9F5), fontSize = 13.sp)
                    }
                }
            }
        }

        StatusBar(state)
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
    onExportPng: () -> Unit,
    onExportPdf: () -> Unit,
    onAgent: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)   // chrome 色带(§5.1):与纸面明确分界
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

        // 撤销 / 重做(禁用态也要可辨,不用 outline——浅色下几乎隐形)
        val disabledTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        IconToggleButton(
            icon = { Icon(Icons.AutoMirrored.Outlined.Undo, null, tint = if (state.canUndo) MaterialTheme.colorScheme.onBackground else disabledTint, modifier = Modifier.size(19.dp)) },
            enabled = state.canUndo, onClick = vm::undo,
        )
        Spacer(Modifier.width(14.dp))
        IconToggleButton(
            icon = { Icon(Icons.AutoMirrored.Outlined.Redo, null, tint = if (state.canRedo) MaterialTheme.colorScheme.onBackground else disabledTint, modifier = Modifier.size(19.dp)) },
            enabled = state.canRedo, onClick = vm::redo,
        )

        Spacer(Modifier.width(18.dp))
        DividerDot()

        // 工具组:钢笔/圆珠/荧光/橡皮/套索 —— 工具与模式(套索/选中)互斥高亮,
        // 同一时刻只亮一个,避免"钢笔+套索"等多状态并存
        Spacer(Modifier.width(18.dp))
        val modeActive = state.lassoArmed || state.selectionCount > 0
        ToolIcon(Icons.Outlined.Draw, "钢笔", !modeActive && state.tool.tool == InkTool.PEN) { vm.setTool(InkTool.PEN) }
        Spacer(Modifier.width(10.dp))
        ToolIcon(Icons.Outlined.Brush, "圆珠", !modeActive && state.tool.tool == InkTool.BALL) { vm.setTool(InkTool.BALL) }
        Spacer(Modifier.width(10.dp))
        ToolIcon(Icons.Outlined.Highlight, "荧光", !modeActive && state.tool.tool == InkTool.HIGHLIGHTER) { vm.setTool(InkTool.HIGHLIGHTER) }
        Spacer(Modifier.width(10.dp))
        ToolIcon(Icons.Outlined.CleaningServices, "橡皮", !modeActive && state.tool.tool == InkTool.ERASER) { vm.setTool(InkTool.ERASER) }
        Spacer(Modifier.width(10.dp))
        ToolIcon(Icons.Outlined.Gesture, "套索", state.lassoArmed) { vm.toggleLasso() }

        Spacer(Modifier.width(18.dp))
        DividerDot()

        // 墨色(荧光黄=荧光笔)
        Spacer(Modifier.width(18.dp))
        InkPalette.forEach { c ->
            val active = state.tool.color == c.toArgbLong()
            Box(
                Modifier
                    .size(if (active) 19.dp else 15.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (active) 2.dp else 1.dp,
                        color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { vm.setColor(c.toArgbLong()) },
            )
            Spacer(Modifier.width(7.dp))
        }

        // 粗细
        Spacer(Modifier.width(6.dp))
        listOf(0.4f, 0.7f, 1.2f).forEach { mm ->
            val active = (state.tool.baseWidthMm - mm).let { it > -0.05f && it < 0.05f }
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { vm.setWidth(mm) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = if (mm > 0.9f) 14.dp else if (mm > 0.5f) 10.dp else 7.dp, height = (mm * 4).dp.coerceAtLeast(2.dp))
                        .background(if (active) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground, CircleShape),
                )
            }
            Spacer(Modifier.width(7.dp))
        }

        Spacer(Modifier.weight(1f))

        // 导出
        IconToggleButton(icon = { Icon(Icons.Outlined.ImageIcon, "导出PNG", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }, enabled = true, onClick = onExportPng)
        Spacer(Modifier.width(12.dp))
        IconToggleButton(icon = { Icon(Icons.Outlined.PictureAsPdf, "导出PDF", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }, enabled = true, onClick = onExportPdf)

        Spacer(Modifier.width(14.dp))
        Box(Modifier.combinedClickable(onClick = {}, onLongClick = onOpenDiagnostics)) {
            PenBatteryCapsule(level = battery)
        }
        Spacer(Modifier.width(14.dp))
        CommandKeyCapsule(onClick = onAgent)
        Spacer(Modifier.width(14.dp))

        // Agent(M2 接框选;蓝色纪律:仅 Agent 与激活态用强调色)
        Box(
            Modifier
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color(0xFF3B5BEC), Color(0xFF5C77F2)),
                    ),
                )
                .clickable(onClick = onAgent)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Agent", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

@Composable
private fun DividerDot() {
    Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconToggleButton(icon: @Composable () -> Unit, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = {}, onDoubleClick = {}),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun ToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .border(
                width = if (active) 1.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, label,
            modifier = Modifier.size(18.dp),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
    val (pwMm, hMm) = com.milknote.notes.model.PAGE_SIZES_MM[nb?.pageSize] ?: com.milknote.notes.model.PAGE_SIZES_MM.getValue("A4")
    val pageAspect = if (nb?.orientation == "landscape") hMm / pwMm else pwMm / hMm
    Column(
        Modifier
            .width(148.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
                        .border(
                            width = if (active) 1.5.dp else 1.dp,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp),
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
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
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
private fun PaperCanvas(vm: WriterViewModel, state: WriterUiState, dark: Boolean, penConnected: Boolean) {
    AndroidView(
        factory = { ctx ->
            InkSurfaceView(ctx).apply {
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

/** 底部状态条(③:笔连接 + 手势提示 + 页码;按模式切换文案) */
@Composable
private fun StatusBar(state: WriterUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)   // chrome 色带:与画布明确分界
            .padding(horizontal = 20.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("笔 就绪", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        val hint = when {
            state.selectionCount > 0 -> "套索:拖动移动 · ✕ 退出"
            state.lassoArmed -> "套索:圈选笔迹(圈住即选中)"
            else -> "双击笔键=笔⇄橡皮 · 长按=框选(M2) · 手指=导航"
        }
        Text(hint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
        Spacer(Modifier.weight(1f))
        if (state.frontBufferEnabled) {
            MonoText("front-buffer", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            Spacer(Modifier.width(10.dp))
        }
        MonoText("${state.currentPageId} / ${state.pages.size} 页", color = MaterialTheme.colorScheme.onSurface)
    }
}
