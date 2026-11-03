package com.milknote.ink.input

import com.milknote.ink.model.InkTool
import com.milknote.ink.model.PressureGamma
import kotlin.math.exp
import kotlin.math.pow

/**
 * 压感 → 笔宽映射(PEN-ADAPTATION §3.4 定稿):
 *  - 死区:p < 0.02 置 0;饱和:p > 0.98 → 1
 *  - S 型 smoothstep 后接 γ(柔和 1.4 / 标准 1.0 / 灵敏 0.7)
 *  - 轻触 0.3mm、重按 2.2mm 由 base×ratio 区间保证(base 1.25mm 时端点重合)
 *  - 显示用宽度做 3ms EMA 平滑(仅显示,不影响存储)
 */
object PressureCurve {

    const val DEAD_ZONE = 0.02f
    const val SATURATION = 0.98f
    /** 轻触/重按宽度比(× base) */
    const val MIN_RATIO = 0.24f
    const val MAX_RATIO = 1.76f

    fun normalize(p: Float): Float {
        val v = p.coerceIn(0f, 1f)
        if (v < DEAD_ZONE) return 0f
        if (v > SATURATION) return 1f
        return (v - DEAD_ZONE) / (SATURATION - DEAD_ZONE)
    }

    /** S 型 smoothstep */
    fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

    /** 映射到 [0,1]:smoothstep^γ */
    fun curve(p: Float, gamma: PressureGamma): Float {
        val n = normalize(p)
        if (n <= 0f) return 0f
        if (n >= 1f) return 1f
        return smoothstep(n).pow(gamma.gamma)
    }

    /** 按工具得到宽度倍率曲线:圆珠压感弱(区间压半),荧光笔/形状工具不随压感(恒宽) */
    fun widthRatio(tool: InkTool, p: Float, gamma: PressureGamma): Float {
        val r = curve(p, gamma)
        return when (tool) {
            InkTool.PEN -> MIN_RATIO + (MAX_RATIO - MIN_RATIO) * r
            InkTool.BALL -> 0.55f + 0.45f * r          // 恒定感更强
            InkTool.HIGHLIGHTER -> 1f                   // 恒宽
            InkTool.ERASER -> 1f
            InkTool.RECT, InkTool.OVAL, InkTool.LINE -> 1f   // 形状:恒定线宽
        }
    }

    /** 显示宽度 EMA:α 由帧间隔与 τ=3ms 决定 */
    fun emaAlpha(dtMs: Float, tauMs: Float = 3f): Float =
        1f - exp(-dtMs / tauMs)
}

/**
 * 显示侧宽度滤波器:逐点 EMA,防止逐点抖动。
 * 「压感是数据,笔宽是样式」——存储始终用原始 pressure。
 */
class DisplayWidthFilter {
    private var last = Float.NaN

    fun next(rawWidth: Float, dtMs: Float): Float {
        if (last.isNaN()) { last = rawWidth; return rawWidth }
        val a = PressureCurve.emaAlpha(dtMs.coerceIn(0.1f, 50f))
        last = last + (rawWidth - last) * a
        return last
    }

    fun reset() { last = Float.NaN }
}
