package dev.pocketdeck.application.scan

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.scan.ScannedCard

/** Where the card grid sits in a frame, in pixels; enough to size and place a scroll gesture. */
data class GridGeometry(
    val firstRowTop: Int,
    val lastRowBottom: Int,
    val rowPitch: Int,
    val visibleRows: Int,
    val centerX: Int,
)

/** Outbound port: finds the card grid in a captured frame, or null when there is none. */
fun interface GridLocator {
    fun locate(frame: ScreenFrame): GridGeometry?
}

/** Outbound port: the most recent picture of the screen, or null while nothing has been captured yet. */
fun interface ScreenCapturer {
    suspend fun latestFrame(): ScreenFrame?
}

/** A drag across the screen from the start to the end point; the content follows the finger. */
data class ScrollGesture(
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val durationMillis: Long,
)

/** Outbound port: performs a drag in the game. Returns false when the gesture could not be carried out. */
fun interface GridScroller {
    suspend fun perform(gesture: ScrollGesture): Boolean
}

/**
 * What one analysed picture contained. The game shows count badges only once the list rests and fades them in tile by
 * tile, so a page is only complete when every tile that can show a badge does.
 */
data class FrameAnalysis(
    val progress: ScanProgress,
    val tilesRecognized: Int,
    /** Tiles whose badge should be readable (tiles under the navigation bar's fade are left out). */
    val countsExpected: Int,
    /** Of the expected ones, how many have a readable count already. */
    val countsRead: Int,
) {
    val countsVisible: Boolean get() = countsRead >= countsExpected
}

data class AutoScanSummary(
    val screens: Int,
)

sealed interface AutoScanFailure {
    /** The game could not be scrolled (for instance the accessibility service is not switched on). */
    data object GestureUnavailable : AutoScanFailure

    data object TooManyScreens : AutoScanFailure

    data object ScanNotRunning : AutoScanFailure
}

/** Inbound port: scroll the game's card grid from the top to the bottom while the running scan reads it. */
fun interface RunAutomaticScan {
    suspend operator fun invoke(): Outcome<AutoScanSummary, AutoScanFailure>
}

/** Inbound port: feed the running scan with whatever the player scrolls past, until the scan ends. */
fun interface RunManualScan {
    suspend operator fun invoke()
}

/** What the auto-scroller saw on one page when it moved on; for diagnosing scans that miss counts. */
data class PageReport(
    val page: Int,
    val analysis: FrameAnalysis?,
    val waitedPolls: Int,
    /** True when it moved on although some expected counts were still missing. */
    val timedOut: Boolean,
)

/** Outbound port: receives what the scanner saw, to find out where counts or cards go missing. */
interface ScanDiagnostics {
    /** Called for every page the auto-scroller settles on. */
    fun pageSettled(report: PageReport)

    /** Called when a scan ends, with everything it found and why each card is (not) trusted. */
    fun scanFinished(review: List<ScannedCard>) = Unit

    companion object {
        val NONE =
            object : ScanDiagnostics {
                override fun pageSettled(report: PageReport) = Unit
            }
    }
}

/** Timing and limits of the automatic scan; the defaults suit the game's own animations. */
data class AutoScanSettings(
    val pollMillis: Long = 250,
    /** How many identical pictures in a row count as "the screen is at rest". */
    val stablePolls: Int = 2,
    /**
     * How long to wait for the count badges before scrolling on anyway. The game fades them in tile by tile once the
     * list has stopped, which took 3-4 seconds in practice, on top of the time the list is still coasting.
     */
    val countWaitPolls: Int = 32,
    val afterScrollMillis: Long = 450,
    val swipeMillis: Long = 900,
    val rewindSwipeMillis: Long = 300,
    val overlapRows: Int = 1,
    /** How many scrolls in a row may leave the picture unchanged before the list is considered ended. */
    val stallLimit: Int = 4,
    val maxScreens: Int = 400,
    val maxRewindSwipes: Int = 60,
)
