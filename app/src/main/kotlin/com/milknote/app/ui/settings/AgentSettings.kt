package com.milknote.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.agent.ModelInfo
import com.milknote.agent.Protocol
import com.milknote.agent.ProviderProfile
import com.milknote.agent.ProviderRegistry
import com.milknote.agent.ReasoningEffort
import com.milknote.agent.VisionSource
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.theme.Mono
import kotlinx.coroutines.launch

/**
 * 设置 → Agent(A2.4 重构,选择列表模式迁移自 deepseek-harness ui-settings-models):
 * - Provider 卡 = 主字段「只写 API Key」+ 折叠自定义区(harness ProviderEditor 同款)
 * - 模型目录 = 行式编辑器:id 行 + 展开区(显示名/输入类型/思考档位声明/容量)(harness ModelRow)
 * - 拉取 = 候选勾选选择器,一次加减多个,默认勾选已配置项(harness fetch picker)
 * 风格按 DESIGN-V0 §5:mono 元数据 / 单强调色 / 胶囊小控件 / MilkTheme。
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

    Column {
        state.profiles.forEach { profile ->
            val active = profile.id == state.activeProfileId
            ProviderCard(
                profile = profile,
                active = active,
                hasKey = agent.hasKey(profile),
                expanded = expandedProvider == profile.id,
                expandedModelId = expandedModel,
                testLine = testResult[profile.id],
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
                    scope.launch {
                        agent.testConnection(profile.id).onSuccess { ms ->
                            testResult = testResult + (profile.id to "✓ 鉴权可达 · ${ms}ms")
                        }.onFailure { e -> testResult = testResult + (profile.id to "✗ ${e.message?.take(110)}") }
                    }
                },
                onSetActive = { agent.setActiveProfile(profile.id) },
                onDelete = { agent.removeProfile(profile.id) },
                onSelectModel = { agent.selectModel(profile.id, it) },
                onAddModel = { agent.addModel(profile.id, it, declaredVision = true) },
                onRemoveModel = { agent.removeModel(profile.id, it) },
                onUpdateModel = { id, dn, vis, lv, ctx, out ->
                    agent.updateModel(profile.id, id, dn, vis, lv, ctx, out)
                },
                onFetch = { onDone ->
                    scope.launch { agent.fetchModels(profile.id).let(onDone) }
                },
                onApplyFetched = { models -> agent.setModels(profile.id, models) },
            )
            Spacer(Modifier.height(10.dp))
        }

        // 新增
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable { adding = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("+ 新增 Provider", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text("预设 12 家 / 自定义", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text(
            "Key 经 Android Keystore 加密仅存本机,不随笔记本备份。整个 Agent 仅在视觉模型上工作;思考档位按模型声明,每家不同。",
            fontSize = 10.sp, lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    if (adding) {
        AddProviderDialog { presetId, title, baseUrl, key, protocol ->
            agent.addProfile(presetId ?: "custom", title, baseUrl, key, protocol)
            adding = false
        }
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
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(
                width = if (active) 1.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // ---- 标题行:激活点 | 名称 | 协议标签(mono)| 缺 Key | 模型数 | › ----
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(profile.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(8.dp))
            ProtocolTag(profile.protocol)
            if (!hasKey) {
                Spacer(Modifier.width(6.dp))
                Text("缺 Key", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.weight(1f))
            MonoMetaLine("${profile.models.size} 模型")
            Spacer(Modifier.width(8.dp))
            Text(if (expanded) "▾" else "▸", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            // ---- 主字段:API Key(只写;harness「单一主键输入」位) ----
            ApiKeyBlock(hasKey = hasKey, onSave = { ServiceLocator.agentController.setApiKey(profile.id, it) })
            Spacer(Modifier.height(8.dp))
            // ---- 自定义区:baseUrl ----
            EditableMonoField(label = "baseUrl", value = profile.baseUrl, onCommit = onBaseUrlChange)
            Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniAction(if (active) "✓ 当前 Provider" else "设为当前") { if (!active) onSetActive() }
                MiniAction("测试连接") { onTest() }
                MiniAction("删除 Provider", danger = true) { onDelete() }
            }
        }
    }
}

/** 协议产品名(harness protocol-label:显示产品名而非 schema id) */
@Composable
private fun ProtocolTag(protocol: Protocol) {
    Text(
        when (protocol) {
            Protocol.OPENAI_COMPATIBLE -> "OpenAI 兼容"
            Protocol.ANTHROPIC -> "Anthropic"
        },
        fontFamily = Mono, fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier
            .clip(CircleShape)
            .border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun ApiKeyBlock(hasKey: Boolean, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(!hasKey) }
    var key by remember { mutableStateOf("") }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("API Key", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (!editing) {
                Text("•••• 已保存(仅本机)", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                MiniAction("更换") { editing = true }
            }
        }
        if (editing) {
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    if (key.isEmpty()) {
                        Text("sk-…", fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    BasicTextField(
                        value = key,
                        onValueChange = { key = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, color = MaterialTheme.colorScheme.onBackground),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                MiniAction("保存", primary = true) {
                    if (key.isNotBlank()) { onSave(key); editing = false; key = "" }
                }
            }
        }
    }
}

/** 就地编辑的 mono 字段(baseUrl):失焦提交 */
@Composable
private fun EditableMonoField(label: String, value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, color = MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (text != value) {
            Spacer(Modifier.height(4.dp))
            MiniAction("保存 baseUrl", primary = true) { onCommit(text) }
        }
    }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("模型", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(6.dp))
            MonoMetaLine(
                "${profile.models.size}" + if (profile.presetId != null) " · 预设可编辑" else " · 自定义",
            )
            Spacer(Modifier.weight(1f))
            MiniAction(
                when (fetchState) {
                    is FetchState.Loading -> "拉取中…"
                    else -> "从端点拉取"
                },
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
            Spacer(Modifier.width(6.dp))
            MiniAction("+ 添加") {
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
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    if (newModelId.isEmpty()) {
                        Text("modelId(如 glm-5.4v)", fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    BasicTextField(
                        value = newModelId,
                        onValueChange = { newModelId = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, color = MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                MiniAction("加入", primary = true) {
                    if (newModelId.isNotBlank()) {
                        onAddModel(newModelId.trim())
                        newModelId = ""
                        fetchState = null
                    }
                }
                MiniAction("取消") { fetchState = null; newModelId = "" }
            }
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
                Spacer(Modifier.height(4.dp))
                Text("✗ ${fs.message}(可手填 modelId)", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
            }
            else -> {}
        }

        Spacer(Modifier.height(4.dp))
        if (profile.models.isEmpty()) {
            Text(
                "暂无模型:从端点拉取,或在上方手填 modelId(勾选「图片」输入类型后方可用于框选)",
                fontSize = 10.sp, lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        profile.models.forEach { model ->
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
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("端点返回 ${candidates.size} 个模型", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("已选 ${picked.size}", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            if (query.isEmpty()) Text("搜索…", fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, color = MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(6.dp))
        val filtered = remember(candidates, query) {
            candidates.filter { query.isBlank() || it.id.contains(query, true) }
        }
        Column(Modifier.height(240.dp)) {
            androidx.compose.foundation.lazy.LazyColumn {
                items(filtered.size) { idx ->
                    val c = filtered[idx]
                    val checked = c.id in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { picked = if (checked) picked - c.id else picked + c.id }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(15.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (checked) Text("✓", color = Color.White, fontSize = 10.sp) }
                        Spacer(Modifier.width(8.dp))
                        Text(c.id, fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        if (c.vision) {
                            Text("视觉", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("需勾图片", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniAction("应用所选", primary = true) {
                // 已配置行保留原声明(用户调过的档位不被拉取覆盖);勾选的新行按候选进表
                val kept = current.filter { it.id in picked }
                val fresh = candidates.filter { it.id in picked && current.none { cur -> cur.id == it.id } }
                onApply(kept + fresh)
            }
            MiniAction("取消") { onCancel() }
        }
    }
}

// ==================== 模型行(harness ModelRow:折叠行 + 展开高级区) ====================

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
    var name by remember(model.displayName) { mutableStateOf(model.displayName) }
    var ctx by remember(model.contextWindowK) { mutableStateOf(if (model.contextWindowK > 0) model.contextWindowK.toString() else "") }
    var out by remember(model.maxTokensK) { mutableStateOf(if (model.maxTokensK > 0) model.maxTokensK.toString() else "") }
    val levels = remember(model.thinkingLevels) { ThinkingDispatchUI.parse(model.thinkingLevels) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (expanded) 0.75f else 0.45f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        // ---- 折叠行:默认点 | id(mono)+ 徽章 | 容量 | › | 删 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable(enabled = model.vision, onClick = onSelectDefault)
                    .background(if (isDefault) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                    .border(1.dp, if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.id, fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    if (!model.vision) Text("纯文本", fontSize = 9.sp, color = MaterialTheme.colorScheme.error)
                    if (model.visionSource == VisionSource.DECLARED.name) {
                        Spacer(Modifier.width(4.dp))
                        Text("已声明", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
                if (levels.isNotEmpty()) {
                    Text(
                        "思考 " + levels.joinToString("/") { ThinkingDispatchUI.label(it) },
                        fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            MonoMetaLine(capacityLine(model))
            Spacer(Modifier.width(6.dp))
            Text(if (expanded) "▾" else "▸", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable(onClick = onToggle).padding(4.dp))
            Spacer(Modifier.width(2.dp))
            Text("✕", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.clickable(onClick = onRemove).padding(4.dp))
        }

        // ---- 展开区:显示名 / 输入类型 / 思考档位声明 / 容量(harness ModelRow 高级区) ----
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("显示名", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            // 输入类型(harness ModelInputTypes:文本/图片复选 = 视觉声明)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("输入类型", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                MiniAction(
                    if (model.vision) "图片 ✓" else "图片",
                    primary = model.vision,
                ) { onUpdate(null, !model.vision, null, null, null) }
                Spacer(Modifier.width(6.dp))
                Text("文本 ✓", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Spacer(Modifier.weight(1f))
                MiniAction("保存") { onUpdate(name.ifBlank { null }, null, null, ctx.toIntOrNull(), out.toIntOrNull()) }
            }
            Spacer(Modifier.height(6.dp))
            // 思考档位声明(harness ModelReasoningEfforts:多选声明,空=不可调)
            Text("思考档位声明(空 = 该模型不可调)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ThinkingDispatchUI.editable.forEach { level ->
                    val on = levels.contains(level)
                    Text(
                        ThinkingDispatchUI.label(level),
                        fontSize = 10.sp,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                val next = if (on) levels - level else levels + level
                                onUpdate(null, null, next, null, null)
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CapacityField("上下文 k tok", ctx) { ctx = it }
                CapacityField("最大输出 k tok", out) { out = it }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CapacityField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.weight(1f)) {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            if (value.isEmpty()) Text("—", fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, color = MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun capacityLine(m: ModelInfo): String = buildString {
    if (m.contextWindowK > 0) append("${m.contextWindowK}k")
    if (m.maxTokensK > 0) {
        if (isNotEmpty()) append(" / ")
        append("out ${m.maxTokensK}k")
    }
    if (isEmpty()) append("—")
}

// ==================== 新增 Provider(预设 → 字段 → 协议 → Key) ====================

@Composable
private fun AddProviderDialog(
    onConfirm: (presetId: String?, title: String, baseUrl: String, key: String, protocol: Protocol) -> Unit,
) {
    var presetId by remember { mutableStateOf(ProviderRegistry.presets.first().id) }
    var title by remember { mutableStateOf(ProviderRegistry.presets.first().title) }
    var baseUrl by remember { mutableStateOf(ProviderRegistry.presets.first().baseUrl) }
    var key by remember { mutableStateOf("") }
    var anthropic by remember { mutableStateOf(false) }

    DialogCard(title = "新增 Provider") {
        // 预设网格
        Column {
            ProviderRegistry.presets.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { preset ->
                        val sel = preset.id == presetId
                        Text(
                            preset.title,
                            fontSize = 11.sp,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .clickable {
                                    presetId = preset.id
                                    if (preset.id == "custom") { title = ""; baseUrl = "" }
                                    else { title = preset.title; baseUrl = preset.baseUrl; anthropic = preset.protocol == Protocol.ANTHROPIC }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        // 协议(自定义可选 Anthropic;预设跟随)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("协议", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            MiniAction(if (!anthropic) "OpenAI 兼容 ✓" else "OpenAI 兼容") { anthropic = false }
            Spacer(Modifier.width(6.dp))
            MiniAction(if (anthropic) "Anthropic ✓" else "Anthropic") { anthropic = true }
            Spacer(Modifier.weight(1f))
            if (anthropic) MonoMetaLine("POST {base}/v1/messages", 9)
        }
        Spacer(Modifier.height(8.dp))
        FieldRow("名称", title) { title = it }
        FieldRow("baseUrl", baseUrl, mono = true) { baseUrl = it }
        FieldRow("API Key(仅存本机)", key, mono = true, mask = true) { key = it }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .then(
                        if (baseUrl.isNotBlank()) Modifier.background(accentBrushSafe())
                        else Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    .clickable(enabled = baseUrl.isNotBlank()) {
                        onConfirm(
                            if (presetId == "custom") null else presetId,
                            title, baseUrl, key,
                            if (anthropic) Protocol.ANTHROPIC else Protocol.OPENAI_COMPATIBLE,
                        )
                    }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) { Text("保存", color = if (baseUrl.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun accentBrushSafe() = com.milknote.app.ui.common.accentBrush()

// ==================== 小控件 ====================

@Composable
private fun MiniAction(label: String, danger: Boolean = false, primary: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.sp,
        color = when {
            danger -> MaterialTheme.colorScheme.error
            primary -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(CircleShape)
            .background(
                when {
                    primary -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun MonoMetaLine(text: String, size: Int = 10) {
    Text(text, fontFamily = Mono, fontSize = size.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
}

/** 档位标签/解析(UI 侧) */
private object ThinkingDispatchUI {
    val editable = listOf(ReasoningEffort.OFF, ReasoningEffort.MINIMAL, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH, ReasoningEffort.XHIGH, ReasoningEffort.MAX)

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

@Composable
internal fun DialogCard(title: String, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
internal fun FieldRow(label: String, value: String, mono: Boolean = false, mask: Boolean = false, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = if (mono) Mono else null,
                ),
                visualTransformation = if (mask) androidx.compose.ui.text.input.PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
