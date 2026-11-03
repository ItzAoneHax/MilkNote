package com.milknote.app.ui.settings

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 更新流程的进程级状态持有者:检查/下载跑在注入的 appScope 里,
 * 状态经 StateFlow 暴露。设置页切页(UpdatesPage 退出组合)不会打断进行中的任务,
 * 切回来仍能看到进度/「发现新版本」/「已下载」。
 */
class UpdateController(
    private val appContext: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AppUpdater.State>(AppUpdater.State.Idle)
    val state: StateFlow<AppUpdater.State> = _state.asStateFlow()

    private var job: Job? = null

    /** 检查更新;任务进行中重复点击直接忽略 */
    fun check(server: String, currentVersion: String, currentBuildTime: Long) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = AppUpdater.State.Checking
            val latest = AppUpdater.check(server)
            _state.value = when {
                latest == null -> AppUpdater.State.Failed("连不上更新服务")
                AppUpdater.isNewer(latest, currentVersion, currentBuildTime) ->
                    AppUpdater.State.Available(latest)
                else -> AppUpdater.State.UpToDate
            }
        }
    }

    /** 下载 APK;任务进行中重复点击直接忽略 */
    fun download(server: String, latest: AppUpdater.Latest) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = AppUpdater.State.Downloading(0)
            val file = AppUpdater.download(appContext, server, latest) { p ->
                _state.value = AppUpdater.State.Downloading(p)
            }
            _state.value = if (file != null) AppUpdater.State.ReadyToInstall(file)
            else AppUpdater.State.Failed("下载失败(服务开着吗?)")
        }
    }
}
