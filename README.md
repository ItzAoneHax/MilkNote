# MilkNote

> 为小米平板 + 小米焦点触控笔打造的低延迟手写笔记应用,内置 Agent 框选讲解。

设计文档见 [docs/](docs/):[DESIGN-V0.md](docs/DESIGN-V0.md)(总体) · [PEN-ADAPTATION.md](docs/PEN-ADAPTATION.md)(焦点笔适配) · [AGENT-BACKEND.md](docs/AGENT-BACKEND.md)(Agent,M2) · [PENENGINE-APPLY.md](docs/PENENGINE-APPLY.md)(SDK 申请,前瞻)。

## 模块

| 模块 | 职责 |
|---|---|
| `:app` | Compose UI:①书架 ②新建弹窗 ③书写页 + 笔诊断面板 + 导出 |
| `:ink-engine` | 笔迹引擎:采集/压感映射/变宽渲染/protobuf 序列化/纸面 |
| `:note-domain` | 笔记本域:`.milk` 目录格式(notebook.json + p*.pb 追加流) |
| `:device-mi` | 焦点笔适配:PenInputPort / 手势判定(双击350/长按400) / 防误掌 / 按键映射 / 事件流水 |

## 构建

```bash
./gradlew :app:assembleDebug        # APK → app/build/outputs/apk/debug/
./gradlew test                      # 单元测试(压感曲线/proto roundtrip/手势判定/页面状态)
```

要求:JDK 17+,Android SDK(API 35)。国内网络已按阿里云镜像解析(`~/.gradle/init.d/mirrors.gradle`)。

## 当前状态(M1 书写核心)

已交付:变宽压感笔迹(S-γ 曲线 + EMA)、批次点展开、免缓冲派发、MotionPredictor 预测尾巴、
整笔划橡皮、撤销/重做(含落盘镜像)、`.milk` 追加流持久化、书架/新建/书写三屏、笔诊断面板(P1 事件流水)、PNG/PDF 导出。

进行中:FrontBufferedRenderer 前置缓冲出墨(当前为 Canvas+Picture 路径)、蓝牙笔电量 GATT、
Pro 笔触控区(PenEngine SDK 反射壳已就位)。

真机 P1 验证:装 debug 包 → 长按顶栏 🪶 电量胶囊打开「笔诊断」→ 按 [PEN-ADAPTATION §7](docs/PEN-ADAPTATION.md) 表格逐键操作并截图流水。
