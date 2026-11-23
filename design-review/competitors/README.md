# 竞品笔记应用实测档案（2026-11-23）

> 目的：在同一个 Android 平板模拟器上安装 7 款笔记应用，逐屏拍照并标注功能，作为 MilkNote 的对照基准。格式沿用 [千本笔记实测档案](../kilonotes/README.md)：原始截图 + UI 层级 + 控件坐标表，并额外生成**带编号框与图例的标注图**。
> 原则：只记录实际安装并操作到的界面；登录墙、系统依赖、地区封锁导致的受阻项如实标注，不用商店宣传截图替代。

## 总览

| 应用 | 包名 / 版本 | 状态 | 原始截图 | 标注图 | 入口 |
|---|---|---|---|---|---|
| Obsidian | `md.obsidian` 1.15.1 | 完整走查 | 27 | 22 | [README](obsidian/README.md) · [PAGES](obsidian/PAGES.md) |
| SpeedyNote / 极疾记 | `org.speedynote.app` 1.6.3 | 完整走查 | 18 | 16 | [README](speedynote/README.md) · [PAGES](speedynote/PAGES.md) |
| StarNote / StarNote笔记 | `com.onyx.galaxy.global.note` 1.5.4.1 | 完整走查（官网官方包） | 15 | 15 | [README](starnote/README.md) · [PAGES](starnote/PAGES.md) |
| OneNote | `com.microsoft.office.onenote` 16.0.19127 | 登录墙（Microsoft 账户） | 2 | 2 | [README](onenote/README.md) · [PAGES](onenote/PAGES.md) |
| Gemini Notebook（原 NotebookLM） | `com.google.android.apps.labs.language.tailwind` 1.67.7 | 登录墙（Google 账户） | 3 | 2 | [README](gemini-notebook/README.md) · [PAGES](gemini-notebook/PAGES.md) |
| Goodnotes | `com.goodnotes.android.app` 1.0.31.0 | 登录墙 + CloudFront 地区封锁 | 4 | 3 | [README](goodnotes/README.md) · [PAGES](goodnotes/PAGES.md) |
| 小米笔记 | `com.miui.notes` 8.5.7 等 5 版 | 无法安装（`micloud-sdk` 系统依赖） | 0 | 0 | [README](xiaomi-notes/README.md) |

功能对照见 [FEATURES.md](FEATURES.md)。

## 测试环境

- AVD `kilonotes_x86`（`D:\Android\avd`）：Android 15 (API 35) `google_apis;x86_64`，2560×1600 横屏，320 dpi，zh-CN，`ro.product.cpu.abilist=x86_64,arm64-v8a`，`libndk_translation.so` 可用；**abilist32 为空，纯 32 位包无法安装**。
- 同时运行、未触碰：`milk_pad`（emulator-5554，自测用 AVD）。
- 工具：`apkeep 1.1.0`（cargo install）拉取 APKPure；`aapt` / `apksigner`（build-tools 34.0.0）核对包名与签名；`adb exec-out screencap` + `uiautomator dump` 截图；PIL 生成标注图。
- 网络约束：GitHub 直连 0 B/s，需走本机代理 `127.0.0.1:7890`；模拟器本身不走代理，因此 Google Play 服务与 web.goodnotes.com 不可达/被封锁。
- 输入约束：AVD 硬件键盘默认开启，软键盘需 `settings put secure show_ime_with_hard_keyboard 1`；Gboard 拼音会吞掉 `adb input text` 的英文，需点地球切英文或用回车提交。

## 目录格式（每个应用）

```
<app>/
├── README.md          来源 / 版本 / 签名 / SHA-256 / 环境 / 走查观察 / 状态
├── PAGES.md           逐页：标注图 + 图例 + 控件坐标
├── annotations.json   标注定义（编号、框、图例），可重跑
├── screenshots/       原始 PNG（2560×1600）
├── annotated/         标注图（左：缩放截图 + 编号框；右：图例，>20 项分两栏）
├── ui/                UI Automator 层级 XML
└── controls.csv       带 text/content-desc 的控件：包名、id、类型、像素/归一化坐标、截图路径
```

工具在 `_tools/`：

- `capture.sh <app_dir> <page_id> [device]`：截图 + UI dump + 打印带文本节点（已处理 Git Bash 的 `/sdcard` 路径转换）。
- `annotate.py <app_dir>`：由 `annotations.json` 生成 `annotated/*.png`。
- `controls.py <app_dir>`：由 `ui/*.xml` 生成 `controls.csv`。
- `pages.py <app_dir>`：由 `annotations.json` 生成 `PAGES.md`。

## 包源与签名核对

| 应用 | 来源 | 签名主体（apksigner） | 结论 |
|---|---|---|---|
| Obsidian | GitHub obsidianmd/obsidian-releases v1.15.1 | Dynalist Inc.（Obsidian 母公司） | 官方 |
| SpeedyNote | GitHub alpha-liu-01/SpeedyNote v1.6.3 | `CN=SpeedyNote` 开发者自签名 | 官方发行版 |
| StarNote | starnote.ai/download → static-us.boox.com | Onyx International | 官方 |
| OneNote | APKPure | Microsoft Corporation（Marketplace 签名） | 与 Play 一致 |
| Gemini Notebook | APKPure XAPK | Google Inc. | 与 Play 一致 |
| Goodnotes | APKPure | Google Inc.（Play 应用签名） | 与 Play 一致 |
| 小米笔记 | APKPure | Xiaomi（miui@xiaomi.com） | 官方，但不可安装 |

## 受阻项汇总

1. **登录墙**：OneNote、Gemini Notebook、Goodnotes 均需账户，且无离线入口。若提供测试账户并在模拟器上完成登录，可用同一套 `capture.sh` 继续走查。
2. **系统依赖**：小米笔记 5 个版本全部要求 `micloud-sdk`；建议连接小米真机（`device-mi` 对应设备）直接截图。
3. **地区封锁**：web.goodnotes.com 对本地区返回 CloudFront 403；Goodnotes 的 TWA 壳完全依赖该网页。
4. **32 位包**：APKPure 的 StarNote 分包只有 `armeabi-v7a`，模拟器 abilist32 为空；已改用官网 arm64 通用包解决。
5. **未走查**：三款完整应用的实际书写、压感、导出、OCR、AI 等需真实输入或网络的功能均未验证，详见各 README 的「状态」。

## 红线

- 未修改 MilkNote 源码；未清空或覆盖 `milk_pad` / `medium_phone`；未在 `kilonotes_x86` 上卸载千本笔记。
- APK 缓存位于系统临时目录 `%TEMP%\milknote-apks`（约 1.3 GB），不属于仓库；文档中记录了 URL、大小与 SHA-256，可随时重新下载。
- 未使用任何第三方重打包 / MOD APK。
