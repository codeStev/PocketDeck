package dev.pocketdeck.domain.meta

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.GameplayKey

/**
 * How many of the tournament decks play each card, from 0 to 1. It tells a card strong players keep choosing from one
 * that merely looks good on paper. Prints of a card count as one card.
 */
class CardPopularity private constructor(
    private val decks: List<Set<GameplayKey>>,
) {
    private val shareByCard: Map<GameplayKey, Double> =
        decks
            .flatten()
            .groupingBy { it }
            .eachCount()
            .mapValues { (_, count) -> count.toDouble() / decks.size }

    fun of(card: Card): Double = shareByCard[card.gameplayKey] ?: 0.0

    /**
     * The same measure among the decks that play [card]: cards that win together are played together, so a deck built
     * around [card] should follow them. With too few such decks to say anything, the overall measure stays.
     */
    fun around(card: Card): CardPopularity {
        val together = decks.filter { card.gameplayKey in it }
        return if (together.size >= MINIMUM_DECKS) CardPopularity(together) else this
    }

    companion object {
        private const val MINIMUM_DECKS = 5

        val NONE = CardPopularity(emptyList())

        fun of(
            snapshot: MetaSnapshot,
            catalog: CardCatalog,
        ): CardPopularity {
            val decks =
                snapshot.archetypes
                    .flatMap { it.decklists }
                    .map { list -> list.entries.mapNotNull { catalog[it.cardId]?.gameplayKey }.toSet() }
            return if (decks.isEmpty()) NONE else CardPopularity(decks)
        }
    }
}
