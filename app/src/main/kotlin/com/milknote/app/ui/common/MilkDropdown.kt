package com.milknote.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.milkPopoverEnter

/**
 * 苹果风格下拉选择器(DESIGN-V2 · 与 [MilkTextField] 同轴):
 * - 触发器:单行恒为 42dp、`MilkRadius.md` 圆角、surfaceVariant 0.45 底 + hairline;
 *   展开时描边加粗到 1.5dp 并转强调色,右侧箭头 0→180° 弹簧翻转;
 * - 弹出层:宽度与触发器等宽(不小于 200dp)、`milkPopoverEnter` 从触发边上沿长出、
 *   `milkGlassRim` 玻璃受光面,最多 260dp 高可滚动;
 * - 选项行 40dp:选中行淡强调底 + **固定 20dp 勾选槽位**(有勾无勾都不改行宽,零位移);
 * - 可选底部动作(如「新建文件夹」):hairline 分隔 + 强调色动作行,点完即收起。
 *
 * 选中态只由 [value] 表达,组件自身不持有选择状态;展开态为纯 UI 状态。
 */
@Composable
fun <T> MilkDropdownField(
    value: T,
    options: List<T>,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    leadingIcon: ImageVector? = null,
    optionIcon: ((T) -> ImageVector?)? = null,
    footerActionLabel: String? = null,
    footerActionIcon: ImageVector? = Icons.Outlined.Add,
    onFooterAction: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val reduced = LocalMilkMotion.current
    val density = LocalDensity.current

    var expanded by remember { mutableStateOf(false) }
    var triggerWidthPx by remember { mutableIntStateOf(0) }

    val borderColor by animateColorAsState(
        if (expanded) cs.primary else cs.outline,
        MilkMotion.colorSpec(), label = "milkDropdownBorder",
    )
    val borderWidth by animateDpAsState(
        if (expanded) 1.5.dp else 1.dp,
        MilkMotion.colorSpec(), label = "milkDropdownBorderWidth",
    )
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduced) snap() else MilkMotion.uiSpring(),
        label = "milkDropdownChevron",
    )

    val shape = RoundedCornerShape(MilkRadius.md)
    val fg = if (enabled) cs.onBackground else cs.onSurfaceVariant.copy(alpha = 0.5f)

    Column(modifier.fillMaxWidth()) {
        label?.let {
            Text(
                text = it,
                style = MilkText.eyebrow,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, bottom = MilkSpace.xs),
            )
        }
        // 触发器与弹出层同属一个锚点 Box:Popup 的 TopStart 即触发器左上角
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { triggerWidthPx = it.size.width }
                    .height(42.dp)
                    .clip(shape)
                    .background(cs.surfaceVariant.copy(alpha = 0.45f))
                    .border(borderWidth, borderColor, shape)
                    .clickable(
                        enabled = enabled,
                        role = Role.DropdownList,
                    ) { expanded = !expanded }
                    .padding(horizontal = 12.dp)
                    .testTag("milk-dropdown-trigger"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.let {
                    Icon(it, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = labelFor(value),
                    style = MilkText.body,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(MilkSpace.xs))
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (expanded) cs.primary else cs.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = chevron },
                )
            }

            if (expanded) {
                val popupWidth = with(density) { triggerWidthPx.toDp() }
                    .coerceAtLeast(200.dp)
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, with(density) { 42.dp.roundToPx() }),
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        Modifier
                            .width(popupWidth)
                            .milkPopoverEnter(TransformOrigin(0.5f, 0f))
                            .milkGlassRim(RoundedCornerShape(MilkRadius.lg), dark, 12.dp)
                            .padding(6.dp)
                            .testTag("milk-dropdown-panel"),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            options.forEach { option ->
                                DropdownOptionRow(
                                    label = labelFor(option),
                                    selected = option == value,
                                    icon = optionIcon?.invoke(option),
                                    onClick = {
                                        expanded = false
                                        onSelect(option)
                                    },
                                )
                            }
                        }
                        if (footerActionLabel != null && onFooterAction != null) {
                            MilkHairlineDivider(Modifier.padding(vertical = 4.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(MilkRadius.sm))
                                    .clickable(role = Role.Button) {
                                        expanded = false
                                        onFooterAction()
                                    }
                                    .heightIn(min = 40.dp)
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                footerActionIcon?.let {
                                    Icon(it, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(9.dp))
                                }
                                Text(
                                    footerActionLabel,
                                    style = MilkText.body,
                                    color = cs.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 下拉选项行:40dp、选中淡强调底;右侧 20dp 勾选槽位常驻,行宽不随选中态变化 */
@Composable
private fun DropdownOptionRow(
    label: String,
    selected: Boolean,
    icon: ImageVector?,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) cs.primary.copy(alpha = 0.09f) else Color.Transparent,
        MilkMotion.colorSpec(), label = "milkDropdownOptionBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MilkRadius.sm))
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 10.dp)
            .testTag("milk-dropdown-option-$label"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = if (selected) cs.primary else cs.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = label,
            style = MilkText.body,
            color = if (selected) cs.primary else cs.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
