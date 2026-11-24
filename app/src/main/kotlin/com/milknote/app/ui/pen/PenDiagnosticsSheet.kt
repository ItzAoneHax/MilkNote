package com.milknote.app.ui.pen

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonSize
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.settings.TextAction
import com.milknote.app.ui.theme.ConsoleAccent
import com.milknote.app.ui.theme.ConsoleBg
import com.milknote.app.ui.theme.ConsoleExport
import com.milknote.app.ui.theme.ConsoleSuccess
import com.milknote.app.ui.theme.ConsoleText
import com.milknote.app.ui.theme.ConsoleWarn
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.device.pen.DeviceInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 笔诊断面板(PEN-ADAPTATION §3.5 / §7-P1 真机验证数据源):
 * 设备与能力探测(留档)+ 30s 压力直方图 + 按键/事件实时流水(一键复制/导出)。
 * 真机按 §7 表格逐条操作,导出回填文档。
 * 骨架走共享 MilkCenterPanel:标题/副题/关闭 + 可滚动内容 + 底部动作条(图标与文字同属一个按钮)。
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

    fun copyLog() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("milknote-pen-log", reportText()))
        port.eventLog.log("COPY  流水已复制(${entries.size} 条)")
    }

    MilkCenterPanel(
        title = "笔诊断",
        subtitle = "逐键单击 / 双击 / 长按 / 按住书写，实时记录事件流水",
        onDismiss = onDismiss,
        maxWidth = 640.dp,
        maxHeight = 720.dp,
        footer = {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MilkSpace.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MilkButton(
                    label = "复制",
                    onClick = { copyLog() },
                    variant = MilkButtonVariant.TEXT,
                    size = MilkButtonSize.SMALL,
                    leading = {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                )
                MilkButton(
                    label = "导出",
                    onClick = { export() },
                    variant = MilkButtonVariant.TEXT,
                    size = MilkButtonSize.SMALL,
                    leading = {
                        Icon(
                            Icons.Outlined.IosShare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                )
                MilkButton(
                    label = "清空",
                    onClick = { port.eventLog.clear() },
                    variant = MilkButtonVariant.TEXT,
                    size = MilkButtonSize.SMALL,
                    leading = {
                        Icon(
                            Icons.Outlined.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                )
            }
            Spacer(Modifier.width(MilkSpace.sm))
            MilkButton(
                label = "关闭",
                onClick = onDismiss,
                variant = MilkButtonVariant.SECONDARY,
                size = MilkButtonSize.SMALL,
            )
        },
    ) {
        // 内容区整体可滚动:矮屏/大字体下也不会裁掉底部动作条
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- 设备信息(留档) ----
            MilkSectionLabel("设备")
            Spacer(Modifier.height(MilkSpace.sm))
            val c = caps
            InfoBlock {
                Mono(DeviceInfo.summary(context))
                Spacer(Modifier.height(3.dp))
                Mono(DeviceInfo.batteryLine(batterySource, battery))
                if (c != null) {
                    Mono("device=${c.deviceName} id=${c.deviceId}")
                    Mono("pressure=${c.pressure}  tilt=${c.tilt}  hover=${c.hover}  buttons=${c.stylusButtonCount}")
                } else {
                    Mono("尚无笔事件(用笔书写 / 悬停 / 按键后此处出现探测结果)")
                }
            }
            Spacer(Modifier.height(MilkSpace.lg))

            // ---- 30s 压力直方图(§3.5 / §7-P2) ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                MilkSectionLabel("压力 30s", Modifier.weight(1f))
                TextAction("重置", onClick = { port.histogram.reset() })
            }
            Spacer(Modifier.height(MilkSpace.sm))
            InfoBlock {
                val s = stats
                if (s != null) {
                    Mono("n=${s.count}   min=${"%.3f".format(s.min)}   p50=${"%.3f".format(s.p50)}   max=${"%.3f".format(s.max)}")
                    Spacer(Modifier.height(MilkSpace.sm))
                    PressureBars(s.min, s.p50, s.max)
                } else {
                    Mono("无样本——用笔书写 30 秒后查看")
                }
            }
            Spacer(Modifier.height(MilkSpace.lg))

            // ---- 事件流水 ----
            MilkSectionLabel("事件流水", description = "${entries.size} 条")
            Spacer(Modifier.height(MilkSpace.sm))
            val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(MilkRadius.md))
                    // 终端语义(A-01):不随主题反转的固定深底——暗色下不得复用 onBackground(浅色)
                    .background(ConsoleBg)
                    .padding(MilkSpace.md),
            ) {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    items(entries) { e ->
                        Text(
                            "${timeFmt.format(Date(e.timeMs))}  ${e.text}",
                            style = MilkText.monoMicro,
                            color = when {
                                e.text.startsWith("TRIGGER") -> ConsoleAccent
                                e.text.startsWith("PALM") -> ConsoleWarn
                                e.text.startsWith("EXPORT") -> ConsoleExport
                                e.text.startsWith("KEY") || e.text.startsWith("BTN") -> ConsoleSuccess
                                else -> ConsoleText
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(MilkSpace.md))
        }
    }
}

/** 信息块:淡灰圆角容器,内放 mono 行 */
@Composable
private fun InfoBlock(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MilkRadius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(MilkSpace.md),
    ) { content() }
}

/** min/p50/max 三条横条(0–1 归一;诊断可读性优先,非精确图形) */
@Composable
private fun PressureBars(min: Float, p50: Float, max: Float) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)   // 可见灰(outline 在奶白底上≈隐形)
    listOf("min" to min, "p50" to p50, "max" to max).forEach { (label, v) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MilkText.monoMicro,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(30.dp),
            )
            Box(
                Modifier
                    .height(6.dp)
                    .fillMaxWidth(v.coerceIn(0.003f, 1f))
                    .clip(RoundedCornerShape(MilkRadius.xs / 2))
                    .background(if (label == "p50") accent else track),
            ) {}
        }
        Spacer(Modifier.height(3.dp))
    }
}

@Composable
private fun Mono(text: String) {
    // 诊断数据是 P1 留档依据,用 onSurface(全对比)而非 onSurfaceVariant
    Text(text, style = MilkText.monoMeta, color = MaterialTheme.colorScheme.onSurface)
}
