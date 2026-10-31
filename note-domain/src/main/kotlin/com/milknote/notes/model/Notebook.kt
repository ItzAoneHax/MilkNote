package com.milknote.notes.model

import kotlinx.serialization.Serializable

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
) {
    /** 页面物理尺寸(px @ mmToPx;方向生效) */
    fun pageMm(mmToPx: Float): Pair<Float, Float> {
        val (w, h) = PAGE_SIZES_MM[pageSize] ?: PAGE_SIZES_MM.getValue("A4")
        return if (orientation == "landscape") h * mmToPx to w * mmToPx else w * mmToPx to h * mmToPx
    }
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
val PAPER_KINDS = listOf("dot" to "点阵", "line" to "横线", "grid" to "方格", "blank" to "空白")

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
