package dev.pocketdeck.application.exchange

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionBackup
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.application.fakes.InMemoryProfileRepository
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.magmar
import dev.pocketdeck.domain.profile.ProfileName
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CollectionFilesTest {
    /** Writes "Account|id=copies,..." so the tests see what the services hand to a codec without a JSON or CSV library. */
    private class FakeCodec : CollectionFileCodec {
        override fun encode(
            file: CollectionFile,
            format: FileFormat,
        ) = file.accountName + "|" + format + "|" +
            file.rows.joinToString(",") { "${it.id.value}=${it.name}=${it.copies}" }

        override fun decode(text: String): Outcome<Map<String, Int>, FileProblem> =
            when {
                text == "newer" -> {
                    Outcome.Failure(FileProblem.UnsupportedVersion(9))
                }

                !text.contains('=') -> {
                    Outcome.Failure(FileProblem.NotACollectionFile)
                }

                else -> {
                    Outcome.Success(
                        text.split(',').associate {
                            it.substringBefore('=') to
                                it.substringAfter('=').toInt()
                        },
                    )
                }
            }
    }

    private val catalog = CardCatalog(listOf(charmander, magmar))
    private val clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC)
    private val collections = InMemoryCollectionRepository(CardCollection.EMPTY.withCopies(charmander.id, 2))
    private val backup = InMemoryCollectionBackup()
    private val profiles = InMemoryProfileRepository(ProfileName("Main Account!"))
    private val catalogs = InMemoryCardCatalogRepository(catalog)
    private val codec = FakeCodec()

    private val export = ExportCollectionService(collections, catalogs, profiles, codec, clock)
    private val read = ReadCollectionFileService(codec, catalogs, collections, profiles)
    private val replace = ReplaceCollectionService(collections, backup)

    @Test
    fun `exports the active account with card names and a file name made of the account and the day`() =
        runTest {
            val file = export(FileFormat.JSON)

            file.fileName shouldBe "pocketdeck-main-account-2026-10-08.json"
            file.format shouldBe FileFormat.JSON
            file.text shouldBe "Main Account!|JSON|${charmander.id.value}=Charmander=2"
        }

    @Test
    fun `a csv export is named csv`() =
        runTest {
            export(FileFormat.CSV).fileName shouldBe "pocketdeck-main-account-2026-10-08.csv"
        }

    @Test
    fun `a file is read into a preview that changes nothing`() =
        runTest {
            val preview = (read("${magmar.id.value}=3,${charmander.id.value}=1") as Outcome.Success).value

            preview.accountName shouldBe "Main Account!"
            preview.cards shouldBe 2
            preview.copies shouldBe 4
            preview.unknownCards shouldBe 0
            preview.currentCards shouldBe 1
            collections.current().copiesOf(charmander.id) shouldBe 2
        }

    @Test
    fun `cards this version does not know are left out and counted`() =
        runTest {
            val preview = (read("${charmander.id.value}=1,zz-999=4") as Outcome.Success).value

            preview.cards shouldBe 1
            preview.unknownCards shouldBe 1
            preview.collection.copiesOf(CardId("zz-999")) shouldBe 0
        }

    @Test
    fun `a file with no known card is refused instead of emptying the account`() =
        runTest {
            read("zz-999=4") shouldBe Outcome.Failure(ImportFailure.NothingRecognised)
        }

    @Test
    fun `says whether the file is not a collection or from a newer version`() =
        runTest {
            read("hello") shouldBe Outcome.Failure(ImportFailure.NotACollectionFile)
            read("newer") shouldBe Outcome.Failure(ImportFailure.UnsupportedVersion(9))
        }

    @Test
    fun `replacing keeps the old collection as the backup so it can be restored`() =
        runTest {
            val preview = (read("${magmar.id.value}=3") as Outcome.Success).value

            replace(preview)

            collections.current().copiesOf(magmar.id) shouldBe 3
            collections.current().copiesOf(charmander.id) shouldBe 0
            backup.load()?.copiesOf(charmander.id) shouldBe 2
        }
}
