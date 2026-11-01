pluginManagement {
    repositories {
        // 国内网络：阿里云镜像优先，官方源后备
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // PREFER_SETTINGS：全局 init 脚本(mirrors.gradle)也向 project 注入仓库，FAIL 模式会冲突；
    // settings 已按「镜像优先 + 官方后备」声明，直接以 settings 为准。
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}

rootProject.name = "MilkNote"

include(":app")
include(":agent")
include(":ink-engine")
include(":note-domain")
include(":device-mi")
