package com.milknote.agent

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentStorePathTest {
    @Test
    fun `附件读取限制在笔记本目录内`() {
        val root = Files.createTempDirectory("milknote-agent-path").toFile()
        val notebooks = File(root, "notebooks")
        val store = AgentStore(root, notebooks)
        val safeDir = File(notebooks, "nb.milk/agent/att").apply { mkdirs() }
        File(safeDir, "safe.png").writeBytes(byteArrayOf(1, 2, 3))
        File(notebooks, "nb.milk/outside.png").writeBytes(byteArrayOf(9))
        val safe = Attachment("a", "agent/att/safe.png", "x", 1, 1, Attachment.Source.SNIP, "safe")
        val traversal = safe.copy(file = "agent/att/../../outside.png")
        assertArrayEquals(byteArrayOf(1, 2, 3), store.readAttachment("nb", safe))
        assertNull(store.readAttachment("nb", traversal))
        assertNull(store.readAttachment("nb", safe.copy(file = "/etc/passwd")))
    }
}
