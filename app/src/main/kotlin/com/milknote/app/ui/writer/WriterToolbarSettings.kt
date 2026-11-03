package com.milknote.app.ui.writer

/** 可固定到书写页顶栏的动作;id 用于 DataStore,重命名文案不影响已有配置。 */
enum class WriterToolbarAction(val id: String, val label: String) {
    LASSO("lasso", "框选"),
    TEXT("text", "文字框"),
    SHAPES("shapes", "图形绘制"),
    IMAGE("image", "插入图片"),
    FOCUS("focus", "专注模式"),
    NEW_PAGE("new_page", "新建页"),
    EXPORT("export", "导出"),
    COMMAND("command", "命令面板"),
    DIAGNOSTICS("diagnostics", "笔诊断");

    companion object {
        val defaultPins = listOf(LASSO, TEXT, SHAPES, IMAGE, FOCUS)
        private val byId = entries.associateBy(WriterToolbarAction::id)

        fun fromId(id: String): WriterToolbarAction? = byId[id]
    }
}

/** null = 从未配置,使用默认;空字符串 = 用户主动取消全部固定。 */
fun decodeWriterToolbarPins(raw: String?): List<WriterToolbarAction> {
    if (raw == null) return WriterToolbarAction.defaultPins
    if (raw.isBlank()) return emptyList()
    return raw.split(',')
        .mapNotNull { WriterToolbarAction.fromId(it.trim()) }
        .distinct()
}

fun encodeWriterToolbarPins(pins: List<WriterToolbarAction>): String =
    pins.joinToString(",") { it.id }

fun setWriterToolbarPin(
    pins: List<WriterToolbarAction>,
    action: WriterToolbarAction,
    pinned: Boolean,
): List<WriterToolbarAction> = when {
    pinned && action !in pins -> pins + action
    !pinned -> pins - action
    else -> pins
}

fun moveWriterToolbarPin(
    pins: List<WriterToolbarAction>,
    action: WriterToolbarAction,
    delta: Int,
): List<WriterToolbarAction> {
    val from = pins.indexOf(action)
    if (from < 0) return pins
    val to = (from + delta).coerceIn(0, pins.lastIndex)
    if (to == from) return pins
    return pins.toMutableList().apply { add(to, removeAt(from)) }
}
