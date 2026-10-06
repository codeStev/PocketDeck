package dev.pocketdeck.adapter.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.magmar
import dev.pocketdeck.domain.pokeBall
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Runs against real SQLite on a device, which is what the DAOs' SQL is meant for. */
class RoomRepositoriesTest {
    private lateinit var database: PocketDeckDatabase
    private lateinit var catalogs: RoomCardCatalogRepository
    private lateinit var collections: RoomCollectionRepository

    @Before
    fun openInMemoryDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = PocketDeckDatabases.inMemory(context)
        catalogs = RoomCardCatalogRepository(database.cardDao())
        collections = RoomCollectionRepository(database.ownedCardDao())
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun emptyCatalogAtStart() =
        runTest {
            catalogs.current().size shouldBe 0
        }

    @Test
    fun replaceAllStoresTheCatalog() =
        runTest {
            catalogs.replaceAll(listOf(charmander, magmar, pokeBall))

            catalogs.current().cards.toSet() shouldBe setOf(charmander, magmar, pokeBall)
        }

    @Test
    fun replaceAllDropsCardsThatNoLongerExist() =
        runTest {
            catalogs.replaceAll(listOf(charmander, magmar))

            catalogs.replaceAll(listOf(charmander))

            catalogs.current().cards shouldBe listOf(charmander)
        }

    @Test
    fun catalogObservationEmitsAfterSync() =
        runTest {
            catalogs.replaceAll(listOf(charmander))

            catalogs.observe().first().size shouldBe 1
        }

    @Test
    fun collectionRoundTrips() =
        runTest {
            val collection = CardCollection.EMPTY.withCopies(charmander.id, 2).withCopies(magmar.id, 1)

            collections.save(collection)

            collections.current() shouldBe collection
            collections.observe().first() shouldBe collection
        }

    @Test
    fun savingAnEmptyCollectionClearsIt() =
        runTest {
            collections.save(CardCollection.EMPTY.withCopies(charmander.id, 2))

            collections.save(CardCollection.EMPTY)

            collections.current() shouldBe CardCollection.EMPTY
        }

    @Test
    fun savingReplacesPreviousCounts() =
        runTest {
            collections.save(CardCollection.EMPTY.withCopies(charmander.id, 2).withCopies(magmar.id, 2))

            collections.save(CardCollection.EMPTY.withCopies(charmander.id, 1))

            collections.current() shouldBe CardCollection.EMPTY.withCopies(charmander.id, 1)
        }
}
