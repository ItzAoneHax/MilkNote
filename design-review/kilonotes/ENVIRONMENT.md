# 实测环境记录

## 2026-11-09 兼容性过程

1. 官方 APK 检查：`3.23.0 (3646)`，仅 `arm64-v8a` / `armeabi-v7a`。
2. 现有 `medium_phone` / `milk_pad` 使用 API 35 `default;x86_64`。安装返回 `INSTALL_FAILED_NO_MATCHING_ABIS`。
3. 从 Google 官方清单下载 `arm64-v8a-35_r02.zip`：769,099,654 bytes，SHA-1 `2026a06409db630b56711afdbffb457c1dbaed49`，与清单一致。
4. 新建 `kilonotes_arm` 后，Emulator 37.2.10.0 明确返回：`Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host`。该 AVD 不能用于本机实测。
5. 官方 Emulator 30.0.0 发布说明及 API 35 Google APIs 镜像运行时资料表明：应使用带 `libndk_translation.so` 的 `google_apis;x86_64` 镜像，而不是 ARM AVD。
6. 下载 `system-images;android-35;google_apis;x86_64` (`x86_64-35_r09.zip`)：1,738,815,903 bytes，SHA-1 `0103e6dab21290c4b9d16550a3ce99476f884eef`，与官方清单一致。
7. 新建独立 `kilonotes_x86` AVD（API 35，2560×1600，320dpi，横屏）。运行时确认 `ro.dalvik.vm.native.bridge=libndk_translation.so`、`ro.product.cpu.abilist=x86_64,arm64-v8a`、`ro.enable.native.bridge.exec=1`。
8. 千本笔记官方 APK 安装结果：`Performing Streamed Install / Success`。

## 约束

- 不清空、不覆盖现有 `medium_phone` 或 `milk_pad` 数据。
- 不使用第三方 ARM 翻译包或第三方重打包 APK。
- 模拟器只能验证界面与鼠标/触摸输入；真实压感、笔键和防误触需真机复验。
- 登录、支付或会员门禁不绕过；无法进入的功能保留门禁证据并标记受阻。

## 参考

- Android Emulator 30.0.0 ARM 支持说明：https://developer.android.com/studio/releases/emulator#emulator-30-0-0
- Emulator 架构与加速限制：https://developer.android.com/studio/run/emulator-acceleration
- Google APIs 系统镜像官方清单：https://dl.google.com/android/repository/sys-img/google_apis/sys-img2-1.xml
