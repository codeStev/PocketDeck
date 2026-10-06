package dev.pocketdeck.adapter.capture

import dev.pocketdeck.application.scan.ScanProgress

/** The short status shown on the floating scan button. */
object ProgressText {
    private const val PROMPT = "Scroll through My Cards"
    private const val AUTO_PROMPT = "Scanning…"

    fun of(
        progress: ScanProgress?,
        automatic: Boolean = false,
    ): String {
        if (progress == null || progress.distinctCards == 0) return if (automatic) AUTO_PROMPT else PROMPT
        val cards = if (progress.distinctCards == 1) "1 card" else "${progress.distinctCards} cards"
        val status = if (progress.cardsNeedingReview > 0) "$cards · ${progress.cardsNeedingReview} to check" else cards
        return if (automatic) "$AUTO_PROMPT $status" else status
    }
}
