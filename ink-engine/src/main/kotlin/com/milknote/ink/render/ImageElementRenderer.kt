package com.milknote.ink.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.milknote.ink.model.ImageElement
import java.io.File

/**
 * 图片元素渲染(GRAPHICS-ENGINE §图片层):
 * 以 `imageDir + src(相对路径)` 解码位图并缓存(LRU 粗略上限防膨胀),
 * 绘制到页坐标(x/y/width/height 与笔迹同一坐标系)。绘制层序:纸面 → 图片 → 笔迹 → 批注卡。
 * 原始文件是数据、显示尺寸是样式:位图按元素矩形缩放绘制,不做色彩/暗色映射(保持素材原样)。
 */
object ImageElementRenderer {

    /** 解码上限(长边 px;超限降采样,防整页大图吃内存) */
    const val MAX_DECODE_EDGE_PX = 2048
    private const val CACHE_MAX_ENTRIES = 32

    /** src → 位图缓存(键 = 绝对路径;[clearCache] 全量失效) */
    private val cache = LinkedHashMap<String, Bitmap>()

    private val paint = Paint().apply { isAntiAlias = true }

    private val rect = RectF()

    /** 解析 src → 绝对文件(imageDir 为空或 src 为空返回 null) */
    fun fileOf(image: ImageElement, imageDir: File?): File? {
        if (imageDir == null || image.src.isBlank()) return null
        val f = File(imageDir, image.src)
        return if (f.isFile) f else null
    }

    /** 取解码后的位图(带缓存;解码失败/文件缺失返回 null) */
    fun bitmap(image: ImageElement, imageDir: File?): Bitmap? {
        val f = fileOf(image, imageDir) ?: return null
        val key = f.absolutePath
        cache[key]?.let { if (!it.isRecycled) return it }
        if (cache.size >= CACHE_MAX_ENTRIES) {
            val it = cache.entries.iterator()
            while (cache.size >= CACHE_MAX_ENTRIES && it.hasNext()) {
                it.next(); it.remove()
            }
        }
        val bmp = decode(f) ?: return null
        cache[key] = bmp
        return bmp
    }

    /** 绘制(页坐标系;dx/dy 为拖动预览偏移;位图缺失画占位细框,导出/截图不空白报错) */
    fun draw(canvas: Canvas, image: ImageElement, imageDir: File?, dx: Float = 0f, dy: Float = 0f) {
        val bmp = bitmap(image, imageDir)
        rect.set(image.x + dx, image.y + dy, image.x + dx + image.width, image.y + dy + image.height)
        if (bmp == null) {
            placeholderPaint.color = 0x143B5BEC
            canvas.drawRect(rect, placeholderPaint)
            placeholderPaint.color = 0x663B5BEC
            placeholderPaint.style = Paint.Style.STROKE
            placeholderPaint.strokeWidth = 1.5f
            canvas.drawRect(rect, placeholderPaint)
            placeholderPaint.style = Paint.Style.FILL
            return
        }
        canvas.drawBitmap(bmp, null, rect, paint)
    }

    /** 命中测试(页坐标;拖动/删除交互用) */
    fun hit(image: ImageElement, x: Float, y: Float): Boolean =
        x >= image.x && x <= image.x + image.width && y >= image.y && y <= image.y + image.height

    /** 全量失效(切换笔记本/图片文件重写后调用) */
    fun clearCache() = cache.clear()

    /** 边界先行解码 → 按长边 [MAX_DECODE_EDGE_PX] 降采样(2 的幂 inSampleSize) */
    private fun decode(f: File): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options()
        var sample = 1
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / (sample * 2) >= MAX_DECODE_EDGE_PX) sample *= 2
        opts.inSampleSize = sample
        BitmapFactory.decodeFile(f.absolutePath, opts)
    }.getOrNull()

    private val placeholderPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
}
