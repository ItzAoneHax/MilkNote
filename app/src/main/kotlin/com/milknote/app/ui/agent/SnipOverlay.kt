package com.milknote.app.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.milknote.agent.Intents
import com.milknote.agent.Rect4
import com.milknote.app.ui.theme.AccentBlue
import com.milknote.app.ui.theme.AccentBlueDark
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.OverlayBorderDark
import com.milknote.app.ui.theme.OverlayMuted
import com.milknote.app.ui.theme.OverlaySurfaceLight
import com.milknote.app.ui.theme.OverlayText
import com.milknote.app.ui.theme.PillShape
import com.milknote.app.ui.theme.ScrimCanvas
import com.milknote.app.ui.writer.SnipPhase
import com.milknote.app.ui.writer.SnipUiState
import com.milknote.ink.render.InkSurfaceView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ④ 框选遮罩(DESIGN-V0 §4.2 / AGENT-BACKEND §4.2):
 * 冻结画布 + 压暗 → 拖出选区(角手柄 + 尺寸标注 mono)→ 松开弹意图条(默认讲解 ↵)。
 * 覆盖在画布之上拦截输入(书写自然暂停);坐标 = 视口坐标(与 InkSurfaceView 同 bounds)。
 * 触控规则(R2 复审):角柄命中 24dp;pointerId 锁定当前拖动(第二触点/手掌不抢);
 * pointerCancel 不执行动作;选框/角柄全程零插值。
 */
@Composable
fun SnipOverlay(
    state: SnipUiState,
    surface: () -> InkSurfaceView?,
    onDrag: (Rect4) -> Unit,
    onRelease: () -> Unit,
    onRectChanged: (Rect4) -> Unit,
    onResetDrag: () -> Unit,
    onFullPage: (Rect4) -> Unit,
    onCancel: () -> Unit,
    onIntent: (Intents.Id) -> Unit,
    onSend: ((Float, Float) -> Pair<Float, Float>) -> Unit,
    onSaveCard: ((Float, Float) -> Pair<Float, Float>) -> Unit,
    onAttach: (((Float, Float) -> Pair<Float, Float>) -> Unit)? = null,
) {
    val dim = ScrimCanvas
    val current by rememberUpdatedState(state)
    val onResetDragLatest by rememberUpdatedState(onResetDrag)
    val onRectChangedLatest by rememberUpdatedState(onRectChanged)

    // CONFIRMED 态手势:拖角柄缩放,拖体内移动,点外部取消。
    // 取消语义:pointerCancel 在 Compose 中=变更被预消费,官方 drag() 原语返回 null,
    // 据此区分自然抬手与系统取消;取消不执行动作、回滚矩形。
    val confirmModifier = if (state.phase == SnipPhase.CONFIRMED && state.rect != null) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val st = current
                val r = st.rect ?: return@awaitEachGesture
                val down = awaitFirstDown()
                val p = down.position
                val corner = nearestCorner(r, p.x, p.y, 24.dp.toPx())
                val inside = p.x >= r.l && p.x <= r.r && p.y >= r.t && p.y <= r.b
                when {
                    corner != null -> {   // 角柄缩放(视觉角柄 7dp,命中 24dp)
                        val ok = drag(down.id) { change ->
                            onRectChangedLatest(withCorner(r, corner, change.position.x, change.position.y))
                        }
                        if (ok == null) onRectChangedLatest(r)   // 取消:回滚到手势前矩形
                    }
                    inside -> {           // 整体移动(增量累积;drag 只跟首触点 id,第二触点不抢)
                        var dx = 0f
                        var dy = 0f
                        var prev = p
                        val ok = drag(down.id) { change ->
                            dx += change.position.x - prev.x
                            dy += change.position.y - prev.y
                            prev = change.position
                            onRectChangedLatest(Rect4(r.l + dx, r.t + dy, r.r + dx, r.b + dy))
                        }
                        if (ok == null) onRectChangedLatest(r)
                    }
                    else -> {             // 遮罩外点按:抬手(未取消)即退出框选
                        val ok = drag(down.id) { }
                        if (ok != null) onCancel()
                    }
                }
            }
        }
    } else {
        Modifier.pointerInput(Unit) {   // FROZEN 态:拖出选区;取消→清空选区留在 FROZEN
            awaitEachGesture {
                val down = awaitFirstDown()
                val start = down.position
                onDrag(Rect4(start.x, start.y, start.x, start.y))
                val end = drag(down.id) { change ->
                    onDrag(Rect4.fromDrag(start.x, start.y, change.position.x, change.position.y))
                }
                if (end != null) onRelease() else onResetDragLatest()
            }
        }
    }

    Box(confirmModifier.fillMaxSize()) {
        // 压暗遮罩:选区外四边矩形(选区内透亮)
        val r = state.rect
        Canvas(Modifier.fillMaxSize()) {
            if (r == null) {
                drawRect(dim)
            } else {
                drawRect(dim, size = Size(r.l, size.height))
                drawRect(dim, topLeft = Offset(r.r, 0f), size = Size(size.width - r.r, size.height))
                drawRect(dim, topLeft = Offset(r.l, 0f), size = Size(r.width, r.t))
                drawRect(dim, topLeft = Offset(r.l, r.b), size = Size(r.width, size.height - r.b))
            }
        }

        // FROZEN 初始引导:居中一句提示;「取消」固定为右上角悬浮胶囊(任何阶段可见)
        if (state.phase == SnipPhase.FROZEN && r == null) {
            Text(
                "拖动框选要提问的区域",
                style = MilkText.label,
                color = OverlayText,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(MilkRadius.sm))
                    .background(OverlaySurfaceLight)
                    .padding(horizontal = MilkSpace.lg - 2.dp, vertical = MilkSpace.sm),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = MilkSpace.lg, end = MilkSpace.lg)
                .clip(PillShape)
                .background(OverlaySurfaceLight)
                .border(1.dp, OverlayBorderDark, PillShape)
                .clickable(onClick = onCancel)
                .defaultMinSize(minHeight = 36.dp)
                .padding(horizontal = MilkSpace.lg - 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("取消", style = MilkText.label, color = OverlayText)
        }

        // 选框 + 四角手柄 + 尺寸标注
        if (r != null && r.width > 0 && r.height > 0) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = OverlayText,
                    topLeft = Offset(r.l, r.t),
                    size = Size(r.width, r.height),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                // 角手柄(亮面圆点 + 亮蓝描边;视觉 12dp,命中 24dp)
                for (c in listOf(Offset(r.l, r.t), Offset(r.r, r.t), Offset(r.l, r.b), Offset(r.r, r.b))) {
                    drawCircle(OverlayText, radius = 6.dp.toPx(), center = c)
                    drawCircle(AccentBlueDark, radius = 6.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                }
            }
            // 尺寸标注:默认选区上方;靠顶时放选区内、距顶 26dp(避开角柄命中区),横向避让角柄
            var labelSize by remember { mutableStateOf(IntSize.Zero) }
            val label = "${r.width.toInt()} × ${r.height.toInt()}"
            val density = androidx.compose.ui.platform.LocalDensity.current
            val clearPx = with(density) { 12.dp.roundToPx() }
            val insetPx = with(density) { 26.dp.roundToPx() }
            val above = r.t >= labelSize.height + clearPx
            val yPx = if (above) r.t - labelSize.height - clearPx else r.t + insetPx
            val xMinInside = r.l + insetPx
            val xMaxInside = maxOf(r.r - labelSize.width - insetPx, xMinInside)
            val xPx = if (above) r.l
            else ((r.l + r.width / 2) - labelSize.width / 2).coerceIn(xMinInside, xMaxInside)
            Text(
                label,
                style = MilkText.monoMeta,
                color = OverlayText,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .onSizeChanged { labelSize = it }
                    .clip(RoundedCornerShape(MilkRadius.xs))
                    .background(OverlaySurfaceLight)
                    .padding(horizontal = MilkSpace.sm, vertical = 3.dp),
            )
        }

        // CONFIRMED:顶部「重新框选 / 全页」深色胶囊 + 底部意图条(讲解/归纳/翻译/出题/LaTeX + 存卡片 + 发送)
        if (state.phase == SnipPhase.CONFIRMED && r != null) {
            val reduced = LocalMilkMotion.current
            // 附件模式只保留一条聚焦动作条(重新框选 / 存卡片 / 加入附件),不叠加顶栏
            if (!state.attachOnly) {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = MilkSpace.lg)
                        .clip(PillShape)
                        .background(OverlaySurfaceLight)
                        .padding(horizontal = MilkSpace.xxs, vertical = MilkSpace.xxs),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SnipAction("重新框选") { onDrag(r) }
                    SnipAction("全页") {
                        val b = surface()?.pageBoundsView()
                        if (b != null) onFullPage(Rect4(b.left, b.top, b.right, b.bottom))
                    }
                }
            }

            AnimatedVisibility(
                visible = state.phase == SnipPhase.CONFIRMED,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                enter = fadeIn(tween(if (reduced) MilkMotion.reduced else 180, easing = MilkMotion.EaseOut)) +
                    slideInVertically(tween(if (reduced) 0 else 180, easing = MilkMotion.EaseOut)) { it / 2 },
                exit = fadeOut(tween(if (reduced) MilkMotion.reduced else 120, easing = MilkMotion.EaseOut)) +
                    slideOutVertically(tween(if (reduced) 0 else 120, easing = MilkMotion.EaseOut)) { it / 2 },
            ) {
                // 意图条:底部深色胶囊(DESIGN-V2 snip-bar);当前意图 = 蓝 22% 淡底 + 浅蓝字
                if (state.attachOnly) {
                    Row(
                        Modifier
                            .clip(PillShape)
                            .background(OverlaySurfaceLight)
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SnipAction("重新框选") { onDrag(r) }
                        Spacer(Modifier.width(MilkSpace.xxs))
                        Box(
                            Modifier
                                .clip(PillShape)
                                .clickable { onSaveCard { x, y -> surface()?.viewToPage(x, y) ?: (0f to 0f) } }
                                .defaultMinSize(minHeight = 36.dp)
                                .padding(horizontal = MilkSpace.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("存卡片", style = MilkText.label, color = OverlayMuted)
                        }
                        Spacer(Modifier.width(MilkSpace.xxs))
                        Box(
                            Modifier
                                .clip(PillShape)
                                .background(AccentBlue)
                                .clickable {
                                    (onAttach ?: onSend) { x, y -> surface()?.viewToPage(x, y) ?: (0f to 0f) }
                                }
                                .defaultMinSize(minHeight = 34.dp)
                                .padding(horizontal = MilkSpace.lg - 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "加入附件 ↵",
                                style = MilkText.label.copy(fontWeight = FontWeight.Medium),
                                color = Color.White,
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier
                            .clip(PillShape)
                            .background(OverlaySurfaceLight)
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Intents.all.forEach { spec ->
                            val active = state.intent == spec.id
                            Box(
                                Modifier
                                    .clip(PillShape)
                                    .background(if (active) AccentBlueDark.copy(alpha = 0.22f) else Color.Transparent)
                                    .clickable { onIntent(spec.id) }
                                    .defaultMinSize(minHeight = 36.dp)
                                    .padding(horizontal = MilkSpace.md),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    spec.label,
                                    style = if (active) MilkText.label.copy(fontWeight = FontWeight.SemiBold) else MilkText.label,
                                    color = if (active) AccentBlueDark else OverlayMuted,
                                )
                            }
                        }
                        Spacer(Modifier.width(MilkSpace.xxs))
                        Box(
                            Modifier
                                .clip(PillShape)
                                .clickable { onSaveCard { x, y -> surface()?.viewToPage(x, y) ?: (0f to 0f) } }
                                .defaultMinSize(minHeight = 36.dp)
                                .padding(horizontal = MilkSpace.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("存卡片", style = MilkText.label, color = OverlayMuted)
                        }
                        if (onAttach != null) {
                            Spacer(Modifier.width(MilkSpace.xxs))
                            Box(
                                Modifier
                                    .clip(PillShape)
                                    .clickable {
                                        onAttach { x, y -> surface()?.viewToPage(x, y) ?: (0f to 0f) }
                                    }
                                    .defaultMinSize(minHeight = 36.dp)
                                    .padding(horizontal = MilkSpace.md),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("加入附件", style = MilkText.label, color = OverlayMuted)
                            }
                        }
                        Spacer(Modifier.width(MilkSpace.xxs))
                        Box(
                            Modifier
                                .clip(PillShape)
                                .background(AccentBlue)
                                .clickable { onSend { x, y -> surface()?.viewToPage(x, y) ?: (0f to 0f) } }
                                .defaultMinSize(minHeight = 34.dp)
                                .padding(horizontal = MilkSpace.lg - 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "发送 ↵",
                                style = MilkText.label.copy(fontWeight = FontWeight.Medium),
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnipAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(PillShape)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 36.dp)
            .padding(horizontal = MilkSpace.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MilkText.label, color = OverlayMuted)
    }
}

private fun nearestCorner(r: Rect4, x: Float, y: Float, tolPx: Float): Int? {
    var best: Int? = null
    var bestD = Float.MAX_VALUE
    listOf(0 to (r.l to r.t), 1 to (r.r to r.t), 2 to (r.l to r.b), 3 to (r.r to r.b)).forEach { (i, c) ->
        val d = abs(x - c.first) + abs(y - c.second)
        if (d < tolPx && d < bestD) {
            bestD = d
            best = i
        }
    }
    return best
}

private fun withCorner(r: Rect4, corner: Int, x: Float, y: Float): Rect4 {
    val n = when (corner) {
        0 -> Rect4(x, y, r.r, r.b)
        1 -> Rect4(r.l, y, x, r.b)
        2 -> Rect4(x, r.t, r.r, y)
        else -> Rect4(r.l, r.t, x, y)
    }.normalized()
    // 最小尺寸钳制(反向拖拽不塌缩)
    return Rect4(n.l, n.t, maxOf(n.r, n.l + 24f), maxOf(n.b, n.t + 24f))
}
