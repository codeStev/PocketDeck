package dev.pocketdeck.domain.collection

import dev.pocketdeck.domain.card.CardId

/** The copies of each printed card the player owns. Immutable; cards with zero copies are not stored. */
class CardCollection private constructor(
    val copiesByCard: Map<CardId, Int>,
) {
    fun copiesOf(id: CardId): Int = copiesByCard[id] ?: 0

    fun withCopies(
        id: CardId,
        copies: Int,
    ): CardCollection {
        require(copies >= 0) { "copies must not be negative but was $copies" }
        val capped = copies.coerceAtMost(MAX_COPIES_PER_CARD)
        val updated = if (capped == 0) copiesByCard - id else copiesByCard + (id to capped)
        return CardCollection(updated)
    }

    fun adjusted(
        id: CardId,
        delta: Int,
    ): CardCollection = withCopies(id, (copiesOf(id) + delta).coerceAtLeast(0))

    override fun equals(other: Any?) = other is CardCollection && other.copiesByCard == copiesByCard

    override fun hashCode() = copiesByCard.hashCode()

    companion object {
        const val MAX_COPIES_PER_CARD = 99

        val EMPTY = CardCollection(emptyMap())
    }
}
