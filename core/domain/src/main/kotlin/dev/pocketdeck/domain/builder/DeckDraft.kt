package dev.pocketdeck.domain.builder

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.deck.DeckEntry

/** A deck in the making. It only accepts what the rules and the player's collection allow. */
internal class DeckDraft(
    private val owned: OwnedCards,
) {
    private val copies = linkedMapOf<Card, Int>()

    val size: Int get() = copies.values.sum()

    val pokemonCount: Int get() = copies.filterKeys { it is PokemonCard }.values.sum()

    fun copiesOf(card: Card): Int = copies[card] ?: 0

    private fun copiesNamed(name: CardName) = copies.filterKeys { it.name == name }.values.sum()

    /** How many more copies of [card] may still be added. */
    fun roomFor(card: Card): Int =
        minOf(
            owned.limitOf(card) - (copies[card] ?: 0),
            Deck.MAX_COPIES_PER_NAME - copiesNamed(card.name),
            Deck.SIZE - size,
        )

    /** Adds up to [wanted] copies and returns how many fit. */
    fun add(
        card: Card,
        wanted: Int,
    ): Int {
        val added = minOf(wanted, roomFor(card)).coerceAtLeast(0)
        if (added > 0) copies[card] = (copies[card] ?: 0) + added
        return added
    }

    fun toDeck(energy: Set<EnergyType>) = Deck(copies.map { (card, count) -> DeckEntry(card, count) }, energy)
}
