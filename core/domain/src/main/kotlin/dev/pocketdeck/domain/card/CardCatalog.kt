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

    /**
     * A catalog stored by an older version lacks details the app needs now (the game's card numbers, the attacks).
     * Every Pokémon has at least one attack, so one without any shows the catalog has to be loaded again. The same goes
     * for packs: every real card comes from somewhere, so a catalog where none has a pack was stored before they existed, and likewise for the rarity.
     */
    val lacksCardDetails: Boolean
        get() =
            cards.any { it.gameEntityId == null || (it is PokemonCard && it.attacks.isEmpty()) } ||
                (cards.isNotEmpty() && cards.none { it.packs.isNotEmpty() }) ||
                (cards.isNotEmpty() && cards.none { it.rarity != null })

    companion object {
        val EMPTY = CardCatalog(emptyList())
    }
}
