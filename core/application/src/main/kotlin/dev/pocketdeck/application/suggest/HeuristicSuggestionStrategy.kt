package dev.pocketdeck.application.suggest

import dev.pocketdeck.domain.builder.BuiltDeck
import dev.pocketdeck.domain.builder.DeckFocus
import dev.pocketdeck.domain.builder.HeuristicDeckBuilder
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.CompletenessCalculator
import dev.pocketdeck.domain.deck.DeckSuggestion
import dev.pocketdeck.domain.deck.SuggestionOrigin

/** Turns what the builder made into a suggestion; the deck is made of owned cards, so it can always be played. */
internal fun toSuggestion(
    built: BuiltDeck,
    inventory: Inventory,
    completeness: CompletenessCalculator = CompletenessCalculator(),
): DeckSuggestion =
    DeckSuggestion(
        title = built.centrepiece.name.value,
        origin = SuggestionOrigin.HEURISTIC,
        deck = built.deck,
        completeness = completeness.calculate(built.deck, inventory),
        score = built.rating * HEURISTIC_WEIGHT,
        centrepiece = built.centrepiece,
    )

/** A deck made from the player's own cards ranks below a tournament deck they can build as printed. */
private const val HEURISTIC_WEIGHT = 0.5

/**
 * Suggests decks made from the player's own cards, whatever the tournament field looks like: one around each of the
 * strongest Pokémon of a few different kinds. It needs no meta data and works offline.
 */
class HeuristicSuggestionStrategy(
    private val builder: HeuristicDeckBuilder = HeuristicDeckBuilder(),
) : DeckSuggestionStrategy {
    override fun suggest(context: SuggestionContext): List<DeckSuggestion> =
        centresOfDifferentKinds(context.catalog, context.inventory)
            .mapNotNull { builder.build(context.catalog, context.inventory, DeckFocus(core = it)) }
            .map { toSuggestion(it, context.inventory) }

    private fun centresOfDifferentKinds(
        catalog: CardCatalog,
        inventory: Inventory,
    ) = builder
        .centrepieces(catalog, inventory)
        .distinctBy { it.energyType }
        .take(MAX_DECKS)

    private companion object {
        const val MAX_DECKS = 4
    }
}
