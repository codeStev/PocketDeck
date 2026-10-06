package dev.pocketdeck.application.scan

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.fakes.FakeCardRecognizer
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.magmar
import dev.pocketdeck.domain.scan.Confidence
import dev.pocketdeck.domain.scan.ScannedCard
import dev.pocketdeck.domain.scan.TileRecognition
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Exercises the scan use cases together, the way the capture adapter drives them. */
class CollectionScanTest {
    private val sessions = InMemoryScanSessionStore()
    private val collections = InMemoryCollectionRepository()
    private val catalogs = InMemoryCardCatalogRepository(CardCatalog(listOf(charmander, magmar)))
    private val frame = ScreenFrame(width = 2, height = 1, pixels = intArrayOf(0, 0))

    private fun seen(
        id: CardId,
        copies: Int,
        confidence: Double = 0.9,
    ) = TileRecognition(id, copies, Confidence(confidence))

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

            submitFrame(frame) shouldBe Outcome.Success(ScanProgress(framesProcessed = 1, distinctCards = 1))
            submitFrame(frame) shouldBe Outcome.Success(ScanProgress(framesProcessed = 2, distinctCards = 2))
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
            val confirm = ConfirmScanResultsService(catalogs, collections)
            val review =
                listOf(ScannedCard(charmander.id, copies = 2, confidence = Confidence(0.9), needsReview = false))

            confirm(review) shouldBe Outcome.Success(ConfirmedScan(updatedCards = 1))

            collections.current().copiesOf(charmander.id) shouldBe 2
        }

    @Test
    fun `confirming keeps cards that were not part of the scan`() =
        runTest {
            collections.save(CardCollection.EMPTY.withCopies(magmar.id, 1))
            val confirm = ConfirmScanResultsService(catalogs, collections)

            confirm(listOf(ScannedCard(charmander.id, 2, Confidence(0.9), needsReview = false)))

            collections.current().copiesOf(magmar.id) shouldBe 1
        }

    @Test
    fun `confirming rejects the whole review when it contains unknown cards`() =
        runTest {
            val confirm = ConfirmScanResultsService(catalogs, collections)
            val unknown = CardId("ghost")
            val review =
                listOf(
                    ScannedCard(charmander.id, 2, Confidence(0.9), needsReview = false),
                    ScannedCard(unknown, 1, Confidence(0.9), needsReview = false),
                )

            confirm(review) shouldBe Outcome.Failure(ConfirmScanFailure.UnknownCards(listOf(unknown)))

            collections.current() shouldBe CardCollection.EMPTY
        }
}
