package com.milknote.device.pen

import android.view.InputDevice
import android.view.MotionEvent

/** 笔能力(运行时探测;PEN-ADAPTATION §3.2),按 deviceId 缓存 */
data class PenCapabilities(
    val deviceId: Int,
    val deviceName: String,
    val pressure: Boolean,
    val tilt: Boolean,
    val hover: Boolean,
    val stylusButtonCount: Int,
    val touchFilm: Boolean = false, // 焦点笔 Pro 触控区(通路③,前瞻)
)

object PenProbe {

    fun probe(event: MotionEvent): PenCapabilities? {
        val dev = InputDevice.getDevice(event.deviceId) ?: return null
        val stylus = InputDevice.SOURCE_STYLUS
        val pRange = dev.getMotionRange(MotionEvent.AXIS_PRESSURE, stylus)
        val tRange = dev.getMotionRange(MotionEvent.AXIS_TILT, stylus)
        val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
            event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
        if (!isStylus && !dev.supportsSource(stylus)) return null
        return PenCapabilities(
            deviceId = dev.id,
            deviceName = dev.name,
            pressure = pRange != null && pRange.resolution > 0,
            tilt = tRange != null && tRange.range > 0f,
            hover = dev.supportsSource(InputDevice.SOURCE_CLASS_POINTER),
            stylusButtonCount = countButtonBits(event),
            touchFilm = MiTouchFilmBridge.isAvailable,
        )
    }

    /** 已见过的按钮位数(接触时 buttonState;标准通路只有主/副两位) */
    private fun countButtonBits(event: MotionEvent): Int {
        var count = 0
        val state = event.buttonState
        if (state and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) count++
        if (state and MotionEvent.BUTTON_STYLUS_SECONDARY != 0) count++
        return count
    }
}
