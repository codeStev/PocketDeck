package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.collection.Inventory
import kotlin.math.abs

/** [copies] of [original] are played as [replacement] instead, because the player does not own [original]. */
data class Substitution(
    val original: Card,
    val replacement: Card,
    val copies: Int,
)

data class SubstitutedDeck(
    val deck: Deck,
    val substitutions: List<Substitution>,
)

/**
 * Fills the holes of a deck with Pokémon the player owns. Only Pokémon are replaced: what a trainer does cannot be
 * judged from its name or kind, and a wrong guess there changes how the deck plays. A Pokémon is a stand-in when it
 * has the same energy type, stage, evolution predecessor and ex status, and the deck stays legal afterwards.
 */
class DeckSubstituter(
    private val completeness: CompletenessCalculator = CompletenessCalculator(),
    private val validator: DeckValidator = DeckValidator(),
) {
    /** The deck with the missing Pokémon replaced, or null if nothing could be replaced or the result is illegal. */
    fun substitute(
        deck: Deck,
        inventory: Inventory,
        catalog: CardCatalog,
    ): SubstitutedDeck? {
        val holes = completeness.calculate(deck, inventory).missing.filter { it.card is PokemonCard }
        if (holes.isEmpty()) return null
        val spare = spareCopies(deck, inventory, catalog).toMutableMap()
        var entries = deck.entries
        val substitutions = mutableListOf<Substitution>()
        for (hole in holes) {
            repeat(hole.missingCopies) {
                val stand = standIn(hole.card as PokemonCard, entries, spare, catalog) ?: return@repeat
                spare[stand.gameplayKey] = spare.getValue(stand.gameplayKey) - 1
                entries = swap(entries, hole.card, stand)
                substitutions += Substitution(hole.card, stand, 1)
            }
        }
        if (substitutions.isEmpty()) return null
        val result = Deck(entries, deck.energyTypes)
        return SubstitutedDeck(result, merge(substitutions)).takeIf { validator.validate(result).isEmpty() }
    }

    /** What the player owns beyond what the deck already uses; replacing must not take from another card of the deck. */
    private fun spareCopies(
        deck: Deck,
        inventory: Inventory,
        catalog: CardCatalog,
    ): Map<GameplayKey, Int> {
        val used =
            deck.entries.groupBy { it.card.gameplayKey }.mapValues { (_, entries) ->
                entries.sumOf { it.copies }
            }
        return catalog.cards
            .filterIsInstance<PokemonCard>()
            .associate { it.gameplayKey to maxOf(0, inventory.copiesOf(it) - (used[it.gameplayKey] ?: 0)) }
    }

    private fun standIn(
        hole: PokemonCard,
        entries: List<DeckEntry>,
        spare: Map<GameplayKey, Int>,
        catalog: CardCatalog,
    ): PokemonCard? =
        catalog.cards
            .asSequence()
            .filterIsInstance<PokemonCard>()
            .filter { it.gameplayKey != hole.gameplayKey && (spare[it.gameplayKey] ?: 0) > 0 }
            .filter { it.energyType == hole.energyType && it.stage == hole.stage && it.evolvesFrom == hole.evolvesFrom }
            .filter { it.isEx == hole.isEx }
            .filter { candidate ->
                entries.filter { it.card.name == candidate.name }.sumOf { it.copies } <
                    Deck.MAX_COPIES_PER_NAME
            }.minWithOrNull(
                compareBy({ abs(it.hp - hole.hp) }, { abs(it.retreatCost - hole.retreatCost) }, { it.name.value }),
            )

    /** One copy of [hole] makes room for one copy of [stand]. */
    private fun swap(
        entries: List<DeckEntry>,
        hole: Card,
        stand: PokemonCard,
    ): List<DeckEntry> {
        val holeEntry = entries.first { it.card.gameplayKey == hole.gameplayKey }
        val without =
            entries.flatMap { entry ->
                when {
                    entry !== holeEntry -> listOf(entry)
                    entry.copies > 1 -> listOf(entry.copy(copies = entry.copies - 1))
                    else -> emptyList()
                }
            }
        val existing = without.firstOrNull { it.card == stand }
        return if (existing == null) {
            without + DeckEntry(stand, 1)
        } else {
            without.map { if (it === existing) it.copy(copies = it.copies + 1) else it }
        }
    }

    private fun merge(single: List<Substitution>): List<Substitution> =
        single
            .groupBy { it.original to it.replacement }
            .map { (pair, group) -> Substitution(pair.first, pair.second, group.sumOf { it.copies }) }
}
