package dev.pocketdeck.domain.meta

import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.magmar
import dev.pocketdeck.domain.pokeBall
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CardPopularityTest {
    private val catalog = CardCatalog(listOf(charmander, magmar, pokeBall))

    private fun list(vararg cards: dev.pocketdeck.domain.card.Card) =
        MetaDecklist(
            ArchetypeName("Deck"),
            cards.map { DecklistEntry(it.id, 2) },
            setOf(EnergyType.FIRE),
            TournamentResult(1, 3, 1, 0),
        )

    @Test
    fun `is the share of decks that play a card`() {
        val snapshot =
            ArchetypeAggregator().aggregate(listOf(list(pokeBall, charmander), list(pokeBall, magmar), list(pokeBall)))

        val popularity = CardPopularity.of(snapshot, catalog)

        popularity.of(pokeBall) shouldBe (1.0 plusOrMinus 1e-9)
        popularity.of(charmander) shouldBe (1.0 / 3 plusOrMinus 1e-9)
    }

    @Test
    fun `counts every print of a card as the same card`() {
        val reprint = charmander.copy(id = CardId("x-reprint"))
        val both = CardCatalog(listOf(charmander, reprint))
        val snapshot = ArchetypeAggregator().aggregate(listOf(list(charmander), list(reprint)))

        CardPopularity.of(snapshot, both).of(charmander) shouldBe (1.0 plusOrMinus 1e-9)
    }

    @Test
    fun `knows nothing about a card no deck plays or without any decks`() {
        CardPopularity.NONE.of(pokeBall) shouldBe 0.0
        CardPopularity.of(MetaSnapshot.EMPTY, catalog).of(magmar) shouldBe 0.0
    }

    @Test
    fun `ignores cards of decklists the catalog does not know`() {
        val snapshot =
            ArchetypeAggregator().aggregate(
                listOf(
                    MetaDecklist(
                        ArchetypeName("X"),
                        listOf(DecklistEntry(CardId("zz-1"), 2)),
                        setOf(),
                        TournamentResult(1, 1, 0, 0),
                    ),
                ),
            )

        CardPopularity.of(snapshot, catalog).of(charmander) shouldBe 0.0
    }

    private fun many(
        times: Int,
        vararg cards: dev.pocketdeck.domain.card.Card,
    ) = List(times) { list(*cards) }

    @Test
    fun `around a card it is the share among the decks that play that card`() {
        // Poké Ball is in every deck, but Magmar only ever appears with Charmander.
        val decks = many(5, charmander, magmar, pokeBall) + many(5, pokeBall)
        val popularity = CardPopularity.of(ArchetypeAggregator().aggregate(decks), catalog)

        val around = popularity.around(charmander)

        around.of(magmar) shouldBe (1.0 plusOrMinus 1e-9)
        popularity.of(magmar) shouldBe (0.5 plusOrMinus 1e-9)
    }

    @Test
    fun `around a card too few decks play it stays as it was`() {
        val decks = many(2, charmander, magmar) + many(20, pokeBall)
        val popularity = CardPopularity.of(ArchetypeAggregator().aggregate(decks), catalog)

        popularity.around(charmander).of(magmar) shouldBe popularity.of(magmar)
    }
}
