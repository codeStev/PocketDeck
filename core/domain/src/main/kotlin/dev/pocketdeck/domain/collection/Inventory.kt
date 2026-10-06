package dev.pocketdeck.domain.collection

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.GameplayKey

/** What the player can actually play: owned copies summed over interchangeable prints. */
class Inventory private constructor(
    private val copiesByGameplayKey: Map<GameplayKey, Int>,
) {
    fun copiesOf(card: Card): Int = copiesByGameplayKey[card.gameplayKey] ?: 0

    companion object {
        fun of(
            collection: CardCollection,
            catalog: CardCatalog,
        ): Inventory {
            val totals = mutableMapOf<GameplayKey, Int>()
            collection.copiesByCard.forEach { (id, copies) ->
                val card = catalog[id] ?: return@forEach
                totals.merge(card.gameplayKey, copies, Int::plus)
            }
            return Inventory(totals)
        }
    }
}
