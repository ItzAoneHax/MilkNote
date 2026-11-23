package com.milknote.app.ui.bookshelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.ViewSidebar
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milknote.app.R
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.cards.CardEditorPanel
import com.milknote.app.ui.cards.CardImage
import com.milknote.app.ui.cards.CardsViewModel
import com.milknote.app.ui.cards.InsertCardPanel
import com.milknote.app.ui.cards.cardColor
import com.milknote.app.ui.common.MilkActionPanel
import com.milknote.app.ui.common.MilkAnchoredMenu
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkColorDot
import com.milknote.app.ui.common.MilkConfirmDialog
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkKeyCap
import com.milknote.app.ui.common.MilkMenuRow
import com.milknote.app.ui.common.MilkPanelAction
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.MilkToast
import com.milknote.app.ui.common.MonoText
import com.milknote.app.ui.common.dashedBorder
import com.milknote.app.ui.common.formatRelative
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.GreenOk
import com.milknote.app.ui.theme.GreenOkDark
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.LocalMilkWarning
import com.milknote.app.ui.theme.Mono
import com.milknote.app.ui.theme.RedPen
import com.milknote.app.ui.writer.edgeHairline
import com.milknote.notes.model.COVER_COLORS
import com.milknote.notes.model.NoteCard
import com.milknote.notes.model.NotebookMeta
import com.milknote.notes.storage.CardTagFilter

private val BookCoverShape = RoundedCornerShape(
    topStart = 4.dp,
    bottomStart = 4.dp,
    topEnd = 12.dp,
    bottomEnd = 12.dp,
)

/**
 * 牛奶笔记第二代书架（DESIGN-V2 §1–§2）：
 * - 左侧平齐目录树（全部 / 星标 / 卡片 / 回收站 / 文件夹，可收起；底部笔状态 + 设置）
 * - 主区 3:4 竖向实体书本封面（无外层冗余卡片框，书脊色带 + 左上朱砂红丝带书签 + 右上 PDF 折角 + 虚线 + 空位）
 * - 支持网格 / 列表视图一键切换，卡片库直接内联在主区展示
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(
    vm: BookshelfViewModel,
    cardsVm: CardsViewModel = viewModel(),
    onOpenNotebook: (String) -> Unit,
    onOpenNotebookSplit: (String) -> Unit = onOpenNotebook,
    onOpenNotebookWithImage: (String, android.net.Uri) -> Unit = { id, _ -> onOpenNotebook(id) },
    onExportNotebook: (String) -> Unit = onOpenNotebook,
    onInsertCard: (NoteCard, String, String) -> Unit = { _, nbId, _ -> onOpenNotebook(nbId) },
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPenSettings: () -> Unit = onOpenSettings,
    onOpenPalette: () -> Unit = {},
    onOpenCards: () -> Unit = {},
    showNewNotebook: Boolean = false,
    onNewNotebookHandled: () -> Unit = {},
) {
    val notebooks by vm.notebooks.collectAsState()
    val liveNotebooks by vm.allLiveNotebooks.collectAsState()
    val trashedNotebooks by vm.trashedNotebooks.collectAsState()
    val nav by vm.nav.collectAsState()
    val listView by vm.listView.collectAsState()
    val railCollapsed by vm.railCollapsed.collectAsState()
    val customFolders by vm.customFolders.collectAsState()
    val sortMode by vm.sortMode.collectAsState()
    val importing by vm.importing.collectAsState()
    val covers by vm.covers.collectAsState()
    val cardsState by cardsVm.state.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var showCreateActions by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var actionMenuTarget by remember { mutableStateOf<NotebookMeta?>(null) }
    var toastText by remember { mutableStateOf("") }
    val appSettings by ServiceLocator.appSettings.flow.collectAsState(initial = com.milknote.app.ui.settings.AppSettings())
    var manageTarget by remember { mutableStateOf<NotebookMeta?>(null) }
    var deleteTarget by remember { mutableStateOf<NotebookMeta?>(null) }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    // 卡片内联弹窗状态
    var creatingCard by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<NoteCard?>(null) }
    var deletingCard by remember { mutableStateOf<NoteCard?>(null) }
    var insertingCard by remember { mutableStateOf<NoteCard?>(null) }

    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    fun openImageNotebook(uri: android.net.Uri, prefix: String) {
        val id = vm.create(prefix, "#2563EB", "blank", "阅读摘录")
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
        cardsVm.refresh()
        vm.events.collect { event ->
            when (event) {
                is BookshelfEvent.Imported -> onOpenNotebook(event.notebookId)
                is BookshelfEvent.Message -> toastText = event.text
            }
        }
    }
    // 应用内轻提示:1.9s 后自动淡出
    androidx.compose.runtime.LaunchedEffect(toastText) {
        if (toastText.isNotEmpty()) {
            kotlinx.coroutines.delay(1900)
            toastText = ""
        }
    }

    LaunchedEffect(showNewNotebook) {
        if (showNewNotebook) {
            showCreate = true
            onNewNotebookHandled()
        }
    }

    val battery by ServiceLocator.penBattery.level.collectAsState()
    val folders = remember(liveNotebooks, customFolders) { vm.folders() }

    // 排序:锚定在排序钮下方的弹出菜单(当前项 ✓)
    val sortContent: @Composable ColumnScope.() -> Unit = {
        BookshelfSortMode.entries.forEach { mode ->
            MilkMenuRow(
                MilkPanelAction(
                    label = mode.label,
                    trailingText = if (mode == sortMode) "✓" else null,
                    onClick = {
                        vm.setSortMode(mode)
                        sortExpanded = false
                    },
                ),
            )
        }
    }
    // 笔记本操作:锚定在封面「…」钮下方(回收站内为 恢复 / 彻底删除)
    val bookActions: (NotebookMeta) -> List<MilkPanelAction> = { target ->
        if (target.trashed) {
            listOf(
                MilkPanelAction(label = "恢复", icon = Icons.Outlined.RestoreFromTrash, onClick = { actionMenuTarget = null; vm.restoreFromTrash(target.id) }),
                MilkPanelAction(label = "彻底删除", icon = Icons.Outlined.Delete, destructive = true, onClick = { actionMenuTarget = null; deleteTarget = target }),
            )
        } else {
            listOf(
                MilkPanelAction(label = "双开分屏", icon = Icons.Outlined.VerticalSplit, onClick = { actionMenuTarget = null; onOpenNotebookSplit(target.id) }),
                MilkPanelAction(
                    label = if (target.pinned) "取消星标" else "星标",
                    icon = if (target.pinned) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                    onClick = { actionMenuTarget = null; vm.togglePin(target.id) },
                ),
                MilkPanelAction(label = "重命名与文件夹", icon = Icons.Outlined.Edit, onClick = { actionMenuTarget = null; manageTarget = target }),
                MilkPanelAction(label = "导出", icon = Icons.Outlined.FileDownload, onClick = { actionMenuTarget = null; onExportNotebook(target.id) }),
                MilkPanelAction(label = "移入回收站", icon = Icons.Outlined.DeleteOutline, destructive = true, onClick = { actionMenuTarget = null; vm.moveToTrash(target.id) }),
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
    Row(Modifier.fillMaxSize()) {
        // ---- 左侧平齐目录树 (Side Rail) ----
        if (!railCollapsed) {
            BookshelfSideRail(
                nav = nav,
                allCount = liveNotebooks.size,
                pinnedCount = liveNotebooks.count { it.pinned },
                cardsCount = cardsState.cards.size,
                trashCount = trashedNotebooks.size,
                folders = folders,
                folderCount = { folder -> liveNotebooks.count { it.folder == folder } },
                battery = battery,
                onSelectNav = { target ->
                    if (target is BookshelfNav.Cards) cardsVm.refresh()
                    vm.setNav(target)
                },
                onAddFolder = { showNewFolderDialog = true },
                onCollapseRail = vm::toggleRail,
                onOpenPenSettings = onOpenPenSettings,
                onOpenDiagnostics = onOpenDiagnostics,
                onOpenSettings = onOpenSettings,
            )
        }

        // ---- 右侧主书架画布 ----
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background),
        ) {
            BookshelfHeaderBar(
                title = when (val n = nav) {
                    BookshelfNav.All -> "全部"
                    BookshelfNav.Pinned -> "星标"
                    BookshelfNav.Cards -> "卡片"
                    BookshelfNav.Trash -> "回收站"
                    is BookshelfNav.Folder -> n.name
                },
                railCollapsed = railCollapsed,
                isCards = nav is BookshelfNav.Cards,
                isTrash = nav is BookshelfNav.Trash,
                hasTrash = trashedNotebooks.isNotEmpty(),
                listView = listView,
                onExpandRail = vm::toggleRail,
                onSearch = onOpenPalette,
                onToggleListView = vm::toggleListView,
                sortExpanded = sortExpanded,
                onSortToggle = { sortExpanded = !sortExpanded },
                onSortDismiss = { sortExpanded = false },
                sortContent = sortContent,
                onPrimaryNew = {
                    if (nav is BookshelfNav.Cards) creatingCard = true
                    else showCreate = true
                },
                onPrimaryMoreCreate = { showCreateActions = true },
                onEmptyTrash = { showEmptyTrashConfirm = true },
            )

            when (nav) {
                BookshelfNav.Cards -> {
                    BookshelfCardsContent(
                        vm = cardsVm,
                        onNewCard = { creatingCard = true },
                        onEditCard = { editingCard = it },
                        onDeleteCard = { deletingCard = it },
                        onInsertCard = { insertingCard = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                else -> {
                    if (nav is BookshelfNav.Trash && notebooks.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "回收站为空",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (listView) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f).testTag("bookshelf-list"),
                        ) {
                            items(notebooks, key = { it.id }) { nb ->
                                PortraitBookListRow(
                                    meta = nb,
                                    pageCount = vm.pageCountOf(nb.id),
                                    onOpen = { if (nb.trashed) actionMenuTarget = nb else onOpenNotebook(nb.id) },
                                    menuExpanded = actionMenuTarget?.id == nb.id,
                                    onMore = { actionMenuTarget = if (actionMenuTarget?.id == nb.id) null else nb },
                                    onDismissMenu = { actionMenuTarget = null },
                                    menu = { bookActions(nb).forEach { MilkMenuRow(it) } },
                                )
                            }
                            if (nav !is BookshelfNav.Trash) {
                                item(key = "__new_list__") {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                            .height(48.dp)
                                            .dashedBorder(MaterialTheme.colorScheme.outline, 1.5.dp, cornerRadius = 10.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { showCreate = true },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Add,
                                            contentDescription = "新建笔记本",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 172.dp),
                            contentPadding = PaddingValues(start = 34.dp, end = 34.dp, top = 18.dp, bottom = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalArrangement = Arrangement.spacedBy(34.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f).testTag("bookshelf-grid"),
                        ) {
                            items(notebooks, key = { it.id }) { nb ->
                                Box(Modifier.animateItem()) {
                                    PortraitBookItem(
                                        meta = nb,
                                        pageCount = vm.pageCountOf(nb.id),
                                        coverThumb = covers[nb.id],
                                        onOpen = { if (nb.trashed) actionMenuTarget = nb else onOpenNotebook(nb.id) },
                                        menuExpanded = actionMenuTarget?.id == nb.id,
                                        onMore = { actionMenuTarget = if (actionMenuTarget?.id == nb.id) null else nb },
                                        onDismissMenu = { actionMenuTarget = null },
                                        menu = { bookActions(nb).forEach { MilkMenuRow(it) } },
                                    )
                                }
                            }
                            if (nav !is BookshelfNav.Trash) {
                                item(key = "__new__") {
                                    PortraitNewBookSlot(
                                        onNew = { showCreate = true },
                                        onLongNew = { showCreateActions = true },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    // 应用内轻提示(DESIGN-V2 toast):底部居中深色胶囊,替代系统 Toast
    com.milknote.app.ui.common.MilkToast(
        visible = toastText.isNotEmpty(),
        text = toastText,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
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
            title = "创建与导入",
            subtitle = "零账号本地存储（.milk 目录）",
            actions = listOf(
                MilkPanelAction("新建笔记本", icon = Icons.Outlined.Add, onClick = {
                    showCreateActions = false; showCreate = true
                }),
                MilkPanelAction("导入 PDF / .milk.zip 备份", icon = Icons.Outlined.FileOpen, onClick = {
                    showCreateActions = false
                    importLauncher.launch(arrayOf("application/pdf", "application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"))
                }),
                MilkPanelAction("从图片创建笔记", icon = Icons.Outlined.Image, onClick = {
                    showCreateActions = false; imageLauncher.launch("image/*")
                }),
                MilkPanelAction("拍照创建笔记", icon = Icons.Outlined.CameraAlt, onClick = {
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

    AnimatedVisibility(visible = showCreate, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
        NewNotebookDialog(
            onDismiss = { showCreate = false },
            defaultPaper = appSettings.defaultPaper,
            defaultPaperColor = appSettings.defaultPaperColor,
            onCreate = { name, color, paper, purpose, pageSize, orientation, paperColor ->
                val id = vm.create(name, color, paper, purpose, pageSize, orientation, paperColor)
                showCreate = false
                onOpenNotebook(id)
            },
        )
    }

    manageTarget?.let { target ->
        NotebookManagePanel(
            meta = target,
            folders = folders,
            onDismiss = { manageTarget = null },
            onSave = { updated -> vm.updateNotebook(updated); manageTarget = null },
            onDelete = {
                manageTarget = null
                if (target.trashed) deleteTarget = target else vm.moveToTrash(target.id)
            },
        )
    }

    deleteTarget?.let { target ->
        MilkConfirmDialog(
            title = "彻底删除「${target.name}」？",
            body = "笔记本目录（${target.id}.milk）将被永久删除，包括全部页面笔迹。此操作不可撤销。",
            confirmLabel = "彻底删除",
            onDismiss = { deleteTarget = null },
            onConfirm = { vm.delete(target.id); deleteTarget = null },
        )
    }

    if (showEmptyTrashConfirm) {
        MilkConfirmDialog(
            title = "清空回收站？",
            body = "回收站中的 ${trashedNotebooks.size} 本笔记将被永久删除，此操作不可撤销。",
            confirmLabel = "清空",
            onDismiss = { showEmptyTrashConfirm = false },
            onConfirm = {
                showEmptyTrashConfirm = false
                vm.emptyTrash()
            },
        )
    }

    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        MilkCenterPanel(
            title = "新建文件夹",
            onDismiss = { showNewFolderDialog = false },
            maxWidth = 420.dp,
        ) {
            Spacer(Modifier.height(10.dp))
            MilkTextField(
                value = folderName,
                onValueChange = { folderName = it },
                placeholder = "如：高数与线代",
                label = "文件夹名称",
                mono = false,
                onDone = {
                    vm.addFolder(folderName)
                    showNewFolderDialog = false
                },
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MilkButton("取消", onClick = { showNewFolderDialog = false }, variant = MilkButtonVariant.SECONDARY)
                Spacer(Modifier.width(10.dp))
                MilkButton("创建", onClick = {
                    vm.addFolder(folderName)
                    showNewFolderDialog = false
                })
            }
        }
    }

    // ---- 卡片弹窗 ----
    if (creatingCard) {
        CardEditorPanel(
            existing = null,
            onDismiss = { creatingCard = false },
            onSave = { title, body, tags, color ->
                cardsVm.save(null, title, body, tags, color)
                creatingCard = false
            },
        )
    }
    editingCard?.let { card ->
        CardEditorPanel(
            existing = card,
            onDismiss = { editingCard = null },
            onSave = { title, body, tags, color ->
                cardsVm.save(card, title, body, tags, color)
                editingCard = null
            },
        )
    }
    deletingCard?.let { card ->
        MilkConfirmDialog(
            title = "删除「${card.title.ifBlank { "无标题卡片" }}」？",
            body = "卡片会从卡片库移除，已经插入到笔记里的副本不受影响。",
            confirmLabel = "删除",
            onDismiss = { deletingCard = null },
            onConfirm = { cardsVm.delete(card.id); deletingCard = null },
        )
    }
    insertingCard?.let { card ->
        InsertCardPanel(
            card = card,
            onDismiss = { insertingCard = null },
            onInsert = { nbId, pageId ->
                insertingCard = null
                onInsertCard(card, nbId, pageId)
            },
        )
    }
}

// ==================== 左侧平齐目录树 (Side Rail) ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfSideRail(
    nav: BookshelfNav,
    allCount: Int,
    pinnedCount: Int,
    cardsCount: Int,
    trashCount: Int,
    folders: List<String>,
    folderCount: (String) -> Int,
    battery: Int,
    onSelectNav: (BookshelfNav) -> Unit,
    onAddFolder: () -> Unit,
    onCollapseRail: () -> Unit,
    onOpenPenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(cs.surfaceVariant)
            .edgeHairline(cs.outline, right = true),
    ) {
        // 品牌头:26dp 奶滴笔锋图标 + 牛奶笔记 + 收起侧栏按钮
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(start = 18.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = "牛奶笔记",
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "牛奶笔记",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onCollapseRail),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.ViewSidebar,
                    contentDescription = "收起侧栏",
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // 目录滚动区
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            SideNavRow(
                label = "全部",
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                count = allCount,
                selected = nav is BookshelfNav.All,
                onClick = { onSelectNav(BookshelfNav.All) },
            )
            SideNavRow(
                label = "星标",
                icon = Icons.Outlined.StarOutline,
                count = pinnedCount,
                selected = nav is BookshelfNav.Pinned,
                onClick = { onSelectNav(BookshelfNav.Pinned) },
            )
            SideNavRow(
                label = "卡片",
                icon = Icons.Outlined.Style,
                count = cardsCount,
                selected = nav is BookshelfNav.Cards,
                onClick = { onSelectNav(BookshelfNav.Cards) },
            )
            SideNavRow(
                label = "回收站",
                icon = Icons.Outlined.DeleteOutline,
                count = trashCount,
                selected = nav is BookshelfNav.Trash,
                onClick = { onSelectNav(BookshelfNav.Trash) },
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "文件夹",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .clickable(onClick = onAddFolder),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "新建文件夹",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            folders.forEach { folder ->
                SideNavRow(
                    label = folder,
                    icon = Icons.Outlined.Folder,
                    count = folderCount(folder),
                    selected = (nav as? BookshelfNav.Folder)?.name == folder,
                    onClick = { onSelectNav(BookshelfNav.Folder(folder)) },
                )
            }
        }

        // 底栏:触控笔状态微胶囊 + 设置齿轮
        Row(
            Modifier
                .fillMaxWidth()
                .edgeHairline(cs.outline, top = true)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val dark = LocalMilkDark.current
            val dotColor = when {
                battery in 0..19 -> LocalMilkWarning.current
                else -> if (dark) GreenOkDark else GreenOk
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = onOpenPenSettings,
                        onLongClick = onOpenDiagnostics,
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (battery >= 0) "$battery%" else "Focus Pen",
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "设置",
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SideNavRow(
    label: String,
    icon: ImageVector,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (selected) cs.primary.copy(alpha = 0.10f) else Color.Transparent
    val fg = if (selected) cs.primary else cs.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 1.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 38.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            count.toString(),
            fontFamily = Mono,
            fontSize = 12.sp,
            color = if (selected) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.75f),
        )
    }
}

// ==================== 主区顶栏 ====================

@Composable
private fun BookshelfHeaderBar(
    title: String,
    railCollapsed: Boolean,
    isCards: Boolean,
    isTrash: Boolean,
    hasTrash: Boolean,
    listView: Boolean,
    onExpandRail: () -> Unit,
    onSearch: () -> Unit,
    onToggleListView: () -> Unit,
    onSortToggle: () -> Unit,
    sortExpanded: Boolean,
    onSortDismiss: () -> Unit,
    sortContent: @Composable ColumnScope.() -> Unit,
    onPrimaryNew: () -> Unit,
    onPrimaryMoreCreate: () -> Unit,
    onEmptyTrash: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(start = 28.dp, end = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (railCollapsed) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onExpandRail),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.ViewSidebar,
                    contentDescription = "展开侧栏",
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
        }

        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onBackground,
        )

        Spacer(Modifier.weight(1f))

        SearchCommandPill(onClick = onSearch)

        if (!isCards) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (listView) cs.primary.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable(onClick = onToggleListView),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (listView) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.FormatListBulleted,
                    contentDescription = "切换列表视图",
                    tint = if (listView) cs.primary else cs.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Box {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (sortExpanded) cs.primary.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable(onClick = onSortToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = "排序",
                        tint = if (sortExpanded) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                // 排序:锚定在排序钮下方的弹出菜单
                MilkAnchoredMenu(expanded = sortExpanded, onDismiss = onSortDismiss, anchorHeight = 36.dp, minWidth = 186.dp) { sortContent() }
            }
        }

        Spacer(Modifier.width(10.dp))

        if (isTrash) {
            if (hasTrash) {
                MilkButton(
                    label = "清空回收站",
                    onClick = onEmptyTrash,
                    variant = MilkButtonVariant.TEXT,
                    destructive = true,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(cs.primary)
                        .clickable(onClick = onPrimaryNew)
                        .defaultMinSize(minHeight = 38.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        tint = cs.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isCards) "新建卡片" else "新建",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onPrimary,
                    )
                }
                if (!isCards) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable(onClick = onPrimaryMoreCreate),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.FileOpen,
                            contentDescription = "导入 PDF 或备份",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 搜索→⌘K 胶囊(书架顶栏):轻盈淡灰填充,36dp 高 */
@Composable
internal fun SearchCommandPill(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .width(210.dp)
            .height(36.dp)
            .clip(CircleShape)
            .background(cs.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text("搜索", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
        MilkKeyCap("⌘K")
    }
}

// ==================== 3:4 竖向实体书本封面（无外层框） ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PortraitBookItem(
    meta: NotebookMeta,
    pageCount: Int,
    coverThumb: androidx.compose.ui.graphics.ImageBitmap?,
    onOpen: () -> Unit,
    menuExpanded: Boolean,
    onMore: () -> Unit,
    onDismissMenu: () -> Unit,
    menu: @Composable ColumnScope.() -> Unit,
) {
    val cover = runCatching { Color(android.graphics.Color.parseColor(meta.coverColor)) }
        .getOrDefault(Color(0xFF2563EB))
    val sheetBg = runCatching { Color(android.graphics.Color.parseColor(meta.paperColor)) }
        .getOrDefault(Color.White)

    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onMore),
    ) {
        // 3:4 竖向实体书本封面（恒保持亮色纸面）
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.76f)
                .softShadow(6.dp, BookCoverShape)
                .clip(BookCoverShape)
                .background(sheetBg)
                .border(1.dp, Color(0x1818191B), BookCoverShape),
        ) {
            // 纸面底纹与内容
            if (coverThumb != null) {
                Image(
                    bitmap = coverThumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopStart,
                    modifier = Modifier.fillMaxSize().padding(start = 11.dp),
                )
            } else {
                PortraitBookPaperArt(
                    meta = meta,
                    coverColor = cover,
                    sheetBg = sheetBg,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 左侧 11dp 实体书脊色带 + 2dp 压痕暗线
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(11.dp)
                    .background(cover)
                    .drawBehind {
                        val crease = 2.dp.toPx()
                        drawRect(
                            color = Color.Black.copy(alpha = 0.14f),
                            topLeft = Offset(size.width - crease, 0f),
                            size = Size(crease, size.height),
                        )
                    },
            )

            // 星标置顶:左上朱砂红丝带书签 (Form speaks, zero text badge!)
            if (meta.pinned) {
                Canvas(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 22.dp)
                        .width(12.dp)
                        .height(22.dp),
                ) {
                    val ribbon = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        lineTo(size.width * 0.5f, size.height * 0.74f)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(ribbon, RedPen)
                }
            }

            // PDF 笔记本:右上折角 + 右下 PDF 淡印
            if (meta.isPdf) {
                Canvas(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(26.dp),
                ) {
                    val fold = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(fold, Color(0xFFE5E7EB))
                    val corner = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(corner, Color(0xFFD1D5DB))
                }
                Text(
                    "PDF",
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedPen.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 42.dp, bottom = 10.dp),
                )
            }

            // 右下角安静的「…」圆形操作钮 + 锚定在其下方的操作菜单
            Box(Modifier.align(Alignment.BottomEnd)) {
                Box(
                    Modifier
                        .padding(8.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.90f))
                        .border(0.5.dp, Color(0x1F18191B), CircleShape)
                        .clickable(onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = "更多操作",
                        tint = Color(0xFF18191B),
                        modifier = Modifier.size(16.dp),
                    )
                }
                MilkAnchoredMenu(expanded = menuExpanded, onDismiss = onDismissMenu, anchorHeight = 30.dp, minWidth = 200.dp) { menu() }
            }
        }

        // 书本下方简洁标题与单行元信息
        Column(Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp)) {
            Text(
                meta.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            MonoText(
                text = "${pageCount} 页 · ${formatRelative(meta.updatedAt)}${if (meta.folder.isBlank()) "" else " · ${meta.folder}"}",
            )
        }
    }
}

/** 封面纸面纹理与自然手写笔迹示意（恒亮色纸面） */
@Composable
private fun PortraitBookPaperArt(
    meta: NotebookMeta,
    coverColor: Color,
    sheetBg: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(sheetBg)) {
        Canvas(Modifier.fillMaxSize().padding(start = 11.dp)) {
            val inkMuted = Color(0xFF18191B).copy(alpha = 0.14f)
            when (meta.paper) {
                "dot" -> {
                    val step = 18.dp.toPx()
                    var y = step
                    while (y < size.height - 12.dp.toPx()) {
                        var x = step
                        while (x < size.width - 10.dp.toPx()) {
                            drawCircle(inkMuted, radius = 1.1f, center = Offset(x, y))
                            x += step
                        }
                        y += step
                    }
                }
                "line", "english" -> {
                    val step = 20.dp.toPx()
                    var y = 34.dp.toPx()
                    while (y < size.height - 16.dp.toPx()) {
                        drawLine(
                            Color(0xFF2563EB).copy(alpha = 0.13f),
                            Offset(12.dp.toPx(), y),
                            Offset(size.width - 12.dp.toPx(), y),
                            strokeWidth = 1.dp.toPx(),
                        )
                        y += step
                    }
                }
                "grid", "tian" -> {
                    val step = 20.dp.toPx()
                    var x = step
                    while (x < size.width) {
                        drawLine(inkMuted.copy(alpha = 0.08f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                        x += step
                    }
                    var y = step
                    while (y < size.height) {
                        drawLine(inkMuted.copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                        y += step
                    }
                }
                "cornell" -> {
                    val headerY = size.height * 0.15f
                    val cueX = size.width * 0.28f
                    drawLine(
                        Color(0xFF2563EB).copy(alpha = 0.32f),
                        Offset(8.dp.toPx(), headerY),
                        Offset(size.width - 8.dp.toPx(), headerY),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        RedPen.copy(alpha = 0.42f),
                        Offset(cueX, headerY),
                        Offset(cueX, size.height - 12.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }

            // 纸面手写曲线与公式高亮示意
            val startX = 16.dp.toPx()
            val endX = size.width - 18.dp.toPx()
            if (endX > startX + 20f) {
                drawRoundRect(
                    color = Color(0xFFFDE047).copy(alpha = 0.42f),
                    topLeft = Offset(startX, size.height * 0.24f),
                    size = Size((endX - startX) * 0.62f, 14.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
                val curve1 = Path().apply {
                    moveTo(startX, size.height * 0.46f)
                    cubicTo(
                        startX + (endX - startX) * 0.3f, size.height * 0.42f,
                        startX + (endX - startX) * 0.6f, size.height * 0.50f,
                        endX * 0.88f, size.height * 0.45f,
                    )
                }
                drawPath(
                    curve1,
                    color = coverColor.copy(alpha = 0.72f),
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
                )
                val curve2 = Path().apply {
                    moveTo(startX, size.height * 0.60f)
                    cubicTo(
                        startX + (endX - startX) * 0.25f, size.height * 0.57f,
                        startX + (endX - startX) * 0.55f, size.height * 0.63f,
                        startX + (endX - startX) * 0.72f, size.height * 0.59f,
                    )
                }
                drawPath(
                    curve2,
                    color = Color(0xFF18191B).copy(alpha = 0.42f),
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 14.dp, top = 20.dp),
        ) {
            Text(
                meta.name,
                fontFamily = FontFamily.Serif,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF18191B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (meta.isPdf) "Qᵀ A Q = Λ" else "A x = λ x",
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 12.5.sp,
                color = coverColor,
                maxLines = 1,
            )
        }
    }
}

/** 虚线新建空位:仅一枚居中 `+`,无多余文字说明 (DESIGN-V2 §1) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PortraitNewBookSlot(
    onNew: () -> Unit,
    onLongNew: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.76f)
            .dashedBorder(MaterialTheme.colorScheme.outline, 1.5.dp, cornerRadius = 12.dp)
            .clip(BookCoverShape)
            .combinedClickable(onClick = onNew, onLongClick = onLongNew),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Add,
            contentDescription = "新建笔记本",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** 列表视图单行 */
@Composable
private fun PortraitBookListRow(
    meta: NotebookMeta,
    pageCount: Int,
    onOpen: () -> Unit,
    menuExpanded: Boolean,
    onMore: () -> Unit,
    onDismissMenu: () -> Unit,
    menu: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val cover = runCatching { Color(android.graphics.Color.parseColor(meta.coverColor)) }
        .getOrDefault(Color(0xFF2563EB))
    val sheetBg = runCatching { Color(android.graphics.Color.parseColor(meta.paperColor)) }
        .getOrDefault(Color.White)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 迷你 3:4 封面
        Box(
            Modifier
                .width(42.dp)
                .aspectRatio(0.78f)
                .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 6.dp, bottomEnd = 6.dp))
                .background(sheetBg)
                .border(1.dp, Color(0x1818191B), RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 6.dp, bottomEnd = 6.dp)),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(cover),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            meta.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (meta.pinned) {
            Icon(
                Icons.Outlined.Star,
                contentDescription = "星标",
                tint = RedPen,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        MonoText("${pageCount} 页 · ${formatRelative(meta.updatedAt)}${if (meta.folder.isBlank()) "" else " · ${meta.folder}"}")
        Spacer(Modifier.width(8.dp))
        Box {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    contentDescription = "更多操作",
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            MilkAnchoredMenu(expanded = menuExpanded, onDismiss = onDismissMenu, anchorHeight = 34.dp, minWidth = 200.dp) { menu() }
        }
    }
}

// ==================== 内联知识卡片视图 (Minimal Index Cards) ====================

@Composable
private fun BookshelfCardsContent(
    vm: CardsViewModel,
    onNewCard: () -> Unit,
    onEditCard: (NoteCard) -> Unit,
    onDeleteCard: (NoteCard) -> Unit,
    onInsertCard: (NoteCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    val cs = MaterialTheme.colorScheme

    Column(modifier) {
        // 轻量筛选条
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MilkChip("全部", state.tag == CardTagFilter.All, onClick = { vm.filterTag(CardTagFilter.All) }, small = true)
            MilkChip("无标签", state.tag == CardTagFilter.Untagged, onClick = { vm.filterTag(CardTagFilter.Untagged) }, small = true)
            state.allTags.forEach { tag ->
                MilkChip(tag, state.tag == CardTagFilter.Tag(tag), onClick = { vm.filterTag(CardTagFilter.Tag(tag)) }, small = true)
            }
            Spacer(Modifier.width(8.dp))
            COVER_COLORS.forEach { hex ->
                val selected = state.color.equals(hex, ignoreCase = true)
                MilkColorDot(
                    color = cardColor(hex),
                    selected = selected,
                    dotSize = 20.dp,
                    onClick = { vm.filterColor(if (selected) null else hex) },
                    contentDescription = "按颜色筛选",
                )
            }
        }

        if (state.cards.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无卡片", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    MilkButton("新建卡片", onClick = onNewCard)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(264.dp),
                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 14.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(state.cards, key = { it.id }) { card ->
                    val accent = cardColor(card.color)
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.surface)
                            .border(1.dp, cs.outline, RoundedCornerShape(12.dp))
                            .drawBehind {
                                drawRect(
                                    color = accent,
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, 3.dp.toPx()),
                                )
                            }
                            .clickable { onEditCard(card) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                card.title.ifBlank { "无标题卡片" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            MilkIconButton(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = "插入到笔记",
                                onClick = { onInsertCard(card) },
                                tint = cs.primary,
                                iconSize = 17.dp,
                                modifier = Modifier.size(32.dp),
                            )
                            MilkIconButton(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "删除卡片",
                                onClick = { onDeleteCard(card) },
                                tint = cs.onSurfaceVariant,
                                iconSize = 16.dp,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        card.imageSrc?.let { CardImage(it) }
                        if (card.body.isNotBlank()) {
                            Text(
                                card.body,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = cs.onSurfaceVariant,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (card.sourceNotebookId != null || card.tags.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = card.sourceNotebookId?.let { "$it · ${card.sourcePageId ?: "p0001"}" } ?: "",
                                    fontFamily = Mono,
                                    fontSize = 11.sp,
                                    color = cs.onSurfaceVariant.copy(alpha = 0.8f),
                                )
                                if (card.tags.isNotEmpty()) {
                                    Text(
                                        card.tags.take(2).joinToString(" ") { "#$it" },
                                        fontSize = 11.5.sp,
                                        color = cs.primary,
                                    )
                                }
                            }
                        }
                    }
                }
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
            drawCircle(color = tint, radius = 1.1f, center = Offset(x, y))
            x += step
        }
        y += step
    }
}
