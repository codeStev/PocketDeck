package dev.pocketdeck.application.suggest

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.domain.builder.DeckFocus
import dev.pocketdeck.domain.builder.HeuristicDeckBuilder
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.DeckSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface BuildDeckFailure {
    /** The collection has no legal deck to offer for this wish. */
    data object NotEnoughCards : BuildDeckFailure
}

/** Inbound port: build a deck from the player's own cards whenever they ask for one, with whatever [DeckFocus] they give. */
fun interface BuildDeck {
    suspend operator fun invoke(focus: DeckFocus): Outcome<DeckSuggestion, BuildDeckFailure>
}

/** Inbound port: the Pokémon the player could build a deck around, strongest first. */
fun interface ListDeckCentrepieces {
    suspend operator fun invoke(): List<PokemonCard>
}

class BuildDeckService(
    private val catalogs: CardCatalogRepository,
    private val collections: CollectionRepository,
    private val builder: HeuristicDeckBuilder = HeuristicDeckBuilder(),
) : BuildDeck {
    override suspend fun invoke(focus: DeckFocus): Outcome<DeckSuggestion, BuildDeckFailure> {
        val catalog = catalogs.current()
        val inventory = Inventory.of(collections.current(), catalog)
        val built = withContext(Dispatchers.Default) { builder.build(catalog, inventory, focus) }
        return built?.let { Outcome.Success(toSuggestion(it, inventory)) }
            ?: Outcome.Failure(BuildDeckFailure.NotEnoughCards)
    }
}

class ListDeckCentrepiecesService(
    private val catalogs: CardCatalogRepository,
    private val collections: CollectionRepository,
    private val builder: HeuristicDeckBuilder = HeuristicDeckBuilder(),
) : ListDeckCentrepieces {
    override suspend fun invoke(): List<PokemonCard> {
        val catalog = catalogs.current()
        val inventory = Inventory.of(collections.current(), catalog)
        return withContext(Dispatchers.Default) { builder.centrepieces(catalog, inventory) }
    }
}
