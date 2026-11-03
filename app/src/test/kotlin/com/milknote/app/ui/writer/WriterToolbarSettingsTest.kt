package com.milknote.app.ui.writer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WriterToolbarSettingsTest {
    @Test
    fun `missing preference uses defaults`() {
        assertEquals(WriterToolbarAction.defaultPins, decodeWriterToolbarPins(null))
    }

    @Test
    fun `blank preference keeps every action unpinned`() {
        assertTrue(decodeWriterToolbarPins("").isEmpty())
        assertTrue(decodeWriterToolbarPins("  ").isEmpty())
    }

    @Test
    fun `decode preserves order and filters duplicates and unknown ids`() {
        val decoded = decodeWriterToolbarPins("image,unknown,lasso,image, focus ")
        assertEquals(
            listOf(WriterToolbarAction.IMAGE, WriterToolbarAction.LASSO, WriterToolbarAction.FOCUS),
            decoded,
        )
    }

    @Test
    fun `encode round trips pins`() {
        val pins = listOf(WriterToolbarAction.EXPORT, WriterToolbarAction.COMMAND)
        assertEquals(pins, decodeWriterToolbarPins(encodeWriterToolbarPins(pins)))
    }

    @Test
    fun `toggle adds once or removes pin`() {
        val added = setWriterToolbarPin(emptyList(), WriterToolbarAction.TEXT, true)
        assertEquals(listOf(WriterToolbarAction.TEXT), added)
        assertEquals(added, setWriterToolbarPin(added, WriterToolbarAction.TEXT, true))
        assertTrue(setWriterToolbarPin(added, WriterToolbarAction.TEXT, false).isEmpty())
    }

    @Test
    fun `move changes order and respects bounds`() {
        val pins = WriterToolbarAction.defaultPins
        val moved = moveWriterToolbarPin(pins, WriterToolbarAction.TEXT, 1)
        assertEquals(WriterToolbarAction.TEXT, moved[2])
        assertEquals(pins, moveWriterToolbarPin(pins, WriterToolbarAction.LASSO, -1))
        assertEquals(pins, moveWriterToolbarPin(pins, WriterToolbarAction.EXPORT, 1))
    }
}
