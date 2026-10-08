package dev.pocketdeck.adapter.ui.chase

import dev.pocketdeck.domain.basicPokemon
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.meta.ChaseEntry
import dev.pocketdeck.domain.meta.ChasePack
import dev.pocketdeck.domain.meta.ChaseRanking
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ChaseListingTest {
    private fun entry(
        index: Int,
        packs: List<String> = listOf("Mewtwo"),
    ) = ChaseEntry(
        basicPokemon("Card $index"),
        score = 100.0 - index,
        decksFinished = 0,
        decksHelped = 2,
        packs = packs,
    )

    @Test
    fun `keeps only the best cards and packs for the small screen`() {
        val cards = (1..30).map { entry(it) }
        val packs = (1..9).map { ChasePack("Pack $it", score = 1.0, cards = cards.take(5)) }

        val state = ChaseListing.build(ChaseRanking(cards, packs), Language.ENGLISH)

        state.cards.map { it.name } shouldBe (1..ChaseListing.MAX_CARDS).map { "Card $it" }
        state.packs.size shouldBe ChaseListing.MAX_PACKS
    }

    @Test
    fun `a pack shows how many cards it holds and the best few of them`() {
        val cards = (1..5).map { entry(it) }

        val state = ChaseListing.build(ChaseRanking(cards, listOf(ChasePack("Mewtwo", 3.0, cards))), Language.ENGLISH)

        state.packs.single().run {
            cardCount shouldBe 5
            cardNames shouldBe listOf("Card 1", "Card 2", "Card 3")
        }
    }

    @Test
    fun `an empty ranking is an empty state`() {
        ChaseListing.build(ChaseRanking.EMPTY, Language.ENGLISH) shouldBe ChaseUiState()
    }
}
