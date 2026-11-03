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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.milknote.app.ui.common.innerHighlight
import com.milknote.app.ui.common.paintedShadow
import com.milknote.app.ui.common.softShadow
import com.milknote.ink.model.InkTool

@Composable
internal fun TextBoxDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(460.dp)
                .softShadow(14.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .innerHighlight()
                .paintedShadow(RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text("插入文字框", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("创建后可拖动位置，并随页面导出", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxSize(),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("输入文字…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(6.dp))
                TextButton(enabled = text.isNotBlank(), onClick = { onCreate(text.trim()) }) { Text("插入") }
            }
        }
    }
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
                .softShadow(14.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .innerHighlight()
                .paintedShadow(RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text("图形绘制", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(InkTool.RECT to "矩形", InkTool.OVAL to "椭圆", InkTool.LINE to "直线").forEach { (candidate, label) ->
                    val active = tool == candidate
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .border(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable { tool = candidate }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("轮廓粗细", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text("%.2f mm".format(width), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            Slider(value = width, onValueChange = { width = it }, valueRange = 0.2f..3f)
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
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { onStart(ShapeConfig(tool, color, width)) }) { Text("开始绘制") }
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
                val paintColor = Color(config.color.toInt())
                val stroke = Stroke(config.widthMm * 5.67f)
                when (config.tool) {
                    InkTool.RECT -> drawRect(paintColor, rect.topLeft, rect.size, style = stroke)
                    InkTool.OVAL -> drawOval(paintColor, rect.topLeft, rect.size, style = stroke)
                    InkTool.LINE -> drawLine(paintColor, a, b, strokeWidth = config.widthMm * 5.67f)
                    else -> Unit
                }
            }
        }
        Text(
            "拖动绘制${when (config.tool) { InkTool.RECT -> "矩形"; InkTool.OVAL -> "椭圆"; else -> "直线" }}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 7.dp),
        )
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopEnd)) { Text("退出图形") }
    }
}
