package dev.pocketdeck.domain.scan

import dev.pocketdeck.domain.card.CardId

@JvmInline
value class Confidence(
    val value: Double,
) {
    init {
        require(value in 0.0..1.0) { "Confidence must be within 0..1 but was $value" }
    }
}

/**
 * One tile of the collection grid as recognised in a single captured frame. [copies] is null when the tile showed
 * no readable count: the game hides the counts while scrolling, and some zoom levels never show them.
 */
data class TileRecognition(
    val cardId: CardId,
    val copies: Int?,
    val confidence: Confidence,
) {
    init {
        require(copies == null || copies >= 1) { "A recognised tile shows at least one copy but was $copies" }
    }
}

data class ScannedCard(
    val cardId: CardId,
    val copies: Int,
    val confidence: Confidence,
    val needsReview: Boolean,
)

/**
 * Collects recognitions across the many frames captured while the player scrolls, and decides per card
 * which copy count to trust. Immutable: [observe] returns a new session.
 */
class ScanSession private constructor(
    private val observations: Map<CardId, List<TileRecognition>>,
    val framesProcessed: Int,
) {
    val distinctCards: Int get() = observations.size

    fun observe(recognitions: List<TileRecognition>): ScanSession {
        val merged = observations.toMutableMap()
        recognitions.forEach { merged[it.cardId] = merged[it.cardId].orEmpty() + it }
        return ScanSession(merged, framesProcessed + 1)
    }

    fun summarize(): List<ScannedCard> =
        observations
            .map { (cardId, seen) -> summarize(cardId, seen) }
            .sortedBy { it.cardId.value }

    private fun summarize(
        cardId: CardId,
        seen: List<TileRecognition>,
    ): ScannedCard {
        val counted = seen.filter { it.copies != null }.groupBy { it.copies }
        // A card that appears in the owned list is owned at least once, which is all we can say without a count.
        if (counted.isEmpty()) {
            return ScannedCard(
                cardId,
                MINIMUM_OWNED,
                Confidence(seen.maxOf { it.confidence.value }),
                needsReview = true,
            )
        }
        // Most confident support wins; on a tie the larger count wins so owned cards are not under-reported.
        val winningCopies =
            counted
                .maxWith(
                    compareBy({ (_, group) -> group.sumOf { it.confidence.value } }, { (copies, _) -> copies }),
                ).key
        val confidence = counted.getValue(winningCopies).maxOf { it.confidence.value }
        val unanimous = counted.size == 1
        return ScannedCard(
            cardId = cardId,
            copies = requireNotNull(winningCopies),
            confidence = Confidence(confidence),
            needsReview = !unanimous || confidence < REVIEW_CONFIDENCE_THRESHOLD,
        )
    }

    companion object {
        const val REVIEW_CONFIDENCE_THRESHOLD = 0.8
        private const val MINIMUM_OWNED = 1

        fun start() = ScanSession(emptyMap(), framesProcessed = 0)
    }
}
