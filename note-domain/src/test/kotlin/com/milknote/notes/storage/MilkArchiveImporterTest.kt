package com.milknote.notes.storage

import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.Stroke
import com.milknote.ink.serialize.ProtoInk
import com.milknote.notes.model.NotebookMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * `.milk.zip` 导入:合法备份、路径安全、重复条目/页面 ID、元数据与 pb 损坏、
 * 已有笔记本 ID 不被覆盖、体积/条目上限。
 */
class MilkArchiveImporterTest {

    private val temp: File = Files.createTempDirectory("milknote-import-test").toFile()
    private val notebooksRoot = File(temp, "notebooks")
    private val repo = NotebookRepository(temp, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    @After
    fun tearDown() {
        temp.deleteRecursively()
    }

    // ---- 合法备份 ----

    @Test
    fun `合法 milk zip 导入 保留页面 页序 sidecar 图片与 agent 文件`() {
        val rootName = "我的笔记.milk"
        val p1 = pageBytes("s1", "s2")
        val p2 = pageBytes("s3")
        val annotations = """
            [{"id":"c1","x":1.0,"y":2.0,"width":100.0,"text":"批注","createdAt":1}]
        """.trimIndent().toByteArray()
        val images = """
            [{"id":"i1","src":"images/shared.png","x":3.0,"y":4.0,"width":50.0,"height":60.0,"createdAt":1}]
        """.trimIndent().toByteArray()
        val imageBytes = byteArrayOf(1, 2, 3, 4)
        val sessionBytes = """{"id":"sess1","notebookId":"oldid01","pageId":"p0001"}""".toByteArray()
        val attachment = byteArrayOf(9, 9, 9)

        val archive = File(temp, "backup.milk.zip")
        writeZip(
            archive,
            listOf(
                "$rootName/notebook.json" to metaBytes("oldid01", "我的笔记", listOf("p0002", "p0001")),
                "$rootName/p0001.pb" to p1,
                "$rootName/p0002.pb" to p2,
                "$rootName/p0001.ann.json" to annotations,
                "$rootName/p0001.img.json" to images,
                "$rootName/images/shared.png" to imageBytes,
                "$rootName/agent/sessions/sess1.json" to sessionBytes,
                "$rootName/agent/att/hash.png" to attachment,
            ),
        )

        val result = MilkArchiveImporter.import(archive, notebooksRoot)

        assertNotEquals("oldid01", result.notebookId)
        assertEquals(8, result.notebookId.length)
        assertEquals(2, result.pageCount)

        val dir = File(notebooksRoot, "${result.notebookId}.milk")
        assertTrue(dir.isDirectory)
        val meta = Json { ignoreUnknownKeys = true }
            .decodeFromString<NotebookMeta>(File(dir, "notebook.json").readText())
        assertEquals(result.notebookId, meta.id)
        assertEquals("我的笔记", meta.name)
        assertEquals(listOf("p0002", "p0001"), meta.pageOrder)
        assertEquals(100L, meta.createdAt)

        assertArrayEquals(p1, File(dir, "p0001.pb").readBytes())
        assertArrayEquals(p2, File(dir, "p0002.pb").readBytes())
        assertArrayEquals(annotations, File(dir, "p0001.ann.json").readBytes())
        assertArrayEquals(images, File(dir, "p0001.img.json").readBytes())
        assertArrayEquals(imageBytes, File(dir, "images/shared.png").readBytes())
        val restoredSession = File(dir, "agent/sessions/sess1.json").readText()
        assertTrue(restoredSession.contains("\"notebookId\":\"${result.notebookId}\""))
        assertArrayEquals(attachment, File(dir, "agent/att/hash.png").readBytes())

        // 从 NotebookRepository 视角验证页序、笔迹、批注与图片引用都可正常读取
        assertEquals(listOf("p0002", "p0001"), repo.listPages(result.notebookId).map { it.pageId })
        assertEquals(listOf("s1", "s2"), repo.loadPage(result.notebookId, "p0001").map { it.id })
        assertEquals(listOf("c1"), repo.loadAnnotations(result.notebookId, "p0001").map { it.id })
        assertEquals(listOf("images/shared.png"), repo.loadImages(result.notebookId, "p0001").map { it.src })
        assertEquals(result.notebookId, repo.listNotebooks().single().id)
    }

    @Test
    fun `尾部截断的 pb 视为崩溃残留而正常导入`() {
        val archive = File(temp, "truncated.milk.zip")
        val page = pageBytes("s1") + byteArrayOf(0x0A) // 声明 10 字节但已被截断
        writeZip(
            archive,
            listOf(
                "nb.milk/notebook.json" to metaBytes("oldid01", "笔记", emptyList()),
                "nb.milk/p0001.pb" to page,
            ),
        )

        val result = MilkArchiveImporter.import(archive, notebooksRoot)
        assertEquals(1, result.pageCount)
        assertEquals(listOf("s1"), repo.loadPage(result.notebookId, "p0001").map { it.id })
    }

    // ---- 路径安全 ----

    @Test
    fun `路径穿越条目被拒绝且不落盘`() {
        val archive = File(temp, "traversal.milk.zip")
        writeZip(
            archive,
            listOf(
                "evil.milk/notebook.json" to metaBytes("oldid01", "笔记", emptyList()),
                "evil.milk/../../escape.txt" to byteArrayOf(1),
            ),
        )

        val error = assertThrows(MilkImportException::class.java) {
            MilkArchiveImporter.import(archive, notebooksRoot)
        }
        assertTrue(error is MilkImportUnsafePathException)
        assertTrue(error.message!!.contains("路径穿越"))
        assertFalse(File(temp, "escape.txt").exists())
        assertFalse(File(temp.parentFile, "escape.txt").exists())
        assertNoNotebookCreated()
    }

    @Test
    fun `绝对路径与盘符条目被拒绝`() {
        val absolute = File(temp, "absolute.milk.zip")
        writeZip(
            absolute,
            listOf("/abs.milk/notebook.json" to metaBytes("oldid01", "笔记", emptyList())),
        )
        val absoluteError = assertThrows(MilkImportException::class.java) {
            MilkArchiveImporter.import(absolute, notebooksRoot)
        }
        assertTrue(absoluteError is MilkImportUnsafePathException)
        assertTrue(absoluteError.message!!.contains("绝对路径"))

        val drive = File(temp, "drive.milk.zip")
        writeZip(
            drive,
            listOf("C:\\notes.milk\\notebook.json" to metaBytes("oldid01", "笔记", emptyList())),
        )
        val driveError = assertThrows(MilkImportException::class.java) {
            MilkArchiveImporter.import(drive, notebooksRoot)
        }
        assertTrue(driveError is MilkImportUnsafePathException)
        assertTrue(driveError.message!!.contains("绝对路径"))
        assertNoNotebookCreated()
    }

    // ---- 重复条目 / 重复 ID ----

    @Test
    fun `反斜杠与正斜杠规范化后重复的条目被拒绝`() {
        val archive = File(temp, "duplicate-entry.milk.zip")
        // ZipOutputStream 允许两个原始名字不同、但规范化后相同的条目
        writeZip(
            archive,
            listOf(
                "nb.milk/notebook.json" to metaBytes("oldid01", "笔记", emptyList()),
                "nb.milk\\notebook.json" to metaBytes("oldid01", "笔记", emptyList()),
            ),
        )

        val error = assertThrows(MilkImportException::class.java) {
            MilkArchiveImporter.import(archive, notebooksRoot)
        }
        assertTrue(error is MilkImportDuplicateException)
        assertTrue(error.message!!.contains("重复条目"))
        assertNoNotebookCreated()
    }

    @Test
    fun `大小写不同的重复页面 ID 被拒绝`() {
        val archive = File(temp, "duplicate-page.milk.zip")
        writeZip(
            archive,
            listOf(
                "nb.milk/notebook.json" to metaBytes("oldid01", "笔记", emptyList()),
                "nb.milk/p0001.pb" to pageBytes("s1"),
                "nb.milk/P0001.pb" to pageBytes("s2"),
            ),
        )

        val error = assertThrows(MilkImportException::class.java) {
            MilkArchiveImporter.import(archive, notebooksRoot)
        }
        assertTrue(error is MilkImportDuplicateException)
        assertTrue(error.message!!.contains("重复的页面 ID"))
        assertNoNotebookCreated()
    }

    @Test
    fun `pageOrder 重复页面 ID 被拒绝`() {
        val archive = File(temp, "duplicate-order.milk.zip")
        writeZip(
            archive,
            listOf(
                "nb.milk/notebook.json" to metaBytes("oldid01", "笔记", listOf("p0001", "p0001")),
                "nb.milk/p0001.pb" to pageBytes("s1"),
            ),
        )

        val error = assertThrows(MilkImportException::class.java) {
            MilkArchiveImporter.import(archive, notebooksRoot)
        }
        assertTrue(error is MilkImportCorruptException)
        assertTrue(error.message!!.contains("pageOrder"))
        assertNoNotebookCreated()
    }

    // ---- 损坏与格式 ----

    @Test
    fun `notebook json 损坏时导入失败`() {
        val archive = File(temp, "corrupt-meta.milk.zip")
        writeZip(
            archive,
            listOf("nb.milk/notebook.json" to "{这不是合法 JSON".toByteArray()),
        )

        val error = assertThrows(MilkImportException::class.java) {
            MilkArchiveImporter.import(archive, notebooksRoot)
        }
        assertTrue(error is MilkImportCorruptException)
        assertTrue(error.message!!.contains("notebook.json"))
        assertNoNotebookCreated()
    }

    @Test
    fun `损坏的 pb 记录被拒绝`() {
        val archive = File(temp, "corrupt-pb.milk.zip")
        writeZip(
            archive,
            listOf(
                "nb.milk/notebook.json" to metaBytes("oldid01", "笔记", emptyList()),
                // 长度 1 + 非法 wire type 0xFF:ProtoInk 会静默丢弃,导入侧应显式拒绝
                "nb.milk/p0001.pb" to byteArrayOf(0x01, 0xFF.toByte()),
            ),
        )

        val error = assertThrows(MilkImportException::class.java) {
            MilkArchiveImporter.import(archive, notebooksRoot)
        }
        assertTrue(error is MilkImportCorruptException)
        assertTrue(error.message!!.contains("页面数据损坏"))
        assertNoNotebookCreated()
    }

    @Test
    fun `非 MilkNote ZIP 与缺少 notebook json 都给出中文错误`() {
        val nonMilk = File(temp, "other.zip")
        writeZip(nonMilk, listOf("notes/readme.txt" to byteArrayOf(1)))
        val nonMilkError = assertThrows(MilkImportException::class.java) {
            MilkArchiveImporter.import(nonMilk, notebooksRoot)
        }
        assertTrue(nonMilkError is NotMilkArchiveException)
        assertTrue(nonMilkError.message!!.contains("不是 MilkNote 备份"))

        val missingMeta = File(temp, "missing-meta.zip")
        writeZip(
            missingMeta,
            listOf(
                "nb.milk/p0001.pb" to pageBytes("s1"),
            ),
        )
        val missingError = assertThrows(MilkImportException::class.java) {
            MilkArchiveImporter.import(missingMeta, notebooksRoot)
        }
        assertTrue(missingError is NotMilkArchiveException)
        assertTrue(missingError.message!!.contains("notebook.json"))
        assertNoNotebookCreated()
    }

    @Test
    fun `多个 milk 根目录被拒绝`() {
        val archive = File(temp, "two-roots.milk.zip")
        writeZip(
            archive,
            listOf(
                "a.milk/notebook.json" to metaBytes("id1", "笔记A", emptyList()),
                "b.milk/p0001.pb" to pageBytes("s1"),
            ),
        )

        val error = assertThrows(MilkImportException::class.java) {
            MilkArchiveImporter.import(archive, notebooksRoot)
        }
        assertTrue(error is NotMilkArchiveException)
        assertTrue(error.message!!.contains("只包含一个"))
        assertNoNotebookCreated()
    }

    @Test
    fun `条目数超出上限被拒绝`() {
        val archive = File(temp, "too-many.zip")
        val entries = buildList {
            add("nb.milk/notebook.json" to metaBytes("oldid01", "笔记", emptyList()))
            for (i in 0 until MilkArchiveImporter.MAX_ENTRIES) {
                add("nb.milk/asset-$i.txt" to byteArrayOf())
            }
        }
        writeZip(archive, entries)

        val error = assertThrows(MilkImportException::class.java) {
            MilkArchiveImporter.import(archive, notebooksRoot)
        }
        assertTrue(error is MilkImportTooLargeException)
        assertTrue(error.message!!.contains("条目过多"))
        assertNoNotebookCreated()
    }

    // ---- 绝不覆盖已有笔记本 ----

    @Test
    fun `已有同 ID 笔记本不被覆盖 导入生成新 ID 目录`() {
        val existing = File(notebooksRoot, "oldid01.milk")
        existing.mkdirs()
        val existingMeta = metaBytes("oldid01", "旧笔记本", emptyList())
        File(existing, "notebook.json").writeBytes(existingMeta)
        File(existing, "p0001.pb").writeBytes(pageBytes("legacy"))
        val sentinel = File(existing, "keep.txt").apply { writeText("不要覆盖") }

        val archive = File(temp, "collision.milk.zip")
        writeZip(
            archive,
            listOf(
                "我的笔记.milk/notebook.json" to metaBytes("oldid01", "我的笔记", listOf("p0001")),
                "我的笔记.milk/p0001.pb" to pageBytes("s1"),
                "我的笔记.milk/images/shared.png" to byteArrayOf(7),
            ),
        )

        val result = MilkArchiveImporter.import(archive, notebooksRoot)

        assertNotEquals("oldid01", result.notebookId)
        // 旧目录内容一字未改
        assertArrayEquals(existingMeta, File(existing, "notebook.json").readBytes())
        assertArrayEquals(pageBytes("legacy"), File(existing, "p0001.pb").readBytes())
        assertEquals("不要覆盖", sentinel.readText())
        assertFalse(File(existing, "images/shared.png").exists())
        // 新目录独立存在
        val importedDir = File(notebooksRoot, "${result.notebookId}.milk")
        assertTrue(importedDir.isDirectory)
        assertArrayEquals(byteArrayOf(7), File(importedDir, "images/shared.png").readBytes())
        assertEquals(
            result.notebookId,
            Json { ignoreUnknownKeys = true }
                .decodeFromString<NotebookMeta>(File(importedDir, "notebook.json").readText()).id,
        )
        assertEquals(2, notebooksRoot.listFiles()!!.count { it.isDirectory && it.name.endsWith(".milk") })
    }

    @Test
    fun `损坏的批注 sidecar 被拒绝`() {
        val archive = File(temp, "bad-annotation.milk.zip")
        writeZip(archive, listOf(
            "nb.milk/notebook.json" to metaBytes("oldid01", "笔记", listOf("p0001")),
            "nb.milk/p0001.pb" to pageBytes("s1"),
            "nb.milk/p0001.ann.json" to "not-json".toByteArray(),
        ))
        assertThrows(MilkImportCorruptException::class.java) { MilkArchiveImporter.import(archive, notebooksRoot) }
        assertNoNotebookCreated()
    }

    @Test
    fun `图片 sidecar 路径穿越被拒绝`() {
        val archive = File(temp, "image-traversal.milk.zip")
        writeZip(archive, listOf(
            "nb.milk/notebook.json" to metaBytes("oldid01", "笔记", listOf("p0001")),
            "nb.milk/p0001.pb" to pageBytes("s1"),
            "nb.milk/p0001.img.json" to """[{"id":"i","src":"../outside.png","x":0,"y":0,"width":10,"height":10}]""".toByteArray(),
        ))
        assertThrows(MilkImportUnsafePathException::class.java) { MilkArchiveImporter.import(archive, notebooksRoot) }
        assertNoNotebookCreated()
    }

    @Test
    fun `媒体 sidecar 路径穿越被拒绝`() {
        val archive = File(temp, "media-traversal.milk.zip")
        writeZip(archive, listOf(
            "nb.milk/notebook.json" to metaBytes("oldid01", "笔记", listOf("p0001")),
            "nb.milk/p0001.pb" to pageBytes("s1"),
            "nb.milk/p0001.media.json" to """[{"id":"m","src":"../outside.mp4"}]""".toByteArray(),
        ))
        assertThrows(MilkImportUnsafePathException::class.java) { MilkArchiveImporter.import(archive, notebooksRoot) }
        assertNoNotebookCreated()
    }

    @Test
    fun `Agent 附件路径穿越被拒绝`() {
        val archive = File(temp, "agent-traversal.milk.zip")
        val session = """{"id":"s","notebookId":"oldid01","messages":[{"attachments":[{"file":"agent/att/../../x","sha1":"x"}]}]}""".toByteArray()
        writeZip(archive, listOf(
            "nb.milk/notebook.json" to metaBytes("oldid01", "笔记", emptyList()),
            "nb.milk/agent/sessions/s.json" to session,
        ))
        assertThrows(MilkImportUnsafePathException::class.java) { MilkArchiveImporter.import(archive, notebooksRoot) }
        assertNoNotebookCreated()
    }

    @Test
    fun `异常逐页尺寸被拒绝`() {
        val archive = File(temp, "bad-spec.milk.zip")
        val meta = """{"id":"oldid01","name":"笔记","createdAt":0,"updatedAt":0,"pageSpecs":{"p0001":{"widthMm":99999,"heightMm":100}}}""".toByteArray()
        writeZip(archive, listOf(
            "nb.milk/notebook.json" to meta,
            "nb.milk/p0001.pb" to pageBytes("s1"),
        ))
        assertThrows(MilkImportCorruptException::class.java) { MilkArchiveImporter.import(archive, notebooksRoot) }
        assertNoNotebookCreated()
    }

    // ---- helpers ----

    private fun stroke(id: String) = Stroke(
        id = id,
        tool = InkTool.PEN,
        color = 0xFF23221E,
        baseWidthMm = 0.7f,
        points = listOf(
            InkPoint(0f, 0f, 0.5f, 0f, 0f, 1L),
            InkPoint(10f, 10f, 0.5f, 0f, 0f, 9L),
        ),
    )

    private fun pageBytes(vararg strokeIds: String): ByteArray {
        val out = ByteArrayOutputStream()
        strokeIds.forEach { id -> ProtoInk.writeRecord(out, ProtoInk.recordOf(stroke(id))) }
        return out.toByteArray()
    }

    private fun metaBytes(id: String, name: String, pageOrder: List<String>): ByteArray =
        """
        {"id":"$id","name":"$name","coverColor":"#3B5BEC","paper":"dot","purpose":"课堂笔记",
         "pageSize":"A5","orientation":"portrait","createdAt":100,"updatedAt":200,
         "pageOrder":[${pageOrder.joinToString(",") { "\"$it\"" }}],"futureField":true}
        """.trimIndent().toByteArray()

    private fun writeZip(file: File, entries: List<Pair<String, ByteArray>>) {
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    /** 导入失败时不应在 notebooks/ 下留下任何 `.milk` 目录或 staging 目录。 */
    private fun assertNoNotebookCreated() {
        val leftovers = notebooksRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
        assertTrue("失败导入不应留下目录: ${leftovers.map { it.name }}", leftovers.isEmpty())
    }
}
