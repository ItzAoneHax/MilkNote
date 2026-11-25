package com.milknote.app.ui.agent.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangeParserTest {

    // ---------------------------------------------------------------- 解析

    @Test
    fun `单页直接解析`() {
        assertEquals(listOf(3), PageRangeParser.parse("3", 10).getOrThrow())
        assertEquals(listOf(1), PageRangeParser.parse(" 1 ", 1).getOrThrow())
    }

    @Test
    fun `区间与逗号混合`() {
        assertEquals(
            listOf(1, 2, 3, 5, 7, 8, 9),
            PageRangeParser.parse("1-3, 5, 7-9", 10).getOrThrow(),
        )
    }

    @Test
    fun `en dash 与波浪号都当区间`() {
        assertEquals(listOf(2, 3, 4), PageRangeParser.parse("2–4", 10).getOrThrow())
        assertEquals(listOf(2, 3, 4), PageRangeParser.parse("2~4", 10).getOrThrow())
        assertEquals(listOf(2, 3, 4), PageRangeParser.parse("2～4", 10).getOrThrow())
        assertEquals(listOf(2, 3, 4), PageRangeParser.parse("2—4", 10).getOrThrow())
    }

    @Test
    fun `倒序区间自动归一`() {
        assertEquals(listOf(1, 2, 3), PageRangeParser.parse("3-1", 10).getOrThrow())
    }

    @Test
    fun `中文逗号分号顿号与空格都当分隔符`() {
        assertEquals(
            listOf(1, 3, 5, 7),
            PageRangeParser.parse("1，3；5、7", 10).getOrThrow(),
        )
        assertEquals(listOf(1, 2, 3), PageRangeParser.parse("1 2 3", 10).getOrThrow())
    }

    @Test
    fun `连字符两侧空格也认区间`() {
        assertEquals(listOf(1, 2, 3), PageRangeParser.parse("1 - 3", 10).getOrThrow())
    }

    @Test
    fun `结果按升序去重`() {
        assertEquals(listOf(1, 2, 3, 5), PageRangeParser.parse("  5,3,1,2,3 ", 10).getOrThrow())
    }

    @Test
    fun `空白输入返回空列表而不是错误`() {
        assertEquals(emptyList<Int>(), PageRangeParser.parse("", 10).getOrThrow())
        assertEquals(emptyList<Int>(), PageRangeParser.parse("   \n\t ", 10).getOrThrow())
    }

    @Test
    fun `超出范围报错`() {
        val failure = PageRangeParser.parse("11", 10).exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals("页码超出范围 (1-10)", failure?.message)

        assertEquals("页码超出范围 (1-10)", PageRangeParser.parse("0", 10).exceptionOrNull()?.message)
        assertEquals("页码超出范围 (1-10)", PageRangeParser.parse("1-11", 10).exceptionOrNull()?.message)
        assertEquals("页码超出范围 (1-99)", PageRangeParser.parse("100", 99).exceptionOrNull()?.message)
    }

    @Test
    fun `非数字报格式错误`() {
        val failure = PageRangeParser.parse("abc", 10).exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals("页码格式无效，示例: 1-3, 5", failure?.message)

        assertEquals("页码格式无效，示例: 1-3, 5", PageRangeParser.parse("1-2-3", 10).exceptionOrNull()?.message)
        assertEquals("页码格式无效，示例: 1-3, 5", PageRangeParser.parse("3a", 10).exceptionOrNull()?.message)
    }

    // ---------------------------------------------------------------- 格式化

    @Test
    fun `连续页码压缩成区间`() {
        assertEquals("1-3, 5, 7-8", PageRangeParser.format(listOf(1, 2, 3, 5, 7, 8)))
        assertEquals("1-3", PageRangeParser.format(listOf(3, 1, 2, 2)))
        assertEquals("2-4", PageRangeParser.format(listOf(2, 3, 4)))
    }

    @Test
    fun `单页与空集合`() {
        assertEquals("4", PageRangeParser.format(listOf(4)))
        assertEquals("", PageRangeParser.format(emptyList()))
    }

    @Test
    fun `中文页码标签`() {
        assertEquals("第 2 页", PageRangeParser.formatChineseLabel(listOf(2)))
        assertEquals("第 1-3, 5 页", PageRangeParser.formatChineseLabel(listOf(1, 2, 3, 5)))
        assertEquals("", PageRangeParser.formatChineseLabel(emptyList()))
    }

    @Test
    fun `parse 与 format 互为逆运算`() {
        val input = "1-3, 5, 7-9"
        val pages = PageRangeParser.parse(input, 20).getOrThrow()
        assertEquals(input, PageRangeParser.format(pages))
    }
}
