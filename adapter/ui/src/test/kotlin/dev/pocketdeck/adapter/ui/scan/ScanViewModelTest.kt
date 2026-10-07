package dev.pocketdeck.adapter.ui.scan

import app.cash.turbine.test
import dev.pocketdeck.application.catalog.ObserveCardCatalogService
import dev.pocketdeck.application.collection.ObserveBackupAvailableService
import dev.pocketdeck.application.collection.ObserveCollectionService
import dev.pocketdeck.application.collection.RestorePreviousCollectionService
import dev.pocketdeck.application.consent.Consent
import dev.pocketdeck.application.consent.GrantConsentService
import dev.pocketdeck.application.consent.ObserveConsentService
import dev.pocketdeck.application.fakes.FakeCardRecognizer
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionBackup
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.application.fakes.InMemoryConsentStore
import dev.pocketdeck.application.scan.ConfirmScanResultsService
import dev.pocketdeck.application.scan.FinishCollectionScanService
import dev.pocketdeck.application.scan.InMemoryScanSessionStore
import dev.pocketdeck.application.scan.ObserveScanProgressService
import dev.pocketdeck.application.scan.ScreenFrame
import dev.pocketdeck.application.scan.StartCollectionScanService
import dev.pocketdeck.application.scan.SubmitScanFrameService
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.magmar
import dev.pocketdeck.domain.scan.Confidence
import dev.pocketdeck.domain.scan.TileRecognition
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessions = InMemoryScanSessionStore()
    private val catalogs = InMemoryCardCatalogRepository(CardCatalog(listOf(charmander, magmar)))
    private val collections = InMemoryCollectionRepository()
    private val backup = InMemoryCollectionBackup()
    private val frame = ScreenFrame(1, 1, intArrayOf(0))

    @BeforeEach
    fun installMainDispatcher() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun removeMainDispatcher() = Dispatchers.resetMain()

    private val consents = InMemoryConsentStore()

    private fun viewModel(consent: InMemoryConsentStore = consents) =
        ScanViewModel(
            observeProgress = ObserveScanProgressService(sessions),
            finishScan = FinishCollectionScanService(sessions),
            confirmScan = ConfirmScanResultsService(catalogs, collections, backup),
            observeCatalog = ObserveCardCatalogService(catalogs),
            observeCollection = ObserveCollectionService(collections),
            observeBackup = ObserveBackupAvailableService(backup),
            restorePrevious = RestorePreviousCollectionService(collections, backup),
            language = Language.ENGLISH,
            consent = ConsentAccess(ObserveConsentService(consent), GrantConsentService(consent)),
        )

    private fun tile(
        card: dev.pocketdeck.domain.card.Card,
        copies: Int?,
        confidence: Double = 0.95,
    ) = TileRecognition(card.id, copies, Confidence(confidence))

    /** Runs a scan that saw [recognitions] in one frame, as the capture service would. */
    private suspend fun scanSeeing(vararg recognitions: TileRecognition) {
        StartCollectionScanService(sessions)()
        SubmitScanFrameService(FakeCardRecognizer(recognitions.toList()), sessions)(frame)
    }

    @Test
    fun `starts idle`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.state.test {
                awaitItem().phase shouldBe ScanPhase.IDLE
            }
        }

    @Test
    fun `follows a running scan`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.state.test {
                awaitItem().phase shouldBe ScanPhase.IDLE
                scanSeeing(tile(charmander, 1))
                advanceUntilIdle()

                val scanning = expectMostRecentItem()
                scanning.phase shouldBe ScanPhase.SCANNING
                scanning.progress?.distinctCards shouldBe 1
            }
        }

    @Test
    fun `loads the review with uncertain cards first`() =
        runTest(dispatcher) {
            scanSeeing(tile(charmander, 2), tile(magmar, null, confidence = 0.9))
            val viewModel = viewModel()

            viewModel.onReviewRequested(complete = false)
            advanceUntilIdle()

            val state = viewModel.state.value
            state.phase shouldBe ScanPhase.REVIEW
            state.review.map { it.name } shouldBe listOf("Magmar", "Charmander")
            state.review.map { it.needsReview } shouldBe listOf(true, false)
        }

    @Test
    fun `asking for a review without a scan says there is nothing to review`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onReviewRequested(complete = false)
            advanceUntilIdle()

            viewModel.state.value.phase shouldBe ScanPhase.IDLE
            viewModel.state.value.notice shouldBe ScanNotice.NOTHING_TO_REVIEW
        }

    @Test
    fun `adjusting a card changes its count and marks it as checked`() =
        runTest(dispatcher) {
            scanSeeing(tile(magmar, null))
            val viewModel = viewModel()
            viewModel.onReviewRequested(complete = false)
            advanceUntilIdle()

            viewModel.onAdjust(magmar.id, delta = 2)

            viewModel.state.value.review.single().run {
                copies shouldBe 3
                needsReview shouldBe false
            }
        }

    @Test
    fun `a count never drops below one`() =
        runTest(dispatcher) {
            scanSeeing(tile(charmander, 1))
            val viewModel = viewModel()
            viewModel.onReviewRequested(complete = false)
            advanceUntilIdle()

            viewModel.onAdjust(charmander.id, delta = -5)

            viewModel.state.value.review
                .single()
                .copies shouldBe 1
        }

    @Test
    fun `a wrongly detected card can be removed`() =
        runTest(dispatcher) {
            scanSeeing(tile(charmander, 1), tile(magmar, 1))
            val viewModel = viewModel()
            viewModel.onReviewRequested(complete = false)
            advanceUntilIdle()

            viewModel.onRemove(magmar.id)

            viewModel.state.value.review
                .map { it.name } shouldBe listOf("Charmander")
        }

    @Test
    fun `confirming writes the edited counts into the collection`() =
        runTest(dispatcher) {
            scanSeeing(tile(charmander, 2), tile(magmar, 1))
            val viewModel = viewModel()
            viewModel.onReviewRequested(complete = false)
            advanceUntilIdle()
            viewModel.onAdjust(charmander.id, delta = 1)
            viewModel.onRemove(magmar.id)

            viewModel.onConfirm()
            advanceUntilIdle()

            collections.current().copiesOf(charmander.id) shouldBe 3
            collections.current().copiesOf(magmar.id) shouldBe 0
            viewModel.state.value.phase shouldBe ScanPhase.SAVED
            viewModel.state.value.savedCards shouldBe 1
        }

    @Test
    fun `discarding leaves the collection alone`() =
        runTest(dispatcher) {
            scanSeeing(tile(charmander, 2))
            val viewModel = viewModel()
            viewModel.onReviewRequested(complete = false)
            advanceUntilIdle()

            viewModel.onDiscard()

            viewModel.state.value.phase shouldBe ScanPhase.IDLE
            collections.current().copiesByCard shouldBe emptyMap()
        }

    @Test
    fun `finishing the summary returns to idle`() =
        runTest(dispatcher) {
            scanSeeing(tile(charmander, 2))
            val viewModel = viewModel()
            viewModel.onReviewRequested(complete = false)
            advanceUntilIdle()
            viewModel.onConfirm()
            advanceUntilIdle()

            viewModel.onDone()

            viewModel.state.value.phase shouldBe ScanPhase.IDLE
        }

    @Test
    fun `scrolling automatically is the default`() =
        runTest(dispatcher) {
            viewModel().state.value.automatic shouldBe true
        }

    @Test
    fun `the player can switch automatic scrolling off and on`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onAutomaticChanged(false)
            viewModel.state.value.automatic shouldBe false

            viewModel.onAutomaticChanged(true)
            viewModel.state.value.automatic shouldBe true
        }

    @Test
    fun `the choice survives finishing a scan`() =
        runTest(dispatcher) {
            scanSeeing(tile(charmander, 2))
            val viewModel = viewModel()
            viewModel.onAutomaticChanged(false)
            viewModel.onReviewRequested(complete = false)
            advanceUntilIdle()
            viewModel.onConfirm()
            advanceUntilIdle()

            viewModel.onDone()

            viewModel.state.value.automatic shouldBe false
        }

    @Test
    fun `a complete scan offers to replace the whole collection`() =
        runTest(dispatcher) {
            scanSeeing(tile(charmander, 2))
            val viewModel = viewModel()

            viewModel.onReviewRequested(complete = true)
            advanceUntilIdle()

            viewModel.state.value.replaceCollection shouldBe true
        }

    @Test
    fun `a partial scan only updates what it saw`() =
        runTest(dispatcher) {
            scanSeeing(tile(charmander, 2))
            val viewModel = viewModel()

            viewModel.onReviewRequested(complete = false)
            advanceUntilIdle()

            viewModel.state.value.replaceCollection shouldBe false
        }

    @Test
    fun `confirming a complete scan removes cards that were not found`() =
        runTest(dispatcher) {
            collections.save(collections.current().withCopies(magmar.id, 5))
            scanSeeing(tile(charmander, 2))
            val viewModel = viewModel()
            viewModel.onReviewRequested(complete = true)
            advanceUntilIdle()

            viewModel.onConfirm()
            advanceUntilIdle()

            collections.current().copiesOf(magmar.id) shouldBe 0
            collections.current().copiesOf(charmander.id) shouldBe 2
        }

    @Test
    fun `the player can turn replacing off`() =
        runTest(dispatcher) {
            collections.save(collections.current().withCopies(magmar.id, 5))
            scanSeeing(tile(charmander, 2))
            val viewModel = viewModel()
            viewModel.onReviewRequested(complete = true)
            advanceUntilIdle()
            viewModel.onReplaceChanged(false)

            viewModel.onConfirm()
            advanceUntilIdle()

            collections.current().copiesOf(magmar.id) shouldBe 5
        }

    @Test
    fun `confirming keeps the known count of a card whose count was never visible`() =
        runTest(dispatcher) {
            collections.save(collections.current().withCopies(magmar.id, 7))
            scanSeeing(tile(magmar, null))
            val viewModel = viewModel()
            viewModel.onReviewRequested(complete = true)
            advanceUntilIdle()

            viewModel.onConfirm()
            advanceUntilIdle()

            collections.current().copiesOf(magmar.id) shouldBe 7
        }

    @Test
    fun `a count the player entered wins over the known one`() =
        runTest(dispatcher) {
            collections.save(collections.current().withCopies(magmar.id, 7))
            scanSeeing(tile(magmar, null))
            val viewModel = viewModel()
            viewModel.onReviewRequested(complete = true)
            advanceUntilIdle()
            viewModel.onAdjust(magmar.id, delta = 1)

            viewModel.onConfirm()
            advanceUntilIdle()

            collections.current().copiesOf(magmar.id) shouldBe 2
        }

    @Test
    fun `a scan that found far fewer cards than are owned does not replace the collection by default`() =
        runTest(dispatcher) {
            val owned = CardCollection.EMPTY.withCopies(charmander.id, 3).withCopies(magmar.id, 2)
            collections.save(owned)
            scanSeeing(tile(charmander, 3))
            val viewModel = viewModel()

            viewModel.onReviewRequested(complete = true)
            advanceUntilIdle()

            viewModel.state.value.run {
                replaceCollection shouldBe false
                looksIncomplete shouldBe true
                ownedBefore shouldBe 2
            }
        }

    @Test
    fun `a scan that found about as many cards as are owned may replace the collection`() =
        runTest(dispatcher) {
            collections.save(CardCollection.EMPTY.withCopies(charmander.id, 3).withCopies(magmar.id, 2))
            scanSeeing(tile(charmander, 3), tile(magmar, 2))
            val viewModel = viewModel()

            viewModel.onReviewRequested(complete = true)
            advanceUntilIdle()

            viewModel.state.value.replaceCollection shouldBe true
            viewModel.state.value.looksIncomplete shouldBe false
        }

    @Test
    fun `the first scan of an empty collection is never called incomplete`() =
        runTest(dispatcher) {
            scanSeeing(tile(charmander, 1))
            val viewModel = viewModel()

            viewModel.onReviewRequested(complete = true)
            advanceUntilIdle()

            viewModel.state.value.looksIncomplete shouldBe false
        }

    @Test
    fun `the player can still force a replacement after the warning`() =
        runTest(dispatcher) {
            collections.save(CardCollection.EMPTY.withCopies(charmander.id, 3).withCopies(magmar.id, 2))
            scanSeeing(tile(charmander, 3))
            val viewModel = viewModel()
            viewModel.onReviewRequested(complete = true)
            advanceUntilIdle()

            viewModel.onReplaceChanged(true)
            viewModel.onConfirm()
            advanceUntilIdle()

            collections.current().copiesOf(magmar.id) shouldBe 0
        }

    @Test
    fun `saving a scan makes the previous collection restorable`() =
        runTest(dispatcher) {
            collections.save(CardCollection.EMPTY.withCopies(magmar.id, 5))
            scanSeeing(tile(charmander, 2))
            val viewModel = viewModel()
            viewModel.state.test {
                awaitItem().backupAvailable shouldBe false
                viewModel.onReviewRequested(complete = true)
                advanceUntilIdle()
                viewModel.onReplaceChanged(true)
                viewModel.onConfirm()
                advanceUntilIdle()

                expectMostRecentItem().backupAvailable shouldBe true
            }
        }

    @Test
    fun `restoring brings the earlier collection back`() =
        runTest(dispatcher) {
            collections.save(CardCollection.EMPTY.withCopies(magmar.id, 5))
            scanSeeing(tile(charmander, 2))
            val viewModel = viewModel()
            viewModel.onReviewRequested(complete = true)
            advanceUntilIdle()
            viewModel.onReplaceChanged(true)
            viewModel.onConfirm()
            advanceUntilIdle()
            collections.current().copiesOf(magmar.id) shouldBe 0

            viewModel.onRestorePrevious()
            advanceUntilIdle()

            collections.current().copiesOf(magmar.id) shouldBe 5
            viewModel.state.value.notice shouldBe ScanNotice.RESTORED
        }

    @Test
    fun `restoring without a backup says so`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onRestorePrevious()
            advanceUntilIdle()

            viewModel.state.value.notice shouldBe ScanNotice.NOTHING_TO_RESTORE
        }

    @Test
    fun `auto-scroll needs the player's consent until they give it`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.state.value.autoScrollConsented shouldBe false

            viewModel.onAutoScrollConsent()
            advanceUntilIdle()

            viewModel.state.value.autoScrollConsented shouldBe true
        }

    @Test
    fun `remembers a consent that was given before`() =
        runTest(dispatcher) {
            val viewModel = viewModel(InMemoryConsentStore(setOf(Consent.AUTO_SCROLL)))

            advanceUntilIdle()

            viewModel.state.value.autoScrollConsented shouldBe true
        }
}
