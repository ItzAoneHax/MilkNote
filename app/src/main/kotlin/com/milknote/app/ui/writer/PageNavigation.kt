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
