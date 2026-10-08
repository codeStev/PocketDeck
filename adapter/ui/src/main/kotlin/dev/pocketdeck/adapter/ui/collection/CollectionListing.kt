package dev.pocketdeck.adapter.ui.collection

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.collection.CardCollection

/** The filtered, display-ready view of the catalog joined with what the player owns. */
data class CollectionListing(
    val items: List<CardItem>,
    val uniqueOwned: Int,
    val totalOwned: Int,
    val catalogSize: Int,
) {
    companion object {
        fun build(
            catalog: CardCatalog,
            collection: CardCollection,
            query: String,
            ownedOnly: Boolean,
            language: Language,
        ): CollectionListing {
            val owned = catalog.cards.filter { collection.copiesOf(it.id) > 0 }
            val needle = query.trim()
            val items =
                catalog.cards
                    .asSequence()
                    .filter { !ownedOnly || collection.copiesOf(it.id) > 0 }
                    .filter { needle.isEmpty() || it.matches(needle) }
                    .map { it.toItem(collection.copiesOf(it.id), language) }
                    .toList()
            return CollectionListing(
                items = items,
                uniqueOwned = owned.size,
                totalOwned = owned.sumOf { collection.copiesOf(it.id) },
                catalogSize = catalog.size,
            )
        }

        /** Players search in whichever language they remember a card by, whatever the display language is. */
        private fun Card.matches(needle: String) =
            listOf(name.value, germanName.value, id.value).any { it.contains(needle, ignoreCase = true) }

        private fun Card.toItem(
            copies: Int,
            language: Language,
        ) = CardItem(
            id = id,
            name = nameIn(language).value,
            detail = detail(),
            artworkUrl = artwork?.value,
            copies = copies,
        )

        private fun Card.detail(): CardDetail =
            when (this) {
                is PokemonCard -> CardDetail.OfPokemon(energyType, stage, hp)
                is TrainerCard -> CardDetail.OfTrainer(kind)
            }
    }
}
