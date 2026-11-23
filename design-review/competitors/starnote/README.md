# StarNote 1.5.4.1 / StarNote笔记 (Android, ONYX) 实测档案

> 目的：记录 StarNote（国内名「StarNote笔记」，ONYX/文石出品）Android 平板端的页面与行为，作为 MilkNote 的对照基准。国内版 `com.onyx.galaxy.note` 与国际版 `com.onyx.galaxy.global.note` 为同一应用，本次实测国际版。

## 安装包与来源

| 项目 | 实测值 |
|---|---|
| 包名 | `com.onyx.galaxy.global.note` |
| 官方来源 | http://starnote.ai/download 直链 `https://static-us.boox.com/galaxy/apks/starnote_global_latest.apk`（BOOX CDN） |
| 下载日期 | 2026-11-23 |
| APK 大小 | 457,199,800 bytes |
| APK SHA-256 | `7a51b7a6426855fec946e3530c561121f18f24b1b6d064f8b8d8103fb0acb00c` |
| 版本 | `1.5.4.1` (`versionCode=5706`) |
| minSdk / targetSdk | 29 / 36 |
| 原生 ABI | `arm64-v8a` `armeabi-v7a` |
| 启动 Activity | `com.onyx.galaxy.global.note/com.onyx.galaxy.note.ui.activity.SplashActivity`（注意类名不含 `global`） |
| 签名主体 | `C=CN, ST=Guang Dong, L=Guang Zhou, O=Onyx International, OU=Onyx International, CN=Zeng Zhu` |
| 签名证书 SHA-256 | `c54552804534f876822a77290eecee02413ffc9a6d93c9b51b953549e667302f` |

**包源说明：** APKPure 的 `com.onyx.galaxy.global.note@arm64-v8a` 包实际只含 `config.armeabi_v7a` 分包（纯 32 位），在 abilist32 为空的 x86_64 模拟器上无法安装；最终改用官网直链的通用包（含 arm64）。

## 实测环境

- AVD `kilonotes_x86`：Android 15 (API 35)，2560×1600 横屏，320 dpi，zh-CN。
- 首启进入沉浸式全屏（无状态栏），系统会弹「全屏模式」提示。
- 编辑器画布为自绘（无无障碍节点），工具栏图标的语义按外观与后续弹层推断，已在标注中注明。

## 走查观察

- 协议弹窗后直接进入书架，无账号要求；首次新建时弹「注册后可网盘备份」的软提示，可关闭继续离线使用。
- 书架：左侧分类树（搜索 / 全部笔记 / 未分类 / 未标签 / 回收站 / 文件夹 / 标签），顶部 全部/笔记/PDF 筛选，右上 网格/排序/多选/云同步；卡片为封面缩略图 + 标题 + 日期 + 「∨」单本菜单（编辑 / 复制 / 置顶 / 更换封面 / 导出并分享 / 标签 / 移动至 / 添加锁 / 云同步 / 移到回收站）。
- 免费版配额：已有 3 本（含回收站）剩余可新建 5 本，底部常驻 Pro 横幅；预置 3 本示例笔记本（Quick Start Guide / Planner / Cornell Book）。
- 「新建」为虚线卡片，轻点两下创建「快捷笔记」并自动**双开**（左右分屏，共用工具栏）；提示「在顶部标题栏选择笔记，长按并下拉即可双开，同一本也可双开」。
- 编辑器顶栏 12 个入口：返回 / 云同步 / 拍照 / 截图 / 录音 / 阅读模式 / 文字面板 / 图层 / 添加页面 / 文档概览 / 设置 / 更多；工具栏可长按拖拽吸附到上下左右四侧。
- 工具栏 17 个工具：撤销重做、钢笔、铅笔、荧光笔、套索、图片、文本框、形状、参考线、图层、贴纸、标签、高亮、激光笔、放大镜、表格 + 5 色 + 自定义色 + 3 档粗细。
- 钢笔设置面板：5 种笔头实时预览、压力灵敏度、粗细（mm）、笔画稳定、颜色、「添加到笔盒」。
- 「更多操作」：全屏放大 / 页面留白调整 / 文档概览（平铺）/ 导出文档 / 导入音频 / 计时器 / 添加锁 / 笔记概览 / 自定义快捷栏。
- 「其他设置」：文档语言、标题、标签、更换模板、更换新增页模版、页面留白调整、跳转页面、添加至大纲、创建页面链接、旋转当前页、删除当前页 / 已删除页面。
- 全局设置 10 类：云同步（Google Drive / OneDrive，自动同步与仅 Wi-Fi）、本地备份、网盘备份、主题、文档、工具（一笔成型 / 压感 / 图形填充 / 长按选文本 / 长按唤起创建菜单）、手写笔（按华为/OPPO/vivo/小米焦点笔/联想/三星 S Pen 逐项映射双击、长按、主副键、轻捏、上下滑）、手势（双指/三指/单指双击/手掌防误触）、StarNote AI、隐私。

## 文件

- [PAGES.md](PAGES.md)：15 页带编号标注的截图 + 图例 + 控件坐标。
- `screenshots/`：15 张原始 PNG；`annotated/`：15 张标注图；`ui/`：UI 层级 XML；`controls.csv`：188 行；`annotations.json`：标注定义。

## 状态

- [x] 官方 APK 下载、签名核对、安装、首启、书架、编辑器、钢笔面板、更多操作、文档概览、页面设置、全局设置 4 页、卡片菜单、新建与双开
- [ ] 实际书写 / 形状识别 / OCR / AI / 导出 / 录音（未走查）
