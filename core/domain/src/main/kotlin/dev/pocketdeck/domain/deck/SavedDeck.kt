package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.EnergyType
import java.time.Instant

@JvmInline
value class SavedDeckId(
    val value: Long,
)

/** One card of a saved deck, by print id: what the player saw is what is saved, even if a card is reprinted later. */
data class SavedCard(
    val id: CardId,
    val copies: Int,
)

/** What is kept of a deck the player chose to keep. Ownership is not saved: it is worked out again from the collection. */
data class SavedDeckContent(
    val title: String,
    val origin: SuggestionOrigin,
    val cards: List<SavedCard>,
    val energyTypes: Set<EnergyType>,
    val rationale: String?,
    val savedAt: Instant,
) {
    /** The same cards and energy, whatever the title: saving a deck twice keeps one copy. */
    fun sameDeckAs(other: SavedDeckContent) =
        cards.sortedBy { it.id.value } == other.cards.sortedBy { it.id.value } && energyTypes == other.energyTypes
}

data class SavedDeck(
    val id: SavedDeckId,
    val content: SavedDeckContent,
)
