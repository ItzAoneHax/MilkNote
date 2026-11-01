package com.milknote.agent

/**
 * 上下文预算(AGENT-BACKEND §5.3):常量集中一处,可按 profile 覆写(M2 用全局默认)。
 * 位图缩放/重编码在 app 装配层完成(共享同一组常量)。
 */
object ImageBudget {
    const val LONG_EDGE_PX = 1568            // 笔迹清晰度与 token 成本平衡,各家全兼容
    const val TARGET_BYTES = 2 * 1024 * 1024 // PNG 超限自动降 JPEG q88
    const val HARD_MAX_BYTES = 5 * 1024 * 1024
    const val MAX_IMAGES_PER_REQUEST = 10    // 绝对上限;实际取 min(model.maxImages, 10)
    const val EST_TOKENS_PER_IMAGE = 1100    // 1568px 经验值,仅 UI 展示
    const val CHAPTER_IMAGE_CAP = 8          // 整章封顶

    /** 单请求图上限 */
    fun maxImagesFor(model: ModelInfo?): Int =
        minOf(model?.maxImages ?: 4, MAX_IMAGES_PER_REQUEST)

    /** 截断(§5.2):超出上限丢最旧的补充图,选区(最前)保留 */
    fun <T> truncate(images: List<T>, max: Int): Pair<List<T>, Int> {
        if (images.size <= max) return images to 0
        return images.take(max) to (images.size - max)
    }

    /** 整章取页:本章其余页按页序截「max - 已用」张,封顶 CHAPTER_IMAGE_CAP */
    fun chapterPageCount(totalPages: Int, imagesUsed: Int, max: Int): Int =
        minOf((max - imagesUsed).coerceAtLeast(0), CHAPTER_IMAGE_CAP, (totalPages - 1).coerceAtLeast(0))

    /** token 估算:图 ×1.1k + 文本 chars/1.6(§7.3) */
    fun estTokens(images: Int, textChars: Int): Int =
        images * EST_TOKENS_PER_IMAGE + (textChars / 1.6).toInt()

    /** PNG 超预算 → JPEG q88(纯决策;编码在装配层) */
    fun shouldFallbackToJpeg(encodedBytes: Int): Boolean = encodedBytes > TARGET_BYTES

    /** 超硬上限 → 缩一档长边重编码(ContextTooLarge 自动缩图路径) */
    fun downscaledLongEdge(current: Int): Int = (current * 0.75f).toInt()

    fun logicalNameSnip(w: Int, h: Int): String = "snip_${w}×${h}.png"
    fun logicalNamePage(pageId: String): String = "page_$pageId.png"
}
