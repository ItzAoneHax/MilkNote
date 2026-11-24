package com.milknote.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Codex 风字体纪律(DESIGN-V0 §5.2):
 * 界面 = 系统默认(MiSans/Roboto 回退);数字·状态·命令 = 等宽(mono)。
 * 阶梯(2026-11 视觉升级):页面标题 22 / 面板标题 17 / 行标题 15 / 正文 14.5 / 说明 12.5 / 眉标 11.5。
 *
 * 光学字距(WWDC 2020 UI Typography):大字收紧负字距、正文归零、小字微正字距;
 * 所有等宽/数值样式启用 tnum,页码·计时器·计数不再跳动。
 */
val Mono = FontFamily.Monospace

/** 等宽数值的表格数字特性:同宽数字,避免动态数值左右抖动 */
private const val TabularNumbers = "tnum"

val MilkTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.5.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp),
)

/** 等宽元数据样式(页码/尺寸/时间戳/模型名):tnum 表格数字 */
val MonoMeta = TextStyle(
    fontFamily = Mono,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    fontWeight = FontWeight.Normal,
    fontFeatureSettings = TabularNumbers,
)

/** 等宽值(设置右侧当前值/输入值):比元数据略大,可读优先;tnum 表格数字 */
val MonoValue = TextStyle(
    fontFamily = Mono,
    fontSize = 12.5.sp,
    lineHeight = 17.sp,
    fontWeight = FontWeight.Normal,
    fontFeatureSettings = TabularNumbers,
)

/** 眉标(分组小标题):小字 + 字距 + muted;用于设置分区、表单分组、列表分组 */
val MilkEyebrow = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 11.5.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.6.sp,
)

/**
 * 业务文本角色(MilkText):页面/面板/卡片/正文/说明的唯一字号来源。
 * 页面不再散写 `.sp`——需要新角色时先在这里命名,再消费。
 */
object MilkText {
    /** 页面主标题(书架/设置顶栏) */
    val pageTitle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.35).sp,
    )

    /** 分区标题(外观/纸纹分组) */
    val sectionTitle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.25).sp,
    )

    /** 浮层/面板标题(弹窗头) */
    val panelTitle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.15).sp,
    )

    /** 卡片标题(封面下的笔记名) */
    val cardTitle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.05).sp,
    )

    /** 行标题(列表/菜单/导航项) */
    val rowTitle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp)

    /** 正文 */
    val body = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 20.sp, letterSpacing = 0.sp)

    /** 正文强调(按钮文字/选中行) */
    val bodyMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 20.sp, letterSpacing = 0.sp)

    /** 说明文字 */
    val caption = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp)

    /** 控件标签(芯片/分段/字段标签) */
    val label = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 17.sp)

    /** 微型标签(角标/纸样名) */
    val micro = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.2.sp)

    /** 等宽正文(命令/代码/地址):tnum 表格数字 */
    val monoBody = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = TabularNumbers,
    )

    /** 等宽元数据:tnum 表格数字 */
    val monoMeta = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        fontFeatureSettings = TabularNumbers,
    )

    /** 等宽微型(封面纸纹标签/键位):tnum 表格数字 */
    val monoMicro = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        fontFeatureSettings = TabularNumbers,
    )

    /** 眉标:与 [MilkEyebrow] 完全一致 */
    val eyebrow: TextStyle get() = MilkEyebrow
}
