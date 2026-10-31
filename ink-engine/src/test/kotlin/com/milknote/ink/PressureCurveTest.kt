package com.milknote.ink

import com.milknote.ink.input.PressureCurve
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.PressureGamma
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 压感映射规范(PEN-ADAPTATION §3.4) */
class PressureCurveTest {

    @Test fun `死区与饱和`() {
        assertEquals(0f, PressureCurve.normalize(0f), 1e-6f)
        assertEquals(0f, PressureCurve.normalize(0.019f), 1e-6f)
        assertEquals(0f, PressureCurve.normalize(-0.5f), 1e-6f)   // 越界防御
        assertEquals(1f, PressureCurve.normalize(0.99f), 1e-6f)
        assertEquals(1f, PressureCurve.normalize(1.5f), 1e-6f)    // 个别设备 >1
        assertTrue(PressureCurve.normalize(0.5f) in 0.01f..0.99f)
    }

    @Test fun `曲线单调递增且端点闭合`() {
        for (gamma in PressureGamma.entries) {
            var prev = -1f
            var p = 0f
            while (p <= 1.0001f) {
                val v = PressureCurve.curve(p, gamma)
                assertTrue("gamma=${gamma} p=$p v=$v prev=$prev", v >= prev - 1e-6f)
                prev = v
                p += 0.05f
            }
            assertEquals(0f, PressureCurve.curve(0f, gamma), 1e-6f)
            assertEquals(1f, PressureCurve.curve(1f, gamma), 1e-6f)
        }
    }

    @Test fun `轻触0_3重按2_2基准1_25`() {
        // DESIGN-V0 §3.1:base=1.25mm 时轻触≈0.3mm、重按≈2.2mm
        val base = 1.25f
        val light = base * PressureCurve.widthRatio(InkTool.PEN, 0.0f, PressureGamma.STANDARD)
        val heavy = base * PressureCurve.widthRatio(InkTool.PEN, 1.0f, PressureGamma.STANDARD)
        assertEquals(0.3f, light, 0.03f)
        assertEquals(2.2f, heavy, 0.03f)
    }

    @Test fun `荧光笔恒宽 圆珠压感弱`() {
        for (p in listOf(0.05f, 0.5f, 0.95f)) {
            assertEquals(1f, PressureCurve.widthRatio(InkTool.HIGHLIGHTER, p, PressureGamma.STANDARD), 1e-6f)
        }
        // 圆珠:轻/重比应显著小于钢笔
        val penSpan = PressureCurve.widthRatio(InkTool.PEN, 1f, PressureGamma.STANDARD) -
            PressureCurve.widthRatio(InkTool.PEN, 0f, PressureGamma.STANDARD)
        val ballSpan = PressureCurve.widthRatio(InkTool.BALL, 1f, PressureGamma.STANDARD) -
            PressureCurve.widthRatio(InkTool.BALL, 0f, PressureGamma.STANDARD)
        assertTrue(ballSpan < penSpan)
    }

    @Test fun `灵敏档低压力即出粗`() {
        val soft = PressureCurve.curve(0.3f, PressureGamma.SOFT)
        val std = PressureCurve.curve(0.3f, PressureGamma.STANDARD)
        val sens = PressureCurve.curve(0.3f, PressureGamma.SENSITIVE)
        assertTrue(soft < std && std < sens)
    }
}
