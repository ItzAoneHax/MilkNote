package com.milknote.app.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.milknote.agent.EndpointResolver
import com.milknote.agent.Protocol
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import kotlinx.coroutines.delay

/**
 * 模型套件(写作面板选模型 + 设置页模型服务共用):
 * 协议标签/徽标、协议选择器、服务分组头、模型单选行、可展开可复制的错误框。
 *
 * 零位移纪律:协议选择器下方常驻 20dp 端点预览、错误框动作钮固定最小宽度、
 * 单选行尾簇固定顺序——任何文字变化或元素出现都不推动相邻元素的位置。
 */

/** 协议产品名:设置/面板里显示的完整写法 */
fun protocolDisplayLabel(protocol: Protocol): String = when (protocol) {
    Protocol.OPENAI_COMPATIBLE -> "ChatCompletions"
    Protocol.ANTHROPIC -> "Anthropic"
    Protocol.OPENAI_RESPONSES -> "Responses"
}

/** 协议简称:塞进单选行/服务头的紧凑写法 */
fun protocolShortLabel(protocol: Protocol): String = when (protocol) {
    Protocol.OPENAI_COMPATIBLE -> "Chat"
    Protocol.ANTHROPIC -> "Anthropic"
    Protocol.OPENAI_RESPONSES -> "Responses"
}

/**
 * 协议胶囊:恒 22dp 高、单行 mono。
 * 默认(跟随服务)= 中性底;模型级覆盖 = 淡强调底,一眼看出「这台模型不走服务默认协议」。
 */
@Composable
fun MilkProtocolBadge(
    protocol: Protocol,
    overridden: Boolean = false,
    short: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val fg = if (overridden) cs.primary else cs.onSurfaceVariant
    val bg = if (overridden) cs.primary.copy(alpha = 0.11f) else cs.surfaceVariant.copy(alpha = 0.8f)
    Row(
        modifier
            .height(22.dp)
            .clip(RoundedCornerShape(MilkRadius.xs))
            .background(bg)
            .padding(horizontal = MilkSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (short) protocolShortLabel(protocol) else protocolDisplayLabel(protocol),
            style = MilkText.monoMicro,
            color = fg,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 服务默认协议选择器:三段式 + 常驻端点预览。
 * 预览行即使 baseUrl 为空也占满 20dp,因此切换协议只会改变文字,不会让下方内容上下跳。
 */
@Composable
fun MilkProtocolPicker(
    selected: Protocol,
    onSelect: (Protocol) -> Unit,
    baseUrl: String = "",
    compact: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        MilkSegmented(
            options = PROTOCOL_OPTIONS,
            selected = selected,
            label = ::protocolDisplayLabel,
            onSelect = onSelect,
            compact = compact,
        )
        MilkProtocolEndpointPreview(
            text = if (baseUrl.isBlank()) "" else "POST ${EndpointResolver.resolveChatUrl(baseUrl, selected)}",
            color = cs.onSurfaceVariant,
        )
    }
}

/**
 * 模型级协议覆盖:四段式(跟随服务 / Chat / Anthropic / Responses)+ 常驻端点预览。
 * 预览按「覆盖生效后的协议」解析,覆盖与否的差异在切换前就能看见。
 */
@Composable
fun MilkModelProtocolOverridePicker(
    overrideProtocol: Protocol?,
    providerProtocol: Protocol,
    baseUrl: String,
    onSelect: (Protocol?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val effective = overrideProtocol ?: providerProtocol
    Column(modifier.fillMaxWidth()) {
        MilkSegmented(
            options = PROTOCOL_OVERRIDE_OPTIONS,
            selected = overrideProtocol,
            label = { option -> if (option == null) "跟随服务" else protocolShortLabel(option) },
            onSelect = onSelect,
            compact = true,
        )
        MilkProtocolEndpointPreview(
            text = if (baseUrl.isBlank()) "" else "POST ${EndpointResolver.resolveChatUrl(baseUrl, effective)}",
            color = if (overrideProtocol != null) cs.primary else cs.onSurfaceVariant,
        )
    }
}

private val PROTOCOL_OPTIONS = listOf(
    Protocol.OPENAI_COMPATIBLE,
    Protocol.ANTHROPIC,
    Protocol.OPENAI_RESPONSES,
)

private val PROTOCOL_OVERRIDE_OPTIONS = listOf<Protocol?>(
    null,
    Protocol.OPENAI_COMPATIBLE,
    Protocol.ANTHROPIC,
    Protocol.OPENAI_RESPONSES,
)

/** 端点预览的固定槽位:行高与文字行高无关,空串时高度不变 */
@Composable
private fun MilkProtocolEndpointPreview(text: String, color: Color) {
    Box(Modifier.fillMaxWidth().height(20.dp).padding(top = 2.dp), contentAlignment = Alignment.CenterStart) {
        Text(
            text = text,
            style = MilkText.monoMicro,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 服务分组头:标题 + 协议徽标 + 可选缺 Key 徽标 + 右对齐的尾部元信息。
 * 标题让位收缩,徽标左靠,尾部信息右靠——三者互不推动。
 */
@Composable
fun MilkModelProviderHeader(
    title: String,
    protocol: Protocol,
    missingKey: Boolean = false,
    trailingText: String? = null,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MilkText.eyebrow,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(MilkSpace.sm))
            MilkProtocolBadge(protocol, short = true)
            if (missingKey) {
                Spacer(Modifier.width(MilkSpace.xs))
                MilkStatusBadge(text = "缺 Key", tone = MilkStatusTone.DANGER)
            }
        }
        if (!trailingText.isNullOrBlank()) {
            Spacer(Modifier.width(MilkSpace.sm))
            Text(
                text = trailingText,
                style = MilkText.monoMicro,
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 统一模型单选行:44dp 起跳的点击区、22dp 圆点槽位、中间名称/副行、尾簇协议+能力+容量。
 * [subtitle] 缺省时第二行回退为「与显示名不同的 modelId」,两者相同则只有一行。
 */
@Composable
fun MilkModelSelectRow(
    displayName: String,
    modelId: String,
    selected: Boolean,
    protocol: Protocol,
    protocolOverridden: Boolean,
    vision: Boolean,
    contextWindowK: Int,
    subtitle: String? = null,
    showVisionBadge: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val title = displayName.ifBlank { modelId }
    val second = subtitle?.takeIf { it.isNotBlank() }
        ?: modelId.takeIf { it.isNotBlank() && it != title }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MilkRadius.sm))
            .background(if (selected) cs.primary.copy(alpha = 0.09f) else Color.Transparent)
            .softClickable(onClick = onClick)
            .semantics { role = Role.RadioButton }
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (selected) cs.primary else Color.Transparent, CircleShape)
                    .border(1.dp, if (selected) cs.primary else cs.outline, CircleShape),
            )
        }
        Spacer(Modifier.width(MilkSpace.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MilkText.rowTitle,
                color = if (selected) cs.primary else cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (second != null) {
                Text(
                    text = second,
                    style = MilkText.monoMicro,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(MilkSpace.sm))
        MilkProtocolBadge(protocol, overridden = protocolOverridden, short = true)
        if (showVisionBadge) {
            Spacer(Modifier.width(MilkSpace.xxs))
            MilkStatusBadge(
                text = if (vision) "视觉" else "纯文本",
                tone = if (vision) MilkStatusTone.ACCENT else MilkStatusTone.NEUTRAL,
            )
        }
        if (contextWindowK > 0) {
            Spacer(Modifier.width(MilkSpace.xs))
            Text(
                text = "${contextWindowK}k",
                style = MilkText.monoMicro,
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 可展开可复制的错误面(写作面板 ErrorBanner 与设置页测试/拉取失败共用)。
 *
 * 长错误永不永久截断:折叠态最多 3 行摘要,展开后完整原文落进 240dp 滚动框,
 * 「复制」一次拿走 `标题 + 换行 + 原文`。两个动作钮都用最小宽度锁死,
 * `展开详情`↔`收起详情`、`复制`↔`已复制` 切换时相邻钮不移位。
 */
@Composable
fun MilkExpandableErrorBox(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val text = detail?.takeIf { it.isNotBlank() }
    val expandable = text != null && (text.length > 90 || text.contains('\n'))
    var expanded by remember(title, text) { mutableStateOf(false) }
    var copied by remember(title, text) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MilkRadius.md))
            .background(cs.error.copy(alpha = 0.08f))
            .border(0.75.dp, cs.error.copy(alpha = 0.24f), RoundedCornerShape(MilkRadius.md))
            .padding(horizontal = 10.dp, vertical = MilkSpace.sm),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = cs.error,
            )
            Spacer(Modifier.width(MilkSpace.sm))
            Text(title, style = MilkText.label, color = cs.error, modifier = Modifier.weight(1f))
        }
        if (text != null) {
            Spacer(Modifier.height(2.dp))
            if (expanded) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp + MilkSpace.sm)
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(MilkRadius.sm))
                        .background(cs.surface)
                        .border(1.dp, cs.outline, RoundedCornerShape(MilkRadius.sm))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MilkSpace.sm, vertical = MilkSpace.xs),
                ) {
                    Text(text, style = MilkText.monoMicro, color = cs.onSurface)
                }
            } else {
                Text(
                    text = text,
                    style = MilkText.micro,
                    color = cs.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp + MilkSpace.sm),
                )
            }
        }
        Spacer(Modifier.height(MilkSpace.xxs))
        Row(
            horizontalArrangement = Arrangement.spacedBy(MilkSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (expandable) {
                MilkInlineAction(
                    label = if (expanded) "收起详情" else "展开详情",
                    onClick = { expanded = !expanded },
                    minWidth = 86.dp,
                )
            }
            MilkInlineAction(
                label = if (copied) "已复制" else "复制",
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("error", "$title\n${text.orEmpty()}"),
                    )
                    copied = true
                },
                icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                tone = if (copied) MilkInlineTone.SUCCESS else MilkInlineTone.NEUTRAL,
                minWidth = 74.dp,
            )
        }
    }
}
