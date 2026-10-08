package dev.pocketdeck.adapter.ui.collection

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Rarity
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.card.TrainerKind
import dev.pocketdeck.domain.collection.CardCollection

/** The filtered, sorted, display-ready view of the catalog joined with what the player owns. */
data class CollectionListing(
    val items: List<CardItem>,
    val uniqueOwned: Int,
    val totalOwned: Int,
    val catalogSize: Int,
    val options: FilterOptions,
) {
    companion object {
        fun build(
            catalog: CardCatalog,
            collection: CardCollection,
            filter: CollectionFilter,
            language: Language,
        ): CollectionListing {
            val owned = catalog.cards.filter { collection.copiesOf(it.id) > 0 }
            val needle = filter.query.trim()
            val shown =
                catalog.cards.filter {
                    (!filter.ownedOnly || collection.copiesOf(it.id) > 0) &&
                        (needle.isEmpty() || it.matches(needle)) &&
                        filter.allows(it)
                }
            return CollectionListing(
                items =
                    shown
                        .sortedBy(
                            filter.sort,
                            collection,
                            language,
                        ).map { it.toItem(collection.copiesOf(it.id), language) },
                uniqueOwned = owned.size,
                totalOwned = owned.sumOf { collection.copiesOf(it.id) },
                catalogSize = catalog.size,
                options = optionsOf(catalog),
            )
        }

        /** Every sort is stable, so cards that tie stay in catalog order. */
        private fun List<Card>.sortedBy(
            sort: CollectionSort,
            collection: CardCollection,
            language: Language,
        ): List<Card> =
            when (sort) {
                CollectionSort.NUMBER -> this

                CollectionSort.NAME -> sortedBy { it.nameIn(language).value.lowercase() }

                CollectionSort.COPIES -> sortedByDescending { collection.copiesOf(it.id) }

                // Cards without a known rarity come last.
                CollectionSort.RARITY -> sortedByDescending { it.rarity?.ordinal ?: -1 }
            }

        private fun optionsOf(catalog: CardCatalog) =
            FilterOptions(
                sets = catalog.cards.map { it.set }.distinct(),
                rarities = Rarity.entries.filter { rarity -> catalog.cards.any { it.rarity == rarity } },
                types = typesIn(catalog),
            )

        private fun typesIn(catalog: CardCatalog): List<CardType> {
            val energies =
                EnergyType.entries.filter { energy ->
                    catalog.cards.any {
                        it is PokemonCard &&
                            it.energyType == energy
                    }
                }
            val kinds =
                TrainerKind.entries.filter { kind ->
                    catalog.cards.any { it is TrainerCard && it.kind == kind }
                }
            return energies.map { CardType.OfEnergy(it) } + kinds.map { CardType.OfTrainer(it) }
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
