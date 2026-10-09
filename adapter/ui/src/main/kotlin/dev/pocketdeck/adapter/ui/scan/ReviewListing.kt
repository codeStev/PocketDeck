package dev.pocketdeck.adapter.ui.scan

import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.scan.ReviewReason
import dev.pocketdeck.domain.scan.ScannedCard

/** One scanned card as shown in the review list; [copies] and [needsReview] change while the player corrects it. */
data class ReviewItem(
    val cardId: CardId,
    val name: String,
    val artworkUrl: String?,
    val copies: Int,
    val confidence: Double,
    val needsReview: Boolean,
    val reason: ReviewReason? = null,
)

object ReviewListing {
    /** Cards the scanner was unsure about come first so the player sees them before saving. */
    fun build(
        scanned: List<ScannedCard>,
        catalog: CardCatalog,
        language: Language,
    ): List<ReviewItem> {
        val order = catalog.cards.withIndex().associate { it.value.id to it.index }
        return scanned
            .mapNotNull { card ->
                catalog[card.cardId]?.let { known ->
                    ReviewItem(
                        cardId = card.cardId,
                        name = known.nameIn(language).value,
                        artworkUrl = known.artworkIn(language)?.value,
                        copies = card.copies,
                        confidence = card.confidence.value,
                        needsReview = card.needsReview,
                        reason = card.reason,
                    )
                }
            }.sortedWith(compareByDescending<ReviewItem> { it.needsReview }.thenBy { order.getValue(it.cardId) })
    }
}
