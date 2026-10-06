package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.EffectClassifier
import dev.pocketdeck.domain.card.EffectTag
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.collection.Inventory
import kotlin.math.abs

/** [copies] of [original] are played as [replacement] instead, because the player does not own [original]. */
data class Substitution(
    val original: Card,
    val replacement: Card,
    val copies: Int,
    /** What both cards do alike; shown to the player so they can judge the swap. */
    val sharedEffects: Set<EffectTag> = emptySet(),
)

data class SubstitutedDeck(
    val deck: Deck,
    val substitutions: List<Substitution>,
)

/**
 * Fills the holes of a deck with cards the player owns that do the same job. What a card does is read from its
 * effect text ([EffectClassifier]): a stand-in has to bring the effects of the card it replaces, and the effects the
 * rest of the deck builds on count most, so a deck that throws energy away and gets it back keeps that playstyle.
 *
 * A Pokémon is a stand-in when it has the same stage, evolution predecessor and ex status, can pay for an attack with
 * the energy the deck produces, and carries enough of the original's effects. A trainer needs the same kind and exactly
 * the same effects. Whatever cannot be matched stays missing, and the deck must still be legal afterwards.
 */
class DeckSubstituter(
    private val completeness: CompletenessCalculator = CompletenessCalculator(),
    private val validator: DeckValidator = DeckValidator(),
) {
    /** The deck with the missing cards replaced, or null if nothing could be replaced or the result is illegal. */
    fun substitute(
        deck: Deck,
        inventory: Inventory,
        catalog: CardCatalog,
    ): SubstitutedDeck? {
        val holes = completeness.calculate(deck, inventory).missing
        if (holes.isEmpty()) return null
        val spare = spareCopies(deck, inventory, catalog).toMutableMap()
        var entries = deck.entries
        val playability = TrainerPlayability(catalog)
        val substitutions = mutableListOf<Substitution>()
        for (hole in holes) {
            repeat(hole.missingCopies) {
                val stand = standIn(hole.card, Context(deck, entries, playability), spare, catalog) ?: return@repeat
                spare[stand.gameplayKey] = spare.getValue(stand.gameplayKey) - 1
                entries = swap(entries, hole.card, stand)
                substitutions += Substitution(hole.card, stand, 1, sharedEffectsOf(hole.card, stand))
            }
        }
        if (substitutions.isEmpty()) return null
        val result = Deck(entries, deck.energyTypes)
        return SubstitutedDeck(result, merge(substitutions)).takeIf { validator.validate(result).isEmpty() }
    }

    private class Context(
        val deck: Deck,
        val entries: List<DeckEntry>,
        val playability: TrainerPlayability,
    )

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
        return catalog.cards.associate {
            it.gameplayKey to maxOf(0, inventory.copiesOf(it) - (used[it.gameplayKey] ?: 0))
        }
    }

    private fun standIn(
        hole: Card,
        context: Context,
        spare: Map<GameplayKey, Int>,
        catalog: CardCatalog,
    ): Card? {
        val themes = themesOf(context.entries, excluding = hole)
        return catalog.cards
            .asSequence()
            .filter { it.gameplayKey != hole.gameplayKey && (spare[it.gameplayKey] ?: 0) > 0 }
            .filter { candidate ->
                context.entries.filter { it.card.name == candidate.name }.sumOf { it.copies } <
                    Deck.MAX_COPIES_PER_NAME
            }.mapNotNull { candidate ->
                similarity(hole, candidate, context, themes)?.let { candidate to it }
            }.minWithOrNull(
                compareByDescending<Pair<Card, Double>> {
                    it.second
                }.thenBy { closeness(hole, it.first) }.thenBy { it.first.name.value },
            )?.first
    }

    /** How well [candidate] does what [hole] does, from 0 to 1, or null if it cannot take its place at all. */
    private fun similarity(
        hole: Card,
        candidate: Card,
        context: Context,
        themes: Set<EffectTag>,
    ): Double? =
        when {
            hole is PokemonCard && candidate is PokemonCard -> pokemonSimilarity(hole, candidate, context.deck, themes)
            hole is TrainerCard && candidate is TrainerCard -> trainerSimilarity(hole, candidate, context)
            else -> null
        }

    private fun pokemonSimilarity(
        hole: PokemonCard,
        candidate: PokemonCard,
        deck: Deck,
        themes: Set<EffectTag>,
    ): Double? {
        val sameRole =
            candidate.stage == hole.stage && candidate.evolvesFrom == hole.evolvesFrom && candidate.isEx == hole.isEx
        if (!sameRole || !canPay(candidate, hole, deck.energyTypes)) return null
        val wanted = EffectClassifier.tagsOf(hole)
        val offered = EffectClassifier.tagsOf(candidate)
        if (wanted.isEmpty()) return if (offered.isEmpty()) 1.0 else NO_EFFECT_MISMATCH
        val total = wanted.sumOf { weight(it, themes) }
        val shared = wanted.intersect(offered).sumOf { weight(it, themes) }
        return (shared / total).takeIf { it >= MINIMUM_SIMILARITY }
    }

    private fun trainerSimilarity(
        hole: TrainerCard,
        candidate: TrainerCard,
        context: Context,
    ): Double? {
        val wanted = EffectClassifier.tagsOf(hole)
        val same = candidate.kind == hole.kind && wanted.isNotEmpty() && wanted == EffectClassifier.tagsOf(candidate)
        // A stand-in that does nothing in this deck (it needs Water Pokémon, say) is no stand-in.
        return if (same && context.playability.unmet(candidate, context.deck).isEmpty()) 1.0 else null
    }

    /** The effects the rest of the deck is built around: its own, and the ones that complement them. */
    private fun themesOf(
        entries: List<DeckEntry>,
        excluding: Card,
    ): Set<EffectTag> {
        val own = entries.filter { it.card != excluding }.flatMap { EffectClassifier.tagsOf(it.card) }.toSet()
        return own + own.flatMap { SYNERGIES[it].orEmpty() }
    }

    private fun weight(
        tag: EffectTag,
        themes: Set<EffectTag>,
    ) = if (tag in themes) THEME_WEIGHT else 1.0

    /** A Pokémon with no attack data can only be judged by its type; otherwise one attack must be payable. */
    private fun canPay(
        candidate: PokemonCard,
        hole: PokemonCard,
        energy: Set<EnergyType>,
    ): Boolean {
        if (candidate.attacks.isEmpty()) return candidate.energyType == hole.energyType
        val payable = energy.map { it.symbol }.toSet() + EnergyType.COLORLESS.symbol
        return candidate.attacks.any { attack -> attack.cost.all { it in payable } }
    }

    private fun closeness(
        hole: Card,
        candidate: Card,
    ): Int =
        if (hole is PokemonCard && candidate is PokemonCard) {
            abs(candidate.hp - hole.hp) + abs(candidate.retreatCost - hole.retreatCost)
        } else {
            0
        }

    /** One copy of [hole] makes room for one copy of [stand]. */
    private fun swap(
        entries: List<DeckEntry>,
        hole: Card,
        stand: Card,
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

    private fun sharedEffectsOf(
        hole: Card,
        stand: Card,
    ) = EffectClassifier.tagsOf(hole).intersect(EffectClassifier.tagsOf(stand))

    private fun merge(single: List<Substitution>): List<Substitution> =
        single
            .groupBy { it.original to it.replacement }
            .map { (pair, group) -> group.first().copy(copies = group.sumOf { it.copies }) }

    private companion object {
        const val MINIMUM_SIMILARITY = 0.5
        const val THEME_WEIGHT = 3.0

        /** Better than nothing, worse than a card that does what the original does. */
        const val NO_EFFECT_MISMATCH = 0.6

        /** Effects that only work together: getting energy back needs it to have been thrown away, and the reverse. */
        val SYNERGIES: Map<EffectTag, Set<EffectTag>> =
            mapOf(
                EffectTag.DISCARD_OWN_ENERGY to setOf(EffectTag.ATTACH_ENERGY_FROM_DISCARD, EffectTag.MOVE_ENERGY),
                EffectTag.ATTACH_ENERGY_FROM_DISCARD to setOf(EffectTag.DISCARD_OWN_ENERGY),
            )
    }
}
