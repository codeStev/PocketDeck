package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Card
import kotlin.random.Random

/** The deck (top at index 0) and the hand: the ways cards move between them. */
internal class CardFlow(
    cards: List<Card>,
    private val random: Random,
    private val onTaken: (Card) -> Unit,
) {
    private val library = cards.toMutableList()
    val hand = mutableListOf<Card>()

    val isLibraryEmpty: Boolean get() = library.isEmpty()

    fun libraryHas(filter: CardFilter): Boolean = library.any { filter.matches(it) }

    /** Deals five cards, again and again until the hand has a card satisfying [needed] (the game forces a Basic in). */
    fun dealOpening(needed: (Card) -> Boolean) {
        do {
            library.addAll(hand)
            hand.clear()
            library.shuffle(random)
            hand += library.take(OPENING_HAND_SIZE)
            repeat(OPENING_HAND_SIZE) { library.removeAt(0) }
        } while (hand.none(needed))
    }

    /** Takes a random card matching [filter] out of the deck without putting it in the hand. */
    fun pull(filter: CardFilter): Card? =
        library.filter { filter.matches(it) }.randomOrNull(random)?.also { library.remove(it) }

    fun draw(count: Int) {
        repeat(count.coerceAtMost(library.size)) { take(library.removeAt(0)) }
    }

    fun search(
        count: Int,
        filter: CardFilter,
    ) {
        repeat(count) {
            val found = library.filter { filter.matches(it) }.randomOrNull(random) ?: return
            library.remove(found)
            take(found)
        }
    }

    fun lookAtTop(
        count: Int,
        filter: CardFilter,
    ) {
        val top = List(count.coerceAtMost(library.size)) { library.removeAt(0) }
        val (wanted, rest) = top.partition { filter.matches(it) }
        wanted.forEach(::take)
        library.addAll(rest)
        library.shuffle(random)
    }

    fun shuffleHandAndDraw(redrawCount: Int?) {
        val count = redrawCount ?: hand.size
        library.addAll(hand)
        hand.clear()
        library.shuffle(random)
        draw(count)
    }

    private fun take(card: Card) {
        hand += card
        onTaken(card)
    }

    private companion object {
        const val OPENING_HAND_SIZE = 5
    }
}
