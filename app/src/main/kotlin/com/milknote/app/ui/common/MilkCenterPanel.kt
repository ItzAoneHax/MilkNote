package com.milknote.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.milkPanelEnter
import com.milknote.app.ui.theme.milkPopoverEnter

/**
 * 所有「居中浮层」的统一骨架(第二代 · DESIGN-V2 modal):
 * 18dp 圆角 hairline 面 + 面板标题/副题 + 36dp 关闭钮 + 20dp 内边距内容区,
 * 可选底部动作条(hairline 分隔 + 统一内边距)。
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
    footer: (@Composable RowScope.() -> Unit)? = null,
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
                .padding(MilkSpace.xxl),
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
                        .then(if (footer != null) Modifier.weight(1f, fill = false) else Modifier)
                        .padding(
                            start = MilkSpace.xl,
                            end = MilkSpace.xl,
                            bottom = if (footer == null) MilkSpace.xl else MilkSpace.md,
                        ),
                    content = content,
                )
                if (footer != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MilkSpace.xl, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = footer,
                    )
                }
            }
        }
    }
}

/** 浮层标题区:可选前置图标 + 面板标题 + 灰副题 + hairline 分隔 + 36dp 关闭钮 */
@Composable
fun MilkPanelHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    closable: Boolean = true,
    onDismiss: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = MilkSpace.xl, top = 14.dp, end = MilkSpace.md, bottom = MilkSpace.xs + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Box(Modifier) { it() }
            Spacer(Modifier.width(MilkSpace.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MilkText.panelTitle,
                color = cs.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MilkText.caption,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        if (closable) {
            Spacer(Modifier.width(MilkSpace.sm))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(MilkRadius.sm))
                    .clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "关闭$title",
                    tint = cs.onSurfaceVariant,
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
 * 锚定弹出菜单(DESIGN-V2 popover · 材质受光):挂在触发按钮所在的 Box 内,向下展开、右缘对齐;
 * 从触发角 [origin] 以 0.94→1 长出 + 上移 4dp→0,玻璃受光边线保证暗色也干净利落。
 */
@Composable
fun MilkAnchoredMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anchorHeight: Dp = 36.dp,
    minWidth: Dp = 200.dp,
    origin: TransformOrigin = TransformOrigin(1f, 0f),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val offsetY = with(density) { anchorHeight.roundToPx() }
    val dark = LocalMilkDark.current
    val shape = RoundedCornerShape(MilkRadius.lg)
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
                .milkPopoverEnter(origin)
                .milkGlassRim(shape, dark, 12.dp)
                .padding(6.dp)
                .testTag("milk-anchored-menu"),
            content = content,
        )
    }
}

/** 弹出菜单行:38dp 高、18dp 图标、正文文案、右侧 mono 提示;危险项红字 */
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
            .clip(RoundedCornerShape(MilkRadius.sm))
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
            style = MilkText.body,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (action.trailingText != null) {
            Spacer(Modifier.width(MilkSpace.sm))
            Text(action.trailingText, style = MilkText.monoMeta, color = cs.onSurfaceVariant)
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
        MilkMotion.colorSpec(), label = "radioRowBg",
    )
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MilkRadius.sm))
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
        Spacer(Modifier.width(MilkSpace.md))
        Text(
            label,
            style = MilkText.body.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal),
            color = if (selected) cs.primary else cs.onBackground,
        )
    }
}
