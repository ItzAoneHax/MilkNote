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
 */
val Mono = FontFamily.Monospace

val MilkTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.5.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp),
)

/** 等宽元数据样式(页码/尺寸/时间戳/模型名) */
val MonoMeta = TextStyle(
    fontFamily = Mono,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    fontWeight = FontWeight.Normal,
)

/** 等宽值(设置右侧当前值/输入值):比元数据略大,可读优先 */
val MonoValue = TextStyle(
    fontFamily = Mono,
    fontSize = 12.5.sp,
    lineHeight = 17.sp,
    fontWeight = FontWeight.Normal,
)

/** 眉标(分组小标题):小字 + 字距 + muted;用于设置分区、表单分组、列表分组 */
val MilkEyebrow = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 11.5.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.9.sp,
)
