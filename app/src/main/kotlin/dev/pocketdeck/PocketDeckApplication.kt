package dev.pocketdeck

import android.app.Application
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import dev.pocketdeck.adapter.capture.CaptureDependencies
import dev.pocketdeck.adapter.capture.CaptureDependenciesProvider
import dev.pocketdeck.application.scan.AutoScanService
import dev.pocketdeck.application.scan.GridLocator
import dev.pocketdeck.application.scan.GridScroller
import dev.pocketdeck.application.scan.ManualScanService
import dev.pocketdeck.application.scan.ObserveScanProgress
import dev.pocketdeck.application.scan.RunAutomaticScan
import dev.pocketdeck.application.scan.RunManualScan
import dev.pocketdeck.application.scan.ScanDiagnostics
import dev.pocketdeck.application.scan.ScreenCapturer
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

    @Inject
    lateinit var gridLocator: GridLocator

    @Inject
    lateinit var diagnostics: ScanDiagnostics

    override val captureDependencies: CaptureDependencies =
        object : CaptureDependencies {
            override val startCollectionScan get() = startScan
            override val observeScanProgress get() = observeProgress

            override fun manualScan(capturer: ScreenCapturer): RunManualScan =
                ManualScanService(capturer, submitFrame.get())

            override fun automaticScan(
                capturer: ScreenCapturer,
                scroller: GridScroller,
            ): RunAutomaticScan =
                AutoScanService(capturer, gridLocator, scroller, submitFrame.get(), diagnostics = diagnostics)
        }
}
