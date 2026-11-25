package com.milknote.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import java.io.File

/** 会话持久化/查询/切换/删除/清空 + 会话指标 */
class AgentSessionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun chatProvider() = FakeProvider { listOf(ChatEvent.Delta("答"), ChatEvent.Done(null)) }

    @Test
    fun `会话落盘后可查询、切换并保留旧会话`() = runTest {
        val fake = chatProvider()
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))

        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        val firstId = c.state.value.currentSessionId!!
        assertEquals(2, c.state.value.messages.size)
        assertEquals(1, c.state.value.sessions.size)
        assertTrue(c.state.value.sessions.first().isCurrent)
        assertNotNull(e.store.loadSession("nb1", firstId))
        // 标题取首条用户消息
        assertEquals("[讲解]", e.store.loadSession("nb1", firstId)!!.title)

        // /清空:新空会话(未落盘) + 旧会话(已落盘)
        c.clearSession()
        val freshId = c.state.value.currentSessionId!!
        assertNotEquals(firstId, freshId)
        assertTrue(c.state.value.messages.isEmpty())
        assertEquals(2, c.state.value.sessions.size)
        assertTrue(c.state.value.sessions.first().isCurrent)
        assertNotNull(e.store.loadSession("nb1", firstId))

        // 切回旧会话:消息完整恢复
        c.switchSession(firstId)
        assertEquals(firstId, c.state.value.currentSessionId)
        assertEquals(2, c.state.value.messages.size)
        assertEquals("答", c.state.value.messages.last().text)
        assertEquals("nb1", c.state.value.boundPage?.notebookId)
        assertEquals("p0001", c.state.value.boundPage?.pageId)
    }

    @Test
    fun `删除会话级联清理附件且尊重跨会话引用`() = runTest {
        val fake = chatProvider()
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))

        c.openDrawer(PageRef("nb1", "p0001"))
        c.addSnip(Rect4(0f, 0f, 100f, 50f))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        val sessionA = c.state.value.currentSessionId!!
        val relFile = c.state.value.messages.first().attachments.single().file
        val attachment = File(e.notebooksRoot, "nb1.milk/$relFile")
        assertTrue(attachment.exists())

        // 第二个会话引用同一份字节(内容寻址 → 同一文件)
        c.clearSession()
        c.addSnip(Rect4(0f, 0f, 100f, 50f))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        val sessionB = c.state.value.currentSessionId!!
        assertEquals(relFile, c.state.value.messages.first().attachments.single().file)

        // 删 B:A 仍引用 → 文件保留
        c.deleteSession(sessionB)
        assertNull(e.store.loadSession("nb1", sessionB))
        assertTrue(attachment.exists())
        assertEquals(sessionA, c.state.value.currentSessionId)

        // 删 A:引用归零 → 文件删除,且新建空会话兜底
        c.deleteSession(sessionA)
        assertFalse(attachment.exists())
        assertNull(e.store.loadSession("nb1", sessionA))
        assertTrue(c.state.value.messages.isEmpty())
        assertEquals(1, c.state.value.sessions.size)
        assertTrue(c.state.value.sessions.single().id !in setOf(sessionA, sessionB))
    }

    @Test
    fun `删除会话级联清理多页文档逐页图`() = runTest {
        val fake = chatProvider()
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))
        c.openDrawer(PageRef("nb1", "p0001"))

        c.addDocumentAttachment(
            logicalName = "讲义.pdf",
            source = Attachment.Source.PDF_DOC,
            textContent = "正文内容",
            extraPages = (1..2).map { DraftPageImage(it, byteArrayOf(it.toByte()), false, 10, 10) },
            pageRangeLabel = "第1-2页",
        )
        c.send()
        advanceUntilIdle()

        val att = c.state.value.messages.first().attachments.single()
        assertEquals(2, att.pageFiles.size)
        val pageFiles = att.pageFiles.map { File(e.notebooksRoot, "nb1.milk/$it") }
        val textFile = File(e.notebooksRoot, "nb1.milk/${att.file}")
        pageFiles.forEach { assertTrue(it.exists()) }
        assertTrue(textFile.exists())
        assertEquals("正文内容", e.store.readAttachmentText("nb1", att))

        c.deleteSession(c.state.value.currentSessionId!!)
        pageFiles.forEach { assertFalse(it.exists()) }
        assertFalse(textFile.exists())
    }

    @Test
    fun `切换会话时保留同一笔记本的当前页`() = runTest {
        val fake = chatProvider()
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))

        // 先在 p0001 产生一条历史会话
        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        val oldId = c.state.value.currentSessionId!!

        // 用户移到 p0003 继续写(当前会话是新页)
        c.openDrawer(PageRef("nb1", "p0003"))
        advanceUntilIdle()
        assertEquals("p0003", c.state.value.boundPage?.pageId)

        // 切回旧会话:仍停留在 p0003(点「当前页」/框选作用于正在看的页面)
        c.switchSession(oldId)
        assertEquals(oldId, c.state.value.currentSessionId)
        assertEquals("p0003", c.state.value.boundPage?.pageId)
        assertEquals(2, c.state.value.messages.size)

        // 跨笔记本切换才回落到目标会话的页
        c.switchSession(oldId)
        assertEquals("p0003", c.state.value.boundPage?.pageId)
    }

    @Test
    fun `删除非当前会话不影响当前会话`() = runTest {
        val fake = chatProvider()
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))

        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        val oldId = c.state.value.currentSessionId!!

        c.clearSession()
        val currentId = c.state.value.currentSessionId!!
        c.deleteSession(oldId)

        assertEquals(currentId, c.state.value.currentSessionId)
        assertEquals(1, c.state.value.sessions.size)
        assertTrue(c.state.value.sessions.single().isCurrent)
    }

    @Test
    fun `流式生成中拒绝切换会话`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val provider = ChatGateProvider(gate)
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> provider }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))
        c.openDrawer(PageRef("nb1", "p0001"))

        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()
        assertTrue(c.state.value.messages.last().streaming)
        val streamingSession = c.state.value.currentSessionId!!

        c.switchSession("whatever")
        assertEquals("回复生成中,请先停止", c.state.value.toast)
        assertEquals(streamingSession, c.state.value.currentSessionId)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(AgentMessage.Phase.DONE, c.state.value.messages.last().phase)
    }

    @Test
    fun `会话ID拒绝路径穿越`() {
        val store = AgentStore(File(tmp.newFolder(), "files"), File(tmp.newFolder(), "nb"))
        store.saveSession(AgentSession("s1", "nb1", "p1", 1, 1, "pr", "m"))
        assertNotNull(store.loadSession("nb1", "s1"))
        assertNull(store.loadSession("nb1", "../s1"))
        assertNull(store.loadSession("nb1", "a/b"))
        assertNull(store.loadSession("nb1", ""))
        assertNull(store.loadSession("nb1", ".."))
    }

    @Test
    fun `会话指标优先真实用量`() {
        val att = Attachment("a", "agent/att/x.png", "x", 10, 10, Attachment.Source.SNIP, "snip.png")
        val session = AgentSession(
            id = "s1", notebookId = "nb", pageId = "p", createdAt = 0, updatedAt = 0,
            providerId = "pr", modelId = "m",
            messages = listOf(
                AgentMessage("u1", AgentMessage.Role.USER, 0, "问题问题", attachments = listOf(att)),
                AgentMessage(
                    "a1", AgentMessage.Role.ASSISTANT, 0, "回答",
                    promptTokens = 120, completionTokens = 30,
                    phase = AgentMessage.Phase.DONE.name, generationMs = 1000,
                ),
            ),
        )
        val m = session.computeMetrics()
        assertEquals(150, m.contextTokens)
        assertEquals(30.0, m.avgTokensPerSec!!, 0.001)
        assertEquals(2, m.messageCount)
        assertEquals(1, m.userTurns)
    }

    @Test
    fun `无用量时按图与文本估算且跳过失败消息`() {
        val att = Attachment("a", "agent/att/x.png", "x", 10, 10, Attachment.Source.SNIP, "snip.png")
        val session = AgentSession(
            id = "s2", notebookId = "nb", pageId = "p", createdAt = 0, updatedAt = 0,
            providerId = "pr", modelId = "m",
            messages = listOf(
                AgentMessage("u1", AgentMessage.Role.USER, 0, "ab", attachments = listOf(att)),
                AgentMessage("a1", AgentMessage.Role.ASSISTANT, 0, "半截", phase = AgentMessage.Phase.FAILED.name),
            ),
        )
        val m = session.computeMetrics()
        // 失败消息的文本不计入估算
        assertEquals(ImageBudget.estTokens(1, 2), m.contextTokens)
        assertNull(m.avgTokensPerSec)
    }

    @Test
    fun `生成耗时按首token到完成计时并换算速度`() = runTest {
        var now = 0L
        val provider = TimelineProvider(
            listOf(
                100L to ChatEvent.Delta("答"),
                200L to ChatEvent.Usage(100, 100),
                1100L to ChatEvent.Done(null),
            ),
        ) { now = it }
        val e = agentHarness(
            tmp.newFolder(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            clock = { now },
        ) { _, _, _ -> provider }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))
        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        assertEquals(1000L, c.state.value.messages.last().generationMs)
        assertEquals(100, c.state.value.messages.last().promptTokens)
        assertEquals(100, c.state.value.messages.last().completionTokens)
        val metrics = c.state.value.sessions.single().metrics
        assertEquals(200, metrics.contextTokens)
        assertEquals(100.0, metrics.avgTokensPerSec!!, 0.001)
    }

    @Test
    fun `零耗时时按50ms兜底`() = runTest {
        var now = 0L
        val provider = TimelineProvider(
            listOf(
                0L to ChatEvent.Delta("答"),
                0L to ChatEvent.Usage(10, 10),
                0L to ChatEvent.Done(null),
            ),
        ) { now = it }
        val e = agentHarness(
            tmp.newFolder(),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            clock = { now },
        ) { _, _, _ -> provider }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))
        c.openDrawer(PageRef("nb1", "p0001"))
        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        assertEquals(50L, c.state.value.messages.last().generationMs)
        assertEquals(200.0, c.state.value.sessions.single().metrics.avgTokensPerSec!!, 0.001)
    }

    @Test
    fun `批量会话按更新时间倒序`() {
        val store = AgentStore(File(tmp.newFolder(), "files"), File(tmp.newFolder(), "nb"))
        store.saveSession(AgentSession("old", "nb1", "p1", 1, 100, "pr", "m"))
        store.saveSession(AgentSession("new", "nb1", "p1", 1, 300, "pr", "m"))
        store.saveSession(AgentSession("mid", "nb1", "p2", 1, 200, "pr", "m"))
        store.saveSession(AgentSession("other", "nb2", "p1", 1, 400, "pr", "m"))

        assertEquals(listOf("new", "mid", "old"), store.loadNotebookSessions("nb1").map { it.id })
        assertEquals(listOf("old", "new"), store.loadSessions("nb1", "p1").map { it.id })
    }
}
