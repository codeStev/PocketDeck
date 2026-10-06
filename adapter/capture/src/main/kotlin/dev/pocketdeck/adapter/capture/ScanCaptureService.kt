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
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import dev.pocketdeck.application.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Holds the screen-capture session while a scan runs: grabs the latest screen image a few times a second and
 * feeds it to the scan use cases. All recognition logic lives behind those ports; this class only moves pixels.
 */
class ScanCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val changes = FrameChangeDetector()
    private var session: CaptureSession? = null
    private var overlay: ScanOverlay? = null

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
        val dependencies = (application as CaptureDependenciesProvider).captureDependencies
        session = openSession(resultCode, resultData)
        dependencies.startCollectionScan()
        overlay = ScanOverlay(this, onStop = { finish(openReview = true) }).also(ScanOverlay::show)
        scope.launch { dependencies.observeScanProgress().collect { overlay?.update(ProgressText.of(it)) } }
        scope.launch { captureLoop(dependencies) }
    }

    private fun openSession(
        resultCode: Int,
        resultData: Intent,
    ): CaptureSession {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection =
            checkNotNull(manager.getMediaProjection(resultCode, resultData)) { "Screen capture was not granted" }
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

    private suspend fun captureLoop(dependencies: CaptureDependencies) {
        while (scope.isActive) {
            try {
                if (!processLatestFrame(dependencies)) return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // One unreadable frame must not end the scan or take the app down; the next frame gets its chance.
                Log.e(TAG, "Skipped a frame", failure)
            }
            delay(CAPTURE_INTERVAL_MS)
        }
    }

    /** Returns false when the scan has ended elsewhere and capturing should stop. */
    private suspend fun processLatestFrame(dependencies: CaptureDependencies): Boolean {
        val started = SystemClock.elapsedRealtime()
        val frame = session?.latestFrame() ?: return true
        if (!changes.isNew(frame)) return true
        if (dependencies.submitScanFrame(frame) is Outcome.Failure) {
            finish(openReview = false)
            return false
        }
        Log.d(TAG, "Frame analysed in ${SystemClock.elapsedRealtime() - started} ms")
        return true
    }

    private fun finish(openReview: Boolean) {
        val current = session ?: return
        session = null
        overlay?.remove()
        overlay = null
        current.release()
        changes.reset()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (openReview) openReviewScreen()
        stopSelf()
    }

    private fun openReviewScreen() {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.action = ScanCapture.ACTION_REVIEW
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
            .setOngoing(true)
            .build()
    }

    private class CaptureSession(
        private val projection: MediaProjection,
        private val reader: ImageReader,
        private val display: VirtualDisplay,
    ) {
        fun latestFrame() =
            reader.acquireLatestImage()?.use { image ->
                val plane = image.planes[0]
                RgbaFrameConverter.toScreenFrame(plane.buffer, image.width, image.height, plane.rowStride)
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
        const val CHANNEL_ID = "scan"
        const val DISPLAY_NAME = "pocketdeck-scan"
        const val READER_BUFFERS = 2
        const val CAPTURE_INTERVAL_MS = 350L
    }
}
