package com.milknote.app.ui.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.milknote.agent.AgentSkill
import com.milknote.agent.ToolCategory
import com.milknote.agent.ToolConfig
import com.milknote.agent.ToolSpec
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.MilkCapabilityEditorPanel
import com.milknote.app.ui.common.MilkCapabilityRow
import com.milknote.app.ui.common.MilkCapabilitySectionHeader
import com.milknote.app.ui.common.MilkConfirmDialog
import com.milknote.app.ui.common.MilkEmptyState
import com.milknote.app.ui.common.MilkInlineAction
import com.milknote.app.ui.common.MilkInlineTone
import com.milknote.app.ui.common.MilkStatusTone
import com.milknote.app.ui.common.MilkSwitch
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.common.MilkToolSettingField

/**
 * 技能与工具:技能可增删改、内置技能可恢复默认;工具由声明驱动——开关、确认策略、
 * 名称/说明与设置字段全部来自 [ToolSpec],新增工具不需要改这一页。
 */
@Composable
internal fun SkillsAndToolsPage() {
    val agent = ServiceLocator.agentController
    val state by agent.state.collectAsState()
    var creatingSkill by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<AgentSkill?>(null) }
    var editingToolSpec by remember { mutableStateOf<ToolSpec?>(null) }

    SettingsPageHeader("技能与工具")

    SettingsGroup {
        MilkCapabilitySectionHeader(
            title = "技能",
            count = state.skills.size,
            actionLabel = "新建",
            actionIcon = Icons.Outlined.Add,
            onAction = { creatingSkill = true },
        )
        SettingsCard {
            if (state.skills.isEmpty()) {
                MilkEmptyState(title = "暂无技能", icon = Icons.Outlined.AutoStories)
            } else {
                state.skills.forEachIndexed { index, skill ->
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
        MilkCapabilitySectionHeader(title = "工具", count = state.toolSpecs.size)
        SettingsCard {
            if (state.toolSpecs.isEmpty()) {
                MilkEmptyState(title = "暂无工具", icon = Icons.Outlined.Build)
            } else {
                state.toolSpecs.forEachIndexed { index, spec ->
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
            onReset = {
                initial?.let { agent.resetBuiltinSkill(it.id) }
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

// ==================== 技能编辑 ====================

@Composable
private fun SkillEditorDialog(
    initial: AgentSkill?,
    onDismiss: () -> Unit,
    onSave: (AgentSkill) -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var summary by remember(initial?.id) { mutableStateOf(initial?.summary.orEmpty()) }
    var content by remember(initial?.id) { mutableStateOf(initial?.content.orEmpty()) }
    var enabled by remember(initial?.id) { mutableStateOf(initial?.enabled ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }

    val resetAction: @Composable RowScope.() -> Unit = {
        MilkInlineAction(
            label = "恢复默认",
            onClick = onReset,
            icon = Icons.Outlined.RestartAlt,
        )
    }
    val deleteAction: @Composable RowScope.() -> Unit = {
        MilkInlineAction(
            label = "删除",
            onClick = { confirmDelete = true },
            icon = Icons.Outlined.DeleteOutline,
            tone = MilkInlineTone.DANGER,
        )
    }
    val leading: (@Composable RowScope.() -> Unit)? = when {
        initial == null -> null
        initial.builtin -> resetAction
        else -> deleteAction
    }

    MilkCapabilityEditorPanel(
        title = if (initial == null) "新建技能" else "编辑技能",
        icon = Icons.Outlined.AutoStories,
        onDismiss = onDismiss,
        leadingAction = leading,
        saveEnabled = name.isNotBlank() && content.isNotBlank(),
        onSave = {
            onSave(
                AgentSkill(
                    id = initial?.id.orEmpty(),
                    name = name.trim(),
                    summary = summary.trim(),
                    content = content,
                    enabled = enabled,
                    builtin = initial?.builtin ?: false,
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

// ==================== 工具编辑 ====================

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
