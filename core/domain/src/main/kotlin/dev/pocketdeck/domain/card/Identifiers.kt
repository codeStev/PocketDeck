package dev.pocketdeck.domain.card

/** Identifies one printed card, e.g. `A1-094`. Different prints of the same card have different ids. */
@JvmInline
value class CardId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "CardId must not be blank" }
    }
}

/** The printed name of a card. The deck rule "max two copies" applies per name. */
@JvmInline
value class CardName(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "CardName must not be blank" }
    }
}

/**
 * Cards sharing a gameplay key are functionally identical and therefore interchangeable in a deck,
 * regardless of rarity or artwork. Same-named cards with different effects get different keys.
 */
@JvmInline
value class GameplayKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "GameplayKey must not be blank" }
    }

    companion object {
        fun of(name: CardName) = GameplayKey(name.value)
    }
}

/** Where the card's picture can be loaded from. */
@JvmInline
value class ArtworkUrl(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ArtworkUrl must not be blank" }
    }
}
