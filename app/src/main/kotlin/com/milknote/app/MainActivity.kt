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

    /** 通路②挂点:悬停态笔实体键走 Activity.dispatchKeyEvent(PEN-ADAPTATION §2) */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
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
        enableEdgeToEdge()
        setContent {
            MilkRoot()   // 主题(明/暗/跟随系统)由 MilkRoot 按设置应用
        }
    }
}
