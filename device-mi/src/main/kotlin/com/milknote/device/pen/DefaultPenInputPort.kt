package com.milknote.device.pen

import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 笔输入端口(DESIGN-V0 §2.2:换笔/换机型不动核心) */
interface PenInputPort {
    val capabilities: StateFlow<PenCapabilities?>
    val actions: SharedFlow<PenActionEvent>
    val eventLog: PenEventLog
    val router: PenActionRouter

    /** 30s 滚动压力直方图(诊断面板/§7-P2 数据源) */
    val histogram: PressureHistogram

    /**
     * 笔键消费开关(书写页激活):消费后阻止系统默认行为(长按截图/画板)。
     * 关闭时只记流水不吞,避免书架/设置页拦截外接键盘翻页键。
     */
    var keyRoutingEnabled: Boolean

    /**
     * 「按住+书写」修饰模式的当前触发器(PEN-ADAPTATION §4.3):
     * 主/副键按住期间落笔,InkSurfaceView 落笔瞬间同步查询(决定套索/临时橡皮)。
     */
    val holdWriteTrigger: PenTrigger?

    /** 通路①:触摸事件首览(InkSurfaceView.dispatchTouchEvent → 此处)。true = 拦截 */
    fun onDispatchTouchEvent(event: MotionEvent): Boolean

    /** 通路①b:笔按键 generic motion 首览(Activity.dispatchGenericMotionEvent → 此处)。
     *  官方通路:悬停/接触时的 ACTION_BUTTON_PRESS/RELEASE 走 generic motion 投递
     *  (developer.android.com stylus-buttons),不经 dispatchTouchEvent。true = 拦截 */
    fun onDispatchGenericMotionEvent(event: MotionEvent): Boolean

    /** 通路②:悬停态实体键(Activity.dispatchKeyEvent → 此处)。true = 拦截 */
    fun onDispatchKeyEvent(event: KeyEvent): Boolean

    /** 通路③:小米 SDK 触控区(焦点笔 Pro,前瞻;MiTouchFilmBridge 内反射隔离) */
    fun attachTouchFilm()
}

data class PenActionEvent(val trigger: PenTrigger, val action: PenAction)

/**
 * 一代焦点笔实现:
 * - 防误掌:TYPE_STYLUS 书写中/刚刚书写后,手掌与手指事件全部吞掉(DESIGN-V0 §3.2)
 * - 实体键:接触屏幕走 buttonState/actionButton(API 23+ 通路①),
 *   悬停走 KEYCODE_STYLUS_BUTTON_*(API 34+ 通路②)
 * - 同一按键双路去重:50ms 时间窗(PEN-ADAPTATION §4.1)
 */
class DefaultPenInputPort(
    override val router: PenActionRouter,
) : PenInputPort {

    override val capabilities = MutableStateFlow<PenCapabilities?>(null)
    override val eventLog = PenEventLog()
    override val actions = MutableSharedFlow<PenActionEvent>(extraBufferCapacity = 16)
    override val histogram = PressureHistogram()

    @Volatile override var keyRoutingEnabled: Boolean = false

    private val gesture = PenGestureDetector(onTrigger = { trigger -> dispatch(trigger) })

    /** 副键优先于主键(两者同按的罕见场景取副键=系统语义更明确的那个) */
    override val holdWriteTrigger: PenTrigger?
        get() = when {
            gesture.isHeld(PenGestureDetector.Btn.SECONDARY) -> PenTrigger.SECONDARY_HOLD_WRITE
            gesture.isHeld(PenGestureDetector.Btn.PRIMARY) -> PenTrigger.PRIMARY_HOLD_WRITE
            else -> null
        }

    // 防误掌状态
    private var stylusWriting = false
    private var stylusUpAt = 0L
    private var lastLoggedButtonState = 0

    // 双路按键去重:(btn, 边沿) → 上次见到的时间
    private val keyEdgeSeen = HashMap<String, Long>()

    override fun onDispatchTouchEvent(event: MotionEvent): Boolean {
        val tool = event.getToolType(0)
        val action = MotionEvent.actionToString(event.actionMasked)
            .removePrefix("ACTION_")

        // ---- 能力探测(首个笔事件)----
        if (capabilities.value == null &&
            (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER)
        ) {
            PenProbe.probe(event)?.let {
                capabilities.value = it
                eventLog.log("PROBE  ${it.deviceName}  pressure=${it.pressure} tilt=${it.tilt} hover=${it.hover} buttons=${it.stylusButtonCount}")
            }
        }

        when (tool) {
            MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> {
                stylusWriting = event.actionMasked != MotionEvent.ACTION_UP &&
                    event.actionMasked != MotionEvent.ACTION_CANCEL &&
                    event.actionMasked != MotionEvent.ACTION_HOVER_EXIT
                if (!stylusWriting) stylusUpAt = event.downTime
                if (event.actionMasked == MotionEvent.ACTION_DOWN) gesture.onWritingStart()

                // ---- 压力直方图采样(接触事件;悬停恒 0 不计) ----
                if (event.actionMasked != MotionEvent.ACTION_HOVER_MOVE &&
                    event.actionMasked != MotionEvent.ACTION_HOVER_ENTER &&
                    event.actionMasked != MotionEvent.ACTION_HOVER_EXIT
                ) {
                    histogram.sample(event.pressure)
                }

                // ---- 按键位(通路①) ----
                val btnBits = when (event.actionMasked) {
                    MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> event.actionButton
                    else -> event.buttonState
                }
                logButtonBits(btnBits, action)
                handleButtonBits(btnBits, fromContact = true)

                // ---- 事件流水(采样,MOVE 100ms 节流) ----
                if (event.actionMasked != MotionEvent.ACTION_MOVE || event.eventTime - lastMoveLog > 100) {
                    if (event.actionMasked == MotionEvent.ACTION_MOVE) lastMoveLog = event.eventTime
                    eventLog.log(
                        "$action  tool=${if (tool == MotionEvent.TOOL_TYPE_STYLUS) "pen" else "eraser"}  " +
                            "p=${"%.3f".format(event.pressure)}  btn=0x${Integer.toHexString(event.buttonState)}"
                    )
                }
                return false // 书写事件交给视图处理
            }
            MotionEvent.TOOL_TYPE_FINGER -> {
                // ---- 防误掌:笔在写 / 笔刚抬起 → 手指全部拒绝 ----
                if (stylusWriting || event.eventTime - stylusUpAt < PALM_GRACE_MS) {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        eventLog.log("PALM-REJECT  finger rejected (stylus active)")
                    }
                    return true
                }
                eventLog.log("$action  tool=finger")
                return false // 纯手指导航 → 视图处理
            }
            else -> {
                eventLog.log("$action  tool=$tool")
                return false
            }
        }
    }

    override fun onDispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        // 只关心笔键位事件(其余 hover/touch 由视图处理)
        if (action != MotionEvent.ACTION_BUTTON_PRESS && action != MotionEvent.ACTION_BUTTON_RELEASE) {
            return false
        }
        val tool = event.getToolType(0)
        if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return false

        val pressed = action == MotionEvent.ACTION_BUTTON_PRESS
        val bits = event.actionButton
        eventLog.log("GM-BTN  ${if (pressed) "press" else "release"}  actionButton=0x${Integer.toHexString(bits)}")
        // 边沿 → 手势判定(与触摸流 buttonState 双路,50ms 去重兜底)
        val primary = bits and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
        val secondary = bits and MotionEvent.BUTTON_STYLUS_SECONDARY != 0
        if (bits != 0 && bits != MotionEvent.BUTTON_STYLUS_PRIMARY && bits != MotionEvent.BUTTON_STYLUS_SECONDARY) {
            // 非标准键位(厂商自定义):进流水供 P1 适配,不吞
            return false
        }
        if (primary && !dedupe("PRIMARY", pressed)) gesture.onKey(PenGestureDetector.Btn.PRIMARY, pressed)
        if (secondary && !dedupe("SECONDARY", pressed)) gesture.onKey(PenGestureDetector.Btn.SECONDARY, pressed)
        return false // 不吞:悬停流继续给视图画光标
    }

    override fun onDispatchKeyEvent(event: KeyEvent): Boolean {
        // ---- 通路②a:小米焦点笔(一代)真机实测 —— 侧键以 PAGE_UP(92)/PAGE_DOWN(93) 投递
        //      (Pad 8 Pro + HyperOS,2026-11-01 P1 诊断流水;非标准 KEYCODE_STYLUS_BUTTON_*)。
        //      消费(return true)阻止系统默认行为(长按截图/画板)——仅在书写页启用。 ----
        val miBtn = when (event.keyCode) {
            KeyEvent.KEYCODE_PAGE_UP -> PenGestureDetector.Btn.PRIMARY      // 上侧键(主键位)
            KeyEvent.KEYCODE_PAGE_DOWN -> PenGestureDetector.Btn.SECONDARY  // 下侧键(副键位)
            else -> null
        }
        if (miBtn != null) {
            val pressed = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
            if (keyRoutingEnabled) {
                eventLog.log("KEY  ${KeyEvent.keyCodeToString(event.keyCode)} ${if (pressed) "down" else "up"} → ${miBtn.name}")
                if (!dedupe(miBtn.name, pressed)) {
                    gesture.onKey(miBtn, pressed)
                }
                return true // 消费:系统不再执行默认行为
            }
            eventLog.log("KEY  ${KeyEvent.keyCodeToString(event.keyCode)} ${if (pressed) "down" else "up"}(未路由:非书写页)")
            return false
        }

        // ---- 通路②b:标准 KEYCODE_STYLUS_BUTTON_*(API 34+;本机未出现,保留) ----
        if (Build.VERSION.SDK_INT >= 34) {
            val btn = when (event.keyCode) {
                KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY -> PenGestureDetector.Btn.PRIMARY
                KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY -> PenGestureDetector.Btn.SECONDARY
                KeyEvent.KEYCODE_STYLUS_BUTTON_TERTIARY -> null // 焦点键候选(P1 验证)
                else -> null
            }
            if (btn != null) {
                val pressed = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
                eventLog.log("KEY  ${KeyEvent.keyCodeToString(event.keyCode)} ${if (pressed) "down" else "up"}")
                if (!dedupe(btn.name, pressed)) {
                    gesture.onKey(btn, pressed)
                }
                return true
            }
            // 非标准键(厂商自定义):进流水、不吞
            eventLog.log("KEY  ${KeyEvent.keyCodeToString(event.keyCode)}(${event.keyCode}) action=${if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"}")
        }
        // ---- 通路③:小米 SDK 转发(焦点笔 Pro,前瞻) ----
        if (MiTouchFilmBridge.onDispatchKeyEvent(event, ::dispatchTouchFilm)) return true
        return false
    }

    override fun attachTouchFilm() {
        MiTouchFilmBridge.attach(::dispatchTouchFilm)
    }

    private fun dispatchTouchFilm(trigger: PenTrigger) = dispatch(trigger)

    private fun dispatch(trigger: PenTrigger) {
        val action = router.actionOf(trigger)
        eventLog.log("TRIGGER  ${trigger.label} → ${action.label}")
        actions.tryEmit(PenActionEvent(trigger, action))
    }

    // ---- 通路① 按键位 → 手势 ----

    private fun handleButtonBits(bits: Int, fromContact: Boolean) {
        val primary = bits and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
        val secondary = bits and MotionEvent.BUTTON_STYLUS_SECONDARY != 0
        // 仅在边沿变化时上报(当前帧的按下集合 vs 上次)
        if (primary != lastContactP && !dedupe("PRIMARY", primary)) {
            gesture.onKey(PenGestureDetector.Btn.PRIMARY, primary)
        }
        if (secondary != lastContactS && !dedupe("SECONDARY", secondary)) {
            gesture.onKey(PenGestureDetector.Btn.SECONDARY, secondary)
        }
        lastContactP = primary; lastContactS = secondary
    }

    private var lastContactP = false
    private var lastContactS = false

    /** 双路去重:同一(键,边沿)50ms 内只算一次(PEN-ADAPTATION §4.1) */
    private fun dedupe(btn: String, pressed: Boolean): Boolean {
        val key = "$btn/${if (pressed) "down" else "up"}"
        val now = android.os.SystemClock.uptimeMillis()
        val last = keyEdgeSeen[key] ?: 0L
        return if (now - last < 50L) {
            true // 重复,丢弃
        } else {
            keyEdgeSeen[key] = now
            false
        }
    }

    private var lastMoveLog = 0L

    private fun logButtonBits(bits: Int, action: String) {
        if (bits != lastLoggedButtonState) {
            eventLog.log("BTN  $action  actionButton=0x${Integer.toHexString(bits)}")
            lastLoggedButtonState = bits
        }
    }

    companion object {
        /** 笔抬起后仍拒绝手指的宽限期(ms) */
        const val PALM_GRACE_MS = 300L
    }
}
