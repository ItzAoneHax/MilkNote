# Goodnotes 1.0.31.0 (Android) 实测档案

> 结论：Goodnotes Android 是 Trusted Web Activity（PWA 壳），启动即要求 Google 登录，且网页端 web.goodnotes.com 对本地区返回 CloudFront 403。三重门禁叠加，本次仅记录到登录层与 403 页。

## 安装包与来源

| 项目 | 实测值 |
|---|---|
| 包名 | `com.goodnotes.android.app` |
| 来源 | APKPure（apkeep `-d apk-pure -o arch=arm64-v8a`） |
| 下载日期 | 2026-11-23 |
| APK 大小 | 4,974,444 bytes（无原生库；dex 9 MB + 本地 netty 服务 + WebView，符合 TWA/PWA 架构） |
| APK SHA-256 | `8943f5afa517c4ac82929f5721753cf4e7ae8fc7de9c50a6b600e2be752778a9` |
| 版本 | `1.0.31.0` (`versionCode=150943`) |
| minSdk / targetSdk | 24 / 34 |
| 原生 ABI | 无 |
| 启动 Activity | `com.goodnotes.android.app/.twa.LauncherActivity` |
| 签名主体 | `CN=Android, OU=Android, O=Google Inc., L=Mountain View, ST=California, C=US` |
| 签名证书 SHA-256 | `5a273bcf08f825b1d1c7195866f8bccbdbb11e72edc341dbcd3a3bf8c9cda35f` |

**包源说明：** APKPure 上 `100161693` 版本为坏包（版本名 1.0.0.0，仅阿语 + mdpi 分包，2.5 MB），`1.0.31.0` 的 5 MB 单包实为完整 TWA 壳。Goodnotes 官方支持文章只提供 Galaxy Store / Google Play / Microsoft Store，没有直链 APK，但明确说明其 Android/Windows 版为 PWA（「齿轮 › 关于 › 重启」即拉取网页更新）。

## 实测过程

1. 启动 TWA 后立即拉起系统 Google 登录卡片，报「出了点问题 / 请返回并重试」。
2. 返回后 TWA 校验失败，退回 Chrome Custom Tab 打开 `web.goodnotes.com`，先出现 Chrome 首启欢迎页（选择「在不登录账号的情况下使用」）。
3. 网页返回 `403 ERROR — The Amazon CloudFront distribution is configured to block access from your country`。
4. P003 为网页加载中的截图，未标注。

## 文件

- [PAGES.md](PAGES.md)：3 页标注图。`screenshots/` 4 张，`annotated/` 3 张，`controls.csv` 24 行。

## 状态

- [x] APK 下载、签名核对、安装、TWA 启动、登录层、CloudFront 403 证据
- [ ] 任何 Goodnotes 自有界面（需 Google 账户 + 非受限地区网络）
