package dev.pocketdeck.adapter.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.pocketdeck.application.collection.CollectionBackup
import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.application.profile.ProfileRepository
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.magmar
import dev.pocketdeck.domain.pokeBall
import dev.pocketdeck.domain.profile.ProfileName
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
    private lateinit var collections: CollectionRepository
    private lateinit var backup: CollectionBackup
    private lateinit var profiles: ProfileRepository

    @Before
    fun openInMemoryDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = PocketDeckDatabases.inMemory(context)
        catalogs = RoomCardCatalogRepository(database.cardDao())
        val storage = PocketDeckDatabases.storageOf(database)
        collections = storage.collections
        backup = storage.backup
        profiles = storage.profiles
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

    @Test
    fun backupIsEmptyAtStart() =
        runTest {
            backup.load() shouldBe null
            backup.observeAvailable().first() shouldBe false
        }

    @Test
    fun backupRoundTripsAndIsReplacedByTheNextOne() =
        runTest {
            val first = CardCollection.EMPTY.withCopies(charmander.id, 2)
            val second = CardCollection.EMPTY.withCopies(magmar.id, 5)

            backup.save(first)
            backup.save(second)

            backup.load() shouldBe second
            backup.observeAvailable().first() shouldBe true
        }

    @Test
    fun aNewDatabaseHasOneProfile() =
        runTest {
            val observed = profiles.observe().first()

            observed.all.map { it.name.value } shouldBe listOf("Main")
            observed.active shouldBe observed.all.single()
        }

    @Test
    fun everyProfileKeepsItsOwnCollectionAndBackup() =
        runTest {
            val main = CardCollection.EMPTY.withCopies(charmander.id, 2)
            collections.save(main)
            backup.save(main)

            val alt = profiles.add(ProfileName("Alt"))
            profiles.activate(alt.id)

            collections.current() shouldBe CardCollection.EMPTY
            backup.load() shouldBe null
            collections.save(CardCollection.EMPTY.withCopies(magmar.id, 1))

            profiles.activate(
                profiles
                    .observe()
                    .first()
                    .all
                    .first()
                    .id,
            )
            collections.current() shouldBe main
        }

    @Test
    fun theCollectionFlowFollowsTheActiveProfile() =
        runTest {
            collections.save(CardCollection.EMPTY.withCopies(charmander.id, 2))
            val alt = profiles.add(ProfileName("Alt"))

            profiles.activate(alt.id)

            collections.observe().first() shouldBe CardCollection.EMPTY
        }

    @Test
    fun removingTheActiveProfileDeletesItsCardsAndFallsBackToAnother() =
        runTest {
            val alt = profiles.add(ProfileName("Alt"))
            profiles.activate(alt.id)
            collections.save(CardCollection.EMPTY.withCopies(magmar.id, 4))

            profiles.remove(alt.id)

            profiles
                .observe()
                .first()
                .active.name.value shouldBe "Main"
            database.ownedCardDao().getAll(alt.id.value) shouldBe emptyList()
        }

    @Test
    fun theLastProfileCannotBeRemoved() =
        runTest {
            val only = profiles.observe().first().active

            runCatching { profiles.remove(only.id) }.isFailure shouldBe true
        }
}
