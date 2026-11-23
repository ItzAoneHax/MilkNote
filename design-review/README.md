# MilkNote 设计审查交付索引

## 文件

- [AUDIT.md](AUDIT.md)：35 条问题，含 R2 触控复审、严重度、代码行与截图证据、动效盘点和用户一天走查。
- [REDESIGN.md](REDESIGN.md)：Quiet Layers R2.1；触控笔/手指优先，L0–L6 层次、Token、动效与手势归属表、12 项功能增强及验收标准。
- [WWDC24-ANIMATION-NOTES.md](WWDC24-ANIMATION-NOTES.md)：Session 10145 转写文稿提炼；共享缩放、稳定来源、速度继承、可中断过渡及 Compose 落地规格。
- [prototype/index.html](prototype/index.html)：无依赖独立高保真原型；文件头含预览说明。
- [screenshots/](screenshots/)：24 张 `milk_pad` AVD 实机渲染截图，全部经本人逐张查看。
- [competitors/](competitors/README.md)：7 款竞品笔记应用（Obsidian / SpeedyNote / StarNote / OneNote / Gemini Notebook / Goodnotes / 小米笔记）在 `kilonotes_x86` 平板模拟器上的逐屏截图、带编号的功能标注图、UI 层级与控件坐标表；功能对照见 [competitors/FEATURES.md](competitors/FEATURES.md)。

## 原型预览

直接打开 `prototype/index.html`，或在仓库根目录运行：

```bash
python -I -m http.server 4173 --directory design-review/prototype
```

访问 [http://127.0.0.1:4173](http://127.0.0.1:4173)。先点击页面使其获得焦点，再使用 Ctrl/⌘+K、↑↓、Enter、Esc。

## R2 触控复审修订

- 通用卡片/按钮不再使用 hover 上浮或淡底；笔尖 hover 只用于纸面光标/笔刷预览。
- 笔与手指共用默认 48dp 触控目标；命令行 52dp；框选角柄使用 24dp 半径而非固定 30px。
- 单击控件不注册空双击回调；专注模式使用显式按钮，不把双击纸面作为主路径。
- ⌘K 触控打开不预选，键盘打开/方向键导航时才显示选中焦点。
- 增加 Agent 左缘调宽与头部滑动关闭的归属约束，手势不侵入书写画布。

## 已完成的验证

### 主 App（AVD）

- `:app:assembleDebug` 构建、安装、启动成功。
- 覆盖书架、新建、书写、框选初始/确认、Agent、附件、模型面板、设置五页、笔诊断、亮暗主题。
- 实测确认：暗色诊断低对比、系统栏不随手动深色、封面标签低对比、卡片不等高、新建/⌘K 过宽、框选初始无引导、尺寸标签遮角柄。
- Mock 配置仅用于本地联调；连接测试只得到笼统失败，未据此宣称主 App 的流式性能通过。Mock 地址已恢复，服务已停止，主题已恢复浅色。
- 未验证真实焦点笔、真机 GPU 阴影/帧率或实际书写延迟；相关结论均标为代码推演或待真机复核。

### 独立原型

- JavaScript 语法检查通过；浏览器运行错误为 0。
- 新建弹窗打开、名称实时预览、取消关闭通过。
- 书架三张卡布局测量均为 259px 高。
- Ctrl+K → ↓ → Enter 进入书写页并显示 Agent 回答通过（需页面焦点）。
- 暗色、减弱动效、附件多选与发送门禁通过。
- 专注模式前后纸张 `x/y/width/height` 完全一致。
- 框选拖动生成 330×350 选区并显示意图条；发送后关闭框选、打开 Agent 和回答。
- R2 复测：原型无 `:hover`；书写顶栏框选/专注按钮实际均为 48×48，意图按钮 48px 高；框选拖动保持通过。
- R2 输入方式检查：键盘打开 ⌘K 有 1 个选中项，触控打开为 0；命令行 CSS 最小高度 52px。
- 内置浏览器的截图表面两次超时，因此**没有声称完成原型截图视觉验收**；交互通过 DOM/坐标真实输入与状态/几何断言验证。

## 红线

本次审查与 WWDC 研究没有修改主 App 源码，也没有把原型接入主工程。注意：2026-11-03 检查时工作区已存在其他任务产生的 `app/src` 改动；它们不属于本次交付，未被我回退或覆盖。
