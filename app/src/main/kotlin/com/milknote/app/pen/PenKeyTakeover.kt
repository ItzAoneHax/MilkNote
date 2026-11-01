package com.milknote.app.pen

import android.content.Context
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 笔键长按接管(2026-11-01 真机实锤的官方标志通道):
 *
 * MIUI 框架的笔键拦截器(MiuiStylusShortcutManager)读 Secure 表的 `gb_boosting`
 * 标志(game_booster),为 1 时整个拦截器停摆——上键长按(系统框选截图)与
 * 下键长按(小米创作)的 380ms 定时器都不再启动,KeyEvent 完整到达应用,无任何提示。
 *
 * 写入需要 WRITE_SECURE_SETTINGS(signature|development 权限,个人开发装机的标准做法):
 *   adb shell pm grant com.milknote.app android.permission.WRITE_SECURE_SETTINGS
 * 一次性授权;未授权时应用回退 FLAG_SECURE 方案(能拦但系统弹「无法截屏」提示)。
 *
 * 生命周期:进入书写页写 1;离开书写页/退到后台/onDestroy 写 0(系统行为恢复)。
 * 崩溃残留的 1 会在下次启动/前台切换时被覆写。
 */
object PenKeyTakeover {

    const val KEY_GB_BOOSTING = "gb_boosting"

    fun canWrite(context: Context): Boolean = runCatching {
        ContextCompat.checkSelfPermission(context, "android.permission.WRITE_SECURE_SETTINGS") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** 开/关笔键接管(无权限时静默无效,由调用方回退 FLAG_SECURE) */
    fun set(context: Context, on: Boolean) {
        if (!canWrite(context)) return
        runCatching { Settings.Secure.putInt(context.contentResolver, KEY_GB_BOOSTING, if (on) 1 else 0) }
    }
}
