package dev.pocketdeck.application.scan

import dev.pocketdeck.application.Outcome

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

/** What one analysed picture contained. Counts are only readable once the game has faded its badges in. */
data class FrameAnalysis(
    val progress: ScanProgress,
    val tilesRecognized: Int,
    val countsRead: Int,
) {
    val countsVisible: Boolean get() =
        tilesRecognized > 0 &&
            countsRead * COUNT_SHARE_DEN >= tilesRecognized * COUNT_SHARE_NUM

    private companion object {
        // "Visible" means at least four out of five recognised tiles had a readable count.
        const val COUNT_SHARE_NUM = 4
        const val COUNT_SHARE_DEN = 5
    }
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

/** Timing and limits of the automatic scan; the defaults suit the game's own animations. */
data class AutoScanSettings(
    val pollMillis: Long = 250,
    /** How many identical pictures in a row count as "the screen is at rest". */
    val stablePolls: Int = 2,
    /** How long to wait for the count badges to fade in before scrolling on anyway. */
    val countWaitPolls: Int = 12,
    val afterScrollMillis: Long = 450,
    val swipeMillis: Long = 900,
    val rewindSwipeMillis: Long = 300,
    val overlapRows: Int = 1,
    val stallLimit: Int = 2,
    val maxScreens: Int = 400,
    val maxRewindSwipes: Int = 60,
)
