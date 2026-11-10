package com.milknote.app.ui.writer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkSlider
import com.milknote.app.ui.common.milkDialogSurface
import com.milknote.app.ui.common.innerHighlight
import com.milknote.app.ui.common.paintedShadow
import com.milknote.app.ui.common.softShadow
import com.milknote.ink.model.CardTextAlign
import com.milknote.ink.model.CardTextStyle
import com.milknote.ink.model.InkTool

@Composable
internal fun TextBoxDialog(
    existing: com.milknote.ink.model.AnnotationCard? = null,
    onDismiss: () -> Unit,
    onCreate: (String, CardTextStyle) -> Unit,
) {
    var text by remember(existing?.id) { mutableStateOf(existing?.text.orEmpty()) }
    var style by remember(existing?.id) { mutableStateOf(existing?.textStyle ?: CardTextStyle()) }
    MilkCenterPanel(title = if (existing == null) "插入文字框" else "编辑文字", onDismiss = onDismiss, maxWidth = 520.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(14.dp),
            ) {
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxSize(),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("输入文字…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("字号", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(16f, 24f, 36f, 48f).forEach { size ->
                    TextStyleChip("${size.toInt()}", style.fontSizePx == size) { style = style.copy(fontSizePx = size) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextStyleChip("左对齐", style.align == CardTextAlign.START) { style = style.copy(align = CardTextAlign.START) }
                TextStyleChip("居中", style.align == CardTextAlign.CENTER) { style = style.copy(align = CardTextAlign.CENTER) }
                TextStyleChip("右对齐", style.align == CardTextAlign.END) { style = style.copy(align = CardTextAlign.END) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextStyleChip("粗体", style.bold) { style = style.copy(bold = !style.bold) }
                TextStyleChip("斜体", style.italic) { style = style.copy(italic = !style.italic) }
                TextStyleChip("下划线", style.underline) { style = style.copy(underline = !style.underline) }
                TextStyleChip("删除线", style.strikethrough) { style = style.copy(strikethrough = !style.strikethrough) }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                listOf(0xFF23221E, 0xFF3B5BEC, 0xFFD2483C, 0xFF2E9E6B).forEach { color ->
                    InkColorDot(color = color, label = "文字颜色", active = style.colorArgb == color,
                        onClick = { style = style.copy(colorArgb = color) })
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            MilkButton(label = "取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(8.dp))
            MilkButton(label = if (existing == null) "插入" else "保存", enabled = text.isNotBlank(), onClick = { onCreate(text.trim(), style) })
        }
    }
}

@Composable
private fun TextStyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick).defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) { Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
}

// public:WriterViewModel.addShape 为 public 函数,参数类型不可 internal(可见性暴露)
data class ShapeConfig(val tool: InkTool, val color: Long, val widthMm: Float)

@Composable
internal fun ShapeSettingsDialog(
    initialColor: Long,
    initialWidth: Float,
    onDismiss: () -> Unit,
    onStart: (ShapeConfig) -> Unit,
) {
    var tool by remember { mutableStateOf(InkTool.RECT) }
    var color by remember { mutableStateOf(initialColor) }
    var width by remember { mutableStateOf(initialWidth) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(430.dp)
                .milkDialogSurface()
                .padding(24.dp),
        ) {
            Text("图形绘制", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            val shapes = listOf(
                InkTool.LINE to "直线", InkTool.DASHED_LINE to "虚线", InkTool.ARROW to "箭头", InkTool.RECT to "矩形",
                InkTool.OVAL to "椭圆", InkTool.TRIANGLE to "三角形", InkTool.STAR to "星形", InkTool.POLYGON to "六边形",
            )
            shapes.chunked(4).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    group.forEach { (candidate, label) ->
                        val active = tool == candidate
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .border(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable { tool = candidate }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("轮廓粗细", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text("%.2f mm".format(width), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            MilkSlider(value = width, onValueChange = { width = it }, valueRange = 0.2f..3f)
            Spacer(Modifier.height(8.dp))
            Text("颜色", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row {
                listOf(0xFF23221E, 0xFF3B5BEC, 0xFFD2483C, 0xFF2E9E6B).forEach { candidate ->
                    InkColorDot(
                        color = candidate,
                        label = "图形颜色",
                        active = color == candidate,
                        onClick = { color = candidate },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MilkButton(label = "取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
                Spacer(Modifier.width(8.dp))
                MilkButton(label = "开始绘制", onClick = { onStart(ShapeConfig(tool, color, width)) })
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
        Text(
            "拖动绘制${when (config.tool) {
                InkTool.RECT -> "矩形"; InkTool.OVAL -> "椭圆"; InkTool.DASHED_LINE -> "虚线"
                InkTool.ARROW -> "箭头"; InkTool.TRIANGLE -> "三角形"; InkTool.STAR -> "星形"
                InkTool.POLYGON -> "六边形"; else -> "直线"
            }}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 7.dp),
        )
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopEnd)) { Text("退出图形") }
    }
}
