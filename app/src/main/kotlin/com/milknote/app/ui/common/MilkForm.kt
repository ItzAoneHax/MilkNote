package com.milknote.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.Mono
import com.milknote.app.ui.theme.MonoMeta
import com.milknote.app.ui.theme.PillShape
import com.milknote.app.ui.theme.SurfaceElevatedDark
import com.milknote.app.ui.theme.milkPanelEnter

/**
 * 共享表单控件（第二代 · DESIGN-V2）：芯片 / 色点 / 分段控件 / 输入框 / 开关 / 键帽 / 确认弹窗。
 * 视觉纪律：hairline 描边 + 平涂底色，仅分段选中段带轻柔投影；无渐变、无内高光。
 */

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
            style = MilkText.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (description != null) {
            Spacer(Modifier.height(MilkSpace.xxs - 1.dp))
            Text(
                text = description,
                style = MilkText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

// ==================== 芯片 ====================

/**
 * 互斥选项胶囊：视觉胶囊 32/34dp,但外框承载 ≥48dp 命中区、testTag 与读屏角色。
 * 选中 = 淡蓝底 + 1dp 强调描边;未选中 = surface 底 + hairline。
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
    role: Role = Role.RadioButton,
) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalMilkMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.96f else 1f,
        animationSpec = tween(MilkMotion.press, easing = MilkMotion.EaseOut),
        label = "milkChipScale",
    )
    val colorSpec = if (reduced) snap<Color>() else MilkMotion.colorSpec<Color>()
    val bg by animateColorAsState(
        when {
            selected -> cs.primary.copy(alpha = 0.11f)
            else -> cs.surface
        },
        colorSpec, label = "milkChipBg",
    )
    val border by animateColorAsState(
        when {
            !enabled -> cs.outline.copy(alpha = 0.6f)
            selected -> cs.primary
            else -> cs.outline
        },
        colorSpec, label = "milkChipBorder",
    )
    val fg by animateColorAsState(
        when {
            !enabled -> cs.onSurfaceVariant.copy(alpha = 0.38f)
            selected -> cs.primary
            else -> cs.onSurfaceVariant
        },
        colorSpec, label = "milkChipFg",
    )

    Box(
        modifier
            .testTag("milk-chip-$label")
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = role,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .height(if (small) 32.dp else 34.dp)
                .clip(PillShape)
                .background(bg)
                .border(1.dp, border, PillShape)
                .padding(horizontal = if (small) 12.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.let {
                it()
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MilkText.label,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 触发一次动作的胶囊；与互斥选项分开声明读屏角色。 */
@Composable
fun MilkActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
) {
    MilkChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        small = small,
        leading = leading,
        role = Role.Button,
    )
}

/**
 * 多选筛选胶囊(标签/类型过滤):与 MilkChip 同一视觉,读屏角色为 Checkbox,
 * 选中时在文字后补一枚 14dp 细勾。
 */
@Composable
fun MilkFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalMilkMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.96f else 1f,
        animationSpec = tween(MilkMotion.press, easing = MilkMotion.EaseOut),
        label = "milkFilterChipScale",
    )
    val colorSpec = if (reduced) snap<Color>() else MilkMotion.colorSpec<Color>()
    val bg by animateColorAsState(
        if (selected) cs.primary.copy(alpha = 0.11f) else cs.surface,
        colorSpec, label = "milkFilterChipBg",
    )
    val border by animateColorAsState(
        when {
            !enabled -> cs.outline.copy(alpha = 0.6f)
            selected -> cs.primary
            else -> cs.outline
        },
        colorSpec, label = "milkFilterChipBorder",
    )
    val fg by animateColorAsState(
        when {
            !enabled -> cs.onSurfaceVariant.copy(alpha = 0.38f)
            selected -> cs.primary
            else -> cs.onSurfaceVariant
        },
        colorSpec, label = "milkFilterChipFg",
    )

    Box(
        modifier
            .testTag("milk-filter-chip-$label")
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .height(if (small) 32.dp else 34.dp)
                .clip(PillShape)
                .background(bg)
                .border(1.dp, border, PillShape)
                .padding(horizontal = if (small) 12.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.let {
                it()
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MilkText.label,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Spacer(Modifier.width(5.dp))
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** 芯片流式布局的行间距约定:横向 6dp、纵向 6dp */
object MilkChipSpacing {
    val horizontal = MilkSpace.xs
    val vertical = MilkSpace.xs
}

// ==================== 色点 ====================

/**
 * 色点选择器(封面/纸色/墨色/文字色共用):16dp 色点 + hairline;
 * 选中 = 色点放大 1.18 倍 + 外扩 2dp 强调色光环(弹簧过渡);
 * 暗色主题下给深色/纯白色点补一圈可见边界。
 */
@Composable
fun MilkColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotSize: Dp = 16.dp,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val reduced = LocalMilkMotion.current
    val ringSize by animateDpAsState(
        targetValue = if (selected) dotSize + 11.dp else dotSize + 2.dp,
        animationSpec = if (reduced) snap() else MilkMotion.smoothSpring(),
        label = "milkDotRing",
    )
    val ringColor by animateColorAsState(
        if (selected) cs.primary else if (dark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.16f),
        MilkMotion.colorSpec(), label = "milkDotRingColor",
    )
    val desc = contentDescription?.let { if (selected) "$it,已选中" else it }
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { if (desc != null) this.contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(ringSize)
                .border(if (selected) 1.5.dp else 1.dp, ringColor, CircleShape),
        )
        Box(
            Modifier
                .size(if (selected) dotSize * 1.18f else dotSize)
                .clip(CircleShape)
                .background(color)
                .border(
                    0.5.dp,
                    if (dark) Color.White.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.10f),
                    CircleShape,
                ),
        )
    }
}

// ==================== 分段控件 ====================

/**
 * 苹果风格滑动分段控件:互斥少量选项(2–4 个)。
 * 默认外层 48dp 命中、36dp 视觉轨道;compact 时外层与轨道同为 34dp(窄侧栏顶条)。
 * 选中滑块用弹簧跟随下标滑动,减弱动效时即时切换。
 */
@Composable
fun <T> MilkSegmented(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTagFor: ((T) -> String)? = null,
    compact: Boolean = false,
) {
    if (options.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val reduced = LocalMilkMotion.current
    val haptics = LocalHapticFeedback.current

    val outerHeight = if (compact) 34.dp else 48.dp
    val trackHeight = if (compact) 34.dp else 36.dp
    val pad = if (compact) 2.dp else 3.dp
    val trackShape = RoundedCornerShape(if (compact) MilkRadius.sm else MilkRadius.md)
    val thumbShape = RoundedCornerShape(if (compact) MilkRadius.xs else MilkRadius.sm)

    val selectedIndex = options.indexOf(selected)
    val animatedIndex by animateFloatAsState(
        targetValue = if (selectedIndex >= 0) selectedIndex.toFloat() else 0f,
        animationSpec = if (reduced || selectedIndex < 0) snap() else MilkMotion.uiSpring(),
        label = "milkSegIndex",
    )

    BoxWithConstraints(modifier.fillMaxWidth().height(outerHeight)) {
        val availableWidth = if (maxWidth == Dp.Infinity) 280.dp else maxWidth
        val segmentWidth = (availableWidth - pad * 2) / options.size
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(trackHeight)
                .background(cs.surfaceVariant.copy(alpha = 0.7f), trackShape),
        )
        if (selectedIndex >= 0) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = pad + segmentWidth * animatedIndex)
                    .width(segmentWidth)
                    .height(trackHeight - pad * 2)
                    .softShadow(2.dp, thumbShape)
                    .clip(thumbShape)
                    .background(if (dark) SurfaceElevatedDark else cs.surface),
            )
        }
        Row(Modifier.fillMaxSize()) {
            options.forEach { option ->
                val active = option == selected
                val interaction = remember(option) { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val pressAmount by animateFloatAsState(
                    targetValue = if (pressed) 1f else 0f,
                    animationSpec = tween(if (pressed) 60 else MilkMotion.exit),
                    label = "milkSegPress",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = {
                                if (option != selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onSelect(option)
                            },
                        )
                        .then(testTagFor?.let { Modifier.testTag(it(option)) } ?: Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pressAmount > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(trackHeight - pad * 2)
                                .clip(thumbShape)
                                .background(
                                    (if (dark) Color.White else Color.Black)
                                        .copy(alpha = pressAmount * if (dark) 0.09f else 0.06f),
                                ),
                        )
                    }
                    Text(
                        text = label(option),
                        style = MilkText.label.copy(
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = when {
                            !enabled -> cs.onSurfaceVariant.copy(alpha = 0.38f)
                            active -> cs.onBackground
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
 * 统一输入框:单行恒为 42dp 高、10dp 圆角、hairline 描边;聚焦/脏值时描边变强调色并加粗到 1.5dp。
 * label 以眉标形式置于框上方;mono 用于值/URL/Key;传 onClear 时右侧常驻清空钮槽位
 * (无内容时只占位不显示图标),因此输入与清空不会改变字段高度或宽度。
 * [visualTransformation] 非空时优先于 [mask](自定义遮蔽/格式化),默认 null 保持既有行为。
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
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (() -> Unit)? = null,
    textStyle: TextStyle = MilkText.body,
    onClear: (() -> Unit)? = null,
    visualTransformation: VisualTransformation? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val highlighted = focused || dirty
    val compact = singleLine || minLines <= 1
    val borderColor by animateColorAsState(
        if (highlighted) cs.primary else cs.outline,
        MilkMotion.colorSpec(), label = "milkFieldBorder",
    )
    val borderWidth by animateDpAsState(
        if (highlighted) 1.5.dp else 1.dp,
        MilkMotion.colorSpec(), label = "milkFieldBorderWidth",
    )
    val container = if (filled) cs.surfaceVariant.copy(alpha = 0.45f) else cs.surface
    val textColor = if (enabled) cs.onBackground else cs.onSurfaceVariant.copy(alpha = 0.5f)
    val fontFamily = if (mono) Mono else null
    val shape = RoundedCornerShape(MilkRadius.md)

    Column(modifier.fillMaxWidth()) {
        label?.let {
            Text(
                text = it,
                style = MilkText.eyebrow,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, bottom = MilkSpace.xs),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(container)
                .border(borderWidth, borderColor, shape)
                .then(
                    if (compact) {
                        // 28dp 清空钮 + 6dp*2 = 40dp ≤ 42dp:单行字段高度恒为 42dp,填空不改高
                        Modifier.heightIn(min = 42.dp).padding(horizontal = 12.dp, vertical = 6.dp)
                    } else {
                        Modifier
                            .heightIn(min = 40.dp * minLines.coerceAtLeast(1))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    },
                ),
            verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Top,
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
                    visualTransformation = visualTransformation
                        ?: if (mask) PasswordVisualTransformation() else VisualTransformation.None,
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                )
            }
            if (onClear != null) {
                // 槽位常驻:图标只在有内容可清时出现,但 28dp 宽度与间距始终占位——输入/清空零位移
                Spacer(Modifier.width(MilkSpace.xs))
                val clearable = value.isNotEmpty() && enabled
                Box(
                    Modifier
                        .size(28.dp)
                        .then(
                            if (clearable) {
                                Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onClear)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (clearable) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "清空",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            trailing?.let {
                Spacer(Modifier.width(10.dp))
                it()
            }
        }
    }
}

// ==================== 对话输入框 ====================

/**
 * AI 输入框(composer):`MilkRadius.xl` 胶囊形、聚焦时描边变强调色并加粗 1→1.5dp、底色淡提亮。
 * 与 [MilkTextField] 的分工:字段用于设置表单(42dp 单行),本控件用于对话输入(可多行 + 前后插槽)。
 * 内层文本区固定 40dp 起,单行时与两侧 40dp 图标垂直同轴,多行时向上生长。
 */
@Composable
fun MilkComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    minLines: Int = 1,
    maxLines: Int = 4,
    enabled: Boolean = true,
    dirty: Boolean = false,
    textStyle: TextStyle = MilkText.body,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Default,
    onDone: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val highlighted = focused || dirty
    val borderColor by animateColorAsState(
        if (highlighted) cs.primary else cs.outline,
        MilkMotion.colorSpec(), label = "milkComposerBorder",
    )
    val borderWidth by animateDpAsState(
        if (highlighted) 1.5.dp else 1.dp,
        MilkMotion.colorSpec(), label = "milkComposerBorderWidth",
    )
    val container by animateColorAsState(
        if (highlighted) cs.surface else cs.surfaceVariant.copy(alpha = 0.45f),
        MilkMotion.colorSpec(), label = "milkComposerBg",
    )
    val textColor = if (enabled) cs.onBackground else cs.onSurfaceVariant.copy(alpha = 0.5f)
    val shape = RoundedCornerShape(MilkRadius.xl)

    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(borderWidth, borderColor, shape)
            .heightIn(min = 48.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = if (maxLines <= 1) Alignment.CenterVertically else Alignment.Bottom,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(4.dp))
        }
        // 内层固定 40dp 起(20dp 行高 + 10dp 上下留白):单行文字与两侧 40dp 图标严格同轴;
        // 2–4 行时文本向上生长,图标贴底,不再出现单行贴底边的错位。
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .padding(horizontal = 6.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = textStyle.copy(color = cs.onSurfaceVariant.copy(alpha = 0.6f)),
                    maxLines = maxLines,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                minLines = minLines,
                maxLines = maxLines,
                textStyle = textStyle.copy(color = textColor),
                visualTransformation = visualTransformation,
                cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        trailing?.let {
            Spacer(Modifier.width(4.dp))
            it()
        }
    }
}

// ==================== 开关 ====================
/**
 * 苹果风弹性开关:48dp 命中区 + 46×26 胶囊轨道 + 22dp 纯白拇指(2dp 柔影)。
 * 按下未松手拇指横向伸展到 26dp(贴边),松手回弹;切换零过冲弹簧 + TextHandleMove 触感。
 * 开 = 强调色轨;关 = 中性灰轨(亮 #D9DCE3 / 暗 #343842);禁用态降饱和。
 */
@Composable
fun MilkSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val reduced = LocalMilkMotion.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // 按下未松手:拇指横向伸展(开启向左 / 关闭向右),始终贴住所在侧边缘
    val thumbWidth by animateDpAsState(
        targetValue = if (pressed && !reduced) 26.dp else 22.dp,
        animationSpec = if (reduced) snap() else tween(MilkMotion.press, easing = MilkMotion.EaseOut),
        label = "milkSwitchThumbWidth",
    )
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (reduced) snap() else MilkMotion.uiSpring(),
        label = "milkSwitchProgress",
    )
    // 关闭 x = 2dp;开启 x = 46 - 2 - thumbWidth(贴右缘),伸展时位置随宽度联动
    val thumbX = 2.dp + (42.dp - thumbWidth) * progress
    val offTrack = if (dark) Color(0xFF343842) else Color(0xFFD9DCE3)
    val trackColor by animateColorAsState(
        when {
            !enabled -> if (checked) cs.primary.copy(alpha = 0.38f) else offTrack.copy(alpha = 0.55f)
            checked -> cs.primary
            else -> offTrack
        },
        MilkMotion.colorSpec(), label = "milkSwitchTrack",
    )
    Box(
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(!checked)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 46.dp, height = 26.dp)
                .background(trackColor, CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = thumbX)
                    .size(width = thumbWidth, height = 22.dp)
                    .softShadow(2.dp, CircleShape)
                    .background(Color.White.copy(alpha = if (enabled) 1f else 0.8f), CircleShape),
            )
        }
    }
}

// ==================== 键帽 ====================

/** 键盘提示键帽(⌘K / ↵ / Esc):mono 小字 + hairline 圆角框 */
@Composable
fun MilkKeyCap(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Text(
        text = text,
        style = MonoMeta,
        color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(MilkRadius.xs))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

// ==================== 危险确认 ====================

/**
 * 统一确认弹窗(第二代 modal):面板标题 + 说明;底部 hairline 分隔 + 右对齐 取消/确认。
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
    icon: ImageVector? = null,
) {
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .milkPanelEnter()
                .milkDialogSurface(),
        ) {
            Column(
                Modifier.padding(
                    start = MilkSpace.xl,
                    top = MilkSpace.lg + 2.dp,
                    end = MilkSpace.xl,
                    bottom = MilkSpace.lg,
                ),
            ) {
                Text(title, style = MilkText.panelTitle, color = cs.onSurface)
                Spacer(Modifier.height(MilkSpace.xs))
                Text(body, style = MilkText.caption, color = cs.onSurfaceVariant)
            }
            HorizontalDivider(color = cs.outline)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MilkSpace.lg, vertical = MilkSpace.md),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MilkButton(cancelLabel, onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
                Spacer(Modifier.width(MilkSpace.sm))
                MilkButton(confirmLabel, onClick = onConfirm, destructive = destructive)
            }
        }
    }
}
