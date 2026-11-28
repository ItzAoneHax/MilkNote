package com.milknote.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milknote.agent.BuiltinTools
import com.milknote.agent.PendingToolConfirmation
import com.milknote.agent.ToolSettingOption
import com.milknote.agent.ToolTrace
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.LocalMilkSuccess
import com.milknote.app.ui.theme.LocalMilkWarning
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText

/*
 * 能力套件(技能 / 工具 / 扩展能力共用):
 * 分区头、能力行、编辑面板、声明式设置字段、工具调用轨迹、高危确认条。
 *
 * 信息卫生:每个元素只用名称、图标、位置、状态徽标与开关表达含义,不写说明性文字。
 * 零位移:分区头恒定 36dp 行高(有无动作钮都不改高度),能力行尾簇顺序固定,
 * 轨迹行展开只向下生长,不推动已有内容的位置。
 */

// ==================== 分区头 ====================

/** 分区头:`标题 + 计数` 贴左,可选文本动作贴右;行高恒定 36dp,无描述行 */
@Composable
fun MilkCapabilitySectionHeader(
    title: String,
    count: Int,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(top = MilkSpace.xxs, bottom = MilkSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MilkText.label,
            color = cs.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.width(MilkSpace.sm))
        Text(
            text = count.toString(),
            style = MilkText.monoMicro,
            color = cs.onSurfaceVariant.copy(alpha = 0.72f),
            maxLines = 1,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.weight(1f))
            MilkInlineAction(
                label = actionLabel,
                onClick = onAction,
                icon = actionIcon,
                tone = MilkInlineTone.ACCENT,
            )
        }
    }
}

// ==================== 能力行 ====================

/**
 * 能力行:左 30dp 圆角图标位 + 标题(可选单行副题)+ 尾簇(徽标 / 开关 / 细箭头)。
 * 开关独立响应开合,行其余区域点击进入编辑器——两个动作不共享命中区。
 */
@Composable
fun MilkCapabilityRow(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeText: String? = null,
    badgeTone: MilkStatusTone = MilkStatusTone.NEUTRAL,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(MilkRadius.sm))
                .background(
                    if (enabled) {
                        cs.primary.copy(alpha = if (dark) 0.18f else 0.10f)
                    } else {
                        cs.surfaceVariant.copy(alpha = if (dark) 0.55f else 0.75f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = if (enabled) cs.onSurface else cs.onSurfaceVariant.copy(alpha = 0.45f),
            )
        }
        Spacer(Modifier.width(MilkSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MilkText.rowTitle,
                color = if (enabled) cs.onBackground else cs.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MilkText.caption,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (badgeText != null) {
            Spacer(Modifier.width(MilkSpace.sm))
            MilkStatusBadge(text = badgeText, tone = badgeTone)
        }
        Spacer(Modifier.width(MilkSpace.sm))
        MilkSwitch(checked = enabled, onChange = onToggle)
        Spacer(Modifier.width(MilkSpace.xs))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = cs.onSurfaceVariant.copy(alpha = 0.55f),
        )
    }
}

// ==================== 编辑面板 ====================

/**
 * 能力编辑面板:标题 + 可滚动内容区 + 底栏(`leadingAction` 贴左 / 取消 + 保存贴右)。
 * 内容区自带 12dp 行距与滚动,长表单不撑破面板。
 */
@Composable
fun MilkCapabilityEditorPanel(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    leadingAction: (@Composable RowScope.() -> Unit)? = null,
    saveLabel: String = "保存",
    saveEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    MilkCenterPanel(
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        modifier = modifier,
        maxWidth = 540.dp,
        maxHeight = 640.dp,
        icon = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        footer = {
            leadingAction?.invoke(this)
            Spacer(Modifier.weight(1f))
            MilkButton(
                label = "取消",
                onClick = onDismiss,
                variant = MilkButtonVariant.SECONDARY,
            )
            Spacer(Modifier.width(MilkSpace.sm))
            MilkButton(
                label = saveLabel,
                onClick = onSave,
                enabled = saveEnabled,
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MilkSpace.md),
            content = content,
        )
    }
}

// ==================== 声明式设置字段 ====================

/**
 * 工具设置字段:有 `choices` 渲染为小型互斥胶囊组,否则渲染为等宽输入框。
 * 所有 `ToolSettingOption` 都由这一个入口渲染,新增工具无需改设置页。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MilkToolSettingField(
    option: ToolSettingOption,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (option.choices.isNotEmpty()) {
        Column(modifier.fillMaxWidth()) {
            MilkSectionLabel(option.label)
            Spacer(Modifier.height(MilkSpace.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                option.choices.forEach { (key, label) ->
                    MilkChip(
                        label = label,
                        selected = value == key,
                        onClick = { onValueChange(key) },
                        small = true,
                    )
                }
            }
        }
    } else {
        MilkTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            placeholder = option.placeholder,
            label = option.label,
            mono = true,
            onClear = { onValueChange("") },
        )
    }
}

// ==================== 工具调用轨迹 ====================

/**
 * 工具调用轨迹:每条调用一行(状态符号 + 名称 + 摘要 + 展开箭头),
 * 展开后显示参数与输出(限行、不内嵌滚动,避免与对话 LazyColumn 抢手势)。
 */
@Composable
fun MilkToolTraceList(
    traces: List<ToolTrace>,
    modifier: Modifier = Modifier,
) {
    if (traces.isEmpty()) return
    MilkSurfaceGroup(modifier) {
        traces.forEachIndexed { index, trace ->
            if (index > 0) MilkHairlineDivider()
            MilkToolTraceRow(trace)
        }
    }
}

@Composable
private fun MilkToolTraceRow(trace: ToolTrace) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalMilkMotion.current
    var expanded by remember(trace.callId) { mutableStateOf(false) }
    val summary = trace.resultSummary.ifBlank { trace.actionSummary }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .heightIn(min = 36.dp)
                .padding(horizontal = 10.dp, vertical = MilkSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolTraceGlyph(trace.status)
            Spacer(Modifier.width(MilkSpace.sm))
            Text(
                text = trace.displayName,
                style = MilkText.label,
                color = cs.onSurface,
                maxLines = 1,
            )
            if (summary.isNotBlank()) {
                Spacer(Modifier.width(MilkSpace.xs))
                Text(
                    text = summary,
                    style = MilkText.caption,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.width(MilkSpace.xxs))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(if (expanded) 90f else 0f),
                tint = cs.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduced) {
                fadeIn(tween(MilkMotion.reduced))
            } else {
                expandVertically(
                    animationSpec = tween(MilkMotion.enter, easing = MilkMotion.EaseOut),
                    expandFrom = Alignment.Top,
                ) + fadeIn(tween(MilkMotion.color, easing = MilkMotion.EaseOut))
            },
            exit = if (reduced) {
                fadeOut(tween(MilkMotion.reduced))
            } else {
                shrinkVertically(
                    animationSpec = tween(MilkMotion.exit, easing = MilkMotion.EaseOut),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(tween(MilkMotion.press, easing = MilkMotion.EaseOut))
            },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = MilkSpace.md, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(MilkSpace.xxs),
            ) {
                val args = trace.argumentsJson.trim()
                if (args.isNotEmpty() && args != "{}") {
                    Text("参数", style = MilkText.monoMicro, color = cs.onSurfaceVariant)
                    Text(
                        text = args,
                        style = MilkText.monoMicro,
                        color = cs.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (trace.output.isNotBlank()) {
                    Text("输出", style = MilkText.monoMicro, color = cs.onSurfaceVariant)
                    Text(
                        text = trace.output.trim(),
                        style = MilkText.monoMicro,
                        color = cs.onSurface,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 轨迹状态符号:进行中/待确认 = 色点;完成 = 勾;被拒/出错 = 中性/错误图标 */
@Composable
private fun ToolTraceGlyph(status: ToolTrace.Status) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        when (status) {
            ToolTrace.Status.RUNNING ->
                Box(Modifier.size(7.dp).clip(CircleShape).background(cs.primary))

            ToolTrace.Status.PENDING_CONFIRM ->
                Box(Modifier.size(7.dp).clip(CircleShape).background(LocalMilkWarning.current))

            ToolTrace.Status.DONE ->
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = LocalMilkSuccess.current,
                )

            ToolTrace.Status.DENIED ->
                Icon(
                    Icons.Outlined.Block,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = cs.onSurfaceVariant.copy(alpha = 0.7f),
                )

            ToolTrace.Status.ERROR ->
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = cs.error,
                )
        }
    }
}

// ==================== 高危操作确认 ====================

/**
 * 待确认操作条:轻质感 hairline 面 + 左侧 3dp 色条。
 * 上行 = 工具名 + 「需确认」徽标,中行 = 动作人话描述,下行 = 固定宽度取消/确认。
 */
@Composable
fun MilkToolConfirmBar(
    confirmation: PendingToolConfirmation,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val warning = LocalMilkWarning.current
    val shape = RoundedCornerShape(MilkRadius.lg)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(cs.surface)
            .border(1.dp, warning.copy(alpha = 0.42f), shape),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(warning))
        Column(
            Modifier
                .weight(1f)
                .padding(start = MilkSpace.md, top = 10.dp, end = MilkSpace.md, bottom = MilkSpace.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = warning,
                )
                Spacer(Modifier.width(MilkSpace.xs))
                Text(
                    text = confirmation.displayName,
                    style = MilkText.label,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(MilkSpace.sm))
                MilkStatusBadge(
                    text = if (confirmation.toolId == BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID) {
                        "需授权"
                    } else {
                        "需确认"
                    },
                    tone = MilkStatusTone.WARNING,
                )
            }
            Spacer(Modifier.height(MilkSpace.xxs))
            Text(
                text = confirmation.actionSummary,
                style = MilkText.body,
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (confirmation.detail.isNotBlank()) {
                Spacer(Modifier.height(MilkSpace.xxs))
                Text(
                    text = confirmation.detail,
                    style = MilkText.caption,
                    color = cs.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(MilkSpace.xs))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MilkInlineAction(
                    label = confirmation.cancelLabel,
                    onClick = onReject,
                    minWidth = 72.dp,
                )
                Spacer(Modifier.width(MilkSpace.xxs))
                MilkInlineAction(
                    label = confirmation.confirmLabel,
                    onClick = onApprove,
                    tone = MilkInlineTone.ACCENT,
                    minWidth = 72.dp,
                )
            }
        }
    }
}
