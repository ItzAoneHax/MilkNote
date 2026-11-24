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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milknote.agent.ModelInfo
import com.milknote.agent.Protocol
import com.milknote.agent.ProviderProfile
import com.milknote.agent.ProviderRegistry
import com.milknote.agent.ReasoningEffort
import com.milknote.agent.VisionSource
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.app.ui.common.MilkChip
import com.milknote.app.ui.common.MilkConfirmDialog
import com.milknote.app.ui.common.MilkFilterChip
import com.milknote.app.ui.common.MilkIconButton
import com.milknote.app.ui.common.MilkSegmented
import com.milknote.app.ui.common.MilkStatusBadge
import com.milknote.app.ui.common.MilkStatusTone
import com.milknote.app.ui.common.MilkTextField
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import kotlinx.coroutines.launch

/**
 * 设置 → AI 教师(A2.4 逻辑 + A2.7 视觉重构,选择列表模式迁移自 deepseek-harness ui-settings-models):
 * - Provider 卡 = 主字段「只写 API Key」+ 折叠自定义区(harness ProviderEditor 同款)
 * - 模型目录 = 行式编辑器:id 行 + 展开区(显示名/输入类型/思考档位声明/容量)(harness ModelRow)
 * - 拉取 = 候选勾选选择器,一次加减多个,默认勾选已配置项(harness fetch picker)
 * 视觉走 SettingsKit:hairline 行 / 值列 › / ✓ 当前 / 文字操作钮 / mono 只用于值;
 * 表单一律用共享 MilkTextField(自带光标色,暗色下不会隐形)。
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
                        // baseUrl 就地保存(整 profile 重写)
                        val book = com.milknote.agent.AgentStore.ProfileBook(
                            state.profiles.map { if (it.id == profile.id) it.copy(baseUrl = url) else it },
                            state.activeProfileId,
                        )
                        agent.replaceProfiles(book)
                    },
                    onTest = {
                        if (profile.id !in testingIds) {
                            testingIds = testingIds + profile.id
                            scope.launch {
                                agent.testConnection(profile.id)
                                    .onSuccess { ms -> testResult = testResult + (profile.id to "鉴权可达 · ${ms}ms") }
                                    .onFailure { e ->
                                        testResult = testResult + (profile.id to (e.message?.take(110) ?: "连接失败"))
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

// ==================== Provider 卡(harness ProviderEditor 模式) ====================

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
    onTest: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit,
    onSelectModel: (String) -> Unit,
    onAddModel: (String) -> Unit,
    onRemoveModel: (String) -> Unit,
    onUpdateModel: (String, String?, Boolean?, List<ReasoningEffort>?, Int?, Int?) -> Unit,
    onFetch: ((Result<List<ModelInfo>>) -> Unit) -> Unit,
    onApplyFetched: (List<ModelInfo>) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    SettingsCard(
        modifier = Modifier.animateContentSize(tween(200)),
        borderColor = if (active) cs.primary.copy(alpha = 0.3f) else cs.outline,
        containerColor = if (active) cs.primary.copy(alpha = 0.05f) else cs.surface,
    ) {
        // ---- 标题行:激活点 | 名称 | 协议徽标 | 缺 Key | 模型数 | ▸/▾ ----
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
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
            // 名称 + 徽标成组:名称最多占满剩余宽度,长标题省略号收缩,徽标不会被挤飞
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.title,
                    style = MilkText.rowTitle,
                    color = cs.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(MilkSpace.sm))
                MilkStatusBadge(text = protocolLabel(profile.protocol), tone = MilkStatusTone.NEUTRAL, mono = true)
                if (!hasKey) {
                    Spacer(Modifier.width(MilkSpace.xs))
                    MilkStatusBadge(text = "缺 Key", tone = MilkStatusTone.DANGER)
                }
            }
            Spacer(Modifier.width(MilkSpace.sm))
            MonoMetaLine("${profile.models.size} 模型")
            Spacer(Modifier.width(MilkSpace.sm))
            MilkIconButton(
                imageVector = if (expanded) Icons.Outlined.ExpandMore else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = if (expanded) "收起 Provider" else "展开 Provider",
                onClick = onToggleExpand,
            )
        }

        if (expanded) {
            Hairline()
            // ---- 主字段:API Key(只写;harness「单一主键输入」位) ----
            ApiKeyBlock(hasKey = hasKey, onSave = { ServiceLocator.agentController.setApiKey(profile.id, it) })
            Hairline()
            // ---- 自定义区:baseUrl ----
            BaseUrlField(value = profile.baseUrl, onCommit = onBaseUrlChange)
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
                onFetch = onFetch,
                onApplyFetched = onApplyFetched,
            )
            testLine?.let {
                Hairline()
                StatusLine(it, tone = if (it.startsWith("鉴权可达")) StatusTone.OK else StatusTone.ERROR)
            }
            Hairline()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = MilkSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(MilkSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextAction(
                    if (active) "✓ 当前 Provider" else "设为当前",
                    primary = active,
                    enabled = !active,
                    onClick = onSetActive,
                )
                TextAction(
                    label = if (testing) "测试中…" else "测试连接",
                    enabled = !testing,
                    onClick = onTest,
                )
                Spacer(Modifier.weight(1f))
                TextAction("删除 Provider", danger = true, onClick = onDelete)
            }
        }
    }
}

/** 协议产品名(harness protocol-label:显示产品名而非 schema id) */
private fun protocolLabel(protocol: Protocol): String = when (protocol) {
    Protocol.OPENAI_COMPATIBLE -> "OpenAI 兼容"
    Protocol.ANTHROPIC -> "Anthropic"
}

@Composable
private fun ApiKeyBlock(hasKey: Boolean, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(!hasKey) }
    var key by remember { mutableStateOf("") }
    if (!editing) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = MilkSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("API Key", style = MilkText.body, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.weight(1f))
            Text(
                "•••• 已保存",
                style = MilkText.monoMeta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(MilkSpace.sm))
            TextAction("更换") { editing = true }
        }
    } else {
        SettingsInputField(
            value = key,
            onValueChange = { key = it },
            label = "API Key",
            placeholder = "sk-…",
            mono = true,
            mask = true,
            dirty = key.isNotBlank(),
            onSave = {
                if (key.isNotBlank()) { onSave(key); editing = false; key = "" }
            },
            onCancel = if (hasKey) ({ editing = false }) else null,
        )
    }
}

/** 就地编辑的 baseUrl 字段(mono;脏→保存) */
@Composable
private fun BaseUrlField(value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    SettingsInputField(
        value = text,
        onValueChange = { text = it },
        label = "baseUrl",
        mono = true,
        dirty = text != value,
        onSave = { onCommit(text) },
        onCancel = { text = value },
    )
}

// ==================== 模型目录(harness ModelListEditor/ModelRow 模式) ====================

@Composable
private fun ModelCatalogSection(
    profile: ProviderProfile,
    expandedModelId: String?,
    onToggleModel: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onAddModel: (String) -> Unit,
    onRemoveModel: (String) -> Unit,
    onUpdateModel: (String, String?, Boolean?, List<ReasoningEffort>?, Int?, Int?) -> Unit,
    onFetch: ((Result<List<ModelInfo>>) -> Unit) -> Unit,
    onApplyFetched: (List<ModelInfo>) -> Unit,
) {
    var fetchState by remember { mutableStateOf<FetchState?>(null) }
    var newModelId by remember { mutableStateOf("") }

    Column {
        // ---- 目录头:模型 · N(继承/自定义)+ 拉取 + 添加 ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("模型", style = MilkText.rowTitle, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(MilkSpace.xs))
            MonoMetaLine(
                "${profile.models.size}" + if (profile.presetId != null) " · 预设可编辑" else " · 自定义",
            )
            Spacer(Modifier.weight(1f))
            TextAction(
                when (fetchState) {
                    is FetchState.Loading -> "拉取中…"
                    else -> "从端点拉取"
                },
                enabled = fetchState !is FetchState.Loading,
            ) {
                if (fetchState !is FetchState.Loading) {
                    fetchState = FetchState.Loading
                    onFetch { result ->
                        fetchState = result.fold(
                            onSuccess = { FetchState.Picker(it) },
                            onFailure = { FetchState.Failed(it.message?.take(100) ?: "拉取失败") },
                        )
                    }
                }
            }
            TextAction("+ 添加") {
                if (newModelId.isNotBlank()) {
                    onAddModel(newModelId.trim())
                    newModelId = ""
                } else {
                    fetchState = fetchState ?: FetchState.Adding
                }
            }
        }

        // 添加行(点 + 添加 出现/harness addModel)
        if (fetchState is FetchState.Adding) {
            SettingsInputField(
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
                onCancel = { fetchState = null; newModelId = "" },
            )
        }

        // 拉取勾选器(harness fetch picker:候选列表 + 勾选,默认勾选已配置;应用=整表替换)
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
            is FetchState.Failed -> {
                StatusLine("${fs.message}(可手填 modelId)", tone = StatusTone.ERROR)
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
                    if (idx > 0) Hairline()
                    ModelRow(
                        model = model,
                        isDefault = model.id == profile.activeModelId,
                        expanded = expandedModelId == model.id,
                        onToggle = { onToggleModel(model.id) },
                        onSelectDefault = { onSelectModel(model.id) },
                        onRemove = { onRemoveModel(model.id) },
                        onUpdate = { dn, vis, lv, ctx, out -> onUpdateModel(model.id, dn, vis, lv, ctx, out) },
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
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MilkSpace.sm, vertical = MilkSpace.sm)
            .clip(RoundedCornerShape(MilkRadius.md))
            .border(1.dp, cs.outline, RoundedCornerShape(MilkRadius.md))
            .padding(MilkSpace.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "端点返回 ${candidates.size} 个模型",
                style = MilkText.caption,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "已选 ${picked.size}",
                style = MilkText.monoMeta,
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
                        .heightIn(min = 40.dp)
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
                    Text(
                        c.id,
                        style = MilkText.monoBody,
                        color = cs.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(MilkSpace.sm))
                    if (c.vision) {
                        MilkStatusBadge(text = "视觉", tone = MilkStatusTone.ACCENT)
                    } else {
                        Text("需勾图片", style = MilkText.micro, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(MilkSpace.xxs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextAction("应用所选", primary = true) {
                // 已配置行保留原声明(用户调过的档位不被拉取覆盖);勾选的新行按候选进表
                val kept = current.filter { it.id in picked }
                val fresh = candidates.filter { it.id in picked && current.none { cur -> cur.id == it.id } }
                onApply(kept + fresh)
            }
            TextAction("取消") { onCancel() }
        }
    }
}

// ==================== 模型行(harness ModelRow:折叠行 + 展开高级区) ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelRow(
    model: ModelInfo,
    isDefault: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelectDefault: () -> Unit,
    onRemove: () -> Unit,
    onUpdate: (String?, Boolean?, List<ReasoningEffort>?, Int?, Int?) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var name by remember(model.displayName) { mutableStateOf(model.displayName) }
    var ctx by remember(model.contextWindowK) { mutableStateOf(if (model.contextWindowK > 0) model.contextWindowK.toString() else "") }
    var out by remember(model.maxTokensK) { mutableStateOf(if (model.maxTokensK > 0) model.maxTokensK.toString() else "") }
    val levels = remember(model.thinkingLevels) { ThinkingDispatchUI.parse(model.thinkingLevels) }

    Column(Modifier.fillMaxWidth().animateContentSize(tween(200))) {
        // ---- 折叠行:默认点 | id(mono)+ 徽章 | 容量 | ▸ | 删 ----
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
                Modifier.size(48.dp).clickable(enabled = model.vision, onClick = onSelectDefault),
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
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        model.id,
                        style = MilkText.monoBody,
                        color = cs.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!model.vision) {
                        Spacer(Modifier.width(MilkSpace.xs))
                        MilkStatusBadge(text = "纯文本", tone = MilkStatusTone.DANGER)
                    }
                    if (model.visionSource == VisionSource.DECLARED.name) {
                        Spacer(Modifier.width(MilkSpace.xxs))
                        MilkStatusBadge(text = "已声明", tone = MilkStatusTone.NEUTRAL)
                    }
                }
                if (levels.isNotEmpty()) {
                    Text(
                        "思考 " + levels.joinToString("/") { ThinkingDispatchUI.label(it) },
                        style = MilkText.micro,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(MilkSpace.xs))
            MonoMetaLine(capacityLine(model))
            MilkIconButton(
                imageVector = if (expanded) Icons.Outlined.ExpandMore else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = if (expanded) "收起模型设置" else "展开模型设置",
                onClick = onToggle,
            )
            MilkIconButton(
                imageVector = Icons.Outlined.Close,
                contentDescription = "删除模型",
                onClick = onRemove,
            )
        }

        // ---- 展开区:显示名 / 输入类型 / 思考档位声明 / 容量(harness ModelRow 高级区) ----
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = MilkSpace.sm)) {
                Spacer(Modifier.height(MilkSpace.xxs))
                MilkTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "显示名",
                    placeholder = "如 GLM-5.4V",
                    onClear = { name = "" },
                )
                Spacer(Modifier.height(10.dp))

                // 输入类型(harness ModelInputTypes:文本恒选且锁定,图片 = 视觉声明)
                Text("输入类型", style = MilkText.micro, color = cs.onSurfaceVariant)
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
                Spacer(Modifier.height(10.dp))

                // 思考档位声明(harness ModelReasoningEfforts:多选声明,空=不可调;流式换行防裁切)
                Text(
                    "思考档位声明（空 = 该模型不可调）",
                    style = MilkText.micro,
                    color = cs.onSurfaceVariant,
                )
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
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CapacityField("上下文 k tok", ctx) { ctx = it }
                    CapacityField("最大输出 k tok", out) { out = it }
                }
                Spacer(Modifier.height(MilkSpace.xs))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextAction("保存", primary = true) {
                        onUpdate(name.ifBlank { null }, null, null, ctx.toIntOrNull(), out.toIntOrNull())
                    }
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

private fun capacityLine(m: ModelInfo): String = buildString {
    if (m.contextWindowK > 0) append("${m.contextWindowK}k")
    if (m.maxTokensK > 0) {
        if (isNotEmpty()) append(" / ")
        append("out ${m.maxTokensK}k")
    }
    if (isEmpty()) append("—")
}

// ==================== 添加模型服务(预设 → 字段 → 协议 → Key) ====================

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
    var anthropic by remember { mutableStateOf(false) }

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
                onClick = {
                    onConfirm(
                        if (presetId == "custom") null else presetId,
                        title, baseUrl, key,
                        if (anthropic) Protocol.ANTHROPIC else Protocol.OPENAI_COMPATIBLE,
                    )
                },
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
                                anthropic = preset.protocol == Protocol.ANTHROPIC
                            }
                        },
                        small = true,
                    )
                }
            }
            Spacer(Modifier.height(MilkSpace.lg))
            MilkSegmented(
                options = listOf(false, true),
                selected = anthropic,
                label = { if (it) "Anthropic" else "OpenAI 兼容" },
                onSelect = { anthropic = it },
            )
            if (anthropic) {
                Spacer(Modifier.height(MilkSpace.xs))
                MonoMetaLine("POST {base}/v1/messages")
            }
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

@Composable
private fun MonoMetaLine(text: String) {
    Text(text, style = MilkText.monoMeta, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
