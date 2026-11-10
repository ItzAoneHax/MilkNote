package com.milknote.notes.storage

import com.milknote.notes.model.DEFAULT_CARD_COLOR
import com.milknote.notes.model.NoteCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 本地卡片仓库:JSON 兼容、CRUD 持久化、纯过滤(搜索/标签/颜色)与稳定排序。
 */
class CardRepositoryTest {

    private val root: File = Files.createTempDirectory("milknote-cards-test").toFile()
    private val repo = CardRepository(root)
    private val storeFile = File(root, "cards/cards.json")

    private fun card(
        id: String,
        title: String = "",
        body: String = "",
        imageSrc: String? = null,
        tags: List<String> = emptyList(),
        color: String = DEFAULT_CARD_COLOR,
        notebookId: String? = null,
        pageId: String? = null,
        createdAt: Long = 1L,
        updatedAt: Long = 1L,
    ) = NoteCard(
        id = id,
        title = title,
        body = body,
        imageSrc = imageSrc,
        tags = tags,
        color = color,
        sourceNotebookId = notebookId,
        sourcePageId = pageId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ids(cards: List<NoteCard>) = cards.map { it.id }

    @Test
    fun `缺字段与未知字段的旧新 JSON 都使用默认值解码`() {
        storeFile.parentFile?.mkdirs()
        storeFile.writeText(
            """
            [
              {"id":"legacy","title":"旧卡","createdAt":10,"updatedAt":20,
               "unknownField":{"nested":true},"anotherFuture":42}
            ]
            """.trimIndent(),
        )

        val legacy = repo.listCards().single()
        assertEquals("legacy", legacy.id)
        assertEquals("旧卡", legacy.title)
        assertEquals("", legacy.body)
        assertNull(legacy.imageSrc)
        assertEquals(emptyList<String>(), legacy.tags)
        assertEquals(DEFAULT_CARD_COLOR, legacy.color)
        assertNull(legacy.sourceNotebookId)
        assertNull(legacy.sourcePageId)
        assertEquals(10L, legacy.createdAt)
        assertEquals(20L, legacy.updatedAt)
    }

    @Test
    fun `CRUD 持久化并在新仓库实例中可读`() {
        val original = card(
            id = "c-1",
            title = "公式",
            body = "E=mc^2",
            imageSrc = "cards/formula.png",
            tags = listOf("物理", "期末"),
            color = "#D2483C",
            notebookId = "nb-1",
            pageId = "p0003",
            createdAt = 100L,
            updatedAt = 200L,
        )

        assertEquals(original, repo.upsert(original))
        assertTrue(storeFile.isFile)
        assertEquals(original, repo.get("c-1"))
        assertEquals(listOf(original), repo.listCards())

        // 模拟 App 重启:新实例从同一目录读回
        val reopened = CardRepository(root)
        assertEquals(original, reopened.get("c-1"))
        assertEquals(listOf(original), reopened.listCards())

        val edited = original.copy(title = "公式(改)", updatedAt = 300L)
        assertEquals(edited, repo.upsert(edited))
        assertEquals(1, repo.listCards().size)
        assertEquals(edited, CardRepository(root).get("c-1"))

        assertTrue(repo.delete("c-1"))
        assertNull(repo.get("c-1"))
        assertEquals(emptyList<NoteCard>(), repo.listCards())
        assertFalse(CardRepository(root).delete("c-1"))
    }

    @Test
    fun `标题和正文搜索大小写不敏感且忽略首尾空白`() {
        repo.upsert(card("a", title = "Milk 笔记", body = "Hello World", updatedAt = 10L))
        repo.upsert(card("b", title = "其他", body = "milkshake recipe", updatedAt = 20L))
        repo.upsert(card("c", title = "无关", body = "nothing", updatedAt = 30L))

        assertEquals(listOf("b", "a"), ids(repo.listCards(query = "milk")))
        assertEquals(listOf("b", "a"), ids(repo.listCards(query = "  MILK  ")))
        assertEquals(listOf("b"), ids(repo.listCards(query = "SHAKE")))
        assertEquals(emptyList<NoteCard>(), repo.listCards(query = "不存在"))
        // 空查询返回全部,并按 updatedAt 降序
        assertEquals(listOf("c", "b", "a"), ids(repo.listCards(query = "")))
        assertEquals(listOf("c", "b", "a"), ids(repo.listCards(query = "   ")))
    }

    @Test
    fun `标签筛选包含未打标签且大小写不敏感`() {
        repo.upsert(card("a", tags = listOf("学习", "数学"), updatedAt = 1L))
        repo.upsert(card("b", tags = listOf("生活"), updatedAt = 2L))
        repo.upsert(card("c", tags = emptyList(), updatedAt = 3L))
        repo.upsert(card("d", tags = listOf("  "), updatedAt = 4L))
        repo.upsert(card("e", tags = listOf("Study"), updatedAt = 5L))

        assertEquals(listOf("a"), ids(repo.listCards(tag = CardTagFilter.Tag("学习"))))
        assertEquals(listOf("a"), ids(repo.listCards(tag = CardTagFilter.Tag("数学"))))
        assertEquals(listOf("e"), ids(repo.listCards(tag = CardTagFilter.Tag("study"))))
        assertEquals(listOf("b"), ids(repo.listCards(tag = CardTagFilter.Tag("生活"))))
        // 空标签与纯空白标签都算「无标签」
        assertEquals(listOf("d", "c"), ids(repo.listCards(tag = CardTagFilter.Untagged)))
        assertEquals(
            listOf("e", "d", "c", "b", "a"),
            ids(repo.listCards(tag = CardTagFilter.All)),
        )
    }

    @Test
    fun `颜色筛选大小写不敏感并可与搜索组合`() {
        repo.upsert(card("red", title = "苹果", color = "#D2483C", updatedAt = 1L))
        repo.upsert(card("green", title = "苹果", color = "#2E9E6B", updatedAt = 2L))
        repo.upsert(card("blue", title = "香蕉", color = "#3B5BEC", updatedAt = 3L))

        assertEquals(listOf("red"), ids(repo.listCards(color = "#D2483C")))
        assertEquals(listOf("red"), ids(repo.listCards(color = "#d2483c")))
        assertEquals(listOf("red"), ids(repo.listCards(query = "苹果", color = "#D2483C")))
        assertEquals(listOf("green"), ids(repo.listCards(query = "苹果", color = "#2E9E6B")))
        assertEquals(listOf("blue", "green", "red"), ids(repo.listCards(color = null)))
        assertEquals(listOf("blue", "green", "red"), ids(repo.listCards(color = "  ")))
        assertEquals(emptyList<NoteCard>(), repo.listCards(color = "#000000"))
    }

    @Test
    fun `排序固定为 updatedAt 降序再按 id 升序`() {
        repo.upsert(card("b", updatedAt = 10L))
        repo.upsert(card("a", updatedAt = 20L))
        repo.upsert(card("c", updatedAt = 20L))
        repo.upsert(card("d", updatedAt = 10L))

        assertEquals(listOf("a", "c", "b", "d"), ids(repo.listCards()))

        // 覆盖更新后顺序随 updatedAt 变化,且不产生重复
        repo.upsert(card("d", updatedAt = 30L))
        assertEquals(listOf("d", "a", "c", "b"), ids(repo.listCards()))
    }

    @Test
    fun `纯过滤函数不依赖磁盘`() {
        val cards = listOf(
            card("a", title = "Milk", tags = listOf("x"), color = "#D2483C", updatedAt = 2L),
            card("b", title = "Tea", body = "milk tea", tags = emptyList(), updatedAt = 1L),
        )

        assertEquals(
            listOf("a"),
            ids(repo.filterCards(cards, query = "MILK", color = "#d2483c")),
        )
        assertEquals(listOf("b"), ids(repo.filterCards(cards, tag = CardTagFilter.Untagged)))
        assertEquals(listOf("a", "b"), ids(repo.filterCards(cards)))
    }

    @Test
    fun `空白 id 不写入不返回且空白 id 数据被跳过`() {
        assertNull(repo.upsert(card(id = " ", title = "无 id")))
        assertFalse(storeFile.exists())
        assertEquals(emptyList<NoteCard>(), repo.listCards())
        assertNull(repo.get(" "))
        assertFalse(repo.delete(" "))

        storeFile.parentFile?.mkdirs()
        storeFile.writeText(
            """
            [
              {"id":"","title":"空"},
              {"id":"  ","title":"空白"},
              {"id":"ok","title":"有效","createdAt":1,"updatedAt":2}
            ]
            """.trimIndent(),
        )
        assertEquals(listOf("ok"), ids(repo.listCards()))
        assertNull(repo.get(""))
        assertFalse(repo.delete(""))
    }

    @Test
    fun `id 首尾空白落盘时去除`() {
        val stored = repo.upsert(card(id = "  c-9  ", title = "trim"))
        assertEquals("c-9", stored?.id)
        assertEquals("c-9", repo.get("c-9")?.id)
        assertEquals("c-9", repo.listCards().single().id)
    }

    @Test
    fun `写入使用临时文件原子替换且不残留 tmp`() {
        repo.upsert(card("a", updatedAt = 1L))
        repo.upsert(card("b", updatedAt = 2L))

        assertTrue(storeFile.isFile)
        assertFalse(File(storeFile.parentFile, "cards.json.tmp").exists())
        assertEquals(listOf("b", "a"), ids(CardRepository(root).listCards()))
    }

    @Test
    fun `损坏 JSON 视为空并可被下一次写入修复`() {
        storeFile.parentFile?.mkdirs()
        storeFile.writeText("{ not json")

        assertEquals(emptyList<NoteCard>(), repo.listCards())
        assertNull(repo.get("a"))

        repo.upsert(card("a", updatedAt = 1L))
        assertEquals(listOf("a"), ids(repo.listCards()))
    }
}
