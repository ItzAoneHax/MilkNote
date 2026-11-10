# 千本笔记 Android 实测档案

> 目的：记录实际安装版本的页面、按钮位置、入口路径与行为，作为 MilkNote 融合迁移的对照基准。商店宣传与实测分开记录；未实际走到的功能不得标为已验证。

## 安装包与来源

| 项目 | 实测值 |
|---|---|
| 中文名称 | 千本笔记官方版 |
| 包名 | `com.topstack.kilonotes.pad` |
| 官方 APK 来源 | https://kn-sd.top-stack.net/notebooks/advert/73f5b9efd3554e7ca81115c15a9ae3bc.apk |
| 官方下载页 | https://www.kilonotesapp.com/en/download |
| 下载日期 | 2026-11-09 |
| APK 大小 | 128,791,910 bytes |
| APK SHA-256 | `8e1fea4bdff296e899a4aa78a8b3c1c2c0b230d9343496c52854954e38cf4caf` |
| 实测 APK 版本 | `3.23.0` (`versionCode=3646`) |
| minSdk / targetSdk | 23 / 35 |
| 原生 ABI | `arm64-v8a`, `armeabi-v7a` |
| 启动 Activity | `com.topstack.kilonotes.base.splash.SplashActivity` |
| 签名主体 | `CN=Top Stack, OU=01, O=01, L=Hefei, ST=Anhui, C=86` |
| 签名证书 SHA-256 | `f33f11c8065d7206e97eb5ca32870b40eef402d21e75c7d366f0a3023c13e8d7` |

**版本说明：** 公开商店页面在调研时显示 3.36.x，但官方下载页实际直链交付的是 3.23.0。本档案只把实际安装并操作的版本作为界面实测依据。

## 档案格式

- `screenshots/`：按页面 ID 和状态命名的原始 PNG。
- `ui/`：对应页面的 UI Automator XML 文本与控件 bounds。
- `controls.csv`：逐按钮记录入口、位置、行为、结果与验证状态。
- `pages/`：逐页文字说明与截图索引。
- `MIGRATION-MATRIX.md`：千本笔记到 MilkNote 的迁移、融合和中央面板改造矩阵。

位置记录同时使用原始像素 bounds 与相对于屏幕宽高的归一化值。模拟器无法验证真实压感、防误触或硬件笔键时，必须明确标记“仅验证界面/鼠标输入”。

## 实测索引

- [首启与引导](pages/01-STARTUP.md)
- [首页、创建与笔记本管理](pages/02-HOME.md)
- [编辑页工具](pages/03-EDITOR-TOOLS.md)
- [编辑页面板、导出与 AI](pages/04-EDITOR-PANELS.md)
- [卡片与商店](pages/05-CARDS-STORE.md)
- [迁移矩阵](MIGRATION-MATRIX.md)
- [模拟器兼容过程](ENVIRONMENT.md)
- [MilkNote 实现与验证](VERIFICATION.md)

本轮形成 P001–P069 编号状态，主要页面均有 PNG；语义可用的控件已导出到 `controls.csv`，自绘图标与无响应状态在逐页文档中单独说明。P064 的 K 状态受前序操作干扰，已由 P066–P069 重新实测更正。

## 状态

- [x] 官方 APK 下载与包名、版本、签名、ABI 检查
- [x] 兼容模拟器安装、中文版首启与主要路径实测
- [x] 迁移矩阵与中央面板改造对照
- [x] MilkNote 中央面板基础设施、现有生长式面板替换、JVM 单测
- [x] 本轮本地可落地融合功能与最终回归验收（拍照成像、真机笔能力等限制见 VERIFICATION.md）
