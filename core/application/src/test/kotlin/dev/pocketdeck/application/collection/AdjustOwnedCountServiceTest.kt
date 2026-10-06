package dev.pocketdeck.application.collection

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.charmander
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AdjustOwnedCountServiceTest {
    private val collections = InMemoryCollectionRepository()
    private val service =
        AdjustOwnedCountService(InMemoryCardCatalogRepository(CardCatalog(listOf(charmander))), collections)

    @Test
    fun `adds copies and persists the new collection`() =
        runTest {
            val outcome = service(charmander.id, delta = 2)

            outcome shouldBe Outcome.Success(2)
            collections.current().copiesOf(charmander.id) shouldBe 2
        }

    @Test
    fun `removing more copies than owned stops at zero`() =
        runTest {
            service(charmander.id, delta = 1)

            service(charmander.id, delta = -5) shouldBe Outcome.Success(0)
        }

    @Test
    fun `rejects cards that are not in the catalog and leaves the collection alone`() =
        runTest {
            val unknown = CardId("does-not-exist")

            service(unknown, delta = 1) shouldBe Outcome.Failure(AdjustCountFailure.UnknownCard(unknown))
            collections.current().copiesByCard shouldBe emptyMap()
        }
}
