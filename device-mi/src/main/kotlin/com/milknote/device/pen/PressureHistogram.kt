package com.milknote.device.pen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 30 秒滚动压力直方图(PEN-ADAPTATION §3.5 / §7-P2 真机验证数据源):
 * 书写事件逐点采样,输出 min/max/p50/样本数,用于判断个体笔的一致性与值域。
 * 时钟可注入(纯 JVM 单测)。
 */
class PressureHistogram(
    private val windowMs: Long = 30_000L,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val emitIntervalMs: Long = 200L,
) {

    data class Stats(val min: Float, val max: Float, val p50: Float, val count: Int, val windowMs: Long)

    private val buffer = ArrayDeque<Pair<Long, Float>>(2048) // (t, p)
    private val _stats = MutableStateFlow<Stats?>(null)
    val stats: StateFlow<Stats?> = _stats

    private var lastEmit = 0L

    /** 采集一个压力样本(建议仅对笔接触事件调用;悬停恒 0,不计入) */
    fun sample(pressure: Float) {
        val now = clock()
        synchronized(buffer) {
            buffer.addLast(now to pressure.coerceIn(0f, 1f))
            pruneLocked(now)
            if (now - lastEmit >= emitIntervalMs || buffer.size == 1) {
                lastEmit = now
                _stats.value = computeLocked()
            }
        }
    }

    fun reset() {
        synchronized(buffer) {
            buffer.clear()
            _stats.value = null
            lastEmit = 0L
        }
    }

    private fun pruneLocked(now: Long) {
        while (buffer.isNotEmpty() && now - buffer.first().first > windowMs) buffer.removeFirst()
    }

    private fun computeLocked(): Stats? {
        if (buffer.isEmpty()) return null
        val values = FloatArray(buffer.size)
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var i = 0
        for ((_, p) in buffer) {
            values[i++] = p
            if (p < min) min = p
            if (p > max) max = p
        }
        values.sort()
        val p50 = values[values.size / 2]
        return Stats(min, max, p50, values.size, windowMs)
    }
}
