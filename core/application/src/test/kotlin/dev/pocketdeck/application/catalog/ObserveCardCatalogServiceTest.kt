package dev.pocketdeck.application.catalog

import app.cash.turbine.test
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.charmander
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ObserveCardCatalogServiceTest {
    @Test
    fun `emits the catalog and every later synchronisation`() =
        runTest {
            val repository = InMemoryCardCatalogRepository()

            ObserveCardCatalogService(repository)().test {
                awaitItem() shouldBe CardCatalog.EMPTY

                repository.replaceAll(listOf(charmander))

                awaitItem().size shouldBe 1
            }
        }
}
