plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// :agent —— Agent 后端(AGENT-BACKEND.md §2.1):Provider / SSE / 上下文 / 意图 / 会话 / Key。
// 不依赖 note-domain / ink-engine(端口解耦);网络层用 HttpURLConnection 系统栈,
// 项目保持零第三方网络依赖(本模块不引 okhttp)。
android {
    namespace = "com.milknote.agent"
    compileSdk = 35
    buildToolsVersion = "34.0.0"

    defaultConfig {
        minSdk = 31
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // 纯 JVM 单测(SSE/提示词/预算/错误映射/状态机):无 android.framework 依赖
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
}
