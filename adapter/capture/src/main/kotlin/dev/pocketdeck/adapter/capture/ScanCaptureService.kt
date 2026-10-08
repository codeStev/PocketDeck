package dev.pocketdeck.adapter.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.scan.AutoScanFailure
import dev.pocketdeck.application.scan.ScanProgress
import dev.pocketdeck.application.scan.ScreenCapturer
import dev.pocketdeck.application.scan.ScreenFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Holds the screen-capture session while a scan runs and starts the scan loop (manual or automatic). All scan logic
 * lives in the application layer; this class only moves pixels, gestures and the floating button.
 */
class ScanCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var session: CaptureSession? = null
    private var overlay: ScanOverlay? = null
    private var automatic = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ScanCapture.ACTION_START -> start(intent)
            ScanCapture.ACTION_STOP -> finish(openReview = true)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        finish(openReview = false)
        scope.cancel()
        super.onDestroy()
    }

    private fun start(intent: Intent) {
        if (session != null) return
        // Android requires the foreground notification before the projection is created.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        val resultData = IntentCompat.getParcelableExtra(intent, ScanCapture.EXTRA_RESULT_DATA, Intent::class.java)
        val resultCode = intent.getIntExtra(ScanCapture.EXTRA_RESULT_CODE, 0)
        if (resultData == null) {
            stopSelf()
            return
        }
        automatic = intent.getBooleanExtra(ScanCapture.EXTRA_AUTOMATIC, false)
        val dependencies = (application as CaptureDependenciesProvider).captureDependencies
        session = openSession(resultCode, resultData)
        dependencies.startCollectionScan()
        overlay = ScanOverlay(this, onStop = { finish(openReview = true) }).also(ScanOverlay::show)
        scope.launch {
            dependencies.observeScanProgress().collect { progress ->
                overlay?.update(overlayText(progress))
                if (progress != null &&
                    progress.framesProcessed % LOG_EVERY_FRAMES == 0
                ) {
                    Log.i(TAG, "Scan progress: $progress")
                }
            }
        }
        scope.launch { runScan(dependencies) }
    }

    private fun openSession(
        resultCode: Int,
        resultData: Intent,
    ): CaptureSession {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val granted = manager.getMediaProjection(resultCode, resultData)
        val projection = checkNotNull(granted) { "Screen capture was not granted" }
        // Android 14 insists on a callback being registered before a virtual display is created.
        projection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() = finish(openReview = false)
            },
            Handler(Looper.getMainLooper()),
        )
        val size = ScreenSize.of(this)
        val reader = ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, READER_BUFFERS)
        val display =
            checkNotNull(
                projection.createVirtualDisplay(
                    DISPLAY_NAME,
                    size.width,
                    size.height,
                    size.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface,
                    null,
                    null,
                ),
            ) { "The virtual display for screen capture could not be created" }
        return CaptureSession(projection, reader, display)
    }

    private suspend fun runScan(dependencies: CaptureDependencies) {
        val capturer = ScreenCapturer { session?.latestFrame() }
        try {
            if (automatic) {
                when (val outcome = dependencies.automaticScan(capturer, AccessibilityGridScroller())()) {
                    is Outcome.Success -> finish(openReview = true, complete = true)
                    is Outcome.Failure -> endAutomaticScan(outcome.reason)
                }
            } else {
                dependencies.manualScan(capturer)()
                finish(openReview = false)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.e(TAG, "The scan stopped unexpectedly", failure)
            finish(openReview = true)
        }
    }

    private fun overlayText(progress: ScanProgress?) =
        if (progress?.screenLooksWrong ==
            true
        ) {
            getString(R.string.scan_wrong_screen)
        } else {
            ProgressText.of(progress, automatic)
        }

    private suspend fun endAutomaticScan(reason: AutoScanFailure) {
        when (reason) {
            AutoScanFailure.GestureUnavailable -> overlay?.update(getString(R.string.scan_auto_unavailable))
            AutoScanFailure.TooManyScreens -> overlay?.update(getString(R.string.scan_auto_too_long))
            AutoScanFailure.NotOnCardList -> overlay?.update(getString(R.string.scan_wrong_screen))
            AutoScanFailure.ScanNotRunning -> Unit
        }
        if (reason != AutoScanFailure.ScanNotRunning) delay(FAILURE_NOTICE_MILLIS)
        // Whatever was found is still worth checking, so the player gets the review in every case.
        finish(openReview = reason != AutoScanFailure.ScanNotRunning)
    }

    private fun finish(
        openReview: Boolean,
        complete: Boolean = false,
    ) {
        val current = session ?: return
        session = null
        overlay?.remove()
        overlay = null
        current.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (openReview) openReviewScreen(complete)
        stopSelf()
    }

    private fun openReviewScreen(complete: Boolean) {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.action = ScanCapture.ACTION_REVIEW
        launch.putExtra(ScanCapture.EXTRA_COMPLETE, complete)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(launch)
    }

    private fun notification(): Notification {
        val name = getString(R.string.scan_channel_name)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_scan)
            .setContentTitle(getString(R.string.scan_notification_title))
            .setContentText(getString(R.string.scan_notification_text))
            .setColor(ACCENT_COLOR)
            .setOngoing(true)
            .build()
    }

    private class CaptureSession(
        private val projection: MediaProjection,
        private val reader: ImageReader,
        private val display: VirtualDisplay,
    ) {
        private var last: ScreenFrame? = null

        /**
         * The newest picture. Android only delivers an image when the screen changes, so while the screen rests the
         * previous picture is the current one.
         */
        fun latestFrame(): ScreenFrame? {
            reader.acquireLatestImage()?.use { image ->
                val plane = image.planes[0]
                last = RgbaFrameConverter.toScreenFrame(plane.buffer, image.width, image.height, plane.rowStride)
            }
            return last
        }

        fun release() {
            display.release()
            reader.close()
            projection.stop()
        }
    }

    private companion object {
        const val TAG = "PocketDeckScan"
        const val NOTIFICATION_ID = 1

        /** The app's accent (Signal brand); this adapter may not depend on the design system. */
        const val ACCENT_COLOR = 0xFFCC3518.toInt()
        const val CHANNEL_ID = "scan"
        const val DISPLAY_NAME = "pocketdeck-scan"
        const val READER_BUFFERS = 2
        const val FAILURE_NOTICE_MILLIS = 2500L
        const val LOG_EVERY_FRAMES = 10
    }
}
