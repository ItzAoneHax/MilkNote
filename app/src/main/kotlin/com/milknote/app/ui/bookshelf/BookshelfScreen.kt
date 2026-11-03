package com.milknote.app.ui.bookshelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.ui.common.MonoText
import com.milknote.app.ui.common.PenBatteryCapsule
import com.milknote.app.ui.common.accentBrush
import com.milknote.app.ui.common.dashedBorder
import com.milknote.app.ui.common.formatRelative
import com.milknote.app.ui.common.innerHighlight
import com.milknote.app.ui.common.paintedShadow
import com.milknote.app.ui.common.softCard
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.Mono
import com.milknote.notes.model.NotebookMeta

/** ① 书架:笔记本卡片网格 + 顶栏(搜索⌘K / 笔电 / 设置 / + 新建) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(
    vm: BookshelfViewModel,
    onOpenNotebook: (String) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPalette: () -> Unit = {},
    showNewNotebook: Boolean = false,
    onNewNotebookHandled: () -> Unit = {},
) {
    val notebooks by vm.notebooks.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<NotebookMeta?>(null) }

    // ⌘K「新建笔记本」请求(面板 → 本屏弹窗)
    LaunchedEffect(showNewNotebook) {
        if (showNewNotebook) { showCreate = true; onNewNotebookHandled() }
    }
    val battery by com.milknote.app.di.ServiceLocator.penBattery.level.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)   // 背景先铺满(含状态栏区域)
            .windowInsetsPadding(WindowInsets.safeDrawing),     // 内容再避开 insets
    ) {
        // ---- 顶栏 ----
        BookshelfTopBar(
            count = notebooks.size,
            battery = battery,
            onNew = { showCreate = true },
            onOpenSettings = onOpenSettings,
            onBatteryLongPress = onOpenDiagnostics,
            onOpenPalette = onOpenPalette,
        )

        // ---- 卡片网格 ----
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 250.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 36.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(notebooks, key = { it.id }) { nb ->
                Box(Modifier.animateItem()) {
                    NotebookCard(
                        meta = nb,
                        pageCount = vm.pageCountOf(nb.id),
                        recent = nb.id == notebooks.firstOrNull()?.id,
                        onOpen = { onOpenNotebook(nb.id) },
                        onDelete = { deleteTarget = nb },
                    )
                }
            }
            item(key = "__new__") {
                NewNotebookCard(onNew = { showCreate = true })
            }
        }
    }

    // ---- ② 新建弹窗 ----
    AnimatedVisibility(visible = showCreate, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
        NewNotebookDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, color, paper, purpose, pageSize, orientation ->
                val id = vm.create(name, color, paper, purpose, pageSize, orientation)
                showCreate = false
                onOpenNotebook(id)
            },
        )
    }

    // ---- 删除确认(Milk 弹窗语言:边框+软阴影+高光,替代 M3 AlertDialog;440dp 上限) ----
    deleteTarget?.let { target ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { deleteTarget = null }) {
            Column(
                Modifier
                    .widthIn(max = 440.dp)   // 先限宽再 fill
                    .fillMaxWidth()
                    .softShadow(12.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    .innerHighlight()
                    .paintedShadow(RoundedCornerShape(20.dp))
                    .padding(24.dp),
            ) {
                Text("删除「${target.name}」?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "笔记本目录(${target.id}.milk)将被整体删除,包括全部页面笔迹。此操作不可撤销。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "取消",
                        fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .clip(CircleShape)
                            .softClickable { deleteTarget = null }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "删除",
                        fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(CircleShape)
                            .softClickable { vm.delete(target.id); deleteTarget = null }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfTopBar(
    count: Int,
    battery: Int,
    onNew: () -> Unit,
    onOpenSettings: () -> Unit,
    onBatteryLongPress: () -> Unit,
    onOpenPalette: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("牛奶笔记", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Text(
            "MILKNOTE",
            fontFamily = Mono,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))

        // 搜索 → ⌘K 命令面板(DESIGN-V0 §5.4 统一入口;识别检索 M3)
        SearchCommandPill(onClick = onOpenPalette)
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .combinedClickable(onClick = onOpenSettings, onLongClick = onBatteryLongPress)
        ) {
            PenBatteryCapsule(level = battery)
        }
        Spacer(Modifier.width(14.dp))

        // 设置入口(笔与手势;48dp 命中,图标 19dp 保持)
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Settings, "设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(14.dp))

        // [+ 新建笔记本] 胶囊主按钮(柔和渐变 + 轻投影 + 按压缩放)
        Row(
            modifier = Modifier
                .softShadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(accentBrush())
                .softClickable(onClick = onNew)
                .padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("新建笔记本", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** 搜索→⌘K 胶囊(书架顶栏) */
@Composable
internal fun SearchCommandPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text("搜索", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text("⌘K", fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 笔记本卡片:封面(迷你点阵纸 + 淡彩渐染 + 色点呼应)+ 元信息 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookCard(
    meta: NotebookMeta,
    pageCount: Int,
    recent: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val cover = runCatching { Color(android.graphics.Color.parseColor(meta.coverColor)) }.getOrDefault(Color(0xFF3B5BEC))
    var showMenu by remember { mutableStateOf(false) }
    // 等高框架(A-05):封面固定 aspectRatio + 元信息固定 68dp,新建卡复用同一骨架
    Column(
        modifier = Modifier
            .softCard()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onOpen, onLongClick = onDelete)
            .innerHighlight()
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.18f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(cover.copy(alpha = 0.16f), cover.copy(alpha = 0.05f))
                    )
                ),
        ) {
            // 迷你点阵纸纹(点阵示意)
            MiniDotsPaper(
                modifier = Modifier.fillMaxSize(),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
            )
            // 「最近」徽标
            if (recent) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0xCCFAF9F5), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("最近", fontSize = 10.sp, color = Color(0xFF23221E))
                }
            }
            // 可见管理入口(A-07):删除不再只靠长按;48dp 命中,图标 18dp
            Box(Modifier.align(Alignment.TopEnd)) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clickable { showMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.MoreVert, "笔记本操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                androidx.compose.material3.DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("打开") },
                        onClick = { showMenu = false; onOpen() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(68.dp)) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Text(
                    meta.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(cover, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    MonoText("${pageCount} 页 · ${formatRelative(meta.updatedAt)}")
                    Spacer(Modifier.weight(1f))
                    // A-03:小字用对比达标前景(亮=同色相加深,暗=提亮;原色对比 2.4–4.5 不足)
                    val tagFg = com.milknote.app.ui.theme.coverForeground(
                        cover, com.milknote.app.ui.theme.LocalMilkDark.current,
                    )
                    Box(
                        Modifier
                            .background(cover.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(meta.purpose, fontSize = 10.sp, color = tagFg)
                    }
                }
            }
        }
    }
}

/** 虚线新建卡(与笔记本卡同一等高框架,仅换内容;A-05) */
@Composable
private fun NewNotebookCard(onNew: () -> Unit) {
    Column(
        modifier = Modifier
            .softCard()
            .dashedBorder(MaterialTheme.colorScheme.outline, 1.5.dp, cornerRadius = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .clickable(onClick = onNew)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.18f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(68.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("新建笔记本", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                MonoText("NEW .MILK")
            }
        }
    }
}

/** 迷你点阵纸(封面纹样) */
@Composable
internal fun MiniDotsPaper(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) { drawDots(tint) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDots(tint: Color) {
    val step = 16.dp.toPx()
    var y = step
    while (y < size.height) {
        var x = step
        while (x < size.width) {
            drawCircle(color = tint, radius = 1.1f, center = androidx.compose.ui.geometry.Offset(x, y))
            x += step
        }
        y += step
    }
}
