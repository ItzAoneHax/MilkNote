package com.milknote.app.di

import android.content.Context
import com.milknote.app.ui.settings.AppSettingsStore
import com.milknote.device.pen.DefaultPenInputPort
import com.milknote.device.pen.PenActionRouter
import com.milknote.device.pen.PenBatteryMonitor
import com.milknote.device.pen.PenInputPort
import com.milknote.notes.storage.NotebookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 手动装配(M1 不引 DI 框架,单进程单实例足够) */
object ServiceLocator {

    lateinit var appContext: Context
        private set

    /** 宿主 Activity(窗口 flag 操作用;MainActivity.onCreate 注册) */
    @Volatile var activity: android.app.Activity? = null

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: NotebookRepository by lazy {
        NotebookRepository(appContext.filesDir, appScope)
    }

    val penRouter: PenActionRouter by lazy { PenActionRouter(appContext, appScope) }

    val penPort: PenInputPort by lazy { DefaultPenInputPort(penRouter) }

    /** 笔电量 GATT(权限授予后 refresh 才真正探测;模拟器/无笔 → --%) */
    val penBattery: PenBatteryMonitor by lazy { PenBatteryMonitor(appContext, appScope) }

    val appSettings: AppSettingsStore by lazy { AppSettingsStore(appContext) }

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        penBattery.refresh()
    }
}
