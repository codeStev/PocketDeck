package dev.pocketdeck.application.suggest

import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.deck.CompletenessCalculator
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.deck.DeckEntry
import dev.pocketdeck.domain.deck.DeckSubstituter
import dev.pocketdeck.domain.deck.DeckSuggestion
import dev.pocketdeck.domain.deck.DeckValidator
import dev.pocketdeck.domain.deck.SuggestionOrigin
import dev.pocketdeck.domain.meta.Archetype
import dev.pocketdeck.domain.meta.MetaDecklist
import kotlin.math.pow

/**
 * Suggests what wins tournaments: for every popular archetype the played list the player is closest to owning.
 * A suggestion only counts as good if it can be played, so the completeness weighs in strongly.
 */
class MetaBasedSuggestionStrategy(
    private val completeness: CompletenessCalculator = CompletenessCalculator(),
    private val validator: DeckValidator = DeckValidator(),
    private val substituter: DeckSubstituter = DeckSubstituter(),
) : DeckSuggestionStrategy {
    override fun suggest(context: SuggestionContext): List<DeckSuggestion> =
        context.meta.archetypes
            .take(MAX_ARCHETYPES)
            .flatMap { archetype -> offersFor(archetype, context) }

    /** The list closest to what the player owns and, if it has holes, the same list with Pokémon they own instead. */
    private fun offersFor(
        archetype: Archetype,
        context: SuggestionContext,
    ): List<DeckSuggestion> {
        val best = bestVariant(archetype, context) ?: return emptyList()
        return listOfNotNull(best, withSubstitutes(archetype, best, context))
    }

    private fun withSubstitutes(
        archetype: Archetype,
        original: DeckSuggestion,
        context: SuggestionContext,
    ): DeckSuggestion? {
        if (original.completeness.isBuildable) return null
        val adapted = substituter.substitute(original.deck, context.inventory, context.catalog) ?: return null
        val suggestion = suggestion(archetype, adapted.deck, context)
        val swapped = adapted.substitutions.sumOf { it.copies }
        return suggestion
            .copy(score = suggestion.score * SUBSTITUTE_PENALTY.pow(swapped), substitutions = adapted.substitutions)
            .takeIf { it.completeness.missingCopies < original.completeness.missingCopies }
    }

    private fun bestVariant(
        archetype: Archetype,
        context: SuggestionContext,
    ): DeckSuggestion? =
        archetype.decklists
            .take(MAX_VARIANTS)
            .mapNotNull { list -> toDeck(list, context)?.let { deck -> list to deck } }
            .filter { (_, deck) -> validator.validate(deck).isEmpty() }
            .map { (_, deck) -> suggestion(archetype, deck, context) }
            .maxByOrNull { it.completeness.ratio }

    private fun suggestion(
        archetype: Archetype,
        deck: Deck,
        context: SuggestionContext,
    ): DeckSuggestion {
        val owned = completeness.calculate(deck, context.inventory)
        return DeckSuggestion(
            title = archetype.name.value,
            origin = SuggestionOrigin.META,
            deck = deck,
            completeness = owned,
            score = owned.ratio.pow(COMPLETENESS_WEIGHT) * archetype.winRate,
        )
    }

    /** A list with a card the catalog does not know cannot be shown or checked, so it is left out. */
    private fun toDeck(
        list: MetaDecklist,
        context: SuggestionContext,
    ): Deck? {
        val entries =
            list.entries.map { entry ->
                val card = context.catalog[entry.cardId] ?: return null
                DeckEntry(card, entry.copies)
            }
        val energy = list.energyTypes.ifEmpty { energyOf(entries) }
        return Deck(entries, energy)
    }

    private fun energyOf(entries: List<DeckEntry>): Set<EnergyType> =
        entries
            .map { it.card }
            .filterIsInstance<PokemonCard>()
            .map { it.energyType }
            .filter { it != EnergyType.COLORLESS }
            .toSet()
            .take(Deck.MAX_ENERGY_TYPES)
            .toSet()

    private companion object {
        const val MAX_ARCHETYPES = 12
        const val MAX_VARIANTS = 25
        const val COMPLETENESS_WEIGHT = 2.0

        /** A stand-in changes how the deck plays, so each swapped card costs a tenth of the score. */
        const val SUBSTITUTE_PENALTY = 0.9
    }
}
