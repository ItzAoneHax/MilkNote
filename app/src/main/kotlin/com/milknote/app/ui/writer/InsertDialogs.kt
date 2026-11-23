package com.milknote.app.ui.writer

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkKeyCap
import com.milknote.app.ui.common.MilkPanelHeader
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.MilkSlider
import com.milknote.app.ui.common.softShadow
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
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .testTag("writer-panel-scroll"),
        ) {
            Spacer(Modifier.height(12.dp))
            MilkSectionLabel("内容")
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 136.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), MaterialTheme.shapes.medium)
                    .padding(14.dp),
            ) {
                if (text.isEmpty()) {
                    Text("输入文字…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))
            MilkSectionLabel("字号")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(16f, 24f, 36f, 48f).forEach { size ->
                    MilkChip(label = "${size.toInt()}", selected = style.fontSizePx == size, onClick = { style = style.copy(fontSizePx = size) })
                }
                Spacer(Modifier.weight(1f))
                Text("px", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
            }
            Spacer(Modifier.height(18.dp))
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
            Spacer(Modifier.height(18.dp))
            MilkSectionLabel("样式")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MilkChip("粗体", style.bold, { style = style.copy(bold = !style.bold) })
                MilkChip("斜体", style.italic, { style = style.copy(italic = !style.italic) })
                MilkChip("下划线", style.underline, { style = style.copy(underline = !style.underline) })
                MilkChip("删除线", style.strikethrough, { style = style.copy(strikethrough = !style.strikethrough) })
            }
            Spacer(Modifier.height(18.dp))
            MilkSectionLabel("颜色")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(0xFF23221E, 0xFF3B5BEC, 0xFFD2483C, 0xFF2E9E6B).forEach { color ->
                    InkColorDot(
                        color = color,
                        label = "文字颜色",
                        active = style.colorArgb == color,
                        onClick = { style = style.copy(colorArgb = color) },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            MilkButton(label = "取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(10.dp))
            MilkButton(label = if (existing == null) "插入" else "保存", enabled = text.isNotBlank(), onClick = { onCreate(text.trim(), style) })
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
                Spacer(Modifier.height(10.dp))
                MilkSectionLabel("图形")
                Spacer(Modifier.height(10.dp))
                writerShapeToolOrder.chunked(4).forEach { group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.forEach { (candidate, label) ->
                            val active = tool == candidate
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .border(1.dp, if (active) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { tool = candidate })
                                    .height(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (active) FontWeight.Medium else null,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        // 不足 4 个时补空位,保持列宽一致
                        repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("轮廓粗细", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Text("%.2f mm".format(width), style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.milknote.app.ui.theme.Mono), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                MilkSlider(value = width, onValueChange = { width = it }, valueRange = 0.2f..3f)
                Spacer(Modifier.height(20.dp))
                MilkSectionLabel("颜色")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(0xFF23221E, 0xFF3B5BEC, 0xFFD2483C, 0xFF2E9E6B).forEach { candidate ->
                        InkColorDot(color = candidate, label = "图形颜色", active = color == candidate, onClick = { color = candidate })
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    MilkKeyCap("Esc")
                    Spacer(Modifier.width(6.dp))
                    Text("关闭", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    MilkButton(label = "取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
                    Spacer(Modifier.width(10.dp))
                    MilkButton(label = "开始绘制", onClick = { onStart(ShapeConfig(tool, color, width)) })
                }
                Spacer(Modifier.height(20.dp))
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
                .padding(top = 14.dp)
                .softShadow(6.dp, RoundedCornerShape(999.dp))
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "在纸上拖动绘制${when (config.tool) {
                    InkTool.RECT -> "矩形"; InkTool.OVAL -> "椭圆"; InkTool.DASHED_LINE -> "虚线"
                    InkTool.ARROW -> "箭头"; InkTool.TRIANGLE -> "三角形"; InkTool.STAR -> "星形"
                    InkTool.POLYGON -> "六边形"; else -> "直线"
                }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(6.dp))
            MilkIconButton(Icons.Outlined.Close, "退出图形", onClick = onCancel, iconSize = 18.dp)
        }
    }
}
