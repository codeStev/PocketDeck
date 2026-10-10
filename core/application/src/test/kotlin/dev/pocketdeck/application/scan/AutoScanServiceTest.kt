package dev.pocketdeck.application.scan

import dev.pocketdeck.application.Outcome
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** A scrollable list of card rows seen through a small viewport, driven by the same gestures the real scroller sends. */
private const val BADGE_TINT = 5_000_000

private class FakeGridWorld(
    private val contentHeight: Int,
    val viewportHeight: Int = 40,
    val rowPitch: Int = 10,
    startOffset: Int = 0,
    private val pollsUntilCountsAppear: Int = 3,
    private val gesturesWork: Boolean = true,
    private val ignoredSwipeMillis: Long? = null,
    /** How much further than asked the list travels (it keeps gliding after the finger lifts). */
    private val flingFactor: Double = 1.0,
) {
    var offset = startOffset
        private set
    private var pollsSinceMove = 0
    val gestures = mutableListOf<ScrollGesture>()
    val submittedOffsets = mutableListOf<Int>()
    val offsetsWhenScrolling = mutableListOf<Boolean>() // whether counts were visible at each scroll

    private val maxOffset get() = contentHeight - viewportHeight
    private val countsVisible get() = pollsSinceMove >= pollsUntilCountsAppear

    val capturer =
        ScreenCapturer {
            pollsSinceMove++
            frame()
        }

    val locator =
        GridLocator {
            GridGeometry(
                firstRowTop = 0,
                lastRowBottom = viewportHeight,
                rowPitch = rowPitch,
                visibleRows =
                    viewportHeight / rowPitch,
                centerX = 10,
            )
        }

    val scroller =
        GridScroller { gesture ->
            gestures += gesture
            offsetsWhenScrolling += countsVisible
            if (!gesturesWork) return@GridScroller false
            val swallowed = gesture.durationMillis == ignoredSwipeMillis && gesture.startY > gesture.endY
            if (swallowed) return@GridScroller true // reported as done, but the list does not move
            val moved = ((gesture.startY - gesture.endY) * flingFactor).toInt()
            offset = (offset + moved).coerceIn(0, maxOffset)
            pollsSinceMove = 0
            true
        }

    val submit =
        SubmitScanFrame {
            submittedOffsets += offset
            val tiles = viewportHeight / rowPitch
            Outcome.Success(
                FrameAnalysis(ScanProgress(submittedOffsets.size, 0, 0), tiles, tiles, if (countsVisible) tiles else 0),
            )
        }

    /**
     * Row y of the frame shows content row (offset + y), so a different offset gives a different picture. Count badges
     * appearing also changes the picture, as in the real game.
     */
    private fun frame(): ScreenFrame {
        val badges = if (countsVisible) BADGE_TINT else 0
        val pixels =
            IntArray(20 * viewportHeight) {
                0xFF000000.toInt() or Random((offset + it / 20) * 7919L + badges).nextInt(0x1000000)
            }
        return ScreenFrame(20, viewportHeight, pixels)
    }

    /** Content rows (of [rowPitch]) that have been in view in at least one submitted frame. */
    fun rowsSeen(): Set<Int> =
        submittedOffsets.flatMap { off -> (off until off + viewportHeight).map { it / rowPitch } }.toSet()
}

@OptIn(ExperimentalCoroutinesApi::class)
class AutoScanServiceTest {
    private fun service(world: FakeGridWorld) =
        AutoScanService(world.capturer, world.locator, world.scroller, world.submit)

    private val noCards = SubmitScanFrame { Outcome.Success(FrameAnalysis(ScanProgress(1, 0, 0), 0, 0, 0)) }

    @Test
    fun `does not swipe at all when the screen shows no card grid`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200)

            val outcome = AutoScanService(world.capturer, { null }, world.scroller, noCards)()

            outcome shouldBe Outcome.Failure(AutoScanFailure.NotOnCardList)
            world.gestures shouldBe emptyList()
        }

    @Test
    fun `waits for the player to open My Cards instead of giving up after a couple of seconds`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200)
            // The player taps Start, the game opens on its landing page and they navigate to My Cards: far longer than two seconds.
            var looks = 0
            val locatesAfterAWhile = GridLocator { frame -> if (++looks > 60) world.locator.locate(frame) else null }

            val outcome = AutoScanService(world.capturer, locatesAfterAWhile, world.scroller, noCards)()

            (outcome is Outcome.Success) shouldBe true
            looks shouldBeGreaterThan 60
        }

    @Test
    fun `looks for the grid once a second after the first two seconds`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200)
            val settings = AutoScanSettings()
            var looks = 0
            val never =
                GridLocator {
                    looks++
                    null
                }

            AutoScanService(world.capturer, never, world.scroller, noCards, settings)()

            // 2 s at 250 ms, then 1 s steps up to the wait of 10 minutes: about 600 looks, not 2,400 at the normal pace.
            val expected =
                (settings.gridQuickLookMillis / settings.pollMillis) +
                    ((settings.gridWaitMillis - settings.gridQuickLookMillis) / settings.gridWaitPollMillis)
            (looks.toLong() > expected - 3) shouldBe true
            (looks.toLong() < expected + 5) shouldBe true
        }

    @Test
    fun `gives up on a screen that is never My Cards once the wait is over`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200)
            val impatient = AutoScanSettings(gridWaitMillis = 10_000)

            val outcome = AutoScanService(world.capturer, { null }, world.scroller, noCards, impatient)()

            outcome shouldBe Outcome.Failure(AutoScanFailure.NotOnCardList)
            world.gestures shouldBe emptyList()
        }

    @Test
    fun `waits a moment for a grid that is still loading`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200)
            var looks = 0
            val locatesLate = GridLocator { frame -> if (++looks > 3) world.locator.locate(frame) else null }

            val outcome = AutoScanService(world.capturer, locatesLate, world.scroller, noCards)()

            (outcome is Outcome.Success) shouldBe true
        }

    @Test
    fun `scans a whole list from top to bottom with every row seen`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200)

            val outcome = service(world)()

            (outcome is Outcome.Success) shouldBe true
            world.rowsSeen() shouldBe (0 until 20).toSet()
        }

    @Test
    fun `sees every row although the list glides further than the swipe asked`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 400, flingFactor = 1.8)

            service(world)()

            world.rowsSeen() shouldBe (0 until 40).toSet()
        }

    @Test
    fun `sees every row although the list falls short of the swipe`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 400, flingFactor = 0.6)

            service(world)()

            world.rowsSeen() shouldBe (0 until 40).toSet()
        }

    @Test
    fun `each scroll leaves one row of overlap`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200)

            service(world)()

            val downwards = world.gestures.filter { it.startY > it.endY }
            // The first swipe is a one-row probe; then 4 visible rows of 10 px: moving by 3 rows (30 px) keeps the
            // last visible row in view.
            downwards.first().let { it.startY - it.endY } shouldBe 10
            downwards[1].let { it.startY - it.endY } shouldBe 30
        }

    @Test
    fun `starts by returning to the top when the list is scrolled down`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200, startOffset = 100)

            service(world)()

            world.submittedOffsets.first() shouldBe 0
            world.rowsSeen() shouldBe (0 until 20).toSet()
        }

    @Test
    fun `ends with a summary once the list stops moving`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 100)

            val outcome = service(world)()

            (outcome as Outcome.Success).value.screens shouldBe world.submittedOffsets.distinct().size
            world.offset shouldBe 60
        }

    @Test
    fun `tries the end of the list only as often as the stall limit allows`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 100)
            val settings = AutoScanSettings()

            service(world)()

            // The probe and two page scrolls move the list (0 -> 10 -> 40 -> 60), then it does not move any more: it is
            // tried stallLimit more times before the scan is declared finished.
            world.gestures.count { it.startY > it.endY } shouldBe 3 + settings.stallLimit
        }

    @Test
    fun `does not mistake blocked swipes for the end of the list`() =
        runTest {
            // The usual swipe is swallowed (something blocks it) but a differently shaped one gets through.
            val world = FakeGridWorld(contentHeight = 200, ignoredSwipeMillis = AutoScanSettings().swipeMillis)

            val outcome = service(world)()

            (outcome is Outcome.Success) shouldBe true
            world.rowsSeen() shouldBe (0 until 20).toSet()
        }

    @Test
    fun `tries differently shaped swipes before giving up`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 100)

            service(world)()

            val atTheEnd = world.gestures.filter { it.startY > it.endY }.drop(2)
            atTheEnd.map { it.durationMillis }.distinct().size shouldBeGreaterThan 1
        }

    @Test
    fun `waits for the counts to appear before scrolling on`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200, pollsUntilCountsAppear = 4)

            service(world)()

            world.gestures.indices
                .filter { world.gestures[it].startY > world.gestures[it].endY }
                .all { world.offsetsWhenScrolling[it] } shouldBe true
        }

    @Test
    fun `is patient enough for badges that fade in over several seconds`() =
        runTest {
            // The game fades the count badges in tile by tile, which takes 3-4 seconds after the list stops.
            val world = FakeGridWorld(contentHeight = 120, pollsUntilCountsAppear = 20)

            service(world)()

            world.gestures.indices
                .filter { world.gestures[it].startY > world.gestures[it].endY }
                .all { world.offsetsWhenScrolling[it] } shouldBe true
        }

    @Test
    fun `scrolls on after a limit when the counts never show up`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 120, pollsUntilCountsAppear = Int.MAX_VALUE)

            val outcome = service(world)()

            (outcome is Outcome.Success) shouldBe true
            world.rowsSeen() shouldBe (0 until 12).toSet()
        }

    @Test
    fun `reports a gesture that could not be performed`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200, gesturesWork = false)

            service(world)() shouldBe Outcome.Failure(AutoScanFailure.GestureUnavailable)
        }

    @Test
    fun `gives up on a list that never ends`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 1_000_000)
            val limited =
                AutoScanService(
                    world.capturer,
                    world.locator,
                    world.scroller,
                    world.submit,
                    AutoScanSettings(maxScreens = 5),
                )

            limited() shouldBe Outcome.Failure(AutoScanFailure.TooManyScreens)
        }

    @Test
    fun `falls back to half a screen when the grid cannot be located`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200)
            val blind = AutoScanService(world.capturer, { null }, world.scroller, world.submit)

            blind()

            world.gestures.first { it.startY > it.endY }.let { it.startY - it.endY } shouldBe world.viewportHeight / 2
        }

    @Test
    fun `stops when the scan is cancelled`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 1_000_000)
            val job = async { service(world)() }
            advanceTimeBy(5_000)
            runCurrent()

            job.cancel()

            (runCatching { job.await() }.exceptionOrNull() is CancellationException) shouldBe true
        }

    @Test
    fun `stops when the scan session has ended elsewhere`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200)
            val ended =
                AutoScanService(
                    world.capturer,
                    world.locator,
                    world.scroller,
                    { Outcome.Failure(ScanFailure.NoActiveScan) },
                )

            ended() shouldBe Outcome.Failure(AutoScanFailure.ScanNotRunning)
        }

    private class RecordingDiagnostics : ScanDiagnostics {
        val pages = mutableListOf<PageReport>()

        override fun pageSettled(report: PageReport) {
            pages += report
        }
    }

    @Test
    fun `reports every settled page with how long it waited`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200, pollsUntilCountsAppear = 4)
            val diagnostics = RecordingDiagnostics()

            AutoScanService(world.capturer, world.locator, world.scroller, world.submit, diagnostics = diagnostics)()

            diagnostics.pages.isNotEmpty() shouldBe true
            diagnostics.pages.map { it.page } shouldBe (1..diagnostics.pages.size).toList()
            diagnostics.pages.all { it.analysis?.countsVisible == true && !it.timedOut } shouldBe true
        }

    @Test
    fun `flags the pages that were left although their counts never showed up`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 120, pollsUntilCountsAppear = Int.MAX_VALUE)
            val diagnostics = RecordingDiagnostics()

            AutoScanService(world.capturer, world.locator, world.scroller, world.submit, diagnostics = diagnostics)()

            diagnostics.pages.all { it.timedOut } shouldBe true
            diagnostics.pages
                .first()
                .analysis
                ?.countsExpected shouldBe 4
            diagnostics.pages
                .first()
                .analysis
                ?.countsRead shouldBe 0
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ManualScanWrongScreenTest {
    private val frame = ScreenFrame(2, 2, IntArray(4) { it })

    private fun TestScope.run(tiles: Int): Int {
        var submitted = 0
        val capturer = ScreenCapturer { frame }
        val submit =
            SubmitScanFrame {
                submitted++
                Outcome.Success(FrameAnalysis(ScanProgress(submitted, 0, 0), tiles, tiles, tiles))
            }
        val job = launch { ManualScanService(capturer, submit)() }
        advanceTimeBy(3_000)
        job.cancel()
        return submitted
    }

    @Test
    fun `a still picture without cards keeps being looked at, so the warning can appear`() =
        runTest {
            (run(tiles = 0) > 5) shouldBe true
        }

    @Test
    fun `a still picture with cards is analysed once`() =
        runTest {
            run(tiles = 12) shouldBe 1
        }
}
