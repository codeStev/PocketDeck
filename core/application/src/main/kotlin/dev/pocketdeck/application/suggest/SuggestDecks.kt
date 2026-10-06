package dev.pocketdeck.application.suggest

import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.application.meta.MetaSnapshotRepository
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.DeckSuggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Inbound port: the decks the player could play, best first, kept up to date as collection and field change. */
fun interface SuggestDecks {
    operator fun invoke(): Flow<List<DeckSuggestion>>
}

class SuggestDecksService(
    private val catalogs: CardCatalogRepository,
    private val collections: CollectionRepository,
    private val snapshots: MetaSnapshotRepository,
    private val strategies: List<DeckSuggestionStrategy>,
) : SuggestDecks {
    override fun invoke(): Flow<List<DeckSuggestion>> =
        combine(catalogs.observe(), collections.observe(), snapshots.observe()) { catalog, collection, meta ->
            val context = SuggestionContext(catalog, Inventory.of(collection, catalog), meta)
            strategies.flatMap { it.suggest(context) }.sortedByDescending { it.score }
        }
}
