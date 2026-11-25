package com.milknote.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import java.io.File

/** 富附件落盘/安全读取 + @ 提及 + 文档块组装 + 旧 JSON 兼容 */
class AgentRichAttachmentTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(): AgentStore =
        AgentStore(File(tmp.newFolder(), "files"), File(tmp.newFolder(), "notebooks"))

    @Test
    fun `文本附件全文落盘预览封顶`() {
        val store = store()
        val content = "a".repeat(13_000)
        val att = store.storeTextAttachment(
            notebookId = "nb1",
            content = content,
            ext = "md",
            mimeType = "text/markdown",
            source = Attachment.Source.MARKDOWN_DOC,
            logicalName = "讲义.md",
            mentionName = "讲义",
            pageRangeLabel = "",
        )

        assertTrue(att.file.startsWith("agent/att/"))
        assertTrue(att.file.endsWith(".md"))
        assertEquals(AgentStore.TEXT_INLINE_LIMIT, att.textContent.length)
        assertEquals("讲义", att.mentionName)
        assertEquals(content, store.readAttachmentText("nb1", att))   // 全文可读回
        assertNotNull(store.readRelativeFile("nb1", att.file))
    }

    @Test
    fun `多页文档逐页图全落盘`() {
        val store = store()
        val pages = (1..3).map { DraftPageImage(it, byteArrayOf(it.toByte()), false, 10, 10) }
        val draft = DraftAttachment(
            id = "d1", logicalName = "lecture.pdf", widthPx = 0, heightPx = 0,
            png = ByteArray(0), jpeg = false, anchor = null, source = Attachment.Source.PDF_DOC,
            mimeType = "application/pdf", mentionName = "lecture", pageRangeLabel = "第1-3页",
            textContent = "第一页文字", extraPages = pages,
        )

        val att = store.storeRichAttachment("nb1", draft)
        assertEquals(3, att.pageFiles.size)
        assertTrue(att.file.endsWith(".txt"))       // 无封面图 → 正文文件作 file
        assertEquals("第一页文字", store.readAttachmentText("nb1", att))
        att.pageFiles.forEachIndexed { i, rel ->
            assertArrayEquals(byteArrayOf((i + 1).toByte()), store.readRelativeFile("nb1", rel))
        }

        // 有封面图 → file 是封面,逐页图仍全保留
        val withCover = draft.copy(png = byteArrayOf(9))
        val att2 = store.storeRichAttachment("nb1", withCover)
        assertTrue(att2.file.endsWith(".png"))
        assertEquals(3, att2.pageFiles.size)
        assertEquals("第1-3页", att2.pageRangeLabel)
    }

    @Test
    fun `单图与纯Markdown分别落盘`() {
        val store = store()
        val img = DraftAttachment(
            "d2", "snip_10×10.png", 10, 10, byteArrayOf(1, 2), false, null,
            Attachment.Source.SNIP, mentionName = "选区",
        )
        val savedImg = store.storeRichAttachment("nb1", img)
        assertTrue(savedImg.file.endsWith(".png"))
        assertEquals("选区", savedImg.mentionName)
        assertEquals("image/png", savedImg.mimeType)

        val md = DraftAttachment(
            "d3", "notes.md", 0, 0, ByteArray(0), false, null,
            Attachment.Source.MARKDOWN_DOC, mimeType = "text/markdown",
            mentionName = "notes", textContent = "# 标题",
        )
        val savedMd = store.storeRichAttachment("nb1", md)
        assertTrue(savedMd.file.endsWith(".md"))
        assertEquals("# 标题", store.readAttachmentText("nb1", savedMd))
    }

    @Test
    fun `相对路径读取限制在笔记本目录内`() {
        val store = store()
        val att = store.storeTextAttachment(
            "nb1", "hello", "txt", "text/plain", Attachment.Source.TEXT_DOC, "a.txt",
        )
        assertArrayEquals("hello".toByteArray(), store.readRelativeFile("nb1", att.file))
        assertNull(store.readRelativeFile("nb1", "../outside.txt"))
        assertNull(store.readRelativeFile("nb1", "/etc/passwd"))
        assertNull(store.readRelativeFile("nb1", "agent/att/../../outside.txt"))
        assertNull(store.readRelativeFile("nb1", ""))
        assertNull(store.readRelativeFile("nb1", "agent/att/missing.txt"))
        // 附件读取走同一套校验
        assertNull(store.readAttachment("nb1", att.copy(file = "agent/att/../../outside.txt")))
    }

    @Test
    fun `附件自动提及可开关且同名去重`() = runTest {
        val fake = FakeProvider { listOf(ChatEvent.Done(null)) }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.openDrawer(PageRef("nb1", "p0001"))

        c.addSnip(Rect4(0f, 0f, 10f, 10f))
        c.addSnip(Rect4(0f, 0f, 20f, 20f))
        assertEquals("@选区 @选区2 ", c.state.value.draftText)

        val second = c.state.value.draftAttachments.last()
        c.removeDraftAttachment(second.id)
        assertEquals("@选区", c.state.value.draftText)   // 整 token 移除并整理空格

        c.setAutoMentionAttachments(false)
        c.addPageAttachment(PageRef("nb1", "p0002"))
        assertEquals("@选区", c.state.value.draftText)   // 关掉后不再自动追加

        c.setAutoMentionAttachments(true)
        c.appendMentionToDraft("第2页")                  // 手动挑选
        assertEquals("@选区 @第2页 ", c.state.value.draftText)
        c.appendMentionToDraft("@第2页")                  // 重复不追加
        assertEquals("@选区 @第2页 ", c.state.value.draftText)
    }

    @Test
    fun `文档附件正文进入请求且无图`() = runTest {
        val fake = FakeProvider { listOf(ChatEvent.Delta("好"), ChatEvent.Done(null)) }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))
        c.openDrawer(PageRef("nb1", "p0001"))

        c.addDocumentAttachment(
            logicalName = "讲义.md",
            source = Attachment.Source.MARKDOWN_DOC,
            textContent = "# 标题\n正文",
        )
        c.setDraftText("@讲义 请总结")
        c.send()
        advanceUntilIdle()

        val req = fake.requests.single()
        assertEquals(0, req.images.size)
        assertEquals("@讲义 请总结\n\n[附件文档 @讲义]\n# 标题\n正文\n[/附件文档]", req.newUserText)
        // 系统提示已升级:支持完整 Markdown/LaTeX + milkdoc 文档块
        assertTrue(req.system.contains("milkdoc"))
        assertTrue(req.system.contains("\$\$"))

        val att = c.state.value.messages.first().attachments.single()
        assertEquals(Attachment.Source.MARKDOWN_DOC, att.source)
        assertEquals("讲义", att.mentionName)
        assertTrue(File(e.notebooksRoot, "nb1.milk/${att.file}").exists())
    }

    @Test
    fun `多页文档逐页发送并标注图名`() = runTest {
        val fake = FakeProvider { listOf(ChatEvent.Delta("好"), ChatEvent.Done(null)) }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))
        c.openDrawer(PageRef("nb1", "p0001"))

        c.addDocumentAttachment(
            logicalName = "课件.pptx",
            source = Attachment.Source.PPT_DOC,
            extraPages = (1..2).map { DraftPageImage(it, byteArrayOf(it.toByte()), false, 10, 10) },
        )
        c.setDraftText("@课件 讲一下")
        c.send()
        advanceUntilIdle()

        val req = fake.requests.last()
        assertEquals(2, req.images.size)
        assertTrue(req.newUserText.startsWith("[图1] [图2] (图片对应:"))
        assertTrue(req.newUserText.contains("[图1]=@课件 第1页"))
        assertTrue(req.newUserText.contains("[图2]=@课件 第2页"))
        assertTrue(req.newUserText.endsWith("@课件 讲一下"))

        val att = c.state.value.messages.first().attachments.single()
        assertEquals(2, att.pageFiles.size)
        assertTrue(att.textContent.isBlank())
    }

    @Test
    fun `多页文档超模型上限时截断并提示`() = runTest {
        val fake = FakeProvider { listOf(ChatEvent.Delta("好"), ChatEvent.Done(null)) }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))
        c.openDrawer(PageRef("nb1", "p0001"))

        c.addDocumentAttachment(
            logicalName = "大课件.pdf",
            source = Attachment.Source.PDF_DOC,
            extraPages = (1..12).map { DraftPageImage(it, byteArrayOf(it.toByte()), false, 10, 10) },
        )
        c.send()
        advanceUntilIdle()

        // glm 预设 maxImages=8
        assertEquals(8, fake.requests.single().images.size)
        assertTrue(c.state.value.toast?.contains("截去") == true)
    }

    @Test
    fun `系统图片附件可直接入草稿`() = runTest {
        val fake = FakeProvider { listOf(ChatEvent.Delta("好"), ChatEvent.Done(null)) }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))
        c.openDrawer(PageRef("nb1", "p0001"))

        c.addSystemImageAttachment(byteArrayOf(7, 7, 7), widthPx = 20, heightPx = 20)
        assertEquals(Attachment.Source.SYSTEM_IMAGE, c.state.value.draftAttachments.single().source)
        assertEquals("@图片 ", c.state.value.draftText)

        c.send()
        advanceUntilIdle()
        assertEquals(1, fake.requests.single().images.size)
        val att = c.state.value.messages.first().attachments.single()
        assertEquals(Attachment.Source.SYSTEM_IMAGE, att.source)
        assertEquals("图片", att.mentionName)
    }

    @Test
    fun `笔记页范围附件逐页发送`() = runTest {
        val fake = FakeProvider { listOf(ChatEvent.Delta("好"), ChatEvent.Done(null)) }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))
        c.openDrawer(PageRef("nb1", "p0001"))

        val name = c.addPageRangeAttachment(
            listOf(PageRef("nb1", "p0002"), PageRef("nb1", "p0003")),
        )
        assertNotNull(name)
        assertEquals("第2-3页", c.state.value.draftAttachments.single().pageRangeLabel)
        assertEquals("@第2-3页 ", c.state.value.draftText)

        c.send()
        advanceUntilIdle()
        assertEquals(2, fake.requests.single().images.size)
        val att = c.state.value.messages.first().attachments.single()
        assertEquals(2, att.pageFiles.size)
        assertEquals("第2-3页", att.pageRangeLabel)
    }

    @Test
    fun `草稿图片可就地替换且保留提及与位置`() = runTest {
        val fake = FakeProvider { listOf(ChatEvent.Delta("好"), ChatEvent.Done(null)) }
        val e = agentHarness(tmp.newFolder(), CoroutineScope(StandardTestDispatcher(testScheduler))) { _, _, _ -> fake }
        val c = e.controller
        c.setSummaryConfig(SummaryConfig(enabled = false))
        c.openDrawer(PageRef("nb1", "p0001"))

        c.addSnip(Rect4(0f, 0f, 100f, 50f))
        val draft = c.state.value.draftAttachments.single()
        assertTrue(c.updateDraftAttachment(draft.id, byteArrayOf(9, 9, 9), jpeg = true, widthPx = 42, heightPx = 24))

        val edited = c.state.value.draftAttachments.single()
        assertEquals(draft.id, edited.id)
        assertEquals("选区", edited.mentionName)          // @ 名保留
        assertEquals(draft.anchor, edited.anchor)          // 位置保留
        assertEquals(42, edited.widthPx)
        assertEquals(24, edited.heightPx)
        assertTrue(edited.jpeg)
        assertEquals("image/jpeg", edited.mimeType)
        assertEquals("@选区 ", c.state.value.draftText)

        // 不存在 / 空图 → false 且状态不变
        assertFalse(c.updateDraftAttachment("missing", byteArrayOf(1), false, 1, 1))
        assertFalse(c.updateDraftAttachment(draft.id, ByteArray(0), false, 1, 1))
        assertEquals(42, c.state.value.draftAttachments.single().widthPx)

        c.sendWithIntent(Intents.Id.EXPLAIN)
        advanceUntilIdle()

        val req = fake.requests.single()
        assertEquals(listOf("image/jpeg"), req.imageMediaTypes)
        assertEquals(java.util.Base64.getEncoder().encodeToString(byteArrayOf(9, 9, 9)), req.images.single())
        val att = c.state.value.messages.first().attachments.single()
        assertEquals(42, att.widthPx)
        assertEquals(24, att.heightPx)
        assertEquals("image/jpeg", att.mimeType)
        assertEquals("选区", att.mentionName)
    }

    @Test
    fun `旧版JSON可反序列化`() {
        val json = Json { ignoreUnknownKeys = true }

        val att = json.decodeFromString<Attachment>(
            """{"id":"a","file":"agent/att/x.png","sha1":"x","widthPx":1,"heightPx":1,"source":"SNIP","logicalName":"s.png"}""",
        )
        assertEquals("image/png", att.mimeType)
        assertEquals("", att.mentionName)
        assertEquals("", att.textContent)
        assertTrue(att.pageFiles.isEmpty())
        assertTrue(att.isImage)

        val msg = json.decodeFromString<AgentMessage>("""{"id":"m","role":"USER","sentAt":1,"text":"t"}""")
        assertEquals(0L, msg.generationMs)
        assertEquals(AgentMessage.Phase.DONE.name, msg.phase)

        val session = json.decodeFromString<AgentSession>(
            """{"id":"s","notebookId":"nb","pageId":"p","createdAt":1,"updatedAt":2,"providerId":"pr","modelId":"m"}""",
        )
        assertEquals("", session.summary)
        assertEquals("", session.title)
        assertEquals(0, session.summaryMessageCount)
        assertEquals(0L, session.summaryUpdatedAt)
        assertTrue(session.messages.isEmpty())

        val book = json.decodeFromString<AgentStore.ProfileBook>("""{"profiles":[],"activeProfileId":null}""")
        assertTrue(book.summaryConfig.enabled)
        assertEquals(SummaryModelMode.INHERIT_CHAT, book.summaryConfig.mode)
        assertNull(book.summaryConfig.profileId)
        assertTrue(book.autoMentionAttachments)
    }

    @Test
    fun `新字段可往返序列化`() {
        val json = Json { ignoreUnknownKeys = true }
        val session = AgentSession(
            id = "s1", notebookId = "nb", pageId = "p", createdAt = 1, updatedAt = 2,
            providerId = "pr", modelId = "m",
            messages = listOf(
                AgentMessage(
                    id = "a1", role = AgentMessage.Role.ASSISTANT, sentAt = 3, text = "答",
                    attachments = listOf(
                        Attachment(
                            id = "at", file = "agent/att/x.md", sha1 = "x", widthPx = 0, heightPx = 0,
                            source = Attachment.Source.MARKDOWN_DOC, logicalName = "doc.md",
                            mimeType = "text/markdown", mentionName = "doc",
                            pageRangeLabel = "第1-2页", textContent = "正文",
                            pageFiles = listOf("agent/att/y.png"),
                        ),
                    ),
                    generationMs = 1234,
                ),
            ),
            title = "标题", summary = "摘要", summaryUpdatedAt = 9,
            summaryMessageCount = 2, summaryModelId = "m2",
        )
        val round = json.decodeFromString<AgentSession>(json.encodeToString(AgentSession.serializer(), session))
        assertEquals(session, round)
        assertFalse(round.messages.single().attachments.single().pageFiles.isEmpty())
    }
}
