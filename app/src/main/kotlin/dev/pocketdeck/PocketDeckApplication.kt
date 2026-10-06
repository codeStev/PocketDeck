package dev.pocketdeck

import android.app.Application
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import dev.pocketdeck.adapter.capture.CaptureDependencies
import dev.pocketdeck.adapter.capture.CaptureDependenciesProvider
import dev.pocketdeck.application.scan.ObserveScanProgress
import dev.pocketdeck.application.scan.StartCollectionScan
import dev.pocketdeck.application.scan.SubmitScanFrame
import javax.inject.Inject

@HiltAndroidApp
class PocketDeckApplication :
    Application(),
    CaptureDependenciesProvider {
    @Inject
    lateinit var startScan: StartCollectionScan

    // Lazy: building the recognizer loads the card index, which should not slow down the app's start.
    @Inject
    lateinit var submitFrame: Lazy<SubmitScanFrame>

    @Inject
    lateinit var observeProgress: ObserveScanProgress

    override val captureDependencies: CaptureDependencies =
        object : CaptureDependencies {
            override val startCollectionScan get() = startScan
            override val submitScanFrame get() = submitFrame.get()
            override val observeScanProgress get() = observeProgress
        }
}
