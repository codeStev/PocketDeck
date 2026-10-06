package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType

data class DeckEntry(
    val card: Card,
    val copies: Int,
) {
    init {
        require(copies > 0) { "copies must be positive but was $copies" }
    }
}

/** A deck as built by a player. Whether it is legal is decided by [DeckValidator]. */
data class Deck(
    val entries: List<DeckEntry>,
    val energyTypes: Set<EnergyType>,
) {
    val size: Int = entries.sumOf { it.copies }

    fun copiesNamed(name: CardName): Int = entries.filter { it.card.name == name }.sumOf { it.copies }

    companion object {
        const val SIZE = 20
        const val MAX_COPIES_PER_NAME = 2
        const val MAX_ENERGY_TYPES = 3
    }
}
