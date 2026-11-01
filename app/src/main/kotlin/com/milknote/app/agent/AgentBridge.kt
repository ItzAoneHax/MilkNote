package com.milknote.app.agent

import android.graphics.Bitmap
import com.milknote.agent.AnnotationSink
import com.milknote.agent.ImageBudget
import com.milknote.agent.PageRef
import com.milknote.agent.Rect4
import com.milknote.agent.SnipResult
import com.milknote.agent.SnipSource
import java.io.ByteArrayOutputStream

/**
 * App 装配层(AGENT-BACKEND §2.1)::agent 端口的实现。
 * WriterViewModel 在页面打开/切换时把「渲染当前页」与「插入批注」注册进来,
 * AgentController 通过端口调用 —— :agent 不依赖 ink-engine / note-domain。
 */
object AgentBridge {

    /** 页面数据快照(渲染输入;由 WriterViewModel 提供) */
    data class PageData(
        val strokes: List<com.milknote.ink.model.Stroke>,
        val cards: List<com.milknote.ink.model.AnnotationCard>,
        val paper: String,
        val pageSize: String,
        val orientation: String,
    )

    /** 渲染回调:pageId → 页面数据(null = 页面不在内存,回退磁盘读取由实现方决定) */
    @Volatile var pageProvider: (suspend (PageRef) -> PageData?)? = null

    /** 批注插入回调:返回卡 id(null = 页面不可达) */
    @Volatile var annotationInserter: (suspend (PageRef, Rect4?, String, Boolean, Float) -> String?)? = null
}

/**
 * 选区/整页位图编码(§3.5 图片编码预算):长边 ≤1568;PNG 优先,>2MB 降 JPEG q88。
 */
object SnipEncoder {

    fun encode(src: Bitmap): Pair<ByteArray, Boolean> {
        var bmp = src
        val longEdge = maxOf(bmp.width, bmp.height)
        if (longEdge > ImageBudget.LONG_EDGE_PX) {
            val scale = ImageBudget.LONG_EDGE_PX.toFloat() / longEdge
            bmp = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        }
        val png = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        if (!ImageBudget.shouldFallbackToJpeg(png.size)) return png to false
        val jpg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
        return if (jpg.size < png.size) jpg to true else png to false
    }
}

/** InAppSnipSource(§4.1):离屏软件渲染(纸面+墨迹+批注全层),矢量重绘非截屏 */
class AppSnipSource(
    private val render: suspend (PageRef, Rect4?) -> Bitmap?,
) : SnipSource {

    override suspend fun captureSnip(page: PageRef, rect: Rect4): SnipResult? {
        val bmp = render(page, rect) ?: return null
        val (bytes, jpeg) = SnipEncoder.encode(bmp)
        return SnipResult(
            png = bytes, jpeg = jpeg,
            widthPx = bmp.width, heightPx = bmp.height,
            logicalName = if (jpeg) {
                "snip_${bmp.width}×${bmp.height}.jpg"
            } else {
                ImageBudget.logicalNameSnip(bmp.width, bmp.height)
            },
        )
    }

    override suspend fun capturePage(page: PageRef): SnipResult? {
        val bmp = render(page, null) ?: return null
        val (bytes, jpeg) = SnipEncoder.encode(bmp)
        return SnipResult(
            png = bytes, jpeg = jpeg,
            widthPx = bmp.width, heightPx = bmp.height,
            logicalName = ImageBudget.logicalNamePage(page.pageId),
        )
    }

    override suspend fun pagesOf(notebookId: String): List<PageRef> =
        pagesListProvider?.invoke(notebookId) ?: emptyList()

    /** 页 id 列表提供方(WriterViewModel 注册;整章组装用) */
    var pagesListProvider: (suspend (String) -> List<PageRef>)? = null
}

/** AnnotationSink(§9):Agent 结果卡 → 页面批注层 + 撤销栈(经 AgentBridge 转发到 WriterViewModel) */
class AppAnnotationSink : AnnotationSink {
    override suspend fun insertCard(
        page: PageRef,
        anchor: Rect4?,
        text: String,
        latex: Boolean,
        cardWidth: Float,
    ): String? = AgentBridge.annotationInserter?.invoke(page, anchor, text, latex, cardWidth)
}
