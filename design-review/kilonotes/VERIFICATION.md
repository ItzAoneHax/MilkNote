# MilkNote 融合实现验证

日期：2026-11-10（跨日连续实测）

## 构建与自动化测试

最终执行：

```bash
./gradlew.bat test :app:assembleDebug --console=plain
```

结果：`BUILD SUCCESSFUL`。测试 XML 中有 **285 个唯一 JVM 用例**（Debug/Release 合计 570 次执行记录），`failures=0`、`errors=0`。

覆盖包括：

- 旧 `.milk` 缺省 `pageOrder`/`lastOpened`/`paperColor`/`pageSpecs` 回退，重命名、标签、文件夹、打开时间和页面复制/删除。
- 卡片 JSON 兼容、CRUD 持久化、标题/正文搜索、标签/无标签/颜色筛选、确定性排序、空结果与损坏恢复。
- ZIP 有效恢复、绝不覆盖、路径穿越/绝对路径/重复条目/重复页面 ID/损坏 pb/批注/图片/媒体 sidecar/异常页面尺寸/条目与体积限制。
- Agent 附件路径 containment 与 `..` 拒绝；恢复后 Agent 会话重写为新笔记本 ID。
- PDF 文件名、pt→mm、页面方向、渲染尺寸与长边限制。
- 工具再次点击策略、中央面板互斥、橡皮档位、图案笔/激光、套索筛选、文字再编辑撤销/重做、扩展图形 proto 往返。
- 媒体模型兼容、混合选择/变换/撤销/重做、media sidecar 页面复制/清除/删除、录音权限拒绝与开始/停止状态机。
- 页面导航、导出文件名/范围、页面清除与既有 Agent/设备测试。

## 模拟器环境

- 设备：`kilonotes_x86` / `emulator-5554`
- API 35，Google APIs x86_64 + ARM64 翻译
- 2560×1600、320dpi、zh-CN；主要流程在横屏实测，另切换系统暗色验证中央面板。
- 安装命令返回 `Performing Streamed Install / Success`。
- 未重新下载或安装来源不明的 APK；既有千本笔记仅用于参考，不作为 MilkNote 的验收结果。

## 本轮模拟器实测

| 项目 | 实测结果 | 证据 |
|---|---|---|
| 书架与底部导航 | 书架卡片、“笔记 / 卡片”正常显示 | `milknote-fusion-home.png` |
| 创建动作面板 | 笔记本/导入/图片/拍照居中；返回关闭正常 | `milknote-fusion-create-actions.png` |
| 新建笔记本 | 创建 `Fusion QA`；田字格 + 浅绿纸色实际生效 | `milknote-fusion-new-page.png`（早期）与 `milknote-fusion-writer.png` |
| 工具再次点击 | 当前钢笔再次点击打开中央设置；橡皮再次点击打开三档大小并选择 9mm | `milknote-fusion-pen-settings.png` / UI Automator |
| 图形 | 八类图形显示；实际绘制星形，侧栏缩略图同步 | `milknote-fusion-shapes.png`、`milknote-fusion-star.png` |
| 文字 | 插入 `Stylednote`；36px、红色、粗体、居中、下划线渲染 | `milknote-fusion-text.png` |
| 文字再编辑 | 点选卡片→编辑文字，恢复原内容和样式 | UI Automator `EditText=Stylednote` |
| 套索/选区 | 混合选区操作条、旋转柄、调层/吸附/删除/取消均可见；类型筛选由 JVM 覆盖 | `milknote-fusion-lasso.png` |
| 本地素材 | 胶带 1 实际插入；PNG 与 `.img.json` 写入 | `milknote-fusion-tape.png` + `run-as find` |
| 激光笔 | 手势前后 `p0001.pb` 均为 226 bytes；再次点击打开“不落盘”设置 | adb stat + UI Automator |
| 阅读模式 | 辅助侧栏/竖轨隐藏，主工具岛保留，纸面 bounds 不变 | `milknote-fusion-reading.png` |
| 专注模式 | 全部主 chrome 隐藏，仅保留明确退出；返回恢复 | `milknote-fusion-focus.png` |
| K 操作台 | 命令面板→K 操作台；内容/画笔/笔记本/页面控制可用 | `milknote-fusion-console.png` |
| 页面旋转 | 当前页从 250×176mm 变为 176×250mm，写入 `pageSpecs.p0001` | `notebook.json` 实测 |
| 卡片 CRUD | 新建 `Review Card` / `Remember` / `#study`；编辑、删除确认后取消正常 | UI Automator |
| 卡片搜索/筛选 | 无匹配空态；恢复 `review` 后显示；蓝色筛选为空、红色筛选恢复 | UI Automator |
| 卡片回插 | 选择 `Fusion QA` 第 1 页；`p0001.ann.json` 新增 `Review Card\n\nRemember` | 设备内 sidecar 实测 |
| 摘保存卡 | 框选“存卡片”生成 `页面摘取 p0001`、来源 ID 和真实 PNG | `files/cards/cards.json` + `cards/assets/*.png` |
| 笔记本管理 | 重命名为 `Fusion QAManaged`，标签 `#D2483C`，移动到 `QA`；卡片同步显示 | `notebook.json` + UI Automator |
| 排序 | “上次打开”不再禁用；名称/修改时间/标签同面板可选 | UI Automator |

## 实际文件与恢复闭环

### ZIP 备份 → 删除 → 导入恢复

1. 验收本创建第 2 页，UI 从 1/1 → 2/2。
2. 导出 `Fusion QA.milk.zip` 到 `文档/MilkNote`，7797 bytes。
3. 拉取到 `verification/FusionQA.milk.zip`，`ZipFile.testzip() = None`。
4. ZIP 含 `notebook.json`、`p0001.pb`、`p0002.pb`、批注/图片 sidecar 和共享素材 PNG。
5. 通过管理面板二次确认删除测试目录 `ba1a08db.milk`；设备列表中该目录消失，原 `d2658deb.milk` 未受影响。
6. 经 SAF 选择同一备份；恢复为新 ID `e150deb4.milk`，显示 2/2。
7. 恢复目录含两页、原 `Stylednote`、回插的 `Review Card` 和共享素材 PNG；没有覆盖已有笔记本。

注意：当前 DocumentsUI 在文档目录的 MIME 过滤下未显示 ZIP，因此导入过滤器加入 `*/*`，实际文件从系统“下载”目录选择；应用仍严格校验格式，不因扩大可见文件范围而接受非 MilkNote ZIP。

### PDF 导入与再批注

- 通过 SAF 导入既有 `未命名笔记.pdf`，生成 `0991daa6.milk`，页序 `p0001/p0002/p0003`，UI 显示 3/3。
- 每页 `PageSpec` 为约 249.77×175.68mm；`images/pdf/p0001.png`、`p0002.png`、`p0003.png` 均真实存在。
- 当前页添加文字批注 `PDF note`，写入 `p0003.ann.json`；导入页可继续编辑。
- 全页图片优先响应手指选择；真实笔书写仍走既有笔/手指分流。本次未用模拟器宣称验证真实压感。

### 录音与视频

- 录音入口先弹系统麦克风权限；选择“不允许”后显示解释和“打开系统设置”，未开始采集。
- 在测试模拟器显式授权后，前台开始/停止并插入；生成 `media/audio-*.m4a` 与 `p0003.media.json`。
- 选中音频→播放媒体→播放，按钮变为“暂停”；关闭面板释放播放器。
- 用模拟器 `screenrecord` 生成 92,569 bytes 的本地 `qa-video.mp4`，经系统选择器导入；生成 `VIDEO` MediaElement 与 notebook 内 MP4。
- 视频中央面板播放/暂停切换正常。媒体静态占位用于缩略图、导出和 Agent 快照。

### 含媒体导出

- 含上述录音、视频和 PDF 页面的整本导出显示“已导出 PDF（3 页）→ 文档/MilkNote”。
- 实际生成 `未命名笔记 (1).pdf`，87,832 bytes。

## 中央面板/主题

- 所有新增调整面板均使用屏幕中央 `MilkCenterPanel` 或既有中央组件；内容滚动、返回/外部关闭、IME 避让与 48dp 命中目标保持统一。
- 新增套索/素材/操作台/录音/视频/播放面板通过 `WriterPanelState` 互斥；选区条、Toast、翻页与框选条保留空间锚定。
- 系统暗色 + 横屏下的笔设置中央面板已截图验收：`milknote-fusion-dark-panel.png`。

## 受阻与限制（不伪造完成）

- **拍照成像受阻**：创建面板已调用系统 `IMAGE_CAPTURE`，但模拟器没有前/后摄像头；`com.android.camera2` 因空相机列表抛出 `ArrayIndexOutOfBoundsException`，系统还结束了当前任务。因此只验证入口，未验证照片生成。
- 图片创建入口已打开系统选择器，但本轮未选择图片生成新笔记本；不标记该子流程完整通过。
- 模拟器没有真实小米焦点笔；压感、笔键、防误触和真实书写延迟仍需真机复验。
- 媒体播放依赖系统 codec；无内置转码。视频整文件在 IO 线程复制，大文件会占用更多时间和存储。
- 导出图片/PDF 中的媒体是静态占位，不是视频帧或音频波形；共享媒体资产随 ZIP 备份。
- 页面“替换”、自动云备份、网盘、会员、账号、竞品 AI/商店和千本笔记 ZIP 未实现或明确不支持。

APK：`D:\Repositories\MilkNote\app\build\outputs\apk\debug\app-debug.apk`（最终构建 65,437,694 bytes）。
