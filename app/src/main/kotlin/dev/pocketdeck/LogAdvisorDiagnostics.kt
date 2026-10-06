package dev.pocketdeck

import android.util.Log
import dev.pocketdeck.application.advice.AdvisorDiagnostics

/** Writes what happens while an AI is asked to the log (tag `PocketDeckAi`), so a request that hangs can be followed. */
class LogAdvisorDiagnostics : AdvisorDiagnostics {
    override fun note(message: String) {
        Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "PocketDeckAi"
    }
}
