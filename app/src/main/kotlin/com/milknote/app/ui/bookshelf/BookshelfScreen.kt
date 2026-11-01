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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.milknote.app.ui.common.CommandKeyCapsule
import com.milknote.app.ui.common.MonoText
import com.milknote.app.ui.common.PenBatteryCapsule
import com.milknote.app.ui.common.accentBrush
import com.milknote.app.ui.common.dashedBorder
import com.milknote.app.ui.common.formatRelative
import com.milknote.app.ui.common.softCard
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
                NotebookCard(
                    meta = nb,
                    pageCount = vm.pageCountOf(nb.id),
                    recent = nb.id == notebooks.firstOrNull()?.id,
                    onOpen = { onOpenNotebook(nb.id) },
                    onDelete = { deleteTarget = nb },
                )
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

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${target.name}」?") },
            text = { Text("笔记本目录(${target.id}.milk)将被整体删除,包括全部页面笔迹。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { vm.delete(target.id); deleteTarget = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
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
        Row(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = onOpenPalette)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text("搜索", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Text("⌘K", fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.combinedClickable(onClick = onOpenSettings, onLongClick = onBatteryLongPress)) {
            PenBatteryCapsule(level = battery)
        }
        Spacer(Modifier.width(14.dp))

        // 设置入口(笔与手势)
        Box(
            Modifier
                .size(34.dp)
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

        // [+ 新建笔记本] 胶囊主按钮(柔和渐变)
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(accentBrush())
                .clickable(onClick = onNew)
                .padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("新建笔记本", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
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
    Column(
        modifier = Modifier
            .softCard()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onOpen, onLongClick = onDelete)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.18f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(cover.copy(alpha = 0.16f), cover.copy(alpha = 0.05f)),
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
                        .padding(8.dp)
                        .background(Color(0xCCFAF9F5), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("最近", fontSize = 10.sp, color = Color(0xFF23221E))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
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
            Box(
                Modifier
                    .background(cover.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(meta.purpose, fontSize = 10.sp, color = cover)
            }
        }
    }
}

/** 虚线新建卡 */
@Composable
private fun NewNotebookCard(onNew: () -> Unit) {
    Column(
        modifier = Modifier
            .height(236.dp)
            .dashedBorder(MaterialTheme.colorScheme.outline, 1.5.dp, cornerRadius = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onNew)
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(10.dp))
        Text("新建笔记本", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        MonoText("NEW .MILK")
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
