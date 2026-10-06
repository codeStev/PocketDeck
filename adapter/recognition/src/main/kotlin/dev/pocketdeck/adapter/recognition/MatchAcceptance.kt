package dev.pocketdeck.adapter.recognition

/**
 * Decides whether the best card for a tile is believable. Clean matches score about 0.99. Overlays and unusual card
 * layouts (stadiums, promos) lower the score without making the match doubtful, as long as no other card comes
 * close; two candidates that look nearly alike are ambiguous and are not guessed.
 */
internal object MatchAcceptance {
    private const val CLEAN_SCORE = 0.90f
    private const val FLOOR_SCORE = 0.80f
    private const val USUAL_MARGIN = 0.12f
    private const val LOWEST_SCORE = 0.60f
    private const val CLEAR_MARGIN = 0.20f

    fun accepts(
        best: Float,
        runnerUp: Float?,
    ): Boolean {
        val margin = best - (runnerUp ?: -1f)
        return best >= CLEAN_SCORE ||
            (best >= FLOOR_SCORE && margin >= USUAL_MARGIN) ||
            (best >= LOWEST_SCORE && margin >= CLEAR_MARGIN)
    }
}
