# Microsoft OneNote 16.0.19127 (Android) 实测档案

> 结论：OneNote Android 必须登录 Microsoft 账户才能进入任何笔记界面；本次仅能记录隐私声明与登录页两屏，后续界面受阻。

## 安装包与来源

| 项目 | 实测值 |
|---|---|
| 包名 | `com.microsoft.office.onenote` |
| 来源 | APKPure（apkeep `-d apk-pure -o arch=arm64-v8a`），Google Play 原版签名包 |
| 下载日期 | 2026-11-23 |
| APK 大小 | 99,777,671 bytes |
| APK SHA-256 | `bde80a7bfacdbb487623117ab4f97c460809c90251e04dcffa9225396d21ef4f` |
| 版本 | `16.0.19127.20320` (`versionCode=1807747187`) |
| minSdk / targetSdk | 28 / 35 |
| 原生 ABI | `arm64-v8a` |
| 启动 Activity | `com.microsoft.office.onenote/.ui.ONMSplashActivity` |
| 签名主体 | `CN=Microsoft Corporation Third Party Marketplace (Do Not Trust), OU=Android Marketplace Signing for Microsoft Office, O=Microsoft Corporation` |
| 签名证书 SHA-256 | `b925136f3ea7c0a1951690a1ab313910da81f40994a85342ec622888f1287051` |

## 实测过程

1. 首启：微软「隐私声明」弹窗（不同意并退出 / 我同意 / 不要提醒我）。
2. 同意后直接进入登录页「欢迎使用 OneNote」，只有 邮箱/电话/用户名 输入 + 下一步；页面没有跳过、离线使用或本地笔记本入口。
3. 停在此处，未输入任何凭据。

## 文件

- [PAGES.md](PAGES.md)：2 页标注图。`screenshots/` 2 张，`annotated/` 2 张，`controls.csv` 9 行。

## 状态

- [x] APK 下载、签名核对、安装、首启两屏
- [ ] 登录后的笔记本 / 分区 / 页面 / 手写 / 绘图 / OCR / 设置（需 Microsoft 测试账户）
