package com.milknote.app.ui.writer

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkFilterChip
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkPanelHeader
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.MilkSlider
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.PillShape
import com.milknote.ink.model.CardTextAlign
import com.milknote.ink.model.CardTextStyle
import com.milknote.ink.model.InkTool

/** 文字框编辑面板:内容 → 字号 → 对齐 → 样式 → 颜色,眉标分区 */
@Composable
internal fun TextBoxDialog(
    existing: com.milknote.ink.model.AnnotationCard? = null,
    initialStyle: CardTextStyle = CardTextStyle(),
    onDismiss: () -> Unit,
    onCreate: (String, CardTextStyle) -> Unit,
) {
    var text by remember(existing?.id) { mutableStateOf(existing?.text.orEmpty()) }
    var style by remember(existing?.id) { mutableStateOf(existing?.textStyle ?: initialStyle) }
    MilkCenterPanel(
        title = if (existing == null) "插入文字框" else "编辑文字",
        subtitle = "输入内容并选择样式；插入后可在纸上拖动、缩放",
        onDismiss = onDismiss,
        maxWidth = 540.dp,
        footer = {
            Spacer(Modifier.weight(1f))
            MilkButton(label = "取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(MilkSpace.sm))
            MilkButton(
                label = if (existing == null) "插入" else "保存",
                enabled = text.isNotBlank(),
                onClick = { onCreate(text.trim(), style) },
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .testTag("writer-panel-scroll"),
        ) {
            Spacer(Modifier.height(MilkSpace.md))
            MilkSectionLabel("内容")
            Spacer(Modifier.height(MilkSpace.sm))
            MilkTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "输入文字…",
                singleLine = false,
                minLines = 5,
                mono = false,
                textStyle = MilkText.body,
            )
            Spacer(Modifier.height(MilkSpace.xl))
            MilkSectionLabel("字号")
            Spacer(Modifier.height(MilkSpace.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs)) {
                listOf(16f, 24f, 36f, 48f).forEach { size ->
                    MilkChip(label = "${size.toInt()}", selected = style.fontSizePx == size, onClick = { style = style.copy(fontSizePx = size) })
                }
                Spacer(Modifier.weight(1f))
                Text("px", style = MilkText.monoMeta, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = MilkSpace.xxs))
            }
            Spacer(Modifier.height(MilkSpace.lg + 2.dp))
            MilkSectionLabel("对齐")
            Spacer(Modifier.height(8.dp))
            MilkSegmented(
                options = listOf(CardTextAlign.START, CardTextAlign.CENTER, CardTextAlign.END),
                selected = style.align,
                label = {
                    when (it) {
                        CardTextAlign.START -> "左对齐"
                        CardTextAlign.CENTER -> "居中"
                        CardTextAlign.END -> "右对齐"
                    }
                },
                onSelect = { style = style.copy(align = it) },
            )
            Spacer(Modifier.height(MilkSpace.lg + 2.dp))
            MilkSectionLabel("样式")
            Spacer(Modifier.height(MilkSpace.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
                verticalArrangement = Arrangement.spacedBy(MilkSpace.xs),
            ) {
                MilkFilterChip("粗体", style.bold, { style = style.copy(bold = !style.bold) })
                MilkFilterChip("斜体", style.italic, { style = style.copy(italic = !style.italic) })
                MilkFilterChip("下划线", style.underline, { style = style.copy(underline = !style.underline) })
                MilkFilterChip("删除线", style.strikethrough, { style = style.copy(strikethrough = !style.strikethrough) })
            }
            Spacer(Modifier.height(MilkSpace.lg + 2.dp))
            MilkSectionLabel("颜色")
            Spacer(Modifier.height(MilkSpace.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(0xFF18191B, 0xFF2563EB, 0xFFD94436, 0xFF23865F).forEach { color ->
                    InkColorDot(
                        color = color,
                        label = "文字颜色",
                        active = style.colorArgb == color,
                        onClick = { style = style.copy(colorArgb = color) },
                    )
                }
            }
        }
    }
}

// public:WriterViewModel.addShape 为 public 函数,参数类型不可 internal(可见性暴露)
data class ShapeConfig(val tool: InkTool, val color: Long, val widthMm: Float)

/** 图形面板:图形网格 → 轮廓粗细 → 颜色 */
@Composable
internal fun ShapeSettingsDialog(
    initialTool: InkTool = InkTool.RECT,
    initialColor: Long,
    initialWidth: Float,
    onDismiss: () -> Unit,
    onStart: (ShapeConfig) -> Unit,
) {
    var tool by remember { mutableStateOf(initialTool) }
    var color by remember { mutableStateOf(initialColor) }
    var width by remember { mutableStateOf(initialWidth) }
    WriterFloatingPanel(onDismiss = onDismiss, width = 440.dp, maxHeight = 620.dp) {
        Column(Modifier.fillMaxWidth()) {
            MilkPanelHeader(title = "图形", subtitle = "选择图形、描边与颜色，然后在纸上拖动绘制", onDismiss = onDismiss)
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .testTag("writer-panel-scroll")
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(MilkSpace.sm + 2.dp))
                MilkSectionLabel("图形")
                Spacer(Modifier.height(MilkSpace.sm + 2.dp))
                writerShapeToolOrder.chunked(4).forEach { group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MilkSpace.sm)) {
                        group.forEach { (candidate, label) ->
                            val active = tool == candidate
                            val container by animateColorAsState(
                                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                MilkMotion.colorSpec(), label = "shapeCardBg",
                            )
                            val borderColor by animateColorAsState(
                                if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                MilkMotion.colorSpec(), label = "shapeCardBorder",
                            )
                            val fg by animateColorAsState(
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                MilkMotion.colorSpec(), label = "shapeCardFg",
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(MilkRadius.md))
                                    .background(container)
                                    .border(1.dp, borderColor, RoundedCornerShape(MilkRadius.md))
                                    .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { tool = candidate })
                                    .height(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    style = if (active) MilkText.bodyMedium else MilkText.body,
                                    color = fg,
                                )
                            }
                        }
                        // 不足 4 个时补空位,保持列宽一致
                        repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(MilkSpace.sm))
                }
                Spacer(Modifier.height(MilkSpace.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("轮廓粗细", style = MilkText.label, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Text("%.2f mm".format(width), style = MilkText.monoMeta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(MilkSpace.sm))
                MilkSlider(value = width, onValueChange = { width = it }, valueRange = 0.2f..3f)
                Spacer(Modifier.height(MilkSpace.xl))
                MilkSectionLabel("颜色")
                Spacer(Modifier.height(MilkSpace.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(0xFF18191B, 0xFF2563EB, 0xFFD94436, 0xFF2E9E6B).forEach { candidate ->
                        InkColorDot(color = candidate, label = "图形颜色", active = color == candidate, onClick = { color = candidate })
                    }
                }
                Spacer(Modifier.height(MilkSpace.xxl))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    MilkButton(label = "取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
                    Spacer(Modifier.width(MilkSpace.sm + 2.dp))
                    MilkButton(label = "开始绘制", onClick = { onStart(ShapeConfig(tool, color, width)) })
                }
                Spacer(Modifier.height(MilkSpace.xl))
            }
        }
    }
}

@Composable
internal fun ShapeOverlay(config: ShapeConfig, onComplete: (Offset, Offset) -> Unit, onCancel: () -> Unit) {
    var start by remember(config) { mutableStateOf<Offset?>(null) }
    var end by remember(config) { mutableStateOf<Offset?>(null) }
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(config) {
                    detectDragGestures(
                        onDragStart = { start = it; end = it },
                        onDrag = { change, _ -> end = change.position },
                        onDragEnd = {
                            val a = start; val b = end
                            if (a != null && b != null) onComplete(a, b)
                            start = null; end = null
                        },
                        onDragCancel = { start = null; end = null },
                    )
                },
        ) {
            val a = start; val b = end
            if (a != null && b != null) {
                val rect = Rect(a, b)
                val previewStroke = com.milknote.ink.model.Stroke(
                    id = "preview", tool = config.tool, color = config.color, baseWidthMm = config.widthMm,
                    points = listOf(
                        com.milknote.ink.model.InkPoint(a.x, a.y, 1f, 0f, 0f, 0L),
                        com.milknote.ink.model.InkPoint(b.x, b.y, 1f, 0f, 0f, 1L),
                    ),
                )
                val path = com.milknote.ink.render.StrokeRenderer.buildPath(
                    previewStroke, 5.67f, com.milknote.ink.model.PressureGamma.STANDARD,
                )
                drawContext.canvas.nativeCanvas.drawPath(path, android.graphics.Paint().apply {
                    color = config.color.toInt(); isAntiAlias = true; style = android.graphics.Paint.Style.FILL
                })
            }
        }
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = MilkSpace.md + 2.dp)
                .softShadow(8.dp, PillShape)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, PillShape)
                .padding(start = MilkSpace.lg, end = MilkSpace.xs, top = MilkSpace.xxs, bottom = MilkSpace.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "在纸上拖动绘制${when (config.tool) {
                    InkTool.RECT -> "矩形"; InkTool.OVAL -> "椭圆"; InkTool.DASHED_LINE -> "虚线"
                    InkTool.ARROW -> "箭头"; InkTool.TRIANGLE -> "三角形"; InkTool.STAR -> "星形"
                    InkTool.POLYGON -> "六边形"; else -> "直线"
                }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MilkText.caption,
            )
            Spacer(Modifier.width(MilkSpace.xs))
            MilkIconButton(Icons.Outlined.Close, "退出图形", onClick = onCancel, iconSize = 18.dp)
        }
    }
}
