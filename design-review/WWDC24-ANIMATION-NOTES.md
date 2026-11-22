# WWDC24 动画研究 → MilkNote 可用方案

> 来源：Apple Developer，《提升 UI 动画和过渡效果 / Enhance your UI animations and transitions》，WWDC24 Session 10145。  
> 原文：https://developer.apple.com/cn/videos/play/wwdc2024/10145/  
> 阅读范围：章节、简介与完整转写文稿。以下「Apple 原则」均有时间点；Compose 数值与 MilkNote 映射是本项目的适配设计，不是 Apple 原文规格。

## 1. 最值得采用的五条原则

### 1. 大型单元格 → 内容页：保持同一元素的缩放过渡

- **Apple 原则**：轻点的单元格放大为新视图；同一 UI 元素始终留在屏幕上，以增强连续感。过渡期间仍可持续互动，而不是变成不可触摸的动画截图（[1:34–2:07](https://developer.apple.com/cn/videos/play/wwdc2024/10145/?time=94)）。
- **MilkNote 映射**：书架笔记本封面/最近页缩略图 → 书写页纸张；返回时回到同一卡片。
- **边界**：只放大共享的**预览层**。真实 `InkSurfaceView` 在目标位置立即可写，不能对 front-buffer/GL 画布做缩放动画。

### 2. 来源用稳定身份，不持有临时 View 引用

- **Apple 原则**：缩放来源闭包在放大和缩小时都会运行；集合视图复用时，应捕获稳定模型标识，而不是直接捕获视图。内容在编辑器内变化时，要依据当前上下文找到正确来源（[3:28–4:06](https://developer.apple.com/cn/videos/play/wwdc2024/10145/?time=208)）。
- **MilkNote 映射**：共享键使用 `notebookId + currentPageId`；返回前重新查询当前卡片，不保存 Composable/View 引用。
- **降级**：卡片已删除、列表过滤后不可见、目标页改变时，退化为 140ms 交叉淡化，不缩放到错误对象。

### 3. 手势期间直接跟手，结束时保留速度

- **Apple 原则**：拖动手势持续更新位置；手势结束后的弹簧应继承手势速度。交互式弹簧可连续重定向，最后的非交互动画以连贯速度继续（[11:20–12:42](https://developer.apple.com/cn/videos/play/wwdc2024/10145/?time=680)）。
- **MilkNote 映射**：Agent 抽屉调宽/滑关、批注卡拖动、套索选中条出现前的对象拖动。
- **边界**：笔迹、框选矩形、套索路径、悬停光标只做输入映射，绝不在 move 事件中创建 tween/spring。

### 4. 过渡可中断，不用 `isTransitioning` 锁交互

- **Apple 原则**：过渡可能随时开始；不要在过渡运行时阻止 push。被中断的推送不是简单“取消并回滚”，而是完成当前状态后转换为反向过渡（[6:32–7:03](https://developer.apple.com/cn/videos/play/wwdc2024/10145/?time=392)）。
- **MilkNote 映射**：书架→书写途中点 BACK，直接从当前预览位置返回；Agent 滑入途中再次点收起，直接从当前宽度/位置反向，不排队等待。
- **状态要求**：ViewModel、Agent 流式、持久化与输入就绪不等待动画完成。

### 5. 少加临时状态；跨 UI 技术栈只让一个时钟负责

- **Apple 原则**：尽可能减少临时过渡状态；必须跟踪时在完成回调中复位（[7:03–7:49](https://developer.apple.com/cn/videos/play/wwdc2024/10145/?time=423)）。Representable 桥接可让 SwiftUI/UIView 动画同步（[10:37–11:20](https://developer.apple.com/cn/videos/play/wwdc2024/10145/?time=637)）。
- **MilkNote 映射**：`SnipPhase`、`WriterUiState` 等业务状态保留；不要额外引入 `isOpening/isClosing` 来禁用输入。Compose chrome 与 Native Canvas 若同时变化，共享同一开始时间/进度；更简单的是只动画其中一层，另一层立即更新。

## 2. 推荐动画清单（Compose 可落地）

| 优先级 | 场景 | 触控过程 | 抬起/完成后 | 建议规格 |
|---|---|---|---|---|
| P1 | 书架卡 → 书写纸面 | 点按立即响应；转场不锁书写 | 预览 sharedBounds 到纸面 bounds | `spring(dampingRatio=1f, stiffness=450f)`；通常约 220–280ms；无 overshoot |
| P1 | 书写 → 书架卡 | BACK/点返回立即接收；可从当前进度反向 | 回到当前 `notebookId` 卡片 | 同一 Animatable 当前值/速度反向；找不到来源则 140ms fade |
| P1 | Agent 左缘调宽 | `onDrag` 直接写 px 宽度，不动画 | 吸附 320/360/480dp，继承速度 | `Animatable.animateTo(..., spring(1f,500f), initialVelocity=px/s)` |
| P1 | Agent 头部右滑关闭 | 位移=手势位移；纸张不动 | 依速度/阈值决定关闭或回弹 | 关闭用 `spring(1f,550f)`；回弹不 overshoot |
| P1 | 批注卡拖动 | 卡片中心跟随笔尖/手指 | 合法位置直接落盘；无效位置继承速度返回 | 无磁性吸附装饰；`spring(1f,600f)` |
| P2 | 框选意图条 | 框选期间不出现 | 松开确认后 fade+8dp | 160ms；调整选区时不重播 |
| P2 | 新消息/选中条 | 输入与滚动不被锁 | 只播一次 fade/8dp | 120–180ms；流式 delta 不播放 |
| P2 | 模态/⌘K | 触控取消始终可用 | fade + 0.98→1 | 160–180ms；不做卡片级共享缩放 |

> 上表的时长是体验目标而非硬编码承诺；弹簧实际停止时间由当前位置与速度决定。减弱动效下取消共享缩放/位移，仅保留 ≤80ms 淡入淡出。

## 3. 明确不采用的内容

1. **不给笔工具、墨色点做缩放过渡**：它们是小控件，不是 Apple 所说的大型内容单元格。
2. **不给页面切换做共享缩放**：页侧栏切页是高频书写行为，直接换内容，仅选中色 160ms。
3. **不对真实画布做弹簧/缩放**：避免输入坐标与显示坐标短暂不一致，也避免 GL/front-buffer 合成风险。
4. **不逐 move 创建动画**：Apple 的交互弹簧原则在 Compose 中应转译为“拖动直接更新 + 结束时继承速度”，而非每个事件重启动画。
5. **不让转场决定数据生命周期**：Agent 流式、会话恢复、笔画提交和撤销不能等待过渡回调。
6. **不为炫技加入回弹**：笔记工具采用无 overshoot 弹簧；只有可拖动对象回到合法位置时才需要弹簧。

## 4. Compose 实现形态（示意，不接入主 App）

```kotlin
// 拖动中：只更新当前值；再次抓住时停止尾部动画，位置不跳。
scope.launch { widthAnim.stop() }
widthAnim.snapTo((widthAnim.value + dragDelta).coerceIn(minPx, maxPx))

// 抬起：VelocityTracker 提供 px/s；由同一 Animatable 继承当前值与速度。
scope.launch {
    widthAnim.animateTo(
        targetValue = chooseTarget(widthAnim.value, velocityPxPerSecond),
        animationSpec = spring(dampingRatio = 1f, stiffness = 500f),
        initialVelocity = velocityPxPerSecond,
    )
}
```

书架共享预览可用 Compose `SharedTransitionLayout` + `sharedBounds/sharedElement`，但需按当前 Compose 版本验证 API；预览层与真实画布分离。不要保存 Android View 作为来源，也不要在 `onDispose` 才恢复书写。

## 5. 验收标准

- **来源正确**：连续打开不同笔记本、在书写内切页再返回，始终回到当前卡片；删除后来源走 fade。
- **可中断**：进入/退出途中连续 BACK、再次点 Agent、重新抓住抽屉，位置不跳、不排队、不锁输入。
- **速度连续**：快速甩动抽屉后无先停再走；反向拖动立即接管。
- **书写优先**：转场第一帧起 InkSurfaceView 可写；输入坐标与页面坐标一致；front-buffer 不被 scale。
- **减少动效**：系统 Animator=0 或应用“减弱”时，无共享缩放/弹簧位移，功能状态完全一致。
- **真机验证**：Pad 8 Pro 记录过渡期间落笔、连续返回、Agent 快速开关；AVD 只验证结构与视觉，不给书写延迟下结论。
