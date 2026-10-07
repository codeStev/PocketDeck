package dev.pocketdeck.application.savedeck

import dev.pocketdeck.domain.deck.SavedDeck
import dev.pocketdeck.domain.deck.SavedDeckContent
import dev.pocketdeck.domain.deck.SavedDeckId
import kotlinx.coroutines.flow.Flow

/**
 * Outbound port: the decks the player chose to keep. They belong to the active profile, like the collection, so
 * switching the account switches the list.
 */
interface SavedDeckRepository {
    /** Newest first. */
    fun observe(): Flow<List<SavedDeck>>

    suspend fun add(content: SavedDeckContent): SavedDeckId

    suspend fun rename(
        id: SavedDeckId,
        title: String,
    )

    suspend fun delete(id: SavedDeckId)
}
