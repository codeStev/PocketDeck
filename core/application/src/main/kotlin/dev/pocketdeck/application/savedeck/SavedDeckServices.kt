package dev.pocketdeck.application.savedeck

import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.CompletenessCalculator
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.deck.DeckEntry
import dev.pocketdeck.domain.deck.DeckSuggestion
import dev.pocketdeck.domain.deck.SavedCard
import dev.pocketdeck.domain.deck.SavedDeck
import dev.pocketdeck.domain.deck.SavedDeckContent
import dev.pocketdeck.domain.deck.SavedDeckId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Clock

class SaveDeckService(
    private val saved: SavedDeckRepository,
    /** Time comes in from outside so tests can fix it. */
    private val clock: Clock,
) : SaveDeck {
    override suspend fun invoke(suggestion: DeckSuggestion): SavedDeckId {
        val content = contentOf(suggestion)
        val existing = saved.observe().first().firstOrNull { it.content.sameDeckAs(content) }
        return existing?.id ?: saved.add(content)
    }

    private fun contentOf(suggestion: DeckSuggestion) =
        SavedDeckContent(
            title = suggestion.title,
            origin = suggestion.origin,
            cards = suggestion.deck.entries.map { SavedCard(it.card.id, it.copies) },
            energyTypes = suggestion.deck.energyTypes,
            rationale = suggestion.rationale,
            savedAt = clock.instant(),
        )
}

class RenameSavedDeckService(
    private val saved: SavedDeckRepository,
) : RenameSavedDeck {
    override suspend fun invoke(
        id: SavedDeckId,
        title: String,
    ) {
        if (title.isNotBlank()) saved.rename(id, title.trim())
    }
}

class DeleteSavedDeckService(
    private val saved: SavedDeckRepository,
) : DeleteSavedDeck {
    override suspend fun invoke(id: SavedDeckId) = saved.delete(id)
}

class ObserveSavedDecksService(
    private val saved: SavedDeckRepository,
    private val catalogs: CardCatalogRepository,
    private val collections: CollectionRepository,
    private val completeness: CompletenessCalculator = CompletenessCalculator(),
) : ObserveSavedDecks {
    override fun invoke(): Flow<List<KeptDeck>> =
        combine(saved.observe(), catalogs.observe(), collections.observe()) { decks, catalog, collection ->
            val inventory = Inventory.of(collection, catalog)
            decks.map { KeptDeck(it.id, suggestionOf(it, catalog, inventory)) }
        }

    private fun suggestionOf(
        kept: SavedDeck,
        catalog: CardCatalog,
        inventory: Inventory,
    ): DeckSuggestion {
        // A card the catalog no longer knows is left out rather than hiding the whole deck.
        val entries = kept.content.cards.mapNotNull { saved -> catalog[saved.id]?.let { DeckEntry(it, saved.copies) } }
        val deck = Deck(entries, kept.content.energyTypes)
        val owned = completeness.calculate(deck, inventory)
        return DeckSuggestion(
            title = kept.content.title,
            origin = kept.content.origin,
            deck = deck,
            completeness = owned,
            score = owned.ratio,
            rationale = kept.content.rationale,
        )
    }
}
