package com.milknote.device

import com.milknote.device.pen.PenGestureDetector
import com.milknote.device.pen.PenTrigger
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.PriorityQueue

/**
 * 笔键手势判定规范(PEN-ADAPTATION §4.3):
 * 双击 <350ms / 长按 >400ms / 按住+书写修饰模式。
 */
class PenGestureDetectorTest {

    /** 假调度器:手动推进时间 */
    private class FakeClock {
        var now = 0L
        private val queue = PriorityQueue<Pair<Long, Int>>(compareBy { it.first })
        private val runnables = HashMap<Int, Runnable>()
        private var seq = 0

        val post: (Runnable, Long) -> Unit = { r, delay ->
            val id = seq++
            runnables[id] = r
            queue.add(now + delay to id)
        }
        val cancel: (Runnable) -> Unit = { r ->
            runnables.entries.removeIf { it.value === r }
        }

        /** 执行所有已到期的定时任务 */
        fun advance(ms: Long) {
            now += ms
            while (queue.isNotEmpty() && queue.peek().first <= now) {
                val (_, id) = queue.poll()
                runnables.remove(id)?.let { it.run() }
            }
        }

        /** 只推进时钟不执行任务(模拟「没等到」) */
        fun pass(ms: Long) { now += ms }
    }

    private class Harness {
        val clock = FakeClock()
        val fired = mutableListOf<PenTrigger>()
        val det = PenGestureDetector(
            onTrigger = { fired.add(it) },
            clock = { clock.now },
            scheduler = clock.post,
            canceler = clock.cancel,
        )
    }

    @Test fun `单击 - 350ms 后确认`() {
        val h = Harness()
        h.det.onKey(PenGestureDetector.Btn.PRIMARY, true)
        h.det.onKey(PenGestureDetector.Btn.PRIMARY, false)   // t=0 抬起
        assertEquals(0, h.fired.size)                          // 延迟确认
        h.clock.advance(351)
        assertEquals(listOf(PenTrigger.PRIMARY_TAP), h.fired)
    }

    @Test fun `双击 - 第二击立即触发不发单击`() {
        val h = Harness()
        h.det.onKey(PenGestureDetector.Btn.PRIMARY, true)
        h.det.onKey(PenGestureDetector.Btn.PRIMARY, false)
        h.clock.advance(200)
        h.det.onKey(PenGestureDetector.Btn.PRIMARY, true)
        h.det.onKey(PenGestureDetector.Btn.PRIMARY, false)   // 距上击 200ms < 350
        assertEquals(listOf(PenTrigger.PRIMARY_DOUBLE), h.fired)
        h.clock.advance(500)                                  // 无迟发单击
        assertEquals(listOf(PenTrigger.PRIMARY_DOUBLE), h.fired)
    }

    @Test fun `间隔超过350ms 是两次单击`() {
        val h = Harness()
        h.det.onKey(PenGestureDetector.Btn.PRIMARY, true)
        h.det.onKey(PenGestureDetector.Btn.PRIMARY, false)
        h.clock.advance(400)                                  // > 350,单击已确认
        h.det.onKey(PenGestureDetector.Btn.PRIMARY, true)
        h.det.onKey(PenGestureDetector.Btn.PRIMARY, false)
        h.clock.advance(400)
        assertEquals(listOf(PenTrigger.PRIMARY_TAP, PenTrigger.PRIMARY_TAP), h.fired)
    }

    @Test fun `长按 400ms 触发框选`() {
        val h = Harness()
        h.det.onKey(PenGestureDetector.Btn.PRIMARY, true)
        h.clock.advance(401)
        assertEquals(listOf(PenTrigger.PRIMARY_LONG), h.fired)
        h.det.onKey(PenGestureDetector.Btn.PRIMARY, false)   // 抬起不追加单击
        h.clock.advance(500)
        assertEquals(listOf(PenTrigger.PRIMARY_LONG), h.fired)
    }

    @Test fun `按住 400ms 内书写进入修饰模式`() {
        val h = Harness()
        h.det.onKey(PenGestureDetector.Btn.PRIMARY, true)
        h.clock.advance(100)
        h.det.onWritingStart()
        assertEquals(listOf(PenTrigger.PRIMARY_HOLD_WRITE), h.fired)
        h.clock.advance(1000)                                 // 长按定时器被取消
        assertEquals(listOf(PenTrigger.PRIMARY_HOLD_WRITE), h.fired)
    }

    @Test fun `副键长按无系统定义 只出单击`() {
        val h = Harness()
        h.det.onKey(PenGestureDetector.Btn.SECONDARY, true)
        h.clock.advance(600)                                  // 副键无长按(系统截图占用)
        h.det.onKey(PenGestureDetector.Btn.SECONDARY, false)
        h.clock.advance(400)
        assertEquals(listOf(PenTrigger.SECONDARY_TAP), h.fired)
    }
}
