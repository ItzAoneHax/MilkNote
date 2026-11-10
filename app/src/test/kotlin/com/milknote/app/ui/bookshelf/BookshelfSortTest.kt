package com.milknote.app.ui.bookshelf

import com.milknote.notes.model.NotebookMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class BookshelfSortTest {

    private fun note(id: String, name: String, updatedAt: Long) = NotebookMeta(
        id = id,
        name = name,
        createdAt = 0L,
        updatedAt = updatedAt,
    )

    @Test
    fun `name sort ignores case and breaks ties by newest`() {
        val input = listOf(
            note("a", "zeta", 10L),
            note("b", "Alpha", 30L),
            note("c", "beta", 20L),
            note("d", "milk", 5L),
            note("e", "MILK", 9L),
        )

        val sorted = sortNotebooks(input, BookshelfSortMode.Name)

        assertEquals(listOf("b", "c", "e", "d", "a"), sorted.map { it.id })
    }

    @Test
    fun `updated sort is newest first and breaks ties by name`() {
        val input = listOf(
            note("a", "bravo", 100L),
            note("b", "charlie", 300L),
            note("c", "alpha", 200L),
            note("d", "delta", 200L),
        )

        val sorted = sortNotebooks(input, BookshelfSortMode.Updated)

        assertEquals(listOf("b", "c", "d", "a"), sorted.map { it.id })
    }

    @Test
    fun `sorting returns a new list and leaves the source order untouched`() {
        val input = listOf(
            note("a", "zeta", 10L),
            note("b", "Alpha", 30L),
        )

        val sorted = sortNotebooks(input, BookshelfSortMode.Name)

        assertNotSame(input, sorted)
        assertEquals(listOf("a", "b"), input.map { it.id })
        assertEquals(listOf("b", "a"), sorted.map { it.id })
    }

    @Test
    fun `last opened sorts by real timestamp with updated fallback`() {
        val input = listOf(
            note("a", "alpha", 10).copy(lastOpened = 100),
            note("b", "beta", 20).copy(lastOpened = 300),
            note("c", "charlie", 30).copy(lastOpened = 0),
        )
        assertEquals(listOf("b", "a", "c"), sortNotebooks(input, BookshelfSortMode.LastOpened).map { it.id })
    }

    @Test
    fun `tag sort uses tag color then name and falls back to cover`() {
        val input = listOf(
            note("a", "zeta", 10).copy(tagColor = "#FFFFFF"),
            note("b", "beta", 20).copy(tagColor = "#000000"),
            note("c", "alpha", 30).copy(tagColor = "#000000"),
        )
        assertEquals(listOf("c", "b", "a"), sortNotebooks(input, BookshelfSortMode.Tag).map { it.id })
    }

    @Test
    fun `duplicate ids still produce deterministic order`() {
        val input = listOf(
            note("same", "zeta", 10L),
            note("same", "alpha", 20L),
        )

        val sorted = sortNotebooks(input, BookshelfSortMode.Name)

        assertEquals(listOf("alpha", "zeta"), sorted.map { it.name })
    }
}
