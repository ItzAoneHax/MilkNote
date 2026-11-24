package com.milknote.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.getValue
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
import com.milknote.app.ui.theme.AccentBlue
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.PaperInk
import com.milknote.notes.model.COVER_COLORS
import com.milknote.notes.model.PAGE_ORIENTATIONS
import com.milknote.notes.model.PAGE_SIZES_MM
import com.milknote.notes.model.PAPER_COLORS
import com.milknote.notes.model.PAPER_KINDS
import com.milknote.notes.model.PURPOSE_TAGS

/**
 * 纸纹绘制。[miniature] = true 时按缩略比例收紧步长(点阵 8.5dp、其余 10dp),
 * 保证 44dp 宽的小样上「点阵」等细密纸纹仍可辨。
 */
@Composable
fun NotebookPaperPattern(kind: String, modifier: Modifier = Modifier, miniature: Boolean = false) {
    val ink = PaperInk
    val dotStep = if (miniature) 8.5.dp else 18.dp
    val ruleStep = if (miniature) 10.dp else 20.dp
    val inset = if (miniature) 5.dp else 8.dp
    Canvas(modifier) {
        val muted = ink.copy(alpha = 0.14f)
        val blue = AccentBlue
        when (kind) {
            "dot" -> {
                val step = dotStep.toPx()
                var y = step
                while (y < size.height) {
                    var x = step
                    while (x < size.width) {
                        drawCircle(muted, radius = (if (miniature) 0.7.dp else 0.8.dp).toPx(), center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
            "line", "english" -> {
                val step = ruleStep.toPx()
                var y = if (miniature) step else 28.dp.toPx()
                while (y < size.height) {
                    drawLine(blue.copy(alpha = 0.18f), Offset(inset.toPx(), y), Offset(size.width - inset.toPx(), y), strokeWidth = 0.8.dp.toPx())
                    if (kind == "english") {
                        drawLine(
                            muted,
                            Offset(inset.toPx(), y + step / 2),
                            Offset(size.width - inset.toPx(), y + step / 2),
                            strokeWidth = 0.6.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                        )
                    }
                    y += step
                }
            }
            "grid", "tian" -> {
                val step = ruleStep.toPx()
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
                val step = ruleStep.toPx()
                var y = headerY + step
                while (y < size.height) {
                    drawLine(muted.copy(alpha = 0.65f), Offset(inset.toPx(), y), Offset(size.width - inset.toPx(), y), strokeWidth = 0.7.dp.toPx())
                    y += step
                }
                drawLine(blue.copy(alpha = 0.35f), Offset(inset.toPx(), headerY), Offset(size.width - inset.toPx(), headerY), strokeWidth = 1.dp.toPx())
                drawLine(Color(0xFFD94436).copy(alpha = 0.42f), Offset(cueX, headerY), Offset(cueX, size.height), strokeWidth = 1.dp.toPx())
            }
        }
    }
}

/** 纸纹选择:7 项 × 50dp 宽一行排齐(纸样 44×54、间距 6),选中描边顺滑过渡 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaperKindSelector(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PAPER_KINDS.forEach { (id, label) ->
            val active = selected == id
            val borderColor by animateColorAsState(
                if (active) cs.primary else cs.outline,
                MilkMotion.colorSpec(), label = "paperKindBorder",
            )
            val borderWidth by animateDpAsState(
                if (active) 1.5.dp else 1.dp,
                MilkMotion.colorSpec(), label = "paperKindBorderWidth",
            )
            Column(
                Modifier
                    .width(50.dp)
                    .clip(RoundedCornerShape(MilkRadius.sm))
                    .clickable(role = Role.RadioButton) { onSelect(id) }
                    .testTag("paper-kind-$id")
                    .semantics { contentDescription = "${label}纸纹${if (active) "，已选中" else ""}" }
                    .padding(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 44.dp, height = 54.dp)
                        .clip(RoundedCornerShape(MilkRadius.xs))
                        .background(Color.White)
                        .border(borderWidth, borderColor, RoundedCornerShape(MilkRadius.xs)),
                ) {
                    NotebookPaperPattern(id, Modifier.fillMaxSize(), miniature = true)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = MilkText.micro.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
                    color = if (active) cs.primary else cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 纸色选择:与纸纹一致的 50dp 列宽(纸样 44×48、间距 6),选中描边顺滑过渡 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaperColorSelector(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PAPER_COLORS.forEach { (hex, label) ->
            val active = selected.equals(hex, ignoreCase = true)
            val borderColor by animateColorAsState(
                if (active) cs.primary else cs.outline,
                MilkMotion.colorSpec(), label = "paperColorBorder",
            )
            val borderWidth by animateDpAsState(
                if (active) 1.5.dp else 1.dp,
                MilkMotion.colorSpec(), label = "paperColorBorderWidth",
            )
            Column(
                Modifier
                    .width(50.dp)
                    .clip(RoundedCornerShape(MilkRadius.sm))
                    .clickable(role = Role.RadioButton) { onSelect(hex) }
                    .testTag("paper-color-$hex")
                    .semantics { contentDescription = "纸色 $label${if (active) "，已选中" else ""}" }
                    .padding(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 44.dp, height = 48.dp)
                        .clip(RoundedCornerShape(MilkRadius.xs))
                        .background(cs.surfaceVariant)
                        .border(borderWidth, borderColor, RoundedCornerShape(MilkRadius.xs))
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
                    style = MilkText.micro.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
                    color = if (active) cs.primary else cs.onSurfaceVariant,
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
