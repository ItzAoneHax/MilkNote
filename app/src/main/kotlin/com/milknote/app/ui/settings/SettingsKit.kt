package com.milknote.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkChoicePanel
import com.milknote.app.ui.common.MilkFilterChip
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkIcons
import com.milknote.app.ui.common.MilkKeyCap
import com.milknote.app.ui.common.MilkRadioRow
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.milkEdgeHairline
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.LocalMilkSuccess
import com.milknote.app.ui.theme.LocalMilkWarning
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.PillShape

/**
 * 设置组件套件(第二代 · DESIGN-V2):
 * - 左导航 224dp 通栏侧栏(眉标分组 + 40dp 图标行,选中 = 强调色淡底 + 强调色字)+ 顶部搜索框;
 *   侧栏底色铺满全高并延伸进系统栏,只有内容吃 insets(消除上下白条)
 * - 窄屏:紧凑顶部栏(返回 + 标题 + 当前分组 + 横向胶囊条),内容列全宽
 * - 内容列 ≤640dp 居中:页面标题 + 说明;分区 = 眉标 + 12dp 圆角 hairline 平涂面
 * - 行 ≥52dp:标题 / 副题;值列 = 灰值 + 细箭头;仅浮层带阴影
 */

// ==================== 导航栏 ====================

internal data class SettingsNavItem(val key: String, val label: String, val icon: ImageVector)

/** 设置页导航项(key 对应 SettingsScreen 的 page 路由) */
internal val SETTINGS_NAV_GROUPS: List<Pair<String, List<SettingsNavItem>>> = listOf(
    "常规" to listOf(
        SettingsNavItem("appearance", "外观", MilkIcons.Palette),
        SettingsNavItem("writing", "书写", MilkIcons.FountainPen),
    ),
    "设备" to listOf(
        SettingsNavItem("pen", "触控笔", MilkIcons.Stylus),
        SettingsNavItem("shortcuts", "手势与快捷键", MilkIcons.Keyboard),
    ),
    "AI 教师" to listOf(
        SettingsNavItem("agent", "模型与隐私", MilkIcons.Sparkle),
        SettingsNavItem("skills_tools", "技能与工具", MilkIcons.Blocks),
    ),
    "数据" to listOf(
        SettingsNavItem("storage", "存储与更新", MilkIcons.StorageUpdate),
    ),
)

/** 当前页所属分组名(紧凑顶栏用位置提示;宽屏由侧栏分组眉标承担) */
internal fun settingsGroupOf(key: String): String =
    SETTINGS_NAV_GROUPS.firstOrNull { (_, items) -> items.any { it.key == key } }?.first ?: "设置"

/**
 * 左侧导航栏:224dp 通栏侧栏。
 * 根 Column 用 surfaceVariant 铺满全高(延伸进状态栏/导航栏),内层再加安全区 padding,
 * 这样侧栏底色不会在系统栏处断层。
 */
@Composable
internal fun SettingsNavRail(
    selected: String,
    onSelect: (String) -> Unit,
    versionLine: String,
    onBack: () -> Unit,
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
    searchNoMatch: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(cs.surfaceVariant)
            .milkEdgeHairline(cs.outline, right = true)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = MilkSpace.xxs, end = MilkSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MilkIconButton(
                imageVector = MilkIcons.ArrowLeft,
                contentDescription = "返回",
                onClick = onBack,
            )
            Spacer(Modifier.width(MilkSpace.xxs))
            Text("设置", style = MilkText.panelTitle, color = cs.onBackground)
        }

        SettingsSearchField(
            query = searchQuery,
            onQueryChange = onSearchChange,
            noMatch = searchNoMatch,
            modifier = Modifier.padding(horizontal = MilkSpace.sm),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MilkSpace.sm),
        ) {
            SETTINGS_NAV_GROUPS.forEach { (group, items) ->
                Text(
                    group,
                    style = MilkText.eyebrow,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, top = MilkSpace.lg, bottom = MilkSpace.xs),
                )
                items.forEach { item ->
                    SettingsNavRow(item = item, active = item.key == selected) { onSelect(item.key) }
                }
            }
            Spacer(Modifier.height(MilkSpace.md))
        }

        Box(Modifier.padding(horizontal = MilkSpace.lg, vertical = MilkSpace.md)) {
            // 大字体下版本号只占一行:超出省略,底脚永远清爽
            Text(
                versionLine,
                style = MilkText.monoMeta,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 侧栏一项:40dp 行 + 18dp 线性图标 + 行标题;选中 = 强调色淡底 + 强调色字 */
@Composable
private fun SettingsNavRow(item: SettingsNavItem, active: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        targetValue = if (active) cs.primary.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = MilkMotion.colorSpec(),
        label = "settingsNavBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 1.dp)
            .clip(RoundedCornerShape(MilkRadius.sm))
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            tint = if (active) cs.primary else cs.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            item.label,
            style = MilkText.rowTitle.copy(
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (active) cs.primary else cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 窄屏紧凑顶栏(<680dp):返回钮 + 「设置」+ 当前分组徽标 + 横向可滚动分组胶囊条,
 * 搜索按需展开(有查询词时保持展开),避免把内容列挤成窄条。
 *
 * 顶栏与内容之间没有固定硬线:停在顶部时底边干净,内容向下滚动后 hairline 才淡入,
 * 让「还有内容」这一事实由滚动本身说话(Apple: 分隔线只在需要时出现)。
 */
@Composable
internal fun SettingsCompactBar(
    selected: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
    searchNoMatch: Boolean = false,
    scrolled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalMilkMotion.current
    var searchOpen by remember { mutableStateOf(false) }
    val showSearch = searchOpen || searchQuery.isNotBlank()

    // 颜色类过渡不受「减弱动效」影响:150ms 强 ease-out 淡入/淡出
    val edgeAlpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(MilkMotion.color, easing = MilkMotion.EaseOut),
        label = "compactBarEdge",
    )

    Column(
        modifier
            .fillMaxWidth()
            .background(cs.surfaceVariant)
            .milkEdgeHairline(cs.outline.copy(alpha = edgeAlpha), bottom = true)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = MilkSpace.xxs, end = MilkSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MilkIconButton(
                imageVector = MilkIcons.ArrowLeft,
                contentDescription = "返回",
                onClick = onBack,
            )
            Spacer(Modifier.width(MilkSpace.xxs))
            Text("设置", style = MilkText.panelTitle, color = cs.onBackground, maxLines = 1)
            Spacer(Modifier.width(MilkSpace.sm))
            GroupBadge(settingsGroupOf(selected))
            Spacer(Modifier.weight(1f))
            MilkIconButton(
                imageVector = MilkIcons.Search,
                contentDescription = "搜索设置",
                onClick = { searchOpen = !searchOpen },
                selected = showSearch,
            )
        }

        // 展开慢、收起快:搜索框高度与淡出一体完成,不做位移
        AnimatedVisibility(
            visible = showSearch,
            enter = if (reduced) {
                fadeIn(tween(MilkMotion.reduced))
            } else {
                expandVertically(
                    animationSpec = tween(MilkMotion.enter, easing = MilkMotion.EaseOut),
                    expandFrom = Alignment.Top,
                ) + fadeIn(tween(160, easing = MilkMotion.EaseOut))
            },
            exit = if (reduced) {
                fadeOut(tween(MilkMotion.reduced))
            } else {
                shrinkVertically(
                    animationSpec = tween(140, easing = MilkMotion.EaseOut),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(tween(100, easing = MilkMotion.EaseOut))
            },
        ) {
            SettingsSearchField(
                query = searchQuery,
                onQueryChange = onSearchChange,
                noMatch = searchNoMatch,
                modifier = Modifier.padding(horizontal = MilkSpace.md),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MilkSpace.md),
            horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SETTINGS_NAV_GROUPS.forEach { (_, items) ->
                items.forEach { item ->
                    MilkChip(
                        label = item.label,
                        selected = item.key == selected,
                        onClick = { onSelect(item.key) },
                        small = true,
                    )
                }
            }
        }
        Spacer(Modifier.height(MilkSpace.sm))
    }
}

/** 当前分组徽标:强调色 10% 淡底胶囊 + 微型字 */
@Composable
private fun GroupBadge(text: String) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MilkText.micro,
        color = cs.primary,
        maxLines = 1,
        modifier = Modifier
            .clip(PillShape)
            .background(cs.primary.copy(alpha = 0.10f))
            .padding(horizontal = MilkSpace.sm, vertical = 2.dp),
    )
}

/** 设置搜索框:共享 MilkTextField + 搜索图标 + 一键清空;无匹配时就地给极简提示 */
@Composable
private fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    noMatch: Boolean,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(vertical = MilkSpace.xs)) {
        MilkTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "搜索设置…",
            leading = {
                Icon(
                    MilkIcons.Search,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
            },
            onClear = { onQueryChange("") },
        )
        if (query.isNotBlank() && noMatch) {
            Spacer(Modifier.height(MilkSpace.xs))
            Text(
                text = "未找到匹配设置",
                style = MilkText.caption,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

// ==================== 页面与分区 ====================

/** 页头:页面标题 + 可选灰说明(无说明时不占多余间距) */
@Composable
internal fun SettingsPageHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = MilkSpace.xxs)) {
        Text(title, style = MilkText.pageTitle, color = MaterialTheme.colorScheme.onBackground)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(MilkSpace.xs))
            Text(subtitle, style = MilkText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 分区标签:眉标字 + 可选说明。
 * 上留白略大于下留白(6/8),让标题清晰归属于下方卡片,而不是浮在两个分区之间。
 */
@Composable
internal fun SettingsSectionHeader(title: String, description: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)) {
        Text(title, style = MilkText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        description?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                it,
                style = MilkText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * 分区组:一个分区标题 + 其卡片。
 * 组内不再额外加间距——8dp 的「标题→卡片」距离由 [SettingsSectionHeader] 的底部留白承担,
 * 组与组之间由页面级 22dp 拉开,避免 8+8 叠成松散的 16dp。
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth(),
        content = content,
    )
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
            .clip(RoundedCornerShape(MilkRadius.lg))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(MilkRadius.lg)),
        content = content,
    )
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

/** 设置行模板:左(标题 + 灰副题)/右(trailing 控件),min 高 52dp */
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
                style = MilkText.rowTitle,
                color = if (enabled) cs.onBackground else cs.onSurfaceVariant.copy(alpha = 0.5f),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MilkText.caption, color = cs.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(MilkSpace.lg))
            trailing(this)
        }
    }
}

/** 值列:当前值(mono 按需)+ 细箭头 */
@Composable
internal fun RowScope.RowValue(value: String, mono: Boolean = false) {
    Text(
        value,
        style = if (mono) MilkText.monoBody else MilkText.body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.width(2.dp))
    Icon(
        MilkIcons.ChevronRight,
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

/** 原地展开的候选列表(inset 面板;单选行复用共享 MilkRadioRow) */
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
            .padding(horizontal = MilkSpace.sm, vertical = MilkSpace.sm)
            .clip(RoundedCornerShape(MilkRadius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(MilkSpace.xxs),
    ) {
        Column(
            Modifier
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            items.forEach { item ->
                MilkRadioRow(
                    selected = item == selected,
                    label = label(item),
                    onClick = { onPick(item) },
                )
            }
        }
    }
}

/** 文字操作钮(控件标签字;primary=强调色字,danger=红字;38dp 高命中) */
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
        animationSpec = MilkMotion.colorSpec(),
        label = "textActionFg",
    )
    Text(
        label,
        style = MilkText.label,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(MilkRadius.sm))
            .softClickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 38.dp)
            .padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm),
    )
}

/** 状态行:语义图标 + 单色文字(OK 走成功色,亮暗主题下都清晰) */
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
        StatusTone.OK -> LocalMilkSuccess.current
        StatusTone.WARN -> LocalMilkWarning.current
        StatusTone.ERROR -> cs.error
        StatusTone.NEUTRAL -> cs.onSurfaceVariant
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = MilkSpace.md),
        verticalAlignment = Alignment.Top,
    ) {
        if (tone != StatusTone.NEUTRAL) {
            Icon(
                when (tone) {
                    StatusTone.OK -> MilkIcons.CheckCircle
                    StatusTone.WARN -> MilkIcons.WarningTriangle
                    StatusTone.ERROR -> MilkIcons.ErrorCircle
                    StatusTone.NEUTRAL -> MilkIcons.InfoCircle
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
            style = if (mono) MilkText.monoMeta else MilkText.caption,
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
            .then(
                if (compact) Modifier.padding(vertical = MilkSpace.xxs)
                else Modifier.padding(horizontal = MilkSpace.lg, vertical = 10.dp),
            ),
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
                Spacer(Modifier.width(MilkSpace.xxs))
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
        style = if (mono) MilkText.monoMeta else MilkText.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
        modifier = Modifier.padding(top = 10.dp),
    )
}

/** 多选胶囊(思考档位/输入类型声明等):读屏语义为 Checkbox,复用共享 MilkFilterChip */
@Composable
internal fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    MilkFilterChip(label = label, selected = on, onClick = onClick, small = true)
}

/** 键盘快捷键展示行:共享键帽 + 说明 */
@Composable
internal fun ShortcutRow(title: String, keys: String, subtitle: String? = null) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        trailing = { MilkKeyCap(keys) },
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
            Row(horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs)) {
                options.forEach { option ->
                    MilkChip(
                        label = label(option),
                        selected = option == selected,
                        onClick = { onPick(option) },
                        small = true,
                    )
                }
            }
        },
    )
}
