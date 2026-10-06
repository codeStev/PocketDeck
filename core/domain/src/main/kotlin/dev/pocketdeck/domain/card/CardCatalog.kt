package dev.pocketdeck.domain.card

/** All cards that exist in the game, looked up by id. */
class CardCatalog(
    val cards: List<Card>,
) {
    private val byId: Map<CardId, Card> = cards.associateBy { it.id }

    init {
        require(byId.size == cards.size) { "Card ids must be unique" }
    }

    val size: Int get() = cards.size

    operator fun get(id: CardId): Card? = byId[id]

    operator fun contains(id: CardId): Boolean = id in byId

    companion object {
        val EMPTY = CardCatalog(emptyList())
    }
}
