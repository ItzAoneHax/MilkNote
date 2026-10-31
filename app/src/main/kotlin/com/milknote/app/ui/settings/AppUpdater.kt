package com.milknote.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内自更新(局域网开发流):检查 → 下载 → 系统安装器覆盖安装。
 * 更新源 = 电脑上 `./gradlew :app:serveUpdates`(HTTP 8765,latest.json + APK)。
 * 版本判定:versionName + 构建时间(同签名同 versionCode 可覆盖安装,数据保留)。
 */
object AppUpdater {

    data class Latest(val versionName: String, val buildTime: Long, val apk: String, val size: Long)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class Available(val latest: Latest) : State
        data object UpToDate : State
        data class Downloading(val percent: Int) : State
        data class ReadyToInstall(val file: File) : State
        data class Failed(val reason: String) : State
    }

    /** 规范化服务器地址(http://ip:port,容错无 scheme / 尾斜杠) */
    fun normalizeServer(raw: String): String {
        var s = raw.trim().trimEnd('/')
        if (s.isNotEmpty() && !s.startsWith("http")) s = "http://$s"
        return s
    }

    /** GET latest.json;网络失败返回 null(调用方给 Failed 状态) */
    suspend fun check(server: String): Latest? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("${normalizeServer(server)}/latest.json").openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode != 200) return@runCatching null
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            Latest(
                versionName = body.regex("versionName") ?: return@runCatching null,
                buildTime = body.regexLong("buildTime") ?: 0L,
                apk = body.regex("apk") ?: "app-debug.apk",
                size = body.regexLong("size") ?: 0L,
            )
        }.getOrNull()
    }

    /**
     * 有新版本:versionName 不同,或构建时间明显更新。
     * 容差 90s:服务端时间戳 = APK 落盘时刻,比客户端 BuildConfig(构建启动时刻)晚,
     * 同一构建不误报;真实新构建间隔远大于容差。
     */
    fun isNewer(latest: Latest, currentVersion: String, currentBuildTime: Long): Boolean =
        latest.versionName != currentVersion || latest.buildTime > currentBuildTime + 90_000L

    /** 下载 APK 到 cacheDir/updates/(带进度);失败返回 null */
    suspend fun download(context: Context, server: String, latest: Latest, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, latest.apk)
                val conn = URL("${normalizeServer(server)}/${latest.apk}").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 30_000
                if (conn.responseCode != 200) return@runCatching null
                val total = conn.contentLengthLong.let { if (it > 0) it else latest.size }
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done * 100 / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
                conn.disconnect()
                out
            }.getOrNull()
        }

    /** 是否已获「安装未知应用」授权(API 26+) */
    fun canRequestInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 26) context.packageManager.canRequestPackageInstalls() else true

    /** 跳系统页授权「允许安装未知应用」 */
    fun installPermissionIntent(): Intent = Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:com.milknote.app"),
    )

    /** 拉起系统安装器(同签名覆盖安装,数据保留) */
    fun launchInstall(context: Context, file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        true
    }.getOrDefault(false)

    // ---- latest.json 手工解析(避免为此引 JSON 依赖) ----
    private fun String.regex(key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(this)?.groupValues?.get(1)

    private fun String.regexLong(key: String): Long? =
        Regex("\"$key\"\\s*:\\s*(\\d+)").find(this)?.groupValues?.get(1)?.toLongOrNull()
}
