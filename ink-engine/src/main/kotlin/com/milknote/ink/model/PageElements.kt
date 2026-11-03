package com.milknote.ink.model

/** 页面元素的稳定引用；选择、调层与持久化共用，避免不同类型之间 id 冲突。 */
data class ElementRef(val kind: Kind, val id: String) {
    enum class Kind(val legacyRank: Int) { IMAGE(0), STROKE(1), CARD(2) }
}

data class LayerState(val ref: ElementRef, val zIndex: Long)

/** 全局绘制顺序中的一个元素。 */
sealed interface PageElement {
    val ref: ElementRef
    val zIndex: Long

    data class Image(val value: ImageElement) : PageElement {
        override val ref = ElementRef(ElementRef.Kind.IMAGE, value.id)
        override val zIndex get() = value.zIndex
    }

    data class Ink(val value: Stroke) : PageElement {
        override val ref = ElementRef(ElementRef.Kind.STROKE, value.id)
        override val zIndex get() = value.zIndex
    }

    data class Card(val value: AnnotationCard) : PageElement {
        override val ref = ElementRef(ElementRef.Kind.CARD, value.id)
        override val zIndex get() = value.zIndex
    }
}

private data class IndexedElement(val element: PageElement, val index: Int)

/**
 * 全局 z 顺序。旧笔记所有 zIndex 都为 0 时，legacyRank + 原列表位置保持
 * “图片 → 笔迹 → 卡片”的历史视觉顺序；调层后 zIndex 成为主排序键。
 */
fun orderedElements(
    strokes: List<Stroke>,
    cards: List<AnnotationCard>,
    images: List<ImageElement>,
): List<PageElement> {
    val all = buildList {
        images.forEachIndexed { i, value -> add(IndexedElement(PageElement.Image(value), i)) }
        strokes.forEachIndexed { i, value -> add(IndexedElement(PageElement.Ink(value), i)) }
        cards.forEachIndexed { i, value -> add(IndexedElement(PageElement.Card(value), i)) }
    }
    return all.sortedWith(
        compareBy<IndexedElement> { it.element.zIndex }
            .thenBy { it.element.ref.kind.legacyRank }
            .thenBy { it.index }
    ).map { it.element }
}

fun PageInkState.orderedElements(): List<PageElement> = orderedElements(strokes, cards, images)

fun PageInkState.nextZIndex(): Long =
    (strokes.maxOfOrNull { it.zIndex } ?: 0L)
        .coerceAtLeast(cards.maxOfOrNull { it.zIndex } ?: 0L)
        .coerceAtLeast(images.maxOfOrNull { it.zIndex } ?: 0L) + 1L
