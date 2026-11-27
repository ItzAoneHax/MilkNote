package com.milknote.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkText

private data class NavItem(val key: String, val label: String, val icon: ImageVector)

/**
 * 库底部导航(笔记 / 卡片):surface 底 + 顶部 hairline;选中 = 淡蓝胶囊 + 蓝图标蓝字。
 * 68dp 高,每项 ≥136×48dp 命中。
 */
@Composable
fun LibraryBottomNav(selected: String, onNotes: () -> Unit, onCards: () -> Unit) {
    val items = listOf(
        NavItem("笔记", "笔记", MilkIcons.Book),
        NavItem("卡片", "卡片", MilkIcons.Cards),
    )
    val actions: Map<String, () -> Unit> = mapOf("笔记" to onNotes, "卡片" to onCards)
    Row(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(MaterialTheme.colorScheme.surface)
            .milkEdgeHairline(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), top = true)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val active = item.key == selected
            val cs = MaterialTheme.colorScheme
            val pillBg by animateColorAsState(
                targetValue = if (active) cs.primary.copy(alpha = 0.12f) else Color.Transparent,
                animationSpec = MilkMotion.colorSpec(),
                label = "navPillBg",
            )
            val fg by animateColorAsState(
                targetValue = if (active) cs.primary else cs.onSurfaceVariant,
                animationSpec = MilkMotion.colorSpec(),
                label = "navPillFg",
            )
            Row(
                Modifier
                    .clip(RoundedCornerShape(MilkRadius.pill))
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
                        style = MilkText.rowTitle,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = fg,
                    )
                }
            }
        }
    }
}
