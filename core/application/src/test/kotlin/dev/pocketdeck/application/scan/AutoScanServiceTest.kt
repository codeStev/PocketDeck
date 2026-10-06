package dev.pocketdeck.application.scan

import dev.pocketdeck.application.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** A scrollable list of card rows seen through a small viewport, driven by the same gestures the real scroller sends. */
private class FakeGridWorld(
    private val contentHeight: Int,
    val viewportHeight: Int = 40,
    val rowPitch: Int = 10,
    startOffset: Int = 0,
    private val pollsUntilCountsAppear: Int = 3,
    private val gesturesWork: Boolean = true,
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
            val moved = (gesture.startY - gesture.endY)
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

    /** Row y of the frame shows content row (offset + y), so a different offset gives a different picture. */
    private fun frame() =
        ScreenFrame(
            20,
            viewportHeight,
            IntArray(20 * viewportHeight) {
                0xFF000000.toInt() or
                    ((offset + it / 20) * 7919 % 0xFFFFFF)
            },
        )

    /** Content rows (of [rowPitch]) that have been in view in at least one submitted frame. */
    fun rowsSeen(): Set<Int> =
        submittedOffsets.flatMap { off -> (off until off + viewportHeight).map { it / rowPitch } }.toSet()
}

@OptIn(ExperimentalCoroutinesApi::class)
class AutoScanServiceTest {
    private fun service(world: FakeGridWorld) =
        AutoScanService(world.capturer, world.locator, world.scroller, world.submit)

    @Test
    fun `scans a whole list from top to bottom with every row seen`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200)

            val outcome = service(world)()

            (outcome is Outcome.Success) shouldBe true
            world.rowsSeen() shouldBe (0 until 20).toSet()
        }

    @Test
    fun `each scroll leaves one row of overlap`() =
        runTest {
            val world = FakeGridWorld(contentHeight = 200)

            service(world)()

            val downwards = world.gestures.filter { it.startY > it.endY }
            // 4 visible rows of 10 px: moving by 3 rows (30 px) keeps the last visible row in view.
            downwards.first().let { it.startY - it.endY } shouldBe 30
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

            service(world)()

            // Two scrolls that move (0 -> 30 -> 60), then the list does not move any more: it is tried exactly
            // twice more (the default stall limit) before the scan is declared finished.
            world.gestures.count { it.startY > it.endY } shouldBe 4
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
}
