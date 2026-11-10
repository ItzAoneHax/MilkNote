package com.milknote.app.ui.writer

/**
 * 录音权限/录制状态机的纯逻辑(无 Android 依赖,可直接 JVM 单测)。
 * UI 只负责把系统权限结果与 MediaRecorder 回调翻译成 [RecordEvent],
 * 再用 [reduceRecordState] 得到可渲染状态;前台独占语义也由此表达:
 * 离开面板/生命周期 = Dismiss/Stopped,不存在后台录制状态。
 */
enum class RecordPermission { UNKNOWN, GRANTED, DENIED }

enum class RecordPhase { IDLE, RECORDING, ERROR }

data class RecordUiState(
    val permission: RecordPermission = RecordPermission.UNKNOWN,
    val phase: RecordPhase = RecordPhase.IDLE,
    val elapsedMs: Long = 0L,
    val message: String? = null,
) {
    val isRecording: Boolean get() = phase == RecordPhase.RECORDING
    val canStart: Boolean get() = permission == RecordPermission.GRANTED && phase != RecordPhase.RECORDING
}

sealed interface RecordEvent {
    data object PermissionGranted : RecordEvent
    data object PermissionDenied : RecordEvent
    data object Start : RecordEvent
    data class Tick(val elapsedMs: Long) : RecordEvent
    data class Stopped(val durationMs: Long) : RecordEvent
    data class Failed(val message: String) : RecordEvent
    data object Dismiss : RecordEvent
}

/** 状态迁移:被拒绝时说明原因;未授权不允许 Start;停止后保留时长用于展示。 */
fun reduceRecordState(state: RecordUiState, event: RecordEvent): RecordUiState = when (event) {
    RecordEvent.PermissionGranted -> state.copy(
        permission = RecordPermission.GRANTED,
        message = null,
    )
    RecordEvent.PermissionDenied -> state.copy(
        permission = RecordPermission.DENIED,
        phase = RecordPhase.IDLE,
        elapsedMs = 0L,
        message = "需要麦克风权限才能录音。请允许权限后重试；若已永久拒绝，请在系统设置中为本应用开启麦克风。",
    )
    RecordEvent.Start -> when {
        state.permission != RecordPermission.GRANTED -> state.copy(
            phase = RecordPhase.IDLE,
            message = "需要麦克风权限才能录音",
        )
        state.phase == RecordPhase.RECORDING -> state
        else -> state.copy(phase = RecordPhase.RECORDING, elapsedMs = 0L, message = null)
    }
    is RecordEvent.Tick -> if (state.phase == RecordPhase.RECORDING) {
        state.copy(elapsedMs = event.elapsedMs.coerceAtLeast(state.elapsedMs))
    } else state
    is RecordEvent.Stopped -> state.copy(
        phase = RecordPhase.IDLE,
        elapsedMs = event.durationMs.coerceAtLeast(0L),
        message = null,
    )
    is RecordEvent.Failed -> state.copy(
        phase = RecordPhase.ERROR,
        message = event.message.ifBlank { "录音失败" },
    )
    RecordEvent.Dismiss -> state.copy(
        phase = RecordPhase.IDLE,
        elapsedMs = 0L,
        message = null,
    )
}

/** mm:ss(录音面板计时;超过 1 小时 h:mm:ss)。 */
fun formatRecordElapsed(elapsedMs: Long): String {
    val total = (elapsedMs / 1000L).coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
