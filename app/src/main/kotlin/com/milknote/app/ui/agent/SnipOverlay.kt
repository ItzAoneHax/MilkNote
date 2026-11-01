package com.milknote.app.ui.agent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.agent.Intents
import com.milknote.agent.Rect4
import com.milknote.app.ui.theme.Mono
import com.milknote.app.ui.common.accentBrush
import com.milknote.app.ui.writer.SnipPhase
import com.milknote.app.ui.writer.SnipUiState
import com.milknote.ink.render.InkSurfaceView
import kotlin.math.abs

/**
 * ④ 框选遮罩(DESIGN-V0 §4.2 / AGENT-BACKEND §4.2):
 * 冻结画布 + 压暗 → 拖出选区(角手柄 + 尺寸标注 mono)→ 松开弹意图条(默认讲解 ↵)。
 * 覆盖在画布之上拦截输入(书写自然暂停);坐标 = 视口坐标(与 InkSurfaceView 同 bounds)。
 */
@Composable
fun SnipOverlay(
    state: SnipUiState,
    surface: () -> InkSurfaceView?,
    onDrag: (Rect4) -> Unit,
    onRelease: () -> Unit,
    onRectChanged: (Rect4) -> Unit,
    onFullPage: (Rect4) -> Unit,
    onCancel: () -> Unit,
    onIntent: (Intents.Id) -> Unit,
    onSend: ((Float, Float) -> Pair<Float, Float>) -> Unit,
) {
    val dim = Color(0x8C0E0F14)
    val current by rememberUpdatedState(state)

    // CONFIRMED 态手势:拖角柄缩放,拖体内移动,点外部取消
    val confirmModifier = if (state.phase == SnipPhase.CONFIRMED && state.rect != null) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val st = current
                val r = st.rect ?: return@awaitEachGesture
                val down = awaitFirstDown()
                val p = down.position
                val corner = nearestCorner(r, p.x, p.y)
                val inside = p.x >= r.l && p.x <= r.r && p.y >= r.t && p.y <= r.b
                when {
                    corner != null -> {   // 角柄缩放
                        var cur = p
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            cur = ch.position
                            onRectChanged(withCorner(r, corner, cur.x, cur.y))
                        }
                    }
                    inside -> {           // 整体移动(增量累积)
                        var dx = 0f
                        var dy = 0f
                        var cur = p
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            dx += ch.position.x - cur.x
                            dy += ch.position.y - cur.y
                            cur = ch.position
                            onRectChanged(Rect4(r.l + dx, r.t + dy, r.r + dx, r.b + dy))
                        }
                    }
                    else -> { awaitPointerEvent(); onCancel() }   // 遮罩外点按取消
                }
            }
        }
    } else {
        Modifier.pointerInput(Unit) {   // FROZEN 态:拖出选区
            awaitEachGesture {
                val down = awaitFirstDown()
                val start = down.position
                onDrag(Rect4(start.x, start.y, start.x, start.y))
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull() ?: break
                    if (!ch.pressed) break
                    onDrag(Rect4.fromDrag(start.x, start.y, ch.position.x, ch.position.y))
                }
                onRelease()
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

        // 选框 + 四角手柄 + 尺寸标注
        if (r != null && r.width > 0 && r.height > 0) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(r.l, r.t),
                    size = Size(r.width, r.height),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 2.dp.toPx()),
                )
                // 角手柄(白底蓝边圆点)
                for (c in listOf(Offset(r.l, r.t), Offset(r.r, r.t), Offset(r.l, r.b), Offset(r.r, r.b))) {
                    drawCircle(Color.White, radius = 7.dp.toPx(), center = c)
                    drawCircle(Color(0xFF3B5BEC), radius = 7.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                }
            }
            // 尺寸标注(选区左上,mono)
            Text(
                "${r.width.toInt()} × ${r.height.toInt()} px · 选区已冻结",
                fontFamily = Mono, fontSize = 11.sp, color = Color(0xFFFAF9F5),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = with(androidx.compose.ui.platform.LocalDensity.current) { r.l.toDp() }.coerceAtLeast(8.dp),
                        top = with(androidx.compose.ui.platform.LocalDensity.current) {
                            (r.t - 30f).toDp()
                        }.coerceAtLeast(46.dp),
                    )
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xE623221E))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // CONFIRMED:顶部操作行 + 底部意图条
        if (state.phase == SnipPhase.CONFIRMED && r != null) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .clip(CircleShape)
                    .background(Color(0xE623221E))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SnipAction("重新框选") { onDrag(r) }
                SnipAction("全页") {
                    val b = surface()?.pageBoundsView()
                    if (b != null) onFullPage(Rect4(b.left, b.top, b.right, b.bottom))
                }
                SnipAction("取消", muted = true) { onCancel() }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 状态条(§4.2:渲染源说明)
                Text(
                    "选区将连同手写层一起送入 Agent · 书写已暂停",
                    fontSize = 10.sp,
                    color = Color(0xCCFAF9F5),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x6623221E))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.height(8.dp))
                // 意图条:底部深色胶囊(DESIGN-V0 §5.5)
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xF023221E))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Intents.all.forEach { spec ->
                        val active = state.intent == spec.id
                        Text(
                            spec.label,
                            fontSize = 12.sp,
                            color = if (active) Color(0xFF7D92FF) else Color(0xFFB7B3A8),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (active) Color(0x1F5C77F2) else Color.Transparent)
                                .clickable { onIntent(spec.id) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(accentBrush())
                            .clickable { onSend { x, y -> surface()?.viewToPage(x, y) ?: (0f to 0f) } }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text("发送 ↵", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SnipAction(label: String, muted: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        color = if (muted) Color(0xFF9B978D) else Color(0xFFE8E6E0),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private fun nearestCorner(r: Rect4, x: Float, y: Float): Int? {
    val tol = 30f
    var best: Int? = null
    var bestD = Float.MAX_VALUE
    listOf(0 to (r.l to r.t), 1 to (r.r to r.t), 2 to (r.l to r.b), 3 to (r.r to r.b)).forEach { (i, c) ->
        val d = abs(x - c.first) + abs(y - c.second)
        if (d < tol && d < bestD) {
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
