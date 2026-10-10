package dev.pocketdeck.application.scan

import dev.pocketdeck.application.Outcome
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

    /** A swipe that was sent, kept until the page after it has settled so that its real effect can be measured. */
    private class IssuedScroll(
        val before: ScreenFrame,
        val geometry: GridGeometry?,
        val commanded: Int,
        val target: Int,
    )

    /** Learns how far the list really travels per pixel of swipe, as it keeps gliding after the finger lifts. */
    private class SwipeGain {
        private var gain = 1.0

        /** Until a swipe has been measured, only a short probe is safe: a long one may skip rows unnoticed. */
        var calibrated = false
            private set

        fun distanceFor(target: Int) = max(1, (target / gain).roundToInt())

        fun learn(
            commanded: Int,
            travelled: Int,
        ) {
            if (commanded > 0 && travelled > 0) {
                gain = (travelled.toDouble() / commanded).coerceIn(MIN_GAIN, MAX_GAIN)
                calibrated = true
            }
        }

        /** The list moved so far that nothing of the old picture is left to measure with: swipe shorter from now on. */
        fun assumeFarther() {
            gain = (gain * UNMEASURABLE_GAIN_STEP).coerceAtMost(MAX_GAIN)
        }
    }

    override suspend fun invoke(): Outcome<AutoScanSummary, AutoScanFailure> {
        if (!showsCardGrid()) return Outcome.Failure(AutoScanFailure.NotOnCardList)
        rewindToTop()?.let { return Outcome.Failure(it) }
        var screens = 0
        var pages = 0
        var stalls = 0
        var previous: ScreenFrame? = null
        var issued: IssuedScroll? = null
        val gain = SwipeGain()
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
            val overshoot = issued?.let { measureOvershoot(it, frame, gain) }
            val gesture =
                if (overshoot != null) {
                    pageBack(frame, overshoot, gain)
                } else {
                    pageDown(frame, stalls, gain)
                }
            issued = if (overshoot == null) gesture.second else null
            if (!scroller.perform(gesture.first)) return Outcome.Failure(AutoScanFailure.GestureUnavailable)
            delay(settings.afterScrollMillis)
        }
    }

    /**
     * Looks for the card grid before the first swipe: for a moment at the normal pace (the list may still be loading), then once a
     * second for up to [AutoScanSettings.gridWaitMillis], because the player opens the game and My Cards themselves. The locator alone is not
     * trusted to say "no": it may miss a real grid (the scan then swipes by a default distance), so when it finds nothing the
     * recognizer gets the picture too, and only a picture without one recognised card counts as the wrong screen.
     */
    private suspend fun showsCardGrid(): Boolean {
        var waited = 0L
        while (waited <= settings.gridWaitMillis) {
            val frame = awaitFrame()
            if (locator.locate(frame) != null) return true
            val analysis = submit(frame)
            if (analysis is Outcome.Success && analysis.value.tilesRecognized > 0) return true
            val pause = if (waited < settings.gridQuickLookMillis) settings.pollMillis else settings.gridWaitPollMillis
            delay(pause)
            waited += pause
        }
        return false
    }

    /**
     * Learns from the last swipe and returns how far (in pixels) the list went past the planned page, if so far that
     * rows were skipped; those rows are never seen, so the list has to be brought back.
     */
    private fun measureOvershoot(
        scroll: IssuedScroll,
        frame: ScreenFrame,
        gain: SwipeGain,
    ): Int? {
        val geometry = scroll.geometry ?: return null
        val travelled = measureScrollShift(scroll.before, frame, geometry.firstRowTop, geometry.lastRowBottom)
        if (travelled == null) {
            gain.assumeFarther()
            return null
        }
        gain.learn(scroll.commanded, travelled)
        val excess = travelled - scroll.target
        return excess.takeIf { it > geometry.rowPitch / OVERSHOOT_DIVISOR }
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

    /**
     * Content moves up by about one page minus the overlap: the finger drags up from the bottom of the grid. When the
     * picture did not change after the previous scroll ([attempt] > 0) the swipe is varied (slower, shorter, from a
     * different column), so a swipe that something swallowed is not mistaken for the end of the list.
     */
    private fun pageDown(
        frame: ScreenFrame,
        attempt: Int,
        gain: SwipeGain,
    ): Pair<ScrollGesture, IssuedScroll?> {
        val geometry = locator.locate(frame)
        val target =
            geometry?.let { if (gain.calibrated) pageHeight(it) else it.rowPitch }
                ?: (frame.height / 2)
        val page = if (attempt == 0) gain.distanceFor(target) else target
        val gesture = swipeUp(frame, geometry, page, attempt)
        return gesture to IssuedScroll(frame, geometry, gesture.startY - gesture.endY, target)
    }

    private fun pageHeight(geometry: GridGeometry) =
        max(geometry.rowPitch, (geometry.visibleRows - settings.overlapRows) * geometry.rowPitch)

    private fun swipeUp(
        frame: ScreenFrame,
        geometry: GridGeometry?,
        page: Int,
        attempt: Int,
    ): ScrollGesture {
        val bottom = geometry?.lastRowBottom ?: (frame.height * FALLBACK_BOTTOM_PERCENT / PERCENT)
        val startY = min(bottom - margin(geometry), frame.height - 1)
        val centre = geometry?.centerX ?: (frame.width / 2)
        return when (attempt % VARIANTS) {
            0 -> {
                ScrollGesture(centre, startY, centre, max(startY - page, 0), settings.swipeMillis)
            }

            1 -> {
                ScrollGesture(centre, startY, centre, max(startY - page, 0), settings.swipeMillis * SLOW_FACTOR)
            }

            2 -> {
                ScrollGesture(centre, startY, centre, max(startY - page / 2, 0), settings.rewindSwipeMillis)
            }

            else -> {
                ScrollGesture(
                    frame.width / SIDE_DIVISOR,
                    startY,
                    frame.width / SIDE_DIVISOR,
                    max(startY - page, 0),
                    settings.swipeMillis,
                )
            }
        }
    }

    /** The list went further than planned: content moves back down by the excess. */
    private fun pageBack(
        frame: ScreenFrame,
        excess: Int,
        gain: SwipeGain,
    ): Pair<ScrollGesture, IssuedScroll?> = swipeDown(frame, gain.distanceFor(excess)) to null

    private fun pageUp(frame: ScreenFrame) =
        swipeDown(frame, locator.locate(frame)?.let { it.visibleRows * it.rowPitch } ?: (frame.height / 2))

    /** Content moves down: the finger drags down, ending at the bottom of the grid. */
    private fun swipeDown(
        frame: ScreenFrame,
        distance: Int,
    ): ScrollGesture {
        val geometry = locator.locate(frame)
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
        const val VARIANTS = 4
        const val SLOW_FACTOR = 2L
        const val SIDE_DIVISOR = 4
        const val FALLBACK_BOTTOM_PERCENT = 80
        const val PERCENT = 100
        const val MARGIN_DIVISOR = 10
        const val OVERSHOOT_DIVISOR = 2
        const val UNMEASURABLE_GAIN_STEP = 1.5
        const val MIN_GAIN = 0.3
        const val MAX_GAIN = 4.0
    }
}

class ManualScanService(
    private val capturer: ScreenCapturer,
    private val submit: SubmitScanFrame,
    private val settings: AutoScanSettings = AutoScanSettings(),
) : RunManualScan {
    override suspend fun invoke() {
        var last: ScreenFrame? = null
        var lastShowedCards = true
        while (true) {
            val frame = capturer.latestFrame()
            // A picture without cards is looked at again even when it does not change: that is how a still wrong screen
            // (the home screen, another game menu) is told from a list that is just resting.
            if (frame != null && (last == null || !frame.looksLike(last) || !lastShowedCards)) {
                val analysis = submit(frame)
                if (analysis !is Outcome.Success) return
                lastShowedCards = analysis.value.tilesRecognized > 0
                last = frame
            }
            delay(settings.pollMillis)
        }
    }
}
