package dev.pocketdeck.adapter.recognition

import dev.pocketdeck.application.scan.CardRecognizer
import dev.pocketdeck.application.scan.ScreenFrame
import dev.pocketdeck.domain.scan.Confidence
import dev.pocketdeck.domain.scan.TileRecognition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

data class CopyCountReading(
    val copies: Int,
    val confidence: Double,
)

/** Reads the number in the badge at the bottom-left of a tile; null when no badge is visible (yet). */
fun interface CopyCountReader {
    fun read(
        frame: ScreenFrame,
        tile: TileBounds,
    ): CopyCountReading?
}

/**
 * The [CardRecognizer] for the game's "My Cards" grid: finds the tiles, identifies each card by its illustration
 * and reads its copy count. Tiles that do not look like any known card are skipped.
 */
class GridCardRecognizer(
    private val index: CardIndex,
    private val detector: CollectionGridDetector,
    private val copyCounts: CopyCountReader,
) : CardRecognizer {
    private val fingerprinter = ArtFingerprinter(index.parameters)

    override suspend fun recognize(frame: ScreenFrame): List<TileRecognition> =
        withContext(Dispatchers.Default) {
            detector.detect(frame).mapNotNull { recognizeTile(frame, it) }
        }

    private fun recognizeTile(
        frame: ScreenFrame,
        tile: TileBounds,
    ): TileRecognition? {
        val matches = index.nearest(fingerprinter.fingerprint(frame, tile), CANDIDATES)
        val best = matches.first()
        if (!isAccepted(best.score, matches.getOrNull(1)?.score)) return null
        // Reprints share their artwork and cannot be told apart on screen; choose the same one every time so
        // repeated sightings of a card agree with each other.
        val chosen = matches.filter { best.score - it.score <= SAME_ARTWORK_TOLERANCE }.minBy { it.cardId.value }
        val reading = copyCounts.read(frame, tile)
        val identification = confidenceOf(best.score)
        val confidence = min(identification, reading?.confidence ?: 1.0)
        return TileRecognition(chosen.cardId, reading?.copies, Confidence(confidence))
    }

    private fun confidenceOf(score: Float): Double =
        ((score - FLOOR_SCORE) / (CERTAIN_MATCH_SCORE - FLOOR_SCORE)).toDouble().coerceIn(0.0, 1.0)

    /**
     * Clean matches score about 0.99. Overlays such as the game's floating buttons cover parts of a tile and lower
     * the score, so a weaker match is still accepted when it stands clearly apart from every other card.
     */
    private fun isAccepted(
        best: Float,
        runnerUp: Float?,
    ) = best >= MINIMUM_MATCH_SCORE || (best >= FLOOR_SCORE && best - (runnerUp ?: -1f) >= MINIMUM_MARGIN)

    private companion object {
        const val CANDIDATES = 4
        const val MINIMUM_MATCH_SCORE = 0.90f
        const val FLOOR_SCORE = 0.80f
        const val MINIMUM_MARGIN = 0.12f
        const val CERTAIN_MATCH_SCORE = 0.98f
        const val SAME_ARTWORK_TOLERANCE = 0.004f
    }
}
