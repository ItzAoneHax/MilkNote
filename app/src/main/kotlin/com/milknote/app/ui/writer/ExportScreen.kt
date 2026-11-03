package com.milknote.app.ui.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image as ImageIcon
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.ui.common.accentBrush
import com.milknote.app.ui.common.innerHighlight
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.common.softHover
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.Mono
import com.milknote.app.ui.theme.PillShape

/**
 * 独立导出页(MilkScreen.Export):顶部返回条 + 居中 ≤560dp 内容列(视觉沿设置页/§5.1「柔和的皮」)。
 * 信息卡(笔记本名称/当前页/总页数)+ 两种格式卡(PNG 当前页 / PDF 整本);
 * 复用 WriterViewModel.state 与 exportPng/exportPdf(不触碰 PageExporter),导出中双卡按钮禁用,
 * 结果就地状态行提示。BackHandler 由 MilkRoot 统一处理(返回书写页)。
 */
@Composable
fun ExportScreen(
    vm: WriterViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val nb = state.notebook

    // 导出进行中(null=空闲;"png"/"pdf"=对应格式导出中)+ 结果提示(PageExporter 返回文案就地展示)
    var exporting by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    val startExport: (String) -> Unit = { kind ->
        if (exporting == null && nb != null) {
            exporting = kind
            result = null
            val done: (String) -> Unit = { msg ->
                exporting = null
                result = msg
            }
            if (kind == "png") vm.exportPng(done) else vm.exportPdf(done)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)   // 背景先铺满(含状态栏区域)
            .windowInsetsPadding(WindowInsets.safeDrawing),     // 内容再避开 insets
    ) {
        ExportTopBar(notebookName = nb?.name ?: "", onBack = onBack)

        // 顶栏/内容分隔:左右渐隐 hairline(与设置页导航分隔同语言)
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.05f to MaterialTheme.colorScheme.outline,
                        0.95f to MaterialTheme.colorScheme.outline,
                        1f to Color.Transparent,
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 560.dp)   // 先限宽再 fill(顺序反了会通栏;平板横屏不溢出)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // 页头(设置页 SettingsPageHeader 式)
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text("导出", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (nb == null) "未打开笔记本" else "「${nb.name}」 · ${state.pages.size} 页",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 信息卡:笔记本名称 / 当前页 / 总页数
                ExportCard {
                    InfoRow("笔记本", nb?.name ?: "—")
                    CardHairline()
                    InfoRow("当前页", state.currentPageId, mono = true)
                    CardHairline()
                    InfoRow("总页数", "${state.pages.size} 页", mono = true)
                }

                // 分区标题(设置页 SettingsSectionHeader 式)
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text("导出格式", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "均含批注卡;导出与缩略图恒为浅色纸",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExportFormatCard(
                        icon = Icons.Outlined.ImageIcon,
                        title = "PNG 图片 · 当前页",
                        subtitle = "导出「${state.currentPageId}」单页 → 图片/MilkNote",
                        actionLabel = "导出 PNG",
                        busy = exporting == "png",
                        locked = exporting != null || nb == null,
                        onAction = { startExport("png") },
                    )
                    ExportFormatCard(
                        icon = Icons.Outlined.PictureAsPdf,
                        title = "PDF 文档 · 整本",
                        subtitle = "全部 ${state.pages.size} 页合并 → 文档/MilkNote",
                        actionLabel = "导出 PDF",
                        busy = exporting == "pdf",
                        locked = exporting != null || nb == null,
                        onAction = { startExport("pdf") },
                    )
                    // 结果提示:成功淡蓝 / 失败淡红(结果文案由导出接口返回)
                    result?.let { msg -> ResultLine(msg) }
                }

                Text(
                    "PNG 保存到系统「图片/MilkNote」,PDF 保存到「文档/MilkNote」",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 顶部返回条:‹ 返回书写(点击回书写页)| 右侧笔记本名(chrome 色带 + 顶缘内高光,同书写页顶栏) */
@Composable
private fun ExportTopBar(notebookName: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .innerHighlight(strength = 0.3f)
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onBack)
                .softHover()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("返回书写", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.weight(1f))
        Text(
            notebookName,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 导出卡:设置卡配方(surface 底 + 16dp 圆角 + hairline 边框 + 软阴影 + 顶缘内高光) */
@Composable
private fun ExportCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .softShadow(3.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .innerHighlight(),
    ) { content() }
}

/** 卡内行间 hairline(对齐行文字缩进,同设置页) */
@Composable
private fun CardHairline() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(start = 16.dp),
    )
}

/** 信息行:左灰标签 / 右值(页码·数字按字体纪律走 mono) */
@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = 13.sp,
            fontFamily = if (mono) Mono else null,
            fontWeight = if (mono) null else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** 格式卡:图标底板 + 标题/副题 + 强调色导出钮;locked(另一格式导出中/无笔记本)时按钮禁用置灰 */
@Composable
private fun ExportFormatCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    busy: Boolean,
    locked: Boolean,
    onAction: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        AccentAction(
            label = if (busy) "导出中…" else actionLabel,
            busy = busy,
            enabled = !locked,
            onClick = onAction,
        )
    }
}

/** 强调色胶囊 CTA(同书写页 Agent 钮配方):导出中转小 spinner 并阻止重复触发 */
@Composable
private fun AccentAction(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .alpha(if (enabled || busy) 1f else 0.45f)
            .softShadow(5.dp, PillShape)
            .clip(PillShape)
            .background(accentBrush())
            .softClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** 结果状态行:✓ 淡蓝(已导出…) / ✗ 淡红(保存失败/没有页面),就地展示不弹窗 */
@Composable
private fun ResultLine(msg: String) {
    val ok = msg.startsWith("已导出")
    val tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (ok) "✓" else "✗", fontSize = 12.sp, color = tint)
        Spacer(Modifier.width(8.dp))
        Text(msg, fontSize = 12.sp, lineHeight = 17.sp, color = tint)
    }
}
