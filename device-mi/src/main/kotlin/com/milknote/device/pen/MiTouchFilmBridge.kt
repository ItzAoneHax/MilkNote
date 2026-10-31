package com.milknote.device.pen

import android.content.Context
import android.view.KeyEvent

/**
 * 小米 PenEngine SDK 触控区(焦点笔 Pro)反射薄包装(PEN-ADAPTATION §5.4):
 * - 核心/上层代码不 import SDK 类;AAR 未入库时本文件照常编译
 * - 无 Pro 笔硬件时永远静默,不影响通路①②
 * - 实现排期:M1 末或 M2 初(PEN-ADAPTATION §4.2),当前 isAvailable 恒 false
 */
object MiTouchFilmBridge {

    @Volatile private var ctxRef: Context? = null

    /** SDK 类是否在 classpath 且已初始化成功 */
    val isAvailable: Boolean get() = false // TODO(M2): 接入 PenEngine 0.3.2 后反射探测 MiuiTouchFilmUtils

    /** SDK function 码 → PenTrigger(PEN-ADAPTATION §4.2) */
    fun onDispatchKeyEvent(event: KeyEvent, dispatch: (PenTrigger) -> Unit): Boolean {
        if (!isAvailable) return false
        return false // TODO(M2): 转发 SDK 拦截逻辑
    }

    fun attach(dispatch: (PenTrigger) -> Unit) {
        if (!isAvailable) return
        ctxRef = ctxRef // TODO(M2): MiuiTouchFilmUtils.init + TouchFilmListener
    }
}
