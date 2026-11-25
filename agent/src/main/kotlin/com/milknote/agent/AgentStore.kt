package com.milknote.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * 存储(§3.2 / §7.2):
 * - 全局:filesDir/agent/profiles.json(Provider 配置;Key 在 KeyVault,不在此)
 * - 随笔记本:<nb>.milk/agent/sessions/<id>.json + att/<sha1>.png(整本备份带走,§A7)
 * - 写策略:消息完成时整文件重写;流式过程不落盘;附件发送时落盘
 */
class AgentStore(
    private val filesRoot: File,       // context.filesDir
    private val notebooksRoot: File,   // filesDir/notebooks(.milk 目录集合)
) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // ---- Provider 配置 ----

    @Serializable
    data class ProfileBook(
        val profiles: List<ProviderProfile> = emptyList(),
        val activeProfileId: String? = null,
        /** 会话摘要配置(默认开启;随配置落盘,跨设备一致) */
        val summaryConfig: SummaryConfig = SummaryConfig(),
        /** 附件自动 @ 提及(默认开;发送时把 @名 追加进草稿文字) */
        val autoMentionAttachments: Boolean = true,
    )

    private val profilesFile: File get() = File(filesRoot, "agent/profiles.json")

    @Synchronized
    fun loadProfiles(): ProfileBook = runCatching {
        if (!profilesFile.exists()) return ProfileBook()
        json.decodeFromString<ProfileBook>(profilesFile.readText())
    }.getOrDefault(ProfileBook())

    @Synchronized
    fun saveProfiles(book: ProfileBook) {
        profilesFile.parentFile?.mkdirs()
        atomicWrite(profilesFile, json.encodeToString(book))
    }

    // ---- 会话(每 (notebook,page) 至多恢复最近一个;§7.1 生命周期) ----

    private fun agentDir(notebookId: String): File = File(notebooksRoot, "$notebookId.milk/agent")
    private fun sessionDir(notebookId: String): File = File(agentDir(notebookId), "sessions")
    private fun attDir(notebookId: String): File = File(agentDir(notebookId), "att")

    fun loadSessions(notebookId: String, pageId: String): List<AgentSession> = runCatching {
        val dir = sessionDir(notebookId)
        if (!dir.exists()) return emptyList()
        dir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { runCatching { json.decodeFromString<AgentSession>(it.readText()) }.getOrNull() }
            ?.filter { it.pageId == pageId }
            ?.sortedBy { it.updatedAt }
            ?: emptyList()
    }.getOrDefault(emptyList())

    /** 整本会话(按 updatedAt 倒序;历史会话列表消费) */
    fun loadNotebookSessions(notebookId: String): List<AgentSession> = runCatching {
        val dir = sessionDir(notebookId)
        if (!dir.exists()) return emptyList()
        dir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { runCatching { json.decodeFromString<AgentSession>(it.readText()) }.getOrNull() }
            ?.filter { it.notebookId == notebookId }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }.getOrDefault(emptyList())

    /** 精确加载(校验 id 不含路径分隔符,防目录穿越) */
    fun loadSession(notebookId: String, sessionId: String): AgentSession? = runCatching {
        if (!isSafeSegment(sessionId)) return null
        val f = File(sessionDir(notebookId), "$sessionId.json")
        if (!f.isFile) return null
        json.decodeFromString<AgentSession>(f.readText())
    }.getOrNull()

    private fun isSafeSegment(segment: String): Boolean =
        segment.isNotBlank() &&
            !segment.contains('/') && !segment.contains('\\') &&
            !segment.contains("..") && !segment.contains(':')

    fun latestSession(notebookId: String, pageId: String): AgentSession? =
        loadSessions(notebookId, pageId).lastOrNull()

    fun saveSession(session: AgentSession) {
        val dir = sessionDir(session.notebookId).apply { mkdirs() }
        atomicWrite(File(dir, "${session.id}.json"), json.encodeToString(session))
    }

    fun deleteSession(session: AgentSession) {
        // 级联:引用计数=0 的附件文件删除(§7.2;含多页文档逐页图)
        val others = sessionDir(session.notebookId)
            .listFiles { f -> f.name.endsWith(".json") && f.name != "${session.id}.json" }
            ?.mapNotNull { runCatching { json.decodeFromString<AgentSession>(it.readText()) }.getOrNull() }
            ?: emptyList()
        val usedElsewhere = others
            .flatMap { s -> s.messages.flatMap { m -> m.attachments.flatMap { it.relativeFiles() } } }
            .toSet()
        for (m in session.messages) {
            for (a in m.attachments) {
                for (rel in a.relativeFiles()) {
                    if (rel in usedElsewhere || !isSafeRelativePath(rel)) continue
                    File(notebooksRoot, "${session.notebookId}.milk/$rel").delete()
                }
            }
        }
        File(sessionDir(session.notebookId), "${session.id}.json").delete()
    }

    // ---- 附件(内容寻址去重) ----

    fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    /** 落盘附件;返回 Attachment(file = agent/att/<sha1>.png) */
    fun storeAttachment(
        notebookId: String,
        png: ByteArray,
        jpeg: Boolean,
        widthPx: Int,
        heightPx: Int,
        source: Attachment.Source,
        logicalName: String,
        mentionName: String = "",
        pageRangeLabel: String = "",
        textContent: String = "",
    ): Attachment {
        val (rel, digest) = writeBlob(notebookId, png, if (jpeg) "jpg" else "png")
        return Attachment(
            id = newAttachmentId(),
            file = rel,
            sha1 = digest,
            widthPx = widthPx,
            heightPx = heightPx,
            source = source,
            logicalName = logicalName,
            mimeType = if (jpeg) "image/jpeg" else "image/png",
            mentionName = mentionName,
            pageRangeLabel = pageRangeLabel,
            textContent = textContent.take(TEXT_INLINE_LIMIT),
        )
    }

    /**
     * 落盘文本/Markdown 文档:全文存 `agent/att/<sha1>.<ext>`,
     * JSON 里只留 ≤[TEXT_INLINE_LIMIT] 的预览(全文可经 [readAttachmentText] 读回)。
     */
    fun storeTextAttachment(
        notebookId: String,
        content: String,
        ext: String,
        mimeType: String,
        source: Attachment.Source,
        logicalName: String,
        mentionName: String = "",
        pageRangeLabel: String = "",
    ): Attachment {
        val safeExt = ext.trim().trimStart('.').ifBlank { "txt" }
        val (rel, digest) = writeBlob(notebookId, content.toByteArray(Charsets.UTF_8), safeExt)
        return Attachment(
            id = newAttachmentId(),
            file = rel,
            sha1 = digest,
            widthPx = 0,
            heightPx = 0,
            source = source,
            logicalName = logicalName,
            mimeType = mimeType,
            mentionName = mentionName,
            pageRangeLabel = pageRangeLabel,
            textContent = content.take(TEXT_INLINE_LIMIT),
        )
    }

    /**
     * 富附件落盘(发送时):
     * - 单图(选区/整页/系统图) → `<sha1>.png|jpg`
     * - 纯文本/Markdown(无图) → `<sha1>.md|txt`
     * - 多页文档(PDF/PPT/页范围) → 封面图作 file,逐页图全进 pageFiles,正文进 textContent
     *   (无封面且正文非空时 file 直接是文本文件)
     */
    fun storeRichAttachment(notebookId: String, draft: DraftAttachment): Attachment {
        val pages = draft.extraPages
        val inlineText = draft.textContent.take(TEXT_INLINE_LIMIT)

        if (pages.isEmpty()) {
            if (draft.png.isEmpty()) {
                return storeTextAttachment(
                    notebookId = notebookId,
                    content = draft.textContent,
                    ext = extForSource(draft.source),
                    mimeType = draft.mimeType.ifBlank { mimeForSource(draft.source) },
                    source = draft.source,
                    logicalName = draft.logicalName,
                    mentionName = draft.mentionName,
                    pageRangeLabel = draft.pageRangeLabel,
                )
            }
            val (rel, digest) = writeBlob(notebookId, draft.png, if (draft.jpeg) "jpg" else "png")
            return Attachment(
                id = newAttachmentId(),
                file = rel,
                sha1 = digest,
                widthPx = draft.widthPx,
                heightPx = draft.heightPx,
                source = draft.source,
                logicalName = draft.logicalName,
                mimeType = draft.mimeType.ifBlank { if (draft.jpeg) "image/jpeg" else "image/png" },
                mentionName = draft.mentionName,
                pageRangeLabel = draft.pageRangeLabel,
                textContent = inlineText,
            )
        }

        val pageFiles = pages.map { p -> writeBlob(notebookId, p.png, if (p.jpeg) "jpg" else "png").first }
        val cover: Pair<String, String> = when {
            draft.png.isNotEmpty() ->
                writeBlob(notebookId, draft.png, if (draft.jpeg) "jpg" else "png")
            draft.textContent.isNotBlank() -> {
                val (rel, digest) = writeBlob(
                    notebookId,
                    draft.textContent.toByteArray(Charsets.UTF_8),
                    extForSource(draft.source),
                )
                rel to digest
            }
            else -> pageFiles.first() to sha1(pages.first().png)
        }
        return Attachment(
            id = newAttachmentId(),
            file = cover.first,
            sha1 = cover.second,
            widthPx = draft.widthPx,
            heightPx = draft.heightPx,
            source = draft.source,
            logicalName = draft.logicalName,
            mimeType = draft.mimeType.ifBlank { if (draft.jpeg) "image/jpeg" else "image/png" },
            mentionName = draft.mentionName,
            pageRangeLabel = draft.pageRangeLabel,
            textContent = inlineText,
            pageFiles = pageFiles,
        )
    }

    /** 通用相对路径读取:与 [readAttachment] 同一套 canonical 包含性校验(防穿越) */
    fun readRelativeFile(notebookId: String, relativePath: String): ByteArray? = runCatching {
        if (!isSafeRelativePath(relativePath)) return null
        val root = File(notebooksRoot, "$notebookId.milk").canonicalFile
        val f = File(root, relativePath).canonicalFile
        if (f.toPath().startsWith(root.toPath()) && f != root && f.isFile) f.readBytes() else null
    }.getOrNull()

    /** 文档正文:文本类文件读全文,图片类回落到 JSON 内预览 */
    fun readAttachmentText(notebookId: String, attachment: Attachment): String? {
        val ext = attachment.file.substringAfterLast('.', "").lowercase()
        if (ext in TEXT_EXTS) {
            val bytes = readRelativeFile(notebookId, attachment.file)
            if (bytes != null) return bytes.toString(Charsets.UTF_8)
        }
        return attachment.textContent.ifBlank { null }
    }

    private fun writeBlob(notebookId: String, bytes: ByteArray, ext: String): Pair<String, String> {
        val digest = sha1(bytes)
        val rel = "agent/att/$digest.$ext"
        val f = File(notebooksRoot, "$notebookId.milk/$rel")
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
        }
        return rel to digest
    }

    fun readAttachment(notebookId: String, attachment: Attachment): ByteArray? = runCatching {
        if (!isSafeRelativePath(attachment.file)) return@runCatching null
        val root = File(notebooksRoot, "$notebookId.milk").canonicalFile
        val f = File(root, attachment.file).canonicalFile
        if (f.toPath().startsWith(root.toPath()) && f != root && f.isFile) f.readBytes() else null
    }.getOrNull()

    private fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() &&
            !path.startsWith('/') && !path.startsWith('\\') &&
            path.replace('\\', '/').split('/').none { it == ".." || it == "." } &&
            !path.contains(':')

    private fun newAttachmentId(): String = java.util.UUID.randomUUID().toString().substring(0, 8)

    private fun extForSource(source: Attachment.Source): String = when (source) {
        Attachment.Source.MARKDOWN_DOC -> "md"
        Attachment.Source.TEXT_DOC -> "txt"
        else -> "txt"
    }

    private fun mimeForSource(source: Attachment.Source): String = when (source) {
        Attachment.Source.MARKDOWN_DOC -> "text/markdown"
        Attachment.Source.TEXT_DOC -> "text/plain"
        Attachment.Source.PDF_DOC -> "application/pdf"
        Attachment.Source.PPT_DOC -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        else -> "image/png"
    }

    private fun atomicWrite(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "atomic write failed: $target" }
        }
    }

    companion object {
        /** JSON 内联文档正文上限(全文另存文件;超限只留预览) */
        const val TEXT_INLINE_LIMIT = 12000

        /** 文本类扩展名(读回时按 UTF-8 解码) */
        val TEXT_EXTS: Set<String> = setOf("md", "markdown", "txt", "tex", "latex")
    }
}

/** 附件引用的全部相对文件(file + 多页逐页图;去重去空) */
fun Attachment.relativeFiles(): List<String> =
    (listOf(file) + pageFiles).filter { it.isNotBlank() }.distinct()
