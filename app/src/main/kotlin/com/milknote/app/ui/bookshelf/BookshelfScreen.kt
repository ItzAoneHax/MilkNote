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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
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
import com.milknote.app.ui.common.MilkActionPanel
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkKeyCap
import com.milknote.app.ui.common.MilkPanelAction
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
    onOpenNotebookWithImage: (String, android.net.Uri) -> Unit = { id, _ -> onOpenNotebook(id) },
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPalette: () -> Unit = {},
    onOpenCards: () -> Unit = {},
    showNewNotebook: Boolean = false,
    onNewNotebookHandled: () -> Unit = {},
) {
    val notebooks by vm.notebooks.collectAsState()
    val sortMode by vm.sortMode.collectAsState()
    val importing by vm.importing.collectAsState()
    val covers by vm.covers.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var showCreateActions by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var manageTarget by remember { mutableStateOf<NotebookMeta?>(null) }
    var deleteTarget by remember { mutableStateOf<NotebookMeta?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    fun openImageNotebook(uri: android.net.Uri, prefix: String) {
        val id = vm.create(prefix, "#3B5BEC", "blank", "阅读摘录")
        onOpenNotebookWithImage(id, uri)
    }
    val imageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) openImageNotebook(uri, "图片笔记") }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { ok -> cameraUri?.let { if (ok) openImageNotebook(it, "拍照笔记") } }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.importDocument(uri) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is BookshelfEvent.Imported -> onOpenNotebook(event.notebookId)
                is BookshelfEvent.Message -> android.widget.Toast.makeText(context, event.text, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // 「最近」徽标跟随真实的最近更新时间,不随排序方式改变含义
    val recentId = remember(notebooks) {
        notebooks.maxByOrNull { it.updatedAt }?.id
    }

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
            onNew = { showCreateActions = true },
            onSort = { showSort = true },
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
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(notebooks, key = { it.id }) { nb ->
                Box(Modifier.animateItem()) {
                    NotebookCard(
                        meta = nb,
                        pageCount = vm.pageCountOf(nb.id),
                        recent = nb.id == recentId,
                        coverThumb = covers[nb.id],
                        onOpen = { onOpenNotebook(nb.id) },
                        onManage = { manageTarget = nb },
                    )
                }
            }
            item(key = "__new__") {
                NewNotebookCard(onNew = { showCreateActions = true })
            }
        }
        com.milknote.app.ui.common.LibraryBottomNav(
            selected = "笔记", onNotes = {}, onCards = onOpenCards,
        )
    }

    if (importing) {
        MilkCenterPanel(title = "正在导入", onDismiss = {}, maxWidth = 420.dp, closable = false) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("正在校验文件并生成新笔记本…")
            }
        }
    }

    if (showCreateActions) {
        MilkActionPanel(
            title = "创建",
            subtitle = "从本地内容开始，不连接外部网盘",
            actions = listOf(
                MilkPanelAction("笔记本", icon = Icons.Outlined.Add, onClick = {
                    showCreateActions = false; showCreate = true
                }),
                MilkPanelAction("导入 PDF / MilkNote 备份", icon = Icons.Outlined.FileOpen, onClick = {
                    showCreateActions = false
                    importLauncher.launch(arrayOf("application/pdf", "application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"))
                }),
                MilkPanelAction("图片", icon = Icons.Outlined.Image, onClick = {
                    showCreateActions = false; imageLauncher.launch("image/*")
                }),
                MilkPanelAction("拍照", icon = Icons.Outlined.CameraAlt, onClick = {
                    showCreateActions = false
                    val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = java.io.File(dir, "capture-${System.currentTimeMillis()}.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file,
                    )
                    cameraUri = uri
                    cameraLauncher.launch(uri)
                }),
            ),
            onDismiss = { showCreateActions = false },
        )
    }

    // ---- ② 新建弹窗 ----
    AnimatedVisibility(visible = showCreate, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
        NewNotebookDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, color, paper, purpose, pageSize, orientation, paperColor ->
                val id = vm.create(name, color, paper, purpose, pageSize, orientation, paperColor)
                showCreate = false
                onOpenNotebook(id)
            },
        )
    }

    // ---- 排序(P057):中央面板,当前项勾选;选择立即生效并保持 ----
    if (showSort) {
        MilkActionPanel(
            title = "排序方式",
            subtitle = "名称 / 修改时间 / 上次打开 / 标签，选择后立即生效。",
            actions = buildList {
                BookshelfSortMode.entries.forEach { mode ->
                    val selected = mode == sortMode
                    add(
                        MilkPanelAction(
                            label = mode.label,
                            icon = if (selected) Icons.Outlined.Check else null,
                            trailingText = if (selected) "当前" else null,
                            onClick = {
                                vm.setSortMode(mode)
                                showSort = false
                            },
                        )
                    )
                }
            },
            onDismiss = { showSort = false },
        )
    }

    manageTarget?.let { target ->
        NotebookManagePanel(
            meta = target,
            folders = vm.folders(),
            onDismiss = { manageTarget = null },
            onSave = { updated -> vm.updateNotebook(updated); manageTarget = null },
            onDelete = { manageTarget = null; deleteTarget = target },
        )
    }

    // ---- 删除确认(统一危险确认弹窗:图标 + 标题 + 说明 + 红色确认键) ----
    deleteTarget?.let { target ->
        com.milknote.app.ui.common.MilkConfirmDialog(
            title = "删除「${target.name}」？",
            body = "笔记本目录（${target.id}.milk）将被整体删除，包括全部页面笔迹。此操作不可撤销。",
            confirmLabel = "删除",
            onDismiss = { deleteTarget = null },
            onConfirm = { vm.delete(target.id); deleteTarget = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfTopBar(
    count: Int,
    battery: Int,
    onNew: () -> Unit,
    onSort: () -> Unit,
    onOpenSettings: () -> Unit,
    onBatteryLongPress: () -> Unit,
    onOpenPalette: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("牛奶笔记", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                // 计数与标题同基线:mono 小胶囊,不再夹在标题与副标题之间
                Text(
                    "$count 本",
                    style = com.milknote.app.ui.theme.MonoMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "MILKNOTE",
                fontFamily = Mono,
                fontSize = 10.sp,
                letterSpacing = 2.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))

        // 搜索 → ⌘K 命令面板(DESIGN-V0 §5.4 统一入口;识别检索 M3)
        SearchCommandPill(onClick = onOpenPalette)
        Spacer(Modifier.width(6.dp))

        // 排序(P057):48dp 命中,图标 20dp;点击打开中央排序面板
        MilkIconButton(
            imageVector = Icons.AutoMirrored.Outlined.Sort,
            contentDescription = "排序",
            onClick = onSort,
        )
        Spacer(Modifier.width(4.dp))

        Box(
            Modifier
                .clip(CircleShape)
                .combinedClickable(onClick = onOpenSettings, onLongClick = onBatteryLongPress),
        ) {
            PenBatteryCapsule(level = battery)
        }
        Spacer(Modifier.width(4.dp))

        // 设置入口(笔与手势;48dp 命中)
        MilkIconButton(
            imageVector = Icons.Outlined.Settings,
            contentDescription = "设置",
            onClick = onOpenSettings,
        )
        Spacer(Modifier.width(12.dp))

        // [+ 新建笔记本] 胶囊主按钮
        MilkButton(
            label = "新建笔记本",
            onClick = onNew,
            leading = { Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
        )
    }
}

/** 搜索→⌘K 胶囊(书架顶栏):填充式淡灰,40dp 高 */
@Composable
internal fun SearchCommandPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 40.dp)
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("搜索", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        com.milknote.app.ui.common.MilkKeyCap("⌘K")
    }
}

/** 笔记本卡片:封面(淡彩渐染 + 首页真实缩略图 / 无内容时点阵)+ 元信息 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookCard(
    meta: NotebookMeta,
    pageCount: Int,
    recent: Boolean,
    coverThumb: androidx.compose.ui.graphics.ImageBitmap?,
    onOpen: () -> Unit,
    onManage: () -> Unit,
) {
    val cover = runCatching { Color(android.graphics.Color.parseColor(meta.coverColor)) }.getOrDefault(Color(0xFF3B5BEC))
    val tagColor = runCatching { Color(android.graphics.Color.parseColor(meta.tagColor.ifBlank { meta.coverColor })) }.getOrDefault(cover)
    // 等高框架(A-05):封面固定 aspectRatio + 元信息固定 72dp,新建卡复用同一骨架
    Column(
        modifier = Modifier
            .softCard()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onOpen, onLongClick = onManage)
            .innerHighlight(strength = 0.3f)
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
            // 迷你点阵纸纹(无缩略图时作为封面主体;有缩略图时退为衬底)
            MiniDotsPaper(
                modifier = Modifier.fillMaxSize(),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (coverThumb != null) 0.14f else 0.22f),
            )
            // 首页真实缩略图(A-06):按纸张比例从顶部铺满封面(裁切显示首页上部——
            // 标题/首行通常在这里),像一本翻开看到的笔记本;淡彩仅留在书脊与底部渐隐
            if (coverThumb != null) {
                androidx.compose.foundation.Image(
                    bitmap = coverThumb,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    alignment = Alignment.TopStart,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, cover.copy(alpha = 0.28f)),
                            )
                        ),
                )
                // 深色书架:缩略图纸张按导出规范恒亮,叠 10% 暗色蒙版避免暗底上刺眼
                if (com.milknote.app.ui.theme.LocalMilkDark.current) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)))
                }
            }
            // 封面色书脊:左侧 6dp 竖条,呼应新建弹窗的预览
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(Brush.horizontalGradient(listOf(cover.copy(alpha = 0.9f), cover.copy(alpha = 0.55f)))),
            )
            // 「最近」徽标
            if (recent) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text("最近", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 可见管理入口：重命名/标签/移动/删除统一进入中央管理面板。
            Box(Modifier.align(Alignment.TopEnd)) {
                Box(
                    Modifier.size(48.dp).clickable(onClick = onManage),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.MoreVert, "笔记本操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(72.dp)) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Text(
                    meta.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(tagColor, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    MonoText("${pageCount} 页 · ${formatRelative(meta.updatedAt)}${if (meta.folder.isBlank()) "" else " · ${meta.folder}"}")
                    Spacer(Modifier.weight(1f))
                    // A-03:小字用对比达标前景(亮=同色相加深,暗=提亮;原色对比 2.4–4.5 不足)
                    val tagFg = com.milknote.app.ui.theme.coverForeground(
                        tagColor, com.milknote.app.ui.theme.LocalMilkDark.current,
                    )
                    Box(
                        Modifier
                            .background(tagColor.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(meta.purpose, fontSize = 10.5.sp, color = tagFg)
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
            .dashedBorder(MaterialTheme.colorScheme.outline, 1.5.dp, cornerRadius = 18.dp)
            .clip(MaterialTheme.shapes.large)
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
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("新建笔记本", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(5.dp))
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
