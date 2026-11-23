package com.milknote.app.ui.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChangeHistory
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.ui.common.MilkAnchoredMenu
import com.milknote.app.ui.common.MilkMenuRow
import com.milknote.app.ui.common.MilkPanelAction
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.writer.WriterMajorTool.BRUSH
import com.milknote.app.ui.writer.WriterMajorTool.ERASER
import com.milknote.app.ui.writer.WriterMajorTool.HIGHLIGHTER
import com.milknote.app.ui.writer.WriterMajorTool.LASER
import com.milknote.app.ui.writer.WriterMajorTool.READING
import com.milknote.app.ui.writer.WriterMajorTool.SHAPE
import com.milknote.ink.model.CardTextAlign
import com.milknote.ink.model.CardTextStyle
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.LassoContent
import com.milknote.ink.model.LassoMode
import kotlin.math.abs

/** 笔匣内容高度(横向);竖排停靠时为厚度 */
internal val WriterMinorToolbarHeight = 40.dp
internal val WriterMinorBarThickness = 56.dp
/** 顶栏 44dp;笔匣悬浮在画布之上,不再占用布局高度 */
internal val WriterChromeHeight = WriterMajorToolbarHeight

/** 辅助栏停靠边(拖拽柄拖到哪边就吸附哪边) */
internal enum class MinorDock { TOP, BOTTOM, LEFT, RIGHT }

/** 悬浮笔匣常驻六工具(其余工具收进「更多工具」溢出菜单) */
internal val dockCoreTools: List<WriterMajorTool> = listOf(BRUSH, HIGHLIGHTER, ERASER, SHAPE, LASER, READING)
internal val dockOverflowTools: List<WriterMajorTool> = listOf(
    WriterMajorTool.LASSO, WriterMajorTool.IMAGE, WriterMajorTool.TEXT,
    WriterMajorTool.DOODLE, WriterMajorTool.TAPE, WriterMajorTool.SNIP,
)

internal val writerShapeToolOrder = listOf(
    InkTool.LINE to "直线",
    InkTool.DASHED_LINE to "虚线",
    InkTool.ARROW to "箭头",
    InkTool.RECT to "矩形",
    InkTool.OVAL to "椭圆",
    InkTool.TRIANGLE to "三角形",
    InkTool.STAR to "星形",
    InkTool.POLYGON to "六边形",
)

/** 辅助栏内容条目(横/竖布局共用同一 LazyListScope DSL) */
private class MinorItems(
    val state: WriterUiState,
    val selectedTool: WriterMajorTool,
    val textStyle: CardTextStyle,
    val shapeTool: InkTool,
    val shapeColor: Long,
    val shapeWidth: Float,
    val onColor: (Long) -> Unit,
    val onWidth: (Float) -> Unit,
    val onEraserWidth: (Float) -> Unit,
    val onLassoMode: (LassoMode) -> Unit,
    val onLassoFilter: (LassoContent) -> Unit,
    val onLassoSettings: () -> Unit,
    val onImage: () -> Unit,
    val onTextEditor: () -> Unit,
    val onTextStyle: (CardTextStyle) -> Unit,
    val onShapeTool: (InkTool) -> Unit,
    val onShapeColor: (Long) -> Unit,
    val onShapeWidth: (Float) -> Unit,
    val onStartShape: () -> Unit,
    val onSnip: () -> Unit,
    val onTape: (Int) -> Unit,
    val onMaterials: () -> Unit,
)

/**
 * 第二代轻盈悬浮笔匣(DESIGN-V2 ink-dock):居中浮动的胶囊,
 * [拖拽柄][撤销][重做] | [钢笔 荧光笔 橡皮 图形 激光 阅读 + 更多工具] | [当前工具的色点/线条粗细/子控件] | [笔设置] [框选]
 * 可拖拽停靠四边;内容过宽时仅中部内容条滚动。
 */
@Composable
internal fun WriterMinorToolbar(
    state: WriterUiState,
    selectedTool: WriterMajorTool,
    textStyle: CardTextStyle,
    shapeTool: InkTool,
    shapeColor: Long,
    shapeWidth: Float,
    dock: MinorDock,
    maxWidth: Dp,
    maxHeight: Dp = 600.dp,
    onDockDrag: ((dragged: Offset, handleCenter: Offset) -> Unit)? = null,
    onDockDrop: ((dragged: Offset, handleCenter: Offset) -> Unit)? = null,
    onDockCancel: (() -> Unit)? = null,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCollapse: () -> Unit,
    onTool: (WriterMajorTool) -> Unit,
    onColor: (Long) -> Unit,
    onWidth: (Float) -> Unit,
    onEraserWidth: (Float) -> Unit,
    onLassoMode: (LassoMode) -> Unit,
    onLassoFilter: (LassoContent) -> Unit,
    onLassoSettings: () -> Unit,
    onImage: () -> Unit,
    onTextEditor: () -> Unit,
    onTextStyle: (CardTextStyle) -> Unit,
    onShapeTool: (InkTool) -> Unit,
    onShapeColor: (Long) -> Unit,
    onShapeWidth: (Float) -> Unit,
    onStartShape: () -> Unit,
    onSnip: () -> Unit,
    onTape: (Int) -> Unit,
    onMaterials: () -> Unit,
    onTune: () -> Unit,
) {
    val enabled = state.snip.phase == SnipPhase.IDLE
    val spec = writerMinorSpec(selectedTool, state.toolSettings)
    val items = MinorItems(
        state, selectedTool, textStyle, shapeTool, shapeColor, shapeWidth,
        onColor, onWidth, onEraserWidth, onLassoMode, onLassoFilter, onLassoSettings,
        onImage, onTextEditor, onTextStyle, onShapeTool, onShapeColor,
        onShapeWidth, onStartShape, onSnip, onTape, onMaterials,
    )
    val listState = rememberLazyListState()
    LaunchedEffect(selectedTool) { listState.scrollToItem(0) }
    val vertical = dock == MinorDock.LEFT || dock == MinorDock.RIGHT
    var handleCenter by remember { mutableStateOf(Offset.Zero) }
    var dragTotal by remember { mutableStateOf(Offset.Zero) }
    val dragModifier = if (onDockDrop != null) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { dragTotal = Offset.Zero },
                onDrag = { _, amount ->
                    dragTotal += amount
                    onDockDrag?.invoke(dragTotal, handleCenter)
                },
                onDragEnd = { onDockDrop(dragTotal, handleCenter) },
                onDragCancel = {
                    dragTotal = Offset.Zero
                    onDockCancel?.invoke()
                },
            )
        }
    } else Modifier
    val shape = if (vertical) RoundedCornerShape(20.dp) else RoundedCornerShape(999.dp)
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)

    val handle: @Composable () -> Unit = {
        Box(
            Modifier
                .size(if (vertical) WriterMinorBarThickness else 36.dp, if (vertical) 36.dp else WriterMinorToolbarHeight)
                .onGloballyPositioned {
                    handleCenter = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                }
                .then(dragModifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.DragIndicator,
                "拖动停靠笔匣",
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (vertical) {
        Column(
            Modifier
                .width(WriterMinorBarThickness)
                .heightIn(max = maxHeight)
                .clip(shape)
                .background(surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .softShadow(8.dp, shape)
                .padding(vertical = 4.dp)
                .semantics { contentDescription = "书写页悬浮笔匣(竖排)" },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            handle()
            DockIcon(Icons.AutoMirrored.Outlined.Undo, "撤销", state.canUndo && enabled, false, onUndo, "writer-undo")
            DockIcon(Icons.AutoMirrored.Outlined.Redo, "重做", state.canRedo && enabled, false, onRedo, "writer-redo")
            DockDivider(vertical = true)
            DockTools(vertical = true, selectedTool = selectedTool, enabled = enabled, onTool = onTool)
            DockDivider(vertical = true)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = WriterMinorBarThickness)
                    .testTag("writer-minor-tools")
                    .semantics { contentDescription = "笔匣内容,可纵向滚动" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { minorToolItems(items, enabled, spec) }
            DockIcon(Icons.Outlined.Tune, "笔盒与参数", true, false, onTune, "writer-tune")
            DockIcon(Icons.Outlined.CropFree, "框选提问", enabled, false, onSnip, "writer-snip", accent = true)
            DockIcon(Icons.Outlined.KeyboardArrowUp, "收起笔匣", true, false, onCollapse, "writer-collapse-minor")
        }
    } else {
        Row(
            Modifier
                .widthIn(max = maxWidth)
                .height(WriterMinorToolbarHeight + 8.dp)
                .clip(shape)
                .background(surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .softShadow(8.dp, shape)
                .padding(horizontal = 4.dp)
                .semantics { contentDescription = "书写页悬浮笔匣" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            handle()
            DockIcon(Icons.AutoMirrored.Outlined.Undo, "撤销", state.canUndo && enabled, false, onUndo, "writer-undo")
            DockIcon(Icons.AutoMirrored.Outlined.Redo, "重做", state.canRedo && enabled, false, onRedo, "writer-redo")
            DockDivider(vertical = false)
            DockTools(vertical = false, selectedTool = selectedTool, enabled = enabled, onTool = onTool)
            DockDivider(vertical = false)
            LazyRow(
                state = listState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .height(WriterMinorToolbarHeight)
                    .testTag("writer-minor-tools")
                    .semantics { contentDescription = "笔匣内容,可横向滚动" },
                horizontalArrangement = if (listState.canScrollForward || listState.canScrollBackward) Arrangement.Start else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) { minorToolItems(items, enabled, spec) }
            DockIcon(Icons.Outlined.Tune, "笔盒与参数", true, false, onTune, "writer-tune")
            DockIcon(Icons.Outlined.CropFree, "框选提问", enabled, false, onSnip, "writer-snip", accent = true)
            DockIcon(Icons.Outlined.KeyboardArrowUp, "收起笔匣", true, false, onCollapse, "writer-collapse-minor")
        }
    }
}

/** 笔匣常驻六工具 + 「更多工具」溢出菜单(套索/图片/文本/图案笔/胶带/摘取) */
@Composable
private fun DockTools(
    vertical: Boolean,
    selectedTool: WriterMajorTool,
    enabled: Boolean,
    onTool: (WriterMajorTool) -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        dockCoreTools.forEach { tool ->
            val active = isMajorToolActive(tool, selectedTool, WriterUiState())
            DockIcon(
                dockToolIcon(tool),
                if (active) "${tool.label}，当前工具" else tool.label,
                enabled,
                active,
                { onTool(tool) },
                "writer-major-tool-${tool.id}",
            )
        }
        Box {
            val overflowActive = selectedTool in dockOverflowTools
            DockIcon(
                Icons.Outlined.MoreHoriz,
                if (overflowActive) "更多工具，当前为${selectedTool.label}" else "更多工具",
                enabled,
                overflowActive,
                { overflowOpen = true },
                "writer-major-tool-more",
            )
            MilkAnchoredMenu(expanded = overflowOpen, onDismiss = { overflowOpen = false }, anchorHeight = 36.dp, minWidth = 176.dp) {
                dockOverflowTools.forEach { tool ->
                    MilkMenuRow(
                        MilkPanelAction(
                            label = tool.label,
                            icon = dockToolIcon(tool),
                            trailingText = if (selectedTool == tool) "当前" else null,
                            onClick = {
                                overflowOpen = false
                                onTool(tool)
                            },
                        ),
                    )
                }
            }
        }
    }
    if (vertical) Column(horizontalAlignment = Alignment.CenterHorizontally) { content() } else Row(verticalAlignment = Alignment.CenterVertically) { content() }
}

private fun dockToolIcon(tool: WriterMajorTool): ImageVector = when (tool) {
    BRUSH -> Icons.Outlined.Edit
    HIGHLIGHTER -> Icons.Outlined.Brush
    ERASER -> Icons.Outlined.Backspace
    SHAPE -> Icons.Outlined.Category
    LASER -> Icons.Outlined.FlashOn
    READING -> Icons.Outlined.PanTool
    WriterMajorTool.LASSO -> Icons.Outlined.Gesture
    WriterMajorTool.IMAGE -> Icons.Outlined.Image
    WriterMajorTool.TEXT -> Icons.Outlined.Title
    WriterMajorTool.DOODLE -> Icons.Outlined.InvertColors
    WriterMajorTool.SNIP -> Icons.Outlined.ContentCopy
    WriterMajorTool.TAPE -> Icons.Outlined.CropSquare
}

@Composable
private fun DockIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    tag: String,
    accent: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(36.dp, 36.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (active) cs.primary.copy(alpha = 0.12f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp).alpha(if (enabled) 1f else 0.35f),
                tint = when {
                    !enabled -> cs.onSurfaceVariant
                    active -> cs.primary
                    accent -> cs.primary
                    else -> cs.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun DockDivider(vertical: Boolean) {
    if (vertical) {
        Box(Modifier.width(18.dp).height(1.dp).background(cs_line()))
    } else {
        Box(Modifier.width(1.dp).height(16.dp).background(cs_line()))
    }
}

@Composable
private fun cs_line(): Color = MaterialTheme.colorScheme.outline

private fun LazyListScope.minorToolItems(items: MinorItems, enabled: Boolean, spec: WriterMinorSpec) {
    val state = items.state
    val selectedTool = items.selectedTool
    when (spec.kind) {
        WriterMinorKind.PEN, WriterMinorKind.HIGHLIGHTER, WriterMinorKind.LASER -> {
            items(spec.colors.size, key = { "color-$it" }) { index ->
                val color = spec.colors[index]
                InkColorDot(
                    color = color,
                    label = "${selectedTool.label}颜色 ${index + 1}",
                    active = state.tool.color == color,
                    enabled = enabled,
                    modifier = Modifier.testTag("writer-minor-color-$index"),
                    onClick = { items.onColor(color) },
                )
            }
            if (spec.kind != WriterMinorKind.LASER) {
                items(spec.widths.size, key = { "width-$it" }) { index ->
                    val width = spec.widths[index]
                    MinorWidthDot(
                        diameter = listOf(4.dp, 7.dp, 11.dp)[index],
                        label = "笔触 ${index + 1}",
                        selected = abs(state.tool.baseWidthMm - width) < 0.05f,
                        enabled = enabled,
                        tag = "writer-minor-width-$index",
                        onClick = { items.onWidth(width) },
                    )
                }
            } else {
                item(key = "laser-note") {
                    Text(
                        "临时指示",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
        WriterMinorKind.ERASER -> {
            items(spec.widths.size, key = { "eraser-$it" }) { index ->
                val width = spec.widths[index]
                MinorWidthDot(
                    diameter = listOf(4.dp, 10.dp, 22.dp)[index],
                    label = listOf("小橡皮", "中橡皮", "大橡皮")[index],
                    selected = abs(state.toolSettings.eraserWidthMm - width) < 0.1f,
                    enabled = enabled,
                    tag = "writer-eraser-width-$index",
                    asBar = false,
                    onClick = { items.onEraserWidth(width) },
                )
            }
        }
        WriterMinorKind.LASSO -> {
            item(key = "lasso-free") {
                DockIcon(Icons.Outlined.Gesture, "自由套索", enabled, state.lassoMode == LassoMode.FREEHAND,
                    { items.onLassoMode(LassoMode.FREEHAND) }, "writer-lasso-free")
            }
            item(key = "lasso-rect") {
                DockIcon(Icons.Outlined.CropFree, "矩形套索", enabled, state.lassoMode == LassoMode.RECTANGLE,
                    { items.onLassoMode(LassoMode.RECTANGLE) }, "writer-lasso-rect")
            }
            LassoContent.entries.forEach { filter ->
                item(key = "lasso-filter-${filter.name}") {
                    DockIcon(
                        lassoFilterIcon(filter),
                        "${filter.label}筛选",
                        enabled,
                        filter in state.lassoFilters,
                        { items.onLassoFilter(filter) },
                        "writer-lasso-filter-${filter.name.lowercase()}",
                    )
                }
            }
            item(key = "lasso-settings") {
                DockIcon(Icons.Outlined.MoreHoriz, "套索筛选说明", enabled, false, items.onLassoSettings, "writer-lasso-filter")
            }
        }
        WriterMinorKind.IMAGE -> {
            item(key = "choose-image") {
                MinorTextAction("选择图片", false, enabled, items.onImage, "writer-choose-image")
            }
            item(key = "recent-image") {
                DockIcon(Icons.Outlined.Image, "最近图片", enabled, false, items.onImage, "writer-recent-images")
            }
        }
        WriterMinorKind.TEXT -> {
            item(key = "text-input") {
                DockIcon(Icons.Outlined.TextFields, "输入文字", enabled, false, items.onTextEditor, "writer-text-input")
            }
            items(5, key = { "text-color-$it" }) { index ->
                val color = listOf(0xFF18191B, 0xFFE53935, 0xFFFFB300, 0xFF2563EB, 0xFF48B66A)[index]
                InkColorDot(
                    color = color,
                    label = "文字颜色 ${index + 1}",
                    active = items.textStyle.colorArgb == color,
                    enabled = enabled,
                    modifier = Modifier.testTag("writer-text-color-$index"),
                    onClick = { items.onTextStyle(items.textStyle.copy(colorArgb = color)) },
                )
            }
            items(4, key = { "font-$it" }) { index ->
                val size = listOf(16f, 24f, 36f, 48f)[index]
                MinorTextAction("${size.toInt()}", items.textStyle.fontSizePx == size, enabled,
                    { items.onTextStyle(items.textStyle.copy(fontSizePx = size)) }, "writer-text-size-$index")
            }
            val aligns = listOf(CardTextAlign.START, CardTextAlign.CENTER, CardTextAlign.END)
            val alignIcons = listOf(Icons.Outlined.FormatAlignLeft, Icons.Outlined.FormatAlignCenter, Icons.Outlined.FormatAlignRight)
            aligns.forEachIndexed { index, align ->
                item(key = "align-$index") {
                    DockIcon(alignIcons[index], listOf("左对齐", "居中", "右对齐")[index], enabled,
                        items.textStyle.align == align, { items.onTextStyle(items.textStyle.copy(align = align)) }, "writer-text-align-$index")
                }
            }
            item(key = "bold") {
                DockIcon(Icons.Outlined.FormatBold, "粗体", enabled, items.textStyle.bold,
                    { items.onTextStyle(items.textStyle.copy(bold = !items.textStyle.bold)) }, "writer-text-bold")
            }
            item(key = "italic") {
                DockIcon(Icons.Outlined.FormatItalic, "斜体", enabled, items.textStyle.italic,
                    { items.onTextStyle(items.textStyle.copy(italic = !items.textStyle.italic)) }, "writer-text-italic")
            }
            item(key = "underline") {
                DockIcon(Icons.Outlined.FormatUnderlined, "下划线", enabled, items.textStyle.underline,
                    { items.onTextStyle(items.textStyle.copy(underline = !items.textStyle.underline)) }, "writer-text-underline")
            }
            item(key = "strike") {
                DockIcon(Icons.Outlined.StrikethroughS, "删除线", enabled, items.textStyle.strikethrough,
                    { items.onTextStyle(items.textStyle.copy(strikethrough = !items.textStyle.strikethrough)) }, "writer-text-strike")
            }
        }
        WriterMinorKind.SHAPE -> {
            items(writerShapeToolOrder.size, key = { "shape-$it" }) { index ->
                val (tool, label) = writerShapeToolOrder[index]
                DockIcon(shapeIcon(tool), label, enabled, items.shapeTool == tool,
                    { items.onShapeTool(tool) }, "writer-shape-$index")
            }
            items(4, key = { "shape-color-$it" }) { index ->
                val color = listOf(0xFF18191B, 0xFF2563EB, 0xFFD94436, 0xFF2E9E6B)[index]
                InkColorDot(color, "图形颜色 ${index + 1}", items.shapeColor == color, enabled,
                    modifier = Modifier.testTag("writer-shape-color-$index"),
                    onClick = { items.onShapeColor(color) })
            }
            items(3, key = { "shape-width-$it" }) { index ->
                val width = listOf(0.4f, 0.7f, 1.2f)[index]
                MinorWidthDot(listOf(4.dp, 7.dp, 11.dp)[index], "图形描边 ${index + 1}",
                    abs(items.shapeWidth - width) < 0.05f, enabled, { items.onShapeWidth(width) }, "writer-shape-width-$index")
            }
            item(key = "start-shape") {
                MinorTextAction("绘制", true, enabled, items.onStartShape, "writer-start-shape")
            }
        }
        WriterMinorKind.SNIP -> {
            item(key = "start-snip") {
                MinorTextAction("圈选摘取", false, enabled, items.onSnip, "writer-start-snip")
            }
        }
        WriterMinorKind.TAPE -> {
            items(4, key = { "tape-$it" }) { index ->
                TapeSwatch(index, enabled, { items.onTape(index) }, "writer-tape-$index")
            }
            item(key = "tape-library") {
                DockIcon(Icons.Outlined.Add, "胶带素材库", enabled, false, items.onMaterials, "writer-tape-library")
            }
        }
        WriterMinorKind.READING -> Unit
    }
}

private fun lassoFilterIcon(filter: LassoContent): ImageVector = when (filter) {
    LassoContent.INK -> Icons.Outlined.Gesture
    LassoContent.SHAPE -> Icons.Outlined.Category
    LassoContent.IMAGE -> Icons.Outlined.Image
    LassoContent.TEXT -> Icons.Outlined.TextFields
    LassoContent.DOODLE -> Icons.Outlined.StarBorder
    LassoContent.TAPE -> Icons.Outlined.CropSquare
    LassoContent.STICKER -> Icons.Outlined.CheckBoxOutlineBlank
    LassoContent.MEDIA -> Icons.Outlined.SelectAll
}

private fun shapeIcon(tool: InkTool): ImageVector = when (tool) {
    InkTool.LINE -> Icons.Outlined.ShowChart
    InkTool.DASHED_LINE -> Icons.Outlined.MoreHoriz
    InkTool.ARROW -> Icons.Outlined.ArrowUpward
    InkTool.RECT -> Icons.Outlined.CropSquare
    InkTool.OVAL -> Icons.Outlined.RadioButtonUnchecked
    InkTool.TRIANGLE -> Icons.Outlined.ChangeHistory
    InkTool.STAR -> Icons.Outlined.StarBorder
    else -> Icons.Outlined.Category
}

@Composable
private fun MinorTextAction(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) cs.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = label }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) cs.primary else cs.onSurfaceVariant,
            fontSize = 12.5.sp,
            maxLines = 1,
        )
    }
}

/** 粗细档位:真实的细/中/粗线条(1.6 / 3 / 5dp);橡皮用圆点面积示意 */
@Composable
private fun MinorWidthDot(
    diameter: Dp,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    asBar: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp, 36.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (asBar) {
            val barHeight = when {
                diameter <= 5.dp -> 1.6.dp
                diameter <= 8.dp -> 3.dp
                else -> 5.dp
            }
            Box(
                Modifier
                    .size(28.dp, 28.dp)
                    .clip(CircleShape)
                    .background(if (selected) cs.primary.copy(alpha = 0.12f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(15.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (selected) cs.primary else cs.onSurfaceVariant),
                )
            }
        } else {
            Box(
                Modifier
                    .size(diameter.coerceAtMost(20.dp))
                    .clip(CircleShape)
                    .background(if (selected) cs.primary else cs.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun TapeSwatch(index: Int, enabled: Boolean, onClick: () -> Unit, tag: String) {
    val colors = listOf(0xFFD9C7A7, 0xFFA8D8EA, 0xFFF2B8C6, 0xFFC7B9E8)
    Box(
        Modifier.size(40.dp, 36.dp).clickable(enabled = enabled, onClick = onClick).testTag(tag)
            .semantics { contentDescription = "胶带样式 ${index + 1}" },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.width(30.dp).height(20.dp)) {
            drawRect(Color(colors[index]))
            val step = 8.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    Color.White.copy(alpha = 0.65f),
                    Offset(x, size.height),
                    Offset(x + size.height, 0f),
                    strokeWidth = 2.dp.toPx(),
                )
                x += step
            }
        }
    }
}
