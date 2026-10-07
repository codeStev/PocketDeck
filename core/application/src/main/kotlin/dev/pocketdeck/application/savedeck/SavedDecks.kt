package dev.pocketdeck.application.savedeck

import dev.pocketdeck.domain.deck.DeckSuggestion
import dev.pocketdeck.domain.deck.SavedDeckId
import kotlinx.coroutines.flow.Flow

/** A kept deck as the player sees it: the saved cards, judged against the collection as it is now. */
data class KeptDeck(
    val id: SavedDeckId,
    val suggestion: DeckSuggestion,
)

/** Inbound port: the kept decks of the active account, kept up to date as the collection changes. */
fun interface ObserveSavedDecks {
    operator fun invoke(): Flow<List<KeptDeck>>
}

/** Inbound port: keep a deck. Keeping the same cards again does not add a second entry. */
fun interface SaveDeck {
    suspend operator fun invoke(suggestion: DeckSuggestion): SavedDeckId
}

/** Inbound port: give a kept deck another title; a blank title is ignored. */
fun interface RenameSavedDeck {
    suspend operator fun invoke(
        id: SavedDeckId,
        title: String,
    )
}

/** Inbound port: stop keeping a deck. */
fun interface DeleteSavedDeck {
    suspend operator fun invoke(id: SavedDeckId)
}
