package com.milknote.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 按钮触发的调整面板统一使用屏幕中央 Dialog，而不是锚定按钮展开。
 * 标题与关闭按钮固定，内容由调用方决定是否需要内部滚动。
 */
@Composable
fun MilkCenterPanel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 560.dp,
    maxHeight: Dp = 620.dp,
    subtitle: String? = null,
    closable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = closable,
            dismissOnClickOutside = closable,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier
                    .widthIn(max = maxWidth)
                    .heightIn(max = maxHeight)
                    .milkDialogSurface()
                    .padding(20.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    if (closable) {
                        MilkIconButton(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭$title",
                            onClick = onDismiss,
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        )
                    }
                }
                content()
            }
        }
    }
}

data class MilkPanelAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val trailingText: String? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

/** 居中的动作列表，替代 DropdownMenu 与按钮下方的展开菜单。 */
@Composable
fun MilkActionPanel(
    title: String,
    actions: List<MilkPanelAction>,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    MilkCenterPanel(title = title, subtitle = subtitle, onDismiss = onDismiss, maxWidth = 440.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .testTag("writer-panel-scroll"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            actions.forEach { action ->
                val color = when {
                    !action.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    action.destructive -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            enabled = action.enabled,
                            role = Role.Button,
                            onClick = action.onClick,
                        )
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (action.icon != null) {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = color,
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(action.label, color = color, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    if (action.trailingText != null) {
                        Text(
                            action.trailingText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 居中的单选列表，选中后立即提交并关闭。 */
@Composable
fun <T> MilkChoicePanel(
    title: String,
    items: List<T>,
    selected: T?,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    MilkCenterPanel(title = title, onDismiss = onDismiss, maxWidth = 440.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .testTag("writer-panel-scroll"),
        ) {
            items.forEach { item ->
                val isSelected = item == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) {
                            onPick(item)
                            onDismiss()
                        }
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (isSelected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "当前选择",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        label(item),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Medium else null,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
