package com.milknote.ink.render

import kotlin.math.abs

/** 缩放收指后的短暂平移冷却；使用 uptime 时间戳，不调度延迟任务。 */
internal class ZoomEndDebounce(private val durationMs: Long = 120L) {
    private var suppressPanUntil = Long.MIN_VALUE

    fun onScaleEnd(nowMs: Long) {
        suppressPanUntil = nowMs + durationMs
    }

    fun shouldSuppressPan(nowMs: Long): Boolean = nowMs < suppressPanUntil

    fun reset() {
        suppressPanUntil = Long.MIN_VALUE
    }
}

internal enum class PageEdge { LEFT, RIGHT }
internal enum class PageTurn { PREVIOUS, NEXT }

internal fun pageEdgeAt(x: Float, width: Float, edgeSize: Float): PageEdge? = when {
    width <= 0f -> null
    x <= edgeSize -> PageEdge.LEFT
    x >= width - edgeSize -> PageEdge.RIGHT
    else -> null
}

/** 只接受从左右边缘向纸内、横向占主导且达到指定位移的手势。 */
internal fun edgeSwipeTurn(
    edge: PageEdge,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    minimumDistance: Float,
): PageTurn? {
    val dx = endX - startX
    val dy = endY - startY
    if (abs(dx) < minimumDistance || abs(dx) <= abs(dy)) return null
    return when (edge) {
        PageEdge.LEFT -> if (dx > 0f) PageTurn.PREVIOUS else null
        PageEdge.RIGHT -> if (dx < 0f) PageTurn.NEXT else null
    }
}
