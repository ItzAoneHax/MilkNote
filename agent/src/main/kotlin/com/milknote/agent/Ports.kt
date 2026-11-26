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

// =====================================================================
// 工具 / 技能端口(纯 Kotlin,装配在 app 层)
// :agent 不依赖 ink-engine / note-domain,页面与画布操作全部经此端口。
// =====================================================================

/** 页面元素摘要(kind = card | image | media | stroke) */
data class PageElementSummary(
    val id: String,
    val kind: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val xRatio: Float,
    val yRatio: Float,
    val preview: String,
)

/** 页序信息(index 为 1-based) */
data class PageInfo(val pageId: String, val index: Int, val isCurrent: Boolean)

/** 页面像素尺寸 */
data class PageDimensions(val widthPx: Float, val heightPx: Float)

/** 笔记本工作区:页管理 + 画布元素操作(由 app 装配层实现) */
interface NotebookWorkspacePort {
    suspend fun listPages(notebookId: String): List<PageInfo>

    suspend fun pageDimensions(page: PageRef): PageDimensions?

    /** position: after | before | end;anchorPageId 为 after/before 的锚点 */
    suspend fun addPage(
        notebookId: String,
        position: String,
        anchorPageId: String?,
        switchTo: Boolean,
    ): PageInfo?

    suspend fun deletePage(notebookId: String, pageId: String): Boolean

    /** 插入 Markdown 卡片;返回卡片 id(null = 失败) */
    suspend fun insertMarkdownCard(
        page: PageRef,
        markdown: String,
        xPx: Float,
        yPx: Float,
        widthPx: Float,
    ): String?

    suspend fun listElements(page: PageRef): List<PageElementSummary>

    /** x/y 为绝对目标坐标(px);dx/dy 为相对偏移(px);均为 null 表示不移动 */
    suspend fun moveElement(
        page: PageRef,
        elementId: String,
        elementKind: String?,
        targetX: Float?,
        targetY: Float?,
        dx: Float?,
        dy: Float?,
    ): Boolean

    suspend fun deleteElement(page: PageRef, elementId: String, elementKind: String?): Boolean
}

/** 技能管理:读写 AgentSkill(内置技能由 AgentStore 首次加载时补齐) */
interface SkillManagerPort {
    fun listSkills(): List<AgentSkill>

    fun findEnabledSkill(idOrName: String): AgentSkill?

    fun createOrUpdateSkill(
        id: String?,
        name: String,
        summary: String,
        content: String,
        enabled: Boolean,
    ): AgentSkill
}

/** 联网搜索结果条目 */
data class WebSearchItem(val title: String, val url: String, val snippet: String)

/** 联网搜索端口(默认实现见 DefaultWebSearchPort) */
interface WebSearchPort {
    suspend fun search(
        query: String,
        maxResults: Int,
        settings: Map<String, String>,
    ): Result<List<WebSearchItem>>
}
