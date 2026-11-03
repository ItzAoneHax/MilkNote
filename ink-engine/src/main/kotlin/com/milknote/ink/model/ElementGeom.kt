package com.milknote.ink.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** 图片/卡片的旋转矩形几何；坐标均为页坐标。 */
object ElementGeom {
    data class Point(val x: Float, val y: Float)

    data class Transform(
        val dx: Float = 0f,
        val dy: Float = 0f,
        val scale: Float = 1f,
        val rotationDeg: Float = 0f,
        val pivotX: Float = 0f,
        val pivotY: Float = 0f,
    ) {
        fun map(x: Float, y: Float): Point {
            val rad = Math.toRadians(rotationDeg.toDouble())
            val cos = cos(rad).toFloat()
            val sin = sin(rad).toFloat()
            val sx = (x - pivotX) * scale
            val sy = (y - pivotY) * scale
            return Point(
                pivotX + sx * cos - sy * sin + dx,
                pivotY + sx * sin + sy * cos + dy,
            )
        }
    }

    fun rotatedCorners(x: Float, y: Float, width: Float, height: Float, rotationDeg: Float): List<Point> {
        val cx = x + width / 2f
        val cy = y + height / 2f
        val rad = Math.toRadians(rotationDeg.toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()
        return listOf(x to y, x + width to y, x + width to y + height, x to y + height).map { (px, py) ->
            val dx = px - cx
            val dy = py - cy
            Point(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos)
        }
    }

    fun bounds(points: List<Point>): StrokeGeom.Box {
        if (points.isEmpty()) return StrokeGeom.Box(0f, 0f, 0f, 0f)
        return StrokeGeom.Box(
            points.minOf { it.x }, points.minOf { it.y },
            points.maxOf { it.x }, points.maxOf { it.y },
        )
    }

    fun bounds(image: ImageElement): StrokeGeom.Box =
        bounds(rotatedCorners(image.x, image.y, image.width, image.height, image.rotationDeg))

    fun bounds(card: AnnotationCard, height: Float): StrokeGeom.Box = bounds(
        rotatedCorners(card.x, card.y, card.width * card.scale, height * card.scale, card.rotationDeg)
    )

    fun contains(x: Float, y: Float, width: Float, height: Float, rotationDeg: Float, px: Float, py: Float): Boolean {
        val cx = x + width / 2f
        val cy = y + height / 2f
        val rad = Math.toRadians(-rotationDeg.toDouble())
        val dx = px - cx
        val dy = py - cy
        val localX = cx + dx * cos(rad).toFloat() - dy * sin(rad).toFloat()
        val localY = cy + dx * sin(rad).toFloat() + dy * cos(rad).toFloat()
        return localX in x..x + width && localY in y..y + height
    }

    fun contains(image: ImageElement, x: Float, y: Float): Boolean =
        contains(image.x, image.y, image.width, image.height, image.rotationDeg, x, y)

    fun contains(card: AnnotationCard, height: Float, x: Float, y: Float): Boolean = contains(
        card.x, card.y, card.width * card.scale, height * card.scale, card.rotationDeg, x, y,
    )

    fun angleDeg(cx: Float, cy: Float, x: Float, y: Float): Float =
        Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
}
