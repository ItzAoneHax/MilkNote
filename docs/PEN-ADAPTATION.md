# 小米焦点触控笔适配开发文档

> **定位**:`device-mi` 模块的输入适配规格 —— 笔按键功能(实体键 + Pro 触控手势)与书写压力检测。对应 DESIGN-V0 §3「小米焦点触控笔深度适配」,M1 交付项「笔键/双击、压感/倾斜」的实现依据。
>
> - 版本:A3(2026-11-01,P1 部分回填:侧键投递形式真机确证,见 §4.1 实测块)
> - **实际设备**:主力机 小米平板 8 Pro + **焦点触控笔(一代,三实体键)**;备用机 小米平板 7 Pro;无焦点触控笔 Pro
> - 状态:P0 已通过;P1 侧键通路已确证(PAGE_UP/PAGE_DOWN),焦点键确认不到应用;P2-P3 待复测
> - 关联:[DESIGN-V0.md](./DESIGN-V0.md) §2.2 模块划分 / §3 深度适配 / §8 风险

> ⚠️ **命名与兼容性说明(重要)**
> 1. 小米官方产品线:灵感触控笔 → 灵感笔二代(平板 5/6 系)→ **焦点触控笔(一代,2024.10,三实体键,官方适配平板 6S Pro/7 系)** → **焦点触控笔 Pro(2025.9,无实体键触控区,官方适配平板 8 系及 6S Pro/7 系)**。市面俗称「二代焦点笔」多指 Pro;本项目主力笔为**一代**。
> 2. **一代笔 × 平板 8 Pro 不在官方适配列表内**(平板 8 系官方配 Pro 笔),但用户已实测(2026-10-31):**千本笔记(已适配小米笔的第三方应用)在 Pad 8 Pro 与 Pad 7 Pro 上行为完全一致**——配对、书写、压感、第三方应用的按键适配均正常,主力机基线确立。理论风险仅剩系统大版本 OTA 变更;应用级事件细节(button 位/keyCode/拦截边界)仍以 §7-P1 诊断面板抓取为准。

---

## 1. 目标硬件与能力基线

### 1.1 小米焦点触控笔(一代,主力配件)

官方适配小米平板 7 / 7 Pro、平板 6S Pro 12.4(与主力机 Pad 8 Pro 的搭配见上方兼容性说明)。关键规格:

| 特性 | 规格 | 对 MilkNote 的意义 |
|---|---|---|
| 压感 | **8192 级**,经系统归一化为 `pressure ∈ [0,1]` | 笔宽动态映射、笔锋表现(§3) |
| 延迟 | 官方标称 3ms(需蓝牙连接的低延迟模式) | 配合 front-buffer 出墨管线 |
| 触控力度 | 0g(笔身与屏幕呈 90°) | `ACTION_DOWN` 即有非零压力,无死区启动问题 |
| 笔尖 | 可更换,内置备用笔尖 | — |
| 连接 | 长边磁吸:无线充电 + 蓝牙配对;闲置休眠需重新磁吸唤醒 | 电量上报(DESIGN-V0 §3.3) |

**三颗实体键及系统默认行为**(据小米官方 FAQ,系统级行为发生在笔未接触屏幕或系统应用内):

| 按键 | 系统默认行为 | MilkNote 计划 |
|---|---|---|
| **主键**(书写键,拇指位) | 书写中按下:在系统应用内切换画笔;长按+触屏:全局唤起「小米画板」 | 书写中按下 → `BUTTON_STYLUS_PRIMARY`(§4.1),应用内自定义 |
| **副键**(截图键) | 长按+轻点屏幕:全局/局部**系统截图**;小米画板内切换颜色 | 书写中按下 → `BUTTON_STYLUS_SECONDARY`,应用内自定义 |
| **焦点键**(笔身顶部) | 按下弹画笔图标/再长按圈注;双击清屏;长按=虚拟激光笔;相机内拍照/录像 | 长按虚拟激光笔与 DESIGN-V0「讲课模式」天然契合;投递方式待真机验证(§7-P1) |

### 1.2 小米焦点触控笔 Pro(前瞻适配,本项目无此硬件)

一体化无实体键笔身,笔身**触控区(Touch Film)**手势:轻捏、双击、上滑、下滑。手势**不直接投递给应用**,需接入小米手写笔引擎 SDK 的触控按键能力(HyperOS 3.0+,详见 §4.2 / §5)。官方适配平板 8 系及 6S Pro/7 系——即用户日后补购 Pro 笔,两台在用平板都能用。

### 1.3 能力总览矩阵

| 能力 | **一代笔 + Pad 8 Pro(主力机,HyperOS 3.0+)** | 一代笔 + Pad 7 Pro(备用机) | Pro 笔 + 平板 8 系(前瞻) | 通用触控笔(USI 等) | 手指 |
|---|---|---|---|---|---|
| 压感(8192 级归一) | ✅ P0 已验证(与 7 Pro 一致) | ✅ 标准通路 | ✅ 标准通路 | ✅(级数视硬件) | ❌(0/1 二值) |
| 倾斜 | ✅ P0 已验证 | ✅ `AXIS_TILT` | ✅(侧旋另有 SDK 增强,§5.4) | 视机型 | ❌ |
| 悬停光标 | ✅ P0 已验证 | ✅ `ACTION_HOVER_*` | ✅(+SDK 悬浮预览) | 视机型 | ❌ |
| 实体键(书写中) | ✅ 预期可用(千本笔记已适配佐证),P1 抓取细节 | ✅ 预期可用,P1 验证 | ❌(无实体键) | 视机型 | ❌ |
| 触控区手势(SDK) | ❌(笔无触控区) | ❌ | ✅(仅 SDK 通路) | ❌ | ❌ |
| 笔迹预测增强(SDK) | ✅ 机型满足(大陆+鉴权) | ✅ 机型满足 | ✅ | — | — |

> P0 结论(2026-10-31,用户实测):千本笔记在 Pad 8 Pro 与 Pad 7 Pro 上行为完全一致——主力机基线确立,「非官方组合」的实际风险已排除,仅保留系统 OTA 变更的理论风险。第三方应用可获得按键功能(千本笔记已适配为佐证),但**投递形式(button 位 / keyCode / 系统拦截边界)仍需 P1 用本项目诊断面板抓取**。

---

## 2. 输入通路总体架构

笔输入分**三条通路**进入应用,全部收敛到 `device-mi` 模块的 `PenInputPort`(DESIGN-V0 §2.2 的接口化要求):

```mermaid
flowchart LR
  subgraph HW[硬件 / 系统]
    D[数字化器<br/>240Hz 笔报点] -->|MotionEvent 流| M1[通路①: 触摸事件<br/>主通路·一代笔]
    B[蓝牙 HID<br/>悬停按键] -->|KeyEvent| M2[通路②: 按键事件<br/>主通路·一代笔]
    S[小米手写笔引擎 SDK<br/>PenEngine 0.3.2] -->|语义 function| M3[通路③: 触控按键<br/>仅 Pro 笔·前瞻]
  end
  M1 --> P[PenInputPort 实现<br/>device-mi]
  M2 --> P
  M3 --> P
  P -->|pressure/tilt/坐标| I[ink-engine 笔迹管线]
  P -->|button/hover| G[PenGestureDetector<br/>单击/双击/长按判定]
  P -->|function(Pro)| G
  G --> R[PenActionRouter<br/>映射表·可自定义]
  R --> A1[切换笔⇄橡皮] & A2[套索] & A3[框选截图] & A4[激光笔] & A5[...]
```

接口骨架(路径 `device-mi/src/.../pen/PenInputPort.kt`):

```kotlin
/** 换笔/换机型不动核心:ink-engine 只依赖此接口 */
interface PenInputPort {
    val capabilities: PenCapabilities   // 压感/倾斜/悬停/键位数/触控区,运行时探测(§3.2)

    fun attach(view: View)              // 注册事件分发挂点
    fun detach()

    /** 原始输入流 → 笔迹管线(坐标、pressure、tilt、时间戳,含历史批次点) */
    val inkEvents: Flow<InkPoint>

    /** 判定完成的笔手势/按键语义(单击、双击、长按、Pro 轻捏等) */
    val penActions: Flow<PenTrigger>

    /** 应用内自定义映射(PenActionRouter 用,§4.4) */
    var mapping: Map<PenTrigger, PenAction>
}
```

三条通路的挂点:

| 通路 | 挂点 | 说明 |
|---|---|---|
| ① MotionEvent | `View.dispatchTouchEvent` / Compose 外层 `AndroidView` | 压感、倾斜、接触时按键、悬停全在此;**必须先于手势系统消费** |
| ② KeyEvent | `Activity.dispatchKeyEvent` | 悬停态按实体键,`KEYCODE_STYLUS_BUTTON_*`(API 34+) |
| ③ 小米 SDK | `Activity.dispatchKeyEvent` 转发 + 独立回调 | `MiuiTouchFilmUtils`,焦点笔 Pro 专用(§4.2);本项目无 Pro 硬件,按文档实现、真机待验 |

---

## 3. 书写压力检测

### 3.1 API 语义(标准通路,无厂商 SDK 依赖)

压感**不走小米 SDK**(SDK 0.3.2 无压感能力),完全使用 Android 标准 API:

- `MotionEvent.getPressure(pointerIndex)`:归一化到 `[0.0, 1.0]`,1.0 = 「正常书写压力」。
  官方**不保证**值域:个别设备可能 >1(重压)或抖动越界,读取后统一 `coerceIn(0f, 1f)` 防御。
- 悬停事件(`ACTION_HOVER_*`)中 pressure 恒为 0,**不得**计入笔迹。
- `TOOL_TYPE_ERASER`(倒置笔/橡皮头)时照样有压感,语义为「擦除力度」(轻擦小范围、重擦大范围,M2 可做)。
- 手指 `TOOL_TYPE_FINGER` 多数机型为二值(0 或 ~1),判定 palm-rejection 时一并拒收(§6)。

### 3.2 能力探测(运行时,决定降级档位)

```kotlin
data class PenCapabilities(
    val pressure: Boolean,      // AXIS_PRESSURE 分辨率 > 0
    val tilt: Boolean,          // AXIS_TILT range 非空
    val hover: Boolean,         // SOURCE_CLASS_POINTER 且有 hover motion range
    val stylusButtonCount: Int, // AXES/按键位探测 + 真机日志(§7)
    val touchFilm: Boolean,     // 小米 SDK isFeatureEnable(Pro)
)

fun probe(context: Context, event: MotionEvent): PenCapabilities {
    val dev = InputDevice.getDevice(event.deviceId) ?: return NONE
    val stylus = InputDevice.SOURCE_STYLUS
    val pRange = dev.getMotionRange(MotionEvent.AXIS_PRESSURE, stylus)
    val tRange = dev.getMotionRange(MotionEvent.AXIS_TILT, stylus)
    return PenCapabilities(
        pressure = pRange != null && pRange.resolution > 0 && dev.supportsSource(stylus),
        tilt = tRange != null && tRange.range > 0f,
        hover = dev.supportsSource(InputDevice.SOURCE_CLASS_POINTER),
        // 首个 ACTION_DOWN 时缓存 deviceId;STYLUS_BUTTON 详见 §4.1
        stylusButtonCount = detectedButtonBits(event),
        touchFilm = MiTouchFilmBridge.isAvailable(context),   // §4.2 包装,反射/软依赖
    )
}
```

要点:
- 平板上 `SOURCE_STYLUS` 与 `SOURCE_TOUCHSCREEN` 可能来自**同一 InputDevice**(内置数字化器),用 `supportsSource()` 而非设备枚举判断。
- 首次探测结果缓存进 `PenCapabilities`,供降级矩阵(DESIGN-V0 §3.4)与 UI 提示(「当前笔不支持按键」时隐藏相关设置项)。
- Pad 8 Pro 与 Pad 7 Pro 探测结果可能不同(数字化器代际不同),`PenCapabilities` 按 `(deviceId)` 缓存、不全局假设。

### 3.3 采样管线:批次点 + 免缓冲

书写中每个 `MotionEvent` 可能**合批**(batched)了多个历史采样点,必须逐点展开,否则压感曲线丢帧、笔宽抖动:

```kotlin
fun MotionEvent.forEachInkPoint(block: (x: Float, y: Float, p: Float, t: Long) -> Unit) {
    // 历史批次点(时间序)
    for (h in 0 until historySize) {
        val ph = getHistoricalPressure(0, h).coerceIn(0f, 1f)
        block(getHistoricalX(0, h), getHistoricalY(0, h), ph, getHistoricalEventTime(0, h))
    }
    // 当前采样点
    block(x, y, pressure.coerceIn(0f, 1f), eventTime)
}
```

配合免缓冲派发(DESIGN-V0 §2.1 已定):
- API 30+:`view.requestUnbufferedDispatch(InputDevice.SOURCE_STYLUS)` 常开(一次性,针对笔源免缓冲);
- 低版本兜底:`ACTION_DOWN` 时 `view.requestUnbufferedDispatch(downEvent)`(单笔生效,每笔调用)。
- 开启免缓冲后 `historySize` 基本为 0,但**展开逻辑必须保留**(系统可能拒绝免缓冲、或其它输入源仍合批)。

`InkPoint` 原样落 protobuf `Point.p`(DESIGN-V0 §6.1),回放/换设备渲染用原始归一值,不存映射后的笔宽——**压感是数据,笔宽是样式**。

### 3.4 压感 → 笔宽映射(V0 定稿)

- 输入:`p ∈ [0,1]`(归一);输出:笔宽 `w = base × curve(p)`,curve 为平滑 S 型(如 `smoothstep`),轻触 0.3mm、重按 2.2mm(DESIGN-V0 §3.1)。
- **死区**:p < 0.02 视为噪声置 0(防 0g 力度下的悬浮误报);p > 0.98 饱和。
- **增益可调**:用户可在「笔设置 → 压感曲线」选 柔和/标准/灵敏 三档(实现为 curve 的 γ 参数 1.4 / 1.0 / 0.7),默认标准。
- 8192 级经归一后约 0.00012/级,`float` 精度充足;连续点之间做 3ms EMA 平滑(仅用于**显示**笔宽,不影响存储值)。

### 3.5 压感校准与调试面板

`设置 → 关于 → 笔诊断`(隐藏入口,长按版本号 5 次出现):

- 实时压力条 + 当前 p 值(mono 字体);
- 30 秒采样直方图(min/max/p50 分布),用于判断个体笔的一致性;
- 按键事件流水:实时打印 ①`getButtonState()` 变化、②`dispatchKeyEvent` 收到的 keyCode、③SDK `onTouchFilmTriggered` —— **§7 真机验证的数据来源**;
- 设备信息区:机型、HyperOS/Android 版本、笔 deviceId、`PenCapabilities` 探测结果——**两台平板分别采样记录**,兼容性差异一目了然。

---

## 4. 笔按键功能

### 4.1 通路①②:实体键(一代笔,**主通路**)

**接触屏幕时**(数字化器随触摸事件上报按钮位,API 23+ 常量,minSdk 31 直接可用):

```kotlin
// 在 dispatchTouchEvent 的 DOWN/MOVE/UP 流中检查
val btn = event.actionMasked.let {
    if (it == MotionEvent.ACTION_BUTTON_PRESS || it == MotionEvent.ACTION_BUTTON_RELEASE)
        event.actionButton            // 本次变化的按钮位
    else event.buttonState             // 当前按住的全部按钮位
}
when {
    btn and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 -> penGesture.onKey(PRIMARY, pressed)
    btn and MotionEvent.BUTTON_STYLUS_SECONDARY != 0 -> penGesture.onKey(SECONDARY, pressed)
}
```

**悬停时**(蓝牙 HID 通路,以 `KeyEvent` 投递;`KEYCODE_STYLUS_BUTTON_*` 为 **API 34 (Android 14)** 新增——Pad 8 Pro(Android 16)与 Pad 7 Pro(Android 15)均满足,但代码须守卫以兼容 minSdk 31):

> **✅ P1 真机实测回填(2026-11-01,Pad 8 Pro + HyperOS V816 / API 37,诊断面板流水)**:
> 1. **侧键投递形式确证:上侧键 = `KEYCODE_PAGE_UP`(92),下侧键 = `KEYCODE_PAGE_DOWN`(93)`**,走 `Activity.dispatchKeyEvent`(KeyEvent 通路);**不是** `KEYCODE_STYLUS_BUTTON_*`,不是 buttonState(书写中 `btn=0x0` 恒零),也没有 generic motion `ACTION_BUTTON_PRESS`。标准通路假设全部不成立,小米走自定义键码。
> 2. **焦点键(笔顶)完全不到达应用**(流水无任何对应事件)——被系统独占,`Trigger.FOCUS_KEY` 维持占位不接。
> 3. **系统长按可接管(2026-11-01 终局,gb_boosting 通道)**:应用对 PAGE_UP/DOWN 的消费(`return true`)/无障碍 keyFilter(实测 onKeyEvent 根本不被调用,MIUI 在该层之前拦截)/settings 覆写(硬编码)全部无效;**FLAG_SECURE 能拦住截图执行但系统弹「无法截屏」提示**。**最终解:MIUI 笔键拦截器(MiuiStylusShortcutManager)读 Secure 表 `gb_boosting`(game_booster),=1 时整个拦截器停摆**——上下键 380ms 长按定时器都不启动,系统行为完全静默,KeyEvent 完整到达应用。MilkNote 实现(`PenKeyTakeover`):进入书写页写 1、离开写 0、onDestroy 兜底;写入需 `WRITE_SECURE_SETTINGS`(manifest 声明 + 一次性 `adb shell pm grant com.milknote.app android.permission.WRITE_SECURE_SETTINGS`;未授权回退 FLAG_SECURE 方案)。验证:书写页内长按上/下键零提示零系统行为,书架页系统行为恢复。**M2 可恢复「长按上键=框选截图」默认映射**(系统占用已解除)。证据链:游戏加速 per-app 实测 → `MiuiSettingsObserver` 监听 `gb_boosting` 源码(chrmhoffmann/miui-services 镜像)→ 手写 settings 实测(系统不回写、ContentObserver 实时生效)。
> 4. 电量 GATT 实测通过:`battery=100% source=Xiaomi Focus Pen`(命名规则 `pen|focus` 命中)。
> 5. 探测陷阱:小米数字化器 `AXIS_PRESSURE` 的 `resolution=0` 但 `getPressure()` 正常工作(实测 p50=0.079/max=0.154)——能力判定须用「值域存在」而非 resolution。`pressure`/`tilt` 原误报 false 已修。
> 6. 机型 25091RP04C,屏幕 3200×2136@**440dpi**(AVD 用 345dpi,UI 全 dp 自适应无碍)。
> 上/下键 ↔ 主/副 的对应(上=PRIMARY)为当前假设,若复测发现语义颠倒,改 `DefaultPenInputPort` 中两行映射即可。

```kotlin
override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (Build.VERSION.SDK_INT >= 34) {
        when (event.keyCode) {
            KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY ->
                return penGesture.onKey(PRIMARY, event.action == KeyEvent.ACTION_DOWN)
            KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY ->
                return penGesture.onKey(SECONDARY, event.action == KeyEvent.ACTION_DOWN)
        }
    }
    if (MiTouchFilmBridge.onDispatchKeyEvent(event)) return true   // 通路③,§4.2
    return super.dispatchKeyEvent(event)
}
```

兼容注意:
- 老机型/老系统可能投递**厂商自定义 keyCode**(非标准 KEYCODE_STYLUS_BUTTON 段),`else` 分支不要吞,进日志流水(§3.5)供适配;一代笔 × Pad 8 Pro 这种非官方组合尤其可能出现非标准投递;
- 同一次按键可能**双路都收到**(接触+悬停边缘),`PenGestureDetector` 内按 `(键位, 时间窗 50ms)` 去重;
- 焦点键(顶部)在两台平板上是否到达应用、以何种形式(button 位 / keyCode / 系统消费)完全未知,**只依赖 P1 实测**;代码侧先按 主/副键 实现,焦点键触发器(`Trigger.FOCUS_KEY`)占位,P1 有数据后再接。

### 4.2 通路③:焦点笔 Pro 触控区手势(小米 SDK,前瞻)

SDK 只投递**语义化的 function**(用户在系统「手写笔设置」里配置手势 → 应用收到解析后的功能码),应用无需自己识别轻捏/双击/滑动:

```kotlin
// 生命周期:onResume 初始化,onPause 销毁(SDK 强约束)
override fun onResume() {
    super.onResume()
    MiuiTouchFilmUtils.init(applicationContext,
        object : MiuiTouchFilmUtils.TouchFilmListener {
            override fun onTouchFilmTriggered(function: Int) {
                when (function) {
                    MiuiTouchFilmUtils.SWITCH_BETWEEN_BRUSH_AND_ERASER -> router.dispatch(Trigger.PRO_PINCH)   // 轻捏:默认对齐系统的笔⇄橡皮
                    MiuiTouchFilmUtils.SWITCH_TO_PREVIOUS_BRUSH        -> router.dispatch(Trigger.PRO_DOUBLE_TAP)
                    MiuiTouchFilmUtils.STYLUS_SLIDE_UP                 -> router.dispatch(Trigger.PRO_SLIDE_UP)
                    MiuiTouchFilmUtils.STYLUS_SLIDE_DOWN               -> router.dispatch(Trigger.PRO_SLIDE_DOWN)
                    MiuiTouchFilmUtils.SHOW_COLOR_WHEEL                -> router.dispatch(Trigger.PRO_FUNC_COLOR)
                    MiuiTouchFilmUtils.SHOW_BRUSH_SETTINGS             -> router.dispatch(Trigger.PRO_FUNC_BRUSH)
                    MiuiTouchFilmUtils.UNKNOWN_SETTINGS_FUNCTION       -> { /* 系统设置里未配置,忽略 */ }
                }
            }
            override fun onBrushPreviewChanged(enable: Boolean) {
                hoverCursor.enabled = enable   // 系统级悬浮预览开关变化,同步应用内光标避免双光标
            }
        })
}

override fun onPause() { MiuiTouchFilmUtils.onDestroy(); super.onPause() }
// dispatchKeyEvent 转发见 §4.1 代码
```

约束:HyperOS 3.0+ 且仅 Pad 8 / 8 Pro 及后续机型 + 焦点笔 Pro;SDK 依赖与鉴权见 §5。

**本项目现状**:无 Pro 笔,此通路**只能按文档实现、无法真机验证**。因此:实现放最后(M1 末或 M2 初)、代码经 §5.4 薄包装隔离、诊断面板留 SDK 事件流水位;补购 Pro 笔后按 §7-P5 一次性闭环。**注意:主力机 Pad 8 Pro 上插着一支 Pro 笔之前,通路③永远静默,不得影响通路①②。**

### 4.3 系统拦截与手势判定

一代笔三键有**系统级行为**(§1.1:副键长按=系统截图、主键长按=唤起画板、焦点键双击=清屏),第三方应用无法取消,只能错开设计。据此:

- **书写中按主/副键**优先路由给前台应用(与三星 S Pen、Wacom 惯例一致)→ MilkNote 的自定义入口全放「书写中按键」;
- **悬停按键**可能被系统手势消费,应用侧不强依赖;
- 应用层手势判定(`PenGestureDetector`,DESIGN-V0 §3.2 的判定参数):

| 判定 | 参数 | 实现 |
|---|---|---|
| 双击 | 间隔 < **350ms** | 键按下时间戳队列,第二击到达即触发,单击延迟 350ms 确认(避免误单发) |
| 长按 | > **400ms** | 按下起 400ms 定时器,未抬即触发;**长按+落笔 = 框选截图**(核心路径,触发即冻结画布) |
| 按住+书写 | 键位按住期间落笔 | 进入「键修饰模式」:主键按住书写=套索圈选,副键按住书写=临时橡皮 |

### 4.4 应用内自定义映射(PenActionRouter)

**默认映射**(与 DESIGN-V0 §3.2 对齐):

| Trigger | 默认动作 | 可选动作池 |
|---|---|---|
| 主键单击(书写中) | 套索选择 | 切换笔⇄橡皮 / 撤销 / 重做 / 新页 / 无 |
| 主键双击 | **切换笔 ⇄ 橡皮** | 同上 |
| 主键长按(>400ms) | **框选截图(核心)** | 套索 / 激光笔 / 无 |
| 主键按住+书写 | 套索圈选 | 临时橡皮 / 无 |
| 副键单击 | 撤销 | 重做 / 套索 / 无 |
| 副键按住+书写 | 临时橡皮 | 套索 / 无 |
| 焦点键长按 | 激光笔(讲课模式,M3) | 可达性待 P1 验证 |
| Pro 轻捏 / 双击 | 笔⇄橡皮 / 上一工具 | 同 SDK function 语义(前瞻) |
| Pro 上滑 / 下滑 | 笔粗+/- | 颜色盘 / 笔刷设置 / 无(前瞻) |

实现要点:
- 映射表 `Map<PenTrigger, PenAction>` 持久化到 **DataStore(Preferences)**,`device-mi` 只读写、不解释语义;
- 设置页 `设置 → 笔与手势`:Trigger 列表(仅显示 `PenCapabilities` 实际支持的项)→ 点击改绑;「恢复默认」一键重置;
- 与系统冲突说明文案:「副键长按为系统截图、主键长按可能唤起小米画板,由系统占用,MilkNote 的按键仅在书写时生效」——设置页固定展示,降低用户困惑;
- 框选截图(核心卖点)**必须有不依赖按键的备选入口**:顶栏 Agent 按钮 + `⌥A`(DESIGN-V0 §4.1),按键通路失效时功能可达——在一带笔 × Pad 8 Pro 组合通过 P1 验证前,这条兜底就是主路径。

---

## 5. 小米手写笔引擎 SDK(PenEngine)集成

来源:小米澎湃OS开发者平台「应用开发 → 服务能力 → 小米手写笔引擎」,SDK 版本 **0.3.2**(2025-10 更新,支持焦点触控笔 Pro)。

### 5.1 获取与依赖(本地 AAR,非 Maven)

> **申请门槛(2026-10 查证)**:开放平台「手写笔引擎」**服务申请要求企业实名认证**,个人开发者不满足;但 AAR 在官方文档页公开网盘可直接下载,且 MilkNote 所需的触控按键/悬浮预览/侧旋为**免鉴权能力**。故取**路径 A:直接集成公开 AAR + 真机验证运行时校验**;路径 B(完整服务申请,需企业主体)、申请材料草案与咨询邮件见 **[PENENGINE-APPLY.md](./PENENGINE-APPLY.md)**。鉴权类能力(笔迹预测等)个人开发者基本无缘 → `MotionPredictor` 定为笔迹预测唯一方案(P4 降级,§7)。

1. (路径 A)从[开发准备文档](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1883)页取网盘链接与提取码,下载 AAR;
2. (路径 B,需企业主体)企业实名认证(1–3 工作日)→ 创建应用 → 配置证书指纹(Release + Debug 都登记,否则 Debug 包鉴权失败)→ 服务申请(2 天内反馈);国内/海外平台相互独立,海外应用暂不能接国内能力;
3. AAR 入库:`app/libs/PenEngine-release_0.3.2.aar`:

```groovy
repositories { flatDir { dirs 'libs' } }
dependencies { implementation(name: 'PenEngine-release_0.3.2', ext: 'aar') }
```

Manifest(鉴权用 meta-data):

```xml
<meta-data android:name="com.xiaomi.xms.APP_ID" android:value="${MI_APP_ID}" />
<meta-data android:name="com.xiaomi.xms.BUILD_TYPE_DEBUG" android:value="${IS_DEBUG}" />
```

### 5.2 混淆规则

```proguard
-dontwarn com.miui.penengine.**
-keep class com.miui.penengine.ocr.** {*;}
-keep class com.miui.penengine.estimate.** {*;}
-keep class com.miui.penengine.onestroke.MiuiOneStroke {*;}
-keep class com.miui.penengine.hover.MiuiHoverPreview {*;}
-keep class com.miui.penengine.posture.MiuiStylusPosture {*;}
-keep class com.miui.penengine.colorpick.** {*;}
-keep class com.miui.penengine.touchfilm.** {*;}
-keep class com.miui.penengine.auth.** {*;}
```

### 5.3 鉴权

仅**笔迹预测、一笔成形、文字识别**三个能力需鉴权(基于 APK 签名验证):

```kotlin
MiuiAuthHelper.initAuthService(context) { success -> authed = success }
// 失败可重试:MiuiAuthHelper.runAuthConnect()
// 退出:MiuiAuthHelper.destroyAuthService()
```

触控按键、悬浮预览、侧旋、取色**无需鉴权**。鉴权失败 → 对应能力按能力矩阵降级,不影响主流程。

### 5.4 能力矩阵与接入取舍

| SDK 能力 | 类 | 系统/机型要求 | 在用设备可用性 | MilkNote 取舍 |
|---|---|---|---|---|
| 触控按键 | `MiuiTouchFilmUtils` | HyperOS 3.0+ · Pad 8 系+ · **焦点笔 Pro** | Pad 8 Pro ✓ 系统,✗ 无 Pro 笔 | **接入**(§4.2),补购后启用 |
| 悬浮预览 | `MiuiHoverPreview.init(view)` + `setBrush(Bitmap)` | HyperOS 3.0+ | Pad 8 Pro ✓ | 可选:与自绘悬停光标二选一,避免双光标;一代笔走自绘 |
| 手写笔侧旋 | `MiuiStylusPosture`(角度 0–360°) | HyperOS 3.0+ · Pad 8 系+ · **焦点笔 Pro** | ✗ 需 Pro 笔 | M3 扁笔刷再接(需补购 Pro) |
| 笔迹预测 | `MiuiStrokeEstimate.getEstimateEvent` | HyperOS 1.0+ · Pad 6S Pro 及后续 · 大陆 only · **需鉴权(=企业认证服务申请)** | 机型 ✓ 但鉴权受阻 | **不接**:`MotionPredictor` 定为唯一方案;对比验证降级为获得认证账号后的可选项 |
| 一笔成形 | `MiuiOneStroke` | HyperOS 2.0+ · 大陆 only · 需鉴权 | 两台均 ✓ | M3 图形规整再评估(自研备选:几何拟合) |
| 文字识别 | `MiuiRecognize` | HyperOS 2.0+ · 大陆 only · 需鉴权 | 两台均 ✓ | 暂不接:已选 ML Kit(跨厂商、离线) |
| 跨应用取色 | `MiuiColorPick` | HyperOS 2.1+(Pad 7 Ultra 起) | 视系统版本 | 不接(无取色场景) |

> SDK 全部类经 `MiTouchFilmBridge`/`MiPenEngineBridge` 薄包装隔离(`device-mi` 内 soft-depend,反射或 `compileOnly` + 运行时探测),**核心代码不 import SDK 类**——未过审/未集成 AAR 时应用可正常构建运行,仅 Pro 手势与 SDK 增强降级。

---

## 6. 兼容与降级矩阵(细化 DESIGN-V0 §3.4)

| 能力 | **一代笔 + Pad 8 Pro(主力机)** | 一代笔 + Pad 7 Pro(备用机) | 焦点笔 Pro + Pad 8 系(前瞻) | 通用 USI 笔 | 手指 |
|---|---|---|---|---|---|
| 低延迟出墨 | ✅ front-buffer | ✅ front-buffer | ✅ | ✅ | ✅ 固定笔宽 |
| 轨迹预测 | ✅ MotionPredictor | ✅ MotionPredictor | ✅(可对比 SDK) | 视机型 | ✅ |
| 压感/倾斜 | ✅ P0 已验证(与 7 Pro 一致) | ✅ §3 | ✅ §3 | 视机型(`PenCapabilities` 探测) | ❌ 固定 0.5 |
| 实体键(书写中) | ✅ 预期可用,P1 抓取细节 | ✅ 预期可用,P1 验证 | —(无实体键) | 视机型 | ❌ |
| 触控区手势 | ❌ | ❌ | ✅ §4.2(SDK) | ❌ | ❌ |
| 悬停光标 | ✅ 自绘 | ✅ 自绘 | ✅(避让 SDK 悬浮预览) | 视机型 | ❌ |
| 防误掌 | ✅(`TOOL_TYPE_STYLUS` 存在时拒收手指/多指) | ✅ 同左 | ✅ | ✅ | —(单指=合法书写,双指=缩放) |
| 按键不可用时 | 框选截图走 ⌥A/顶栏;套索走工具栏 | 同左 | 同左 | 同左 | 同左 |

蓝牙未连接降级(DESIGN-V0 §3.3):压感本身经数字化器上报、不依赖蓝牙(待 P2 验证);蓝牙断开主要影响 低延迟模式、电量上报。P0 已确认一代笔可在 Pad 8 Pro 正常配对使用;若个别用户设备/固件组合配对失败,按「通用被动笔」档降级:无按键、无电量、无低延迟模式,压感视数字化器而定,UI 提示引导用户笔的适配状态。

---

## 7. 真机验证计划(P0 已通过;P1 阻塞按键代码定稿)

设备:主力 **小米平板 8 Pro**(HyperOS 3.0+,Android 16)+ 焦点触控笔(一代);备用 **小米平板 7 Pro**(Android 15+,记录实际 HyperOS 版本)。

| # | 验证项 | 设备 | 方法(用 §3.5 诊断面板) | 判定 |
|---|---|---|---|---|
| **P0** | **一代笔 × Pad 8 Pro 基础兼容** | Pad 8 Pro + 一代笔 | **✅ 已通过(2026-10-31,用户实测)**:千本笔记在 Pad 8 Pro 与 Pad 7 Pro 上行为完全一致(配对/书写/压感/按键适配);应用内 `PenCapabilities` 探测与压力直方图随 M1 诊断面板补充留档 | 主力机基线确立;OTA 大版本后复测 |
| P1 | 实体键投递:书写中主/副键是否产生 `BUTTON_STYLUS_PRIMARY/SECONDARY`;悬停时 `dispatchKeyEvent` 收到的真实 keyCode;**焦点键是否/如何到达应用** | **两台各测一遍** | 逐键 单击/双击/长按/按住书写,记录事件流水(含非标准 keyCode) | 得到「机型 × 键位 → 事件」映射表,回填 §4.1/§4.4;焦点键定去留 |
| P2 | 压感:未连蓝牙时 pressure 是否可用;实际值域/噪声;0g 力度死区表现 | 两台 | 采样直方图 + 连/断蓝牙对比 | 确认 §3.4 死区与 §6 蓝牙降级条件 |
| P3 | 系统拦截:副键长按截图/主键长按画板/焦点键双击清屏在 MilkNote 前台是否仍被系统消费 | 两台 | 前台逐手势操作,看系统弹层 vs 应用日志 | 更新 §1.1/§4.3 冲突清单 |
| P4 | ~~预测对比~~ **降级为可选**:SDK 笔迹预测需鉴权(=企业认证服务申请,个人开发者受阻),`MotionPredictor` 直接定为唯一方案 | — | 获得认证账号后再做 `MotionPredictor` vs `MiuiStrokeEstimate` 对比 | 不再阻塞 M1 |
| P5 | SDK 触控按键/悬浮预览/侧旋 | **需焦点笔 Pro(未持有)** | 集成 0.3.2 跑 demo | 待补购 Pro 笔后闭环;当前按文档实现+代码评审把关 |

> P0 已通过,**P1 成为按键代码实现前的唯一阻塞项**(两台各测一遍);PenEngine 走路径 A(公开 AAR 直接集成,见 [PENENGINE-APPLY.md](./PENENGINE-APPLY.md)),与 P1–P3 并行。**对拍基准:千本笔记**(两机行为一致、已适配小米笔按键;注意其**完全不支持按键自定义**——这正是 MilkNote 的直接开发动机,P1 只对照其按键响应行为,无设置页可参照)。

---

## 8. 风险与开放问题

| 风险/问题 | 影响 | 对策 |
|---|---|---|
| 一代笔 × Pad 8 Pro 非官方适配组合(**P0 已通过,风险降级**) | 仅剩系统大版本 OTA 变更导致行为变化的理论风险 | 千本笔记两机一致已佐证;OTA 后复测 P0/P1;如遇异常以 Pad 7 Pro 为开发机、补购 Pro 笔兜底(两台平板通用) |
| 一代笔按键投递方式无官方文档(P1 未验证) | 笔键功能可能部分不可用 | 标准通路先行 + 诊断面板实测;不可用的 trigger 在设置页隐藏;核心功能备选入口(⌥A/顶栏) |
| 系统级按键行为(截图/画板/清屏)与应用手势冲突 | 用户体验割裂 | §4.4 固定文案说明;映射默认值避开系统冲突组合 |
| `KEYCODE_STYLUS_BUTTON_*` 需 API 34 | 低于 A14 设备悬停按键不可用 | 两台在用设备均 ≥A15;代码守卫 + 诊断日志发现厂商自定义码 |
| 无焦点笔 Pro 硬件,SDK 触控按键无法真机验证 | 通路③ 上线时风险后置 | 薄包装隔离 + 实现放最后 + 补购后 P5 闭环;通路③ 静默不影响通路①② |
| **PenEngine 服务申请需企业实名认证,个人开发者受阻** | 鉴权能力(笔迹预测/一笔成形/文字识别)无法开通,P4 SDK 侧对比搁置 | 路径 A:公开 AAR + 免鉴权能力(触控按键/悬浮预览)真机验证;邮件咨询个人路径;`MotionPredictor` 定为唯一预测方案;详见 [PENENGINE-APPLY.md](./PENENGINE-APPLY.md) |
| 笔迹预测双方案(MotionPredictor vs SDK) | 维护成本 | P4 实测后二选一,不长期并存 |
| SDK 部分能力仅大陆、且鉴权基于签名 | 海外包功能差异 | 按能力矩阵运行时降级;证书指纹 Release+Debug 都登记 |

---

## 9. 参考资料

**小米官方**
- [小米手写笔引擎 · 业务介绍(dev.mi.com,pId=1882)](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1882) — 能力清单、系统/机型矩阵、地区限制
- [小米手写笔引擎 · 开发准备(pId=1883)](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1883) — 账号/签名/审核流程、AAR 集成、混淆规则
- [小米手写笔引擎 · 客户端接入指南(pId=1884)](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1884) — 全部 API 与代码示例(`MiuiTouchFilmUtils` 等)
- [小米手写笔引擎 · 版本信息(pId=1886)](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1886) — SDK 0.3.2 变更
- [小米焦点触控笔 · 官方 FAQ(mi.com)](https://www.mi.com/global/support/faq/details/KA-630877) — 三键行为、配对充电、磁吸细节
- [小米焦点触控笔 · 产品页(mi.com 台湾站)](https://www.mi.com/tw/product/xiaomi-focus-pen) — 8192 级压感、3ms 延迟、0g 触控力度
- [Xiaomi Focus Pen Pro · 产品页(mi.com global)](https://www.mi.com/global/product/xiaomi-focus-pen-pro) — 触控区手势(轻捏/双击/滑动)、兼容列表(Pad 6S Pro/7 系/8 系)

**Android 官方**
- [MotionEvent | Android API Reference](https://developer.android.com/reference/android/view/MotionEvent) — `getPressure` 归一化语义、`TOOL_TYPE_*`(API 14)、`BUTTON_STYLUS_PRIMARY/SECONDARY`(API 23)、`ACTION_BUTTON_PRESS/RELEASE`(API 23)、`ACTION_HOVER_*`(API 14)、`getHistoricalPressure`
- [KeyEvent · API 34 Diff](https://developer.android.com/sdk/api_diff/34/changes/android.view/KeyEvent) — `KEYCODE_STYLUS_BUTTON_PRIMARY/SECONDARY/TERTIARY/TAIL` 于 API 34 新增
- [androidx.input MotionPredictor](https://developer.android.com/reference/androidx/input/motionprediction/MotionPredictor) — 轨迹预测(DESIGN-V0 §2.1 选型)

---

*本文档由公开资料调研 + 用户实测(P0)而成,标注「待验证」项以真机结论为准(§7);P1 结论回填后升版 A3。*
