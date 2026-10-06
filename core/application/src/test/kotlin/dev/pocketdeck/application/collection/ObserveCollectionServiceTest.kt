package dev.pocketdeck.application.collection

import app.cash.turbine.test
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.collection.CardCollection
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ObserveCollectionServiceTest {
    @Test
    fun `emits the current collection and every later change`() =
        runTest {
            val repository = InMemoryCollectionRepository()
            val observe = ObserveCollectionService(repository)

            observe().test {
                awaitItem() shouldBe CardCollection.EMPTY

                val updated = CardCollection.EMPTY.withCopies(charmander.id, 2)
                repository.save(updated)

                awaitItem() shouldBe updated
            }
        }
}
