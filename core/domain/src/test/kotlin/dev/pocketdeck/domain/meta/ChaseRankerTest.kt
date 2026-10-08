package dev.pocketdeck.domain.meta

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.giovanni
import dev.pocketdeck.domain.professorsResearch
import dev.pocketdeck.domain.sabrina
import dev.pocketdeck.domain.trainer
import dev.pocketdeck.domain.validFireDeck
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ChaseRankerTest {
    private val deck = validFireDeck()
    private val cards = deck.entries.map { it.card }

    private fun fireDeck(replace: Map<Card, Card> = emptyMap()) =
        Deck(deck.entries.map { it.copy(card = replace[it.card] ?: it.card) }, deck.energyTypes)

    private fun list(
        name: String,
        deck: Deck = fireDeck(),
    ) = MetaDecklist(
        ArchetypeName(name),
        deck.entries.map { DecklistEntry(it.card.id, it.copies) },
        deck.energyTypes,
        TournamentResult(placing = 1, wins = 3, losses = 1, ties = 0),
    )

    private fun inventoryOf(
        owned: List<Card>,
        catalog: CardCatalog,
    ): Inventory {
        val collection = owned.fold(CardCollection.EMPTY) { total, card -> total.withCopies(card.id, 2) }
        return Inventory.of(collection, catalog)
    }

    /** One fire deck in the field; the player owns two copies of every card except those in [lacking]. */
    private fun chase(
        lacking: List<Card>,
        replace: Map<Card, Card> = emptyMap(),
        reprints: List<Card> = emptyList(),
    ): ChaseRanking {
        val inDeck = cards.map { replace[it] ?: it }
        val catalog = CardCatalog(inDeck + reprints)
        val snapshot = ArchetypeAggregator().aggregate(listOf(list("Fire", fireDeck(replace))))
        return ChaseRanker().rank(snapshot, catalog, inventoryOf(inDeck - lacking.toSet(), catalog))
    }

    @Test
    fun `the only missing card finishes the deck and takes its whole weight`() {
        val ranking = chase(lacking = listOf(sabrina))

        val entry = ranking.cards.single()
        entry.card.gameplayKey shouldBe sabrina.gameplayKey
        entry.decksFinished shouldBe 1
        entry.decksHelped shouldBe 1
        val weight =
            ArchetypeAggregator()
                .aggregate(listOf(list("Fire")))
                .archetypes
                .single()
                .strength
        entry.score shouldBe (weight plusOrMinus 1e-9)
    }

    @Test
    fun `a deck passes its weight on in proportion to the copies each card lacks`() {
        val ranking = chase(lacking = listOf(sabrina, giovanni))

        val weight =
            ArchetypeAggregator()
                .aggregate(listOf(list("Fire")))
                .archetypes
                .single()
                .strength
        ranking.cards.sumOf { it.score } shouldBe (weight plusOrMinus 1e-9)
        ranking.cards.map { it.decksFinished } shouldBe listOf(0, 0)
        ranking.cards.map { it.decksHelped } shouldBe listOf(1, 1)
    }

    @Test
    fun `a deck that is already buildable adds nothing`() {
        chase(lacking = emptyList()) shouldBe ChaseRanking.EMPTY
    }

    @Test
    fun `a deck far from complete says nothing about what to chase`() {
        chase(lacking = cards.take(6)) shouldBe ChaseRanking.EMPTY
    }

    @Test
    fun `a card the popular deck lacks outranks one only a rare deck lacks`() {
        val tech = trainer("Rare Tech")
        val rareDeck = fireDeck(mapOf(giovanni to tech))
        val catalog = CardCatalog(cards + tech)
        val lists = List(3) { list("Common") } + list("Rare", rareDeck)
        val snapshot = ArchetypeAggregator().aggregate(lists)

        val ranking = ChaseRanker().rank(snapshot, catalog, inventoryOf(cards - sabrina, catalog))

        ranking.cards.map { it.card.gameplayKey } shouldBe listOf(sabrina.gameplayKey, tech.gameplayKey)
        (ranking.cards.first().score > ranking.cards.last().score) shouldBe true
    }

    @Test
    fun `another print of a card counts as owning it`() {
        val reprint = sabrina.copy(id = CardId("x-sabrina"))
        val catalog = CardCatalog(cards + reprint)
        val snapshot = ArchetypeAggregator().aggregate(listOf(list("Fire")))

        val ranking = ChaseRanker().rank(snapshot, catalog, inventoryOf(cards - sabrina + reprint, catalog))

        ranking shouldBe ChaseRanking.EMPTY
    }

    @Test
    fun `a list with a card the catalog does not know is skipped`() {
        val unknown = list("Fire").let { it.copy(entries = it.entries + DecklistEntry(CardId("zz-1"), 2)) }
        val catalog = CardCatalog(cards)

        val ranking =
            ChaseRanker().rank(
                ArchetypeAggregator().aggregate(listOf(unknown)),
                catalog,
                inventoryOf(cards - sabrina, catalog),
            )

        ranking shouldBe ChaseRanking.EMPTY
    }

    @Test
    fun `no decks give no ranking`() {
        val catalog = CardCatalog(cards)

        ChaseRanker().rank(MetaSnapshot.EMPTY, catalog, inventoryOf(cards, catalog)) shouldBe ChaseRanking.EMPTY
    }

    @Test
    fun `a card is found in every pack that holds one of its prints`() {
        val inMewtwo = sabrina.copy(packs = listOf("Mewtwo"))
        val reprint = sabrina.copy(id = CardId("x-sabrina"), packs = listOf("Charizard", "Mewtwo"))

        val ranking =
            chase(lacking = listOf(inMewtwo), replace = mapOf(sabrina to inMewtwo), reprints = listOf(reprint))

        ranking.cards.single().packs shouldBe listOf("Mewtwo", "Charizard")
    }

    @Test
    fun `a pack ranks by what it holds and a card in several packs is split between them`() {
        val shared = sabrina.copy(packs = listOf("A", "B"))
        val onlyInA = giovanni.copy(packs = listOf("A"))

        val ranking =
            chase(lacking = listOf(shared, onlyInA), replace = mapOf(sabrina to shared, giovanni to onlyInA))

        ranking.packs.map { it.name } shouldBe listOf("A", "B")
        ranking.packs
            .first()
            .cards
            .map { it.card.gameplayKey }
            .toSet() shouldBe
            setOf(shared.gameplayKey, onlyInA.gameplayKey)
        ranking.packs.sumOf { it.score } shouldBe (ranking.cards.sumOf { it.score } plusOrMinus 1e-9)
    }

    @Test
    fun `a card without a known pack is ranked but belongs to no pack`() {
        val noPack = professorsResearch.copy(packs = emptyList())

        val ranking = chase(lacking = listOf(noPack), replace = mapOf(professorsResearch to noPack))

        ranking.cards.single().packs shouldBe emptyList()
        ranking.packs shouldBe emptyList()
    }
}
