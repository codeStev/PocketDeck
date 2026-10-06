package dev.pocketdeck.adapter.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import dev.pocketdeck.application.scan.GridScroller
import dev.pocketdeck.application.scan.ScrollGesture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android only lets an accessibility service inject gestures into other apps. This one does nothing else: it
 * ignores all events and reads no screen content (see `grid_scroll_service.xml`). The player has to switch it on in
 * the system's accessibility settings.
 */
class GridScrollService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    /** Drags across the screen as described; true once the gesture has been carried out completely. */
    internal suspend fun drag(gesture: ScrollGesture): Boolean =
        suspendCancellableCoroutine { continuation ->
            val path =
                Path().apply {
                    moveTo(gesture.startX.toFloat(), gesture.startY.toFloat())
                    lineTo(gesture.endX.toFloat(), gesture.endY.toFloat())
                }
            val stroke = GestureDescription.StrokeDescription(path, 0, gesture.durationMillis)
            val callback =
                object : GestureResultCallback() {
                    override fun onCompleted(description: GestureDescription?) = continuation.resume(true)

                    override fun onCancelled(description: GestureDescription?) = continuation.resume(false)
                }
            val dispatched = dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), callback, null)
            if (!dispatched) continuation.resume(false)
        }

    internal companion object {
        @Volatile
        var instance: GridScrollService? = null
    }
}

/** The [GridScroller] backed by the accessibility service; unavailable until the player has switched it on. */
class AccessibilityGridScroller : GridScroller {
    override suspend fun perform(gesture: ScrollGesture): Boolean = GridScrollService.instance?.drag(gesture) ?: false
}

object AutoScroll {
    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val ours = ComponentName(context, GridScrollService::class.java).flattenToString()
        return enabled?.split(':')?.any { it.equals(ours, ignoreCase = true) } == true
    }

    fun settingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}
