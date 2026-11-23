package com.milknote.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.writer.edgeHairline

private data class NavItem(val key: String, val label: String, val icon: ImageVector)

/**
 * 库底部导航(笔记 / 卡片):surface 底 + 顶部 hairline;选中 = 淡蓝胶囊 + 蓝图标蓝字。
 * 64dp 高,每项 ≥120×48dp 命中。
 */
@Composable
fun LibraryBottomNav(selected: String, onNotes: () -> Unit, onCards: () -> Unit) {
    val items = listOf(
        NavItem("笔记", "笔记", Icons.Outlined.Notes),
        NavItem("卡片", "卡片", Icons.Outlined.Style),
    )
    val actions: Map<String, () -> Unit> = mapOf("笔记" to onNotes, "卡片" to onCards)
    Row(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(MaterialTheme.colorScheme.surface)
            .edgeHairline(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), top = true)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val active = item.key == selected
            val cs = MaterialTheme.colorScheme
            val pillBg by androidx.compose.animation.animateColorAsState(
                targetValue = if (active) cs.primary.copy(alpha = 0.12f) else Color.Transparent,
                animationSpec = androidx.compose.animation.core.tween(com.milknote.app.ui.theme.MilkMotion.color),
                label = "navPillBg",
            )
            val fg by androidx.compose.animation.animateColorAsState(
                targetValue = if (active) cs.primary else cs.onSurfaceVariant,
                animationSpec = androidx.compose.animation.core.tween(com.milknote.app.ui.theme.MilkMotion.color),
                label = "navPillFg",
            )
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(pillBg)
                    .clickable(onClick = actions.getValue(item.key))
                    .defaultMinSize(minWidth = 136.dp, minHeight = 48.dp)
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Column {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.Medium else null,
                        color = fg,
                    )
                }
            }
        }
    }
}
