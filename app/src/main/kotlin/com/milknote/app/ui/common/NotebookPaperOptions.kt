package com.milknote.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.notes.model.COVER_COLORS
import com.milknote.notes.model.PAGE_ORIENTATIONS
import com.milknote.notes.model.PAGE_SIZES_MM
import com.milknote.notes.model.PAPER_COLORS
import com.milknote.notes.model.PAPER_KINDS
import com.milknote.notes.model.PURPOSE_TAGS

@Composable
fun NotebookPaperPattern(kind: String, modifier: Modifier = Modifier) {
    val ink = Color(0xFF18191B)
    Canvas(modifier) {
        val muted = ink.copy(alpha = 0.14f)
        val blue = Color(0xFF2563EB)
        when (kind) {
            "dot" -> {
                val step = 18.dp.toPx()
                var y = step
                while (y < size.height) {
                    var x = step
                    while (x < size.width) {
                        drawCircle(muted, radius = 0.8.dp.toPx(), center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
            "line", "english" -> {
                val step = 20.dp.toPx()
                var y = 28.dp.toPx()
                while (y < size.height) {
                    drawLine(blue.copy(alpha = 0.18f), Offset(8.dp.toPx(), y), Offset(size.width - 8.dp.toPx(), y), strokeWidth = 0.8.dp.toPx())
                    if (kind == "english") {
                        drawLine(
                            muted,
                            Offset(8.dp.toPx(), y + step / 2),
                            Offset(size.width - 8.dp.toPx(), y + step / 2),
                            strokeWidth = 0.6.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                        )
                    }
                    y += step
                }
            }
            "grid", "tian" -> {
                val step = 20.dp.toPx()
                var p = step
                while (p < size.width || p < size.height) {
                    if (p < size.width) drawLine(muted.copy(alpha = 0.65f), Offset(p, 0f), Offset(p, size.height), strokeWidth = 0.7.dp.toPx())
                    if (p < size.height) drawLine(muted.copy(alpha = 0.65f), Offset(0f, p), Offset(size.width, p), strokeWidth = 0.7.dp.toPx())
                    p += step
                }
                if (kind == "tian") {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))
                    var x = step / 2
                    while (x < size.width) {
                        drawLine(muted.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5.dp.toPx(), pathEffect = dash)
                        x += step
                    }
                    var y = step / 2
                    while (y < size.height) {
                        drawLine(muted.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx(), pathEffect = dash)
                        y += step
                    }
                }
            }
            "cornell" -> {
                val headerY = size.height * 0.15f
                val cueX = size.width * 0.28f
                val step = 20.dp.toPx()
                var y = headerY + step
                while (y < size.height) {
                    drawLine(muted.copy(alpha = 0.65f), Offset(8.dp.toPx(), y), Offset(size.width - 8.dp.toPx(), y), strokeWidth = 0.7.dp.toPx())
                    y += step
                }
                drawLine(blue.copy(alpha = 0.35f), Offset(8.dp.toPx(), headerY), Offset(size.width - 8.dp.toPx(), headerY), strokeWidth = 1.dp.toPx())
                drawLine(Color(0xFFD94436).copy(alpha = 0.42f), Offset(cueX, headerY), Offset(cueX, size.height), strokeWidth = 1.dp.toPx())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaperKindSelector(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PAPER_KINDS.forEach { (id, label) ->
            val active = selected == id
            Column(
                Modifier
                    .width(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.RadioButton) { onSelect(id) }
                    .testTag("paper-kind-$id")
                    .semantics { contentDescription = "${label}纸纹${if (active) "，已选中" else ""}" }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 48.dp, height = 58.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.White)
                        .border(if (active) 1.5.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp)),
                ) {
                    NotebookPaperPattern(id, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    fontSize = 11.sp,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaperColorSelector(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PAPER_COLORS.forEach { (hex, label) ->
            val active = selected.equals(hex, ignoreCase = true)
            Column(
                Modifier
                    .width(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.RadioButton) { onSelect(hex) }
                    .testTag("paper-color-$hex")
                    .semantics { contentDescription = "纸色 $label${if (active) "，已选中" else ""}" }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 48.dp, height = 52.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(if (active) 1.5.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp))
                        .padding(5.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(android.graphics.Color.parseColor(hex)))
                            .border(0.5.dp, Color.Black.copy(alpha = 0.08f)),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    fontSize = 11.sp,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotebookAdvancedOptions(
    pageSize: String,
    onPageSize: (String) -> Unit,
    orientation: String,
    onOrientation: (String) -> Unit,
    paperColor: String,
    onPaperColor: (String) -> Unit,
    purpose: String,
    onPurpose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        MilkSectionLabel("纸色", description = paperColorName(paperColor))
        Spacer(Modifier.height(8.dp))
        PaperColorSelector(selected = paperColor, onSelect = onPaperColor)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                MilkSectionLabel("尺寸")
                Spacer(Modifier.height(8.dp))
                MilkSegmented(
                    options = PAGE_SIZES_MM.keys.toList(),
                    selected = pageSize,
                    label = { it },
                    onSelect = onPageSize,
                )
            }
            Column(Modifier.weight(1f)) {
                MilkSectionLabel("方向")
                Spacer(Modifier.height(8.dp))
                MilkSegmented(
                    options = PAGE_ORIENTATIONS.map { it.first },
                    selected = orientation,
                    label = { id -> PAGE_ORIENTATIONS.firstOrNull { it.first == id }?.second ?: id },
                    onSelect = onOrientation,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        MilkSectionLabel("用途")
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PURPOSE_TAGS.forEach { tag ->
                MilkChip(label = tag, selected = purpose == tag, onClick = { onPurpose(tag) }, small = true)
            }
        }
    }
}

fun paperColorName(hex: String): String =
    PAPER_COLORS.firstOrNull { it.first.equals(hex, ignoreCase = true) }?.second ?: "自定义"

fun coverColorName(hex: String): String {
    val names = listOf("蓝", "红", "绿", "琥珀", "紫", "青")
    val index = COVER_COLORS.indexOfFirst { it.equals(hex, ignoreCase = true) }
    return names.getOrElse(index) { "自定义" }
}
