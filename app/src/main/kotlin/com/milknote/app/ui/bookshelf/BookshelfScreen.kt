package com.milknote.app.ui.bookshelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
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
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milknote.app.R
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.cards.CardEditorPanel
import com.milknote.app.ui.cards.CardFilterRow
import com.milknote.app.ui.cards.CardsViewModel
import com.milknote.app.ui.cards.InsertCardPanel
import com.milknote.app.ui.cards.MilkNoteCard
import com.milknote.app.ui.common.BookCoverShape
import com.milknote.app.ui.common.MilkActionPanel
import com.milknote.app.ui.common.MilkAnchoredMenu
import com.milknote.app.ui.common.MilkBookCover
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkConfirmDialog
import com.milknote.app.ui.common.MilkEmptyState
import com.milknote.app.ui.common.MilkHairlineDivider
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkKeyCap
import com.milknote.app.ui.common.MilkMenuRow
import com.milknote.app.ui.common.MilkPanelAction
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.MilkToast
import com.milknote.app.ui.common.MonoText
import com.milknote.app.ui.common.dashedBorder
import com.milknote.app.ui.common.formatRelative
import com.milknote.app.ui.common.milkEdgeHairline
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.GreenOk
import com.milknote.app.ui.theme.GreenOkDark
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.LocalMilkWarning
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.PillShape
import com.milknote.app.ui.theme.RedPen
import com.milknote.app.ui.theme.SurfaceElevatedDark
import com.milknote.notes.model.NoteCard
import com.milknote.notes.model.NotebookMeta
import com.milknote.notes.storage.CardTagFilter

/**
 * 牛奶笔记第二代书架（DESIGN-V2 §1–§2）：
 * - 左侧平齐目录树（全部 / 星标 / 卡片 / 回收站 / 文件夹，可收起；底部笔状态 + 设置）
 * - 侧栏底色通栏铺满(延伸进状态栏与导航栏),内容各自避让 safeDrawing,消除上下白条
 * - 主区 3:4 竖向实体书本封面（共享 MilkBookCover:书脊色带 + 朱砂书签 + PDF 折角 + 虚线 + 空位）
 * - 支持网格 / 列表视图一键切换，卡片库直接内联在主区展示(共享 MilkNoteCard)
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
    // 文件夹行/块上的「…」只记录文件夹名;重命名与删除各自独立弹窗
    var folderMenuTarget by remember { mutableStateOf<String?>(null) }
    var renameFolderTarget by remember { mutableStateOf<String?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<String?>(null) }

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
    // 每个文件夹的内容(按最近编辑倒序):文件夹块用它画 2×2 迷你封面与「N 本 · 最近」摘要
    val folderBooks = remember(liveNotebooks, folders) {
        folders.associateWith { folder ->
            liveNotebooks.filter { it.folder == folder }.sortedByDescending { it.updatedAt }
        }
    }

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

    // 文件夹操作:打开 / 重命名 / 删除(删除只移除位置,笔记本退回未分组)
    val folderActions: (String) -> List<MilkPanelAction> = { folderName ->
        listOf(
            MilkPanelAction(label = "打开文件夹", icon = Icons.Outlined.FolderOpen, onClick = {
                folderMenuTarget = null
                vm.setNav(BookshelfNav.Folder(folderName))
            }),
            MilkPanelAction(label = "重命名文件夹", icon = Icons.Outlined.Edit, onClick = {
                folderMenuTarget = null
                renameFolderTarget = folderName
            }),
            MilkPanelAction(label = "删除文件夹", icon = Icons.Outlined.Delete, destructive = true, onClick = {
                folderMenuTarget = null
                deleteFolderTarget = folderName
            }),
        )
    }

    // ---- 滚动边缘(Scroll Edge)----
    // 每个滚动容器一个 state;当前页的第一项是否离顶决定顶栏底缘 hairline 是否浮现。
    val reducedMotion = LocalMilkMotion.current
    val notebooksGridState = rememberLazyGridState()
    val notebooksListState = rememberLazyListState()
    val cardsGridState = rememberLazyGridState()
    // 空态页没有滚动容器:不沿用上一页残留的滚动位置来显示分隔线
    val notebooksEmpty = notebooks.isEmpty()
    val scrolled by remember(nav, listView, notebooksEmpty) {
        derivedStateOf {
            when {
                nav is BookshelfNav.Cards ->
                    cardsGridState.firstVisibleItemIndex > 0 || cardsGridState.firstVisibleItemScrollOffset > 0
                notebooksEmpty && nav !is BookshelfNav.All -> false
                listView ->
                    notebooksListState.firstVisibleItemIndex > 0 || notebooksListState.firstVisibleItemScrollOffset > 0
                else ->
                    notebooksGridState.firstVisibleItemIndex > 0 || notebooksGridState.firstVisibleItemScrollOffset > 0
            }
        }
    }

    // 外层铺满全屏(不施加 safeDrawing):侧栏底色因此能通栏延伸进状态栏/导航栏
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxSize()) {
            // 侧栏:Apple 抽屉式编排(宽度 EaseDrawer 260/190ms,淡化 200/130ms 各自计时);
            // 减弱动效退化为 60ms 淡化,不做宽度位移。
            AnimatedVisibility(
                visible = !railCollapsed,
                enter = if (reducedMotion) {
                    fadeIn(tween(MilkMotion.reduced))
                } else {
                    expandHorizontally(
                        animationSpec = tween(MilkMotion.drawerEnter, easing = MilkMotion.EaseDrawer),
                        expandFrom = Alignment.Start,
                    ) + fadeIn(tween(MilkMotion.enter, easing = MilkMotion.EaseOut))
                },
                exit = if (reducedMotion) {
                    fadeOut(tween(MilkMotion.reduced))
                } else {
                    shrinkHorizontally(
                        animationSpec = tween(MilkMotion.drawerExit, easing = MilkMotion.EaseDrawer),
                        shrinkTowards = Alignment.Start,
                    ) + fadeOut(tween(MilkMotion.exit, easing = MilkMotion.EaseOut))
                },
            ) {
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

            // 右侧主区:侧栏展开时只避让上/下/结束边,侧栏收起时才需要避让起始边
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(
                        // 侧栏收起:四边全部避让;侧栏展开:起始边已由侧栏自己让开,只补上/下/结束边
                        if (railCollapsed) WindowInsets.safeDrawing
                        else WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End),
                    ),
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
                    scrolled = scrolled,
                    // 进入文件夹后顶栏给一枚固定返回位:标题左侧、无位移动画,点了回「全部」
                    showBack = nav is BookshelfNav.Folder,
                    onBack = { vm.setNav(BookshelfNav.All) },
                )

                when (val current = nav) {
                    BookshelfNav.Cards -> {
                        BookshelfCardsContent(
                            vm = cardsVm,
                            onNewCard = { creatingCard = true },
                            onEditCard = { editingCard = it },
                            onDeleteCard = { deletingCard = it },
                            onInsertCard = { insertingCard = it },
                            gridState = cardsGridState,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                    else -> {
                        // 「全部」在有文件夹时:文件夹自己排在最前,未分组笔记本随后——
                        // 已归档的笔记本不重复出现在这一层,否则「全部」会变成同一本书出现两次
                        val showFolders = current is BookshelfNav.All && folders.isNotEmpty()
                        val displayedNotebooks =
                            if (showFolders) notebooks.filter { it.folder.isBlank() } else notebooks
                        val isEmptyView = notebooks.isEmpty() &&
                            (current is BookshelfNav.Trash || current is BookshelfNav.Pinned || current is BookshelfNav.Folder)
                        if (isEmptyView) {
                            Box(
                                Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                when (current) {
                                    is BookshelfNav.Trash -> MilkEmptyState(
                                        title = "回收站为空",
                                        description = "移入回收站的笔记本会先停在这里，可随时恢复",
                                        icon = Icons.Outlined.DeleteOutline,
                                    )
                                    is BookshelfNav.Pinned -> MilkEmptyState(
                                        title = "还没有星标笔记",
                                        description = "在笔记本「…」菜单里加星标，常用的会置顶",
                                        icon = Icons.Outlined.StarOutline,
                                    )
                                    is BookshelfNav.Folder -> MilkEmptyState(
                                        title = "「${current.name}」还是空的",
                                        description = "新建的笔记本会自动归入这个文件夹",
                                        icon = Icons.Outlined.Folder,
                                        actionLabel = "新建笔记本",
                                        onAction = { showCreate = true },
                                    )
                                    else -> Unit
                                }
                            }
                        } else if (listView) {
                            LazyColumn(
                                state = notebooksListState,
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth().weight(1f).testTag("bookshelf-list"),
                            ) {
                                if (showFolders) {
                                    items(folders, key = { "__folder_row__$it" }) { folderName ->
                                        MilkFolderListRow(
                                            name = folderName,
                                            count = folderBooks[folderName]?.size ?: 0,
                                            onOpen = { vm.setNav(BookshelfNav.Folder(folderName)) },
                                            menuExpanded = folderMenuTarget == folderName,
                                            onMore = {
                                                folderMenuTarget = if (folderMenuTarget == folderName) null else folderName
                                            },
                                            onDismissMenu = { folderMenuTarget = null },
                                            menu = { folderActions(folderName).forEach { MilkMenuRow(it) } },
                                        )
                                    }
                                    if (displayedNotebooks.isNotEmpty()) {
                                        item(key = "__folder_sep__") {
                                            MilkHairlineDivider(Modifier.padding(vertical = MilkSpace.xs))
                                        }
                                    }
                                }
                                items(displayedNotebooks, key = { it.id }) { nb ->
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
                                if (current !is BookshelfNav.Trash) {
                                    item(key = "__new_list__") {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp)
                                                .height(48.dp)
                                                .clip(RoundedCornerShape(MilkRadius.md))
                                                .dashedBorder(MaterialTheme.colorScheme.outline, 1.dp, cornerRadius = MilkRadius.md)
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
                                state = notebooksGridState,
                                contentPadding = PaddingValues(start = 34.dp, end = 34.dp, top = 18.dp, bottom = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(28.dp),
                                verticalArrangement = Arrangement.spacedBy(34.dp),
                                modifier = Modifier.fillMaxWidth().weight(1f).testTag("bookshelf-grid"),
                            ) {
                                if (showFolders) {
                                    items(folders, key = { "__folder__$it" }) { folderName ->
                                        Box(Modifier.animateItem()) {
                                            MilkFolderGridTile(
                                                name = folderName,
                                                books = folderBooks[folderName].orEmpty(),
                                                onOpen = { vm.setNav(BookshelfNav.Folder(folderName)) },
                                                menuExpanded = folderMenuTarget == folderName,
                                                onMore = {
                                                    folderMenuTarget = if (folderMenuTarget == folderName) null else folderName
                                                },
                                                onDismissMenu = { folderMenuTarget = null },
                                                menu = { folderActions(folderName).forEach { MilkMenuRow(it) } },
                                            )
                                        }
                                    }
                                }
                                items(displayedNotebooks, key = { it.id }) { nb ->
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
                                if (current !is BookshelfNav.Trash) {
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
        // 应用内轻提示(DESIGN-V2 toast):底部居中深色胶囊,避开导航栏
        MilkToast(
            visible = toastText.isNotEmpty(),
            text = toastText,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(bottom = 22.dp),
        )
    }

    if (importing) {
        MilkCenterPanel(title = "正在导入", onDismiss = {}, maxWidth = 420.dp, closable = false) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("正在校验文件并生成新笔记本…", style = MilkText.body, color = MaterialTheme.colorScheme.onSurface)
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
                MilkPanelAction("新建文件夹", icon = Icons.Outlined.Folder, onClick = {
                    showCreateActions = false; showNewFolderDialog = true
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

    // 新建弹窗:入场/退场唯一交给 MilkCenterPanel.milkPanelEnter(居中 0.96→1 微缩放)。
    // 这里再包 AnimatedVisibility(enter = fadeIn() + scaleIn()) 会与面板入场叠成双层动画,
    // 且默认 scaleIn() 从 scale(0) 起步——整块「从无到有」炸开,正是要修的缺陷。
    if (showCreate) {
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
            // 从这里新建的文件夹只登记位置,不把用户从当前面板/页面拽走
            onCreateFolder = { vm.addFolder(it, navigate = false) },
        )
    }

    renameFolderTarget?.let { target ->
        var renamed by remember(target) { mutableStateOf(target) }
        MilkCenterPanel(
            title = "重命名文件夹",
            onDismiss = { renameFolderTarget = null },
            maxWidth = 420.dp,
            footer = {
                Spacer(Modifier.weight(1f))
                MilkButton("取消", onClick = { renameFolderTarget = null }, variant = MilkButtonVariant.SECONDARY)
                Spacer(Modifier.width(MilkSpace.sm))
                MilkButton(
                    label = "保存",
                    enabled = renamed.isNotBlank(),
                    onClick = {
                        vm.renameFolder(target, renamed)
                        renameFolderTarget = null
                    },
                )
            },
        ) {
            Spacer(Modifier.height(MilkSpace.md))
            MilkTextField(
                value = renamed,
                onValueChange = { renamed = it },
                placeholder = "文件夹名称",
                label = "文件夹名称",
                mono = false,
                onDone = {
                    vm.renameFolder(target, renamed)
                    renameFolderTarget = null
                },
                onClear = { renamed = "" },
            )
            Spacer(Modifier.height(MilkSpace.md))
        }
    }

    deleteFolderTarget?.let { target ->
        MilkConfirmDialog(
            title = "删除文件夹「$target」？",
            body = "文件夹将被移除，其中的笔记本会保留在「全部」的未分组列表中，不会被删除。",
            confirmLabel = "删除",
            onDismiss = { deleteFolderTarget = null },
            onConfirm = {
                vm.deleteFolder(target)
                deleteFolderTarget = null
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
            footer = {
                Spacer(Modifier.weight(1f))
                MilkButton("取消", onClick = { showNewFolderDialog = false }, variant = MilkButtonVariant.SECONDARY)
                Spacer(Modifier.width(MilkSpace.sm))
                MilkButton(
                    label = "创建",
                    enabled = folderName.isNotBlank(),
                    onClick = {
                        vm.addFolder(folderName)
                        showNewFolderDialog = false
                    },
                )
            },
        ) {
            Spacer(Modifier.height(MilkSpace.md))
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
            Spacer(Modifier.height(MilkSpace.md))
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
            .milkEdgeHairline(cs.outline, right = true),
    ) {
        // 内容层避让系统栏:底色已经在外面铺满整高,这里只让内容离开状态栏/导航栏
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
                ),
        ) {
            // 品牌头:26dp 奶滴笔锋图标 + 牛奶笔记 + 收起侧栏按钮
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(start = 18.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher),
                    contentDescription = "牛奶笔记",
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(MilkRadius.xs)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "牛奶笔记",
                    style = MilkText.cardTitle,
                    color = cs.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                MilkIconButton(
                    imageVector = Icons.Outlined.ViewSidebar,
                    contentDescription = "收起侧栏",
                    onClick = onCollapseRail,
                    iconSize = 18.dp,
                )
            }

            // 目录滚动区
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MilkSpace.sm, vertical = 4.dp),
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
                        .padding(start = 10.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "文件夹",
                        style = MilkText.eyebrow,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    MilkIconButton(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "新建文件夹",
                        onClick = onAddFolder,
                        iconSize = 16.dp,
                        visualSize = 28.dp,
                    )
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
                    .milkEdgeHairline(cs.outline, top = true)
                    .padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
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
                        .clip(RoundedCornerShape(MilkRadius.sm))
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
                        style = MilkText.monoMeta,
                        color = cs.onSurfaceVariant,
                    )
                }
                MilkIconButton(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "设置",
                    onClick = onOpenSettings,
                    iconSize = 18.dp,
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
    // 选中底色与文字色走同一 160ms 颜色语义,切换页签不再瞬跳
    val bg by animateColorAsState(
        if (selected) cs.primary.copy(alpha = 0.10f) else Color.Transparent,
        MilkMotion.colorSpec(),
        label = "sideNavBg",
    )
    val fg by animateColorAsState(
        if (selected) cs.primary else cs.onSurfaceVariant,
        MilkMotion.colorSpec(),
        label = "sideNavFg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .clip(RoundedCornerShape(MilkRadius.sm))
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
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
            style = MilkText.rowTitle.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            count.toString(),
            style = MilkText.monoMeta,
            color = if (selected) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.75f),
        )
    }
}

// ==================== 主区顶栏 ====================

@Composable
internal fun BookshelfHeaderBar(
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
    scrolled: Boolean = false,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    // 滚动边缘分隔线(Apple scroll-edge):停在顶部时完全干净,内容滚到顶栏下方才 150ms 淡入 1dp hairline
    val hairline by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(MilkMotion.color, easing = MilkMotion.EaseOut),
        label = "bookshelfHeaderHairline",
    )
    BoxWithConstraints {
        // 窄宽断点对所有页签生效:搜索收成图标、CTA 回到「新建」
        // 返回位是固定 48dp 槽位:带着它时提前收窄搜索胶囊,标题才不会被挤成一个「…」
        val compact = maxWidth < (if (showBack) 700.dp else 580.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .milkEdgeHairline(cs.outline.copy(alpha = hairline), bottom = true)
                .padding(start = if (compact) 12.dp else 28.dp, end = if (compact) 12.dp else 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (railCollapsed) {
                MilkIconButton(
                    imageVector = Icons.Outlined.ViewSidebar,
                    contentDescription = "展开侧栏",
                    onClick = onExpandRail,
                    iconSize = 18.dp,
                )
                Spacer(Modifier.width(MilkSpace.xxs))
            }

            // 文件夹内的返回位:固定槽位(不参与折叠动画),标题因此不会左右跳动
            if (showBack) {
                MilkIconButton(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回全部",
                    onClick = onBack,
                    iconSize = 18.dp,
                )
                Spacer(Modifier.width(MilkSpace.xxs))
            }

            Text(
                title,
                style = MilkText.pageTitle,
                color = cs.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(MilkSpace.md))

            // ---- 次级操作:搜索 → 视图切换 → 排序 → 导入 ----
            if (compact) {
                MilkIconButton(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "搜索",
                    onClick = onSearch,
                    iconSize = 18.dp,
                )
            } else {
                SearchCommandPill(onClick = onSearch)
                Spacer(Modifier.width(MilkSpace.xs))
            }

            if (!isCards) {
                MilkIconButton(
                    imageVector = if (listView) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.FormatListBulleted,
                    contentDescription = "切换列表视图",
                    onClick = onToggleListView,
                    selected = listView,
                    iconSize = 18.dp,
                )
                Box {
                    MilkIconButton(
                        imageVector = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = "排序",
                        onClick = onSortToggle,
                        selected = sortExpanded,
                        iconSize = 18.dp,
                    )
                    // 排序:锚定在排序钮下方的弹出菜单
                    MilkAnchoredMenu(
                        expanded = sortExpanded,
                        onDismiss = onSortDismiss,
                        anchorHeight = 48.dp,
                        minWidth = 186.dp,
                    ) { sortContent() }
                }
            }

            // 导入只属于「可用书架」:卡片页与回收站页都不该出现(回收站没有可导入的目标)
            if (!isCards && !isTrash) {
                MilkIconButton(
                    imageVector = Icons.Outlined.FileOpen,
                    contentDescription = "导入 PDF 或备份",
                    onClick = onPrimaryMoreCreate,
                    iconSize = 18.dp,
                )
            }

            Spacer(Modifier.width(MilkSpace.sm))

            // ---- 主 CTA:永远锚定最右端 ----
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
                MilkButton(
                    label = if (isCards && !compact) "新建卡片" else "新建",
                    onClick = onPrimaryNew,
                    leading = {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            tint = cs.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

/** 搜索→⌘K 胶囊(书架顶栏):surfaceVariant 轻底 + 1dp hairline,内置 ⌘K 键帽 */
@Composable
internal fun SearchCommandPill(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .width(210.dp)
            .height(36.dp)
            .clip(PillShape)
            .background(cs.surfaceVariant)
            .border(1.dp, cs.outline.copy(alpha = 0.7f), PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(MilkSpace.sm))
        Text(
            "搜索",
            style = MilkText.body,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        MilkKeyCap(
            "⌘K",
            modifier = Modifier
                .clip(RoundedCornerShape(MilkRadius.xs))
                .background(cs.surface),
        )
    }
}

// ==================== 3:4 竖向实体书本封面（共享 MilkBookCover） ====================

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
    val cover = remember(meta.coverColor) { parseColor(meta.coverColor, Color(0xFF2563EB)) }
    val sheetBg = remember(meta.paperColor) { parseColor(meta.paperColor, Color.White) }
    // 按压反馈挂在封面上(MilkBookCover 内部 0.97 微缩);整列不再叠方框按压阴影,
    // 否则连下方标题一起变灰,封面微缩这一下就不干净了。
    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = pressSource,
                indication = null,
                onClick = onOpen,
                onLongClick = onMore,
            ),
    ) {
        MilkBookCover(
            name = meta.name,
            cover = cover,
            paperColor = sheetBg,
            paper = meta.paper,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.76f),
            thumbnail = coverThumb,
            pinned = meta.pinned,
            isPdf = meta.isPdf,
            shadowElevation = 6.dp,
            pressed = pressed,
            overlay = {
                // 右下角安静的「…」圆形操作钮 + 锚定在其下方的操作菜单
                Box {
                    Box(
                        Modifier
                            .padding(MilkSpace.sm)
                            .size(30.dp)
                            .softShadow(3.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.92f))
                            .border(0.5.dp, Color(0x1F18191B), CircleShape)
                            .clickable(role = Role.Button, onClick = onMore),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.MoreHoriz,
                            contentDescription = "更多操作",
                            tint = Color(0xFF18191B),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    MilkAnchoredMenu(
                        expanded = menuExpanded,
                        onDismiss = onDismissMenu,
                        anchorHeight = 30.dp,
                        minWidth = 200.dp,
                    ) { menu() }
                }
            },
        )

        // 书本下方简洁标题与单行元信息
        Column(Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp)) {
            Text(
                meta.name,
                style = MilkText.cardTitle,
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

private fun parseColor(hex: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

/** 虚线新建空位:仅一枚居中 `+`,无多余文字说明 (DESIGN-V2 §1) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PortraitNewBookSlot(
    onNew: () -> Unit,
    onLongNew: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.76f)
            .clip(BookCoverShape)
            .dashedBorder(cs.outline, 1.dp, cornerRadius = MilkRadius.lg)
            .combinedClickable(onClick = onNew, onLongClick = onLongNew),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Add,
            contentDescription = "新建笔记本",
            tint = cs.onSurfaceVariant.copy(alpha = 0.7f),
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
    val cover = remember(meta.coverColor) { parseColor(meta.coverColor, Color(0xFF2563EB)) }
    val sheetBg = remember(meta.paperColor) { parseColor(meta.paperColor, Color.White) }
    val miniShape = RoundedCornerShape(
        topStart = 2.dp,
        bottomStart = 2.dp,
        topEnd = MilkRadius.xs,
        bottomEnd = MilkRadius.xs,
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MilkRadius.md))
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 迷你 3:4 封面
        Box(
            Modifier
                .width(38.dp)
                .aspectRatio(0.78f)
                .clip(miniShape)
                .background(sheetBg)
                .border(1.dp, Color(0x1818191B), miniShape),
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
            style = MilkText.rowTitle,
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
        Box {
            MilkIconButton(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = "更多操作",
                onClick = onMore,
                iconSize = 18.dp,
            )
            MilkAnchoredMenu(
                expanded = menuExpanded,
                onDismiss = onDismissMenu,
                anchorHeight = 48.dp,
                minWidth = 200.dp,
            ) { menu() }
        }
    }
}

// ==================== 文件夹(网格块 / 列表行) ====================

/**
 * 网格文件夹块:与 [PortraitBookItem] 完全同一列几何(3:4 视觉块 + 10dp 间距 + 两行说明),
 * 文件夹与笔记本混排时行高严格对齐。右下角「…」与书本封面上的操作钮是同一枚。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MilkFolderGridTile(
    name: String,
    books: List<NotebookMeta>,
    onOpen: () -> Unit,
    menuExpanded: Boolean,
    onMore: () -> Unit,
    onDismissMenu: () -> Unit,
    menu: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val reduced = LocalMilkMotion.current
    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.97f else 1f,
        animationSpec = tween(MilkMotion.press, easing = MilkMotion.EaseOut),
        label = "folderTileScale",
    )
    val shape = remember { FolderCardShape() }
    val latestRel = books.maxOfOrNull { it.updatedAt }?.let { " · ${formatRelative(it)}" }.orEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = pressSource,
                indication = null,
                onClick = onOpen,
                onLongClick = onMore,
            ),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.76f)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .softShadow(4.dp, shape)
                    .clip(shape)
                    .background(if (dark) SurfaceElevatedDark else cs.surfaceVariant.copy(alpha = 0.72f))
                    .border(1.dp, cs.outline, shape),
            ) {
                FolderCardBody(books = books)
            }
            Box(Modifier.align(Alignment.BottomEnd)) {
                Box(
                    Modifier
                        .padding(MilkSpace.sm)
                        .size(30.dp)
                        .softShadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f))
                        .border(0.5.dp, Color(0x1F18191B), CircleShape)
                        .clickable(role = Role.Button, onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = "更多操作",
                        tint = Color(0xFF18191B),
                        modifier = Modifier.size(16.dp),
                    )
                }
                MilkAnchoredMenu(
                    expanded = menuExpanded,
                    onDismiss = onDismissMenu,
                    anchorHeight = 30.dp,
                    minWidth = 200.dp,
                ) { menu() }
            }
        }

        Column(Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp)) {
            Text(
                name,
                style = MilkText.cardTitle,
                color = cs.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            MonoText("${books.size} 本$latestRel")
        }
    }
}

/**
 * 文件夹轮廓内部:有内容时铺 2×2 迷你封面(宽、高双向约束取小值,任何瓦片尺寸都不溢出);
 * 空文件夹只留一枚克制的淡图标。底部 40dp 留白专供「…」钮,两者永不重叠。
 */
@Composable
private fun FolderCardBody(books: List<NotebookMeta>) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tabHeight = 14.dp
        if (books.isEmpty()) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = cs.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = tabHeight)
                    .size(28.dp),
            )
        } else {
            val gap = 6.dp
            val horizontal = 16.dp
            val top = tabHeight + 14.dp
            val bottom = 40.dp
            val availableWidth = maxWidth - horizontal * 2
            val availableHeight = maxHeight - top - bottom
            val swatchWidth = minOf(
                (availableWidth - gap) / 2,
                ((availableHeight - gap) / 2) * 0.78f,
            )
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = horizontal, top = top),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (row in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (column in 0 until 2) {
                            val book = books.getOrNull(row * 2 + column)
                            if (book == null) {
                                Spacer(Modifier.size(swatchWidth, swatchWidth / 0.78f))
                            } else {
                                MiniFolderBookSwatch(meta = book, width = swatchWidth)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 文件夹里的迷你书本:只保留书脊色、纸色与 3:4 比例,hairline 描边,缩小后仍一眼可辨 */
@Composable
private fun MiniFolderBookSwatch(meta: NotebookMeta, width: Dp) {
    val cover = remember(meta.coverColor) { parseColor(meta.coverColor, Color(0xFF2563EB)) }
    val sheet = remember(meta.paperColor) { parseColor(meta.paperColor, Color.White) }
    val shape = RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    Box(
        Modifier
            .size(width = width, height = width / 0.78f)
            .clip(shape)
            .background(sheet)
            .border(0.5.dp, Color(0x1F18191B), shape),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width((width * 0.16f).coerceAtLeast(3.dp))
                .background(cover),
        )
    }
}

/** 列表文件夹行:与 [PortraitBookListRow] 同行高、同内边距;左侧 38dp 文件夹徽章替代迷你封面 */
@Composable
private fun MilkFolderListRow(
    name: String,
    count: Int,
    onOpen: () -> Unit,
    menuExpanded: Boolean,
    onMore: () -> Unit,
    onDismissMenu: () -> Unit,
    menu: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val badgeShape = RoundedCornerShape(
        topStart = 2.dp,
        bottomStart = 2.dp,
        topEnd = MilkRadius.xs,
        bottomEnd = MilkRadius.xs,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MilkRadius.md))
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(38.dp)
                .aspectRatio(0.78f)
                .clip(badgeShape)
                .background(cs.primary.copy(alpha = 0.10f))
                .border(1.dp, cs.primary.copy(alpha = 0.22f), badgeShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            name,
            style = MilkText.rowTitle,
            color = cs.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MonoText("$count 本")
        Box {
            MilkIconButton(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = "更多操作",
                onClick = onMore,
                iconSize = 18.dp,
            )
            MilkAnchoredMenu(
                expanded = menuExpanded,
                onDismiss = onDismissMenu,
                anchorHeight = 48.dp,
                minWidth = 200.dp,
            ) { menu() }
        }
    }
}

/**
 * 文件夹实体形状:左上标签页 + 主体两段圆角矩形按 Union 合成为一条连续轮廓——
 * 标签页与主体之间没有接缝,hairline 描边因此不会在中途断成两截。
 */
private class FolderCardShape(
    private val tabWidthFraction: Float = 0.38f,
    private val tabHeight: Dp = 14.dp,
    private val tabCorner: Dp = MilkRadius.sm,
    private val bodyTopStart: Dp = 2.dp,
    private val bodyCorner: Dp = MilkRadius.lg,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tabBottom = with(density) { tabHeight.toPx() }
        val tabRadius = with(density) { tabCorner.toPx() }
        val bodyRadius = with(density) { bodyCorner.toPx() }
        val bodyTopRadius = with(density) { bodyTopStart.toPx() }
        val tab = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width * tabWidthFraction,
                    bottom = tabBottom * 2f,
                    topLeftCornerRadius = CornerRadius(tabRadius),
                    topRightCornerRadius = CornerRadius(tabRadius),
                    bottomRightCornerRadius = CornerRadius.Zero,
                    bottomLeftCornerRadius = CornerRadius.Zero,
                ),
            )
        }
        val body = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = tabBottom,
                    right = size.width,
                    bottom = size.height,
                    topLeftCornerRadius = CornerRadius(bodyTopRadius),
                    topRightCornerRadius = CornerRadius(bodyRadius),
                    bottomRightCornerRadius = CornerRadius(bodyRadius),
                    bottomLeftCornerRadius = CornerRadius(bodyRadius),
                ),
            )
        }
        return Outline.Generic(Path().apply { op(tab, body, PathOperation.Union) })
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
    gridState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    val filtered = state.query.isNotBlank() || state.tag != CardTagFilter.All || state.color != null

    Column(modifier) {
        // 搜索 + 筛选:宽屏横向并排,窄屏搜索在上、筛选在下
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 6.dp),
        ) {
            if (maxWidth >= 560.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardSearchField(
                        query = state.query,
                        onQueryChange = vm::search,
                        modifier = Modifier.width(260.dp),
                    )
                    Spacer(Modifier.width(MilkSpace.md))
                    CardFilterRow(
                        state = state,
                        onTag = vm::filterTag,
                        onColor = { hex -> vm.filterColor(if (state.color.equals(hex, ignoreCase = true)) null else hex) },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column {
                    CardSearchField(query = state.query, onQueryChange = vm::search)
                    Spacer(Modifier.height(MilkSpace.xs))
                    CardFilterRow(
                        state = state,
                        onTag = vm::filterTag,
                        onColor = { hex -> vm.filterColor(if (state.color.equals(hex, ignoreCase = true)) null else hex) },
                    )
                }
            }
        }

        if (state.cards.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (filtered) {
                    MilkEmptyState(
                        title = "没有匹配的卡片",
                        description = "换一个关键词，或清除标签与颜色筛选",
                        icon = Icons.Outlined.Search,
                        actionLabel = "清除筛选",
                        onAction = {
                            vm.search("")
                            vm.filterTag(CardTagFilter.All)
                            vm.filterColor(null)
                        },
                    )
                } else {
                    MilkEmptyState(
                        title = "暂无卡片",
                        description = "新建一张，或在书写页把摘取保存为卡片",
                        icon = Icons.Outlined.Style,
                        actionLabel = "新建卡片",
                        onAction = onNewCard,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(264.dp),
                state = gridState,
                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 10.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(state.cards, key = { it.id }) { card ->
                    MilkNoteCard(
                        card = card,
                        onClick = { onEditCard(card) },
                        onInsert = { onInsertCard(card) },
                        onDelete = { onDeleteCard(card) },
                        sourceLabel = state.sourceLabels[card.id],
                    )
                }
            }
        }
    }
}

/** 书架卡片页的搜索框(与卡片页同一视觉与清空行为) */
@Composable
private fun CardSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    MilkTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = "搜索卡片…",
        mono = false,
        onClear = { onQueryChange("") },
        leading = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = modifier,
    )
}
