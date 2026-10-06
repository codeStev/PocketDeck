package dev.pocketdeck.application.suggest

import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.DeckSuggestion
import dev.pocketdeck.domain.meta.MetaSnapshot

/** Everything a strategy may look at to come up with decks. */
data class SuggestionContext(
    val catalog: CardCatalog,
    val inventory: Inventory,
    val meta: MetaSnapshot,
)

/** One way of coming up with decks for the player. New ways (e.g. an AI) plug in by implementing this port. */
fun interface DeckSuggestionStrategy {
    fun suggest(context: SuggestionContext): List<DeckSuggestion>
}
