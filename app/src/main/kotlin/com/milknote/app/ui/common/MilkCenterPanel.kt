package com.milknote.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.milknote.app.ui.theme.milkPanelEnter

/**
 * 所有「居中浮层」的统一骨架(视觉升级 2026-11):
 * 24dp 圆角模态面 + [MilkPanelHeader](标题/副题/关闭) + 24dp 内边距内容区。
 * 调用方只管内容;内容自带滚动时用 heightIn(max) + verticalScroll。
 */
@Composable
fun MilkCenterPanel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 560.dp,
    maxHeight: Dp = 640.dp,
    subtitle: String? = null,
    closable: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
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
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .milkPanelEnter()
                    .milkDialogSurface(),
            ) {
                MilkPanelHeader(title = title, subtitle = subtitle, icon = icon, closable = closable, onDismiss = onDismiss)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    content = content,
                )
            }
        }
    }
}

/** 浮层标题区:可选前置图标 + 标题 17sp + 灰副题 + 40dp 圆形关闭钮 */
@Composable
fun MilkPanelHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    closable: Boolean = true,
    onDismiss: () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 22.dp, end = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        icon?.let {
            Box(Modifier.padding(top = 0.dp)) { it() }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = if (icon != null) 8.dp else 2.dp),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (closable) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    .clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "关闭$title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

data class MilkPanelAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    /** 右侧 mono 提示(页数 / 锚点页 id / 「当前」);「当前项」语义由调用方以 leading ✓ 表达 */
    val trailingText: String? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

/** 居中的动作列表(创建 / 排序 / 更多):52dp 行 + 36dp 图标容器 + 右侧 mono 提示 */
@Composable
fun MilkActionPanel(
    title: String,
    actions: List<MilkPanelAction>,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    MilkCenterPanel(title = title, subtitle = subtitle, onDismiss = onDismiss, maxWidth = 420.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .testTag("writer-panel-scroll"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            actions.forEach { action -> MilkActionRow(action) }
        }
    }
}

@Composable
private fun MilkActionRow(action: MilkPanelAction) {
    val cs = MaterialTheme.colorScheme
    val fg = when {
        !action.enabled -> cs.onSurfaceVariant.copy(alpha = 0.38f)
        action.destructive -> cs.error
        else -> cs.onSurface
    }
    val container = if (action.destructive) cs.error.copy(alpha = 0.10f) else cs.surfaceVariant.copy(alpha = 0.75f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = action.enabled, role = Role.Button, onClick = action.onClick)
            .heightIn(min = 56.dp)
            .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            if (action.icon != null) {
                Icon(action.icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            action.label,
            color = fg,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (action.trailingText != null) {
            Text(
                action.trailingText,
                style = com.milknote.app.ui.theme.MonoValue,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

/** 居中的单选列表,选中后立即提交并关闭:左侧单选圆点,选中行淡蓝底 */
@Composable
fun <T> MilkChoicePanel(
    title: String,
    items: List<T>,
    selected: T?,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    MilkCenterPanel(title = title, subtitle = subtitle, onDismiss = onDismiss, maxWidth = 400.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .testTag("writer-panel-scroll"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEach { item -> MilkChoiceRow(item == selected, label(item)) { onPick(item); onDismiss() } }
        }
    }
}

/** 单选行(面板内列表 / 任意选择列表):左侧单选圆点,选中淡蓝底 + 蓝字 */
@Composable
fun MilkRadioRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) cs.primary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 50.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) cs.primary else cs.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(cs.primary))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) cs.primary else cs.onSurface,
            fontWeight = if (selected) FontWeight.Medium else null,
        )
    }
}

@Composable
private fun MilkChoiceRow(selected: Boolean, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) cs.primary.copy(alpha = 0.08f) else Color.Transparent,
        androidx.compose.animation.core.tween(160), label = "choiceRowBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) cs.primary else cs.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(cs.primary))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) cs.primary else cs.onSurface,
            fontWeight = if (selected) FontWeight.Medium else null,
        )
    }
}
