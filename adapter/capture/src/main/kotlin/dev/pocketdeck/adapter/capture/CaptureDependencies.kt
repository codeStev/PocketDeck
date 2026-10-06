package dev.pocketdeck.adapter.capture

import dev.pocketdeck.application.scan.ObserveScanProgress
import dev.pocketdeck.application.scan.StartCollectionScan
import dev.pocketdeck.application.scan.SubmitScanFrame

/** The inbound ports this adapter drives; supplied by the application so the adapter needs no DI framework. */
interface CaptureDependencies {
    val startCollectionScan: StartCollectionScan
    val submitScanFrame: SubmitScanFrame
    val observeScanProgress: ObserveScanProgress
}

/** Implemented by the `Application` class that wires the app together. */
interface CaptureDependenciesProvider {
    val captureDependencies: CaptureDependencies
}
