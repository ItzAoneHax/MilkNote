package com.milknote.app.ui.writer

import java.util.Locale

/**
 * 导出配置的纯逻辑(无 Android 依赖,可直接单测):
 * 格式元数据、文件名清洗、页面范围解析、逐页文件名。
 */
enum class ExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val title: String,
    val description: String,
) {
    PDF(
        label = "PDF",
        extension = "pdf",
        mimeType = "application/pdf",
        title = "PDF 文档",
        description = "所选页面合并为一个 PDF，保留矢量笔迹、图片与批注卡",
    ),
    JPG(
        label = "JPG",
        extension = "jpg",
        mimeType = "image/jpeg",
        title = "JPG 图片",
        description = "每页一张 JPG，体积更小，适合直接分享",
    ),
    PNG(
        label = "PNG",
        extension = "png",
        mimeType = "image/png",
        title = "PNG 图片",
        description = "每页一张 PNG，无损清晰，适合二次编辑",
    ),
    ZIP(
        label = "ZIP",
        extension = "milk.zip",
        mimeType = "application/zip",
        title = "MilkNote 备份（.milk.zip）",
        description = "整本打包为 MilkNote 自有备份；不是千本笔记 ZIP 格式",
    ),
    ;

    val isImage: Boolean get() = this == JPG || this == PNG
    val isBackup: Boolean get() = this == ZIP
}

/** 页面范围:全部 / 当前页 / 缩略图多选 */
enum class ExportRangeMode(val label: String) {
    ALL("全部页"),
    CURRENT("当前页"),
    SELECTED("多选页面"),
}

const val EXPORT_FALLBACK_NAME = "未命名笔记"
const val EXPORT_MAX_BASE_NAME_LENGTH = 80

/** 文件名清洗:去掉路径分隔符与系统保留字符,收敛空白,限制长度,空名回退。 */
fun sanitizeExportBaseName(
    raw: String,
    fallback: String = EXPORT_FALLBACK_NAME,
): String {
    val cleaned = raw.trim().map { ch ->
        when {
            ch.code < 32 -> '_'
            ch in "\\/:*?\"<>|" -> '_'
            else -> ch
        }
    }.joinToString("")
    val collapsed = cleaned.replace(Regex("\\s+"), " ").trim().trim('.', ' ')
    val capped = collapsed.take(EXPORT_MAX_BASE_NAME_LENGTH).trimEnd('.', ' ')
    return capped.ifBlank { fallback.ifBlank { EXPORT_FALLBACK_NAME } }
}

/**
 * 按笔记本页序解析导出页:
 * - ALL:全部页
 * - CURRENT:当前页(不在列表中则为空)
 * - SELECTED:与当前页序求交集,忽略已不存在的页
 */
fun resolveExportPageIds(
    allPageIds: List<String>,
    currentPageId: String,
    mode: ExportRangeMode,
    selectedPageIds: Set<String>,
): List<String> = when (mode) {
    ExportRangeMode.ALL -> allPageIds
    ExportRangeMode.CURRENT -> allPageIds.filter { it == currentPageId }
    ExportRangeMode.SELECTED -> {
        val selected = selectedPageIds.toSet()
        allPageIds.filter { it in selected }
    }
}

/**
 * 生成最终文件名(含扩展名):
 * - PDF/ZIP 始终一个文件;
 * - JPG/PNG 单页时用基础名,多页时追加页 ID,避免同名覆盖。
 */
fun exportFileNames(
    baseName: String,
    format: ExportFormat,
    pageIds: List<String>,
): List<String> {
    val base = sanitizeExportBaseName(baseName)
    return when (format) {
        ExportFormat.PDF -> listOf("$base.pdf")
        ExportFormat.ZIP -> listOf("${base.removeSuffix(".milk")}.milk.zip")
        ExportFormat.JPG, ExportFormat.PNG -> when (pageIds.size) {
            0 -> emptyList()
            1 -> listOf("$base.${format.extension}")
            else -> pageIds.map { pageId -> "$base-${pageIdToken(pageId)}.${format.extension}" }
        }
    }
}

/** ZIP 内根目录:`<笔记名>.milk`,与本地笔记本目录语义一致。 */
fun exportZipRootDir(baseName: String): String =
    "${sanitizeExportBaseName(baseName).removeSuffix(".milk")}.milk"

/** 页 ID 只保留安全字符,避免异常 ID 污染文件名。 */
private fun pageIdToken(pageId: String): String =
    pageId.map { ch ->
        if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_'
    }.joinToString("").ifBlank { "page" }.lowercase(Locale.ROOT)
