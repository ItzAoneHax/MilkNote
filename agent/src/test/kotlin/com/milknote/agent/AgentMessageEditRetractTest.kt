package com.milknote.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// =============================================================================
// 消息操作后端:编辑重发(保留附件/截断后续/忙碌拒绝)与撤回(同步 UI 与磁盘)
// =============================================================================

@OptIn(ExperimentalCoroutinesApi::class)
class MessageEditRetractTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val page = PageRef("nb1", "p0001")

    @Test
    fun `编辑首条消息后旧标题与摘要失效`() = runTest {
        val provider = ControlledProvider()
        val rig = newRig(provider, StandardTestDispatcher(testScheduler))
        val controller = rig.controller

        controller.setDraftText("第一问")
        controller.send()
        advanceUntilIdle()
        val sessionId = controller.state.value.currentSessionId!!
        assertEquals("第一问", rig.store.loadSession("nb1", sessionId)!!.title)

        controller.regenerateSummary(sessionId)
        advanceUntilIdle()
        val summarized = rig.store.loadSession("nb1", sessionId)!!
        assertTrue(summarized.summary.contains("旧摘要"))
        assertTrue(summarized.summaryMessageCount > 0)

        val firstId = controller.state.value.messages.first().id
        assertTrue(controller.editUserMessage(firstId, "新问题"))
        val immediatelySaved = rig.store.loadSession("nb1", sessionId)!!
        assertFalse("编辑返回后旧标题必须立即失效", immediatelySaved.title.contains("第一问"))
        assertEquals("编辑返回后旧摘要必须立即失效", "", immediatelySaved.summary)
        assertEquals(0, immediatelySaved.summaryMessageCount)
        val immediateItem = controller.state.value.sessions.first { it.id == sessionId }
        assertFalse(immediateItem.summary.contains("旧摘要"))

        advanceUntilIdle()

        val saved = rig.store.loadSession("nb1", sessionId)!!
        assertEquals("新问题", saved.title)
        assertEquals("", saved.summary)
        assertEquals(0, saved.summaryMessageCount)
        assertEquals("", saved.summaryModelId)
        assertFalse(saved.title.contains("第一问"))
        assertFalse(saved.summary.contains("旧摘要"))

        val item = controller.state.value.sessions.first { it.id == sessionId }
        assertFalse(item.summary.contains("旧摘要"))
        assertFalse(item.fallbackPreview.contains("第一问"))
    }

    @Test
    fun `撤回首条后旧标题与在途摘要都不能写回`() = runTest {
        val provider = ControlledProvider()
        val rig = newRig(provider, StandardTestDispatcher(testScheduler))
        val controller = rig.controller

        controller.setDraftText("第一问")
        controller.send()
        advanceUntilIdle()
        val sessionId = controller.state.value.currentSessionId!!
        assertEquals("第一问", rig.store.loadSession("nb1", sessionId)!!.title)

        provider.summaryGate = CompletableDeferred()
        controller.regenerateSummary(sessionId)
        advanceUntilIdle()
        assertTrue(controller.state.value.summarizingSessionIds.contains(sessionId))

        val firstId = controller.state.value.messages.first().id
        assertTrue(controller.retractUserMessage(firstId))
        assertFalse(
            "撤回必须取消在途摘要",
            controller.state.value.summarizingSessionIds.contains(sessionId),
        )

        provider.summaryGate!!.complete(Unit)
        provider.summaryGate = null
        advanceUntilIdle()

        val saved = rig.store.loadSession("nb1", sessionId)!!
        assertTrue(saved.messages.isEmpty())
        assertEquals("", saved.title)
        assertEquals("", saved.summary)
        assertEquals(0, saved.summaryMessageCount)
        assertEquals("", saved.summaryModelId)
        assertFalse(saved.title.contains("第一问"))
        assertFalse(saved.summary.contains("旧摘要"))

        val item = controller.state.value.sessions.first { it.id == sessionId }
        assertFalse(item.summary.contains("旧摘要"))
        assertFalse(item.fallbackPreview.contains("第一问"))
    }

    @Test
    fun `发送前置条件失败时编辑不破坏原消息`() = runTest {
        val provider = ControlledProvider()
        val rig = newRig(provider, StandardTestDispatcher(testScheduler))
        val controller = rig.controller

        controller.setDraftText("第一问")
        controller.send()
        advanceUntilIdle()
        val sessionId = controller.state.value.currentSessionId!!
        val firstId = controller.state.value.messages.first().id
        val uiBefore = controller.state.value.messages.map { it.id to it.text }
        val diskBefore = rig.store.loadSession("nb1", sessionId)!!.messages.map { it.id to it.text }

        controller.removeProfile(controller.state.value.activeProfileId!!)
        assertTrue(controller.state.value.setupRequired)

        assertFalse("无可用模型/Key 时编辑必须失败", controller.editUserMessage(firstId, "不该生效"))
        assertEquals("原 UI 消息不得被裁剪", uiBefore, controller.state.value.messages.map { it.id to it.text })
        assertEquals(
            "原磁盘消息不得被改写",
            diskBefore,
            rig.store.loadSession("nb1", sessionId)!!.messages.map { it.id to it.text },
        )
        assertEquals("第一问", controller.state.value.messages.first().text)
    }

    private fun newRig(
        provider: AgentProvider,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): MessageRig {
        val files = File(tmp.newFolder(), "files").apply { mkdirs() }
        val notebooks = File(tmp.newFolder(), "notebooks").apply { mkdirs() }
        val store = AgentStore(files, notebooks)
        val controller = AgentController(
            scope = CoroutineScope(dispatcher),
            store = store,
            snipSource = null,
            annotationSink = null,
            keyFor = { "sk-test" },
            providerFactory = { _, _, _ -> provider },
            workspace = null,
        )
        controller.addProfile("glm", "", "", "sk-test")
        controller.setSummaryConfig(SummaryConfig(enabled = false))
        controller.openDrawer(page)
        return MessageRig(controller, store)
    }

    @Test
    fun `修改用户消息保留多页文档附件并截断后续回合`() = runTest {
        val provider = ControlledProvider()
        val rig = newRig(provider, StandardTestDispatcher(testScheduler))
        val controller = rig.controller

        controller.addDocumentAttachment(
            logicalName = "讲义.md",
            source = Attachment.Source.MARKDOWN_DOC,
            textContent = "原始文档正文",
            extraPages = listOf(
                DraftPageImage(
                    pageNumber = 1,
                    png = "page-one".toByteArray(),
                    jpeg = false,
                    widthPx = 10,
                    heightPx = 10,
                ),
            ),
        )
        controller.setDraftText("第一问")
        controller.send()
        advanceUntilIdle()
        assertEquals(2, controller.state.value.messages.size)
        val original = controller.state.value.messages.first()
        val originalId = original.id
        assertEquals(AgentMessage.Role.USER, original.role)
        assertEquals(1, original.attachments.size)
        assertEquals(1, original.attachments.single().pageFiles.size)

        assertTrue(controller.editUserMessage(originalId, "改后的第二问"))
        advanceUntilIdle()

        assertEquals(2, provider.requests.size)
        val messages = controller.state.value.messages
        assertEquals("旧回合必须被截断", 2, messages.size)
        assertEquals("改后的第二问", messages.first().text)
        assertEquals(AgentMessage.Role.USER, messages.first().role)
        assertEquals("附件必须保留", 1, messages.first().attachments.size)
        assertEquals(1, messages.first().attachments.single().pageFiles.size)
        assertTrue(messages.first().attachments.single().textContent.contains("原始文档正文"))
        assertTrue("后续旧回复必须移除", messages.last().text.isNotBlank())

        val editedWire = provider.requests.last().newUserText
        assertTrue(editedWire, editedWire.contains("改后的第二问"))
        assertTrue(editedWire, editedWire.contains("原始文档正文"))
        assertTrue(
            "历史窗口不得再带旧回合",
            provider.requests.last().history.none { it.content.contains("第一问") },
        )

        val sessionId = controller.state.value.currentSessionId!!
        val saved = rig.store.loadSession("nb1", sessionId)!!
        assertEquals(2, saved.messages.size)
        assertEquals("改后的第二问", saved.messages.first().text)
        assertEquals(1, saved.messages.first().attachments.single().pageFiles.size)
    }

    @Test
    fun `生成中-等待确认-已有草稿时修改失败且不破坏原消息`() = runTest {
        val provider = ControlledProvider()
        val rig = newRig(provider, StandardTestDispatcher(testScheduler))
        val controller = rig.controller

        controller.setDraftText("第一问")
        controller.send()
        advanceUntilIdle()
        val originalId = controller.state.value.messages.first().id

        // 1) 生成中
        provider.gate = CompletableDeferred()
        controller.setDraftText("第二问")
        controller.send()
        advanceUntilIdle()
        assertEquals(AgentMessage.Phase.STREAMING, controller.state.value.phase)
        assertFalse(controller.editUserMessage(originalId, "不该生效"))
        assertEquals(4, controller.state.value.messages.size)
        assertEquals("第一问", controller.state.value.messages.first().text)
        provider.gate!!.complete(Unit)
        provider.gate = null
        advanceUntilIdle()

        // 2) 等待工具确认
        provider.script = { _, i ->
            if (i == 2) {
                listOf(
                    ChatEvent.ToolCallReady(
                        ToolCall("t1", BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID, """{"x1":1,"y1":2}"""),
                    ),
                    ChatEvent.Done(null),
                )
            } else {
                listOf(ChatEvent.Delta("答"), ChatEvent.Done(null))
            }
        }
        controller.setDraftText("第三问")
        controller.send()
        advanceUntilIdle()
        assertTrue(controller.state.value.pendingConfirmation != null)
        assertFalse("等待确认时不能修改", controller.editUserMessage(originalId, "不该生效"))
        assertEquals("第一问", controller.state.value.messages.first().text)
        controller.rejectPendingTool()
        advanceUntilIdle()

        // 3) 已有未发送草稿
        controller.setDraftText("未发送草稿")
        assertFalse("有草稿时不能修改", controller.editUserMessage(originalId, "不该生效"))
        assertEquals("第一问", controller.state.value.messages.first().text)
        assertEquals("未发送草稿", controller.state.value.draftText)
        controller.setDraftText("")
    }

    @Test
    fun `撤回同步裁剪当前会话并立即落盘`() = runTest {
        val provider = ControlledProvider()
        val rig = newRig(provider, StandardTestDispatcher(testScheduler))
        val controller = rig.controller

        controller.setDraftText("第一问")
        controller.send()
        advanceUntilIdle()
        controller.setDraftText("第二问")
        controller.send()
        advanceUntilIdle()
        val sessionId = controller.state.value.currentSessionId!!
        val firstUserId = controller.state.value.messages.first().id
        assertEquals(4, rig.store.loadSession("nb1", sessionId)!!.messages.size)

        assertTrue(controller.retractUserMessage(firstUserId))
        assertTrue("UI 必须同步裁剪", controller.state.value.messages.isEmpty())
        assertTrue(
            "磁盘必须同步裁剪",
            rig.store.loadSession("nb1", sessionId)!!.messages.isEmpty(),
        )
        assertTrue(controller.state.value.sessions.any { it.id == sessionId })

        // 撤回 assistant 消息必须拒绝
        assertFalse(controller.retractUserMessage("not-a-user-message"))
    }

    @Test
    fun `撤回只允许当前会话的用户消息`() = runTest {
        val provider = ControlledProvider()
        val rig = newRig(provider, StandardTestDispatcher(testScheduler))
        val controller = rig.controller

        controller.setDraftText("第一问")
        controller.send()
        advanceUntilIdle()
        val oldSessionId = controller.state.value.currentSessionId!!
        val oldUserId = controller.state.value.messages.first().id
        assertEquals(2, rig.store.loadSession("nb1", oldSessionId)!!.messages.size)

        controller.clearSession()
        assertNotEquals(oldSessionId, controller.state.value.currentSessionId)
        assertFalse("切走后不得撤回旧会话消息", controller.retractUserMessage(oldUserId))
        assertEquals(
            "旧会话磁盘内容不得被改动",
            2,
            rig.store.loadSession("nb1", oldSessionId)!!.messages.size,
        )
    }
}

private class MessageRig(
    val controller: AgentController,
    val store: AgentStore,
)

private class ControlledProvider : AgentProvider {
    override val profileId = "controlled"
    val requests = ArrayList<ChatRequest>()
    var gate: CompletableDeferred<Unit>? = null
    var summaryGate: CompletableDeferred<Unit>? = null
    var summaryText: String = "旧摘要"
    var script: (ChatRequest, Int) -> List<ChatEvent> = { _, _ ->
        listOf(ChatEvent.Delta("答"), ChatEvent.Done(null))
    }
    private var chatCount = 0

    override fun chat(req: ChatRequest) = flow {
        requests.add(req)
        if (req.system == SUMMARY_SYSTEM_PROMPT) {
            summaryGate?.await()
            emit(ChatEvent.Delta(summaryText))
            emit(ChatEvent.Done(null))
            return@flow
        }
        val index = chatCount++
        gate?.await()
        script(req, index).forEach { emit(it) }
    }

    override suspend fun fetchModels(): Result<List<ModelInfo>> = Result.success(emptyList())
    override suspend fun testConnection(): Result<Long> = Result.success(1L)
}
