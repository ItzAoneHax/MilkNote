package com.milknote.app.ui.agent.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.media.ExifInterface
import android.net.Uri
import com.milknote.agent.ImageBudget
import com.milknote.app.agent.SnipEncoder
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 归一化(0..1)裁剪框。存归一化值而不是像素,才能在「旋转 / 缩放 / 换设备」时保持一致。
 */
data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    /** 收敛到合法框:各边夹到 0..1、允许反向拖拽自动交换、最小边长 [MIN_SPAN] */
    fun clamped(): NormalizedCropRect {
        var l = left.finiteOr(0f).coerceIn(0f, 1f)
        var t = top.finiteOr(0f).coerceIn(0f, 1f)
        var r = right.finiteOr(1f).coerceIn(0f, 1f)
        var b = bottom.finiteOr(1f).coerceIn(0f, 1f)
        if (r < l) {
            val swap = l
            l = r
            r = swap
        }
        if (b < t) {
            val swap = t
            t = b
            b = swap
        }
        if (r - l < MIN_SPAN) {
            val center = (l + r) / 2f
            l = (center - MIN_SPAN / 2f).coerceIn(0f, 1f - MIN_SPAN)
            r = l + MIN_SPAN
        }
        if (b - t < MIN_SPAN) {
            val center = (t + b) / 2f
            t = (center - MIN_SPAN / 2f).coerceIn(0f, 1f - MIN_SPAN)
            b = t + MIN_SPAN
        }
        return NormalizedCropRect(l, t, r, b)
    }

    /** 是否等于整图(容差 0.1%;先 [clamped] 再比较) */
    fun isFullFrame(): Boolean {
        val c = clamped()
        return c.left <= EDGE_EPSILON &&
            c.top <= EDGE_EPSILON &&
            c.right >= 1f - EDGE_EPSILON &&
            c.bottom >= 1f - EDGE_EPSILON
    }

    /**
     * 在框内最大化为指定宽高比(以中心为锚点)。
     *
     * @param imageAspect 原图宽高比(pixelWidth / pixelHeight);归一化宽高比换算成像素需要它
     */
    fun withAspect(aspect: CropAspect, imageAspect: Float): NormalizedCropRect {
        val base = clamped()
        val ratio = aspect.ratio
        if (ratio == null || ratio <= 0f) return base
        if (!imageAspect.isFinite() || imageAspect <= 0f) return base

        val width = base.right - base.left
        val height = base.bottom - base.top
        if (width <= 0f || height <= 0f) return base

        // 归一化空间:w/h * imageAspect = 目标像素宽高比
        val needed = ratio / imageAspect
        val targetWidth: Float
        val targetHeight: Float
        if (needed > width / height) {
            targetWidth = width
            targetHeight = width / needed
        } else {
            targetHeight = height
            targetWidth = height * needed
        }
        if (targetWidth <= 0f || targetHeight <= 0f) return base

        val centerX = (base.left + base.right) / 2f
        val centerY = (base.top + base.bottom) / 2f
        val left = (centerX - targetWidth / 2f).coerceIn(0f, (1f - targetWidth).coerceAtLeast(0f))
        val top = (centerY - targetHeight / 2f).coerceIn(0f, (1f - targetHeight).coerceAtLeast(0f))
        return NormalizedCropRect(
            left = left,
            top = top,
            right = (left + targetWidth).coerceAtMost(1f),
            bottom = (top + targetHeight).coerceAtMost(1f),
        ).clamped()
    }

    private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

    companion object {
        /** 最小归一化边长(2% 图幅),防止裁剪框退化成一个点 */
        const val MIN_SPAN = 0.02f
        private const val EDGE_EPSILON = 0.001f
    }
}

/** 裁剪比例预设 */
enum class CropAspect(val label: String, val ratio: Float?) {
    FREE("自由", null),
    SQUARE("1:1", 1f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_16_9("16:9", 16f / 9f),
}

/** 涂鸦点(归一化 0..1,坐标系 = 旋转/翻转之后、裁剪之前的整图) */
data class DoodlePoint(val x: Float, val y: Float)

/** 一笔涂鸦 */
data class DoodleStroke(
    val colorArgb: Long,
    val widthFraction: Float,
    val points: List<DoodlePoint>,
)

/**
 * 编辑状态(纯数据,零 Android 依赖)。
 *
 * 坐标空间约定:涂鸦点记录在**旋转/翻转之后、裁剪之前**的整图归一化坐标里。
 * 引擎先把涂鸦画到变换后的整图上、最后才裁剪,所以「先涂鸦再裁剪」与「先裁剪再涂鸦」
 * 得到的是同一张图,切换工具不会让笔迹跳位。
 */
data class ImageEditState(
    val crop: NormalizedCropRect = NormalizedCropRect(),
    val rotationDeg: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val documentEnhance: Boolean = false,
    val strokes: List<DoodleStroke> = emptyList(),
) {
    fun rotateLeft(): ImageEditState = copy(rotationDeg = normalizeRotation(rotationDeg + 270))

    fun rotateRight(): ImageEditState = copy(rotationDeg = normalizeRotation(rotationDeg + 90))

    fun toggleFlipH(): ImageEditState = copy(flipHorizontal = !flipHorizontal)

    fun toggleFlipV(): ImageEditState = copy(flipVertical = !flipVertical)

    fun undoStroke(): ImageEditState = if (strokes.isEmpty()) this else copy(strokes = strokes.dropLast(1))

    fun clearStrokes(): ImageEditState = if (strokes.isEmpty()) this else copy(strokes = emptyList())

    /** 全量重置(含裁剪、旋转、翻转、明暗、涂鸦) */
    fun reset(): ImageEditState = ImageEditState()

    fun hasEdits(): Boolean =
        rotationDeg % 360 != 0 ||
            flipHorizontal ||
            flipVertical ||
            brightness != 0f ||
            contrast != 1f ||
            documentEnhance ||
            strokes.isNotEmpty() ||
            !crop.isFullFrame()

    /** 把外部输入收敛到合法范围(角度吸附 90°、明暗/对比夹紧) */
    fun sanitized(): ImageEditState = copy(
        crop = crop.clamped(),
        rotationDeg = normalizeRotation(rotationDeg),
        brightness = if (brightness.isFinite()) brightness.coerceIn(-50f, 50f) else 0f,
        contrast = if (contrast.isFinite()) contrast.coerceIn(0.5f, 1.5f) else 1f,
    )

    private fun normalizeRotation(deg: Int): Int {
        val normalized = ((deg % 360) + 360) % 360
        return normalized / 90 * 90
    }
}

/** 编辑后的编码结果([jpeg] true 表示 PNG 超预算降级成 JPEG) */
data class EncodedImage(
    val bytes: ByteArray,
    val jpeg: Boolean,
    val widthPx: Int,
    val heightPx: Int,
)

/**
 * 位图编辑引擎:旋转/翻转 → 涂鸦 → 裁剪 → 明暗/对比/文稿增强 → 编码。
 *
 * 输出统一走 [SnipEncoder](长边 ≤1568、PNG 优先、超 2MB 降 JPEG q88),与系统相册图片附件同预算。
 */
object ImageEditEngine {

    private const val MIN_CROP_PX = 16

    /**
     * 应用编辑并返回一张新位图(**调用方负责 recycle**)。
     * 没有任何编辑时直接返回 [source] 本身,此时不要 recycle(否则会连带回收调用方的图)。
     */
    fun apply(source: Bitmap, edits: ImageEditState): Bitmap {
        if (source.isRecycled) throw IllegalStateException("源图已被回收，无法编辑")
        val state = edits.sanitized()
        val created = ArrayList<Bitmap>(3)
        var working = source

        transformMatrix(state)?.let { matrix ->
            working = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            created += working
        }

        if (state.strokes.isNotEmpty()) {
            if (working === source) {
                // 绝不在调用方持有的位图上落笔
                val copy = source.copy(Bitmap.Config.ARGB_8888, true)
                    ?: throw IllegalStateException("内存不足，无法复制图片")
                working = copy
                created += copy
            }
            drawStrokes(working, state.strokes)
        }

        var result = working
        val rect = cropRectPixels(state.crop, working.width, working.height)
        val isCropped = rect.left != 0 || rect.top != 0 ||
            rect.width != working.width || rect.height != working.height
        if (isCropped) {
            result = Bitmap.createBitmap(working, rect.left, rect.top, rect.width, rect.height)
            created += result
        }

        colorFilter(state)?.let { filter ->
            val filtered = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
            created += filtered
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = filter }
            Canvas(filtered).drawBitmap(result, 0f, 0f, paint)
            result = filtered
        }

        for (bitmap in created) {
            if (bitmap !== result && !bitmap.isRecycled) bitmap.recycle()
        }
        return result
    }

    /** 应用编辑并编码成附件字节(可直接喂给 `addSystemImageAttachment`) */
    fun applyAndEncode(source: Bitmap, edits: ImageEditState): EncodedImage {
        val edited = apply(source, edits)
        try {
            val (bytes, jpeg) = SnipEncoder.encode(edited)
            val (width, height) = encodedSize(edited.width, edited.height)
            return EncodedImage(bytes = bytes, jpeg = jpeg, widthPx = width, heightPx = height)
        } finally {
            if (edited !== source && !edited.isRecycled) edited.recycle()
        }
    }

    /**
     * 解码相册/相机图片:按 [maxEdgePx] 降采样 + 按 EXIF 方向转正(相机竖拍不会躺倒)。
     * 返回的 Bitmap 由调用方负责 recycle。
     */
    fun decodeSampled(context: Context, uri: Uri, maxEdgePx: Int = 2048): Bitmap? {
        val resolver = context.applicationContext.contentResolver
        val edge = maxEdgePx.coerceIn(256, 4096)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, edge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: return null

        val orientation = readExifOrientation(context, uri)
        return applyExifOrientation(decoded, orientation)
    }

    // ---------------------------------------------------------------- 内部实现

    private fun transformMatrix(state: ImageEditState): Matrix? {
        val rotation = state.rotationDeg
        if (rotation == 0 && !state.flipHorizontal && !state.flipVertical) return null
        return Matrix().apply {
            if (rotation != 0) postRotate(rotation.toFloat())
            if (state.flipHorizontal) postScale(-1f, 1f)
            if (state.flipVertical) postScale(1f, -1f)
        }
    }

    private fun drawStrokes(bitmap: Bitmap, strokes: List<DoodleStroke>) {
        val canvas = Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in strokes) {
            val points = stroke.points.filter { it.x.isFinite() && it.y.isFinite() }
            if (points.isEmpty()) continue
            paint.color = stroke.colorArgb.toInt()
            val widthFraction = if (stroke.widthFraction.isFinite()) {
                stroke.widthFraction.coerceIn(0.001f, 0.25f)
            } else {
                0.01f
            }
            paint.strokeWidth = (widthFraction * width).coerceAtLeast(1f)
            if (points.size == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(
                    points[0].x.coerceIn(0f, 1f) * width,
                    points[0].y.coerceIn(0f, 1f) * height,
                    paint.strokeWidth / 2f,
                    paint,
                )
                paint.style = Paint.Style.STROKE
                continue
            }
            val path = Path()
            path.moveTo(points[0].x.coerceIn(0f, 1f) * width, points[0].y.coerceIn(0f, 1f) * height)
            for (i in 1 until points.size) {
                path.lineTo(
                    points[i].x.coerceIn(0f, 1f) * width,
                    points[i].y.coerceIn(0f, 1f) * height,
                )
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun cropRectPixels(crop: NormalizedCropRect, width: Int, height: Int): IntRect {
        val c = crop.clamped()
        var left = (c.left * width).roundToInt().coerceIn(0, width - 1)
        var top = (c.top * height).roundToInt().coerceIn(0, height - 1)
        var right = (c.right * width).roundToInt().coerceIn(left + 1, width)
        var bottom = (c.bottom * height).roundToInt().coerceIn(top + 1, height)

        val minWidth = min(MIN_CROP_PX, width)
        val minHeight = min(MIN_CROP_PX, height)
        if (right - left < minWidth) {
            val center = (left + right) / 2
            left = (center - minWidth / 2).coerceIn(0, width - minWidth)
            right = left + minWidth
        }
        if (bottom - top < minHeight) {
            val center = (top + bottom) / 2
            top = (center - minHeight / 2).coerceIn(0, height - minHeight)
            bottom = top + minHeight
        }
        return IntRect(left, top, right - left, bottom - top)
    }

    private fun colorFilter(state: ImageEditState): ColorMatrixColorFilter? {
        val brightness = state.brightness
        val contrast = state.contrast
        val hasAdjust = brightness != 0f || contrast != 1f || state.documentEnhance
        if (!hasAdjust) return null

        val matrix = ColorMatrix()
        if (state.documentEnhance) {
            // 文稿增强:去色 → 强对比 → 轻微提亮,让白底更白、字迹更黑(白板/课本/试卷)
            matrix.set(ColorMatrix().apply { setSaturation(0f) })
            matrix.postConcat(contrastMatrix(scale = 1.45f, offset = 16f))
            if (brightness != 0f || contrast != 1f) {
                matrix.postConcat(contrastMatrix(contrast, brightness / 50f * 60f))
            }
        } else {
            matrix.set(contrastMatrix(contrast, brightness / 50f * 50f))
        }
        return ColorMatrixColorFilter(matrix)
    }

    /** 对比度围绕 128 中灰展开;offset 为额外平移(亮度/提亮用) */
    private fun contrastMatrix(scale: Float, offset: Float): ColorMatrix {
        val translate = 128f * (1f - scale) + offset
        return ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (maxOf(width / sample, height / sample) > maxEdge) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private fun readExifOrientation(context: Context, uri: Uri): Int = runCatching {
        context.applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrNull() ?: return bitmap
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun encodedSize(width: Int, height: Int): Pair<Int, Int> {
        val longEdge = maxOf(width, height)
        if (longEdge <= ImageBudget.LONG_EDGE_PX) return width to height
        val scale = ImageBudget.LONG_EDGE_PX.toFloat() / longEdge
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    private data class IntRect(val left: Int, val top: Int, val width: Int, val height: Int)
}
