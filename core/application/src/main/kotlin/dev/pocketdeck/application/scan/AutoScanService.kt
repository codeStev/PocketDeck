package dev.pocketdeck.application.scan

import dev.pocketdeck.application.Outcome
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * Scrolls the game's card grid by itself: back to the top first, then one page at a time with a row of overlap,
 * waiting each time until the picture is at rest and the count badges have faded in. It ends when the list stops
 * moving. All platform specifics (capturing, gestures) are behind ports.
 */
class AutoScanService(
    private val capturer: ScreenCapturer,
    private val locator: GridLocator,
    private val scroller: GridScroller,
    private val submit: SubmitScanFrame,
    private val settings: AutoScanSettings = AutoScanSettings(),
    private val diagnostics: ScanDiagnostics = ScanDiagnostics.NONE,
) : RunAutomaticScan {
    private sealed interface Settled {
        class Ready(
            val frame: ScreenFrame,
            val analysis: FrameAnalysis?,
            val waitedPolls: Int,
        ) : Settled

        data object ScanEnded : Settled
    }

    override suspend fun invoke(): Outcome<AutoScanSummary, AutoScanFailure> {
        rewindToTop()?.let { return Outcome.Failure(it) }
        var screens = 0
        var pages = 0
        var stalls = 0
        var previous: ScreenFrame? = null
        while (true) {
            val frame =
                when (val settled = settle()) {
                    is Settled.ScanEnded -> return Outcome.Failure(AutoScanFailure.ScanNotRunning)
                    is Settled.Ready -> settled.also { report(++pages, it) }.frame
                }
            if (previous != null && frame.looksLike(previous)) stalls++ else stalls = 0
            if (stalls >= settings.stallLimit) return Outcome.Success(AutoScanSummary(screens))
            if (previous == null || !frame.looksLike(previous)) screens++
            if (screens > settings.maxScreens) return Outcome.Failure(AutoScanFailure.TooManyScreens)
            previous = frame
            if (!scroller.perform(pageDown(frame))) return Outcome.Failure(AutoScanFailure.GestureUnavailable)
            delay(settings.afterScrollMillis)
        }
    }

    private fun report(
        page: Int,
        settled: Settled.Ready,
    ) = diagnostics.pageSettled(
        PageReport(page, settled.analysis, settled.waitedPolls, timedOut = settled.analysis?.countsVisible != true),
    )

    /** Swipes down until the picture stops changing. Returns a failure only if swiping is impossible. */
    private suspend fun rewindToTop(): AutoScanFailure? {
        repeat(settings.maxRewindSwipes) {
            val before = awaitFrame()
            val gesture = pageUp(before)
            if (!scroller.perform(gesture)) return AutoScanFailure.GestureUnavailable
            delay(settings.afterScrollMillis)
            if (awaitFrame().looksLike(before)) return null
        }
        return null
    }

    /** Waits until the picture rests and its counts are readable (or the wait runs out), analysing every new picture. */
    private suspend fun settle(): Settled {
        var last: ScreenFrame? = null
        var analysis: FrameAnalysis? = null
        var stable = 0
        var polls = 0
        while (true) {
            val frame = awaitFrame()
            if (last == null || !frame.looksLike(last)) {
                stable = 0
                when (val result = submit(frame)) {
                    is Outcome.Failure -> return Settled.ScanEnded
                    is Outcome.Success -> analysis = result.value
                }
            } else {
                stable++
            }
            last = frame
            val countsReady = analysis?.countsVisible == true
            val waitedLongEnough = polls >= settings.countWaitPolls
            if (stable >= settings.stablePolls &&
                (countsReady || waitedLongEnough)
            ) {
                return Settled.Ready(frame, analysis, polls)
            }
            if (polls >= settings.countWaitPolls * GIVE_UP_FACTOR) return Settled.Ready(frame, analysis, polls)
            polls++
            delay(settings.pollMillis)
        }
    }

    private suspend fun awaitFrame(): ScreenFrame {
        while (true) {
            capturer.latestFrame()?.let { return it }
            delay(settings.pollMillis)
        }
    }

    /** Content moves up by about one page minus the overlap: the finger drags up from the bottom of the grid. */
    private fun pageDown(frame: ScreenFrame): ScrollGesture {
        val geometry = locator.locate(frame)
        val distance =
            geometry?.let { max(it.rowPitch, (it.visibleRows - settings.overlapRows) * it.rowPitch) }
                ?: (frame.height / 2)
        val bottom = geometry?.lastRowBottom ?: (frame.height * FALLBACK_BOTTOM_PERCENT / PERCENT)
        val startY = min(bottom - margin(geometry), frame.height - 1)
        val x = geometry?.centerX ?: (frame.width / 2)
        return ScrollGesture(x, startY, x, max(startY - distance, 0), settings.swipeMillis)
    }

    /** Content moves down by a page: the finger drags down, ending at the bottom of the grid. */
    private fun pageUp(frame: ScreenFrame): ScrollGesture {
        val geometry = locator.locate(frame)
        val distance = geometry?.let { it.visibleRows * it.rowPitch } ?: (frame.height / 2)
        val endY =
            min(
                (geometry?.lastRowBottom ?: (frame.height * FALLBACK_BOTTOM_PERCENT / PERCENT)) - margin(geometry),
                frame.height - 1,
            )
        val x = geometry?.centerX ?: (frame.width / 2)
        return ScrollGesture(x, max(endY - distance, 0), x, endY, settings.rewindSwipeMillis)
    }

    private fun margin(geometry: GridGeometry?) = (geometry?.rowPitch ?: 0) / MARGIN_DIVISOR

    private companion object {
        const val GIVE_UP_FACTOR = 3
        const val FALLBACK_BOTTOM_PERCENT = 80
        const val PERCENT = 100
        const val MARGIN_DIVISOR = 10
    }
}

class ManualScanService(
    private val capturer: ScreenCapturer,
    private val submit: SubmitScanFrame,
    private val settings: AutoScanSettings = AutoScanSettings(),
) : RunManualScan {
    override suspend fun invoke() {
        var last: ScreenFrame? = null
        while (true) {
            val frame = capturer.latestFrame()
            if (frame != null && (last == null || !frame.looksLike(last))) {
                if (submit(frame) is Outcome.Failure) return
                last = frame
            }
            delay(settings.pollMillis)
        }
    }
}
