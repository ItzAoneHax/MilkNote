# Obsidian 1.15.1 (Android) 实测档案

> 目的：记录 Obsidian Android 平板端在 2560×1600 横屏下的页面、入口路径与行为，作为 MilkNote 的对照基准。所有结论仅基于实际安装并操作到的界面；未走到的功能不标为已验证。

## 安装包与来源

| 项目 | 实测值 |
|---|---|
| 包名 | `md.obsidian` |
| 官方来源 | https://github.com/obsidianmd/obsidian-releases/releases/tag/v1.15.1 （`Obsidian-1.15.1.apk`） |
| 下载日期 | 2026-11-23（经本机代理，直连 GitHub 为 0 B/s） |
| APK 大小 | 14,971,407 bytes |
| APK SHA-256 | `577cd2e1289c00a6c11b230b7bc562029a116382cb2b9dea2a24b5bb7495836e` |
| 版本 | `1.15.1` (`versionCode=404`) |
| minSdk / targetSdk | 23 / 36 |
| 原生 ABI | `arm64-v8a` `armeabi-v7a` `x86` `x86_64`（本机以 x86_64 原生运行） |
| 启动 Activity | `md.obsidian/.MainActivity`（Electron/Chromium WebView） |
| 签名主体 | `CN=dynalist.io, OU=Unknown, O=Dynalist Inc., L=Waterloo, ST=Ontario, C=CA` |
| 签名证书 SHA-256 | `bd3bd52f4427b5ab7f8059d071a35e3de943c646546d32313da5f1cac254b7e4` |

## 实测环境

- AVD `kilonotes_x86`：Android 15 (API 35) `google_apis;x86_64`，2560×1600 横屏，320 dpi，zh-CN，`libndk_translation` 可用。
- 软键盘：AVD 默认 `hw.keyboard=yes`，实测时打开 `show_ime_with_hard_keyboard=1` 才能看到键盘上方的格式工具栏（P013）。
- 仓库创建在 `Documents/Research/`（系统 SAF 选择器），未触碰测试机上已有的 `Documents/MilkNote` 目录。

## 走查观察

- 首启无账号体系：仓库选择页只有「创建仓库 / 使用现有仓库」+ 语言切换；账户仅在「设置 › 关于」中出现，用于 Sync / Publish 等付费服务。
- 创建仓库流程 3 步：同步提示（可跳过）→ 命名 + 存储位置（设备存储 vs 应用存储）→ 「允许文件访问」应用内理由页 → 系统「所有文件访问权限」页 → 系统文件选择器选文件夹 → SAF 确认弹窗。
- 主界面是浏览器式标签页：新标签页 / + / × / 折叠；前进/后退是标签页历史；左右侧栏均为浮动面板（平板布局），右侧面板只有 反向链接 / 出链 / 大纲 三种。
- 移动端默认隐藏 Ribbon（功能区）；设置 › 界面 可打开，并可配置「快捷命令」下拉手势。
- 编辑器：内联标题（文件名即 H1）、默认「实时预览」模式、键盘上方的格式工具栏（21 个按钮，末尾 🔧 可自定义），键盘收起后工具栏仍停靠在底部。
- 「更多选项 ⋮」：阅读视图 / 源码模式 / 左右分屏 / 上下分屏 / 重命名 / 移动 / 收藏 / 合并 / 增加笔记属性 / 导出 PDF。
- 新标签页提供三种新建：笔记（Ctrl+N）、数据库（Bases）、白板（Canvas）。
- 命令面板在平板上未找到直接入口（下拉标题栏手势在模拟器上未触发），未验证。

## 文件

- [PAGES.md](PAGES.md)：22 页带编号标注的截图 + 图例 + 控件坐标。
- `screenshots/`：27 张原始 PNG（含未标注的 P004 / P007 / P013b / P014 / P021）。
- `annotated/`：22 张标注图（编号框 + 右侧图例）。
- `ui/`：UI Automator 层级 XML（Electron 节点可读）。
- `controls.csv`：1185 行带文本/描述的控件坐标表。
- `annotations.json`：标注定义（可重跑 `_tools/annotate.py`）。

## 状态

- [x] 官方 APK 下载、签名核对、安装、首启、仓库创建、主界面、侧栏、编辑器、工具栏、菜单、设置、阅读视图
- [ ] 命令面板、数据库、白板、分屏实际操作、第三方插件（未走查）
