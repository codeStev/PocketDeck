package dev.pocketdeck.application.collection

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.card.CardId

sealed interface AdjustCountFailure {
    data class UnknownCard(
        val cardId: CardId,
    ) : AdjustCountFailure
}

/** Inbound port: manually change how many copies of a card the player owns. Success carries the new count. */
fun interface AdjustOwnedCount {
    suspend operator fun invoke(
        cardId: CardId,
        delta: Int,
    ): Outcome<Int, AdjustCountFailure>
}
