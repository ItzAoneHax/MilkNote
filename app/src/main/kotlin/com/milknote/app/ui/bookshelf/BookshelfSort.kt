package com.milknote.app.ui.bookshelf

import com.milknote.notes.model.NotebookMeta

/** 书架排序方式（P057）。所有条目均有真实本地数据支撑。 */
enum class BookshelfSortMode(val label: String) {
    Updated("修改时间"),
    Name("名称"),
    LastOpened("上次打开"),
    Tag("标签"),
}

/** 纯 Kotlin 确定性排序；相同键以名称、时间、id 兜底，避免刷新抖动。 */
fun sortNotebooks(
    notebooks: List<NotebookMeta>,
    mode: BookshelfSortMode,
): List<NotebookMeta> = when (mode) {
    BookshelfSortMode.Updated -> notebooks.sortedWith(
        compareByDescending<NotebookMeta> { it.updatedAt }
            .thenBy { it.name.lowercase() }
            .thenBy { it.id },
    )
    BookshelfSortMode.Name -> notebooks.sortedWith(
        compareBy<NotebookMeta> { it.name.lowercase() }
            .thenByDescending { it.updatedAt }
            .thenBy { it.id },
    )
    BookshelfSortMode.LastOpened -> notebooks.sortedWith(
        compareByDescending<NotebookMeta> { it.lastOpened }
            .thenByDescending { it.updatedAt }
            .thenBy { it.id },
    )
    BookshelfSortMode.Tag -> notebooks.sortedWith(
        compareBy<NotebookMeta> { it.tagColor.ifBlank { it.coverColor } }
            .thenBy { it.name.lowercase() }
            .thenBy { it.id },
    )
}
