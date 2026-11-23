# 小米笔记 (com.miui.notes) 实测档案

> 结论：小米笔记是 MIUI/HyperOS 系统应用，所有可获取版本都在清单中把 `micloud-sdk` 声明为**必需**共享库，非小米系统一律 `INSTALL_FAILED_MISSING_SHARED_LIBRARY`。本次无法安装、无法截图；以下为安装尝试记录。

## 安装包与来源

| 版本 | 来源 | 结果 |
|---|---|---|
| `8.5.7` (`versionCode=857`), 74,913,073 bytes, arm64-v8a + armeabi-v7a | APKPure（apkeep，arch=arm64-v8a） | `INSTALL_FAILED_MISSING_SHARED_LIBRARY … requires unavailable shared library micloud-sdk` |
| `7.4.9` (749) | APKPure | 清单 `uses-library:'micloud-sdk'`（必需），同上 |
| `6.0.0` (600) | APKPure | 同上 |
| `5.9.8` (598) | APKPure | 同上 |
| `2.3.0.7` (2307, compileSdk 36 的新编译包) | APKPure | 同上 |

- 8.5.7 SHA-256：`f2702c07f85449a16bd83db01519841792e96d66f186384ffdf69140b8fcbe76`
- 8.5.7 签名主体：`EMAILADDRESS=miui@xiaomi.com, CN=MIUI, OU=MIUI, O=Xiaomi, L=Beijing, ST=Beijing, C=CN`，证书 SHA-256 `c9009d01ebf9f5d0302bc71b2fe9aa9a47a432bba17308a3111b75d7b2149025`
- 启动 Activity：`com.miui.notes/.ui.NotesListActivity`

`aapt dump badging` 节选（8.5.7）：

```
uses-library:'micloud-sdk'
uses-library-not-required:'com.miui.system'
uses-library-not-required:'androidx.window.extensions'
```

## 为什么不绕过

- `micloud-sdk` 只存在于 MIUI/HyperOS 系统分区，AOSP 模拟器无法提供。
- 把 `uses-library` 改为可选需要重打包并破坏小米签名，违背「不使用第三方重打包 APK」的约束，故不采用。

## 后续可行路径

1. 连接一台小米/Redmi 真机或平板（项目内已有 `device-mi` 模块，说明目标设备存在），用 `adb shell pm path com.miui.notes` 直接定位系统 APK，并按本目录同样的 `capture.sh` 流程截图。
2. 或使用带 HyperOS 镜像的模拟器（目前无官方 x86_64 HyperOS 镜像）。

## 文件

- 本目录仅有本 README；无截图、无 UI 层级、无标注。
