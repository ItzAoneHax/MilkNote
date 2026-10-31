package com.milknote.device.pen

import android.os.Handler
import android.os.Looper

/**
 * 笔键手势判定(PEN-ADAPTATION §4.3):
 * - 双击:间隔 < 350ms,第二击立即触发,单击延迟 350ms 确认(避免误单发)
 * - 长按:> 400ms 未抬且未书写 → 触发(核心路径:框选截图)
 * - 按住+书写:按下后 400ms 内开始书写 → 修饰模式(套索/临时橡皮)
 *
 * 时钟/调度可注入(纯 JVM 单测);默认走主线程 Handler。
 */
class PenGestureDetector(
    private val onTrigger: (PenTrigger) -> Unit,
    clock: (() -> Long)? = null,
    scheduler: ((Runnable, Long) -> Unit)? = null,
    canceler: ((Runnable) -> Unit)? = null,
) {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val now: () -> Long = clock ?: { android.os.SystemClock.uptimeMillis() }
    private val postDelayed: (Runnable, Long) -> Unit = scheduler ?: { r, d -> mainHandler.postDelayed(r, d) }
    private val cancel: (Runnable) -> Unit = canceler ?: { r -> mainHandler.removeCallbacks(r) }

    enum class Btn { PRIMARY, SECONDARY }

    // 按住状态
    private val held = HashMap<Btn, Long>()          // btn -> downTime
    private var holdModeConsumed = false              // 已进入「按住+书写」
    private var longFired = false

    /** 键当前是否被按住(落笔瞬间同步查询「按住+书写」修饰模式用) */
    fun isHeld(btn: Btn): Boolean = held.containsKey(btn)

    // 双击窗口
    private val lastTapTime = HashMap<Btn, Long>()
    private val pendingTap = HashMap<Btn, Runnable>()

    /** 事件注入:书写开始(每笔 ACTION_DOWN 调用) */
    fun onWritingStart() {
        for ((btn, downAt) in held) {
            if (!holdModeConsumed && !longFired && now() - downAt < LONG_PRESS_MS) {
                holdModeConsumed = true
                cancelLongTimer()
                onTrigger(when (btn) {
                    Btn.PRIMARY -> PenTrigger.PRIMARY_HOLD_WRITE
                    Btn.SECONDARY -> PenTrigger.SECONDARY_HOLD_WRITE
                })
            }
        }
    }

    /** 按键变化 */
    fun onKey(btn: Btn, pressed: Boolean) {
        if (pressed) {
            held[btn] = now()
            holdModeConsumed = false
            longFired = false
            pendingTap.remove(btn)?.let(cancel)
            if (btn == Btn.PRIMARY) startLongTimer(btn)
        } else {
            held.remove(btn)
            cancelLongTimer()
            if (!longFired && !holdModeConsumed) {
                val t = now()
                val last = lastTapTime[btn]
                if (last != null && t - last < DOUBLE_TAP_MS) {
                    // 第二击:立即触发双击(副键双击未定义,退化为单击),撤销挂起的单击
                    pendingTap.remove(btn)?.let(cancel)
                    lastTapTime.remove(btn)
                    onTrigger(when (btn) {
                        Btn.PRIMARY -> PenTrigger.PRIMARY_DOUBLE
                        Btn.SECONDARY -> PenTrigger.SECONDARY_TAP
                    })
                } else {
                    // 首击:延迟确认单击
                    lastTapTime[btn] = t
                    val r = Runnable {
                        pendingTap.remove(btn)
                        lastTapTime.remove(btn)
                        onTrigger(when (btn) {
                            Btn.PRIMARY -> PenTrigger.PRIMARY_TAP
                            Btn.SECONDARY -> PenTrigger.SECONDARY_TAP
                        })
                    }
                    pendingTap[btn] = r
                    postDelayed(r, DOUBLE_TAP_MS)
                }
            }
            holdModeConsumed = false
            longFired = false
        }
    }

    private fun startLongTimer(btn: Btn) {
        cancelLongTimer()
        longRunnable = Runnable {
            longFired = true
            if (btn == Btn.PRIMARY) onTrigger(PenTrigger.PRIMARY_LONG)
        }
        postDelayed(longRunnable!!, LONG_PRESS_MS)
    }

    private var longRunnable: Runnable? = null
    private fun cancelLongTimer() {
        longRunnable?.let(cancel)
        longRunnable = null
    }

    fun reset() {
        cancelLongTimer()
        pendingTap.values.forEach(cancel)
        pendingTap.clear()
        held.clear()
        lastTapTime.clear()
        longFired = false
        holdModeConsumed = false
    }

    companion object {
        const val DOUBLE_TAP_MS = 350L   // DESIGN-V0 §3.2:双击间隔 <350ms
        const val LONG_PRESS_MS = 400L   // 长按 >400ms
    }
}
