package com.milknote.agent

/**
 * 草稿附件(⑤ 抽屉 composer 上待发送的附件;发送时才落盘,§4.3)。
 * 既承载页面渲染图(框选/整页/整章),也承载文档(Markdown/Text/PDF/PPT 逐页图)。
 */
data class DraftAttachment(
    val id: String,
    val logicalName: String,
    val widthPx: Int,
    val heightPx: Int,
    val png: ByteArray,
    val jpeg: Boolean,
    val anchor: Rect4?,                 // 选区页坐标(批注贴回定位)
    val source: Attachment.Source,
    val mimeType: String = if (jpeg) "image/jpeg" else "image/png",
    /** @ 提及名(自动清洗空格/@;发送时写入 Attachment.mentionName) */
    val mentionName: String = "",
    /** 多页附件页码范围标签(如「第1-3页」) */
    val pageRangeLabel: String = "",
    /** 文档正文(Markdown/纯文本;PPT/PDF 可为提取文本) */
    val textContent: String = "",
    /** 多页文档逐页图(PDF/PPT/笔记页范围);非空时按逐页图发送 */
    val extraPages: List<DraftPageImage> = emptyList(),
) {
    /** 是否带可发送的视觉内容 */
    val hasVisual: Boolean get() = extraPages.isNotEmpty() || png.isNotEmpty()

    /** 是否带文档正文 */
    val hasText: Boolean get() = textContent.isNotBlank()
}

/** 多页文档中的一页(渲染图 + 该页文字,供逐页发送与恢复) */
data class DraftPageImage(
    val pageNumber: Int,
    val png: ByteArray,
    val jpeg: Boolean = false,
    val widthPx: Int,
    val heightPx: Int,
    val text: String = "",
)
