package com.milknote.notes.storage

import com.milknote.notes.model.NoteCard
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 卡片标签筛选:全部 / 未打标签(或标签全为空白) / 指定标签(大小写不敏感)。 */
sealed interface CardTagFilter {
    data object All : CardTagFilter
    data object Untagged : CardTagFilter
    data class Tag(val name: String) : CardTagFilter
}

/**
 * 本地卡片仓库:全部卡片存于 `rootDir/cards/cards.json`,整表原子替换(临时文件 + rename)。
 *
 * - 写入同步完成:方法返回时数据已落盘,便于调用方与单测确定地读回。
 * - 单进程内所有读写走同一把锁;跨进程不做保证(本地卡片的唯一写入方是 App 本身)。
 * - 空白 id 不写入、不返回;id 首尾空白落盘时去除。
 * - 列表顺序固定为 updatedAt 降序、id 升序,保证同时间戳下也稳定。
 * - Json 读取忽略未知字段,写入补齐默认字段,兼容旧/新版本。
 */
class CardRepository(private val rootDir: File) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true   // 前向兼容:新增字段不破坏旧客户端
        encodeDefaults = true      // 后向兼容:默认字段也落盘,后续版本无需依赖隐式默认
    }
    private val lock = Any()

    private fun cardsDir(): File = File(rootDir, "cards")
    private fun cardsFile(): File = File(cardsDir(), "cards.json")

    // ---- 读取与查询 ----

    /**
     * 读取并按固定顺序返回卡片;查询条件在内存中纯过滤,不产生额外 IO。
     * [query] 只匹配标题/正文(大小写不敏感,首尾空白忽略);[tag] 支持未打标签;[color] 为 null/空白表示不过滤。
     */
    fun listCards(
        query: String = "",
        tag: CardTagFilter = CardTagFilter.All,
        color: String? = null,
    ): List<NoteCard> = synchronized(lock) {
        filterCards(readCardsLocked(), query, tag, color)
    }

    /** 按 id 取单张卡片;id 为空/空白或不存在时返回 null。 */
    fun get(id: String): NoteCard? = synchronized(lock) {
        val key = id.trim()
        if (key.isEmpty()) return@synchronized null
        readCardsLocked().firstOrNull { it.id.trim() == key }
    }

    /**
     * 纯过滤函数(不读写磁盘):标题/正文搜索、标签(含未打标签)、颜色,结果按
     * updatedAt 降序、id 升序排序。对外单独暴露便于 UI/单测复用。
     */
    fun filterCards(
        cards: List<NoteCard>,
        query: String = "",
        tag: CardTagFilter = CardTagFilter.All,
        color: String? = null,
    ): List<NoteCard> {
        val needle = query.trim()
        val colorKey = color?.trim()
        return cards.asSequence()
            .filter { card ->
                needle.isEmpty() ||
                    card.title.contains(needle, ignoreCase = true) ||
                    card.body.contains(needle, ignoreCase = true)
            }
            .filter { card ->
                when (tag) {
                    CardTagFilter.All -> true
                    CardTagFilter.Untagged -> card.tags.none { it.isNotBlank() }
                    is CardTagFilter.Tag ->
                        card.tags.any { it.trim().equals(tag.name.trim(), ignoreCase = true) }
                }
            }
            .filter { card ->
                colorKey.isNullOrEmpty() || card.color.equals(colorKey, ignoreCase = true)
            }
            .sortedWith(CARD_ORDER)
            .toList()
    }

    // ---- 写入 ----

    /**
     * 新增或按 id 覆盖一张卡片;id 为空/空白时拒绝写入并返回 null。
     * 返回实际落盘的卡片(id 已去除首尾空白),内容与时间戳不做隐式改写。
     */
    fun upsert(card: NoteCard): NoteCard? = synchronized(lock) {
        val id = card.id.trim()
        if (id.isEmpty()) return@synchronized null
        val stored = if (id == card.id) card else card.copy(id = id)
        val remaining = readCardsLocked().filterNot { it.id.trim() == id }
        writeCardsLocked(remaining + stored)
        stored
    }

    /** 按 id 删除;存在并删除成功返回 true,不存在或 id 空白返回 false。 */
    fun delete(id: String): Boolean = synchronized(lock) {
        val key = id.trim()
        if (key.isEmpty()) return@synchronized false
        val current = readCardsLocked()
        val remaining = current.filterNot { it.id.trim() == key }
        if (remaining.size == current.size) return@synchronized false
        writeCardsLocked(remaining)
        true
    }

    // ---- 落盘 ----

    private fun readCardsLocked(): List<NoteCard> {
        val file = cardsFile()
        if (!file.isFile) return emptyList()
        return runCatching { json.decodeFromString<List<NoteCard>>(file.readText()) }
            .getOrDefault(emptyList())
            .filter { it.id.isNotBlank() }
            .sortedWith(CARD_ORDER)
    }

    /** 临时文件 + rename 原子替换,避免中途崩溃留下半截 JSON。 */
    private fun writeCardsLocked(cards: List<NoteCard>) {
        val dir = cardsDir().apply { mkdirs() }
        val target = File(dir, "cards.json")
        val tmp = File(dir, "cards.json.tmp")
        tmp.writeText(json.encodeToString(cards.sortedWith(CARD_ORDER)))
        try {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

private val CARD_ORDER: Comparator<NoteCard> =
    compareByDescending<NoteCard> { it.updatedAt }.thenBy { it.id }
