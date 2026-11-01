package com.milknote.agent

/**
 * 端口(§2.1)::agent 不依赖 ink-engine / note-domain,
 * 页面渲染与批注写入由 app 装配层实现(与 PenInputPort 同款思路)。
 */

/** 框选截图采集(§4.1):页面坐标矩形 → 已按预算编码的 PNG */
interface SnipSource {
    /** 选区截图(纸面+墨迹+批注全层,矢量重绘非截屏) */
    suspend fun captureSnip(page: PageRef, rect: Rect4): SnipResult?

    /** 整页渲染(当前页上下文 / 整章页) */
    suspend fun capturePage(page: PageRef): SnipResult?

    /** 笔记本页序(整章 chips 预估/组装用) */
    suspend fun pagesOf(notebookId: String): List<PageRef>
}

data class SnipResult(
    val png: ByteArray,          // 已按 ImageBudget 编码(PNG 优先,JPEG 降级)
    val jpeg: Boolean = false,
    val widthPx: Int,
    val heightPx: Int,
    val logicalName: String,     // snip_1246×742.png / page_p0002.png
)

/** 结果动作落点(§9):把 Agent 结果卡插入页面批注层(实现写 note-domain + 撤销栈) */
interface AnnotationSink {
    /** 插入结果卡;anchor 优先选区正下方;返回卡 id(null = 页面不可达) */
    suspend fun insertCard(
        page: PageRef,
        anchor: Rect4?,
        text: String,
        latex: Boolean,
        cardWidth: Float,
    ): String?
}
