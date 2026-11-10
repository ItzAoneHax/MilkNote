package com.milknote.app.ui.writer

import android.media.MediaPlayer
import android.os.SystemClock
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.milknote.app.ui.common.MilkButton
import com.milknote.app.ui.common.MilkButtonVariant
import com.milknote.app.ui.common.MilkCenterPanel
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.MediaKind
import com.milknote.ink.render.MediaElementRenderer
import kotlinx.coroutines.delay
import java.io.File

/**
 * 插入视频入口(中央面板):明确说明「本地复制、不上传」,再由系统 SAF 选择文件。
 */
@Composable
internal fun VideoInsertPanel(
    onChoose: () -> Unit,
    onDismiss: () -> Unit,
) {
    MilkCenterPanel(
        title = "插入视频",
        subtitle = "本地视频 · 复制进当前笔记本",
        onDismiss = onDismiss,
        maxWidth = 460.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(
                "从系统文件选择器选择一个本地视频。文件会复制到笔记本的 media 目录，页面只显示静态占位，点击播放时在中央面板内播放；不会上传或使用外部服务。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MilkButton(label = "选择视频", onClick = onChoose)
                MilkButton(
                    label = "取消",
                    variant = MilkButtonVariant.SECONDARY,
                    onClick = onDismiss,
                )
            }
        }
    }
}

/**
 * 录音面板(前台独占):
 * - 未授权:解释为什么需要麦克风,并提供「授权」与「系统设置」两条出路;
 * - 已授权:开始/停止;停止后 m4a 交给 ViewModel 插入为音频元素;
 * - 关闭面板 / ON_STOP(退后台)立即停止并丢弃本次录音,不做后台录制。
 */
@Composable
internal fun AudioRecordPanel(
    permission: RecordPermission,
    onRequestPermission: () -> Unit,
    onInserted: (File, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    var state by remember { mutableStateOf(RecordUiState(permission = permission)) }
    var startedAtMs by remember { mutableStateOf(0L) }

    LaunchedEffect(permission) {
        state = when (permission) {
            RecordPermission.GRANTED -> reduceRecordState(state, RecordEvent.PermissionGranted)
            RecordPermission.DENIED -> reduceRecordState(state, RecordEvent.PermissionDenied)
            RecordPermission.UNKNOWN -> state
        }
    }

    // 计时只驱动 UI;真实时长以 MediaRecorder 输出的元数据为准(VM 侧探测)。
    LaunchedEffect(state.phase) {
        if (state.phase != RecordPhase.RECORDING) return@LaunchedEffect
        while (true) {
            delay(200)
            state = reduceRecordState(
                state,
                RecordEvent.Tick(SystemClock.elapsedRealtime() - startedAtMs),
            )
        }
    }

    val dismiss = {
        if (recorder.isRecording) {
            recorder.cancel()
            state = reduceRecordState(state, RecordEvent.Dismiss)
        }
        onDismiss()
    }

    // 生命周期:离开前台立即停止并丢弃,避免后台录音。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && recorder.isRecording) {
                recorder.cancel()
                state = reduceRecordState(state, RecordEvent.Dismiss)
                onDismiss()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recorder.cancel()
        }
    }

    MilkCenterPanel(
        title = "录音",
        subtitle = "仅前台录音 · 关闭面板或退到后台会停止并丢弃",
        onDismiss = dismiss,
        maxWidth = 460.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            if (state.permission != RecordPermission.GRANTED) {
                Text(
                    "录音需要麦克风权限。MilkNote 只在你按下开始后、且本面板在前台时录音，不会后台上传或使用外部服务。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MilkButton(label = "授权麦克风", onClick = onRequestPermission)
                    if (state.permission == RecordPermission.DENIED) {
                        MilkButton(
                            label = "打开系统设置",
                            variant = MilkButtonVariant.SECONDARY,
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            android.net.Uri.fromParts("package", context.packageName, null),
                                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                        )
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatRecordElapsed(state.elapsedMs),
                            fontSize = 34.sp,
                            fontFamily = com.milknote.app.ui.theme.Mono,
                            color = if (state.isRecording) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            when {
                                state.isRecording -> "正在录音…"
                                state.elapsedMs > 0 -> "录音已停止"
                                else -> "准备录音"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.message?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isRecording) {
                        MilkButton(
                            label = "停止并插入",
                            onClick = {
                                val elapsed = SystemClock.elapsedRealtime() - startedAtMs
                                val file = recorder.stop()
                                if (file == null) {
                                    state = reduceRecordState(
                                        state,
                                        RecordEvent.Failed("录音时间过短或设备不可用，请重新录制"),
                                    )
                                } else {
                                    state = reduceRecordState(state, RecordEvent.Stopped(elapsed))
                                    onInserted(file, elapsed)
                                    onDismiss()
                                }
                            },
                        )
                    } else {
                        MilkButton(
                            label = "开始录音",
                            enabled = state.canStart,
                            onClick = {
                                val cache = File(context.cacheDir, "record")
                                if (recorder.start(cache)) {
                                    startedAtMs = SystemClock.elapsedRealtime()
                                    state = reduceRecordState(state, RecordEvent.Start)
                                } else {
                                    state = reduceRecordState(
                                        state,
                                        RecordEvent.Failed("无法启动录音，请检查麦克风是否被其他应用占用"),
                                    )
                                }
                            },
                        )
                    }
                    MilkButton(
                        label = "关闭",
                        variant = MilkButtonVariant.SECONDARY,
                        onClick = dismiss,
                    )
                }
            }
        }
    }
}

/**
 * 中央媒体播放面板(同一时间最多一个):视频 VideoView / 音频 MediaPlayer,均提供播放/暂停。
 * 关闭即释放播放器;退到后台自动暂停,不常驻后台播放。
 */
@Composable
internal fun MediaPlaybackPanel(
    element: MediaElement,
    notebookDir: File,
    onDismiss: () -> Unit,
) {
    val file = remember(element.id, element.src) { resolveMediaFile(notebookDir, element.src) }
    val title = if (element.kind == MediaKind.VIDEO) "视频" else "录音"
    if (file == null) {
        MilkCenterPanel(title = title, subtitle = "本地媒体", onDismiss = onDismiss, maxWidth = 460.dp) {
            Text(
                "媒体文件缺失或不在笔记本目录内。",
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    MilkCenterPanel(
        title = title,
        subtitle = "本地文件 · ${MediaElementRenderer.formatDuration(element.durationMs)}",
        onDismiss = onDismiss,
        maxWidth = 620.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            if (element.kind == MediaKind.VIDEO) {
                VideoPlaybackBody(file, element)
            } else {
                AudioPlaybackBody(file, element)
            }
        }
    }
}

@Composable
private fun VideoPlaybackBody(file: File, element: MediaElement) {
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var playing by remember(element.id) { mutableStateOf(false) }
    var error by remember(element.id) { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                videoView?.let { if (it.isPlaying) it.pause() }
                playing = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            videoView?.stopPlayback()
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoPath(file.absolutePath)
                    setOnPreparedListener { it.isLooping = false }
                    setOnCompletionListener { playing = false }
                    setOnErrorListener { _, _, _ ->
                        error = "视频无法播放(格式不受系统支持)"
                        playing = false
                        true
                    }
                    videoView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
    error?.let {
        Text(
            it,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MilkButton(
            label = if (playing) "暂停" else "播放",
            enabled = error == null,
            onClick = {
                val vv = videoView ?: return@MilkButton
                if (playing) {
                    vv.pause()
                    playing = false
                } else {
                    vv.start()
                    playing = true
                }
            },
        )
        Text(
            MediaElementRenderer.formatDuration(element.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AudioPlaybackBody(file: File, element: MediaElement) {
    var player by remember(element.id) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(element.id) { mutableStateOf(false) }
    var error by remember(element.id) { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(file) {
        val media = MediaPlayer()
        val sourceOk = runCatching { media.setDataSource(file.absolutePath) }.isSuccess
        if (sourceOk) {
            media.setOnPreparedListener { prepared ->
                player = prepared
            }
            media.setOnCompletionListener {
                playing = false
                runCatching { media.seekTo(0) }
            }
            media.setOnErrorListener { _, _, _ ->
                playing = false
                error = "录音无法播放"
                true
            }
            runCatching { media.prepareAsync() }
                .onFailure { error = "录音无法播放(文件损坏或格式不支持)" }
        } else {
            runCatching { media.release() }
            error = "录音无法播放(文件损坏或格式不支持)"
        }
        onDispose {
            runCatching { media.release() }
            player = null
            playing = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player?.let { runCatching { if (it.isPlaying) it.pause() } }
                playing = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (element.durationMs > 0L) MediaElementRenderer.formatDuration(element.durationMs) else "录音",
                fontSize = 28.sp,
                fontFamily = com.milknote.app.ui.theme.Mono,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (playing) "播放中…" else "本地录音",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    error?.let {
        Text(
            it,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MilkButton(
            label = if (playing) "暂停" else "播放",
            enabled = player != null,
            onClick = {
                val p = player ?: return@MilkButton
                if (playing) {
                    runCatching { p.pause() }
                    playing = false
                } else {
                    runCatching { p.start() }
                    playing = true
                }
            },
        )
        Spacer(Modifier.width(2.dp))
        Text(
            MediaElementRenderer.formatDuration(element.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** src 解析到 notebook 目录内;越界/缺失/含 . 或 .. 段返回 null(不读取任意路径)。 */
private fun resolveMediaFile(notebookDir: File, src: String): File? {
    if (src.isBlank()) return null
    val segments = src.replace('\\', '/').split('/')
    if (segments.any { it == "." || it == ".." }) return null
    return runCatching {
        val root = notebookDir.canonicalFile
        val f = File(notebookDir, src).canonicalFile
        if (f.toPath().startsWith(root.toPath()) && f != root && f.isFile) f else null
    }.getOrNull()
}
