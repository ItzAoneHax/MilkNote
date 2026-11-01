package com.milknote.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.MilkRoot

class MainActivity : ComponentActivity() {

    /** 通路②挂点:悬停态笔实体键走 Activity.dispatchKeyEvent(PEN-ADAPTATION §2);
     *  另接外接键盘快捷键:⌘K 命令面板 / ⌥A Agent(DESIGN-V0 §4.1) */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val isCmd = event.isCtrlPressed || event.isMetaPressed
            val openPalette = isCmd && event.keyCode == KeyEvent.KEYCODE_K
            val openAgent = event.isAltPressed && event.keyCode == KeyEvent.KEYCODE_A
            when {
                openPalette -> ServiceLocator.uiEvents.tryEmit(ServiceLocator.UiEvent.OpenPalette)
                openAgent -> ServiceLocator.uiEvents.tryEmit(ServiceLocator.UiEvent.OpenAgent)
            }
            if (openPalette || openAgent) return true
        }
        if (ServiceLocator.penPort.onDispatchKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /** 通路①b挂点:笔按键 ACTION_BUTTON_PRESS/RELEASE 官方走 generic motion(悬停/接触皆然) */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (ServiceLocator.penPort.onDispatchGenericMotionEvent(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.activity = this
        enableEdgeToEdge()
        setContent {
            MilkRoot()   // 主题(明/暗/跟随系统)由 MilkRoot 按设置应用
        }
    }

    override fun onDestroy() {
        // 兜底:进程销毁时复位笔键接管标志(防崩溃残留)
        com.milknote.app.pen.PenKeyTakeover.set(applicationContext, false)
        super.onDestroy()
    }
}
