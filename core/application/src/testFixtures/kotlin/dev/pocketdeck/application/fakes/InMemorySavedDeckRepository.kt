package dev.pocketdeck.application.fakes

import dev.pocketdeck.application.savedeck.SavedDeckRepository
import dev.pocketdeck.domain.deck.SavedDeck
import dev.pocketdeck.domain.deck.SavedDeckContent
import dev.pocketdeck.domain.deck.SavedDeckId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemorySavedDeckRepository : SavedDeckRepository {
    private val state = MutableStateFlow<List<SavedDeck>>(emptyList())
    private var nextId = 1L

    override fun observe(): Flow<List<SavedDeck>> = state

    override suspend fun add(content: SavedDeckContent): SavedDeckId {
        val id = SavedDeckId(nextId++)
        state.value = listOf(SavedDeck(id, content)) + state.value
        return id
    }

    override suspend fun rename(
        id: SavedDeckId,
        title: String,
    ) {
        state.value = state.value.map { if (it.id == id) it.copy(content = it.content.copy(title = title)) else it }
    }

    override suspend fun delete(id: SavedDeckId) {
        state.value = state.value.filterNot { it.id == id }
    }
}
