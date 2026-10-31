plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.milknote.device"
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
}

dependencies {
    api(project(":ink-engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // 前瞻:PenEngine SDK(触控区手势,焦点笔 Pro)经 MiTouchFilmBridge 反射薄包装,
    // 不在此声明编译依赖(AAR 未入库时本模块照常构建)。见 PEN-ADAPTATION §5.4。

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.android)
}
