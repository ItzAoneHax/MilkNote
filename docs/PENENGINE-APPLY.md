# 小米手写笔引擎(PenEngine)申请与集成路径

> **结论先行**:开放平台「手写笔引擎」**服务申请要求企业实名认证**,个人开发者不满足;但 MilkNote 核心所需能力(触控按键/悬浮预览/侧旋)按官方文档为**免鉴权**,且 AAR 在官方文档页公开网盘可下载——**首选路径 A「直接集成公开 AAR + 真机验证运行时校验」,服务申请(路径 B)作为兜底**。受此影响,鉴权类能力(笔迹预测等)个人开发者基本无缘,`MotionPredictor` 定为笔迹预测唯一方案。
>
> - 版本:B0(2026-10-31,依据 dev.mi.com 公开信息查证)
> - 关联:[PEN-ADAPTATION.md](./PEN-ADAPTATION.md) §5(技术集成)、§7-P4(预测对比降级)

---

## 1. 门槛结论(2026-10 查证)

| 事项 | 要求 | 对 MilkNote 的影响 |
|---|---|---|
| 服务申请(开放平台 → 开发与测试 → 开放服务 → 手写笔引擎) | **企业实名认证**开发者账号(营业执照、企业联系人信息),个人开发者不满足 | 路径 B 受阻,除非注册企业/个体主体 |
| AAR 获取 | [开发准备文档页](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1883)公开网盘链接 + 提取码,**无需登录** | ✅ 可直接取得 `PenEngine-release_0.3.2.aar` |
| 鉴权能力:笔迹预测 / 一笔成形 / 文字识别 | 平台登记签名指纹 + `MiuiAuthHelper` 运行时鉴权(依赖服务申请) | ❌ 个人开发者基本无缘 → 笔迹预测只用 `MotionPredictor`;一笔成形自研几何拟合;文字识别用 ML Kit(原本即如此选型,**实际影响仅 P4 对比**) |
| 免鉴权能力:触控按键 / 悬浮预览 / 侧旋 / 跨应用取色 | 官方接入指南明确「无需鉴权」 | ✅ 正是 MilkNote 需要的;**运行时是否仍校验 APP_ID/签名待路径 A 验证** |

---

## 2. 路径 A(首选):直接集成公开 AAR

1. 从[开发准备文档](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1883)页取网盘链接与提取码,下载 AAR → `app/libs/PenEngine-release_0.3.2.aar`;
2. 按 PEN-ADAPTATION §5.1–5.2 集成(`flatDir` 依赖 + 混淆规则),Manifest 暂**不配** `APP_ID` meta-data;
3. Pad 8 Pro 真机验证(需 M1 工程骨架,半天):
   - `MiTouchFilmBridge.isAvailable()` 探测是否通过;
   - `MiuiTouchFilmUtils.init()` 是否成功(**无 Pro 笔时 init 成功且无手势触发即算通过**——说明无签名/APP_ID 校验);
   - 若 init 报错,补配占位 `APP_ID` 再试,仍失败则说明运行时校验签名 → 转路径 B 或放弃通路③。
4. 判定通过 → 通路③(Pro 触控手势)技术上成立,等补购 Pro 笔后 P5 闭环。

## 3. 路径 B(兜底):完整服务申请(需企业主体)

| 步骤 | 内容 | 周期 |
|---|---|---|
| 1 | 小米账号 → [dev.mi.com](https://dev.mi.com) → 开发者实名认证(**企业**,备营业执照) | 1–3 工作日 |
| 2 | 管理中心创建应用:名称 **MilkNote(牛奶笔记)**;包名 `com.milknote.app`(⚠️ 定稿不可改,提交前最终确认);分类 工具/效率 | 1–3 工作日 |
| 3 | 管理中心「配置签名」:登记证书指纹(Release + Debug 都配) | — |
| 4 | 开发与测试 → 开放服务 → 手写笔引擎 → 服务申请,填: | 2 天内反馈 |
| 5 | 下载 AAR(同路径 A)→ 集成 | — |

**服务申请文案草案**(直接复制):

- 服务范围(30 字内):
  > 手写笔触控按键与悬浮预览能力接入,用于手写笔记应用
- 使用场景(200 字内):
  > MilkNote(牛奶笔记)是面向小米平板与小米焦点触控笔的手写笔记应用。通过手写笔触控按键能力,将笔身按键与手势映射为切换笔擦、套索选择、框选截图等高频操作,并支持用户自定义按键映射;通过悬浮预览能力,在笔尖接触屏幕前预览笔刷位置与大小,提升手写效率与体验。

**证书指纹**(无 Android 工程也能先生成 keystore):

```bash
# 生成 Release keystore(口令自设并妥善保管,丢失无法补发)
keytool -genkeypair -v -keystore milknote-release.jks -alias milknote \
  -keyalg RSA -keysize 2048 -validity 10950

# 输出指纹(SHA1/SHA256/MD5 都会列出,按平台界面要求提交)
keytool -list -v -keystore milknote-release.jks

# Debug 指纹(Android Studio 首次构建后,默认口令 android)
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore"
```

## 4. 邮件咨询(与路径 A 并行,立即发)

收件人:`developer@xiaomi.com`(澎湃OS开发者平台支持邮箱)。草稿:

> 主题:个人开发者咨询「手写笔引擎 SDK」免鉴权能力接入条件
>
> 您好。我是个人开发者,正在为小米平板 + 焦点触控笔开发手写笔记应用(本地工具类,计划通过 GitHub/官网直发 APK)。咨询:
> 1. 「手写笔触控按键」「悬浮预览」等官方文档标注为无需鉴权的能力,个人开发者(无企业主体)直接集成 SDK AAR 使用,是否合规?运行时是否校验 APP_ID 或签名指纹?
> 2. 若不行,个人开发者是否有其他合规接入路径?后续是否会开放个人认证?
> 3. 该 SDK 能力是否要求应用在小米应用商店上架后才能生效?
> 感谢!

## 5. 分发注意(顺带查证)

公开信息显示小米应用商店自 2024 年底起不再支持个体工商户注册上架(个人开发者上架政策收紧,细节待核实)。MilkNote 初期 **GitHub/官网直发 APK 不受影响**;若日后上架小米商店,再评估企业主体或合作发行。

## 6. 时间线与并行任务

| 任务 | 预估 | 依赖 | 状态 |
|---|---|---|---|
| 邮件咨询(§4) | 即时发送,回复 1–5 天 | 无 | ⬜ 待发 |
| 路径 A 验证(§2,需 M1 工程骨架) | 半天 | 工程骨架 | ⬜ 待 M1 |
| P1 按键事件抓取(可先做 PenProbe 独立小工具) | 1 天 | 无 | ⬜ 待做 |
| 路径 B 企业主体申请 | 1–2 周 | 企业主体 | ⏸ 暂缓 |

> 状态更新约定:完成后改 ✅ 并附日期/结论;路径 A 结论回填 PEN-ADAPTATION §5.1。

---

## 参考资料

- [手写笔引擎能力页(含服务申请入口与企业认证要求)](https://dev.mi.com/xiaomihyperos/ability/Pencil-Engine)
- [开发准备(AAR 网盘下载、混淆、签名配置)](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1883)
- [客户端接入指南(鉴权/免鉴权能力划分)](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1884)
- [版本信息(SDK 0.3.2)](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1886)
