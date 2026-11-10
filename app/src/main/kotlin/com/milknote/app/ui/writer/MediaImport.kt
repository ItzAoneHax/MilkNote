package com.milknote.app.ui.writer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.MediaKind
import java.io.File
import java.util.UUID

/** 导入结果:src 为相对 notebookDir 的路径,aspectRatio = 宽/高。 */
internal data class ImportedMedia(
    val src: String,
    val durationMs: Long,
    val aspectRatio: Float,
)

/** 页坐标媒体框(x/y/width/height)。 */
internal data class MediaBox(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * 本地音视频导入(纯本地文件,无外部服务):
 * - 视频经 SAF 选择后复制进 `<notebook>.milk/media/`,不保留 content Uri 依赖;
 * - 录音由 MediaRecorder 写入应用缓存,停止后由调用方复制进同一媒体目录;
 * - 时长/画面比例用 MediaMetadataRetriever 探测,失败回退安全默认值。
 */
internal object MediaImport {

    private const val AUDIO_HEIGHT_RATIO = 0.22f

    /** SAF 视频 → notebook media 目录。失败返回 null(调用方提示)。 */
    fun copyVideo(context: Context, uri: Uri, mediaDir: File): ImportedMedia? {
        mediaDir.mkdirs()
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val ext = videoExtension(mime)
        val temp = File(mediaDir, "video-${UUID.randomUUID()}.tmp")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: error("无法读取视频")
        }
        if (copied.isFailure) {
            temp.delete()
            return null
        }
        val target = File(mediaDir, "video-${UUID.randomUUID()}.$ext")
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        val probe = probe(target)
        return ImportedMedia(
            src = "media/${target.name}",
            durationMs = probe.durationMs,
            aspectRatio = probe.aspectRatio,
        )
    }

    /** 录音临时文件 → notebook media 目录(m4a)。 */
    fun importRecordedAudio(file: File, mediaDir: File, durationFallbackMs: Long): ImportedMedia? {
        if (!file.isFile) return null
        mediaDir.mkdirs()
        val target = File(mediaDir, "audio-${UUID.randomUUID()}.m4a")
        val copied = runCatching { file.copyTo(target, overwrite = false) }
        file.delete()
        if (copied.isFailure) {
            target.delete()
            return null
        }
        val probe = probe(target)
        return ImportedMedia(
            src = "media/${target.name}",
            durationMs = if (probe.durationMs > 0L) probe.durationMs else durationFallbackMs.coerceAtLeast(0L),
            aspectRatio = 1f,
        )
    }

    fun videoElement(pageW: Float, pageH: Float, imported: ImportedMedia, now: Long): MediaElement {
        val box = videoBox(pageW, pageH, imported.aspectRatio)
        return MediaElement(
            id = UUID.randomUUID().toString().substring(0, 12),
            kind = MediaKind.VIDEO,
            src = imported.src,
            x = box.x, y = box.y, width = box.width, height = box.height,
            durationMs = imported.durationMs,
            createdAt = now,
        )
    }

    fun audioElement(pageW: Float, pageH: Float, imported: ImportedMedia, now: Long): MediaElement {
        val box = audioBox(pageW, pageH)
        return MediaElement(
            id = UUID.randomUUID().toString().substring(0, 12),
            kind = MediaKind.AUDIO,
            src = imported.src,
            x = box.x, y = box.y, width = box.width, height = box.height,
            durationMs = imported.durationMs,
            createdAt = now,
        )
    }

    /** 视频框:页宽 72% / 页高 60% 内等比适配并居中。 */
    fun videoBox(pageW: Float, pageH: Float, aspectRatio: Float): MediaBox {
        val ratio = aspectRatio.takeIf { it in 0.2f..5f } ?: (16f / 9f)
        val maxW = pageW * 0.72f
        val maxH = pageH * 0.60f
        var width = maxW
        var height = width / ratio
        if (height > maxH) {
            height = maxH
            width = height * ratio
        }
        return MediaBox(
            x = ((pageW - width) / 2f).coerceAtLeast(0f),
            y = ((pageH - height) / 2f).coerceAtLeast(0f),
            width = width,
            height = height,
        )
    }

    /** 音频条:固定宽高比,居中偏上,避免遮住页脚。 */
    fun audioBox(pageW: Float, pageH: Float): MediaBox {
        val width = (pageW * 0.55f).coerceIn(240f, 560f).coerceAtMost(pageW * 0.9f)
        val height = (width * AUDIO_HEIGHT_RATIO).coerceAtLeast(48f)
        return MediaBox(
            x = ((pageW - width) / 2f).coerceAtLeast(0f),
            y = (pageH * 0.42f).coerceAtLeast(0f),
            width = width,
            height = height,
        )
    }

    fun videoExtension(mime: String?): String = when (mime?.lowercase()) {
        "video/webm" -> "webm"
        "video/x-matroska", "video/mkv" -> "mkv"
        "video/3gpp", "video/3gp" -> "3gp"
        "video/quicktime" -> "mov"
        else -> "mp4"
    }

    private data class Probe(val durationMs: Long, val aspectRatio: Float)

    private fun probe(file: File): Probe {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val ratio = if (width > 0 && height > 0) width.toFloat() / height else 16f / 9f
            Probe(duration.coerceAtLeast(0L), ratio)
        } catch (_: Throwable) {
            Probe(0L, 16f / 9f)
        } finally {
            runCatching { retriever.release() }
        }
    }
}
