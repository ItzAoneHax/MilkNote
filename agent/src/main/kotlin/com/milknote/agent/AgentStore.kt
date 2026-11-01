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

    fun latestSession(notebookId: String, pageId: String): AgentSession? =
        loadSessions(notebookId, pageId).lastOrNull()

    fun saveSession(session: AgentSession) {
        val dir = sessionDir(session.notebookId).apply { mkdirs() }
        atomicWrite(File(dir, "${session.id}.json"), json.encodeToString(session))
    }

    fun deleteSession(session: AgentSession) {
        // 级联:引用计数=0 的附件文件删除(§7.2)
        val others = sessionDir(session.notebookId)
            .listFiles { f -> f.name.endsWith(".json") && f.name != "${session.id}.json" }
            ?.mapNotNull { runCatching { json.decodeFromString<AgentSession>(it.readText()) }.getOrNull() }
            ?: emptyList()
        val usedElsewhere = others.flatMap { s -> s.messages.flatMap { m -> m.attachments.map { it.file } } }.toSet()
        for (m in session.messages) {
            for (a in m.attachments) {
                if (a.file !in usedElsewhere) File(notebooksRoot, "${session.notebookId}.milk/${a.file}").delete()
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
    ): Attachment {
        val digest = sha1(png)
        val ext = if (jpeg) "jpg" else "png"
        val rel = "agent/att/$digest.$ext"
        val f = File(notebooksRoot, "$notebookId.milk/$rel")
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            f.writeBytes(png)
        }
        return Attachment(
            id = java.util.UUID.randomUUID().toString().substring(0, 8),
            file = rel,
            sha1 = digest,
            widthPx = widthPx,
            heightPx = heightPx,
            source = source,
            logicalName = logicalName,
        )
    }

    fun readAttachment(notebookId: String, attachment: Attachment): ByteArray? {
        val f = File(notebooksRoot, "$notebookId.milk/${attachment.file}")
        return if (f.exists()) f.readBytes() else null
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
}
