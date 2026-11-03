package com.milknote.app.agent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.milknote.agent.PageRef
import com.milknote.agent.Rect4
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.Stroke
import com.milknote.ink.render.AnnotationCardRenderer
import com.milknote.ink.render.ImageElementRenderer
import com.milknote.ink.render.PaperRenderer
import com.milknote.ink.render.StrokeRenderer
import com.milknote.notes.model.PAGE_SIZES_MM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 页面离屏渲染(AGENT-BACKEND §4.1 InAppSnipSource 的渲染核心):
 * 纸面 + 墨迹 + 批注全层,与屏幕呈现同一绘制路径 —— 矢量重绘,不依赖 GL/截屏。
 * 渲染源优先取内存(AgentBridge),缺页回退磁盘。
 */
object PageRenderer {

    const val MM_TO_PX = 5.67f
    const val PAGE_DPI_SCALE = 2f          // 全页渲染清晰度

    /** 全页位图(rect=null)或选区裁剪位图 */
    suspend fun render(page: PageRef, rect: Rect4?): Bitmap? = withContext(Dispatchers.Default) {
        val data = loadData(page) ?: return@withContext null
        if (rect == null) renderFull(data) else renderRect(data, rect)
    }

    /** 整页(2x);snip 整页上下文与缩略图/导出共用 */
    fun renderFull(data: AgentBridge.PageData): Bitmap {
        val (pageW, pageH) = pageDimsPx(data.pageSize, data.orientation)
        val w = (pageW * PAGE_DPI_SCALE).toInt()
        val h = (pageH / pageW * w).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val scale = w / pageW
        canvas.scale(scale, scale)
        drawAll(canvas, data, pageW, pageH, clip = null)
        return bmp
    }

    /** 选区裁剪:按选区长边 ~1568×2 渲染源区域(预算内再由 SnipEncoder 统一收口) */
    private fun renderRect(data: AgentBridge.PageData, rect: Rect4): Bitmap {
        val r = rect.normalized()
        val (pageW, _) = pageDimsPx(data.pageSize, data.orientation)
        val target = com.milknote.agent.ImageBudget.LONG_EDGE_PX
        val scale = (target * 1.25f) / maxOf(r.width, r.height).coerceAtLeast(1f)
        val w = (r.width * scale).toInt().coerceAtLeast(8)
        val h = (r.height * scale).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        canvas.translate(-r.l, -r.t)
        drawAll(canvas, data, pageW, pageDimsPx(data.pageSize, data.orientation).second, clip = RectF(r.l, r.t, r.r, r.b))
        return bmp
    }

    /** 纸 + 点阵 + 图片 + 笔迹 + 批注卡(与 InkSurfaceView.onDraw 同一顺序/同一路径) */
    fun drawAll(
        canvas: Canvas,
        data: AgentBridge.PageData,
        pageW: Float,
        pageH: Float,
        clip: RectF?,
    ) {
        if (clip != null) canvas.clipRect(clip)
        val paperPaint = Paint().apply { color = 0xFFFAF9F5.toInt() }
        canvas.drawRect(0f, 0f, pageW, pageH, paperPaint)   // 显式矩形(禁 drawColor)
        PaperRenderer.draw(canvas, data.paper, pageW, pageH, 0xFFD8D4C8.toInt())
        if (data.images.isNotEmpty()) {
            val dir = data.imageDir?.let { java.io.File(it) }
            for (img in data.images) {
                ImageElementRenderer.draw(canvas, img, dir)
            }
        }
        val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        for (s in data.strokes) {
            paint.color = s.color.toInt()
            paint.alpha = if (s.tool == InkTool.HIGHLIGHTER) (255 * 0.45f).toInt() else 255
            canvas.drawPath(StrokeRenderer.buildPath(s, MM_TO_PX, com.milknote.ink.model.PressureGamma.STANDARD), paint)
        }
        for (card in data.cards) {
            AnnotationCardRenderer.draw(canvas, card, dark = false, expanded = true)
        }
    }

    fun pageDimsPx(pageSize: String, orientation: String): Pair<Float, Float> {
        val (w, h) = PAGE_SIZES_MM[pageSize] ?: PAGE_SIZES_MM.getValue("A4")
        return if (orientation == "landscape") h * MM_TO_PX to w * MM_TO_PX else w * MM_TO_PX to h * MM_TO_PX
    }

    private suspend fun loadData(page: PageRef): AgentBridge.PageData? {
        // 内存优先;图片层由磁盘补齐(pageProvider 的快照可能不含 images/imageDir)
        AgentBridge.pageProvider?.invoke(page)?.let { return fillImages(page, it) }
        // 回退:从磁盘读(非当前页的整章页;图片一并,notebookDir 解析 src)
        return runCatching {
            val repo = com.milknote.app.di.ServiceLocator.repository
            val meta = repo.listNotebooks().firstOrNull { it.id == page.notebookId } ?: return null
            AgentBridge.PageData(
                strokes = repo.loadPage(page.notebookId, page.pageId),
                cards = repo.loadAnnotations(page.notebookId, page.pageId),
                paper = meta.paper,
                pageSize = meta.pageSize,
                orientation = meta.orientation,
                images = repo.loadImages(page.notebookId, page.pageId),
                imageDir = repo.notebookDir(page.notebookId).absolutePath,
            )
        }.getOrNull()
    }

    /** 图片层补齐:<pageId>.img.json 在磁盘上则注入(快照未携带时) */
    private suspend fun fillImages(page: PageRef, data: AgentBridge.PageData): AgentBridge.PageData {
        if (data.images.isNotEmpty() && data.imageDir != null) return data
        return runCatching {
            val repo = com.milknote.app.di.ServiceLocator.repository
            val imgs = repo.loadImages(page.notebookId, page.pageId)
            if (imgs.isEmpty() && data.imageDir != null) data
            else data.copy(
                images = if (data.images.isEmpty()) imgs else data.images,
                imageDir = data.imageDir ?: repo.notebookDir(page.notebookId).absolutePath,
            )
        }.getOrDefault(data)
    }

    /** 旧签名兼容:纯笔迹页(strokes only) */
    fun dataOf(strokes: List<Stroke>, cards: List<AnnotationCard>, paper: String, pageSize: String, orientation: String) =
        AgentBridge.PageData(strokes, cards, paper, pageSize, orientation)
}
