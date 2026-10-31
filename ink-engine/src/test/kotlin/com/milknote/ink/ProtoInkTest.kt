package com.milknote.ink

import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.Stroke
import com.milknote.ink.serialize.ProtoInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** 笔迹 protobuf 追加流 roundtrip(DESIGN-V0 §6.1 / §6.2 追加写) */
class ProtoInkTest {

    private fun stroke(id: String, color: Long = 0xFF23221E, pts: Int = 5) = Stroke(
        id = id,
        tool = InkTool.PEN,
        color = color,
        baseWidthMm = 0.7f,
        points = (0 until pts).map {
            InkPoint(it * 10f, it * 4f, 0.2f + it * 0.1f, 5f, 5f, 1_000L + it * 8)
        },
    )

    @Test fun `单笔 roundtrip`() {
        val out = ByteArrayOutputStream()
        ProtoInk.writeRecord(out, ProtoInk.recordOf(stroke("s1")))
        val back = ProtoInk.readPage(ByteArrayInputStream(out.toByteArray()))
        assertEquals(1, back.size)
        assertEquals("s1", back[0].id)
        assertEquals(InkTool.PEN, back[0].tool)
        assertEquals(0xFF23221EL, back[0].color)
        assertEquals(5, back[0].points.size)
        val p = back[0].points[2]
        assertEquals(20f, p.x, 1e-4f)
        assertEquals(0.4f, p.pressure, 1e-6f)
    }

    @Test fun `多笔追加 + tombstone 语义`() {
        val out = ByteArrayOutputStream()
        ProtoInk.writeRecord(out, ProtoInk.recordOf(stroke("a")))
        ProtoInk.writeRecord(out, ProtoInk.recordOf(stroke("b")))
        ProtoInk.writeRecord(out, ProtoInk.recordOf(stroke("c")))
        ProtoInk.writeRecord(out, ProtoInk.tombstoneOf("b"))
        val back = ProtoInk.readPage(ByteArrayInputStream(out.toByteArray()))
        assertEquals(listOf("a", "c"), back.map { it.id })  // 顺序保持
    }

    @Test fun `尾部半条崩溃残留被丢弃`() {
        val out = ByteArrayOutputStream()
        ProtoInk.writeRecord(out, ProtoInk.recordOf(stroke("keep")))
        val bytes = out.toByteArray()
        // 伪造半条:varint 声明 100 字节但实际只有 3 字节
        val tail = byteArrayOf(100, 1, 2, 3)
        val full = bytes + tail
        val back = ProtoInk.readPage(ByteArrayInputStream(full))
        assertEquals(1, back.size)
        assertEquals("keep", back[0].id)
    }

    @Test fun `空文件可读`() {
        assertEquals(0, ProtoInk.readPage(ByteArrayInputStream(ByteArray(0))).size)
    }

    @Test fun `颜色 hex 解析`() {
        assertEquals(0xFFFFD65AL, ProtoInk.parseColorOrNull("#FFFFD65A"))
        assertEquals(0xFFFFD65AL, ProtoInk.parseColorOrNull("#FFD65A"))
        assertEquals(null, ProtoInk.parseColorOrNull("#GGGGGG"))
        assertEquals(null, ProtoInk.parseColorOrNull(null))
        assertTrue(ProtoInk.parseColorOrNull("#3B5BEC")!! in 0xFF000000L..0xFFFFFFFFL)
    }
}
