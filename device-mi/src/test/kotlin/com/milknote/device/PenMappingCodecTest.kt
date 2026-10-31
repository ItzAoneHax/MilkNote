package com.milknote.device

import com.milknote.device.pen.DEFAULT_MAPPING
import com.milknote.device.pen.PenAction
import com.milknote.device.pen.PenMappingCodec
import com.milknote.device.pen.PenTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 改绑持久化 roundtrip(PEN-ADAPTATION §4.4) */
class PenMappingCodecTest {

    @Test fun `默认映射 roundtrip`() {
        val raw = PenMappingCodec.serialize(DEFAULT_MAPPING)
        assertEquals(DEFAULT_MAPPING, PenMappingCodec.parse(raw))
    }

    @Test fun `改绑后 roundtrip`() {
        val rebound = DEFAULT_MAPPING +
            (PenTrigger.SECONDARY_TAP to PenAction.REDO) +
            (PenTrigger.PRIMARY_TAP to PenAction.TOGGLE_ERASER)
        val raw = PenMappingCodec.serialize(rebound)
        assertEquals(rebound, PenMappingCodec.parse(raw))
    }

    @Test fun `损坏输入返回 null 走默认`() {
        assertNull(PenMappingCodec.parse("garbage"))
        assertNull(PenMappingCodec.parse("PRIMARY_TAP=不存在"))
        assertNull(PenMappingCodec.parse("PRIMARY_TAP"))
        assertNull(PenMappingCodec.parse(""))
    }
}
