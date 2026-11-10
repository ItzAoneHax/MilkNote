package com.milknote.notes.storage

import com.google.protobuf.CodedInputStream
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.MediaElement
import com.milknote.ink.proto.StrokeRecord
import com.milknote.ink.serialize.ProtoInk
import com.milknote.notes.model.NotebookMeta
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * MilkNote `.milk.zip` 备份导入失败。所有消息都是可直接展示给用户的中文。
 *
 * 分层类型便于调用方区分：非 MilkNote 容器、路径不安全、重复条目、体积超限、内容损坏。
 */
open class MilkImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** ZIP 不是一个合法的 MilkNote `.milk` 备份(根目录/元数据缺失等)。 */
class NotMilkArchiveException(message: String, cause: Throwable? = null) :
    MilkImportException(message, cause)

/** ZIP 内路径是绝对路径、包含 `..` 穿越或非法字符。 */
class MilkImportUnsafePathException(message: String, cause: Throwable? = null) :
    MilkImportException(message, cause)

/** ZIP 内存在重复条目或重复的页面 ID。 */
class MilkImportDuplicateException(message: String, cause: Throwable? = null) :
    MilkImportException(message, cause)

/** ZIP 条目数或解压体积超出导入上限。 */
class MilkImportTooLargeException(message: String, cause: Throwable? = null) :
    MilkImportException(message, cause)

/** notebook.json 或页面 `.pb` 数据损坏。 */
class MilkImportCorruptException(message: String, cause: Throwable? = null) :
    MilkImportException(message, cause)

/** 导入成功后的结果(新笔记本 ID 与落盘目录)。 */
data class MilkImportResult(
    val notebookId: String,
    val notebookDir: File,
    val meta: NotebookMeta,
    val pageCount: Int,
)

/**
 * MilkNote 自有 `.milk.zip` 备份导入(纯 JVM/domain,同步 IO)。
 *
 * 导出侧见 PageExporter.saveZipBackup:ZIP 内只有一个 `<笔记名>.milk/` 根目录,
 * 根目录下包含 notebook.json、`p*.pb`、`<pageId>.ann.json` / `<pageId>.img.json`
 * sidecar、`images/` 共享图片以及 `agent/` 会话与附件。
 *
 * 导入策略:
 * - 严格校验(单个 .milk 根、notebook.json 可解码、路径安全、无重复条目/页面 ID、
 *   pageOrder 合法、pb 可解析、条目数与体积上限);任何失败都抛 [MilkImportException]。
 * - **绝不覆盖已有笔记本**:先解压到 `notebooks/` 内的临时 staging 目录,生成新的
 *   8 位十六进制 notebook id,改写 notebook.json 的 id 后用 rename 原子落盘;
 *   失败时递归清理 staging,不留下半成品。
 * - 页面/图片/Agent 等文件按原相对路径原样保留,页序写入 notebook.json 的 pageOrder。
 *
 * 这是同步阻塞 API;调用方(Android UI)应放到 IO 协程/线程执行。
 */
object MilkArchiveImporter {

    /** 单个备份最多条目数。 */
    const val MAX_ENTRIES = 10_000

    /** 压缩包本体大小上限(256 MiB)。 */
    const val MAX_ARCHIVE_BYTES = 256L * 1024 * 1024

    /** 解压后总大小上限(512 MiB),同时防御 zip bomb。 */
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 512L * 1024 * 1024

    /** 单个条目解压后大小上限(128 MiB)。 */
    const val MAX_ENTRY_BYTES = 128L * 1024 * 1024

    /** notebook.json 大小上限。 */
    const val MAX_METADATA_BYTES = 1L * 1024 * 1024

    private const val BUFFER_SIZE = 64 * 1024
    private const val MILK_SUFFIX = ".milk"
    private val PAGE_ID = Regex("[A-Za-z0-9_-]{1,128}")
    private val DRIVE_PREFIX = Regex("^[A-Za-z]:")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 从文件导入并接管 [ZipFile] 生命周期。
     *
     * @param zipFile `.milk.zip` 文件
     * @param notebooksRoot 笔记本根目录(即 `filesDir/notebooks`;不存在时自动创建)
     */
    fun import(zipFile: File, notebooksRoot: File): MilkImportResult {
        if (!zipFile.isFile) {
            throw MilkImportException("导入失败：找不到备份文件")
        }
        if (zipFile.length() > MAX_ARCHIVE_BYTES) {
            throw MilkImportTooLargeException("备份文件过大，超过 ${MAX_ARCHIVE_BYTES / (1024 * 1024)}MB 上限")
        }
        val zip = try {
            ZipFile(zipFile)
        } catch (e: ZipException) {
            throw NotMilkArchiveException("不是 MilkNote 备份：ZIP 文件已损坏或格式不正确", e)
        } catch (e: IOException) {
            throw MilkImportException("导入失败：无法读取备份文件", e)
        }
        return zip.use { import(it, notebooksRoot) }
    }

    /**
     * 从已打开的 [ZipFile] 导入。调用方负责关闭 [zip](本方法不会关闭它)。
     *
     * @param zip `.milk.zip` 备份
     * @param notebooksRoot 笔记本根目录(即 `filesDir/notebooks`;不存在时自动创建)
     */
    fun import(zip: ZipFile, notebooksRoot: File): MilkImportResult {
        ensureRoot(notebooksRoot)

        // ---- 1. 条目清单:规范化、去重、限额 ----
        val entries = readEntries(zip)
        if (entries.isEmpty()) {
            throw NotMilkArchiveException("不是 MilkNote 备份：ZIP 是空的")
        }

        // ---- 2. 必须恰好一个 .milk 根目录 ----
        val rootNames = entries.map { it.normalized.substringBefore('/') }.toSet()
        val root = rootNames.singleOrNull()
            ?: throw NotMilkArchiveException("不是 MilkNote 备份：ZIP 必须只包含一个 .milk 笔记目录")
        val rootBase = root.removeSuffix(MILK_SUFFIX)
        if (!root.lowercase(Locale.ROOT).endsWith(MILK_SUFFIX) || rootBase.isBlank()) {
            throw NotMilkArchiveException("不是 MilkNote 备份：ZIP 根目录不是 .milk 笔记目录")
        }

        // ---- 3. notebook.json 必须存在且可解码 ----
        val metaEntry = entries.firstOrNull { it.normalized == "$root/notebook.json" }
        if (metaEntry == null || metaEntry.entry.isDirectory) {
            throw NotMilkArchiveException("不是 MilkNote 备份：缺少 notebook.json")
        }
        val meta = decodeMeta(readEntryBytes(zip, metaEntry.entry, MAX_METADATA_BYTES, metaEntry.normalized))
        if (meta.id.isBlank()) {
            throw MilkImportCorruptException("笔记元数据 notebook.json 损坏：缺少有效的笔记 ID")
        }

        // ---- 4. 页面 ID 与页序校验 ----
        val pageIds = collectPageIds(entries, root)
        val finalOrder = resolvePageOrder(meta, pageIds)
        validatePageSpecs(meta, pageIds)

        // ---- 5. 解压到 staging,失败时整体清理 ----
        val staging = File(notebooksRoot, ".milk-import-${UUID.randomUUID()}.staging")
        if (!staging.mkdirs()) {
            throw MilkImportException("导入失败：无法创建导入临时目录（请检查存储空间）")
        }
        var succeeded = false
        try {
            extract(zip, entries, root, staging)
            pageIds.forEach { pageId -> validatePageProto(File(staging, "$pageId.pb"), "$pageId.pb") }
            validateImageSidecars(staging)

            val newId = freshNotebookId(notebooksRoot)
            rewriteAgentSessions(staging, newId)
            val importedMeta = meta.copy(
                id = newId,
                name = meta.name.trim().ifBlank { "恢复的笔记" },
                pageOrder = finalOrder,
                updatedAt = System.currentTimeMillis(),
                lastOpened = System.currentTimeMillis(),
            )
            writeMetaAtomically(staging, importedMeta)

            val target = File(notebooksRoot, "$newId$MILK_SUFFIX")
            if (target.exists()) {
                throw MilkImportException("导入失败：目标笔记本目录已存在，请重试")
            }
            if (!staging.renameTo(target)) {
                throw MilkImportException("导入失败：无法写入笔记本目录（请检查存储空间）")
            }
            succeeded = true
            return MilkImportResult(newId, target, importedMeta, pageIds.size)
        } finally {
            if (!succeeded) {
                runCatching { staging.deleteRecursively() }
            }
        }
    }

    // ---- 条目读取与校验 ----

    private data class Entry(val entry: ZipEntry, val normalized: String)

    private fun readEntries(zip: ZipFile): List<Entry> {
        val enumeration = zip.entries()
        val seen = HashSet<String>()
        val result = ArrayList<Entry>()
        var declaredTotal = 0L
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            if (result.size >= MAX_ENTRIES) {
                throw MilkImportTooLargeException("备份文件条目过多，超过 $MAX_ENTRIES 个上限")
            }
            val normalized = normalizeEntryName(entry.name)
            if (!seen.add(normalized)) {
                throw MilkImportDuplicateException("备份包含重复条目：$normalized")
            }
            val declared = entry.size
            if (declared > MAX_ENTRY_BYTES) {
                throw MilkImportTooLargeException("备份条目过大：$normalized")
            }
            if (declared > 0) {
                declaredTotal += declared
                if (declaredTotal > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw MilkImportTooLargeException("备份解压后体积超过 ${MAX_TOTAL_UNCOMPRESSED_BYTES / (1024 * 1024)}MB 上限")
                }
            }
            result += Entry(entry, normalized)
        }
        return result
    }

    /**
     * 统一分隔符为 `/`,并拒绝绝对路径、盘符、`..` 穿越、空段与控制字符。
     * 反斜杠一律视为分隔符(Windows 打包工具可能写入 `root\\file`）。
     */
    private fun normalizeEntryName(raw: String): String {
        if (raw.isEmpty()) throw MilkImportUnsafePathException("备份包含空的条目名")
        if (raw.indexOf('\u0000') >= 0) {
            throw MilkImportUnsafePathException("备份包含非法条目名（含 NUL 字符）")
        }
        val unified = raw.replace('\\', '/')
        if (unified.startsWith("/")) {
            throw MilkImportUnsafePathException("备份包含绝对路径条目：$raw")
        }
        if (DRIVE_PREFIX.containsMatchIn(unified)) {
            throw MilkImportUnsafePathException("备份包含绝对路径条目：$raw")
        }
        val isDirectory = unified.endsWith("/")
        val body = if (isDirectory) unified.dropLast(1) else unified
        val segments = body.split('/')
        segments.forEach { segment ->
            when {
                segment.isEmpty() -> throw MilkImportUnsafePathException("备份包含非法路径条目：$raw")
                segment == "." || segment == ".." ->
                    throw MilkImportUnsafePathException("备份包含路径穿越条目：$raw")
                ':' in segment || segment.any { it.code < 0x20 } ->
                    throw MilkImportUnsafePathException("备份包含非法路径条目：$raw")
            }
        }
        return segments.joinToString("/")
    }

    private fun collectPageIds(entries: List<Entry>, root: String): List<String> {
        val ids = ArrayList<String>()
        val lowerSeen = HashSet<String>()
        val prefix = "$root/"
        entries.forEach { item ->
            val relative = item.normalized.removePrefix(prefix)
            if (relative == item.normalized) return@forEach // 根目录条目自身
            if (relative.contains('/') || !relative.endsWith(".pb") || item.entry.isDirectory) return@forEach
            val pageId = relative.removeSuffix(".pb")
            if (!PAGE_ID.matches(pageId)) {
                throw MilkImportCorruptException("备份包含非法页面文件名：$relative")
            }
            if (!lowerSeen.add(pageId.lowercase(Locale.ROOT))) {
                throw MilkImportDuplicateException("备份包含重复的页面 ID：$pageId")
            }
            ids += pageId
        }
        return ids
    }

    /** 只保留仍存在的页;缺失页按 pageId 字典序补到末尾(与 NotebookRepository 的兼容策略一致)。 */
    private fun resolvePageOrder(meta: NotebookMeta, pageIds: List<String>): List<String> {
        val order = meta.pageOrder
        val lowered = order.map { it.lowercase(Locale.ROOT) }
        if (lowered.size != lowered.toSet().size) {
            throw MilkImportCorruptException("笔记元数据 pageOrder 包含重复页面 ID")
        }
        order.forEach { pageId ->
            if (!PAGE_ID.matches(pageId)) {
                throw MilkImportCorruptException("笔记元数据 pageOrder 包含非法页面 ID：$pageId")
            }
        }
        val existing = pageIds.toSet()
        val stored = order.filter { it in existing }
        return stored + pageIds.filter { it !in stored }.sorted()
    }

    private fun decodeMeta(bytes: ByteArray): NotebookMeta = try {
        json.decodeFromString<NotebookMeta>(bytes.decodeToString())
    } catch (e: Exception) {
        throw MilkImportCorruptException("笔记元数据 notebook.json 损坏，无法解析", e)
    }

    // ---- 解压 ----

    private fun extract(zip: ZipFile, entries: List<Entry>, root: String, staging: File) {
        val base = staging.canonicalFile
        var actualTotal = 0L
        entries.forEach { item ->
            val relative = item.normalized.removePrefix("$root/")
            if (relative == item.normalized || relative.isEmpty()) return@forEach // 根目录条目
            if (relative == "notebook.json") return@forEach // 稍后改写为新 ID 的元数据
            val target = File(staging, relative)
            val canonical = try {
                target.canonicalFile
            } catch (e: IOException) {
                throw MilkImportUnsafePathException("备份包含非法路径条目：${item.normalized}", e)
            }
            if (!canonical.path.startsWith(base.path + File.separator)) {
                throw MilkImportUnsafePathException("备份包含路径穿越条目：${item.normalized}")
            }
            if (item.entry.isDirectory) {
                target.mkdirs()
                return@forEach
            }
            target.parentFile?.mkdirs()
            zip.getInputStream(item.entry).use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var entryTotal = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        entryTotal += read
                        actualTotal += read
                        if (entryTotal > MAX_ENTRY_BYTES) {
                            throw MilkImportTooLargeException("备份条目过大：${item.normalized}")
                        }
                        if (actualTotal > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                            throw MilkImportTooLargeException(
                                "备份解压后体积超过 ${MAX_TOTAL_UNCOMPRESSED_BYTES / (1024 * 1024)}MB 上限",
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    /**
     * 严格校验页面追加流(与 [ProtoInk] 相同分帧):每条 varint 长度 + StrokeRecord。
     * 仅容忍**尾部**被截断的记录(崩溃残留,ProtoInk 也如此);
     * 中段出现无法解析的记录视为备份损坏,拒绝导入。
     */
    private fun validatePageProto(file: File, entryName: String) {
        if (!file.exists() || file.length() == 0L) return
        try {
            file.inputStream().buffered().use { input ->
                val cis = CodedInputStream.newInstance(input)
                cis.setSizeLimit(MAX_ENTRY_BYTES.toInt())
                while (true) {
                    if (cis.isAtEnd) break
                    val size = try {
                        cis.readRawVarint32()
                    } catch (_: IOException) {
                        break // 尾部 varint 不完整:崩溃残留
                    }
                    if (size <= 0) break
                    if (size > ProtoInk.MAX_RECORD_BYTES) {
                        throw MilkImportCorruptException("页面数据损坏（$entryName）：笔迹记录超出大小限制")
                    }
                    val body = try {
                        cis.readRawBytes(size)
                    } catch (_: IOException) {
                        break // 尾部记录被截断:崩溃残留
                    }
                    try {
                        StrokeRecord.parseFrom(body)
                    } catch (e: Exception) {
                        throw MilkImportCorruptException("页面数据损坏（$entryName）：笔迹无法解析", e)
                    }
                }
            }
        } catch (e: MilkImportException) {
            throw e
        } catch (e: Exception) {
            throw MilkImportCorruptException("页面数据损坏（$entryName）：无法读取", e)
        }
    }

    private fun validatePageSpecs(meta: NotebookMeta, pageIds: List<String>) {
        val pages = pageIds.toSet()
        meta.pageSpecs.forEach { (pageId, spec) ->
            if (pageId !in pages || !spec.widthMm.isFinite() || !spec.heightMm.isFinite() ||
                spec.widthMm <= 0f || spec.heightMm <= 0f || spec.widthMm > 2000f || spec.heightMm > 2000f
            ) {
                throw MilkImportCorruptException("笔记元数据包含无效页面尺寸：$pageId")
            }
        }
    }

    private fun validateImageSidecars(dir: File) {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".ann.json") }?.forEach { file ->
            try {
                json.decodeFromString<List<AnnotationCard>>(file.readText())
            } catch (e: Exception) {
                throw MilkImportCorruptException("文字页面数据损坏：${file.name}", e)
            }
        }
        val root = dir.canonicalFile
        dir.listFiles { f -> f.isFile && f.name.endsWith(".img.json") }?.forEach { file ->
            val images = try {
                json.decodeFromString<List<ImageElement>>(file.readText())
            } catch (e: Exception) {
                throw MilkImportCorruptException("图片页面数据损坏：${file.name}", e)
            }
            images.forEach { image ->
                val normalized = normalizeEntryName(image.src)
                if (normalized != image.src) throw MilkImportUnsafePathException("图片路径必须使用标准相对路径：${image.src}")
                val target = File(root, normalized).canonicalFile
                if (!target.toPath().startsWith(root.toPath()) || target == root) {
                    throw MilkImportUnsafePathException("图片路径越出笔记本目录：${image.src}")
                }
            }
        }
        dir.listFiles { f -> f.isFile && f.name.endsWith(".media.json") }?.forEach { file ->
            val media = try {
                json.decodeFromString<List<MediaElement>>(file.readText())
            } catch (e: Exception) {
                throw MilkImportCorruptException("媒体页面数据损坏：${file.name}", e)
            }
            media.forEach { item ->
                val normalized = normalizeEntryName(item.src)
                if (normalized != item.src) throw MilkImportUnsafePathException("媒体路径必须使用标准相对路径：${item.src}")
                val target = File(root, normalized).canonicalFile
                if (!target.toPath().startsWith(root.toPath()) || target == root) {
                    throw MilkImportUnsafePathException("媒体路径越出笔记本目录：${item.src}")
                }
            }
        }
    }

    /** 恢复后所有 Agent 会话属于新 ID；附件只允许 agent/att/ 下的安全相对路径。 */
    private fun rewriteAgentSessions(dir: File, notebookId: String) {
        val sessions = File(dir, "agent/sessions").listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?: return
        sessions.forEach { file ->
            val root = try {
                Json.parseToJsonElement(file.readText()) as? JsonObject
            } catch (e: Exception) {
                throw MilkImportCorruptException("Agent 会话损坏：${file.name}", e)
            } ?: throw MilkImportCorruptException("Agent 会话损坏：${file.name}")
            validateAttachmentPaths(root, dir)
            val rewritten = JsonObject(root.toMutableMap().apply { put("notebookId", JsonPrimitive(notebookId)) })
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(rewritten.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) throw MilkImportException("导入失败：无法恢复 Agent 会话")
            }
        }
    }

    private fun validateAttachmentPaths(element: kotlinx.serialization.json.JsonElement, dir: File) {
        when (element) {
            is JsonObject -> {
                val file = (element["file"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (file != null && element.containsKey("sha1")) {
                    if (normalizeEntryName(file) != file || !file.startsWith("agent/att/")) {
                        throw MilkImportUnsafePathException("Agent 附件路径不安全：$file")
                    }
                    val root = dir.canonicalFile
                    val target = File(root, file).canonicalFile
                    if (!target.toPath().startsWith(root.toPath())) {
                        throw MilkImportUnsafePathException("Agent 附件路径越界：$file")
                    }
                }
                element.values.forEach { validateAttachmentPaths(it, dir) }
            }
            is JsonArray -> element.forEach { validateAttachmentPaths(it, dir) }
            else -> Unit
        }
    }

    // ---- 元数据 / 目录落盘 ----

    private fun ensureRoot(notebooksRoot: File) {
        if (!notebooksRoot.exists() && !notebooksRoot.mkdirs()) {
            throw MilkImportException("导入失败：无法创建笔记本目录")
        }
        if (!notebooksRoot.isDirectory) {
            throw MilkImportException("导入失败：笔记本根路径不是目录")
        }
    }

    /** 生成全新的 8 位十六进制 notebook id,绝不与已有 `<id>.milk` 冲突。 */
    private fun freshNotebookId(notebooksRoot: File): String {
        repeat(100) {
            val id = UUID.randomUUID().toString().replace("-", "").take(8)
            if (!File(notebooksRoot, "$id$MILK_SUFFIX").exists()) return id
        }
        throw MilkImportException("导入失败：无法生成唯一的笔记 ID")
    }

    private fun writeMetaAtomically(dir: File, meta: NotebookMeta) {
        val target = File(dir, "notebook.json")
        val tmp = File(dir, "notebook.json.tmp")
        tmp.writeText(json.encodeToString(meta))
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                throw MilkImportException("导入失败：无法写入笔记元数据")
            }
        }
    }

    private fun readEntryBytes(
        zip: ZipFile,
        entry: ZipEntry,
        limit: Long,
        entryName: String,
    ): ByteArray {
        if (entry.size > limit) {
            throw MilkImportTooLargeException("备份条目过大：$entryName")
        }
        val out = ByteArrayOutputStream()
        zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) {
                    throw MilkImportTooLargeException("备份条目过大：$entryName")
                }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }
}
