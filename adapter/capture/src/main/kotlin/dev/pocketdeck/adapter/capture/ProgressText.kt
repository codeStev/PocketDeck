package dev.pocketdeck.adapter.capture

import dev.pocketdeck.application.scan.ScanProgress

/** The short status shown on the floating scan button. */
object ProgressText {
    private const val PROMPT = "Scroll through My Cards"

    fun of(progress: ScanProgress?): String {
        if (progress == null || progress.distinctCards == 0) return PROMPT
        val cards = if (progress.distinctCards == 1) "1 card" else "${progress.distinctCards} cards"
        return if (progress.cardsNeedingReview > 0) "$cards · ${progress.cardsNeedingReview} to check" else cards
    }
}
