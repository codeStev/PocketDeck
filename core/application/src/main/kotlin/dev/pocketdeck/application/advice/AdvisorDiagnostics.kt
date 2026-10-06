package dev.pocketdeck.application.advice

/**
 * Outbound port: notes what happens while an AI is asked, in plain sentences, so a request that hangs or fails can be
 * followed. Never carries the player's key.
 */
fun interface AdvisorDiagnostics {
    fun note(message: String)

    companion object {
        val NONE = AdvisorDiagnostics { }
    }
}
