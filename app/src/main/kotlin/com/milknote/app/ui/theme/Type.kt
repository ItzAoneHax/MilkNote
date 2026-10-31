package com.milknote.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Codex 风字体纪律(DESIGN-V0 §5.2):
 * 界面 = 系统默认(MiSans/Roboto 回退);数字·状态·命令 = 等宽(mono)。
 */
val Mono = FontFamily.Monospace

val MilkTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

/** 等宽元数据样式(页码/尺寸/时间戳/模型名) */
val MonoMeta = TextStyle(
    fontFamily = Mono,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    fontWeight = FontWeight.Normal,
)
