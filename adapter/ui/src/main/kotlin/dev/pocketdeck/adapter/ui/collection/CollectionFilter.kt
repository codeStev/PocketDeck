package dev.pocketdeck.adapter.ui.collection

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Rarity
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.card.TrainerKind

/**
 * What to sort by. Ascending means: card number 1 upwards, names A to Z, fewest copies first, most common first.
 * [startsDescending] is the direction a player usually wants when they pick it (the most copies or the rarest first).
 */
enum class CollectionSort(
    val startsDescending: Boolean,
) {
    /** The catalog's own order: by set, then card number. */
    NUMBER(false),
    NAME(false),
    COPIES(true),
    RARITY(true),
}

/** A kind of card to show: the Pokémon of one energy type, or one kind of trainer. */
sealed interface CardType {
    data class OfEnergy(
        val energy: EnergyType,
    ) : CardType

    data class OfTrainer(
        val kind: TrainerKind,
    ) : CardType

    fun matches(card: Card): Boolean =
        when (this) {
            is OfEnergy -> card is PokemonCard && card.energyType == energy
            is OfTrainer -> card is TrainerCard && card.kind == kind
        }
}

/**
 * What the player asked to see. Within one group (sets, rarities, types) any choice matches; the groups all have to match.
 * An empty group means "no restriction".
 */
data class CollectionFilter(
    val query: String = "",
    val ownedOnly: Boolean = false,
    val sets: Set<String> = emptySet(),
    val rarities: Set<Rarity> = emptySet(),
    val types: Set<CardType> = emptySet(),
    val sort: CollectionSort = CollectionSort.NUMBER,
    val descending: Boolean = false,
) {
    /** How many choices the filter panel holds, for the badge on its button. */
    val chosenCount: Int
        get() = sets.size + rarities.size + types.size + (if (sort == CollectionSort.NUMBER && !descending) 0 else 1)

    /** The panel's "Clear": everything it sets, but not the search text or the owned-only switch beside it. */
    fun withoutPanelChoices() =
        copy(
            sets = emptySet(),
            rarities = emptySet(),
            types = emptySet(),
            sort = CollectionSort.NUMBER,
            descending = false,
        )

    /** Picks a sort; another sort starts in the direction that usually suits it, the same one keeps the player's choice. */
    fun sortedBy(newSort: CollectionSort) =
        if (newSort == sort) this else copy(sort = newSort, descending = newSort.startsDescending)

    fun allows(card: Card): Boolean =
        (sets.isEmpty() || card.set in sets) &&
            (rarities.isEmpty() || card.rarity in rarities) &&
            (types.isEmpty() || types.any { it.matches(card) })
}

/** What the catalog offers to choose from, so the panel shows no set or rarity that has no card. */
data class FilterOptions(
    val sets: List<String> = emptyList(),
    val rarities: List<Rarity> = emptyList(),
    val types: List<CardType> = emptyList(),
)
