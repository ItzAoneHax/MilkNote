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
import com.milknote.agent.ProviderRegistry
import com.milknote.agent.VisionSource
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.theme.Mono
import com.milknote.app.ui.common.accentBrush
import kotlinx.coroutines.launch

/**
 * 设置 → Agent(AGENT-BACKEND §12):
 * Provider 列表(预设 12 选 1 或自定义)/ 模型管理(手填+视觉声明)/ 测试连接(仅验鉴权)。
 */
@Composable
fun AgentSettingsSection() {
    val agent = ServiceLocator.agentController
    val state by agent.state.collectAsState()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var addingModel by remember { mutableStateOf<String?>(null) }
    var editingKey by remember { mutableStateOf<String?>(null) }
    var managing by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    Column {
        // Provider 卡片
        state.profiles.forEach { profile ->
            val active = profile.id == state.activeProfileId
            val hasKey = agent.hasKey(profile)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(
                        width = if (active) 1.dp else 0.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable {
                        managing = if (managing == profile.id) null else profile.id
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.width(8.dp))
                            if (!hasKey) {
                                Text("缺 Key", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(
                            profile.baseUrl.ifBlank { "未填 baseUrl" },
                            fontFamily = Mono, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Text(
                        "${profile.models.count { it.vision }} 模型",
                        fontFamily = Mono, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (managing == profile.id) {
                    Spacer(Modifier.height(10.dp))
                    // 模型列表(仅视觉可激活;非视觉灰显可删)
                    profile.models.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(m.id, fontFamily = Mono, fontSize = 11.sp,
                                color = if (m.vision) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when (m.visionSource) {
                                    VisionSource.PRESET.name -> "预设·已验证"
                                    VisionSource.METADATA.name -> "元数据"
                                    else -> "已声明"
                                },
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            if (!m.vision) {
                                Text("非视觉", fontSize = 9.sp, color = MaterialTheme.colorScheme.error)
                            }
                            Text(
                                if (m.id == profile.activeModelId) "默认" else "设默认",
                                fontSize = 11.sp,
                                color = if (m.id == profile.activeModelId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable(enabled = m.vision) { agent.selectModel(profile.id, m.id) }
                                    .padding(horizontal = 6.dp),
                            )
                            Text(
                                "删",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { agent.removeModel(profile.id, m.id) }
                                    .padding(horizontal = 6.dp),
                            )
                        }
                    }
                    testResult[profile.id]?.let {
                        Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        MiniAction("设为默认 Provider") { agent.setActiveProfile(profile.id) }
                        MiniAction("拉取模型列表") {
                            scope.launch {
                                agent.fetchModels(profile.id).onSuccess { list ->
                                    testResult = testResult + (profile.id to "拉到 ${list.size} 个模型(vision 标记:${list.count { it.vision }});在下方手填加入")
                                }.onFailure { testResult = testResult + (profile.id to "拉取失败:${it.message?.take(80)}(可手填)") }
                            }
                        }
                        MiniAction("测试连接") {
                            scope.launch {
                                agent.testConnection(profile.id).onSuccess { ms ->
                                    testResult = testResult + (profile.id to "✓ 鉴权可达 · ${ms}ms")
                                }.onFailure { e ->
                                    testResult = testResult + (profile.id to "✗ ${e.message?.take(100)}")
                                }
                            }
                        }
                        MiniAction("+ 模型") { addingModel = profile.id }
                        MiniAction("更新 Key") { editingKey = profile.id }
                        MiniAction("删除", danger = true) { agent.removeProfile(profile.id) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // 新增
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .clickable { adding = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("+ 新增 Provider", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text("预设 12 家 / 自定义 OpenAI 兼容", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text(
            "Key 经 Android Keystore 加密仅存本机,不随笔记本备份。整个 Agent 仅在视觉模型上工作。",
            fontSize = 10.sp, lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 2.dp),
        )
    }

    if (adding) {
        AddProviderDialog { presetId, title, baseUrl, key ->
            agent.addProfile(presetId ?: "custom", title, baseUrl, key)
            adding = false
        }
    }
    if (addingModel != null) {
        AddModelDialog { modelId, declared ->
            agent.addModel(addingModel!!, modelId, declared)
            addingModel = null
        }
    }
    if (editingKey != null) {
        KeyDialog(profileId = editingKey!!) { key ->
            agent.setApiKey(editingKey!!, key)
            editingKey = null
        }
    }
}

/** 手填 modelId:视觉声明复选框为保存前提(§3.4) */
@Composable
private fun AddModelDialog(onConfirm: (String, Boolean) -> Unit) {
    var modelId by remember { mutableStateOf("") }
    var declared by remember { mutableStateOf(false) }
    val agent = ServiceLocator.agentController
    DialogCard(title = "添加模型") {
        FieldRow("modelId", modelId, mono = true) { modelId = it }
        Spacer(Modifier.height(8.dp))
        // 固定警示(§3.4 声明式约束)
        Text(
            "MilkNote 的全部能力依赖多模态视觉模型(图片输入)。请确认你填写的模型支持图片输入;纯文本模型将无法使用框选讲解。",
            fontSize = 11.sp, lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { declared = !declared }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (declared) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (declared) Text("✓", color = Color.White, fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text("我已确认该模型支持图片输入", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(8.dp))
        val canSave = declared && modelId.isNotBlank()
        Box(
            Modifier
                .clip(CircleShape)
                .then(
                    if (canSave) Modifier.background(accentBrush())
                    else Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                )
                .clickable(enabled = canSave) { onConfirm(modelId, declared) }
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) { Text("保存", color = if (canSave) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        Spacer(Modifier.height(2.dp))
        // 可选:拉取列表结果直接采纳(手填为主,拉取尽力而为)
        Text("「拉取模型列表」结果可在上方测试;OpenRouter 拉取可自动标记视觉。", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        // 静默引用避免未用告警
        @Suppress("UNUSED_EXPRESSION")
        agent.state
    }
}

@Composable
private fun MiniAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.sp,
        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** 新增 Provider:预设选择 + 自定义;Key 仅写入 KeyVault */
@Composable
private fun AddProviderDialog(onConfirm: (presetId: String?, title: String, baseUrl: String, key: String) -> Unit) {
    var presetId by remember { mutableStateOf(ProviderRegistry.presets.first().id) }
    var title by remember { mutableStateOf(ProviderRegistry.presets.first().title) }
    var baseUrl by remember { mutableStateOf(ProviderRegistry.presets.first().baseUrl) }
    var key by remember { mutableStateOf("") }

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
                                    if (preset.id == "custom") {
                                        title = ""
                                        baseUrl = ""
                                    } else {
                                        title = preset.title
                                        baseUrl = preset.baseUrl
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        FieldRow("名称", title) { title = it }
        FieldRow("baseUrl", baseUrl, mono = true) { baseUrl = it }
        FieldRow("API Key(仅存本机)", key, mono = true, mask = true) { key = it }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(accentBrush())
                    .clickable { onConfirm(if (presetId == "custom") null else presetId, title, baseUrl, key) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) { Text("保存", color = Color.White, fontSize = 13.sp) }
        }
    }
}

/** 修改 Key(保存后关闭) */
@Composable
private fun KeyDialog(profileId: String, onConfirm: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    DialogCard(title = "更新 API Key") {
        FieldRow("API Key(仅存本机)", key, mono = true, mask = true) { key = it }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(accentBrush())
                .clickable { if (key.isNotBlank()) onConfirm(key) }
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) { Text("保存", color = Color.White, fontSize = 13.sp) }
    }
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
