import java.io.File
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.concurrent.Executors

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---------------- 无线安装到平板(adb over Wi-Fi) ----------------
// 首次(一次性):
//   1. 平板:设置 → 更多设置 → 开发者选项 → 无线调试 → 「使用配对码配对设备」
//   2. ./gradlew :app:pairWifi -Paddr=192.168.x.x:配对端口 -Pcode=屏幕上的6位码
//   3. 把连接地址(无线调试主页显示的 IP:端口,注意不是配对端口)写进 local.properties:
//        wifi.addr=192.168.x.x:端口
// 之后每次更新:./gradlew :app:installWifi(自动构建+连接+覆盖安装,平板零操作)
// ---------------------------------------------------------------

fun adbPath(): String {
    val env = System.getenv("ANDROID_SDK_ROOT")
    val sdk = if (!env.isNullOrBlank()) env else {
        val props = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        props.getProperty("sdk.dir")?.replace("\\:", ":")
            ?: error("找不到 Android SDK(设 ANDROID_SDK_ROOT 或 local.properties sdk.dir)")
    }
    return File(sdk, "platform-tools/adb.exe").absolutePath
}

tasks.register("pairWifi") {
    group = "milknote"
    description = "首次配对平板无线调试(配对码见无线调试页)"
    doLast {
        val addr = (project.findProperty("addr") as String?) ?: error("用法: -Paddr=192.168.x.x:配对端口 -Pcode=六位配对码")
        val code = (project.findProperty("code") as String?) ?: error("缺少 -Pcode")
        exec { commandLine(adbPath(), "pair", addr, code) }
    }
}

tasks.register("installWifi") {
    group = "milknote"
    description = "构建 debug 包并无线安装到平板(local.properties: wifi.addr)"
    dependsOn("assembleDebug")
    doLast {
        val props = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        val addr = (project.findProperty("deviceAddr") as String?) ?: props.getProperty("wifi.addr")
        ?: error("local.properties 缺少 wifi.addr=IP:端口(见无线调试主页;或临时用 -PdeviceAddr=)")
        val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        exec { commandLine(adbPath(), "connect", addr) }
        exec { commandLine(adbPath(), "-s", addr, "install", "-r", apk.absolutePath) }
        println("✓ 已无线安装到 $addr(平板上直接打开 MilkNote 即可,数据保留)")
    }
}

// ---------------- 局域网更新服务(应用内「检查更新」的数据源) ----------------
// 用法:./gradlew :app:serveUpdates   →  构建最新 APK 并在本机 8765 端口托管
// 平板 App:设置 → 更新 → 服务器填 http://<本机局域网IP>:8765 → 检查更新
// (Ctrl+C 停止;Windows 首次会弹防火墙授权,选「允许」)
// latest.json 按请求时 APK 实际状态动态生成(buildTime = APK 落盘时刻)
tasks.register("serveUpdates") {
    group = "milknote"
    description = "构建并在本机 8765 端口托管最新 APK + latest.json(平板应用内检查更新)"
    dependsOn("assembleDebug")
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        val version = android.defaultConfig.versionName ?: "dev"

        val server = com.sun.net.httpserver.HttpServer.create(InetSocketAddress(8765), 0)
        server.createContext("/") { ex ->
            fun respond(bytes: ByteArray, type: String, code: Int = 200) {
                ex.responseHeaders.add("Content-Type", type)
                ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
                ex.responseBody.use { if (bytes.isNotEmpty()) it.write(bytes) }
            }
            val path = ex.requestURI.path.trimStart('/')
            val latest = """{"versionName":"$version","buildTime":${apk.lastModified()},"apk":"app-debug.apk","size":${apk.length()}}"""
            when (path) {
                "latest.json" -> respond(latest.toByteArray(), "application/json")
                "app-debug.apk" -> respond(apk.readBytes(), "application/vnd.android.package-archive")
                else -> respond("MilkNote update server".toByteArray(), "text/plain")
            }
            ex.close()
        }
        server.executor = Executors.newCachedThreadPool()
        server.start()
        val ips = NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filter { !it.isLoopbackAddress && it is Inet4Address }
            .joinToString(" / ") { "http://${it.hostAddress}:8765" }
        println("")
        println("✓ MilkNote 更新服务已启动(版本 $version,文件时间 ${SimpleDateFormat("MM-dd HH:mm:ss").format(Date(apk.lastModified()))})")
        println("  平板 App → 设置 → 更新 → 服务器地址填:$ips")
        println("  Ctrl+C 停止")
        Thread.currentThread().join() // 阻塞保持运行
    }
}

// ---------------- GitHub Releases 发布(平板应用内「检查更新」的公网数据源) ----------------
// 前置(一次性):git 仓库推 GitHub;gh CLI 已登录(gh auth login);
//               local.properties 加一行 github.repo=<用户名>/MilkNote
// 发布:./gradlew :app:publishGithub   →  构建 + 固定 tag=latest 的 Release(自动替换旧版)
// 平板 App:设置 → 更新 → 服务器填 https://github.com/<用户名>/MilkNote/releases/download/latest
tasks.register("publishGithub") {
    group = "milknote"
    description = "构建并发布到 GitHub Releases(固定 tag=latest;App 端填 releases/download/latest 地址)"
    dependsOn("assembleDebug")
    doLast {
        val props = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        val repo = (project.findProperty("repo") as String?) ?: props.getProperty("github.repo")
        ?: error("local.properties 缺少 github.repo=<用户名>/MilkNote(或临时 -Prepo=)")
        val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        val version = android.defaultConfig.versionName ?: "dev"
        val latestFile = layout.buildDirectory.file("updates/latest.json").get().asFile.apply {
            parentFile.mkdirs()
            writeText("""{"versionName":"$version","buildTime":${apk.lastModified()},"apk":"app-debug.apk","size":${apk.length()}}""")
        }
        val gh = if (System.getProperty("os.name").lowercase().contains("windows")) "gh.exe" else "gh"
        // 固定 tag=latest:先删旧 Release 与 tag,再建新的(App 端 URL 恒定)
        exec {
            commandLine(gh, "release", "delete", "latest", "--yes", "--cleanup-tag", "--repo", repo)
            isIgnoreExitValue = true // 首次发布不存在,忽略
        }
        exec {
            commandLine(
                gh, "release", "create", "latest",
                apk.absolutePath, latestFile.absolutePath,
                "--title", "MilkNote $version",
                "--notes", "构建于 ${SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(apk.lastModified()))}(应用内检查更新可用)",
                "--repo", repo,
            )
        }
        println("")
        println("✓ 已发布 → https://github.com/$repo/releases/tag/latest")
        println("  平板 App → 设置 → 更新 → 服务器地址填:")
        println("  https://github.com/$repo/releases/download/latest")
    }
}

android {
    namespace = "com.milknote.app"
    compileSdk = 35
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.milknote.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0-m2"
        // 应用内更新比对用:每次构建取当前时间(同 versionCode 覆盖安装,以时间为新)
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":agent"))
    implementation(project(":ink-engine"))
    implementation(project(":note-domain"))
    implementation(project(":device-mi"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
}
