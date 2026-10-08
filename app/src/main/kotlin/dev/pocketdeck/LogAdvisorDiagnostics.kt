package dev.pocketdeck

import android.util.Log
import dev.pocketdeck.adapter.ui.about.DiagnosticsLog
import dev.pocketdeck.application.advice.AdvisorDiagnostics

/**
 * Writes what happens while an AI is asked to the log (tag `PocketDeckAi`), so a request that hangs can be followed, and
 * keeps the same lines for the diagnostics a player can copy (the log itself is stripped from release builds).
 */
class LogAdvisorDiagnostics(
    private val diagnostics: DiagnosticsLog,
) : AdvisorDiagnostics {
    override fun note(message: String) {
        Log.i(TAG, message)
        diagnostics.note("AI: $message")
    }

    private companion object {
        const val TAG = "PocketDeckAi"
    }
}
