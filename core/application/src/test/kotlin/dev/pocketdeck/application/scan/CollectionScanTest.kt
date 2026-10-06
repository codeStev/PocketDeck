package dev.pocketdeck.application.scan

import app.cash.turbine.test
import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.fakes.FakeCardRecognizer
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionBackup
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.magmar
import dev.pocketdeck.domain.scan.Confidence
import dev.pocketdeck.domain.scan.ReviewBreakdown
import dev.pocketdeck.domain.scan.ReviewReason
import dev.pocketdeck.domain.scan.ScannedCard
import dev.pocketdeck.domain.scan.TileRecognition
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Exercises the scan use cases together, the way the capture adapter drives them. */
class CollectionScanTest {
    private val sessions = InMemoryScanSessionStore()
    private val collections = InMemoryCollectionRepository()
    private val backup = InMemoryCollectionBackup()
    private val catalogs = InMemoryCardCatalogRepository(CardCatalog(listOf(charmander, magmar)))
    private val frame = ScreenFrame(width = 2, height = 1, pixels = intArrayOf(0, 0))

    private fun seen(
        id: CardId,
        copies: Int,
        confidence: Double = 0.9,
    ) = TileRecognition(id, copies, Confidence(confidence))

    private fun progressOf(outcome: Outcome<FrameAnalysis, ScanFailure>) = (outcome as Outcome.Success).value.progress

    private fun progress(
        frames: Int,
        cards: Int,
        needingReview: Int = 0,
    ) = ScanProgress(frames, cards, needingReview)

    private val start = StartCollectionScanService(sessions)

    private fun submit(recognizer: FakeCardRecognizer) = SubmitScanFrameService(recognizer, sessions)

    private val finish = FinishCollectionScanService(sessions)

    @Test
    fun `frames submitted without a started scan are rejected`() =
        runTest {
            submit(FakeCardRecognizer())(frame) shouldBe Outcome.Failure(ScanFailure.NoActiveScan)
        }

    @Test
    fun `submitting frames reports progress`() =
        runTest {
            val recognizer =
                FakeCardRecognizer(
                    listOf(seen(charmander.id, 1)),
                    listOf(seen(charmander.id, 1), seen(magmar.id, 2)),
                )
            val submitFrame = submit(recognizer)
            start()

            progressOf(submitFrame(frame)) shouldBe progress(1, 1)
            progressOf(submitFrame(frame)) shouldBe progress(2, 2)
            recognizer.framesSeen.size shouldBe 2
        }

    @Test
    fun `finishing returns the merged review and ends the scan`() =
        runTest {
            val submitFrame = submit(FakeCardRecognizer(listOf(seen(charmander.id, 2, 0.95))))
            start()
            submitFrame(frame)

            finish() shouldBe
                Outcome.Success(
                    listOf(ScannedCard(charmander.id, copies = 2, confidence = Confidence(0.95), needsReview = false)),
                )
            finish() shouldBe Outcome.Failure(ScanFailure.NoActiveScan)
        }

    @Test
    fun `starting again discards the previous unfinished scan`() =
        runTest {
            val submitFrame = submit(FakeCardRecognizer(listOf(seen(charmander.id, 1))))
            start()
            submitFrame(frame)

            start()

            finish() shouldBe Outcome.Success(emptyList())
        }

    @Test
    fun `confirming writes the scanned copies into the collection`() =
        runTest {
            val confirm = ConfirmScanResultsService(catalogs, collections, backup)
            val review =
                listOf(ScannedCard(charmander.id, copies = 2, confidence = Confidence(0.9), needsReview = false))

            confirm(review, replaceCollection = false) shouldBe Outcome.Success(ConfirmedScan(updatedCards = 1))

            collections.current().copiesOf(charmander.id) shouldBe 2
        }

    @Test
    fun `confirming keeps cards that were not part of the scan`() =
        runTest {
            collections.save(CardCollection.EMPTY.withCopies(magmar.id, 1))
            val confirm = ConfirmScanResultsService(catalogs, collections, backup)

            confirm(
                listOf(ScannedCard(charmander.id, 2, Confidence(0.9), needsReview = false)),
                replaceCollection = false,
            )

            collections.current().copiesOf(magmar.id) shouldBe 1
        }

    @Test
    fun `confirming rejects the whole review when it contains unknown cards`() =
        runTest {
            val confirm = ConfirmScanResultsService(catalogs, collections, backup)
            val unknown = CardId("ghost")
            val review =
                listOf(
                    ScannedCard(charmander.id, 2, Confidence(0.9), needsReview = false),
                    ScannedCard(unknown, 1, Confidence(0.9), needsReview = false),
                )

            confirm(review, replaceCollection = false) shouldBe
                Outcome.Failure(ConfirmScanFailure.UnknownCards(listOf(unknown)))

            collections.current() shouldBe CardCollection.EMPTY
        }

    @Test
    fun `progress counts the cards that will need a review`() =
        runTest {
            val unreadableCount = TileRecognition(charmander.id, copies = null, confidence = Confidence(0.95))
            val submitFrame = submit(FakeCardRecognizer(listOf(unreadableCount, seen(magmar.id, 1))))
            start()

            val analysis = (submitFrame(frame) as Outcome.Success).value

            analysis.progress shouldBe ScanProgress(1, 2, cardsNeedingReview = 1, breakdown = ReviewBreakdown(1, 0, 0))
            analysis.tilesRecognized shouldBe 2
            analysis.countsExpected shouldBe 2
            analysis.countsRead shouldBe 1
        }

    @Test
    fun `observers follow a scan from its start to its end`() =
        runTest {
            val observe = ObserveScanProgressService(sessions)
            val submitFrame = submit(FakeCardRecognizer(listOf(seen(charmander.id, 1))))

            observe().test {
                awaitItem() shouldBe null

                start()
                awaitItem() shouldBe ScanProgress(0, 0, 0)

                submitFrame(frame)
                awaitItem() shouldBe ScanProgress(1, 1, 0)

                finish()
                awaitItem() shouldBe null
            }
        }

    @Test
    fun `replacing the collection drops cards that the scan did not find`() =
        runTest {
            collections.save(CardCollection.EMPTY.withCopies(magmar.id, 4))
            val confirm = ConfirmScanResultsService(catalogs, collections, backup)

            confirm(
                listOf(ScannedCard(charmander.id, 2, Confidence(0.9), needsReview = false)),
                replaceCollection = true,
            )

            collections.current().copiesOf(charmander.id) shouldBe 2
            collections.current().copiesOf(magmar.id) shouldBe 0
        }

    @Test
    fun `a rejected review does not touch the collection even when replacing`() =
        runTest {
            collections.save(CardCollection.EMPTY.withCopies(magmar.id, 4))
            val confirm = ConfirmScanResultsService(catalogs, collections, backup)
            val review = listOf(ScannedCard(CardId("ghost"), 1, Confidence(0.9), needsReview = false))

            confirm(review, replaceCollection = true) shouldBe
                Outcome.Failure(ConfirmScanFailure.UnknownCards(listOf(CardId("ghost"))))

            collections.current().copiesOf(magmar.id) shouldBe 4
        }

    @Test
    fun `a card whose count was never visible keeps the count it already had`() =
        runTest {
            collections.save(CardCollection.EMPTY.withCopies(charmander.id, 7))
            val confirm = ConfirmScanResultsService(catalogs, collections, backup)
            val unread =
                ScannedCard(
                    charmander.id,
                    1,
                    Confidence(0.95),
                    needsReview = true,
                    reason = ReviewReason.COUNT_NEVER_READ,
                )

            confirm(listOf(unread), replaceCollection = true)

            collections.current().copiesOf(charmander.id) shouldBe 7
        }

    @Test
    fun `an unread count of a new card is stored as one`() =
        runTest {
            val confirm = ConfirmScanResultsService(catalogs, collections, backup)
            val unread =
                ScannedCard(magmar.id, 1, Confidence(0.95), needsReview = true, reason = ReviewReason.COUNT_NEVER_READ)

            confirm(listOf(unread), replaceCollection = false)

            collections.current().copiesOf(magmar.id) shouldBe 1
        }

    @Test
    fun `a count the player corrected replaces the old one`() =
        runTest {
            collections.save(CardCollection.EMPTY.withCopies(charmander.id, 7))
            val confirm = ConfirmScanResultsService(catalogs, collections, backup)
            val corrected = ScannedCard(charmander.id, 3, Confidence(0.95), needsReview = false)

            confirm(listOf(corrected), replaceCollection = true)

            collections.current().copiesOf(charmander.id) shouldBe 3
        }

    @Test
    fun `tiles under the navigation fade are not waited for`() =
        runTest {
            val faded = TileRecognition(magmar.id, copies = null, confidence = Confidence(0.95), expectsCount = false)
            val submitFrame = submit(FakeCardRecognizer(listOf(seen(charmander.id, 2), faded)))
            start()

            val analysis = (submitFrame(frame) as Outcome.Success).value

            analysis.tilesRecognized shouldBe 2
            analysis.countsExpected shouldBe 1
            analysis.countsRead shouldBe 1
            analysis.countsVisible shouldBe true
        }

    @Test
    fun `the previous collection is kept before a scan is saved`() =
        runTest {
            val before = CardCollection.EMPTY.withCopies(magmar.id, 4)
            collections.save(before)
            val confirm = ConfirmScanResultsService(catalogs, collections, backup)

            confirm(
                listOf(ScannedCard(charmander.id, 2, Confidence(0.9), needsReview = false)),
                replaceCollection = true,
            )

            backup.load() shouldBe before
        }

    @Test
    fun `an empty collection is not worth a backup`() =
        runTest {
            val confirm = ConfirmScanResultsService(catalogs, collections, backup)

            confirm(
                listOf(ScannedCard(charmander.id, 2, Confidence(0.9), needsReview = false)),
                replaceCollection = false,
            )

            backup.load() shouldBe null
        }

    @Test
    fun `a rejected review leaves the existing backup alone`() =
        runTest {
            val older = CardCollection.EMPTY.withCopies(magmar.id, 9)
            backup.save(older)
            collections.save(CardCollection.EMPTY.withCopies(magmar.id, 4))
            val confirm = ConfirmScanResultsService(catalogs, collections, backup)

            confirm(
                listOf(ScannedCard(CardId("ghost"), 1, Confidence(0.9), needsReview = false)),
                replaceCollection = true,
            )

            backup.load() shouldBe older
        }

    @Test
    fun `finishing reports the review to the diagnostics`() =
        runTest {
            val reported = mutableListOf<List<ScannedCard>>()
            val diagnostics =
                object : ScanDiagnostics {
                    override fun pageSettled(report: PageReport) = Unit

                    override fun scanFinished(review: List<ScannedCard>) {
                        reported += review
                    }
                }
            val submitFrame =
                submit(
                    FakeCardRecognizer(
                        listOf(seen(charmander.id, 2), TileRecognition(magmar.id, null, Confidence(0.9))),
                    ),
                )
            start()
            submitFrame(frame)

            val review = (FinishCollectionScanService(sessions, diagnostics)() as Outcome.Success).value

            reported shouldBe listOf(review)
            review.single { it.cardId == magmar.id }.reason shouldBe ReviewReason.COUNT_NEVER_READ
        }
}
