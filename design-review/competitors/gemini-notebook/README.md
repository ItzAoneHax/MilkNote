# Gemini Notebook 1.67.7 (Android, 原 NotebookLM) 实测档案

> 结论：Gemini Notebook（2026-07 由 NotebookLM 更名）必须登录 Google 账户；测试 AVD 无 Google 账户且 Play 服务不可达，停在「正在核对信息…」。本次仅记录欢迎页与登录层。

## 安装包与来源

| 项目 | 实测值 |
|---|---|
| 包名 | `com.google.android.apps.labs.language.tailwind`（NotebookLM 时代的内部代号 tailwind，更名后未改包名） |
| 来源 | APKPure（apkeep `-d apk-pure -o arch=arm64-v8a`），XAPK：`base.apk + config.arm64_v8a.apk + config.xxhdpi.apk`，`adb install-multiple` 安装 |
| 下载日期 | 2026-11-23 |
| XAPK 大小 | 57,075,052 bytes |
| XAPK SHA-256 | `110fff61a1f49f0b55979754a2f8d572429fdced8468c26bff1ff362bbe5bbae` |
| 版本 | `1.67.7.1020541034` (`versionCode=174813`) |
| minSdk / targetSdk | 29 / 37 |
| 原生 ABI | `arm64-v8a` |
| 应用标签 | `Notebook` |
| 签名主体 | `CN=Android, OU=Android, O=Google Inc., L=Mountain View, ST=California, C=US` |
| 签名证书 SHA-256 | `ba49176908275f83be9ae1034968f0b18e65177a64e5a40b3a621f148dfb6fa2` |

## 实测过程

1. 首启欢迎页：品牌「Gemini Notebook」，主张「定格万象。AI 创想。」，底部卡片 Google 标识 + 唯一按钮「登录」。没有离线或跳过路径。
2. 点登录后弹出系统级 Google 登录卡片「正在核对信息…」，长时间停留（无账户、Play 服务不可达）。
3. P003 为同一状态的等待截图，未标注。

## 文件

- [PAGES.md](PAGES.md)：2 页标注图。`screenshots/` 3 张，`annotated/` 2 张，`controls.csv` 4 行。

## 状态

- [x] XAPK 下载、签名核对、安装、欢迎页、登录层
- [ ] 登录后的笔记本列表 / 来源管理 / 音频概览 / 对话（需 Google 测试账户）
