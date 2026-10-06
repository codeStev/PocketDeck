package dev.pocketdeck.adapter.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/** The player's permission to capture the screen once; Android hands it out per capture session. */
class ScreenCaptureConsent(
    val resultCode: Int,
    val data: Intent,
)

/** Asks the player for the system's "start recording or casting" consent. Returns null when declined. */
class RequestScreenCapture : ActivityResultContract<Unit, ScreenCaptureConsent?>() {
    override fun createIntent(
        context: Context,
        input: Unit,
    ): Intent = context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent()

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): ScreenCaptureConsent? =
        intent
            ?.takeIf {
                resultCode == Activity.RESULT_OK
            }?.let { ScreenCaptureConsent(resultCode, it) }
}

/** The floating scan button needs the "display over other apps" permission. */
object OverlayPermission {
    fun isGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
}

/** Entry points for the app to start and stop a capture session. */
object ScanCapture {
    /** Sent to the app's launch activity when the player stops a scan, so it can show the review. */
    const val ACTION_REVIEW = "dev.pocketdeck.action.REVIEW_SCAN"

    /** Boolean extra of [ACTION_REVIEW]: the scan went from the top to the end of the list. */
    const val EXTRA_COMPLETE = "complete"

    internal const val ACTION_START = "dev.pocketdeck.action.START_SCAN"
    internal const val ACTION_STOP = "dev.pocketdeck.action.STOP_SCAN"
    internal const val EXTRA_RESULT_CODE = "resultCode"
    internal const val EXTRA_RESULT_DATA = "resultData"
    internal const val EXTRA_AUTOMATIC = "automatic"

    fun start(
        context: Context,
        consent: ScreenCaptureConsent,
        automatic: Boolean,
    ) {
        val intent =
            Intent(context, ScanCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, consent.resultCode)
                .putExtra(EXTRA_RESULT_DATA, consent.data)
                .putExtra(EXTRA_AUTOMATIC, automatic)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, ScanCaptureService::class.java).setAction(ACTION_STOP))
    }
}
