package dev.pocketdeck.application.catalog

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.fakes.FakeCardCatalogSource
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.magmar
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SyncCardCatalogServiceTest {
    private val repository = InMemoryCardCatalogRepository()

    private fun serviceWith(result: Outcome<List<Card>, SourceUnavailable>) =
        SyncCardCatalogService(FakeCardCatalogSource(result), repository)

    @Test
    fun `stores the fetched cards and reports how many`() =
        runTest {
            val service = serviceWith(Outcome.Success(listOf(charmander, magmar)))

            val outcome = service()

            outcome shouldBe Outcome.Success(CatalogSyncReport(cardCount = 2))
            repository.current().size shouldBe 2
        }

    @Test
    fun `keeps the stored catalog when the source is unavailable`() =
        runTest {
            repository.replaceAll(listOf(charmander))
            val service = serviceWith(Outcome.Failure(SourceUnavailable("offline")))

            val outcome = service()

            outcome shouldBe Outcome.Failure(CatalogSyncFailure.SourceNotReachable("offline"))
            repository.current().size shouldBe 1
        }

    @Test
    fun `refuses to replace the catalog with an empty one`() =
        runTest {
            repository.replaceAll(listOf(charmander))
            val service = serviceWith(Outcome.Success(emptyList()))

            val outcome = service()

            outcome shouldBe Outcome.Failure(CatalogSyncFailure.EmptyCatalog)
            repository.current().size shouldBe 1
        }
}
