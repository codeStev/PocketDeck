package dev.pocketdeck.domain.meta

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.CompletenessCalculator
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.deck.DeckCompleteness
import dev.pocketdeck.domain.deck.DeckEntry

/** A card the player does not own enough of, and what getting it would do for them. */
data class ChaseEntry(
    /** A print of the card as the tournament lists name it. */
    val card: Card,
    /** How much the card is worth chasing: higher is better. Only comparable within one ranking. */
    val score: Double,
    /** Near-complete tournament decks this card alone would finish. */
    val decksFinished: Int,
    /** Near-complete tournament decks that play it. */
    val decksHelped: Int,
    /** Where any print of the card can be obtained. */
    val packs: List<String>,
)

/** A pack, or other source, with the cards worth chasing that it holds. */
data class ChasePack(
    val name: String,
    val score: Double,
    val cards: List<ChaseEntry>,
)

data class ChaseRanking(
    /** Best first. */
    val cards: List<ChaseEntry>,
    /** Best first. */
    val packs: List<ChasePack>,
) {
    companion object {
        val EMPTY = ChaseRanking(emptyList(), emptyList())
    }
}

/**
 * Ranks the cards the player lacks by how much of the competitive field they would unlock. Only tournament decks that
 * are close to complete count: a deck missing a dozen cards says nothing about which one to chase first.
 *
 * Every deck carries a weight, its archetype's strength shared among that archetype's lists. A deck passes on its weight
 * to its missing cards in proportion to the copies each one lacks. Prints count as one card, as everywhere else.
 */
class ChaseRanker(
    private val completeness: CompletenessCalculator = CompletenessCalculator(),
) {
    fun rank(
        snapshot: MetaSnapshot,
        catalog: CardCatalog,
        inventory: Inventory,
    ): ChaseRanking {
        val tallies = LinkedHashMap<GameplayKey, Tally>()
        for ((list, weight) in weightedLists(snapshot)) {
            val owned = completenessOf(list, catalog, inventory) ?: continue
            if (owned.missingCopies in 1..MAX_MISSING_COPIES) addTo(tallies, owned, weight)
        }
        val packs = packsByCard(catalog, tallies.keys)
        val cards = tallies.map { (key, tally) -> tally.toEntry(packs[key].orEmpty()) }.sortedWith(BEST_FIRST)
        return ChaseRanking(cards, packsOf(cards))
    }

    private fun weightedLists(snapshot: MetaSnapshot): List<Pair<MetaDecklist, Double>> =
        snapshot.archetypes.flatMap { archetype ->
            archetype.decklists.map { it to archetype.strength / archetype.decklists.size }
        }

    /** A list with a card the catalog does not know cannot be checked, so it says nothing. */
    private fun completenessOf(
        list: MetaDecklist,
        catalog: CardCatalog,
        inventory: Inventory,
    ): DeckCompleteness? {
        val entries = list.entries.map { entry -> DeckEntry(catalog[entry.cardId] ?: return null, entry.copies) }
        return completeness.calculate(Deck(entries, list.energyTypes), inventory)
    }

    private fun addTo(
        tallies: MutableMap<GameplayKey, Tally>,
        owned: DeckCompleteness,
        weight: Double,
    ) {
        for (missing in owned.missing) {
            val tally = tallies.getOrPut(missing.card.gameplayKey) { Tally(missing.card) }
            tally.score += weight * missing.missingCopies / owned.missingCopies
            tally.helped++
            if (owned.missing.size == 1) tally.finished++
        }
    }

    /** Any print of a card can be pulled, so a card is in every pack that holds one of its prints. */
    private fun packsByCard(
        catalog: CardCatalog,
        keys: Set<GameplayKey>,
    ): Map<GameplayKey, List<String>> =
        catalog.cards
            .filter { it.gameplayKey in keys }
            .groupBy { it.gameplayKey }
            .mapValues { (_, prints) -> prints.flatMap { it.packs }.distinct() }

    /** A card in several packs shares its score between them, so a card found in one pack weighs most for that pack. */
    private fun packsOf(cards: List<ChaseEntry>): List<ChasePack> =
        cards
            .flatMap { entry -> entry.packs.map { pack -> pack to entry } }
            .groupBy({ it.first }, { it.second })
            .map { (name, inPack) -> ChasePack(name, inPack.sumOf { it.score / it.packs.size }, inPack) }
            .sortedWith(compareByDescending<ChasePack> { it.score }.thenBy { it.name })

    private class Tally(
        val card: Card,
    ) {
        var score = 0.0
        var finished = 0
        var helped = 0

        fun toEntry(packs: List<String>) = ChaseEntry(card, score, finished, helped, packs)
    }

    private companion object {
        /** More than a few missing copies and the deck is a project, not a purchase. */
        const val MAX_MISSING_COPIES = 4

        val BEST_FIRST =
            compareByDescending<ChaseEntry> { it.score }
                .thenByDescending { it.decksFinished }
                .thenBy { it.card.name.value }
    }
}
