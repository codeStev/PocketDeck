package dev.pocketdeck.domain.builder

import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.deck.DeckValidator
import dev.pocketdeck.domain.deck.TrainerPlayability
import dev.pocketdeck.domain.meta.CardPopularity
import kotlin.math.pow

/**
 * Builds a deck out of nothing but the player's own cards, without any tournament data: the strongest evolution lines
 * the energy can pay for, then the trainers that keep a deck running (searching, drawing, healing), then whatever is
 * needed to reach twenty cards. Every deck is legal. The same collection always gives the same deck.
 */
class HeuristicDeckBuilder(
    private val validator: DeckValidator = DeckValidator(),
) {
    /** The best deck for [focus], or null if the collection is too small to make a legal one. */
    fun build(
        catalog: CardCatalog,
        inventory: Inventory,
        focus: DeckFocus = DeckFocus(),
        popularity: CardPopularity = CardPopularity.NONE,
    ): BuiltDeck? {
        val owned = OwnedCards(catalog, inventory)
        val lines = LineFinder.linesOf(owned)
        return energyOptions(focus, owned)
            .mapNotNull { energy ->
                buildFor(owned, lines, energy, Wish(focus, popularity, TrainerPlayability(catalog)))
            }.maxByOrNull { it.rating }
    }

    /** The Pokémon worth building a deck around, strongest first, each with the lines it needs. */
    fun centrepieces(
        catalog: CardCatalog,
        inventory: Inventory,
        popularity: CardPopularity = CardPopularity.NONE,
    ): List<PokemonCard> {
        val owned = OwnedCards(catalog, inventory)
        return LineFinder
            .linesOf(owned)
            .sortedWith(compareByDescending<PokemonLine> { bestRating(it, popularity) }.thenBy { it.last.name.value })
            .map { it.last }
            .distinctBy { it.name }
    }

    private fun bestRating(
        line: PokemonLine,
        popularity: CardPopularity,
    ) = SELECTABLE_ENERGY.maxOf { CardRatings.lineRating(line, setOf(it), popularity) }

    private fun energyOptions(
        focus: DeckFocus,
        owned: OwnedCards,
    ): List<Set<EnergyType>> {
        if (focus.energyTypes.isNotEmpty()) return listOf(focus.energyTypes)
        val coreEnergy = focus.core?.let { coreEnergy(it) }.orEmpty()
        if (coreEnergy.isNotEmpty()) return listOf(coreEnergy)
        val singles = SELECTABLE_ENERGY.map { setOf(it) }
        val pairs = SELECTABLE_ENERGY.flatMapIndexed { i, a -> SELECTABLE_ENERGY.drop(i + 1).map { b -> setOf(a, b) } }
        return singles + pairs
    }

    /** The energy the strongest attack of the core needs. */
    private fun coreEnergy(core: PokemonCard): Set<EnergyType> =
        core.attacks
            .maxByOrNull { CardRatings.strength(it) }
            ?.let { CardRatings.energyOf(it) }
            .orEmpty()

    private fun buildFor(
        owned: OwnedCards,
        lines: List<PokemonLine>,
        energy: Set<EnergyType>,
        wish: Wish,
    ): BuiltDeck? {
        val focus = wish.focus
        val playable = lines.filter { CardRatings.bestAttack(it.last, energy) != null }
        val seed = focus.core?.let { core -> playable.firstOrNull { it.last == core } ?: return null }
        val centre = seed ?: playable.maxByOrNull { CardRatings.lineRating(it, energy, wish.popularity) } ?: return null
        // What strong players put next to the centrepiece matters more than what is popular overall.
        val popularity = wish.popularity.around(centre.last)
        val ranked =
            playable.sortedWith(
                compareByDescending<PokemonLine> {
                    CardRatings.lineRating(
                        it,
                        energy,
                        popularity,
                    )
                }.thenBy { it.last.name.value },
            )
        val draft = DeckDraft(owned)
        val picked = (listOfNotNull(seed) + ranked.filter { it != seed }).filter { tryAdd(draft, it) }
        if (picked.isEmpty()) return null
        fillWithTrainers(draft, owned, popularity, wish.playability, energy)
        fillWithPokemon(draft, picked + ranked.filter { it !in picked })
        return draft
            .takeIf { it.size == Deck.SIZE }
            ?.toDeck(energy)
            ?.takeIf { validator.validate(it).isEmpty() }
            ?.let { BuiltDeck(it, rating(picked, energy, popularity), picked.first().last) }
    }

    /** Adds a whole line, or nothing: half an evolution line cannot be played. */
    private fun tryAdd(
        draft: DeckDraft,
        line: PokemonLine,
    ): Boolean {
        val fits = draft.pokemonCount + line.chain.size <= MAX_POKEMON && line.chain.all { draft.roomFor(it) >= 1 }
        if (!fits || draft.pokemonCount >= POKEMON_TARGET) return false
        line.chain.forEachIndexed { index, card ->
            val room = MAX_POKEMON - draft.pokemonCount - missingAfter(line, card)
            val behind = if (index == 0) COPIES_PER_LINE_CARD else draft.copiesOf(line.chain[index - 1])
            draft.add(card, minOf(COPIES_PER_LINE_CARD, behind, room))
        }
        return true
    }

    /** Slots to keep for the cards of the line that come after [card]. */
    private fun missingAfter(
        line: PokemonLine,
        card: PokemonCard,
    ) = line.chain.size - 1 - line.chain.indexOf(card)

    /** The good trainers in pairs first; if the deck still has room, a second copy of the others. */
    private fun fillWithTrainers(
        draft: DeckDraft,
        owned: OwnedCards,
        popularity: CardPopularity,
        playability: TrainerPlayability,
        energy: Set<EnergyType>,
    ) {
        // A trainer that talks about Water Pokémon or "Team Rocket" is a dead card in a deck without them.
        val pokemonOnly = draft.toDeck(energy)
        val ranked =
            owned.trainers.filter { playability.unmet(it, pokemonOnly).isEmpty() }.sortedWith(
                compareByDescending<TrainerCard> { CardRatings.trainerRating(it, popularity) }.thenBy { it.name.value },
            )
        ranked.forEach { draft.add(it, if (CardRatings.trainerRating(it, popularity) >= GOOD_TRAINER) 2 else 1) }
        ranked.forEach { draft.add(it, 1) }
    }

    /** Not enough trainers: more copies of Pokémon, those of the picked lines first, then of the other playable lines. */
    private fun fillWithPokemon(
        draft: DeckDraft,
        picked: List<PokemonLine>,
    ) {
        val cards =
            picked.flatMap { line ->
                line.chain.mapIndexed { index, card ->
                    card to
                        line.chain.getOrNull(index - 1)
                }
            }
        val candidates = cards.distinctBy { it.first }

        fun balanced(pair: Pair<PokemonCard, PokemonCard?>) =
            pair.second == null || draft.copiesOf(pair.second!!) > draft.copiesOf(pair.first)
        // An evolution without its predecessor in hand is dead weight, so only a collection too small for anything
        // else gets such copies.
        repeatWhileAdding(draft) { candidates.filter(::balanced).any { draft.add(it.first, 1) > 0 } }
        repeatWhileAdding(draft) { candidates.any { draft.add(it.first, 1) > 0 } }
    }

    private fun repeatWhileAdding(
        draft: DeckDraft,
        addOne: () -> Boolean,
    ) {
        var progressed = true
        while (draft.size < Deck.SIZE && progressed) progressed = addOne()
    }

    /** The mean of the three strongest lines, 0 to 1; a deck with a second energy type is harder to play. */
    private fun rating(
        picked: List<PokemonLine>,
        energy: Set<EnergyType>,
        popularity: CardPopularity,
    ): Double {
        val strongest =
            picked
                .map {
                    CardRatings.lineRating(
                        it,
                        energy,
                        popularity,
                    )
                }.sortedDescending()
                .take(LINES_THAT_COUNT)
        return (strongest.average() / MAXIMUM_RATING).coerceIn(0.0, 1.0) * EXTRA_TYPE_FACTOR.pow(energy.size - 1)
    }

    /** What the player asked for and what the tournaments show strong players choose. */
    private class Wish(
        val focus: DeckFocus,
        val popularity: CardPopularity,
        val playability: TrainerPlayability,
    )

    private companion object {
        const val POKEMON_TARGET = 11
        const val MAX_POKEMON = 14
        const val COPIES_PER_LINE_CARD = 2
        const val GOOD_TRAINER = 2.0
        const val LINES_THAT_COUNT = 3
        const val MAXIMUM_RATING = 2.0
        const val EXTRA_TYPE_FACTOR = 0.9
        val SELECTABLE_ENERGY = EnergyType.entries.filter { it != EnergyType.DRAGON && it != EnergyType.COLORLESS }
    }
}
