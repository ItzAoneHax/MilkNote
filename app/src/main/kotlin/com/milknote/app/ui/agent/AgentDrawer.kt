package com.milknote.app.ui.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.agent.AgentError
import com.milknote.agent.AgentMessage
import com.milknote.agent.AgentUiState
import com.milknote.agent.Intents
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.theme.Mono
import com.milknote.app.ui.common.accentBrush

/** ⑤ Agent 抽屉:右缘滑入 360dp 浮层(DESIGN-V0 §5.4;AGENT-BACKEND §8) */
@Composable
fun AgentDrawerHost(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val agent = ServiceLocator.agentController
    val state by agent.state.collectAsState()

    AnimatedVisibility(
        visible = state.drawerOpen,
        enter = slideInHorizontally(tween(220)) { it } + fadeIn(tween(160)),
        exit = slideOutHorizontally(tween(200)) { it } + fadeOut(tween(140)),
        modifier = modifier,
    ) {
        AgentDrawer(state = state, onOpenSettings = onOpenSettings)
    }

}

@Composable
private fun AgentDrawer(state: AgentUiState, onOpenSettings: () -> Unit) {
    val agent = ServiceLocator.agentController
    // 宿主在书写页画布区(顶栏下/底部状态条上,DESIGN-V0 §5.4);容器已吃 safeDrawing
    // (含 IME)inset,此处不再重复 padding,否则顶进状态栏/盖住底条(真机实证)
    Column(
        Modifier
            .width(360.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        DrawerHeader(state)
        if (state.setupRequired) {
            SetupGuide(onOpenSettings)
        } else {
            ContextChips(state)
            Messages(state, Modifier.weight(1f))
            Composer(state)
            DrawerHints(state)
        }
    }
}

@Composable
private fun DrawerHeader(state: AgentUiState) {
    val agent = ServiceLocator.agentController
    var showModels by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Agent", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(10.dp))
        // 模型胶囊:模型名 mono + › 切换
        Row(
            Modifier
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { showModels = !showModels }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.activeModelId ?: "未配置",
                fontFamily = Mono, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            // 思考档位徽标(AUTO 不显示)
            state.profiles.firstOrNull { it.id == state.activeProfileId }?.let { pf ->
                val eff = runCatching { com.milknote.agent.ReasoningEffort.valueOf(pf.reasoningEffort) }
                    .getOrDefault(com.milknote.agent.ReasoningEffort.AUTO)
                if (eff != com.milknote.agent.ReasoningEffort.AUTO) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "思考·${effortLabel(eff)}",
                        fontFamily = Mono, fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable { agent.closeDrawer() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, "收起", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showModels) ModelSheet(state) { showModels = false }
}

/** 无可用视觉模型 → 引导卡(§3.4:入口即视觉,不显示空对话) */
@Composable
private fun SetupGuide(onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("配置视觉模型后开始", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "MilkNote 的全部 Agent 能力依赖多模态视觉模型。\n设置 → Agent:选厂商预设,填入你的 API Key。",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(accentBrush())
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 20.dp, vertical = 9.dp),
        ) {
            Text("去设置", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** 上下文 chips:选区 N / 当前页 p.X / +整章 + 预估(§5.2) */
@Composable
private fun ContextChips(state: AgentUiState) {
    val agent = ServiceLocator.agentController
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.draftAttachments.isNotEmpty()) {
            Chip("选区 · ${state.draftAttachments.size}", selected = true) {}
        }
        state.chips.forEach { chip ->
            Chip(
                label = chip.label + if (chip.selected && chip.estImages > 0) " +${chip.estImages}" else "",
                selected = chip.selected,
            ) { agent.toggleContext(chip.id) }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "≈${agent.estTokensNow()} tok",
            fontFamily = Mono, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label, fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------- 消息区(§8.4 流式渲染) ----------------

@Composable
private fun Messages(state: AgentUiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val agent = ServiceLocator.agentController

    // 贴底跟随;用户上滑即解除
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            last == null || last.index >= listState.layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(state.messages.lastOrNull()?.text?.length, state.messages.size) {
        if (atBottom && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        // 底部预留输入条高度:操作 chips 不被 composer 遮挡(视觉审查实证)
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.messages, key = { it.id }) { msg ->
            when (msg.role) {
                AgentMessage.Role.USER -> UserMessage(msg)
                AgentMessage.Role.ASSISTANT -> AssistantMessage(state, msg)
            }
        }
        if (state.error != null) {
            item(key = "err") { ErrorBanner(state.error!!) }
        }
    }
}

/** 思考过程折叠胶囊(默认收起;mono 灰,展开后内衬浅底) */
@Composable
private fun ReasoningBlock(msg: com.milknote.agent.UiMessage) {
    var expanded by remember(msg.id) { mutableStateOf(false) }
    val streamingThought = msg.streaming && msg.text.isBlank()
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (streamingThought) "✻ 思考中…" else "✻ 思考过程 · ${msg.reasoning.length} 字",
                fontFamily = Mono, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(if (expanded) "▾" else "▸", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            Text(
                msg.reasoning,
                fontFamily = Mono, fontSize = 10.sp, lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun UserMessage(msg: com.milknote.agent.UiMessage) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        // 附件卡:逻辑名 + 尺寸 mono(§1.2)
        msg.attachments.forEach { att ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Description, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(att.logicalName, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${att.widthPx}×${att.heightPx}px · 已含手写层与批注",
                        fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (msg.text.isNotBlank()) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(msg.text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun AssistantMessage(state: AgentUiState, msg: com.milknote.agent.UiMessage) {
    val agent = ServiceLocator.agentController
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        // 思考过程:默认折叠;流式中(答案未出)显示「思考中…」,完成后显示字数
        if (msg.reasoning.isNotBlank()) {
            ReasoningBlock(msg)
            Spacer(Modifier.height(6.dp))
        }
        MarkdownBlocks(msg.text + if (msg.streaming) " ▍" else "")
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // meta 行:模型 · tokens(Codex 风 mono)
            val meta = buildString {
                append(msg.modelId ?: "")
                if (msg.promptTokens > 0 || msg.completionTokens > 0) {
                    append(" · ${(msg.promptTokens / 100.0).let { if (it >= 10) it.toInt() else String.format("%.1f", it).toFloat() }}k→")
                    append("${(msg.completionTokens / 100.0).let { if (it >= 10) it.toInt() else String.format("%.1f", it).toFloat() }}k tok")
                }
            }
            if (meta.isNotBlank()) {
                Text(meta, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
        }
        // 状态与操作 chips(§9):复制 / 插入批注层(幂等)/ 记忆卡 M3 stub
        when (msg.phase) {
            AgentMessage.Phase.STREAMING -> {}
            AgentMessage.Phase.FAILED -> ActionChip("重试", accent = true) { agent.retry() }
            AgentMessage.Phase.INTERRUPTED -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("已中断", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionChip("重试", accent = true) { agent.retry() }
            }
            AgentMessage.Phase.DONE -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip("复制", icon = Icons.Outlined.ContentCopy) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("agent", msg.text))
                }
                if (msg.insertedAnnotationId == null) {
                    ActionChip("插入批注层", accent = true) { agent.insertAnnotation(msg.id) }
                } else {
                    ActionChip("已插入批注层 ✓", accent = false) {}
                }
                ActionChip("记忆卡", enabled = false) {}   // M3
            }
            else -> {}
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    accent: Boolean = false,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    accent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, null, modifier = Modifier.size(12.dp), tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label, fontSize = 11.sp,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                accent -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ErrorBanner(error: AgentError) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(errorTitle(error), fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
            Text(error.detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

private fun errorTitle(e: AgentError): String = when (e) {
    is AgentError.Auth -> "Key 无效或额度不足"
    is AgentError.RateLimit -> "请求过快,已自动退避"
    is AgentError.Server -> "服务方故障"
    is AgentError.Network -> "网络不可用"
    is AgentError.Timeout -> "网络超时"
    is AgentError.ModelNotVision -> "当前模型疑似不支持图片输入,请更换视觉模型"
    is AgentError.NotFound -> "模型或端点不存在(可能已下线)"
    is AgentError.ContextTooLarge -> "上下文过大,请减少页数"
    is AgentError.StreamBroken -> "流式传输中断"
    is AgentError.NoProvider, is AgentError.NotVision -> "未配置视觉模型"
    else -> "请求失败"
}

// ---------------- 输入区(追问 / slash) ----------------

@Composable
private fun Composer(state: AgentUiState) {
    val agent = ServiceLocator.agentController
    var text by remember { mutableStateOf("") }
    LaunchedEffect(state.draftText) { }
    val streaming = state.phase == AgentMessage.Phase.STREAMING || state.phase == AgentMessage.Phase.SENDING

    // slash 面板(§6.4:前缀匹配六意图 + /清空)
    val slashSuggestions = if (text.startsWith("/")) {
        listOfNotNull(
            *Intents.all.filter { "/${it.label}".startsWith(text) || text == "/" }
                .map { "/${it.label}" to it.id }.toTypedArray(),
            if ("/清空".startsWith(text)) "/清空" to null else null,
        )
    } else emptyList()

    Column(Modifier.fillMaxWidth()) {
        if (slashSuggestions.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                slashSuggestions.take(7).forEach { (label, intent) ->
                    Chip(label, selected = false) {
                        if (intent != null) {
                            text = ""
                            agent.sendWithIntent(intent)
                        } else {
                            text = ""
                            agent.clearSession()
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 回形针:当前页并入草稿附件
            Icon(
                Icons.Outlined.AttachFile, "附加当前页",
                modifier = Modifier
                    .size(20.dp)
                    .clickable { agent.toggleContext("current_page") },   // 附加当前页 = 开 current_page chip
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(
                        "继续追问,或输入 / 唤起命令…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            if (streaming) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { agent.stop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Stop, "停止", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            } else {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accentBrush())
                        .clickable {
                            agent.setDraftText(text)
                            agent.send()
                            text = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, "发送", modifier = Modifier.size(15.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DrawerHints(state: AgentUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "笔键长按 再次框选 · 结果可贴回批注层",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.weight(1f))
        Text(
            state.boundPage?.pageId ?: "",
            fontFamily = Mono, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

// ---------------- 模型切换 sheet(仅视觉) ----------------

@Composable
private fun ModelSheet(state: AgentUiState, onDismiss: () -> Unit) {
    val agent = ServiceLocator.agentController
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(vertical = 8.dp),
    ) {
        state.profiles.forEach { profile ->
            Text(
                profile.title,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            profile.models.filter { it.vision }.forEach { m ->
                val active = profile.id == state.activeProfileId && m.id == state.activeModelId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            agent.selectModel(profile.id, m.id)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(m.id, fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        // ---- 思考强度(§3.8:每家档位不同,按当前模型声明显示) ----
        val activeProfile = state.profiles.firstOrNull { it.id == state.activeProfileId }
        val activeModel = activeProfile?.activeModel()
        val declared = activeModel?.thinkingLevels
            ?.mapNotNull { runCatching { com.milknote.agent.ReasoningEffort.valueOf(it) }.getOrNull() }
            ?: listOf(
                // 自定义模型未声明:按 OpenAI 标准给通用四档
                com.milknote.agent.ReasoningEffort.OFF,
                com.milknote.agent.ReasoningEffort.LOW,
                com.milknote.agent.ReasoningEffort.MEDIUM,
                com.milknote.agent.ReasoningEffort.HIGH,
            )
        val current = runCatching { com.milknote.agent.ReasoningEffort.valueOf(activeProfile?.reasoningEffort ?: "AUTO") }
            .getOrDefault(com.milknote.agent.ReasoningEffort.AUTO)
        Spacer(Modifier.height(8.dp))
        Text(
            "思考强度 · ${activeModel?.id ?: state.activeModelId ?: ""}",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            EffortChip("自动", current == com.milknote.agent.ReasoningEffort.AUTO) {
                agent.setReasoningEffort(com.milknote.agent.ReasoningEffort.AUTO)
            }
            declared.forEach { level ->
                EffortChip(effortLabel(level), current == level) {
                    agent.setReasoningEffort(level)
                }
            }
        }
        Text(
            "档位映射因厂商而异(OpenAI/Kimi reasoning_effort · DeepSeek/GLM/Qwen thinking 开关 · OpenRouter reasoning.effort · Claude 预算);自动=不发参数",
            fontSize = 9.sp, lineHeight = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "管理模型 → 设置 · Agent",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clickable(onClick = onDismiss),
        )
    }
}

private fun effortLabel(e: com.milknote.agent.ReasoningEffort): String = when (e) {
    com.milknote.agent.ReasoningEffort.AUTO -> "自动"
    com.milknote.agent.ReasoningEffort.OFF -> "关"
    com.milknote.agent.ReasoningEffort.MINIMAL -> "最低"
    com.milknote.agent.ReasoningEffort.LOW -> "低"
    com.milknote.agent.ReasoningEffort.MEDIUM -> "中"
    com.milknote.agent.ReasoningEffort.HIGH -> "高"
    com.milknote.agent.ReasoningEffort.XHIGH -> "超高"
    com.milknote.agent.ReasoningEffort.MAX -> "最高"
}

@Composable
private fun EffortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.sp,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

// ---------------- Markdown 渲染(§6.3 容错子集) ----------------

@Composable
fun MarkdownBlocks(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { MarkdownLite.parse(text) }
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { i, b ->
            when (b) {
                is MarkdownLite.Block.Heading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(remember(b) { spansToAnnotated(b.spans) }, fontWeight = FontWeight.SemiBold, fontSize = (18 - b.level * 2).coerceIn(11, 16).sp)
                }
                is MarkdownLite.Block.ListItem -> Row {
                    Text(
                        b.order?.let { "$it. " } ?: "•  ",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(remember(b) { spansToAnnotated(b.spans) }, fontSize = 13.sp, lineHeight = 19.sp)
                }
                is MarkdownLite.Block.Paragraph -> Text(
                    remember(b) { spansToAnnotated(b.spans) },
                    fontSize = 13.sp, lineHeight = 19.sp,
                )
                is MarkdownLite.Block.MathBlock -> Box(
                    Modifier
                        .padding(vertical = 3.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(b.latex, fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                is MarkdownLite.Block.TagLine -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    b.tags.take(8).forEach { tag ->
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text("#$tag", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (i != blocks.lastIndex) Spacer(Modifier.height(4.dp))
        }
    }
}

private fun spansToAnnotated(spans: List<MarkdownLite.Span>): AnnotatedString {
    val builder = AnnotatedString.Builder()
    for (s in spans) {
        when (s) {
            is MarkdownLite.Span.Plain -> builder.append(s.text)
            is MarkdownLite.Span.Bold -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                builder.append(s.text)
                builder.pop()
            }
            is MarkdownLite.Span.Code -> {
                builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x14000000)))
                builder.append(s.text)
                builder.pop()
            }
            is MarkdownLite.Span.Math -> {
                builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                builder.append(s.latex)
                builder.pop()
            }
        }
    }
    return builder.toAnnotatedString()
}
