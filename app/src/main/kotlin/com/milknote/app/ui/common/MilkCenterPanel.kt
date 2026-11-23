package com.milknote.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.milknote.app.ui.theme.MonoValue
import com.milknote.app.ui.theme.milkPanelEnter

/**
 * 所有「居中浮层」的统一骨架(第二代 · DESIGN-V2 modal):
 * 18dp 圆角 hairline 面 + 标题 16sp/副题 12sp + 36dp 关闭钮 + 20dp 内边距内容区。
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
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    content = content,
                )
            }
        }
    }
}

/** 浮层标题区:可选前置图标 + 标题 16sp + 灰副题 + hairline 分隔 + 36dp 关闭钮 */
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
            .padding(start = 20.dp, top = 14.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Box(Modifier) { it() }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (closable) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "关闭$title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
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

/** 居中的动作列表(创建 / 排序 / 更多):38dp 行 + 18dp 图标 + 右侧 mono 提示 */
@Composable
fun MilkActionPanel(
    title: String,
    actions: List<MilkPanelAction>,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    MilkCenterPanel(title = title, subtitle = subtitle, onDismiss = onDismiss, maxWidth = 320.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .testTag("writer-panel-scroll"),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            actions.forEach { action -> MilkMenuRow(action) }
        }
    }
}

/**
 * 锚定弹出菜单(DESIGN-V2 popover):挂在触发按钮所在的 Box 内,向下展开、右缘对齐;
 * 13dp 圆角 hairline 面 + 柔和投影,点外部/返回键关闭。
 */
@Composable
fun MilkAnchoredMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anchorHeight: Dp = 36.dp,
    minWidth: Dp = 200.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = androidx.compose.ui.platform.LocalDensity.current
    val offsetY = with(density) { anchorHeight.roundToPx() }
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(13.dp)
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = minWidth)
                .clip(shape)
                .background(cs.surface)
                .border(1.dp, cs.outline, shape)
                .softShadow(12.dp, shape)
                .padding(6.dp)
                .testTag("milk-anchored-menu"),
            content = content,
        )
    }
}

/** 弹出菜单行:38dp 高、18dp 图标、13sp 文案、右侧 mono 提示;危险项红字 */
@Composable
fun MilkMenuRow(action: MilkPanelAction) {
    val cs = MaterialTheme.colorScheme
    val fg = when {
        !action.enabled -> cs.onSurfaceVariant.copy(alpha = 0.38f)
        action.destructive -> cs.error
        else -> cs.onSurface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = action.enabled, role = Role.Button, onClick = action.onClick)
            .heightIn(min = 38.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        action.icon?.let {
            Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            action.label,
            color = fg,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (action.trailingText != null) {
            Spacer(Modifier.width(8.dp))
            Text(action.trailingText, style = MonoValue.copy(fontSize = 12.sp), color = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun MilkMenuDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).height(1.dp).background(MaterialTheme.colorScheme.outline))
}

/** 居中的单选列表,选中后立即提交并关闭:左侧单选圆点,选中行淡强调底 */
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
    MilkCenterPanel(title = title, subtitle = subtitle, onDismiss = onDismiss, maxWidth = 360.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .testTag("writer-panel-scroll"),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items.forEach { item -> MilkRadioRow(selected = item == selected, label = label(item), onClick = { onPick(item); onDismiss() }) }
        }
    }
}

/** 单选行(面板内列表 / 任意选择列表):左侧 16dp 单选圆点,选中淡强调底 + 强调色字 */
@Composable
fun MilkRadioRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) cs.primary.copy(alpha = 0.09f) else Color.Transparent,
        tween(160), label = "radioRowBg",
    )
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) cs.primary else cs.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(cs.primary))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) cs.primary else cs.onBackground,
        )
    }
}
