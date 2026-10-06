package dev.pocketdeck.application.collection

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.fakes.InMemoryCollectionBackup
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.magmar
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RestorePreviousCollectionServiceTest {
    private val collections = InMemoryCollectionRepository()
    private val backup = InMemoryCollectionBackup()
    private val restore = RestorePreviousCollectionService(collections, backup)

    @Test
    fun `puts the backed up collection back`() =
        runTest {
            val before = CardCollection.EMPTY.withCopies(charmander.id, 7)
            backup.save(before)
            collections.save(CardCollection.EMPTY.withCopies(magmar.id, 1))

            restore() shouldBe Outcome.Success(Unit)

            collections.current() shouldBe before
        }

    @Test
    fun `keeps what is there as the new backup so the restore can be undone`() =
        runTest {
            val scanResult = CardCollection.EMPTY.withCopies(magmar.id, 1)
            backup.save(CardCollection.EMPTY.withCopies(charmander.id, 7))
            collections.save(scanResult)

            restore()

            backup.load() shouldBe scanResult
        }

    @Test
    fun `without a backup nothing is changed`() =
        runTest {
            collections.save(CardCollection.EMPTY.withCopies(magmar.id, 1))

            restore() shouldBe Outcome.Failure(RestoreFailure.NoBackup)

            collections.current().copiesOf(magmar.id) shouldBe 1
        }
}
