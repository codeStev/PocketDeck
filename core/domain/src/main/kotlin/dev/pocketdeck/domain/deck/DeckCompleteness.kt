package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.collection.Inventory

data class MissingCard(
    val card: Card,
    val missingCopies: Int,
)

data class DeckCompleteness(
    val totalCopies: Int,
    val missing: List<MissingCard>,
) {
    val missingCopies: Int = missing.sumOf { it.missingCopies }

    val isBuildable: Boolean get() = missing.isEmpty()

    /** Share of the deck's cards the player already owns, from 0.0 to 1.0. */
    val ratio: Double get() = if (totalCopies == 0) 0.0 else (totalCopies - missingCopies).toDouble() / totalCopies
}

class CompletenessCalculator {
    fun calculate(
        deck: Deck,
        inventory: Inventory,
    ): DeckCompleteness {
        val missing =
            deck.entries
                .groupBy { it.card.gameplayKey }
                .values
                .mapNotNull { entries -> missingOf(entries, inventory) }
        return DeckCompleteness(totalCopies = deck.size, missing = missing)
    }

    private fun missingOf(
        entries: List<DeckEntry>,
        inventory: Inventory,
    ): MissingCard? {
        val card = entries.first().card
        val shortfall = entries.sumOf { it.copies } - inventory.copiesOf(card)
        return MissingCard(card, shortfall).takeIf { shortfall > 0 }
    }
}
