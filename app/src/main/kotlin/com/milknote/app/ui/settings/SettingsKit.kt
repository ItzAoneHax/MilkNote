package com.milknote.app.ui.settings

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkChoicePanel
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkSwitch
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.LocalMilkWarning
import com.milknote.app.ui.theme.MilkEyebrow
import com.milknote.app.ui.theme.Mono
import com.milknote.app.ui.theme.MonoValue

/**
 * 设置组件套件(第二代 · DESIGN-V2):
 * - 左导航 224dp 平齐侧栏(眉标分组 + 38dp 图标行,选中 = 强调色淡底 + 强调色字)+ 顶部搜索框
 * - 内容列 ≤580dp 居中:18sp 标题 + 13sp 说明;分区 = 12sp 灰标签;列表 = 12dp 圆角 hairline 平涂面
 * - 行 ≥52dp:标题 14sp / 副题 12sp;值列 = 灰值 + 细箭头;仅浮层带阴影
 */

// ==================== 导航栏 ====================

internal data class SettingsNavItem(val key: String, val label: String, val icon: ImageVector)

/** 设置页导航项(key 对应 SettingsScreen 的 page 路由) */
internal val SETTINGS_NAV_GROUPS: List<Pair<String, List<SettingsNavItem>>> = listOf(
    "常规" to listOf(
        SettingsNavItem("appearance", "外观", Icons.Outlined.Palette),
        SettingsNavItem("writing", "书写", Icons.Outlined.Draw),
    ),
    "设备" to listOf(
        SettingsNavItem("pen", "触控笔", Icons.Outlined.Gesture),
        SettingsNavItem("shortcuts", "手势与快捷键", Icons.Outlined.Keyboard),
    ),
    "Agent" to listOf(
        SettingsNavItem("agent", "模型与隐私", Icons.Outlined.SmartToy),
    ),
    "数据" to listOf(
        SettingsNavItem("storage", "存储与更新", Icons.Outlined.SystemUpdate),
    ),
)

/** 左侧导航栏:224dp 平齐侧栏;顶部返回 + 标题 + 搜索框;分组眉标 + 38dp 图标行;底部 mono 版本号 */
@Composable
internal fun SettingsNavRail(
    selected: String,
    onSelect: (String) -> Unit,
    versionLine: String,
    onBack: () -> Unit,
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(cs.surfaceVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack, "返回",
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("设置", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
        }
        Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            MilkTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = "搜索设置…",
                mono = false,
                leading = {
                    Icon(Icons.Outlined.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(17.dp))
                },
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
        ) {
            SETTINGS_NAV_GROUPS.forEach { (group, items) ->
                Text(
                    group,
                    style = MilkEyebrow,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, top = 16.dp, bottom = 6.dp),
                )
                items.forEach { item ->
                    val active = item.key == selected
                    val itemBg by animateColorAsState(
                        targetValue = if (active) cs.primary.copy(alpha = 0.10f) else Color.Transparent,
                        animationSpec = tween(180),
                        label = "navItemBg",
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 1.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(itemBg)
                            .clickable { onSelect(item.key) }
                            .heightIn(min = 38.dp)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            item.icon, null,
                            tint = if (active) cs.primary else cs.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            item.label,
                            fontSize = 13.5.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) cs.primary else cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                versionLine,
                style = com.milknote.app.ui.theme.MonoMeta,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

// ==================== 页面与分区 ====================

/** 页头:18sp 标题 + 13sp 灰说明 */
@Composable
internal fun SettingsPageHeader(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(5.dp))
        Text(
            subtitle,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 分区标签:12sp 灰字 + 可选说明;卡外、小节之上 */
@Composable
internal fun SettingsSectionHeader(title: String, description: String? = null) {
    Column(Modifier.padding(top = 6.dp, bottom = 8.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        description?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
        }
    }
}

/** 设置列表面(第二代):12dp 圆角 + hairline + 平涂 surface,无投影无高光;卡内零 padding,由行自带 */
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
            .clip(MaterialTheme.shapes.large)
            .background(containerColor)
            .border(1.dp, borderColor, MaterialTheme.shapes.large),
    ) { content() }
}

/** 行间 hairline(start 对齐行文字缩进) */
@Composable
internal fun Hairline() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 16.dp),
    )
}

// ==================== 行与控件 ====================

/** 设置行模板:左(14sp 标题 + 12sp 灰副题)/右(trailing 控件),min 高 52dp */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) cs.onBackground else cs.onSurfaceVariant.copy(alpha = 0.5f),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = cs.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing(this)
        }
    }
}

/** 值列:当前值(mono 按需)+ 细箭头 */
@Composable
internal fun RowScope.RowValue(value: String, mono: Boolean = false) {
    Text(
        value,
        style = if (mono) MonoValue else MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.width(2.dp))
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.size(18.dp),
    )
}

/** 选择行:点击打开单选面板,✓ 标当前项,点选即关闭。 */
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
    SettingsRow(
        title = title,
        subtitle = subtitle,
        onClick = { open = true },
        trailing = { RowValue(value = selected?.let(label) ?: "—", mono = mono) },
    )
    if (open) {
        MilkChoicePanel(
            title = title,
            items = items,
            selected = selected,
            label = label,
            onPick = onPick,
            onDismiss = { open = false },
        )
    }
}

/** 原地展开的候选列表(inset 面板;单选圆点 = 当前项,当前项加粗) */
@Composable
internal fun <T> InlineSelectionList(
    items: List<T>,
    selected: T?,
    label: (T) -> String,
    onPick: (T) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.45f))
            .padding(4.dp),
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
                        .background(if (isSel) cs.primary.copy(alpha = 0.09f) else Color.Transparent)
                        .clickable { onPick(item) }
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, if (isSel) cs.primary else cs.outline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSel) Box(Modifier.size(8.dp).clip(CircleShape).background(cs.primary))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        label(item),
                        fontSize = 13.sp,
                        fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSel) cs.primary else cs.onBackground,
                    )
                }
            }
        }
    }
}

/** 文字操作钮(13sp Medium;primary=强调色字,danger=红字;38dp 高命中) */
@Composable
internal fun TextAction(
    label: String,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val fg by animateColorAsState(
        targetValue = when {
            !enabled -> cs.onSurfaceVariant.copy(alpha = 0.45f)
            danger -> cs.error
            primary -> cs.primary
            else -> cs.onBackground
        },
        animationSpec = tween(160),
        label = "textActionFg",
    )
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .softClickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 38.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** 状态行:16dp 语义图标 + 13sp 单色文字 */
internal enum class StatusTone { NEUTRAL, OK, WARN, ERROR }

@Composable
internal fun StatusLine(
    text: String,
    tone: StatusTone = StatusTone.NEUTRAL,
    mono: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val color = when (tone) {
        StatusTone.OK -> cs.primary
        StatusTone.WARN -> LocalMilkWarning.current
        StatusTone.ERROR -> cs.error
        StatusTone.NEUTRAL -> cs.onSurfaceVariant
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (tone != StatusTone.NEUTRAL) {
            Icon(
                when (tone) {
                    StatusTone.OK -> Icons.Outlined.CheckCircle
                    StatusTone.WARN -> Icons.Outlined.Warning
                    StatusTone.ERROR -> Icons.Outlined.ErrorOutline
                    StatusTone.NEUTRAL -> Icons.Outlined.Info
                },
                contentDescription = when (tone) {
                    StatusTone.OK -> "正常"
                    StatusTone.WARN -> "注意"
                    StatusTone.ERROR -> "错误"
                    StatusTone.NEUTRAL -> "信息"
                },
                tint = color,
                modifier = Modifier.padding(top = 1.dp).size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontFamily = if (mono) Mono else null,
            color = if (tone == StatusTone.NEUTRAL) cs.onSurfaceVariant else color,
        )
    }
}

/** 输入框:复用共享 MilkTextField;脏(≠源值)时描边变蓝并出「保存/取消」;compact=去掉外层行距 */
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
    Column(
        modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.padding(vertical = 4.dp) else Modifier.padding(horizontal = 16.dp, vertical = 10.dp)),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            MilkTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                label = label,
                placeholder = placeholder,
                mono = mono,
                mask = mask,
                dirty = dirty,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onDone = { if (dirty && onSave != null) onSave() },
            )
            if (dirty && onSave != null) {
                Spacer(Modifier.width(4.dp))
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
        fontSize = if (mono) 11.5.sp else 12.5.sp,
        lineHeight = if (mono) 16.sp else 18.sp,
        fontFamily = if (mono) Mono else null,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
        modifier = Modifier.padding(top = 10.dp),
    )
}

/** 多选胶囊(思考档位/输入类型声明等):复用共享 MilkChip(small) */
@Composable
internal fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    MilkChip(label = label, selected = on, onClick = onClick, small = true)
}

/** 键盘快捷键展示行:mono 键帽 + 说明 */
@Composable
internal fun ShortcutRow(title: String, keys: String, subtitle: String? = null) {
    val cs = MaterialTheme.colorScheme
    SettingsRow(
        title = title,
        subtitle = subtitle,
        trailing = {
            Text(
                keys,
                fontFamily = Mono,
                fontSize = 11.5.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier
                    .border(1.dp, cs.outline, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        },
    )
}

/** 单选项芯片组(值列):复用 MilkChip(small) */
@Composable
internal fun <T> ChipChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onPick: (T) -> Unit,
    subtitle: String? = null,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        trailing = {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                options.forEach { option ->
                    MilkChip(label = label(option), selected = option == selected, onClick = { onPick(option) }, small = true)
                }
            }
        },
    )
}
