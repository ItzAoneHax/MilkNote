package com.milknote.app.ui.writer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChangeHistory
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.TextFields
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.ink.model.CardTextAlign
import com.milknote.ink.model.CardTextStyle
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.LassoContent
import com.milknote.ink.model.LassoMode
import kotlin.math.abs

internal val WriterMinorToolbarHeight = 40.dp
/** 竖排停靠(左/右缘)时辅助栏厚度;横排高度仍为 40dp */
internal val WriterMinorBarThickness = 56.dp
internal val WriterChromeHeight = WriterMajorToolbarHeight + WriterMinorToolbarHeight

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

/** 辅助栏停靠边(千本笔记 drag_point:拖拽柄拖到哪边就吸附哪边,P065 收起恢复配套) */
internal enum class MinorDock { TOP, BOTTOM, LEFT, RIGHT }

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

/** 第二行整体随当前工具切换;可停靠四边(拖拽柄),可容纳时居中,溢出仅滚动。 */
@Composable
internal fun WriterMinorToolbar(
    state: WriterUiState,
    selectedTool: WriterMajorTool,
    textStyle: CardTextStyle,
    shapeTool: InkTool,
    shapeColor: Long,
    shapeWidth: Float,
    dock: MinorDock = MinorDock.TOP,
    onDockDrag: ((dragged: Offset, handleCenter: Offset) -> Unit)? = null,
    onDockDrop: ((dragged: Offset, handleCenter: Offset) -> Unit)? = null,
    onDockCancel: (() -> Unit)? = null,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCollapse: () -> Unit,
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
    // 拖拽柄:root 坐标中心 + 累计位移;拖动中实时回报(onDockDrag→外层画停靠预览),
    // 松手按最近边吸附一次(onDockDrop)。不做拖拽中途切换:横/竖布局分支切换会销毁
    // pointerInput,活动拖拽会被打断,故落位保留在松手时刻。
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

    val barShape = if (dock == MinorDock.LEFT || dock == MinorDock.RIGHT) {
        RoundedCornerShape(20.dp)
    } else {
        RoundedCornerShape(999.dp)
    }
    if (dock == MinorDock.LEFT || dock == MinorDock.RIGHT) {
        Box(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(WriterMinorBarThickness)
                    .fillMaxHeight()
                    .clip(barShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                    . edgeHairline(MaterialTheme.colorScheme.outline, left = true, right = true, top = true, bottom = true)
                    .semantics { contentDescription = "书写页辅助工具栏(竖排)" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(WriterMinorBarThickness, 40.dp)
                        .onGloballyPositioned {
                            handleCenter = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                        }
                        .dragHandle(dragModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DragIndicator,
                        "拖动停靠辅助栏",
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MinorIconButton(Icons.AutoMirrored.Outlined.Undo, "撤销", state.canUndo && enabled, false, onUndo, "writer-undo")
                MinorIconButton(Icons.AutoMirrored.Outlined.Redo, "重做", state.canRedo && enabled, false, onRedo, "writer-redo")
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("writer-minor-tools")
                        .semantics { contentDescription = "辅助工具带,可纵向滚动" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    minorToolItems(items, enabled, spec)
                }
                MinorIconButton(Icons.Outlined.KeyboardArrowUp, "收起辅助栏", true, false, onCollapse, "writer-collapse-minor")
            }
        }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(WriterMinorToolbarHeight)
                    .clip(barShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                    .edgeHairline(MaterialTheme.colorScheme.outline, left = true, right = true, top = true, bottom = true)
                    .padding(horizontal = 4.dp)
                    .semantics { contentDescription = "书写页辅助工具栏" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .fillMaxHeight()
                        .onGloballyPositioned {
                            handleCenter = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                        }
                        .dragHandle(dragModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DragIndicator,
                        "拖动停靠辅助栏",
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MinorIconButton(Icons.AutoMirrored.Outlined.Undo, "撤销", state.canUndo && enabled, false, onUndo, "writer-undo")
                MinorIconButton(Icons.AutoMirrored.Outlined.Redo, "重做", state.canRedo && enabled, false, onRedo, "writer-redo")
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("writer-minor-tools")
                        .semantics { contentDescription = "辅助工具带,可横向滚动" },
                    horizontalArrangement = if (listState.canScrollForward || listState.canScrollBackward) {
                        Arrangement.Start
                    } else {
                        Arrangement.Center
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    minorToolItems(items, enabled, spec)
                }
                MinorIconButton(Icons.Outlined.KeyboardArrowUp, "收起辅助栏", true, false, onCollapse, "writer-collapse-minor")
            }
        }
    }
}

private fun Modifier.dragHandle(drag: Modifier): Modifier = then(drag)

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
                MinorIconButton(Icons.Outlined.Gesture, "自由套索", enabled, state.lassoMode == LassoMode.FREEHAND,
                    { items.onLassoMode(LassoMode.FREEHAND) }, "writer-lasso-free")
            }
            item(key = "lasso-rect") {
                MinorIconButton(Icons.Outlined.CropFree, "矩形套索", enabled, state.lassoMode == LassoMode.RECTANGLE,
                    { items.onLassoMode(LassoMode.RECTANGLE) }, "writer-lasso-rect")
            }
            LassoContent.entries.forEach { filter ->
                item(key = "lasso-filter-${filter.name}") {
                    MinorIconButton(
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
                MinorIconButton(Icons.Outlined.MoreHoriz, "套索筛选说明", enabled, false, items.onLassoSettings, "writer-lasso-filter")
            }
        }
        WriterMinorKind.IMAGE -> {
            item(key = "choose-image") {
                MinorTextAction("选择图片", false, enabled, items.onImage, "writer-choose-image")
            }
            item(key = "recent-image") {
                MinorIconButton(Icons.Outlined.Image, "最近图片", enabled, false, items.onImage, "writer-recent-images")
            }
        }
        WriterMinorKind.TEXT -> {
            item(key = "text-input") {
                MinorIconButton(Icons.Outlined.TextFields, "输入文字", enabled, false, items.onTextEditor, "writer-text-input")
            }
            items(5, key = { "text-color-$it" }) { index ->
                val color = listOf(0xFF23221E, 0xFFE53935, 0xFFFFB300, 0xFF3B5BEC, 0xFF48B66A)[index]
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
                    MinorIconButton(alignIcons[index], listOf("左对齐", "居中", "右对齐")[index], enabled,
                        items.textStyle.align == align, { items.onTextStyle(items.textStyle.copy(align = align)) }, "writer-text-align-$index")
                }
            }
            item(key = "bold") {
                MinorIconButton(Icons.Outlined.FormatBold, "粗体", enabled, items.textStyle.bold,
                    { items.onTextStyle(items.textStyle.copy(bold = !items.textStyle.bold)) }, "writer-text-bold")
            }
            item(key = "italic") {
                MinorIconButton(Icons.Outlined.FormatItalic, "斜体", enabled, items.textStyle.italic,
                    { items.onTextStyle(items.textStyle.copy(italic = !items.textStyle.italic)) }, "writer-text-italic")
            }
            item(key = "underline") {
                MinorIconButton(Icons.Outlined.FormatUnderlined, "下划线", enabled, items.textStyle.underline,
                    { items.onTextStyle(items.textStyle.copy(underline = !items.textStyle.underline)) }, "writer-text-underline")
            }
            item(key = "strike") {
                MinorIconButton(Icons.Outlined.StrikethroughS, "删除线", enabled, items.textStyle.strikethrough,
                    { items.onTextStyle(items.textStyle.copy(strikethrough = !items.textStyle.strikethrough)) }, "writer-text-strike")
            }
        }
        WriterMinorKind.SHAPE -> {
            items(writerShapeToolOrder.size, key = { "shape-$it" }) { index ->
                val (tool, label) = writerShapeToolOrder[index]
                MinorIconButton(shapeIcon(tool), label, enabled, items.shapeTool == tool,
                    { items.onShapeTool(tool) }, "writer-shape-$index")
            }
            items(4, key = { "shape-color-$it" }) { index ->
                val color = listOf(0xFF23221E, 0xFF3B5BEC, 0xFFD2483C, 0xFF2E9E6B)[index]
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
                MinorIconButton(Icons.Outlined.Add, "胶带素材库", enabled, false, items.onMaterials, "writer-tape-library")
            }
        }
        WriterMinorKind.READING -> Unit
    }
}

/** 供 MinorItems 闭包携带的套索说明入口(保持与横排一致的调用形态) */

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
private fun MinorIconButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(6.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                Modifier.size(19.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MinorTextAction(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = label }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun MinorWidthDot(
    diameter: androidx.compose.ui.unit.Dp,
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
            .size(44.dp)
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
                    .size(28.dp)
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
                    .size(diameter)
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
        Modifier.size(48.dp).clickable(enabled = enabled, onClick = onClick).testTag(tag)
            .semantics { contentDescription = "胶带样式 ${index + 1}" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.width(34.dp).height(24.dp)) {
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
