package com.milknote.app.ui.writer

import com.milknote.notes.model.NotebookPage

/** 页序由仓库返回的页面列表决定；越界或当前页未知时不跳转。 */
internal fun adjacentPageId(
    pages: List<NotebookPage>,
    currentPageId: String,
    delta: Int,
): String? {
    val index = pages.indexOfFirst { it.pageId == currentPageId }
    if (index < 0) return null
    return pages.getOrNull(index + delta)?.pageId
}

/**
 * 删除页之后应停留的相邻页：优先下一页，其次上一页，删的是唯一页时返回 null。
 * 本应用没有「封面页」概念，第 1 页也按普通页处理。
 */
internal fun pageIdAfterDelete(
    pages: List<NotebookPage>,
    deletedPageId: String,
): String? {
    val index = pages.indexOfFirst { it.pageId == deletedPageId }
    if (index < 0) return null
    return pages.getOrNull(index + 1)?.pageId ?: pages.getOrNull(index - 1)?.pageId
}
