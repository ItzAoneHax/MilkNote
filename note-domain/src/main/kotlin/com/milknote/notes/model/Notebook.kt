package com.milknote.notes.model

import kotlinx.serialization.Serializable

@Serializable
data class PageSpec(
    val widthMm: Float = 210f,
    val heightMm: Float = 297f,
    val paper: String = "dot",
    val paperColor: String = "#FAF9F5",
)

/** 笔记本元数据(notebook.json;DESIGN-V0 §6.2) */
@Serializable
data class NotebookMeta(
    val id: String,
    val name: String,
    val coverColor: String = "#3B5BEC",   // 封面色(6 色之一)
    val paper: String = "dot",            // dot | line | grid | blank
    val purpose: String = "课堂笔记",      // 用途标签
    val pageSize: String = "A4",          // A4 | A5 | B5(旧数据缺字段 → A4,与既有页面几何一致)
    val orientation: String = "portrait", // portrait | landscape(同上,旧数据保持竖向)
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * 显式页序(页面管理「之前/之后」插入用;旧 .milk 缺字段 → 空,回退按 pageId 排序)。
     * 只记录仍存在的 p*.pb id;新增页 insert 到目标位置,不重命名既有页面文件。
     */
    val pageOrder: List<String> = emptyList(),
    val lastOpened: Long = 0L,
    val tagColor: String = "",
    val folder: String = "",
    val paperColor: String = "#FAF9F5",
    /** PDF 等逐页尺寸；普通页缺省沿用笔记本级尺寸/纸张，旧数据无需迁移。 */
    val pageSpecs: Map<String, PageSpec> = emptyMap(),
    /** 第二代：星标置顶（右上/左上朱砂红丝带书签） */
    val pinned: Boolean = false,
    /** 第二代：回收站软删除 */
    val trashed: Boolean = false,
    val trashedAt: Long = 0L,
) {
    /** 是否为 PDF 导入笔记本（形态即说明：右上折角 + 右下 PDF 淡印） */
    val isPdf: Boolean get() = pageSpecs.isNotEmpty() || name.endsWith(".pdf", ignoreCase = true)

    /** 页面物理尺寸(px @ mmToPx;方向生效) */
    fun pageMm(mmToPx: Float): Pair<Float, Float> {
        val (w, h) = PAGE_SIZES_MM[pageSize] ?: PAGE_SIZES_MM.getValue("A4")
        return if (orientation == "landscape") h * mmToPx to w * mmToPx else w * mmToPx to h * mmToPx
    }

    fun pageSpec(pageId: String): PageSpec = pageSpecs[pageId] ?: run {
        val (w, h) = pageMm(1f)
        PageSpec(w, h, paper, paperColor)
    }

    fun pageMm(pageId: String, mmToPx: Float): Pair<Float, Float> =
        pageSpec(pageId).let { it.widthMm * mmToPx to it.heightMm * mmToPx }
}

/** 页面条目(由 p*.pb 文件扫描得出,不单独存表) */
data class NotebookPage(
    val notebookId: String,
    val pageId: String,     // 如 "p0001"
    val updatedAt: Long,    // 文件 mtime
)

/** 封面色板(新建弹窗 6 色) */
val COVER_COLORS = listOf(
    "#3B5BEC", // 蓝(accent)
    "#D2483C", // 红(批注红)
    "#2E9E6B", // 绿
    "#D98E04", // 琥珀
    "#7C5CE0", // 紫
    "#2C8CC8", // 青
)

/** 纸张模板 */
val PAPER_KINDS = listOf(
    "dot" to "点阵", "line" to "横线", "grid" to "方格", "cornell" to "康奈尔",
    "tian" to "田字格", "english" to "英文", "blank" to "空白",
)

val PAPER_COLORS = listOf(
    "#FAF9F5" to "奶白", "#FFFFFF" to "纯白", "#F4F1DE" to "米黄",
    "#EAF2F8" to "浅蓝", "#F3EAF4" to "浅紫", "#EAF4EA" to "浅绿",
)

/** 纸张尺寸(mm) */
val PAGE_SIZES_MM = mapOf(
    "A4" to (210f to 297f),
    "A5" to (148f to 210f),
    "B5" to (176f to 250f),
)

/** 页面方向 */
val PAGE_ORIENTATIONS = listOf("portrait" to "竖向", "landscape" to "横向")

/** 用途标签 */
val PURPOSE_TAGS = listOf("课堂笔记", "考研考证", "错题本", "阅读摘录", "灵感草稿")
