package com.milknote.app.ui.bookshelf

import com.milknote.notes.model.PAGE_SIZES_MM
import com.milknote.notes.model.PageSpec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * PDF 导入的数值上限与渲染常量(纯 JVM,便于单元测试)。
 *
 * [MM_TO_PX] 必须与 `com.milknote.app.agent.PageRenderer.MM_TO_PX` 一致,
 * 否则整页图片与纸面坐标会错位;导入侧通过参数显式传入调用方的比例。
 */
object PdfImportLimits {
    /** 单次导入页数上限,防止误选超大 PDF 造成长时间占用。 */
    const val MAX_PAGES = 300

    /** 单个 PDF 文件大小上限(100 MB)。 */
    const val MAX_FILE_BYTES = 100L * 1024 * 1024

    /** 单页物理尺寸上限(mm),超过视为异常页面。 */
    const val MAX_PAGE_MM = 2_000f

    /** 页坐标比例:1 mm 对应的页内像素(与既有书写/导出几何一致)。 */
    const val MM_TO_PX = 5.67f

    /** 导入位图的清晰度倍数(与全页离屏渲染一致,便于放大查看/导出)。 */
    const val RENDER_SCALE = 2f

    /** 单页位图长边上限,防止超大页面拖垮内存。 */
    const val MAX_RENDER_EDGE_PX = 4_096
}

/** 渲染位图像素尺寸。 */
data class RenderSizePx(val width: Int, val height: Int)

/**
 * PDF 页面几何:PDF 页面尺寸以 pt(1/72 inch)给出,笔记本以 mm 记录物理尺寸。
 * 逐页 [PageSpec] 保留原始页面框方向(横向 PDF 不会被强制转向)。
 */
object PdfImportGeometry {
    const val POINTS_PER_INCH = 72f
    const val MM_PER_INCH = 25.4f

    /** PDF 点 → 毫米。 */
    fun pointsToMm(points: Int): Float = points / POINTS_PER_INCH * MM_PER_INCH

    /** 页面物理尺寸;纸面设为空白/纯白,避免整页图片边缘透出点阵。 */
    fun pageSpec(widthPoints: Int, heightPoints: Int): PageSpec = PageSpec(
        widthMm = pointsToMm(widthPoints),
        heightMm = pointsToMm(heightPoints),
        paper = "blank",
        paperColor = "#FFFFFF",
    )

    /** 页坐标 px(ImageElement 的整页宽度/高度)。 */
    fun pageSizePx(
        spec: PageSpec,
        mmToPx: Float = PdfImportLimits.MM_TO_PX,
    ): Pair<Float, Float> = spec.widthMm * mmToPx to spec.heightMm * mmToPx

    /**
     * 渲染位图尺寸:物理尺寸 × [mmToPx] × [scale],长边超过 [maxEdgePx] 时等比缩小。
     * 保持宽高比,最小 1px。
     */
    fun renderSizePx(
        spec: PageSpec,
        mmToPx: Float = PdfImportLimits.MM_TO_PX,
        scale: Float = PdfImportLimits.RENDER_SCALE,
        maxEdgePx: Int = PdfImportLimits.MAX_RENDER_EDGE_PX,
    ): RenderSizePx {
        val rawWidth = (spec.widthMm * mmToPx * scale).roundToInt().coerceAtLeast(1)
        val rawHeight = (spec.heightMm * mmToPx * scale).roundToInt().coerceAtLeast(1)
        val longEdge = max(rawWidth, rawHeight)
        if (longEdge <= maxEdgePx) return RenderSizePx(rawWidth, rawHeight)
        val factor = maxEdgePx.toFloat() / longEdge
        return RenderSizePx(
            (rawWidth * factor).roundToInt().coerceAtLeast(1),
            (rawHeight * factor).roundToInt().coerceAtLeast(1),
        )
    }

    /**
     * 最接近的笔记本级纸张(A4/A5/B5)。
     * 逐页尺寸已写入 pageSpecs;这里只兜底尚未读取 pageSpecs 的旧渲染路径。
     */
    fun nearestPageSize(spec: PageSpec): String {
        val portraitWidth = min(spec.widthMm, spec.heightMm)
        val portraitHeight = max(spec.widthMm, spec.heightMm)
        return PAGE_SIZES_MM.minByOrNull { (_, size) ->
            abs(size.first - portraitWidth) + abs(size.second - portraitHeight)
        }?.key ?: "A4"
    }

    /** 页面方向字符串(与 NotebookMeta.orientation 取值一致)。 */
    fun orientationOf(spec: PageSpec): String =
        if (spec.widthMm > spec.heightMm) "landscape" else "portrait"
}

/**
 * PDF 导入的显示名/笔记本名规则(纯 JVM)。
 *
 * - 优先 SAF 的 DISPLAY_NAME,其次调用方给出的 Uri 末段提示;
 * - 去掉目录、`.pdf` 后缀,清洗文件系统非法字符;
 * - 清洗后为空时回退「PDF 导入」。
 */
object PdfImportNaming {
    const val DEFAULT_NOTEBOOK_NAME = "PDF 导入"
    const val MAX_NAME_LENGTH = 48

    private val ILLEGAL_FILE_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val WHITESPACE = Regex("\\s+")

    /** 人类可读名称:去掉目录与 `.pdf` 后缀;永不返回空串。 */
    fun displayName(rawName: String?, uriFallback: String? = null): String {
        cleanDisplay(rawName)?.takeIf { it.isNotBlank() }?.let { return it }
        cleanDisplay(uriFallback)?.takeIf { it.isNotBlank() }?.let { return it }
        return DEFAULT_NOTEBOOK_NAME
    }

    /** 安全笔记本名:在 [displayName] 基础上清洗非法字符并限制长度。 */
    fun notebookName(rawName: String?, uriFallback: String? = null): String =
        sanitize(displayName(rawName, uriFallback))

    /** 清洗名称:非法文件字符/控制字符 → 空格,压缩连续空白,去首尾点与空格,限制长度。 */
    fun sanitize(raw: String): String {
        val withoutIllegal = raw.replace(ILLEGAL_FILE_CHARS, " ")
        val collapsed = withoutIllegal.replace(WHITESPACE, " ").trim().trim('.', ' ')
        val limited = if (collapsed.length > MAX_NAME_LENGTH) {
            collapsed.take(MAX_NAME_LENGTH).trim()
        } else {
            collapsed
        }
        return limited.ifBlank { DEFAULT_NOTEBOOK_NAME }
    }

    private fun cleanDisplay(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        return stripPdfExtension(basename(value)).trim()
    }

    /** 同时兼容 `/` 与 `\\`(SAF 名称通常不含目录,防御性处理剪贴板/第三方来源)。 */
    private fun basename(value: String): String =
        value.substringAfterLast('/').substringAfterLast('\\')

    private fun stripPdfExtension(name: String): String =
        if (name.endsWith(".pdf", ignoreCase = true)) name.dropLast(4) else name
}
