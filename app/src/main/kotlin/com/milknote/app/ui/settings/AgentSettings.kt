package com.milknote.app.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milknote.agent.ModelInfo
import com.milknote.agent.Protocol
import com.milknote.agent.ProviderProfile
import com.milknote.agent.ProviderRegistry
import com.milknote.agent.ReasoningEffort
import com.milknote.agent.SummaryModelMode
import com.milknote.agent.VisionSource
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonSize
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkConfirmDialog
import com.milknote.app.ui.common.MilkEmptyState
import com.milknote.app.ui.common.MilkExpandableErrorBox
import com.milknote.app.ui.common.MilkFilterChip
import com.milknote.app.ui.common.MilkHairlineDivider
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkInlineAction
import com.milknote.app.ui.common.MilkInlineStatus
import com.milknote.app.ui.common.MilkInlineTone
import com.milknote.app.ui.common.MilkModelProviderHeader
import com.milknote.app.ui.common.MilkModelProtocolOverridePicker
import com.milknote.app.ui.common.MilkModelSelectRow
import com.milknote.app.ui.common.MilkProtocolBadge
import com.milknote.app.ui.common.MilkProtocolPicker
import com.milknote.app.ui.common.MilkSectionLabel
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.MilkStatusBadge
import com.milknote.app.ui.common.MilkStatusTone
import com.milknote.app.ui.common.MilkSurfaceGroup
import com.milknote.app.ui.common.MilkSwitch
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import kotlinx.coroutines.launch

/**
 * 设置 → AI 教师(A2.4 逻辑 + A2.7 视觉重构,选择列表模式迁移自 deepseek-harness ui-settings-models):
 * - Provider 卡 = 头部一行(状态点/名称/协议徽标/缺 Key/模型数/展开)+ 折叠高级区
 * - 协议 = 服务级默认 + 模型级覆盖(同一网关的不同模型可各自走 Chat/Anthropic/Responses)
 * - 模型目录 = 行式编辑器:技能徽标齐全,容量/协议一眼可读,展开区才有低频字段
 * - 拉取 = 候选勾选选择器,一次加减多个,默认勾选已配置项
 * 视觉走 Gen2:单行恒高、操作钮最小宽度锁死、错误原文永不截断(可展开可复制)。
 */
@Composable
fun AgentSettingsSection() {
    val agent = ServiceLocator.agentController
    val state by agent.state.collectAsState()
    val scope = rememberCoroutineScope()

    var adding by remember { mutableStateOf(false) }
    var expandedProvider by remember { mutableStateOf<String?>(null) }
    var expandedModel by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var testingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // A-27:删除需确认(Provider 含端点/模型声明/调档结果;模型 ✕ 误触即丢)
    data class DeleteReq(val providerId: String, val providerTitle: String, val modelId: String? = null)
    var deleteReq by remember { mutableStateOf<DeleteReq?>(null) }

    Column {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MilkSpace.sm)) {
            state.profiles.forEach { profile ->
                val active = profile.id == state.activeProfileId
                ProviderCard(
                    profile = profile,
                    active = active,
                    hasKey = agent.hasKey(profile),
                    expanded = expandedProvider == profile.id,
                    expandedModelId = expandedModel,
                    testLine = testResult[profile.id],
                    testing = profile.id in testingIds,
                    onToggleExpand = { expandedProvider = if (expandedProvider == profile.id) null else profile.id },
                    onToggleModel = { id -> expandedModel = if (expandedModel == id) null else id },
                    onBaseUrlChange = { url ->
                        // 只改 baseUrl:整本回传 ProfileBook 会顺手覆盖摘要/自动提及配置
                        agent.updateBaseUrl(profile.id, url)
                    },
                    onProtocolChange = { agent.updateProtocol(profile.id, it) },
                    onTest = {
                        if (profile.id !in testingIds) {
                            testingIds = testingIds + profile.id
                            scope.launch {
                                agent.testConnection(profile.id)
                                    .onSuccess { ms -> testResult = testResult + (profile.id to "鉴权可达 · ${ms}ms") }
                                    // 失败原文整段保留:展开详情才能看到服务方到底回了什么
                                    .onFailure { e ->
                                        testResult = testResult + (profile.id to (e.message ?: "连接失败"))
                                    }
                                testingIds = testingIds - profile.id
                            }
                        }
                    },
                    onSetActive = { agent.setActiveProfile(profile.id) },
                    onDelete = { deleteReq = DeleteReq(profile.id, profile.title) },
                    onSelectModel = { agent.selectModel(profile.id, it) },
                    onAddModel = { agent.addModel(profile.id, it, declaredVision = true) },
                    onRemoveModel = { deleteReq = DeleteReq(profile.id, profile.title, it) },
                    onUpdateModel = { id, dn, vis, lv, ctx, out ->
                        agent.updateModel(profile.id, id, dn, vis, lv, ctx, out)
                    },
                    onUpdateModelProtocol = { id, protocol ->
                        agent.updateModel(
                            profileId = profile.id,
                            modelId = id,
                            protocol = protocol,
                            clearProtocolOverride = protocol == null,
                        )
                    },
                    onFetch = { onDone ->
                        scope.launch { agent.fetchModels(profile.id).let(onDone) }
                    },
                    onApplyFetched = { models -> agent.setModels(profile.id, models) },
                )
            }

            // 新增入口:单行 hairline 卡,沉稳不漂浮
            SettingsCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { adding = true }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(MilkSpace.sm))
                    Text("添加模型服务", style = MilkText.rowTitle, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    if (adding) {
        AddProviderDialog(
            onCancel = { adding = false },   // A-26:误入新增必须有取消出口
            onConfirm = { presetId, title, baseUrl, key, protocol ->
                agent.addProfile(presetId ?: "custom", title, baseUrl, key, protocol)
                adding = false
            },
        )
    }

    // 删除确认(A-27):Provider 整套配置 / 模型声明均不可静默丢失
    deleteReq?.let { req ->
        MilkConfirmDialog(
            title = if (req.modelId == null) "删除 Provider「${req.providerTitle}」？" else "删除模型 ${req.modelId}？",
            body = if (req.modelId == null)
                "端点、已声明模型与思考档设置将一并删除；已保存的会话记录不受影响。"
            else
                "将从「${req.providerTitle}」的模型目录移除；正在进行的对话不受影响。",
            confirmLabel = "删除",
            onDismiss = { deleteReq = null },
            onConfirm = {
                if (req.modelId == null) agent.removeProfile(req.providerId)
                else agent.removeModel(req.providerId, req.modelId)
                deleteReq = null
            },
        )
    }
}

// ==================== 会话总结 / 附件与引用 ====================

/**
 * 行为配置(模型服务的兄弟分区):会话总结 + 附件自动 @。
 * 全部即时落盘(经 AgentController,随 ProfileBook 保存),没有「保存」按钮。
 */
@Composable
fun AgentBehaviorSettingsSection() {
    val agent = ServiceLocator.agentController
    val state by agent.state.collectAsState()
    var showSummaryModelPicker by remember { mutableStateOf(false) }

    val cfg = state.summaryConfig
    val selectedProfile = state.profiles.firstOrNull { it.id == cfg.profileId }
    val selectedModel = selectedProfile?.models?.firstOrNull { it.id == cfg.modelId }

    SettingsGroup {
        SettingsSectionHeader("会话总结", "为历史会话生成摘要，便于在会话切换菜单中快速定位")
        SettingsCard {
            SettingsRow(
                title = "自动生成会话总结",
                onClick = { agent.setSummaryConfig(cfg.copy(enabled = !cfg.enabled)) },
                trailing = {
                    MilkSwitch(
                        checked = cfg.enabled,
                        onChange = { agent.setSummaryConfig(cfg.copy(enabled = it)) },
                    )
                },
            )
            Hairline()
            SettingsRow(
                title = "总结用模型",
                trailing = {
                    MilkSegmented(
                        options = SUMMARY_MODEL_MODES,
                        selected = cfg.mode,
                        label = { if (it == SummaryModelMode.INHERIT_CHAT) "继承对话" else "指定模型" },
                        onSelect = { mode ->
                            agent.setSummaryConfig(
                                cfg.copy(
                                    mode = mode,
                                    // 首次切到「指定模型」时预填当前对话模型;切回继承保留原选择便于再切回来
                                    profileId = if (mode == SummaryModelMode.CUSTOM) {
                                        cfg.profileId ?: state.activeProfileId
                                    } else {
                                        cfg.profileId
                                    },
                                    modelId = if (mode == SummaryModelMode.CUSTOM) {
                                        cfg.modelId ?: state.activeModelId
                                    } else {
                                        cfg.modelId
                                    },
                                ),
                            )
                        },
                        compact = true,
                        modifier = Modifier.width(176.dp),
                    )
                },
            )
            if (cfg.mode == SummaryModelMode.CUSTOM) {
                Hairline()
                SettingsRow(
                    title = "默认总结模型",
                    subtitle = selectedProfile?.title ?: "未指定",
                    onClick = { showSummaryModelPicker = true },
                    trailing = { RowValue(selectedModel?.id ?: "—", mono = true) },
                )
            }
            Hairline()
            StatusLine("首轮完成后自动生成；每新增 3 轮或切换/新建会话时自动刷新", tone = StatusTone.NEUTRAL)
        }
    }

    SettingsGroup {
        SettingsSectionHeader("附件与引用")
        SettingsCard {
            SettingsRow(
                title = "上传附件时默认 @ 引用",
                subtitle = "添加图片、文档或笔记页面后，自动在输入框插入 @附件名",
                onClick = { agent.setAutoMentionAttachments(!state.autoMentionAttachments) },
                trailing = {
                    MilkSwitch(
                        checked = state.autoMentionAttachments,
                        onChange = { agent.setAutoMentionAttachments(it) },
                    )
                },
            )
        }
    }

    if (showSummaryModelPicker) {
        SummaryModelPickerPanel(
            profiles = state.profiles,
            selectedProfileId = cfg.profileId,
            selectedModelId = cfg.modelId,
            onPick = { profile, model ->
                agent.setSummaryConfig(
                    cfg.copy(
                        mode = SummaryModelMode.CUSTOM,
                        profileId = profile.id,
                        modelId = model.id,
                    ),
                )
                showSummaryModelPicker = false
            },
            onDismiss = { showSummaryModelPicker = false },
        )
    }
}

private val SUMMARY_MODEL_MODES = listOf(SummaryModelMode.INHERIT_CHAT, SummaryModelMode.CUSTOM)

/**
 * 默认总结模型选择器:列出全部 Provider 的全部模型(含纯文本模型——摘要只需文本能力,
 * 选便宜快的即可)。视觉能力只作为角标信息,不作过滤条件。
 */
@Composable
private fun SummaryModelPickerPanel(
    profiles: List<ProviderProfile>,
    selectedProfileId: String?,
    selectedModelId: String?,
    onPick: (ProviderProfile, ModelInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    MilkCenterPanel(
        title = "默认总结模型",
        subtitle = "纯文本模型即可胜任，优先选响应快的",
        onDismiss = onDismiss,
        maxWidth = 420.dp,
        maxHeight = 600.dp,
    ) {
        if (profiles.none { it.models.isNotEmpty() }) {
            MilkEmptyState(
                title = "尚未配置模型",
                description = "先在「模型服务」添加端点与模型",
                icon = Icons.Outlined.SmartToy,
            )
            return@MilkCenterPanel
        }

        MilkTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "搜索模型 id…",
            leading = {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
            },
            onClear = { query = "" },
        )
        Spacer(Modifier.height(MilkSpace.md))

        val needle = query.trim()
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            profiles.forEach { profile ->
                val models = profile.models.filter {
                    needle.isEmpty() || it.id.contains(needle, true) || it.displayName.contains(needle, true)
                }
                if (models.isEmpty()) return@forEach
                MilkModelProviderHeader(
                    title = profile.title,
                    protocol = profile.protocol,
                    trailingText = "${models.size} 模型",
                )
                Spacer(Modifier.height(MilkSpace.xxs))
                MilkSurfaceGroup {
                    models.forEachIndexed { index, model ->
                        if (index > 0) MilkHairlineDivider(startIndent = 44.dp)
                        MilkModelSelectRow(
                            displayName = model.displayName.ifBlank { model.id },
                            modelId = model.id,
                            selected = profile.id == selectedProfileId && model.id == selectedModelId,
                            protocol = model.protocol ?: profile.protocol,
                            protocolOverridden = model.protocol != null,
                            vision = model.vision,
                            contextWindowK = model.contextWindowK,
                            showVisionBadge = true,
                            onClick = { onPick(profile, model) },
                        )
                    }
                }
                Spacer(Modifier.height(MilkSpace.sm))
            }
        }
    }
}

// ==================== Provider 卡 ====================

@Composable
private fun ProviderCard(
    profile: ProviderProfile,
    active: Boolean,
    hasKey: Boolean,
    expanded: Boolean,
    expandedModelId: String?,
    testLine: String?,
    testing: Boolean,
    onToggleExpand: () -> Unit,
    onToggleModel: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onProtocolChange: (Protocol) -> Unit,
    onTest: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit,
    onSelectModel: (String) -> Unit,
    onAddModel: (String) -> Unit,
    onRemoveModel: (String) -> Unit,
    onUpdateModel: (String, String?, Boolean?, List<ReasoningEffort>?, Int?, Int?) -> Unit,
    onUpdateModelProtocol: (String, Protocol?) -> Unit,
    onFetch: ((Result<List<ModelInfo>>) -> Unit) -> Unit,
    onApplyFetched: (List<ModelInfo>) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    SettingsCard(
        modifier = Modifier.animateContentSize(tween(200)),
        borderColor = if (active) cs.primary.copy(alpha = 0.3f) else cs.outline,
        containerColor = if (active) cs.primary.copy(alpha = 0.05f) else cs.surface,
    ) {
        // ---- 标题行:状态点 | 名称 | 协议徽标 | 缺 Key | 模型数 | ▸/▾ ----
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (active) cs.primary else Color.Transparent)
                    .border(1.dp, if (active) cs.primary else cs.outline, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            // 名称独占剩余宽度:长名字省略号收缩,徽标与计数永不被挤走
            Text(
                profile.title,
                style = MilkText.rowTitle,
                color = cs.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(MilkSpace.sm))
            MilkProtocolBadge(profile.protocol, short = true)
            if (!hasKey) {
                Spacer(Modifier.width(MilkSpace.xs))
                MilkStatusBadge(text = "缺 Key", tone = MilkStatusTone.DANGER)
            }
            Spacer(Modifier.width(MilkSpace.sm))
            Text(
                text = "${profile.models.size} 模型",
                style = MilkText.monoMicro,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 52.dp),
            )
            MilkIconButton(
                imageVector = if (expanded) Icons.Outlined.ExpandMore else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = if (expanded) "收起 Provider" else "展开 Provider",
                onClick = onToggleExpand,
            )
        }

        if (expanded) {
            Hairline()
            // ---- 主字段:API Key(只写) + baseUrl + 服务默认协议 ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = MilkSpace.md),
                verticalArrangement = Arrangement.spacedBy(MilkSpace.md),
            ) {
                ApiKeyBlock(hasKey = hasKey, onSave = { ServiceLocator.agentController.setApiKey(profile.id, it) })
                BaseUrlField(value = profile.baseUrl, onCommit = onBaseUrlChange)
                Column(Modifier.fillMaxWidth()) {
                    MilkSectionLabel(text = "默认聊天流形式")
                    Spacer(Modifier.height(MilkSpace.sm))
                    MilkProtocolPicker(
                        selected = profile.protocol,
                        onSelect = onProtocolChange,
                        baseUrl = profile.baseUrl,
                    )
                }
            }
            Hairline()
            // ---- 模型目录 ----
            ModelCatalogSection(
                profile = profile,
                expandedModelId = expandedModelId,
                onToggleModel = onToggleModel,
                onSelectModel = onSelectModel,
                onAddModel = onAddModel,
                onRemoveModel = onRemoveModel,
                onUpdateModel = onUpdateModel,
                onUpdateModelProtocol = onUpdateModelProtocol,
                onFetch = onFetch,
                onApplyFetched = onApplyFetched,
            )
            testLine?.let { line ->
                Hairline()
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = MilkSpace.sm)) {
                    if (line.startsWith("鉴权可达")) {
                        StatusLine(line, tone = StatusTone.OK)
                    } else {
                        // 失败原文不截断:展开详情可读完整响应体,一键复制去提工单
                        MilkExpandableErrorBox(title = "连接失败", detail = line)
                    }
                }
            }
            Hairline()
            // ---- 底部动作条:当前服务 / 测试 / 删除(三者几何独立,文案变化不互相推动) ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = MilkSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active) {
                    MilkInlineStatus(
                        label = "当前服务",
                        icon = Icons.Outlined.Check,
                        tone = MilkInlineTone.ACCENT,
                        minWidth = 88.dp,
                    )
                } else {
                    MilkInlineAction(
                        label = "设为当前",
                        onClick = onSetActive,
                        minWidth = 88.dp,
                    )
                }
                MilkInlineAction(
                    label = if (testing) "测试中…" else "测试连接",
                    onClick = onTest,
                    enabled = !testing,
                    minWidth = 82.dp,
                )
                Spacer(Modifier.weight(1f))
                MilkInlineAction(
                    label = "删除",
                    onClick = onDelete,
                    tone = MilkInlineTone.DANGER,
                )
            }
        }
    }
}

/**
 * 只写的 API Key 块:已保存时是一行状态 +「更换」;编辑时输入框独占整行,
 * 保存/取消落在输入框下方——打字永远不会改变输入框宽度。
 */
@Composable
private fun ApiKeyBlock(hasKey: Boolean, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(!hasKey) }
    var key by remember { mutableStateOf("") }
    if (!editing) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("API Key", style = MilkText.rowTitle, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.weight(1f))
            Text(
                "•••• 已保存",
                style = MilkText.monoMicro,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(MilkSpace.sm))
            MilkInlineAction(label = "更换", onClick = { editing = true }, minWidth = 66.dp)
        }
    } else {
        SettingsFieldBlock(
            value = key,
            onValueChange = { key = it },
            label = "API Key",
            placeholder = "sk-…",
            mono = true,
            mask = true,
            dirty = key.isNotBlank(),
            onSave = {
                if (key.isNotBlank()) {
                    onSave(key)
                    key = ""
                    editing = false
                }
            },
            onCancel = if (hasKey) {
                {
                    key = ""
                    editing = false
                }
            } else {
                null
            },
        )
    }
}

/** 就地编辑的 baseUrl 字段(mono;脏→保存/取消落在下方一行) */
@Composable
private fun BaseUrlField(value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    SettingsFieldBlock(
        value = text,
        onValueChange = { text = it },
        label = "baseUrl",
        placeholder = "https://api.example.com/v1",
        mono = true,
        dirty = text != value,
        onSave = { onCommit(text) },
        onCancel = { text = value },
    )
}

/**
 * 全宽输入框 + 下方动作行:动作永远在输入框之外的另一行,
 * 因此「保存/取消」出现或消失都不会挤压正在输入的文本宽度。
 */
@Composable
private fun SettingsFieldBlock(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    mono: Boolean = false,
    mask: Boolean = false,
    dirty: Boolean,
    onSave: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        MilkTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = label,
            placeholder = placeholder,
            mono = mono,
            mask = mask,
            dirty = dirty,
            onClear = { onValueChange("") },
        )
        if (dirty) {
            Spacer(Modifier.height(MilkSpace.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MilkInlineAction(
                    label = "保存",
                    onClick = onSave,
                    tone = MilkInlineTone.ACCENT,
                    minWidth = 66.dp,
                )
                if (onCancel != null) {
                    MilkInlineAction(label = "取消", onClick = onCancel, minWidth = 66.dp)
                }
            }
        }
    }
}

// ==================== 模型目录 ====================

@Composable
private fun ModelCatalogSection(
    profile: ProviderProfile,
    expandedModelId: String?,
    onToggleModel: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onAddModel: (String) -> Unit,
    onRemoveModel: (String) -> Unit,
    onUpdateModel: (String, String?, Boolean?, List<ReasoningEffort>?, Int?, Int?) -> Unit,
    onUpdateModelProtocol: (String, Protocol?) -> Unit,
    onFetch: ((Result<List<ModelInfo>>) -> Unit) -> Unit,
    onApplyFetched: (List<ModelInfo>) -> Unit,
) {
    var fetchState by remember { mutableStateOf<FetchState?>(null) }
    var newModelId by remember { mutableStateOf("") }

    Column {
        // ---- 目录头:模型 · N + 拉取列表 + 添加 ----
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("模型", style = MilkText.rowTitle, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(MilkSpace.xs))
            Text(
                text = if (profile.presetId != null) "${profile.models.size} · 预设可编辑" else "${profile.models.size} · 自定义",
                style = MilkText.monoMicro,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MilkInlineAction(
                label = if (fetchState is FetchState.Loading) "拉取中…" else "拉取列表",
                onClick = {
                    if (fetchState !is FetchState.Loading) {
                        fetchState = FetchState.Loading
                        onFetch { result ->
                            fetchState = result.fold(
                                onSuccess = { FetchState.Picker(it) },
                                // 原文整段保留:失败原因常常藏在服务方返回的最后一行
                                onFailure = { FetchState.Failed(it.message ?: "拉取失败") },
                            )
                        }
                    }
                },
                enabled = fetchState !is FetchState.Loading,
                minWidth = 76.dp,
            )
            MilkInlineAction(
                label = "添加",
                onClick = {
                    if (newModelId.isNotBlank()) {
                        onAddModel(newModelId.trim())
                        newModelId = ""
                    } else {
                        fetchState = fetchState ?: FetchState.Adding
                    }
                },
                icon = Icons.Outlined.Add,
                minWidth = 66.dp,
            )
        }

        // 添加行(点「添加」出现)
        if (fetchState is FetchState.Adding) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = MilkSpace.xs)) {
                SettingsFieldBlock(
                    value = newModelId,
                    onValueChange = { newModelId = it },
                    label = "modelId",
                    placeholder = "如 glm-5.4v",
                    mono = true,
                    dirty = newModelId.isNotBlank(),
                    onSave = {
                        if (newModelId.isNotBlank()) {
                            onAddModel(newModelId.trim())
                            newModelId = ""
                            fetchState = null
                        }
                    },
                    onCancel = {
                        fetchState = null
                        newModelId = ""
                    },
                )
            }
        }

        when (val fs = fetchState) {
            is FetchState.Picker -> FetchPicker(
                current = profile.models,
                candidates = fs.candidates,
                onApply = { picked ->
                    onApplyFetched(picked)
                    fetchState = null
                },
                onCancel = { fetchState = null },
            )
            is FetchState.Failed -> Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = MilkSpace.xs),
            ) {
                MilkExpandableErrorBox(title = "拉取失败", detail = fs.message)
            }
            else -> {}
        }

        if (profile.models.isEmpty()) {
            StatusLine("暂无模型:从端点拉取,或手填 modelId(勾选「图片」输入类型后方可用于框选)", tone = StatusTone.NEUTRAL)
        }
        // ---- 模型行列表:hairline 分行,不再套灰底容器(避免多层卡片套娃) ----
        if (profile.models.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = MilkSpace.sm, vertical = MilkSpace.xs)) {
                profile.models.forEachIndexed { idx, model ->
                    if (idx > 0) MilkHairlineDivider(startIndent = 44.dp)
                    ModelRow(
                        model = model,
                        profileProtocol = profile.protocol,
                        baseUrl = profile.baseUrl,
                        isDefault = model.id == profile.activeModelId,
                        expanded = expandedModelId == model.id,
                        onToggle = { onToggleModel(model.id) },
                        onSelectDefault = { onSelectModel(model.id) },
                        onRemove = { onRemoveModel(model.id) },
                        onUpdate = { dn, vis, lv, ctx, out -> onUpdateModel(model.id, dn, vis, lv, ctx, out) },
                        onUpdateProtocol = { picked -> onUpdateModelProtocol(model.id, picked) },
                    )
                }
            }
        }
    }
}

private sealed interface FetchState {
    data object Loading : FetchState
    data object Adding : FetchState
    data class Picker(val candidates: List<ModelInfo>) : FetchState
    data class Failed(val message: String) : FetchState
}

/** 拉取候选勾选器:搜索 + 复选(默认勾选已配置 id;OpenRouter 元数据标视觉) */
@Composable
private fun FetchPicker(
    current: List<ModelInfo>,
    candidates: List<ModelInfo>,
    onApply: (List<ModelInfo>) -> Unit,
    onCancel: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val configured = remember(candidates) { current.map { it.id }.toSet() }
    // 勾选集:已配置的默认勾上(harness pickedByDefault)
    var picked by remember(candidates) {
        mutableStateOf(candidates.filter { it.id in configured }.map { it.id }.toSet())
    }
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(horizontal = MilkSpace.sm, vertical = MilkSpace.xs)) {
        MilkSurfaceGroup(Modifier.padding(MilkSpace.xs)) {
            Column(Modifier.fillMaxWidth().padding(MilkSpace.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "端点返回 ${candidates.size} 个模型",
                        style = MilkText.caption,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "已选 ${picked.size}",
                        style = MilkText.monoMicro,
                        color = cs.primary,
                    )
                }
                Spacer(Modifier.height(MilkSpace.sm))
                MilkTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索模型 id…",
                    leading = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                    },
                    onClear = { query = "" },
                )
                Spacer(Modifier.height(MilkSpace.sm))

                val filtered = remember(candidates, query) {
                    candidates.filter { query.isBlank() || it.id.contains(query, true) }
                }
                LazyColumn(Modifier.fillMaxWidth().height(240.dp)) {
                    items(filtered, key = { it.id }) { c ->
                        val checked = c.id in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { picked = if (checked) picked - c.id else picked + c.id }
                                .heightIn(min = 44.dp)
                                .padding(horizontal = MilkSpace.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(MilkRadius.xs - 1.dp))
                                    .background(if (checked) cs.primary else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (checked) cs.primary else cs.outline,
                                        RoundedCornerShape(MilkRadius.xs - 1.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (checked) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = cs.onPrimary,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    c.displayName.ifBlank { c.id },
                                    style = MilkText.rowTitle,
                                    color = cs.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (c.displayName.isNotBlank() && c.displayName != c.id) {
                                    Text(
                                        c.id,
                                        style = MilkText.monoMicro,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Spacer(Modifier.width(MilkSpace.sm))
                            c.protocol?.let { protocol ->
                                MilkProtocolBadge(protocol, overridden = true, short = true)
                                Spacer(Modifier.width(MilkSpace.xxs))
                            }
                            MilkStatusBadge(
                                text = if (c.vision) "视觉" else "纯文本",
                                tone = if (c.vision) MilkStatusTone.ACCENT else MilkStatusTone.NEUTRAL,
                            )
                            if (c.contextWindowK > 0) {
                                Spacer(Modifier.width(MilkSpace.xs))
                                Text(
                                    "${c.contextWindowK}k",
                                    style = MilkText.monoMicro,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(MilkSpace.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs)) {
                    MilkButton(
                        label = "应用所选",
                        onClick = {
                            // 已配置行保留原声明(用户调过的档位不被拉取覆盖);勾选的新行按候选进表
                            val kept = current.filter { it.id in picked }
                            val fresh = candidates.filter { it.id in picked && current.none { cur -> cur.id == it.id } }
                            onApply(kept + fresh)
                        },
                        size = MilkButtonSize.SMALL,
                    )
                    MilkButton(
                        label = "取消",
                        onClick = onCancel,
                        variant = MilkButtonVariant.SECONDARY,
                        size = MilkButtonSize.SMALL,
                    )
                }
            }
        }
    }
}

// ==================== 模型行 ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelRow(
    model: ModelInfo,
    profileProtocol: Protocol,
    baseUrl: String,
    isDefault: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelectDefault: () -> Unit,
    onRemove: () -> Unit,
    onUpdate: (String?, Boolean?, List<ReasoningEffort>?, Int?, Int?) -> Unit,
    onUpdateProtocol: (Protocol?) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var name by remember(model.displayName) { mutableStateOf(model.displayName) }
    var ctx by remember(model.contextWindowK) { mutableStateOf(if (model.contextWindowK > 0) model.contextWindowK.toString() else "") }
    var out by remember(model.maxTokensK) { mutableStateOf(if (model.maxTokensK > 0) model.maxTokensK.toString() else "") }
    val levels = remember(model.thinkingLevels) { ThinkingDispatchUI.parse(model.thinkingLevels) }
    val effectiveProtocol = model.protocol ?: profileProtocol

    Column(Modifier.fillMaxWidth().animateContentSize(tween(200))) {
        // ---- 折叠行:默认点 | 显示名 + id/思考 | 协议 + 纯文本 + 容量 | ▸ | 删 ----
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MilkRadius.sm))
                .clickable(onClick = onToggle)
                .heightIn(min = 56.dp)
                .padding(horizontal = MilkSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clickable(enabled = model.vision, onClick = onSelectDefault),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isDefault) cs.primary else Color.Transparent, CircleShape)
                        .border(1.dp, if (isDefault) cs.primary else cs.outline, CircleShape),
                )
            }
            Spacer(Modifier.width(MilkSpace.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    model.displayName.ifBlank { model.id },
                    style = MilkText.rowTitle,
                    color = if (isDefault) cs.primary else cs.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    if (model.displayName.isNotBlank() && model.displayName != model.id) add(model.id)
                    if (levels.isNotEmpty()) {
                        add("思考 " + levels.joinToString("/") { ThinkingDispatchUI.label(it) })
                    }
                    if (model.visionSource == VisionSource.DECLARED.name) add("已声明图片")
                }
                if (meta.isNotEmpty()) {
                    Text(
                        meta.joinToString(" · "),
                        style = MilkText.monoMicro,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(MilkSpace.xs))
            MilkProtocolBadge(effectiveProtocol, overridden = model.protocol != null, short = true)
            if (!model.vision) {
                Spacer(Modifier.width(MilkSpace.xxs))
                MilkStatusBadge(text = "纯文本", tone = MilkStatusTone.DANGER)
            }
            Spacer(Modifier.width(MilkSpace.xs))
            Text(
                text = if (model.contextWindowK > 0) "${model.contextWindowK}k" else "—",
                style = MilkText.monoMicro,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 40.dp),
            )
            MilkIconButton(
                imageVector = if (expanded) Icons.Outlined.ExpandMore else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = if (expanded) "收起模型设置" else "展开模型设置",
                onClick = onToggle,
                touchSize = 40.dp,
                visualSize = 32.dp,
                iconSize = 17.dp,
            )
            MilkIconButton(
                imageVector = Icons.Outlined.Close,
                contentDescription = "删除模型",
                onClick = onRemove,
                touchSize = 40.dp,
                visualSize = 32.dp,
                iconSize = 17.dp,
            )
        }

        // ---- 展开区:显示名 / 聊天流形式 / 输入类型 / 思考档位声明 / 容量 ----
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = MilkSpace.sm),
                verticalArrangement = Arrangement.spacedBy(MilkSpace.sm),
            ) {
                Spacer(Modifier.height(MilkSpace.xxs))
                MilkTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "显示名",
                    placeholder = "如 GLM-5.4V",
                    onClear = { name = "" },
                )

                // 聊天流形式:模型级覆盖(空 = 跟随服务默认)
                Column(Modifier.fillMaxWidth()) {
                    MilkSectionLabel(text = "聊天流形式")
                    Spacer(Modifier.height(MilkSpace.xs))
                    MilkModelProtocolOverridePicker(
                        overrideProtocol = model.protocol,
                        providerProtocol = profileProtocol,
                        baseUrl = baseUrl,
                        onSelect = onUpdateProtocol,
                    )
                }

                // 输入类型(harness ModelInputTypes:文本恒选且锁定,图片 = 视觉声明)
                Column(Modifier.fillMaxWidth()) {
                    MilkSectionLabel(text = "输入类型")
                    Spacer(Modifier.height(MilkSpace.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs)) {
                        MilkFilterChip(
                            label = "文本",
                            selected = true,
                            enabled = false,
                            onClick = {},
                            small = true,
                        )
                        MilkFilterChip(
                            label = "图片",
                            selected = model.vision,
                            onClick = { onUpdate(null, !model.vision, null, null, null) },
                            small = true,
                        )
                    }
                }

                // 思考档位声明(多选声明,空=不可调;流式换行防裁切)
                Column(Modifier.fillMaxWidth()) {
                    MilkSectionLabel(text = "思考档位声明（空 = 该模型不可调）")
                    Spacer(Modifier.height(MilkSpace.xs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
                        verticalArrangement = Arrangement.spacedBy(MilkSpace.xxs),
                    ) {
                        ThinkingDispatchUI.editable.forEach { level ->
                            val on = levels.contains(level)
                            MilkFilterChip(
                                label = ThinkingDispatchUI.label(level),
                                selected = on,
                                onClick = {
                                    val next = if (on) levels - level else levels + level
                                    onUpdate(null, null, next, null, null)
                                },
                                small = true,
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CapacityField("上下文 k tok", ctx) { ctx = it }
                    CapacityField("最大输出 k tok", out) { out = it }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MilkButton(
                        label = "保存",
                        onClick = {
                            onUpdate(name.ifBlank { null }, null, null, ctx.toIntOrNull(), out.toIntOrNull())
                        },
                        size = MilkButtonSize.SMALL,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.CapacityField(label: String, value: String, onChange: (String) -> Unit) {
    MilkTextField(
        value = value,
        onValueChange = { raw -> onChange(raw.filter { it.isDigit() }.take(4)) },
        modifier = Modifier.weight(1f),
        label = label,
        placeholder = "—",
        mono = true,
        filled = true,
    )
}

// ==================== 添加模型服务(预设 → 协议 → 字段 → Key) ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddProviderDialog(
    onCancel: () -> Unit,
    onConfirm: (presetId: String?, title: String, baseUrl: String, key: String, protocol: Protocol) -> Unit,
) {
    var presetId by remember { mutableStateOf(ProviderRegistry.presets.first().id) }
    var title by remember { mutableStateOf(ProviderRegistry.presets.first().title) }
    var baseUrl by remember { mutableStateOf(ProviderRegistry.presets.first().baseUrl) }
    var key by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(ProviderRegistry.presets.first().protocol) }

    MilkCenterPanel(
        title = "添加模型服务",
        subtitle = "先选预设，再填 Key；协议按端点实际情况切换",
        onDismiss = onCancel,
        maxWidth = 600.dp,
        maxHeight = 640.dp,
        footer = {
            Spacer(Modifier.weight(1f))
            MilkButton(
                label = "取消",
                onClick = onCancel,
                variant = MilkButtonVariant.SECONDARY,
            )
            Spacer(Modifier.width(10.dp))
            MilkButton(
                label = "保存",
                enabled = baseUrl.isNotBlank(),
                onClick = { onConfirm(if (presetId == "custom") null else presetId, title, baseUrl, key, protocol) },
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
                verticalArrangement = Arrangement.spacedBy(MilkSpace.xxs),
            ) {
                ProviderRegistry.presets.forEach { preset ->
                    MilkChip(
                        label = preset.title,
                        selected = preset.id == presetId,
                        onClick = {
                            presetId = preset.id
                            if (preset.id == "custom") {
                                title = ""
                                baseUrl = ""
                            } else {
                                title = preset.title
                                baseUrl = preset.baseUrl
                            }
                            protocol = preset.protocol
                        },
                        small = true,
                    )
                }
            }
            Spacer(Modifier.height(MilkSpace.lg))
            // 协议三选一:下方常驻端点预览,切换协议不会让表单高低跳
            MilkProtocolPicker(
                selected = protocol,
                onSelect = { protocol = it },
                baseUrl = baseUrl,
            )
            Spacer(Modifier.height(MilkSpace.md))
            MilkTextField(
                value = title,
                onValueChange = { title = it },
                label = "名称",
                placeholder = "如 智谱 GLM",
                onClear = { title = "" },
            )
            Spacer(Modifier.height(10.dp))
            MilkTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = "baseUrl",
                placeholder = "https://api.example.com/v1",
                mono = true,
                onClear = { baseUrl = "" },
            )
            Spacer(Modifier.height(10.dp))
            MilkTextField(
                value = key,
                onValueChange = { key = it },
                label = "API Key",
                placeholder = "sk-…",
                mono = true,
                mask = true,
            )
            Spacer(Modifier.height(MilkSpace.sm))
            Text(
                "Key 只写入本机 Keystore，不在界面回显，也不会随笔记同步。",
                style = MilkText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 档位标签/解析(UI 侧) */
private object ThinkingDispatchUI {
    val editable = listOf(
        ReasoningEffort.OFF,
        ReasoningEffort.MINIMAL,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH,
        ReasoningEffort.XHIGH,
        ReasoningEffort.MAX,
    )

    fun label(e: ReasoningEffort): String = when (e) {
        ReasoningEffort.AUTO -> "自动"
        ReasoningEffort.OFF -> "关"
        ReasoningEffort.MINIMAL -> "最低"
        ReasoningEffort.LOW -> "低"
        ReasoningEffort.MEDIUM -> "中"
        ReasoningEffort.HIGH -> "高"
        ReasoningEffort.XHIGH -> "超高"
        ReasoningEffort.MAX -> "最高"
    }

    fun parse(raw: List<String>): List<ReasoningEffort> =
        raw.mapNotNull { runCatching { ReasoningEffort.valueOf(it) }.getOrNull() }
}
