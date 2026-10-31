package com.milknote.device

import com.milknote.device.pen.PressureHistogram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 30s 滚动压力直方图(PEN-ADAPTATION §3.5;时钟注入) */
class PressureHistogramTest {

    private class Clock(var now: Long = 0L)

    @Test fun `min max p50 统计`() {
        val clock = Clock()
        val h = PressureHistogram(clock = { clock.now }, emitIntervalMs = 0)
        listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f).forEach { h.sample(it) }
        val s = h.stats.value!!
        assertEquals(0.1f, s.min, 1e-6f)
        assertEquals(0.9f, s.max, 1e-6f)
        assertEquals(0.5f, s.p50, 1e-6f)
        assertEquals(5, s.count)
    }

    @Test fun `窗口裁剪 - 30s 前的样本过期`() {
        val clock = Clock()
        val h = PressureHistogram(clock = { clock.now }, emitIntervalMs = 0)
        h.sample(0.9f)                       // t=0
        clock.now = 31_000
        h.sample(0.1f)                       // t=31s,t=0 的样本出窗
        val s = h.stats.value!!
        assertEquals(1, s.count)
        assertEquals(0.1f, s.min, 1e-6f)
        assertEquals(0.1f, s.max, 1e-6f)
    }

    @Test fun `越界值被夹取`() {
        val clock = Clock()
        val h = PressureHistogram(clock = { clock.now }, emitIntervalMs = 0)
        h.sample(1.7f)   // 重压越界 → 1.0
        h.sample(-0.2f)  // 噪声 → 0
        val s = h.stats.value!!
        assertEquals(1.0f, s.max, 1e-6f)
        assertEquals(0.0f, s.min, 1e-6f)
    }

    @Test fun `reset 清空`() {
        val clock = Clock()
        val h = PressureHistogram(clock = { clock.now }, emitIntervalMs = 0)
        h.sample(0.5f)
        h.reset()
        assertNull(h.stats.value)
    }
}
