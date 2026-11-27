package com.milknote.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milknote.agent.AgentSkill
import com.milknote.agent.ToolCategory
import com.milknote.agent.ToolConfig
import com.milknote.agent.ToolSpec
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCapabilityEditorPanel
import com.milknote.app.ui.common.MilkCapabilityRow
import com.milknote.app.ui.common.MilkCapabilitySectionHeader
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkConfirmDialog
import com.milknote.app.ui.common.MilkEmptyState
import com.milknote.app.ui.common.MilkInlineAction
import com.milknote.app.ui.common.MilkInlineTone
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkStatusBadge
import com.milknote.app.ui.common.MilkStatusTone
import com.milknote.app.ui.common.MilkSwitch
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.MilkToolSettingField
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText

/**
 * 技能与工具:
 * - 应用自带(内置)技能/工具严格只读:只有名称与说明展示,可打开只读查看,无编辑/删除/开关/覆盖;
 * - 自定义技能保持增删改与启用开关;
 * - 自定义工具继续由 [ToolSpec] 声明驱动通用设置(开关/确认/名称/说明/设置字段)。
 */
@Composable
internal fun SkillsAndToolsPage() {
    val agent = ServiceLocator.agentController
    val state by agent.state.collectAsState()
    var creatingSkill by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<AgentSkill?>(null) }
    var viewingSkill by remember { mutableStateOf<AgentSkill?>(null) }
    var viewingTool by remember { mutableStateOf<ToolSpec?>(null) }
    var editingToolSpec by remember { mutableStateOf<ToolSpec?>(null) }

    val builtinSkills = state.skills.filter { it.builtin }
    val customSkills = state.skills.filterNot { it.builtin }
    val builtinTools = state.toolSpecs.filter { it.builtin }
    val customTools = state.toolSpecs.filterNot { it.builtin }

    SettingsPageHeader("技能与工具")

    SettingsGroup {
        MilkCapabilitySectionHeader(title = "内置技能", count = builtinSkills.size)
        SettingsCard {
            builtinSkills.forEachIndexed { index, skill ->
                if (index > 0) Hairline()
                ReadOnlyCapabilityRow(
                    title = skill.name,
                    subtitle = skill.summary,
                    icon = Icons.Outlined.AutoStories,
                    badges = listOf("只读" to MilkStatusTone.NEUTRAL),
                    onClick = { viewingSkill = skill },
                )
            }
        }
    }

    SettingsGroup {
        MilkCapabilitySectionHeader(
            title = "我的技能",
            count = customSkills.size,
            actionLabel = "新建",
            actionIcon = Icons.Outlined.Add,
            onAction = { creatingSkill = true },
        )
        SettingsCard {
            if (customSkills.isEmpty()) {
                MilkEmptyState(title = "暂无自定义技能", icon = Icons.Outlined.AutoStories)
            } else {
                customSkills.forEachIndexed { index, skill ->
                    if (index > 0) Hairline()
                    MilkCapabilityRow(
                        title = skill.name,
                        icon = Icons.Outlined.AutoStories,
                        enabled = skill.enabled,
                        onToggle = { agent.toggleSkill(skill.id, it) },
                        onClick = { editingSkill = skill },
                        subtitle = skill.summary.takeIf { it.isNotBlank() },
                    )
                }
            }
        }
    }

    SettingsGroup {
        MilkCapabilitySectionHeader(title = "内置工具", count = builtinTools.size)
        SettingsCard {
            builtinTools.forEachIndexed { index, spec ->
                if (index > 0) Hairline()
                val badges = buildList {
                    if (spec.defaultRequireConfirmation) add("需确认" to MilkStatusTone.WARNING)
                    if (spec.requiresUserGrant) add("需申请" to MilkStatusTone.ACCENT)
                    add("只读" to MilkStatusTone.NEUTRAL)
                }
                ReadOnlyCapabilityRow(
                    title = spec.defaultDisplayName,
                    subtitle = spec.defaultDescription,
                    icon = toolIconFor(spec.id, spec.category),
                    badges = badges,
                    onClick = { viewingTool = spec },
                )
            }
        }
    }

    if (customTools.isNotEmpty()) {
        SettingsGroup {
            MilkCapabilitySectionHeader(title = "自定义工具", count = customTools.size)
            SettingsCard {
                customTools.forEachIndexed { index, spec ->
                    if (index > 0) Hairline()
                    val cfg = state.toolConfigs[spec.id]
                    MilkCapabilityRow(
                        title = cfg?.displayName?.takeIf { it.isNotBlank() } ?: spec.defaultDisplayName,
                        icon = toolIconFor(spec.id, spec.category),
                        enabled = cfg?.enabled ?: spec.defaultEnabled,
                        onToggle = { agent.toggleTool(spec.id, it) },
                        onClick = { editingToolSpec = spec },
                        badgeText = if (cfg?.requireConfirmation ?: spec.defaultRequireConfirmation) {
                            "需确认"
                        } else {
                            null
                        },
                        badgeTone = MilkStatusTone.WARNING,
                    )
                }
            }
        }
    }

    if (creatingSkill || editingSkill != null) {
        val initial = editingSkill
        SkillEditorDialog(
            initial = initial,
            onDismiss = { creatingSkill = false; editingSkill = null },
            onSave = { skill ->
                agent.upsertSkill(skill)
                creatingSkill = false
                editingSkill = null
            },
            onDelete = {
                initial?.let { agent.deleteSkill(it.id) }
                creatingSkill = false
                editingSkill = null
            },
        )
    }

    editingToolSpec?.let { spec ->
        ToolEditorDialog(
            spec = spec,
            config = state.toolConfigs[spec.id],
            onDismiss = { editingToolSpec = null },
            onSave = { config ->
                agent.updateToolConfig(config)
                editingToolSpec = null
            },
            onReset = {
                agent.resetToolConfig(spec.id)
                editingToolSpec = null
            },
        )
    }

    viewingSkill?.let { skill ->
        BuiltinSkillViewer(skill = skill, onDismiss = { viewingSkill = null })
    }

    viewingTool?.let { spec ->
        BuiltinToolViewer(spec = spec, onDismiss = { viewingTool = null })
    }
}

/** 工具图标:已知工具给语义图标,未知工具按类别兜底 */
private fun toolIconFor(id: String, category: ToolCategory): ImageVector = when (id) {
    "web_search" -> Icons.Outlined.Public
    "calculate" -> Icons.Outlined.Calculate
    "add_page" -> Icons.AutoMirrored.Outlined.NoteAdd
    "delete_page" -> Icons.Outlined.DeleteOutline
    "insert_markdown" -> Icons.AutoMirrored.Outlined.Article
    "list_page_elements" -> Icons.Outlined.Layers
    "modify_page_element" -> Icons.Outlined.OpenWith
    "get_element_timestamps" -> Icons.Outlined.Schedule
    "read_skill" -> Icons.AutoMirrored.Outlined.MenuBook
    "create_skill" -> Icons.Outlined.LibraryAdd
    else -> when (category) {
        ToolCategory.SEARCH -> Icons.Outlined.Public
        ToolCategory.PAGE -> Icons.Outlined.Description
        ToolCategory.CANVAS -> Icons.Outlined.Layers
        ToolCategory.COMPUTE -> Icons.Outlined.Calculate
        ToolCategory.SKILL -> Icons.Outlined.AutoStories
    }
}

// ==================== 内置能力只读行 ====================

/** 只读能力行:与 MilkCapabilityRow 同尺寸,但无开关、无编辑入口;点开只读查看 */
@Composable
private fun ReadOnlyCapabilityRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badges: List<Pair<String, MilkStatusTone>>,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    Row(
        Modifier
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
                .background(cs.primary.copy(alpha = if (dark) 0.18f else 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = cs.onSurface,
            )
        }
        Spacer(Modifier.width(MilkSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MilkText.rowTitle,
                color = cs.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
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
        badges.forEach { (text, tone) ->
            Spacer(Modifier.width(MilkSpace.xs))
            MilkStatusBadge(text = text, tone = tone)
        }
        Spacer(Modifier.width(MilkSpace.xs))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = cs.onSurfaceVariant.copy(alpha = 0.55f),
        )
    }
}

// ==================== 内置能力只读查看 ====================

@Composable
private fun BuiltinSkillViewer(skill: AgentSkill, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    MilkCenterPanel(
        title = skill.name,
        subtitle = "应用自带 · 只读",
        onDismiss = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = cs.onSurfaceVariant,
            )
        },
        footer = {
            Spacer(Modifier.weight(1f))
            MilkButton(label = "关闭", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MilkSpace.md),
        ) {
            if (skill.summary.isNotBlank()) {
                Text(skill.summary, style = MilkText.body, color = cs.onSurfaceVariant)
            }
            SettingsCard {
                Text(
                    skill.content.trim(),
                    modifier = Modifier.padding(16.dp),
                    style = MilkText.body,
                    color = cs.onSurface,
                )
            }
            Text(
                "内置技能随应用提供,不可编辑、关闭或删除;用户补充的方法请建为「我的技能」。",
                style = MilkText.caption,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BuiltinToolViewer(spec: ToolSpec, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    MilkCenterPanel(
        title = spec.defaultDisplayName,
        subtitle = "应用自带 · 只读",
        onDismiss = onDismiss,
        icon = {
            Icon(
                toolIconFor(spec.id, spec.category),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = cs.onSurfaceVariant,
            )
        },
        footer = {
            Spacer(Modifier.weight(1f))
            MilkButton(label = "关闭", onClick = onDismiss, variant = MilkButtonVariant.SECONDARY)
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MilkSpace.md),
        ) {
            Text(spec.defaultDescription, style = MilkText.body, color = cs.onSurface)
            if (spec.defaultRequireConfirmation) {
                Text(
                    "高危操作:执行前必须由用户确认,该策略不可关闭。",
                    style = MilkText.caption,
                    color = cs.onSurfaceVariant,
                )
            }
            if (spec.requiresUserGrant) {
                Text(
                    "仅在用户主动「申请熟练度分析」时对模型可见;执行时还要求授权页与发起页一致。",
                    style = MilkText.caption,
                    color = cs.onSurfaceVariant,
                )
            }
            if (spec.parameters.isNotEmpty()) {
                MilkSectionLabel("参数")
                SettingsCard {
                    spec.parameters.forEachIndexed { index, parameter ->
                        if (index > 0) Hairline()
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                buildString {
                                    append(parameter.name)
                                    if (parameter.required) append(" · 必填")
                                },
                                style = MilkText.monoBody,
                                color = cs.onSurface,
                            )
                            Text(
                                parameter.description,
                                style = MilkText.caption,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Text(
                "内置工具的开关、名称、说明与确认策略由应用固定,不可修改。",
                style = MilkText.caption,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

// ==================== 自定义技能编辑 ====================

@Composable
private fun SkillEditorDialog(
    initial: AgentSkill?,
    onDismiss: () -> Unit,
    onSave: (AgentSkill) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var summary by remember(initial?.id) { mutableStateOf(initial?.summary.orEmpty()) }
    var content by remember(initial?.id) { mutableStateOf(initial?.content.orEmpty()) }
    var enabled by remember(initial?.id) { mutableStateOf(initial?.enabled ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }

    val deleteAction: @Composable RowScope.() -> Unit = {
        MilkInlineAction(
            label = "删除",
            onClick = { confirmDelete = true },
            icon = Icons.Outlined.DeleteOutline,
            tone = MilkInlineTone.DANGER,
        )
    }

    MilkCapabilityEditorPanel(
        title = if (initial == null) "新建技能" else "编辑技能",
        icon = Icons.Outlined.AutoStories,
        onDismiss = onDismiss,
        leadingAction = if (initial == null) null else deleteAction,
        saveEnabled = name.isNotBlank() && content.isNotBlank(),
        onSave = {
            onSave(
                AgentSkill(
                    id = initial?.id.orEmpty(),
                    name = name.trim(),
                    summary = summary.trim(),
                    content = content,
                    enabled = enabled,
                    builtin = false,
                ),
            )
        },
    ) {
        MilkTextField(
            value = name,
            onValueChange = { name = it },
            label = "名称",
            onClear = { name = "" },
        )
        MilkTextField(
            value = summary,
            onValueChange = { summary = it },
            label = "摘要",
            onClear = { summary = "" },
        )
        MilkTextField(
            value = content,
            onValueChange = { content = it },
            label = "正文",
            singleLine = false,
            minLines = 6,
            maxLines = 12,
        )
        SettingsCard {
            SettingsRow(
                title = "启用",
                trailing = { MilkSwitch(checked = enabled, onChange = { enabled = it }) },
            )
        }
    }

    if (confirmDelete && initial != null) {
        MilkConfirmDialog(
            title = "删除技能？",
            body = "「${initial.name}」会从技能列表移除。",
            confirmLabel = "删除",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

// ==================== 自定义工具编辑 ====================

@Composable
private fun ToolEditorDialog(
    spec: ToolSpec,
    config: ToolConfig?,
    onDismiss: () -> Unit,
    onSave: (ToolConfig) -> Unit,
    onReset: () -> Unit,
) {
    val effective = remember(spec.id, config) {
        ToolConfig(
            id = spec.id,
            enabled = config?.enabled ?: spec.defaultEnabled,
            displayName = config?.displayName,
            customDescription = config?.customDescription,
            requireConfirmation = config?.requireConfirmation ?: spec.defaultRequireConfirmation,
            settings = spec.settingOptions.associate { it.key to it.defaultValue } + (config?.settings ?: emptyMap()),
        )
    }
    var enabled by remember(effective) { mutableStateOf(effective.enabled) }
    var requireConfirm by remember(effective) {
        mutableStateOf(effective.requireConfirmation ?: spec.defaultRequireConfirmation)
    }
    var displayName by remember(effective) { mutableStateOf(effective.displayName.orEmpty()) }
    var description by remember(effective) { mutableStateOf(effective.customDescription.orEmpty()) }
    var settings by remember(effective) { mutableStateOf(effective.settings) }

    MilkCapabilityEditorPanel(
        title = "工具设置",
        icon = toolIconFor(spec.id, spec.category),
        onDismiss = onDismiss,
        leadingAction = {
            MilkInlineAction(
                label = "恢复默认",
                onClick = onReset,
                icon = Icons.Outlined.RestartAlt,
            )
        },
        onSave = {
            onSave(
                ToolConfig(
                    id = spec.id,
                    enabled = enabled,
                    displayName = displayName.trim().ifBlank { null },
                    customDescription = description.trim().ifBlank { null },
                    requireConfirmation = requireConfirm,
                    settings = settings,
                ),
            )
        },
    ) {
        SettingsCard {
            SettingsRow(
                title = "启用",
                trailing = { MilkSwitch(checked = enabled, onChange = { enabled = it }) },
            )
            Hairline()
            SettingsRow(
                title = "执行前确认",
                trailing = { MilkSwitch(checked = requireConfirm, onChange = { requireConfirm = it }) },
            )
        }
        MilkTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = "名称",
            placeholder = spec.defaultDisplayName,
            onClear = { displayName = "" },
        )
        MilkTextField(
            value = description,
            onValueChange = { description = it },
            label = "调用说明",
            placeholder = spec.defaultDescription,
            singleLine = false,
            minLines = 3,
            maxLines = 6,
        )
        spec.settingOptions
            .filter { it.key != "custom_url" || settings["engine"] == "custom" }
            .forEach { option ->
                MilkToolSettingField(
                    option = option,
                    value = settings[option.key] ?: option.defaultValue,
                    onValueChange = { value -> settings = settings + (option.key to value) },
                )
            }
    }
}
