package com.milknote.app.ui.writer

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import com.milknote.app.ui.common.MilkAnchoredMenu
import com.milknote.app.ui.common.MilkIcons
import com.milknote.app.ui.common.MilkMenuRow
import com.milknote.app.ui.common.MilkPanelAction
import com.milknote.app.ui.common.milkGlassRim
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.PillShape
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
import kotlinx.coroutines.launch

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
    val onColorDetail: (Int) -> Unit,
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
    onColorDetail: (Int) -> Unit = {},
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
        onColor, onColorDetail, onWidth, onEraserWidth, onLassoMode, onLassoFilter, onLassoSettings,
        onImage, onTextEditor, onTextStyle, onShapeTool, onShapeColor,
        onShapeWidth, onStartShape, onSnip, onTape, onMaterials,
    )
    val listState = rememberLazyListState()
    LaunchedEffect(selectedTool) { listState.scrollToItem(0) }
    val vertical = dock == MinorDock.LEFT || dock == MinorDock.RIGHT
    val reduced = LocalMilkMotion.current
    val dark = LocalMilkDark.current
    val scope = rememberCoroutineScope()
    var handleCenter by remember { mutableStateOf(Offset.Zero) }
    // 拖拽起点中心:随手指移动的偏移只进 graphicsLayer,不参与布局
    var dragStartCenter by remember { mutableStateOf(Offset.Zero) }
    var dragTotal by remember { mutableStateOf(Offset.Zero) }
    // 以 dock 为 key:停靠边变化时偏移实例同步重建为 0,形态切换不会残留旧位移
    val dragOffsetX = remember(dock) { Animatable(0f) }
    val dragOffsetY = remember(dock) { Animatable(0f) }
    val liftScale = remember { Animatable(1f) }
    var isDragging by remember { mutableStateOf(false) }

    fun settleBack(velocityX: Float = 0f, velocityY: Float = 0f) {
        scope.launch {
            if (!reduced) liftScale.animateTo(1f, tween(MilkMotion.press, easing = MilkMotion.EaseOut))
            if (reduced) {
                liftScale.snapTo(1f)
                dragOffsetX.snapTo(0f)
                dragOffsetY.snapTo(0f)
            } else {
                launch {
                    dragOffsetX.animateTo(
                        0f,
                        MilkMotion.momentumSpring(),
                        initialVelocity = velocityX,
                    )
                }
                launch {
                    dragOffsetY.animateTo(
                        0f,
                        MilkMotion.momentumSpring(),
                        initialVelocity = velocityY,
                    )
                }
            }
        }
    }

    val dragModifier = if (onDockDrop != null) {
        Modifier.pointerInput(Unit) {
            val velocityTracker = VelocityTracker()
            detectDragGestures(
                onDragStart = { pos ->
                    dragTotal = Offset.Zero
                    dragStartCenter = handleCenter
                    velocityTracker.resetTracking()
                    velocityTracker.addPosition(SystemClock.uptimeMillis(), pos)
                    isDragging = true
                    if (!reduced) {
                        scope.launch {
                            liftScale.animateTo(
                                1.02f,
                                tween(MilkMotion.press, easing = MilkMotion.EaseOut),
                            )
                        }
                    }
                },
                onDrag = { change, amount ->
                    change.consume()
                    dragTotal += amount
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    // 纯 GPU 平移:1:1 跟手,绝不触发布局重排
                    scope.launch {
                        dragOffsetX.snapTo(dragTotal.x)
                        dragOffsetY.snapTo(dragTotal.y)
                    }
                    onDockDrag?.invoke(dragTotal, dragStartCenter)
                },
                onDragEnd = {
                    val velocity = velocityTracker.calculateVelocity()
                    // 苹果动量投影:松手位置 + 继续滑行距离 → 交给屏幕决定最近停靠边
                    val projected = Offset(
                        dragTotal.x + MilkMotion.project(velocity.x, 0.992f),
                        dragTotal.y + MilkMotion.project(velocity.y, 0.992f),
                    )
                    onDockDrop?.invoke(projected, dragStartCenter)
                    isDragging = false
                    settleBack(velocity.x, velocity.y)
                },
                onDragCancel = {
                    isDragging = false
                    onDockCancel?.invoke()
                    settleBack()
                },
            )
        }
    } else Modifier
    val shape = if (vertical) RoundedCornerShape(MilkRadius.xl) else PillShape
    val rimElevation = if (isDragging) 16.dp else 10.dp
    val dockLayer = Modifier.graphicsLayer {
        translationX = dragOffsetX.value
        translationY = dragOffsetY.value
        scaleX = liftScale.value
        scaleY = liftScale.value
    }

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
                MilkIcons.GripDots,
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
                .then(dockLayer)
                .milkGlassRim(shape, dark = dark, elevation = rimElevation)
                .padding(vertical = 4.dp)
                .semantics { contentDescription = "书写页悬浮笔匣(竖排)" },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            handle()
            DockIcon(MilkIcons.Undo, "撤销", state.canUndo && enabled, false, onUndo, "writer-undo")
            DockIcon(MilkIcons.Redo, "重做", state.canRedo && enabled, false, onRedo, "writer-redo")
            DockDivider(vertical = true)
            DockTools(vertical = true, state = state, selectedTool = selectedTool, enabled = enabled, onTool = onTool)
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
            DockIcon(MilkIcons.Sliders, "笔盒与参数", true, false, onTune, "writer-tune")
            DockIcon(MilkIcons.SnipSpark, "框选提问", enabled, false, onSnip, "writer-snip", accent = true)
            DockIcon(MilkIcons.ChevronUp, "收起笔匣", true, false, onCollapse, "writer-collapse-minor")
        }
    } else {
        Row(
            Modifier
                .widthIn(max = maxWidth)
                .height(WriterMinorToolbarHeight + 8.dp)
                .then(dockLayer)
                .milkGlassRim(shape, dark = dark, elevation = rimElevation)
                .padding(horizontal = 4.dp)
                .semantics { contentDescription = "书写页悬浮笔匣" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            handle()
            DockIcon(MilkIcons.Undo, "撤销", state.canUndo && enabled, false, onUndo, "writer-undo")
            DockIcon(MilkIcons.Redo, "重做", state.canRedo && enabled, false, onRedo, "writer-redo")
            DockDivider(vertical = false)
            DockTools(vertical = false, state = state, selectedTool = selectedTool, enabled = enabled, onTool = onTool)
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
            DockIcon(MilkIcons.Sliders, "笔盒与参数", true, false, onTune, "writer-tune")
            DockIcon(MilkIcons.SnipSpark, "框选提问", enabled, false, onSnip, "writer-snip", accent = true)
            DockIcon(MilkIcons.ChevronUp, "收起笔匣", true, false, onCollapse, "writer-collapse-minor")
        }
    }
}

/** 笔匣常驻六工具 + 「更多工具」溢出菜单(套索/图片/文本/图案笔/胶带/摘取) */
@Composable
private fun DockTools(
    vertical: Boolean,
    state: WriterUiState,
    selectedTool: WriterMajorTool,
    enabled: Boolean,
    onTool: (WriterMajorTool) -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    val currentInkTool = state.toolSettings.currentTool
    val content: @Composable () -> Unit = {
        dockCoreTools.forEach { tool ->
            val active = isMajorToolActive(tool, selectedTool, state)
            DockIcon(
                dockToolIcon(tool, currentInkTool),
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
                MilkIcons.MoreDots,
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
                            icon = dockToolIcon(tool, currentInkTool),
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

private fun dockToolIcon(tool: WriterMajorTool, currentInkTool: InkTool = InkTool.PEN): ImageVector = when (tool) {
    BRUSH -> if (currentInkTool == InkTool.BALL) MilkIcons.BallpointPen else MilkIcons.FountainPen
    HIGHLIGHTER -> MilkIcons.Highlighter
    ERASER -> MilkIcons.Eraser
    SHAPE -> MilkIcons.Shapes
    LASER -> MilkIcons.LaserPointer
    READING -> MilkIcons.HandPan
    WriterMajorTool.LASSO -> MilkIcons.Lasso
    WriterMajorTool.IMAGE -> MilkIcons.Image
    WriterMajorTool.TEXT -> MilkIcons.TextBox
    WriterMajorTool.DOODLE -> MilkIcons.PatternBrush
    WriterMajorTool.SNIP -> MilkIcons.SnipExtract
    WriterMajorTool.TAPE -> MilkIcons.WashiTape
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
    val container by animateColorAsState(
        if (active) cs.primary.copy(alpha = 0.12f) else Color.Transparent,
        MilkMotion.colorSpec(), label = "dockIconBg",
    )
    val tint by animateColorAsState(
        when {
            active || accent -> cs.primary
            else -> cs.onSurfaceVariant
        },
        MilkMotion.colorSpec(), label = "dockIconTint",
    )
    Box(
        Modifier
            .size(36.dp, 36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp).alpha(if (enabled) 1f else 0.35f),
                tint = tint,
            )
        }
    }
}

@Composable
private fun DockDivider(vertical: Boolean) {
    if (vertical) {
        Box(Modifier.padding(vertical = 4.dp).width(18.dp).height(1.dp).background(cs_line()))
    } else {
        Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(16.dp).background(cs_line()))
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
                    onClick = {
                        if (state.tool.color == color) items.onColorDetail(index) else items.onColor(color)
                    },
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
                        style = MilkText.micro,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                DockIcon(MilkIcons.Lasso, "自由套索", enabled, state.lassoMode == LassoMode.FREEHAND,
                    { items.onLassoMode(LassoMode.FREEHAND) }, "writer-lasso-free")
            }
            item(key = "lasso-rect") {
                DockIcon(MilkIcons.LassoRect, "矩形套索", enabled, state.lassoMode == LassoMode.RECTANGLE,
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
                DockIcon(MilkIcons.MoreDots, "套索筛选说明", enabled, false, items.onLassoSettings, "writer-lasso-filter")
            }
        }
        WriterMinorKind.IMAGE -> {
            item(key = "choose-image") {
                MinorTextAction("选择图片", false, enabled, items.onImage, "writer-choose-image")
            }
            item(key = "recent-image") {
                DockIcon(MilkIcons.Image, "最近图片", enabled, false, items.onImage, "writer-recent-images")
            }
        }
        WriterMinorKind.TEXT -> {
            item(key = "text-input") {
                DockIcon(MilkIcons.TextBox, "输入文字", enabled, false, items.onTextEditor, "writer-text-input")
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
            val alignIcons = listOf(MilkIcons.AlignLeft, MilkIcons.AlignCenter, MilkIcons.AlignRight)
            aligns.forEachIndexed { index, align ->
                item(key = "align-$index") {
                    DockIcon(alignIcons[index], listOf("左对齐", "居中", "右对齐")[index], enabled,
                        items.textStyle.align == align, { items.onTextStyle(items.textStyle.copy(align = align)) }, "writer-text-align-$index")
                }
            }
            item(key = "bold") {
                DockIcon(MilkIcons.FormatBold, "粗体", enabled, items.textStyle.bold,
                    { items.onTextStyle(items.textStyle.copy(bold = !items.textStyle.bold)) }, "writer-text-bold")
            }
            item(key = "italic") {
                DockIcon(MilkIcons.FormatItalic, "斜体", enabled, items.textStyle.italic,
                    { items.onTextStyle(items.textStyle.copy(italic = !items.textStyle.italic)) }, "writer-text-italic")
            }
            item(key = "underline") {
                DockIcon(MilkIcons.FormatUnderline, "下划线", enabled, items.textStyle.underline,
                    { items.onTextStyle(items.textStyle.copy(underline = !items.textStyle.underline)) }, "writer-text-underline")
            }
            item(key = "strike") {
                DockIcon(MilkIcons.FormatStrikethrough, "删除线", enabled, items.textStyle.strikethrough,
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
                DockIcon(MilkIcons.Add, "胶带素材库", enabled, false, items.onMaterials, "writer-tape-library")
            }
        }
        WriterMinorKind.READING -> Unit
    }
}

private fun lassoFilterIcon(filter: LassoContent): ImageVector = when (filter) {
    LassoContent.INK -> MilkIcons.InkStroke
    LassoContent.SHAPE -> MilkIcons.Shapes
    LassoContent.IMAGE -> MilkIcons.Image
    LassoContent.TEXT -> MilkIcons.TextBox
    LassoContent.DOODLE -> MilkIcons.PatternBrush
    LassoContent.TAPE -> MilkIcons.WashiTape
    LassoContent.STICKER -> MilkIcons.Sticker
    LassoContent.MEDIA -> MilkIcons.MediaPlay
}

private fun shapeIcon(tool: InkTool): ImageVector = when (tool) {
    InkTool.LINE -> MilkIcons.ShapeLine
    InkTool.DASHED_LINE -> MilkIcons.ShapeDashedLine
    InkTool.ARROW -> MilkIcons.ShapeArrow
    InkTool.RECT -> MilkIcons.ShapeRect
    InkTool.OVAL -> MilkIcons.ShapeOval
    InkTool.TRIANGLE -> MilkIcons.ShapeTriangle
    InkTool.STAR -> MilkIcons.ShapeStar
    else -> MilkIcons.ShapeHexagon
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
    val container by animateColorAsState(
        if (selected) cs.primary.copy(alpha = 0.12f) else Color.Transparent,
        MilkMotion.colorSpec(), label = "minorActionBg",
    )
    val fg by animateColorAsState(
        if (selected) cs.primary else cs.onSurfaceVariant,
        MilkMotion.colorSpec(), label = "minorActionFg",
    )
    Box(
        Modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 36.dp)
            .clip(PillShape)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = label }
            .padding(horizontal = MilkSpace.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MilkText.label,
            color = fg,
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
                        .clip(PillShape)
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
    val shape = RoundedCornerShape(MilkRadius.xs)
    Box(
        Modifier
            .size(40.dp, 36.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = "胶带样式 ${index + 1}" },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(
            Modifier
                .width(28.dp)
                .height(18.dp)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), shape),
        ) {
            drawRect(Color(colors[index]))
            val step = 7.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    Color.White.copy(alpha = 0.65f),
                    Offset(x, size.height),
                    Offset(x + size.height, 0f),
                    strokeWidth = 1.5.dp.toPx(),
                )
                x += step
            }
        }
    }
}
