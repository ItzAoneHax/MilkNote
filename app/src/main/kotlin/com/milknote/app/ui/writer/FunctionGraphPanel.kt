package com.milknote.app.ui.writer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.agent.FunctionGraph
import com.milknote.agent.FunctionGraphSpec
import com.milknote.agent.GraphCrop
import com.milknote.agent.GraphViewport
import com.milknote.app.graph.FunctionGraphRenderer
import com.milknote.app.ui.agent.LatexNativeFormula
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkIcons
import com.milknote.app.ui.common.MilkInlineAction
import com.milknote.app.ui.common.MilkInlineTone
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 视口文本 → 模型(纯逻辑,便于 JVM 单测) */
internal fun parseGraphViewport(values: List<String>): Result<GraphViewport> = runCatching {
    require(values.size == 4) { "需要 4 个视口数值" }
    val nums = values.map { it.trim().toDoubleOrNull() ?: error("视口必须是数字") }
    val vp = GraphViewport(nums[0], nums[1], nums[2], nums[3])
    FunctionGraph.validateViewport(vp)
    vp
}

internal fun formatGraphViewportValue(v: Double): String =
    if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else v.toString()

/**
 * 函数图像用户面板:1–6 个函数、格式化输入、数学符号键盘、颜色/视口、实时预览与框选导出。
 * 公式输入区只显示 LatexNativeFormula 排版;原始 LaTeX 由透明 BasicTextField 承接键盘。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FunctionGraphPanel(
    onDismiss: () -> Unit,
    onInsert: (FunctionGraphSpec, GraphCrop?, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var expressions by remember { mutableStateOf(listOf("x^2")) }
    var colors by remember { mutableStateOf(listOf(FunctionGraph.colorAt(0))) }
    var selected by remember { mutableStateOf(0) }
    var editor by remember { mutableStateOf(FormulaInputState.of("x^2")) }
    var viewportText by remember {
        mutableStateOf(
            listOf(
                formatGraphViewportValue(GraphViewport().xMin),
                formatGraphViewportValue(GraphViewport().xMax),
                formatGraphViewportValue(GraphViewport().yMin),
                formatGraphViewportValue(GraphViewport().yMax),
            ),
        )
    }
    var buildError by remember { mutableStateOf<String?>(null) }
    var viewportError by remember { mutableStateOf<String?>(null) }
    var spec by remember { mutableStateOf<FunctionGraphSpec?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var crop by remember { mutableStateOf<GraphCrop?>(null) }
    var cropMode by remember { mutableStateOf(false) }
    var activeCrop by remember { mutableStateOf<GraphCrop?>(null) }
    var dragStart by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var inserting by remember { mutableStateOf(false) }

    fun select(index: Int) {
        val safe = index.coerceIn(0, expressions.lastIndex)
        selected = safe
        editor = FormulaInputState.of(expressions[safe])
    }

    fun setEditor(next: FormulaInputState) {
        if (selected !in expressions.indices) return
        editor = next
        expressions = expressions.toMutableList().also { it[selected] = next.latex }
    }

    fun addExpression() {
        if (expressions.size >= FunctionGraph.MAX_CURVES) return
        expressions = expressions + "x"
        colors = colors + FunctionGraph.colorAt(expressions.lastIndex)
        select(expressions.lastIndex)
    }

    fun removeAt(index: Int) {
        if (expressions.size <= FunctionGraph.MIN_CURVES) return
        if (index !in expressions.indices) return
        val wasSelected = selected
        expressions = expressions.toMutableList().also { it.removeAt(index) }
        colors = colors.toMutableList().also { it.removeAt(index) }
        val nextSelected = when {
            wasSelected == index -> index.coerceAtMost(expressions.lastIndex)
            wasSelected > index -> wasSelected - 1
            else -> wasSelected
        }
        select(nextSelected)
    }

    LaunchedEffect(expressions, colors, viewportText) {
        val vp = parseGraphViewport(viewportText).getOrElse {
            viewportError = it.message ?: "视口范围不合法"
            return@LaunchedEffect
        }
        viewportError = null
        val built = runCatching { FunctionGraph.build(expressions, colors, vp) }
        if (built.isFailure) {
            buildError = built.exceptionOrNull()?.message ?: "函数表达式不合法"
            return@LaunchedEffect
        }
        buildError = null
        val value = built.getOrThrow()
        spec = value
        delay(120)
        preview = withContext(Dispatchers.Default) {
            FunctionGraphRenderer.render(value, 720, 480)
        }
    }

    fun doInsert(cropToUse: GraphCrop?) {
        val vp = parseGraphViewport(viewportText).getOrElse {
            viewportError = it.message ?: "视口范围不合法"
            return
        }
        val current = runCatching { FunctionGraph.build(expressions, colors, vp) }.getOrElse {
            buildError = it.message ?: "函数表达式不合法"
            return
        }
        if (cropToUse != null) {
            runCatching { FunctionGraph.validateCrop(cropToUse) }.onFailure {
                buildError = it.message ?: "框选区域太小"
                return
            }
        }
        inserting = true
        onInsert(current, cropToUse) { ok ->
            inserting = false
            if (ok) onDismiss()
        }
    }

    MilkCenterPanel(
        title = "函数图像",
        subtitle = "1–6 条曲线 · 普通算式或 LaTeX",
        onDismiss = onDismiss,
        modifier = modifier.testTag("function-graph-panel"),
        maxWidth = 780.dp,
        maxHeight = 780.dp,
        footer = {
            if (crop != null) {
                MilkInlineAction(
                    label = "插入整图",
                    onClick = { doInsert(null) },
                    enabled = spec != null && buildError == null && !inserting,
                    tone = MilkInlineTone.NEUTRAL,
                )
                Spacer(Modifier.width(MilkSpace.sm))
            }
            Spacer(Modifier.weight(1f))
            MilkButton(
                label = "取消",
                onClick = onDismiss,
                variant = MilkButtonVariant.SECONDARY,
            )
            Spacer(Modifier.width(MilkSpace.sm))
            MilkButton(
                label = if (crop != null) "将选区加入笔记" else "将图像加入笔记",
                onClick = { doInsert(crop) },
                enabled = spec != null && buildError == null && viewportError == null && !inserting,
                busy = inserting,
                modifier = Modifier.testTag("function-graph-insert"),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MilkSpace.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MilkSectionLabel("函数", Modifier.weight(1f))
                MilkInlineAction(
                    label = "添加",
                    onClick = ::addExpression,
                    icon = MilkIcons.Add,
                    tone = MilkInlineTone.ACCENT,
                    enabled = expressions.size < FunctionGraph.MAX_CURVES,
                    modifier = Modifier.testTag("function-graph-add"),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                expressions.forEachIndexed { index, raw ->
                    val color = colors.getOrNull(index) ?: FunctionGraph.colorAt(index)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MilkRadius.md))
                            .background(
                                if (index == selected) cs.primary.copy(alpha = 0.08f)
                                else Color.Transparent,
                            )
                            .clickable(role = Role.Button) { select(index) }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("function-graph-row-$index"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(color.toInt())),
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            LatexNativeFormula(
                                latex = raw.ifBlank { "\\square" },
                                displayMode = true,
                                fontSize = 16.sp,
                                color = cs.onSurface,
                            )
                        }
                        if (expressions.size > FunctionGraph.MIN_CURVES) {
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(MilkRadius.sm))
                                    .clickable(role = Role.Button) { removeAt(index) }
                                    .testTag("function-graph-remove-$index"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    MilkIcons.Close,
                                    contentDescription = "删除函数 ${index + 1}",
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }

            MilkSectionLabel("颜色")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FunctionGraph.palette.forEachIndexed { index, color ->
                    val selectedColor = colors.getOrNull(selected) == color
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(color.toInt()))
                            .border(
                                if (selectedColor) 2.dp else 1.dp,
                                if (selectedColor) cs.onSurface else cs.outline,
                                CircleShape,
                            )
                            .clickable(role = Role.RadioButton) {
                                colors = colors.toMutableList().also { it[selected] = color }
                            }
                            .testTag("function-graph-color-$index"),
                    )
                }
            }

            MilkSectionLabel("表达式")
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 66.dp)
                    .clip(RoundedCornerShape(MilkRadius.md))
                    .background(cs.surfaceVariant.copy(alpha = 0.45f))
                    .border(1.dp, cs.outline, RoundedCornerShape(MilkRadius.md))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    LatexNativeFormula(
                        latex = editor.latex.ifBlank { "\\square" },
                        displayMode = true,
                        color = cs.onSurface,
                        fontSize = 19.sp,
                    )
                }
                BasicTextField(
                    value = TextFieldValue(editor.latex, TextRange(editor.cursor)),
                    onValueChange = { value ->
                        setEditor(FormulaInputEditor.onFieldChange(editor, value.text, value.selection.start))
                    },
                    textStyle = TextStyle(color = Color.Transparent, fontSize = 19.sp),
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .matchParentSize()
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = "函数表达式输入" }
                        .testTag("function-graph-input"),
                )
            }
            Text(
                "输入字母/数字/运算符,或用下方符号;按 / 生成分式",
                style = MilkText.caption,
                color = cs.onSurfaceVariant,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                @Composable
                fun key(label: String, tag: String, action: () -> Unit) {
                    MilkChip(
                        label = label,
                        selected = false,
                        onClick = action,
                        small = true,
                        role = Role.Button,
                        modifier = Modifier.testTag(tag),
                    )
                }
                key("a/b", "function-graph-key-fraction") {
                    setEditor(FormulaInputEditor.insertTemplate(editor, FormulaTemplate.FRACTION))
                }
                key("√", "function-graph-key-sqrt") {
                    setEditor(FormulaInputEditor.insertTemplate(editor, FormulaTemplate.SQRT))
                }
                key("x²", "function-graph-key-square") {
                    setEditor(FormulaInputEditor.insertTemplate(editor, FormulaTemplate.SQUARE))
                }
                key("π", "function-graph-key-pi") {
                    setEditor(FormulaInputEditor.insertTemplate(editor, FormulaTemplate.PI))
                }
                key("( )", "function-graph-key-parens") {
                    setEditor(FormulaInputEditor.insertTemplate(editor, FormulaTemplate.PARENS))
                }
                key("sin", "function-graph-key-sin") {
                    setEditor(FormulaInputEditor.insertTemplate(editor, FormulaTemplate.SIN))
                }
                key("cos", "function-graph-key-cos") {
                    setEditor(FormulaInputEditor.insertTemplate(editor, FormulaTemplate.COS))
                }
                key("tan", "function-graph-key-tan") {
                    setEditor(FormulaInputEditor.insertTemplate(editor, FormulaTemplate.TAN))
                }
                key("ln", "function-graph-key-ln") {
                    setEditor(FormulaInputEditor.insertTemplate(editor, FormulaTemplate.LN))
                }
                key("log", "function-graph-key-log") {
                    setEditor(FormulaInputEditor.insertTemplate(editor, FormulaTemplate.LOG))
                }
                key("|x|", "function-graph-key-abs") {
                    setEditor(FormulaInputEditor.insertTemplate(editor, FormulaTemplate.ABS))
                }
                key("←", "function-graph-key-left") {
                    setEditor(FormulaInputEditor.moveLeft(editor))
                }
                key("→", "function-graph-key-right") {
                    setEditor(FormulaInputEditor.moveRight(editor))
                }
                key("⌫", "function-graph-key-delete") {
                    setEditor(FormulaInputEditor.backspace(editor))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                MilkSectionLabel("视口", Modifier.weight(1f))
                MilkInlineAction(
                    label = "恢复默认",
                    onClick = {
                        val vp = GraphViewport()
                        viewportText = listOf(
                            formatGraphViewportValue(vp.xMin),
                            formatGraphViewportValue(vp.xMax),
                            formatGraphViewportValue(vp.yMin),
                            formatGraphViewportValue(vp.yMax),
                        )
                    },
                    modifier = Modifier.testTag("function-graph-viewport-reset"),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val labels = listOf("x min", "x max", "y min", "y max")
                labels.forEachIndexed { index, label ->
                    MilkTextField(
                        value = viewportText.getOrElse(index) { "" },
                        onValueChange = { value ->
                            viewportText = viewportText.toMutableList().also { it[index] = value }
                        },
                        label = label,
                        keyboardType = KeyboardType.Text,
                        mono = true,
                        modifier = Modifier
                            .width(126.dp)
                            .testTag("function-graph-viewport-$index"),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                MilkSectionLabel("预览", Modifier.weight(1f))
                MilkChip(
                    label = if (cropMode) "拖拽框选" else "框选",
                    selected = cropMode,
                    onClick = {
                        cropMode = !cropMode
                        if (cropMode) {
                            crop = null
                            activeCrop = null
                        }
                    },
                    small = true,
                    role = Role.Button,
                    modifier = Modifier.testTag("function-graph-crop"),
                )
                if (crop != null) {
                    Spacer(Modifier.width(6.dp))
                    MilkInlineAction(
                        label = "重选",
                        onClick = {
                            cropMode = true
                            crop = null
                            activeCrop = null
                        },
                    )
                    Spacer(Modifier.width(4.dp))
                    MilkInlineAction(
                        label = "取消",
                        onClick = {
                            cropMode = false
                            crop = null
                            activeCrop = null
                        },
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(MilkRadius.md))
                    .background(cs.surface)
                    .border(1.dp, cs.outline, RoundedCornerShape(MilkRadius.md))
                    .onSizeChanged { previewSize = it }
                    .then(
                        if (cropMode) {
                            Modifier.pointerInput(cropMode, previewSize) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val n = offset.toNormalized(previewSize)
                                        if (n != null) {
                                            dragStart = n
                                            activeCrop = GraphCrop.fromDrag(n.first, n.second, n.first, n.second)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val start = dragStart ?: return@detectDragGestures
                                        val n = change.position.toNormalized(previewSize) ?: return@detectDragGestures
                                        activeCrop = GraphCrop.fromDrag(start.first, start.second, n.first, n.second)
                                    },
                                    onDragEnd = {
                                        val candidate = activeCrop
                                        dragStart = null
                                        if (candidate != null &&
                                            candidate.width >= 0.05 && candidate.height >= 0.05
                                        ) {
                                            crop = candidate
                                            cropMode = false
                                        } else {
                                            activeCrop = null
                                        }
                                    },
                                    onDragCancel = {
                                        dragStart = null
                                        activeCrop = null
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                    .testTag("function-graph-preview"),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = preview
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "函数图像预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("正在生成预览…", style = MilkText.caption, color = cs.onSurfaceVariant)
                }
                val shown = activeCrop ?: crop
                if (shown != null) {
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        val left = (shown.left * size.width).toFloat()
                        val top = (shown.top * size.height).toFloat()
                        val right = (shown.right * size.width).toFloat()
                        val bottom = (shown.bottom * size.height).toFloat()
                        val scrim = Color.Black.copy(alpha = 0.18f)
                        drawRect(scrim, Offset(0f, 0f), Size(size.width, top))
                        drawRect(scrim, Offset(0f, bottom), Size(size.width, size.height - bottom))
                        drawRect(scrim, Offset(0f, top), Size(left, bottom - top))
                        drawRect(scrim, Offset(right, top), Size(size.width - right, bottom - top))
                        drawRect(
                            color = cs.primary,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
            if (crop != null) {
                Text(
                    "已选择区域;插入时只保留选区",
                    style = MilkText.caption,
                    color = cs.onSurfaceVariant,
                )
            }

            val error = viewportError ?: buildError
            if (error != null) {
                Text(error, style = MilkText.caption, color = cs.error)
            }
        }
    }
}

private fun Offset.toNormalized(size: IntSize): Pair<Double, Double>? {
    if (size.width <= 0 || size.height <= 0) return null
    val x = (x / size.width.toFloat()).coerceIn(0f, 1f).toDouble()
    val y = (y / size.height.toFloat()).coerceIn(0f, 1f).toDouble()
    return x to y
}
