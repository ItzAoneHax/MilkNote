# SpeedyNote 1.6.3 / 极疾记 (Android) 实测档案

> 目的：记录 SpeedyNote（Qt/QML 跨平台手写笔记）Android 平板端的页面与行为，作为 MilkNote 的对照基准。仅记录实际操作到的界面。

## 安装包与来源

| 项目 | 实测值 |
|---|---|
| 包名 | `org.speedynote.app` |
| 官方来源 | https://github.com/alpha-liu-01/SpeedyNote/releases/tag/v1.6.3 （`SpeedyNote-1.6.3-arm64.apk`） |
| 下载日期 | 2026-11-23 |
| APK 大小 | 85,351,332 bytes |
| APK SHA-256 | `fac7dcc0add01badfda372322ae494083f867c48439fc224b358ec7da48d5a1e` |
| 版本 | `1.6.3` (`versionCode=7`)，关于页中文名「极疾记」 |
| minSdk / targetSdk | 26 / 35 |
| 原生 ABI | `arm64-v8a`（经 libndk_translation 运行） |
| 启动 Activity | `org.speedynote.app/.SpeedyNoteActivity` |
| 技术栈 | Qt 6.9.3（关于页） |
| 签名主体 | `CN=SpeedyNote, O=Personal, L=Mississauga, ST=Ontario, C=CA`（开发者自签名） |
| 签名证书 SHA-256 | `931d9a2000f9c17808b4a99455d645ae5cb505670cdc89cce1a1dab8a9951454` |

## 实测环境

- AVD `kilonotes_x86`：Android 15 (API 35)，2560×1600 横屏，320 dpi，zh-CN。
- Qt 应用的无障碍树完整，`controls.csv` 可逐按钮记录；但导航栏/工具栏上报的 y 坐标比实际触控位置偏大约 +41 px，点击时以截图坐标为准。
- 首启只请求「通知」权限，无引导页、无账号。

## 走查观察

- 首页极简：左侧「历史记录 / 收藏夹 / 搜索」三入口，右下悬浮「+」展开 4 项：导入 .snbx 文件包、注释 PDF、新的分页笔记本、新的无边笔记。
- 编辑器 = 青色导航栏（返回 / 保存 Ctrl+S / 新的文档 / + / 标题 / 全屏 / 分享 / Markdown 笔记 / ⋮）+ 单行工具栏 + 可滚动画布。
- 工具栏 17 个工具：钢笔（3 色预设 + 3 粗细预设，再点编辑）、记号笔、橡皮、直线、套索、图片、链接、文本框、文字选中、OCR、手形、撤销/重做、手势三态。
- 桌面化交互：快捷键完整（设置 › 快捷键 为可编辑树表），对话框为 Qt 风格（QColorDialog 等），颜色预设编辑器即标准 Qt 色板。
- 文档设置三页签（页面 / OCR / 主题）只覆盖本文档；全局设置 8 页签（页面 / 工具 / 快捷键 / 主题 / 语言 / 手写笔 / 缓存 / 关于）。OCR 支持中日韩逐格识别、条格线作分界。
- 防误触：笔悬浮即屏蔽触摸手势，恢复延迟 500 ms。
- Markdown 笔记侧栏与手写页面并排，可按页面范围筛选。
- 放大镜按钮实际是「搜索条」（PDF 文本 / 识别后手写检索）。
- 重要行为：编辑器内未按「保存」直接返回，笔记本不会进入历史记录，也没有任何提示。

## 文件

- [PAGES.md](PAGES.md)：16 页带编号标注的截图 + 图例 + 控件坐标。
- `screenshots/`：18 张原始 PNG；`annotated/`：16 张标注图；`ui/`：UI 层级 XML；`controls.csv`：391 行；`annotations.json`：标注定义。

## 状态

- [x] 官方 APK 下载、签名核对、安装、首启、新建、编辑器、工具栏、菜单、文档设置、全局设置 5 页、Markdown 侧栏、颜色编辑器、搜索条
- [ ] 实际书写、PDF 注释、.snbx 导入、图层、分享（未走查）
