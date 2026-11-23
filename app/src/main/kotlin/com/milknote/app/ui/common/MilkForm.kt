package com.milknote.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.MilkEyebrow
import com.milknote.app.ui.theme.Mono
import com.milknote.app.ui.theme.MonoMeta
import com.milknote.app.ui.theme.PillShape
import com.milknote.app.ui.theme.milkPanelEnter

/**
 * 共享表单控件(视觉升级 2026-11):所有页面/面板统一使用,不再各自拼装。
 * 芯片 / 色点 / 分段控件 / 输入框 / 开关 / 键帽 / 危险确认弹窗。
 */

private val ColorTween = tween<Color>(durationMillis = 160)
private val DpTween = tween<Dp>(durationMillis = 180)

// ==================== 分组眉标 ====================

/** 分组小标题:眉标字 + 可选说明;用在表单分组、面板分区、列表分组 */
@Composable
fun MilkSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MilkEyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (description != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

// ==================== 芯片 ====================

/**
 * 统一选项芯片:选中 = 淡蓝底 + 蓝字 + 蓝描边;未选 = surface 底 + hairline。
 * 视觉高 40dp(small 34dp),命中区按 M3 最小交互尺寸外扩到 48dp(A-9)。
 */
@Composable
fun MilkChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) cs.primary.copy(alpha = 0.12f) else cs.surface,
        ColorTween, label = "milkChipBg",
    )
    val border by animateColorAsState(
        if (selected) cs.primary.copy(alpha = 0.55f) else cs.outline,
        ColorTween, label = "milkChipBorder",
    )
    val fg by animateColorAsState(
        if (selected) cs.primary else cs.onSurface,
        ColorTween, label = "milkChipFg",
    )
    Row(
        modifier
            .clip(PillShape)
            .background(bg)
            .border(1.dp, border, PillShape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .defaultMinSize(minHeight = if (small) 34.dp else 40.dp)
            .padding(horizontal = if (small) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Medium else null,
            color = if (enabled) fg else fg.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 芯片流式布局的行间距约定:横向 6dp、纵向 6dp */
object MilkChipSpacing {
    val horizontal = 6.dp
    val vertical = 6.dp
}

// ==================== 色点 ====================

/**
 * 色点选择器(封面/纸色/墨色/文字色共用):命中 44dp,视觉点 [dotSize];
 * 选中 = 外扩 2dp 蓝色光环,未选 = hairline 细边。
 */
@Composable
fun MilkColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotSize: Dp = 28.dp,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val ringSize by animateDpAsState(if (selected) dotSize + 8.dp else dotSize, DpTween, label = "milkDotRing")
    val desc = contentDescription?.let { if (selected) "$it,已选中" else it }
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { if (desc != null) this.contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(ringSize)
                .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
        )
        Box(
            Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape),
        )
    }
}

// ==================== 分段控件 ====================

/** 分段控件:互斥少量选项(2–4 个),选中段白底上浮;替代裸文字切换 */
@Composable
fun <T> MilkSegmented(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTagFor: ((T) -> String)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.7f))
            .padding(3.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            options.forEach { option ->
                val active = option == selected
                val bg by animateColorAsState(
                    if (active) cs.surface else Color.Transparent, ColorTween, label = "milkSegBg",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .clickable(enabled = enabled, role = Role.RadioButton, onClick = { onSelect(option) })
                        .then(testTagFor?.let { Modifier.testTag(it(option)) } ?: Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.Medium else null,
                        color = when {
                            !enabled -> cs.onSurfaceVariant.copy(alpha = 0.38f)
                            active -> cs.onSurface
                            else -> cs.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ==================== 输入框 ====================

/**
 * 统一输入框:填充式(surfaceVariant 淡底)或描边式;聚焦/脏值时描边变蓝。
 * label 以眉标形式置于框上方;mono 用于值/URL/Key。
 */
@Composable
fun MilkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    mono: Boolean = false,
    mask: Boolean = false,
    enabled: Boolean = true,
    filled: Boolean = true,
    dirty: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Default,
    onDone: (() -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    var focused by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val borderColor by animateColorAsState(
        when {
            focused || dirty -> cs.primary
            filled -> Color.Transparent
            else -> cs.outline
        },
        ColorTween, label = "milkFieldBorder",
    )
    val container = if (filled) cs.surfaceVariant.copy(alpha = 0.55f) else cs.surface
    val textColor = if (enabled) cs.onBackground else cs.onSurfaceVariant.copy(alpha = 0.5f)
    val fontFamily = if (mono) Mono else null

    Column(modifier.fillMaxWidth()) {
        label?.let {
            Text(
                text = it,
                style = MilkEyebrow,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(container)
                .border(1.dp, borderColor, MaterialTheme.shapes.medium)
                .heightIn(min = 48.dp * minLines.coerceAtLeast(1))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = if (singleLine || minLines <= 1) Alignment.CenterVertically else Alignment.Top,
        ) {
            leading?.let {
                it()
                Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.weight(1f), contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(
                            fontFamily = fontFamily,
                            color = cs.onSurfaceVariant.copy(alpha = 0.6f),
                        ),
                        maxLines = maxLines,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    enabled = enabled,
                    textStyle = textStyle.copy(fontFamily = fontFamily, color = textColor),
                    visualTransformation = if (mask) PasswordVisualTransformation() else VisualTransformation.None,
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(imeAction = imeAction),
                    keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                )
            }
            trailing?.let {
                Spacer(Modifier.width(10.dp))
                it()
            }
        }
    }
}

// ==================== 开关 ====================

/** 胶囊开关:on = 蓝轨 + 亮 thumb / off = 灰轨 + 灰边 + 白(暗色为浅灰)thumb */
@Composable
fun MilkSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val thumbX by animateDpAsState(if (checked) 22.dp else 2.dp, DpTween, label = "milkSwitchThumb")
    val trackColor by animateColorAsState(if (checked) cs.primary else cs.surfaceVariant, ColorTween, label = "milkSwitchTrack")
    val trackBorder by animateColorAsState(if (checked) Color.Transparent else cs.outline, ColorTween, label = "milkSwitchBorder")
    val thumbColor = if (checked) cs.onPrimary else if (dark) cs.onSurfaceVariant else cs.surface
    Box(
        modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(CircleShape)
            .background(trackColor)
            .border(1.dp, trackBorder, CircleShape)
            .clickable(enabled = enabled, role = Role.Switch, onClick = { onChange(!checked) }),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbX)
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

// ==================== 键帽 ====================

/** 键盘提示键帽(⌘K / ↵ / Esc):mono 小字 + hairline 圆角框 */
@Composable
fun MilkKeyCap(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Text(
        text = text,
        style = MonoMeta.copy(fontSize = 11.sp),
        color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

// ==================== 危险确认 ====================

/**
 * 统一确认弹窗:图标 + 标题 + 说明 + 右对齐动作;危险操作用红实心确认键。
 * 所有「删除 / 不可撤销」路径共用,替代各处手写 Dialog。
 */
@Composable
fun MilkConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = "取消",
    destructive: Boolean = true,
    icon: ImageVector? = if (destructive) Icons.Outlined.DeleteOutline else null,
) {
    val cs = MaterialTheme.colorScheme
    val tint = if (destructive) cs.error else cs.primary
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .milkPanelEnter()
                .milkDialogSurface()
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(tint.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(body, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MilkButton(cancelLabel, onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
                Spacer(Modifier.width(10.dp))
                MilkButton(confirmLabel, onClick = onConfirm, destructive = destructive)
            }
        }
    }
}
