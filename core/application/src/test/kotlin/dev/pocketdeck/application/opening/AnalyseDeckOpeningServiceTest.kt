package dev.pocketdeck.application.opening

import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.charizardEx
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.validFireDeck
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext

class AnalyseDeckOpeningServiceTest {
    private val deck = validFireDeck()
    private val catalogs = InMemoryCardCatalogRepository(CardCatalog(deck.entries.map { it.card }))
    private val service = AnalyseDeckOpeningService(catalogs, EmptyCoroutineContext)

    @Test
    fun `lists every pokemon of the deck once, for both seats`() =
        runTest {
            val analysis = service(deck)

            analysis.goingFirst.map { it.card.name.value }.toSet() shouldBe
                setOf("Charmander", "Charmeleon", "Charizard ex", "Magmar")
            analysis.goingSecond.size shouldBe analysis.goingFirst.size
        }

    @Test
    fun `gives the same odds every time for the same deck`() =
        runTest {
            service(deck) shouldBe service(deck)
        }

    @Test
    fun `the player going first cannot attack on the first turn but the one going second can`() =
        runTest {
            val analysis = service(deck)

            analysis.goingFirst
                .first { it.card == charmander }
                .turns
                .first()
                .hasAttacked shouldBe 0.0
            (
                analysis.goingSecond
                    .first { it.card == charmander }
                    .turns
                    .first()
                    .hasAttacked > 0.0
            ) shouldBe true
        }

    @Test
    fun `lists the hardest hitter first`() =
        runTest {
            val first = service(deck).goingFirst.first().card
            (first.attacks.maxOf { it.damage ?: 0 } >= charizardEx.attacks.maxOf { it.damage ?: 0 }) shouldBe true
        }
}
