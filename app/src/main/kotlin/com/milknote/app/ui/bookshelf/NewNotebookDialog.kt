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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
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
import com.milknote.app.ui.common.innerHighlight
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.CoverPalette
import com.milknote.app.ui.theme.MonoMeta
import com.milknote.app.ui.theme.coverForeground
import com.milknote.notes.model.PAGE_ORIENTATIONS
import com.milknote.notes.model.PAGE_SIZES_MM
import com.milknote.notes.model.PAPER_COLORS
import com.milknote.notes.model.PAPER_KINDS
import com.milknote.notes.model.PURPOSE_TAGS

/**
 * ② 新建笔记本弹窗(2026-11 重设计):左侧实时封面预览 + 右侧分组表单。
 * 预览即时反映名称 / 封面色(书脊)/ 尺寸方向(比例)/ 纸张(纹理)/ 纸色 / 用途,所见即所得;
 * 「创建 ↵」直接进入第 1 页。小窗口/IME 下整体可滚动,CTA 固定在底部(A-08)。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewNotebookDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, coverHex: String, paper: String, purpose: String, pageSize: String, orientation: String, paperColor: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var coverIdx by rememberSaveable { mutableStateOf(0) }
    var paper by rememberSaveable { mutableStateOf("dot") }
    var paperColor by rememberSaveable { mutableStateOf(PAPER_COLORS.first().first) }
    var pageSize by rememberSaveable { mutableStateOf("B5") }        // 默认 B5
    var orientation by rememberSaveable { mutableStateOf("landscape") } // 默认横向
    var purpose by rememberSaveable { mutableStateOf(PURPOSE_TAGS.first()) }

    fun submit() {
        onCreate(name.trim().ifBlank { "未命名笔记" }, CoverPalette[coverIdx].toArgbHex(), paper, purpose, pageSize, orientation, paperColor)
    }

    MilkCenterPanel(
        title = "新建笔记本",
        subtitle = "创建后直接进入第一页；之后可在卡片「…」里改名、换封面",
        onDismiss = onDismiss,
        maxWidth = 780.dp,
        maxHeight = 720.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            MilkTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "如：高数·第三章",
                label = "名称",
                mono = false,
                leading = {
                    Icon(
                        Icons.Outlined.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
                imeAction = ImeAction.Done,
                onDone = { submit() },
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                NotebookPreview(
                    name = name.trim().ifBlank { "未命名笔记" },
                    cover = CoverPalette[coverIdx],
                    paperColor = paperColor,
                    paper = paper,
                    pageSize = pageSize,
                    orientation = orientation,
                    purpose = purpose,
                    modifier = Modifier.width(196.dp),
                )
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    MilkSectionLabel("封面色")
                    Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            MilkSectionLabel("尺寸")
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                PAGE_SIZES_MM.keys.forEach { id ->
                                    MilkChip(label = id, selected = pageSize == id, onClick = { pageSize = id })
                                }
                            }
                        }
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1.25f)) {
                            MilkSectionLabel("方向")
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                PAGE_ORIENTATIONS.forEach { (id, label) ->
                                    MilkChip(label = label, selected = orientation == id, onClick = { orientation = id })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    MilkSectionLabel("纸张")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PAPER_KINDS.forEach { (id, label) ->
                            MilkChip(label = label, selected = paper == id, onClick = { paper = id })
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    MilkSectionLabel("纸色", description = PAPER_COLORS.firstOrNull { it.first == paperColor }?.second)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        PAPER_COLORS.forEach { (hex, _) ->
                            MilkColorDot(
                                color = Color(android.graphics.Color.parseColor(hex)),
                                selected = paperColor == hex,
                                onClick = { paperColor = hex },
                                contentDescription = "纸色",
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    MilkSectionLabel("用途")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PURPOSE_TAGS.forEach { tag ->
                            MilkChip(label = tag, selected = purpose == tag, onClick = { purpose = tag })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        // CTA 行:取消 + 创建,均 ≥48dp 命中,始终可达(A-08)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MilkButton(
                label = "取消",
                onClick = onDismiss,
                variant = MilkButtonVariant.SECONDARY,
            )
            Spacer(Modifier.width(10.dp))
            MilkButton(label = "创建 ↵", onClick = { submit() })
        }
    }
}

/**
 * 实时预览:书架卡片同款骨架(封面区 + 名称 + mono 元信息)。
 * 封面区 = 纸色底 + 纸张纹理,左侧 10dp 封面色书脊;比例随尺寸/方向变化;左上角用途标签。
 */
@Composable
private fun NotebookPreview(
    name: String,
    cover: Color,
    paperColor: String,
    paper: String,
    pageSize: String,
    orientation: String,
    purpose: String,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val (wMm, hMm) = PAGE_SIZES_MM[pageSize] ?: PAGE_SIZES_MM.getValue("B5")
    val ratio = if (orientation == "landscape") hMm / wMm else wMm / hMm
    val paperLabel = PAPER_KINDS.firstOrNull { it.first == paper }?.second ?: "点阵"
    val orientLabel = PAGE_ORIENTATIONS.firstOrNull { it.first == orientation }?.second ?: "横向"
    val coverFg = coverForeground(cover, com.milknote.app.ui.theme.LocalMilkDark.current)

    Column(
        modifier
            .softShadow(4.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline, RoundedCornerShape(18.dp))
            .innerHighlight(strength = 0.3f),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
        ) {
            PaperPattern(
                kind = paper,
                paperColor = Color(android.graphics.Color.parseColor(paperColor)),
                modifier = Modifier.fillMaxSize(),
            )
            // 书脊:封面色竖条 + 右侧 1px 分界
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(10.dp)
                    .background(Brush.horizontalGradient(listOf(cover, cover.copy(alpha = 0.85f)))),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color.Black.copy(alpha = 0.10f)),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(cover.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(purpose, fontSize = 10.sp, color = coverFg, maxLines = 1)
            }
            // 纸面示意笔迹:两道淡墨曲线,让预览像"用过"的纸而非空块
            Canvas(Modifier.fillMaxSize().padding(start = 26.dp, end = 16.dp, top = 30.dp, bottom = 18.dp)) {
                val ink = Color(0xFF23221E).copy(alpha = 0.35f)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, size.height * 0.35f)
                    cubicTo(size.width * 0.25f, size.height * 0.15f, size.width * 0.45f, size.height * 0.55f, size.width * 0.7f, size.height * 0.3f)
                    cubicTo(size.width * 0.82f, size.height * 0.18f, size.width * 0.9f, size.height * 0.4f, size.width, size.height * 0.3f)
                    moveTo(0f, size.height * 0.68f)
                    cubicTo(size.width * 0.3f, size.height * 0.5f, size.width * 0.5f, size.height * 0.9f, size.width * 0.85f, size.height * 0.62f)
                }
                drawPath(path, ink, style = Stroke(width = 1.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "$pageSize · $orientLabel · $paperLabel",
                style = MonoMeta,
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 纸张纹理小样:点阵 / 横线 / 方格 / 空白 / 英文四线 / 田字格 */
@Composable
private fun PaperPattern(kind: String, paperColor: Color, modifier: Modifier = Modifier) {
    val line = Color(0xFF23221E).copy(alpha = 0.16f)
    val lineStrong = Color(0xFF3B5BEC).copy(alpha = 0.35f)
    Canvas(modifier.background(paperColor)) {
        when (kind) {
            "dot" -> {
                val step = 11.dp.toPx()
                var y = step
                while (y < size.height) {
                    var x = step
                    while (x < size.width) {
                        drawCircle(line, radius = 0.9f, center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
            "line", "english" -> {
                val step = 13.dp.toPx()
                var y = step
                while (y < size.height) {
                    if (kind == "english") {
                        drawLine(lineStrong, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8.dp.toPx())
                        drawLine(
                            line, Offset(0f, y + step / 2f), Offset(size.width, y + step / 2f),
                            strokeWidth = 0.6.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
                        )
                    } else {
                        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8.dp.toPx())
                    }
                    y += step
                }
            }
            "cornell" -> {
                val step = 13.dp.toPx()
                val headerY = size.height * 0.14f
                val cueX = size.width * 0.25f
                var y = headerY + step
                while (y < size.height) {
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.7.dp.toPx())
                    y += step
                }
                drawLine(Color(0xFF2563EB).copy(alpha = 0.35f), Offset(0f, headerY), Offset(size.width, headerY), strokeWidth = 1.dp.toPx())
                drawLine(Color(0xFFD94436).copy(alpha = 0.42f), Offset(cueX, headerY), Offset(cueX, size.height), strokeWidth = 1.dp.toPx())
            }
            "grid", "tian" -> {
                val step = 12.dp.toPx()
                var p = step
                while (p < size.width || p < size.height) {
                    if (p < size.width) drawLine(line, Offset(p, 0f), Offset(p, size.height), strokeWidth = 0.7.dp.toPx())
                    if (p < size.height) drawLine(line, Offset(0f, p), Offset(size.width, p), strokeWidth = 0.7.dp.toPx())
                    p += step
                }
                if (kind == "tian") {
                    // 每格中心十字(田字格)
                    var gx = step / 2f
                    while (gx < size.width) {
                        var gy = step / 2f
                        while (gy < size.height) {
                            drawLine(
                                line.copy(alpha = 0.10f), Offset(gx, gy - step / 2f), Offset(gx, gy + step / 2f),
                                strokeWidth = 0.5.dp.toPx(),
                            )
                            drawLine(
                                line.copy(alpha = 0.10f), Offset(gx - step / 2f, gy), Offset(gx + step / 2f, gy),
                                strokeWidth = 0.5.dp.toPx(),
                            )
                            gy += step
                        }
                        gx += step
                    }
                }
            }
            else -> Unit
        }
    }
}

/** Color → #RRGGBB */
internal fun androidx.compose.ui.graphics.Color.toArgbHex(): String =
    String.format("#%06X", 0xFFFFFF and toArgb())
