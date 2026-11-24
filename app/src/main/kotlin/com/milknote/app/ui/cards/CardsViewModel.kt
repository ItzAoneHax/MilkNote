package com.milknote.app.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milknote.app.di.ServiceLocator
import com.milknote.notes.model.DEFAULT_CARD_COLOR
import com.milknote.notes.model.NoteCard
import com.milknote.notes.storage.CardTagFilter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CardsUiState(
    val cards: List<NoteCard> = emptyList(),
    val allTags: List<String> = emptyList(),
    val query: String = "",
    val tag: CardTagFilter = CardTagFilter.All,
    val color: String? = null,
    val sourceLabels: Map<String, String> = emptyMap(),
)

class CardsViewModel : ViewModel() {
    private val repo = ServiceLocator.cardRepository
    private val _state = MutableStateFlow(CardsUiState())
    val state: StateFlow<CardsUiState> = _state
    private var refreshGeneration = 0L

    init { refresh() }

    fun search(query: String) { _state.value = _state.value.copy(query = query); refresh() }
    fun filterTag(tag: CardTagFilter) { _state.value = _state.value.copy(tag = tag); refresh() }
    fun filterColor(color: String?) { _state.value = _state.value.copy(color = color); refresh() }

    fun save(existing: NoteCard?, title: String, body: String, tags: List<String>, color: String) {
        val now = System.currentTimeMillis()
        val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        val card = NoteCard(
            id = existing?.id ?: UUID.randomUUID().toString().substring(0, 12),
            title = title.trim(),
            body = body.trim(),
            imageSrc = existing?.imageSrc,
            tags = cleanTags,
            color = color.ifBlank { DEFAULT_CARD_COLOR },
            sourceNotebookId = existing?.sourceNotebookId,
            sourcePageId = existing?.sourcePageId,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        viewModelScope.launch(Dispatchers.IO) {
            repo.upsert(card)
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(id)
            refresh()
        }
    }

    fun refresh() {
        val generation = ++refreshGeneration
        val current = _state.value
        viewModelScope.launch(Dispatchers.IO) {
            val all = repo.listCards()
            val filtered = repo.filterCards(all, current.query, current.tag, current.color)
            val tags = all.flatMap { it.tags }.map { it.trim() }.filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }.sorted()
            val notebookRepo = ServiceLocator.repository
            val sourceIds = all.mapNotNull { it.sourceNotebookId }.distinct()
            val notebooks = sourceIds.associateWith { notebookRepo.getNotebook(it) }
            val pageIds = sourceIds.associateWith { id ->
                notebooks[id]?.let { notebookRepo.listPages(id).map { page -> page.pageId } }.orEmpty()
            }
            val sourceLabels = all.mapNotNull { card ->
                val sourceId = card.sourceNotebookId ?: return@mapNotNull null
                card.id to cardSourceLabel(notebooks[sourceId]?.name, pageIds[sourceId].orEmpty(), card.sourcePageId)
            }.toMap()
            withContext(Dispatchers.Main) {
                if (generation == refreshGeneration) {
                    _state.value = _state.value.copy(cards = filtered, allTags = tags, sourceLabels = sourceLabels)
                }
            }
        }
    }
}
