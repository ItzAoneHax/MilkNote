package com.milknote.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SystemUpdate
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import com.milknote.app.ui.common.innerHighlight
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.common.softHover
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.LocalMilkWarning
import com.milknote.app.ui.theme.Mono

/**
 * 设置组件套件(A2.7,Codex/ZCode 设置页调研收敛):
 * - 骨架 = 左导航(分组小标题+图标行,选中=淡蓝胶囊) + 右侧 ≤560dp 居中列
 * - 行 = 左(标题+灰副题)/右(控件),行间 hairline;值列「当前值 ›」,选择器=行下原地展开(✓ 标当前)
 * - 纪律:mono 只用于值/URL/id/数字;蓝仅激活态;即时生效优先,显式输入才出「保存」
 */

// ==================== 导航栏 ====================

internal data class SettingsNavItem(val key: String, val label: String, val icon: ImageVector)

/** 设置页五个导航项(key 对应 SettingsScreen 的 page 路由) */
internal val SETTINGS_NAV_GROUPS: List<Pair<String, List<SettingsNavItem>>> = listOf(
    "常规" to listOf(
        SettingsNavItem("appearance", "外观", Icons.Outlined.Palette),
        SettingsNavItem("writing", "书写", Icons.Outlined.Draw),
        SettingsNavItem("updates", "更新", Icons.Outlined.SystemUpdate),
    ),
    "设备" to listOf(
        SettingsNavItem("pen", "笔与手势", Icons.Outlined.Gesture),
    ),
    "Agent" to listOf(
        SettingsNavItem("agent", "Provider 与模型", Icons.Outlined.SmartToy),
    ),
)

/** 左侧导航栏:分组小标题 + 图标行;选中 = 淡蓝填充胶囊;底部 mono 版本号(ZCode 式) */
@Composable
internal fun SettingsNavRail(
    selected: String,
    onSelect: (String) -> Unit,
    versionLine: String,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onBack)
                .softHover()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("设置", style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            SETTINGS_NAV_GROUPS.forEach { (group, items) ->
                Text(
                    group,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp),
                )
                items.forEach { item ->
                    val active = item.key == selected
                    val itemBg by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                        animationSpec = tween(200),
                        label = "navItemBg",
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(itemBg)
                            .clickable { onSelect(item.key) }
                            .softHover()
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            item.icon, null,
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(18.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            item.label,
                            fontSize = 14.sp,
                            fontWeight = if (active) FontWeight.Medium else null,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Text(
            versionLine,
            fontFamily = Mono, fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

// ==================== 页面与分区 ====================

/** 页头:标题(普通大小写)+ 一行弱化副标题(Codex "Changes save automatically" 位) */
@Composable
internal fun SettingsPageHeader(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 小节标题(卡外):15sp SemiBold + 可选灰说明 */
@Composable
internal fun SettingsSectionHeader(title: String, description: String? = null) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        description?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 设置卡:surface 底 + 16dp 圆角 + 1dp hairline 边框 + 轻软阴影/顶缘内高光;卡内零 padding,由行自带 */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .softShadow(3.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .innerHighlight(),
    ) { content() }
}

/** 行间 hairline(start 对齐行文字缩进) */
@Composable
internal fun Hairline() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(start = 16.dp),
    )
}

// ==================== 行与控件 ====================

/** 设置行模板:左(标题+灰副题)/右(trailing 控件),min 高 56dp */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick).softHover() else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing(this)
        }
    }
}

/** 值列:当前值(mono 按需)+ ›(可进入下一级;Codex "value ›") */
@Composable
internal fun RowScope.RowValue(value: String, mono: Boolean = false) {
    Text(
        value,
        fontSize = 13.sp,
        fontFamily = if (mono) Mono else null,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.width(6.dp))
    Text("›", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 选择行:点击展开候选列表,✓ 标当前项,点选即收起(替换 AlertDialog+RadioButton) */
@Composable
internal fun <T> SelectRow(
    title: String,
    items: List<T>,
    selected: T?,
    label: (T) -> String,
    onPick: (T) -> Unit,
    subtitle: String? = null,
    mono: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.animateContentSize(tween(200))) {
        SettingsRow(
            title = title,
            subtitle = subtitle,
            onClick = { open = !open },
            trailing = { RowValue(value = selected?.let(label) ?: "—", mono = mono) },
        )
        if (open) {
            Hairline()
            InlineSelectionList(items = items, selected = selected, label = label) {
                onPick(it)
                open = false
            }
        }
    }
}

/** 原地展开的候选列表(inset 面板;✓ 蓝=当前项,当前项加粗) */
@Composable
internal fun <T> InlineSelectionList(
    items: List<T>,
    selected: T?,
    label: (T) -> String,
    onPick: (T) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(vertical = 4.dp),
    ) {
        Column(
            Modifier
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            items.forEach { item ->
                val isSel = item == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPick(item) }
                        .softHover()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (isSel) "✓" else " ",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(18.dp),
                    )
                    Text(
                        label(item),
                        fontSize = 14.sp,
                        fontWeight = if (isSel) FontWeight.Medium else null,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

/** 胶囊开关:on = 蓝轨白 thumb / off = 灰轨灰 thumb(ZCode 式;轨道/描边颜色 200ms 过渡) */
@Composable
internal fun MilkSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val thumbX by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(200),
        label = "milkSwitchThumb",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "milkSwitchTrack",
    )
    val trackBorder by animateColorAsState(
        targetValue = if (checked) Color.Transparent else MaterialTheme.colorScheme.outline,
        animationSpec = tween(200),
        label = "milkSwitchBorder",
    )
    Box(
        Modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(CircleShape)
            .background(trackColor)
            .border(1.dp, trackBorder, CircleShape)
            .clickable { onChange(!checked) },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbX)
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (checked) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        )
    }
}

/** 文字操作钮(13sp Medium;primary=蓝字,danger=红字;带按压缩放) */
@Composable
internal fun TextAction(
    label: String,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val fg by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            danger -> MaterialTheme.colorScheme.error
            primary -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onBackground
        },
        animationSpec = tween(200),
        label = "textActionFg",
    )
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = fg,
        modifier = Modifier
            .clip(CircleShape)
            .softClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 状态行:✓/⚠/✗ 前缀 + 单色文字(Codex 警告行式,不用弹窗) */
internal enum class StatusTone { NEUTRAL, OK, WARN, ERROR }

@Composable
internal fun StatusLine(
    text: String,
    tone: StatusTone = StatusTone.NEUTRAL,
    mono: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val color = when (tone) {
        StatusTone.OK -> MaterialTheme.colorScheme.primary
        StatusTone.WARN -> LocalMilkWarning.current
        StatusTone.ERROR -> MaterialTheme.colorScheme.error
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (tone != StatusTone.NEUTRAL) {
            Text(
                when (tone) {
                    StatusTone.OK -> "✓"
                    StatusTone.WARN -> "⚠"
                    StatusTone.ERROR -> "✗"
                    StatusTone.NEUTRAL -> ""
                },
                fontSize = 12.sp,
                color = color,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontFamily = if (mono) Mono else null,
            color = color,
        )
    }
}

/** 输入框:值按需 mono;脏(≠源值)时描边变蓝并出「保存/取消」(ZCode 显式保存行式);compact=去掉外层行距(弹窗/面板内) */
@Composable
internal fun SettingsInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    mono: Boolean = true,
    mask: Boolean = false,
    dirty: Boolean = false,
    onSave: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val boxBorder by animateColorAsState(
        targetValue = when {
            focused || dirty -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(200),
        label = "inputBorder",
    )
    Column(
        modifier.fillMaxWidth().then(
            if (compact) Modifier.padding(vertical = 6.dp) else Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ),
    ) {
        label?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, boxBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        fontFamily = if (mono) Mono else null,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = if (mono) Mono else null,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    visualTransformation = if (mask) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                )
            }
            if (dirty && onSave != null) {
                Spacer(Modifier.width(8.dp))
                TextAction("保存", primary = true, onClick = onSave)
                if (onCancel != null) TextAction("取消", onClick = onCancel)
            }
        }
    }
}

/** 卡外底部提示行(命令类 mono,散文类 sans) */
@Composable
internal fun FooterHint(text: String, mono: Boolean = false) {
    Text(
        text,
        fontSize = if (mono) 11.sp else 12.sp,
        lineHeight = if (mono) 15.sp else 17.sp,
        fontFamily = if (mono) Mono else null,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** 多选胶囊(思考档位/输入类型声明等):on = 淡蓝底蓝字,off = 灰底灰字;颜色 200ms 过渡 */
@Composable
internal fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        animationSpec = tween(200),
        label = "toggleChipBg",
    )
    val fg by animateColorAsState(
        targetValue = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "toggleChipFg",
    )
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = if (on) FontWeight.Medium else null,
        color = fg,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
