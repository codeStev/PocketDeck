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
    /**
     * How many tiles of the same card this observation adds up. Reprints with identical artwork are indistinguishable,
     * so when several of them are on screen together their counts are summed into one observation; a view that
     * combined more tiles is a more complete picture of the card than one that saw only a part.
     */
    val parts: Int = 1,
) {
    init {
        require(copies == null || copies >= 1) { "A recognised tile shows at least one copy but was $copies" }
        require(parts >= 1) { "An observation combines at least one tile but was $parts" }
    }
}

/** Why a scanned card is not trusted yet; shown to the player so they know what to look at. */
enum class ReviewReason {
    /** The count badge was never visible while the card was on screen (the game hides it while scrolling). */
    COUNT_NEVER_READ,

    /** Different pictures showed different counts. */
    COUNT_DISAGREES,

    /** The artwork or the digit matched poorly (partly covered, glare, blurred by motion). */
    LOW_CONFIDENCE,
}

data class ScannedCard(
    val cardId: CardId,
    val copies: Int,
    val confidence: Confidence,
    val needsReview: Boolean,
    val reason: ReviewReason? = null,
)

/** How many cards need a review for each reason. */
data class ReviewBreakdown(
    val neverCounted: Int,
    val disagreed: Int,
    val lowConfidence: Int,
) {
    val total: Int get() = neverCounted + disagreed + lowConfidence

    companion object {
        val NONE = ReviewBreakdown(0, 0, 0)
    }
}

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
        // The most complete views win: a frame that showed all twin prints beats frames that showed only some.
        val mostParts = seen.filter { it.copies != null }.maxOfOrNull { it.parts }
        val counted = seen.filter { it.copies != null && it.parts == mostParts }.groupBy { it.copies }
        // A card that appears in the owned list is owned at least once, which is all we can say without a count.
        if (counted.isEmpty()) {
            val identification = Confidence(seen.maxOf { it.confidence.value })
            return ScannedCard(
                cardId,
                MINIMUM_OWNED,
                identification,
                needsReview = true,
                reason = ReviewReason.COUNT_NEVER_READ,
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
        val reason =
            when {
                !unanimous -> ReviewReason.COUNT_DISAGREES
                confidence < REVIEW_CONFIDENCE_THRESHOLD -> ReviewReason.LOW_CONFIDENCE
                else -> null
            }
        return ScannedCard(
            cardId = cardId,
            copies = requireNotNull(winningCopies),
            confidence = Confidence(confidence),
            needsReview = reason != null,
            reason = reason,
        )
    }

    fun breakdown(): ReviewBreakdown {
        val reasons = summarize().mapNotNull { it.reason }.groupingBy { it }.eachCount()
        return ReviewBreakdown(
            neverCounted = reasons[ReviewReason.COUNT_NEVER_READ] ?: 0,
            disagreed = reasons[ReviewReason.COUNT_DISAGREES] ?: 0,
            lowConfidence = reasons[ReviewReason.LOW_CONFIDENCE] ?: 0,
        )
    }

    companion object {
        const val REVIEW_CONFIDENCE_THRESHOLD = 0.8
        private const val MINIMUM_OWNED = 1

        fun start() = ScanSession(emptyMap(), framesProcessed = 0)
    }
}
