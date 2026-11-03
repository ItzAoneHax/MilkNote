package com.milknote.app.ui.pen

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.innerHighlight
import com.milknote.app.ui.common.paintedShadow
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.Mono
import com.milknote.device.pen.DeviceInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 笔诊断面板(PEN-ADAPTATION §3.5 / §7-P1 真机验证数据源):
 * 设备与能力探测(留档)+ 30s 压力直方图 + 按键/事件实时流水(一键复制/导出)。
 * 真机按 §7 表格逐条操作,导出回填文档。
 */
@Composable
fun PenDiagnosticsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val port = ServiceLocator.penPort
    val caps by port.capabilities.collectAsState()
    val entries by port.eventLog.entries.collectAsState()
    val stats by port.histogram.stats.collectAsState()
    val battery by ServiceLocator.penBattery.level.collectAsState()
    val batterySource by ServiceLocator.penBattery.sourceName.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        // 事件流水逐条到达:直接贴底(不逐条动画滚动,A-20)
        if (entries.isNotEmpty()) listState.scrollToItem(entries.size - 1)
    }

    fun reportText(): String = buildString {
        appendLine("MilkNote 笔诊断 ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine(DeviceInfo.summary(context))
        appendLine(DeviceInfo.batteryLine(batterySource, battery))
        val c = caps
        if (c != null) {
            appendLine("pen device=${c.deviceName} id=${c.deviceId}")
            appendLine("pen pressure=${c.pressure} tilt=${c.tilt} hover=${c.hover} buttons=${c.stylusButtonCount}")
        } else {
            appendLine("pen device=未探测到笔事件")
        }
        val s = stats
        appendLine(
            if (s != null) "pressure 30s n=${s.count} min=${"%.3f".format(s.min)} p50=${"%.3f".format(s.p50)} max=${"%.3f".format(s.max)}"
            else "pressure 30s 无样本"
        )
        appendLine("---- events (${entries.size}) ----")
        val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        for (e in entries) appendLine("${timeFmt.format(Date(e.timeMs))}  ${e.text}")
    }

    fun export() {
        val text = reportText()
        val uri = runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, "milknote-pen-log-${System.currentTimeMillis() % 100000}.txt")
                put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/MilkNote")
            }
            context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)?.also { u ->
                context.contentResolver.openOutputStream(u)?.use { it.write(text.toByteArray()) }
            }
        }.getOrNull()
        port.eventLog.log(if (uri != null) "EXPORT  → 文档/MilkNote/*.txt" else "EXPORT  失败")
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .softShadow(12.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                .innerHighlight()
                .paintedShadow(RoundedCornerShape(18.dp))
                .padding(20.dp),
        ) {
            Text("笔诊断", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "P1 验证:逐键 单击/双击/长按/按住书写,记录事件流水",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            // ---- 设备信息(留档) ----
            val c = caps
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp),
            ) {
                Mono(DeviceInfo.summary(context))
                Spacer(Modifier.height(4.dp))
                Mono(DeviceInfo.batteryLine(batterySource, battery))
                if (c != null) {
                    Mono("device=${c.deviceName} id=${c.deviceId}")
                    Mono("pressure=${c.pressure}  tilt=${c.tilt}  hover=${c.hover}  buttons=${c.stylusButtonCount}")
                } else {
                    Mono("尚无笔事件(用笔书写/悬停/按键后此处出现探测结果)")
                }
            }
            Spacer(Modifier.height(10.dp))

            // ---- 30s 压力直方图(§3.5 / §7-P2) ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("压力 30s", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { port.histogram.reset() }) {
                        Text("重置", style = MaterialTheme.typography.labelMedium)
                    }
                }
                val s = stats
                if (s != null) {
                    Mono("n=${s.count}   min=${"%.3f".format(s.min)}   p50=${"%.3f".format(s.p50)}   max=${"%.3f".format(s.max)}")
                    Spacer(Modifier.height(6.dp))
                    PressureBars(s.min, s.p50, s.max)
                } else {
                    Mono("无样本——用笔书写 30 秒后查看")
                }
            }
            Spacer(Modifier.height(10.dp))

            // ---- 事件流水 ----
            Text("事件流水", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(10.dp))
                    // 终端语义(A-01):不随主题反转的固定深底——暗色下不得复用 onBackground(浅色)
                    .background(com.milknote.app.ui.theme.ConsoleBg)
                    .padding(10.dp),
            ) {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    items(entries) { e ->
                        Text(
                            "${timeFmt.format(Date(e.timeMs))}  ${e.text}",
                            fontFamily = Mono,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = when {
                                e.text.startsWith("TRIGGER") -> com.milknote.app.ui.theme.ConsoleAccent
                                e.text.startsWith("PALM") -> com.milknote.app.ui.theme.ConsoleWarn
                                e.text.startsWith("EXPORT") -> com.milknote.app.ui.theme.ConsoleExport
                                e.text.startsWith("KEY") || e.text.startsWith("BTN") -> com.milknote.app.ui.theme.ConsoleSuccess
                                else -> com.milknote.app.ui.theme.ConsoleText
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("milknote-pen-log", reportText()))
                    port.eventLog.log("COPY  流水已复制(${entries.size} 条)")
                }) { Text("复制") }
                TextButton(onClick = { export() }) { Text("导出") }
                TextButton(onClick = { port.eventLog.clear() }) { Text("清空") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}

/** min/p50/max 三条横条(0–1 归一;诊断可读性优先,非精确图形) */
@Composable
private fun PressureBars(min: Float, p50: Float, max: Float) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)   // 可见灰(outline 在奶白底上≈隐形)
    listOf("min" to min, "p50" to p50, "max" to max).forEach { (label, v) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(30.dp))
            Box(
                Modifier
                    .height(6.dp)
                    .fillMaxWidth(v.coerceIn(0.003f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (label == "p50") accent else track),
            ) {}
        }
        Spacer(Modifier.height(3.dp))
    }
}

@Composable
private fun Mono(text: String) {
    // 诊断数据是 P1 留档依据,用 onSurface(全对比)而非 onSurfaceVariant
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface)
}
