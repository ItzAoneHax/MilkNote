package com.milknote.app.di

import android.content.Context
import com.milknote.app.agent.AppAnnotationSink
import com.milknote.app.agent.AppSnipSource
import com.milknote.app.ui.settings.AppSettingsStore
import com.milknote.app.ui.settings.UpdateController
import com.milknote.agent.AgentController
import com.milknote.agent.AgentStore
import com.milknote.agent.KeyVault
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

    /** 全局 UI 意图(⌘K/⌥A 硬件键 → MilkRoot;MainActivity.dispatchKeyEvent 发射) */
    sealed interface UiEvent {
        data object OpenPalette : UiEvent
        data object OpenAgent : UiEvent
    }

    val uiEvents = kotlinx.coroutines.flow.MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)

    val repository: NotebookRepository by lazy {
        NotebookRepository(appContext.filesDir, appScope)
    }

    val penRouter: PenActionRouter by lazy { PenActionRouter(appContext, appScope) }

    val penPort: PenInputPort by lazy { DefaultPenInputPort(penRouter) }

    /** 笔电量 GATT(权限授予后 refresh 才真正探测;模拟器/无笔 → --%) */
    val penBattery: PenBatteryMonitor by lazy { PenBatteryMonitor(appContext, appScope) }

    val appSettings: AppSettingsStore by lazy { AppSettingsStore(appContext) }

    /** 应用内更新:状态与任务进程级持有,设置页切页/返回不丢失进度 */
    val appUpdater: UpdateController by lazy { UpdateController(appContext, appScope) }

    /** Agent(M2):渲染源经 AgentBridge 由 WriterViewModel 注册 */
    val agentSnipSource: AppSnipSource by lazy { AppSnipSource(render = ::renderAgentPage) }

    val agentController: AgentController by lazy {
        val vault = KeyVault(appContext)
        val c = AgentController(
            scope = appScope,
            store = AgentStore(appContext.filesDir, notebooksRoot()),
            snipSource = agentSnipSource,
            annotationSink = AppAnnotationSink(),
            keyFor = { alias -> runCatching { vault.get(alias) }.getOrNull() },
        )
        c.attachKeyVault(
            put = { alias, key -> vault.put(alias, key) },
            remove = { alias -> vault.remove(alias) },
        )
        c
    }

    private fun notebooksRoot() = java.io.File(appContext.filesDir, "notebooks")

    /** Agent 页面渲染入口(离屏软件渲染;数据由 AgentBridge 提供) */
    private suspend fun renderAgentPage(
        page: com.milknote.agent.PageRef,
        rect: com.milknote.agent.Rect4?,
    ): android.graphics.Bitmap? =
        com.milknote.app.agent.PageRenderer.render(page, rect)

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        penBattery.refresh()
    }
}
