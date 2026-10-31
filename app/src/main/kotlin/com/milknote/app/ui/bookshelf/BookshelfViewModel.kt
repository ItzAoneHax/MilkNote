package com.milknote.app.ui.bookshelf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.milknote.app.di.ServiceLocator
import com.milknote.notes.model.NotebookMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BookshelfViewModel : ViewModel() {

    private val repo = ServiceLocator.repository

    private val _notebooks = MutableStateFlow<List<NotebookMeta>>(emptyList())
    val notebooks: StateFlow<List<NotebookMeta>> = _notebooks

    /** 页数缓存(打开书架时刷新;删除笔记本后失效) */
    private val pageCounts = mutableMapOf<String, Int>()

    init { refresh() }

    fun refresh() {
        _notebooks.value = repo.listNotebooks()
        pageCounts.clear()
    }

    fun pageCountOf(id: String): Int =
        pageCounts.getOrPut(id) { repo.pageCount(id) }

    fun create(
        name: String,
        coverColorHex: String,
        paper: String,
        purpose: String,
        pageSize: String = "B5",
        orientation: String = "landscape",
    ): String {
        val meta = repo.create(name, coverColorHex, paper, purpose, pageSize, orientation)
        refresh()
        return meta.id
    }

    fun delete(id: String) {
        repo.delete(id)
        refresh()
    }
}
