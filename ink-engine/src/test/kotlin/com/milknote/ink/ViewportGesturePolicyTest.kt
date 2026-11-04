package com.milknote.ink

import com.milknote.ink.render.PageEdge
import com.milknote.ink.render.PageTurn
import com.milknote.ink.render.ZoomEndDebounce
import com.milknote.ink.render.edgeSwipeTurn
import com.milknote.ink.render.pageEdgeAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportGesturePolicyTest {
    @Test fun `缩放结束后只在时间窗内抑制平移`() {
        val debounce = ZoomEndDebounce(durationMs = 120)
        debounce.onScaleEnd(1_000)
        assertTrue(debounce.shouldSuppressPan(1_000))
        assertTrue(debounce.shouldSuppressPan(1_119))
        assertFalse(debounce.shouldSuppressPan(1_120))
    }

    @Test fun `新手势会清除缩放冷却`() {
        val debounce = ZoomEndDebounce()
        debounce.onScaleEnd(1_000)
        debounce.reset()
        assertFalse(debounce.shouldSuppressPan(1_010))
    }

    @Test fun `只有左右起手区会武装边缘手势`() {
        assertEquals(PageEdge.LEFT, pageEdgeAt(20f, 1_000f, 32f))
        assertEquals(PageEdge.RIGHT, pageEdgeAt(980f, 1_000f, 32f))
        assertNull(pageEdgeAt(200f, 1_000f, 32f))
        assertNull(pageEdgeAt(0f, 0f, 32f))
    }

    @Test fun `左缘右滑上一页右缘左滑下一页`() {
        assertEquals(
            PageTurn.PREVIOUS,
            edgeSwipeTurn(PageEdge.LEFT, 20f, 100f, 120f, 106f, 56f),
        )
        assertEquals(
            PageTurn.NEXT,
            edgeSwipeTurn(PageEdge.RIGHT, 980f, 100f, 880f, 106f, 56f),
        )
    }

    @Test fun `向外滑动反向滑动和纵向手势不翻页`() {
        assertNull(edgeSwipeTurn(PageEdge.LEFT, 20f, 100f, -80f, 100f, 56f))
        assertNull(edgeSwipeTurn(PageEdge.RIGHT, 980f, 100f, 1_080f, 100f, 56f))
        assertNull(edgeSwipeTurn(PageEdge.LEFT, 20f, 100f, 120f, 220f, 56f))
    }

    @Test fun `未达到位移阈值不翻页`() {
        assertNull(edgeSwipeTurn(PageEdge.LEFT, 20f, 100f, 75f, 100f, 56f))
        assertEquals(
            PageTurn.PREVIOUS,
            edgeSwipeTurn(PageEdge.LEFT, 20f, 100f, 76f, 100f, 56f),
        )
    }
}
