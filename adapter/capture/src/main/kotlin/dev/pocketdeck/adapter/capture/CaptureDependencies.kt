package dev.pocketdeck.adapter.capture

import dev.pocketdeck.application.scan.GridScroller
import dev.pocketdeck.application.scan.ObserveScanProgress
import dev.pocketdeck.application.scan.RunAutomaticScan
import dev.pocketdeck.application.scan.RunManualScan
import dev.pocketdeck.application.scan.ScreenCapturer
import dev.pocketdeck.application.scan.StartCollectionScan

/**
 * What this adapter needs from the application, supplied by it so the adapter needs no DI framework. The scan loops are
 * built here from the adapter's own capturer and scroller, because only the adapter can provide those.
 */
interface CaptureDependencies {
    val startCollectionScan: StartCollectionScan
    val observeScanProgress: ObserveScanProgress

    fun manualScan(capturer: ScreenCapturer): RunManualScan

    fun automaticScan(
        capturer: ScreenCapturer,
        scroller: GridScroller,
    ): RunAutomaticScan
}

/** Implemented by the `Application` class that wires the app together. */
interface CaptureDependenciesProvider {
    val captureDependencies: CaptureDependencies
}
