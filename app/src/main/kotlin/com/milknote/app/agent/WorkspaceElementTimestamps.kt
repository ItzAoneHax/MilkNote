package com.milknote.app.agent

import com.milknote.agent.ElementTimestamp
import com.milknote.agent.Rect4
import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.ElementHitGeom
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.PageElement
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.orderedElements
import com.milknote.ink.render.AnnotationCardRenderer

/**
 * 工作区元素时间查询（任务 C）。
 *
 * 输入 = 页面原始像素的单点（[Rect4] 退化矩形）或双顶点轴对齐矩形（工具侧已 normalize）；
 * 输出 = 所有**相交**元素的创建/写入时间，按渲染层序 **从顶层至底层**
 * （[orderedElements] 的反序：后绘制者覆盖前者），被上层遮挡的元素照常返回。
 *
 * 命中判定走 [ElementHitGeom]：普通笔迹沿真实路径与渲染笔宽，形状只命中可见轮廓，
 * 图片/媒体/卡片按旋转矩形足迹（卡片含展开高度与 `scale`）。
 * 可选 [erasedStrokes]（历史/已被整笔擦除）追加在现态之后，`erased = true`。
 */
internal object WorkspaceElementTimestamps {

    const val KIND_STROKE = "stroke"
    const val KIND_CARD = "card"
    const val KIND_IMAGE = "image"
    const val KIND_MEDIA = "media"

    fun of(
        strokes: List<Stroke>,
        cards: List<AnnotationCard>,
        images: List<ImageElement>,
        media: List<MediaElement>,
        rect: Rect4,
        erasedStrokes: List<Stroke> = emptyList(),
    ): List<ElementTimestamp> {
        val area = ElementHitGeom.Area(rect.l, rect.t, rect.r, rect.b)
        val result = ArrayList<ElementTimestamp>(strokes.size + cards.size + images.size + media.size)
        for (element in orderedElements(strokes, cards, images, media).asReversed()) {
            when (element) {
                is PageElement.Ink -> if (ElementHitGeom.stroke(element.value, area, PageRenderer.MM_TO_PX)) {
                    result += strokeTimestamp(element.value)
                }
                is PageElement.Card -> if (ElementHitGeom.card(element.value, cardHeight(element.value), area)) {
                    result += cardTimestamp(element.value)
                }
                is PageElement.Image -> if (ElementHitGeom.image(element.value, area)) {
                    result += elementTimestamp(
                        element.value.id, KIND_IMAGE, element.value.createdAt,
                    )
                }
                is PageElement.Media -> if (ElementHitGeom.media(element.value, area)) {
                    result += elementTimestamp(
                        element.value.id, KIND_MEDIA, element.value.createdAt,
                    )
                }
            }
        }
        if (erasedStrokes.isNotEmpty()) {
            // 历史笔迹不再参与渲染，按同一 z 序（顶层优先）追加在现态之后
            for (element in orderedElements(erasedStrokes, emptyList(), emptyList()).asReversed()) {
                if (element !is PageElement.Ink) continue
                if (ElementHitGeom.stroke(element.value, area, PageRenderer.MM_TO_PX)) {
                    result += strokeTimestamp(element.value).copy(erased = true)
                }
            }
        }
        return result
    }

    private fun strokeTimestamp(stroke: Stroke): ElementTimestamp = ElementTimestamp(
        id = stroke.id,
        kind = KIND_STROKE,
        createdAtMs = stroke.createdAt.takeIf { it > 0L },
        durationMs = when {
            stroke.points.size < 2 -> null
            else -> (stroke.points.last().timeMs - stroke.points.first().timeMs).coerceAtLeast(0L)
        },
    )

    private fun cardTimestamp(card: AnnotationCard): ElementTimestamp =
        elementTimestamp(card.id, KIND_CARD, card.createdAt)

    private fun elementTimestamp(id: String, kind: String, createdAt: Long): ElementTimestamp =
        ElementTimestamp(id = id, kind = kind, createdAtMs = createdAt.takeIf { it > 0L })

    /**
     * 卡片展开高度（渲染层同源）。JVM 单测没有 StaticLayout 时会抛 Stub，
     * 回退到按可见行数估算 —— 只影响测试环境，设备上始终走真实排版。
     */
    private fun cardHeight(card: AnnotationCard): Float =
        runCatching { AnnotationCardRenderer.cardHeight(card, expanded = true) }
            .getOrElse { estimatedCardHeight(card) }
            .coerceAtLeast(1f)

    private fun estimatedCardHeight(card: AnnotationCard): Float {
        val lines = card.text.lines().count { it.isNotBlank() }.coerceAtLeast(1)
        return AnnotationCardRenderer.PAD_V_PX * 2f +
            lines * card.textStyle.fontSizePx * AnnotationCardRenderer.LINE_SPACING
    }
}
