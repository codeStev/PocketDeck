package dev.pocketdeck.adapter.recognition

/**
 * How sure we are which card a tile shows, from 0 to 1. Two independent signs of certainty count, the better one wins:
 * a near-perfect match score, or a match that stands far apart from every other card. The second matters because holo
 * and full-art cards often score only 0.93-0.95 against their reference picture while no other card comes close.
 */
internal object IdentificationConfidence {
    private const val FLOOR_SCORE = 0.80f
    private const val CERTAIN_SCORE = 0.98f
    private const val SEPARATED_MARGIN = 0.05f
    private const val CERTAIN_MARGIN = 0.25f

    fun of(
        score: Float,
        margin: Float,
    ): Double {
        val byScore = (score - FLOOR_SCORE) / (CERTAIN_SCORE - FLOOR_SCORE)
        val byMargin = (margin - SEPARATED_MARGIN) / (CERTAIN_MARGIN - SEPARATED_MARGIN)
        return maxOf(byScore, byMargin).toDouble().coerceIn(0.0, 1.0)
    }
}
