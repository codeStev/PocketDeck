package dev.pocketdeck.adapter.ui.scan

import app.cash.turbine.test
import dev.pocketdeck.application.catalog.ObserveCardCatalogService
import dev.pocketdeck.application.fakes.FakeCardRecognizer
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
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
    private val frame = ScreenFrame(1, 1, intArrayOf(0))

    @BeforeEach
    fun installMainDispatcher() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun removeMainDispatcher() = Dispatchers.resetMain()

    private fun viewModel() =
        ScanViewModel(
            observeProgress = ObserveScanProgressService(sessions),
            finishScan = FinishCollectionScanService(sessions),
            confirmScan = ConfirmScanResultsService(catalogs, collections),
            observeCatalog = ObserveCardCatalogService(catalogs),
            language = Language.ENGLISH,
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

            viewModel.onReviewRequested()
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

            viewModel.onReviewRequested()
            advanceUntilIdle()

            viewModel.state.value.phase shouldBe ScanPhase.IDLE
            viewModel.state.value.notice shouldBe ScanNotice.NOTHING_TO_REVIEW
        }

    @Test
    fun `adjusting a card changes its count and marks it as checked`() =
        runTest(dispatcher) {
            scanSeeing(tile(magmar, null))
            val viewModel = viewModel()
            viewModel.onReviewRequested()
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
            viewModel.onReviewRequested()
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
            viewModel.onReviewRequested()
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
            viewModel.onReviewRequested()
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
            viewModel.onReviewRequested()
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
            viewModel.onReviewRequested()
            advanceUntilIdle()
            viewModel.onConfirm()
            advanceUntilIdle()

            viewModel.onDone()

            viewModel.state.value.phase shouldBe ScanPhase.IDLE
        }
}
