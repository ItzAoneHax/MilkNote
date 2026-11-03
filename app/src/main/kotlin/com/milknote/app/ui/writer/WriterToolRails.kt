package com.milknote.app.ui.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
internal fun WriterLeftRail(
    state: WriterUiState,
    enabled: Boolean,
    motionIndex: Int?,
    onMotion: (Int, Boolean) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onColor: (Long) -> Unit,
    onColorDetail: (Int) -> Unit,
) {
    Column(
        Modifier
            .width(64.dp)
            .fillMaxHeight()
            .railEdge(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), MaterialTheme.colorScheme.outline, right = true)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailIconButton(Icons.AutoMirrored.Outlined.Undo, "撤销", state.canUndo && enabled, onUndo)
        Spacer(Modifier.height(4.dp))
        RailIconButton(Icons.AutoMirrored.Outlined.Redo, "重做", state.canRedo && enabled, onRedo)
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.width(24.dp).height(1.dp).background(MaterialTheme.colorScheme.outline))
        Spacer(Modifier.height(12.dp))
        state.toolSettings.palettes.getValue(state.tool.tool).forEachIndexed { index, color ->
            val motion = index + 4
            val active = state.tool.color == color
            InkColorDot(
                color = color,
                label = "颜色 ${index + 1}",
                active = active,
                enabled = enabled,
                squeezed = motionIndex != null && abs(motionIndex - motion) == 1,
                onHoverChanged = { onMotion(motion, it) },
                onClick = { if (active) onColorDetail(index) else onColor(color) },
            )
        }
    }
}

@Composable
private fun RailIconButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp)) }
}

private fun Modifier.railEdge(background: Color, line: Color, right: Boolean): Modifier =
    background(background).drawBehind {
        val x = if (right) size.width - 1.dp.toPx() else 0f
        drawRect(line, topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height))
    }
