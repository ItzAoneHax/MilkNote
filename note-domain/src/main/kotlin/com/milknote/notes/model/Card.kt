package com.milknote.notes.model

import kotlinx.serialization.Serializable

/** 卡片默认颜色(与书架主色 accent 一致;颜色只存 hex,不在模型层解释语义) */
const val DEFAULT_CARD_COLOR = "#3B5BEC"

/**
 * 本地卡片(独立于页面批注卡 [com.milknote.ink.model.AnnotationCard]):
 * 标题 / 正文 / 可选图片,标签与颜色用于跨笔记本组织。
 *
 * 所有字段都有默认值:旧版本数据缺字段时可解码;新增字段由 Json.ignoreUnknownKeys 兼容。
 * [sourceNotebookId] / [sourcePageId] 记录摘取来源,手工新建的卡片为 null。
 * id 为空白的卡片由 [com.milknote.notes.storage.CardRepository] 忽略,不落盘。
 */
@Serializable
data class NoteCard(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val imageSrc: String? = null,
    val tags: List<String> = emptyList(),
    val color: String = DEFAULT_CARD_COLOR,
    val sourceNotebookId: String? = null,
    val sourcePageId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
