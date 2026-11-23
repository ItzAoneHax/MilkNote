package com.milknote.app.ui.bookshelf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkColorDot
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.theme.CoverPalette
import com.milknote.app.ui.theme.MonoMeta
import com.milknote.notes.model.PAGE_ORIENTATIONS
import com.milknote.notes.model.PAGE_SIZES_MM
import com.milknote.notes.model.PAPER_COLORS
import com.milknote.notes.model.PAPER_KINDS
import com.milknote.notes.model.PURPOSE_TAGS

/**
 * ② 新建笔记本弹窗(第二代 · DESIGN-V2 dlg-new):左侧 164dp 实时 3:4 实体封面预览(书脊色/纸纹/名称所见即所得),
 * 右侧 名称 / 封面色 / 纸纹 / 尺寸 / 方向 / 纸色 / 用途;「创建 ↵」直接进入第 1 页。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewNotebookDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, coverHex: String, paper: String, purpose: String, pageSize: String, orientation: String, paperColor: String) -> Unit,
    defaultPaper: String = "dot",
    defaultPaperColor: String = "#FFFFFF",
) {
    var name by rememberSaveable { mutableStateOf("") }
    var coverIdx by rememberSaveable { mutableStateOf(0) }
    var paper by rememberSaveable { mutableStateOf(defaultPaper) }
    var paperColor by rememberSaveable { mutableStateOf(defaultPaperColor) }
    var pageSize by rememberSaveable { mutableStateOf("B5") }
    var orientation by rememberSaveable { mutableStateOf("landscape") }
    var purpose by rememberSaveable { mutableStateOf(PURPOSE_TAGS.first()) }

    fun submit() {
        onCreate(name.trim().ifBlank { "未命名笔记" }, CoverPalette[coverIdx].toArgbHex(), paper, purpose, pageSize, orientation, paperColor)
    }

    MilkCenterPanel(title = "新建笔记本", onDismiss = onDismiss, maxWidth = 640.dp, maxHeight = 720.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            LiveBookCover(
                name = name.trim().ifBlank { "未命名" },
                cover = CoverPalette[coverIdx],
                paperColor = paperColor,
                paper = paper,
                modifier = Modifier.width(164.dp),
            )
            Column(Modifier.weight(1f)) {
                MilkTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "笔记本名称",
                    mono = false,
                    leading = {
                        Icon(
                            Icons.Outlined.AutoStories,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    imeAction = ImeAction.Done,
                    onDone = { submit() },
                )
                Spacer(Modifier.height(16.dp))
                MilkSectionLabel("封面色")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    CoverPalette.forEachIndexed { i, c ->
                        MilkColorDot(
                            color = c,
                            selected = i == coverIdx,
                            onClick = { coverIdx = i },
                            contentDescription = "封面色 ${i + 1}",
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                MilkSectionLabel("纸张")
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PAPER_KINDS.forEach { (id, label) ->
                        MilkChip(label = label, selected = paper == id, onClick = { paper = id }, small = true)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        MilkSectionLabel("尺寸")
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PAGE_SIZES_MM.keys.forEach { id ->
                                MilkChip(label = id, selected = pageSize == id, onClick = { pageSize = id }, small = true)
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1.25f)) {
                        MilkSectionLabel("方向")
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PAGE_ORIENTATIONS.forEach { (id, label) ->
                                MilkChip(label = label, selected = orientation == id, onClick = { orientation = id }, small = true)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                MilkSectionLabel("纸色", description = PAPER_COLORS.firstOrNull { it.first.equals(paperColor, ignoreCase = true) }?.second)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    PAPER_COLORS.forEach { (hex, _) ->
                        MilkColorDot(
                            color = Color(android.graphics.Color.parseColor(hex)),
                            selected = paperColor.equals(hex, ignoreCase = true),
                            onClick = { paperColor = hex },
                            contentDescription = "纸色",
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                MilkSectionLabel("用途")
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PURPOSE_TAGS.forEach { tag ->
                        MilkChip(label = tag, selected = purpose == tag, onClick = { purpose = tag }, small = true)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            MilkButton(label = "取消", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
            Spacer(Modifier.width(8.dp))
            MilkButton(label = "创建 ↵", onClick = { submit() })
        }
    }
}

/**
 * 实时预览:与书架同款的 3:4 竖向实体书本(纸色底 + 纸纹 + 11dp 封面色书脊 + 名称 + 淡墨曲线)。
 */
@Composable
private fun LiveBookCover(
    name: String,
    cover: Color,
    paperColor: String,
    paper: String,
    modifier: Modifier = Modifier,
) {
    val sheet = runCatching { Color(android.graphics.Color.parseColor(paperColor)) }.getOrDefault(Color.White)
    val bookShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(0.76f)
            .clip(bookShape)
            .background(sheet)
            .border(1.dp, Color.Black.copy(alpha = 0.09f), bookShape),
    ) {
        PaperPattern(kind = paper, modifier = Modifier.fillMaxSize())
        // 书脊:11dp 封面色竖条 + 右缘 2dp 压痕
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(11.dp)
                .background(cover)
                .drawBehind {
                    val crease = 2.dp.toPx()
                    drawRect(Color.Black.copy(alpha = 0.14f), topLeft = Offset(size.width - crease, 0f), size = Size(crease, size.height))
                },
        )
        // 纸面:名称 + 淡墨曲线
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 20.dp)) {
            Text(
                name,
                fontFamily = FontFamily.Serif,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF18191B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "A x = λ x",
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = cover,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Canvas(Modifier.fillMaxSize().padding(start = 24.dp, end = 14.dp, top = 84.dp, bottom = 18.dp)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height * 0.35f)
                cubicTo(size.width * 0.25f, size.height * 0.15f, size.width * 0.45f, size.height * 0.55f, size.width * 0.7f, size.height * 0.3f)
                moveTo(0f, size.height * 0.68f)
                cubicTo(size.width * 0.3f, size.height * 0.5f, size.width * 0.5f, size.height * 0.9f, size.width * 0.85f, size.height * 0.62f)
            }
            drawPath(path, Color(0xFF18191B).copy(alpha = 0.38f), style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
        }
        Box(Modifier.padding(start = 11.dp).padding(end = 10.dp, bottom = 8.dp).align(Alignment.BottomEnd)) {
            Text(
                PAPER_KINDS.firstOrNull { it.first == paper }?.second ?: "",
                style = MonoMeta.copy(fontSize = 10.sp),
                color = Color(0xFF9AA0A8),
            )
        }
    }
}

/** 纸张纹理小样:点阵 / 横线 / 方格 / 康奈尔 / 田字格 / 英文四线 / 空白 */
@Composable
private fun PaperPattern(kind: String, modifier: Modifier = Modifier) {
    val line = Color(0xFF18191B).copy(alpha = 0.16f)
    val lineStrong = Color(0xFF2563EB).copy(alpha = 0.35f)
    Canvas(modifier) {
        when (kind) {
            "dot" -> {
                val step = 13.dp.toPx()
                var y = step
                while (y < size.height) {
                    var x = step + 8.dp.toPx()
                    while (x < size.width) {
                        drawCircle(line, radius = 0.9f, center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
            "line", "english" -> {
                val step = 14.dp.toPx()
                var y = 70.dp.toPx()
                while (y < size.height) {
                    if (kind == "english") {
                        drawLine(lineStrong, Offset(16.dp.toPx(), y), Offset(size.width - 6.dp.toPx(), y), strokeWidth = 0.8.dp.toPx())
                        drawLine(
                            line, Offset(16.dp.toPx(), y + step / 2f), Offset(size.width - 6.dp.toPx(), y + step / 2f),
                            strokeWidth = 0.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
                        )
                    } else {
                        drawLine(Color(0xFF2563EB).copy(alpha = 0.13f), Offset(16.dp.toPx(), y), Offset(size.width - 6.dp.toPx(), y), strokeWidth = 0.8.dp.toPx())
                    }
                    y += step
                }
            }
            "grid", "tian" -> {
                val step = 13.dp.toPx()
                var p = step
                while (p < size.width || p < size.height) {
                    if (p < size.width) drawLine(line.copy(alpha = 0.09f), Offset(p, 0f), Offset(p, size.height), strokeWidth = 0.7.dp.toPx())
                    if (p < size.height) drawLine(line.copy(alpha = 0.09f), Offset(0f, p), Offset(size.width, p), strokeWidth = 0.7.dp.toPx())
                    p += step
                }
                if (kind == "tian") {
                    var gx = step / 2f
                    while (gx < size.width) {
                        var gy = step / 2f
                        while (gy < size.height) {
                            drawLine(line.copy(alpha = 0.10f), Offset(gx, gy - step / 2f), Offset(gx, gy + step / 2f), strokeWidth = 0.5.dp.toPx())
                            drawLine(line.copy(alpha = 0.10f), Offset(gx - step / 2f, gy), Offset(gx + step / 2f, gy), strokeWidth = 0.5.dp.toPx())
                            gy += step
                        }
                        gx += step
                    }
                }
            }
            "cornell" -> {
                val step = 14.dp.toPx()
                val headerY = size.height * 0.14f
                val cueX = size.width * 0.28f
                var y = headerY + step
                while (y < size.height) {
                    drawLine(line.copy(alpha = 0.10f), Offset(16.dp.toPx(), y), Offset(size.width - 6.dp.toPx(), y), strokeWidth = 0.7.dp.toPx())
                    y += step
                }
                drawLine(Color(0xFF2563EB).copy(alpha = 0.35f), Offset(16.dp.toPx(), headerY), Offset(size.width - 6.dp.toPx(), headerY), strokeWidth = 1.dp.toPx())
                drawLine(Color(0xFFD94436).copy(alpha = 0.42f), Offset(cueX, headerY), Offset(cueX, size.height - 8.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
            else -> Unit
        }
    }
}

/** Color → #RRGGBB */
internal fun androidx.compose.ui.graphics.Color.toArgbHex(): String =
    String.format("#%06X", 0xFFFFFF and toArgb())
