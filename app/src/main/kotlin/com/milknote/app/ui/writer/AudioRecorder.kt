package com.milknote.app.ui.writer

import android.content.Context
import android.media.MediaRecorder
import java.io.File
import java.util.UUID

/**
 * 前台录音器:MediaRecorder(MIC → MPEG_4/AAC → .m4a)。
 *
 * 只由录音面板在可见时持有;面板关闭或生命周期离开前台必须 stop/cancel,
 * 不存在 Service、后台任务或常驻录音。输出先写应用缓存 `record/`,
 * 停止后由 WriterViewModel 复制进笔记本媒体目录再插入页面元素。
 */
internal class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var output: File? = null

    val isRecording: Boolean get() = recorder != null

    /** 开始录音;已有录音、权限缺失或设备配置失败返回 false。 */
    fun start(cacheDir: File): Boolean {
        if (recorder != null) return false
        cacheDir.mkdirs()
        val file = File(cacheDir, "record-${UUID.randomUUID()}.m4a")
        val media = MediaRecorder(context)
        val ok = runCatching {
            media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setAudioSamplingRate(44_100)
            media.setAudioEncodingBitRate(96_000)
            media.setOutputFile(file.absolutePath)
            media.prepare()
            media.start()
        }.isSuccess
        return if (ok) {
            recorder = media
            output = file
            true
        } else {
            runCatching { media.release() }
            file.delete()
            false
        }
    }

    /** 停止并返回缓存文件(过短/编码失败返回 null 并清理)。 */
    fun stop(): File? {
        val media = recorder ?: return null
        val file = output
        recorder = null
        output = null
        return try {
            media.stop()
            media.release()
            file?.takeIf { it.isFile && it.length() > 0L }
        } catch (_: Throwable) {
            runCatching { media.release() }
            file?.delete()
            null
        }
    }

    /** 放弃本次录音(关闭面板/退后台):停止并删除临时文件。 */
    fun cancel() {
        stop()?.delete()
    }
}
