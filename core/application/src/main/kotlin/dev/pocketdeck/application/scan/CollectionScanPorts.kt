package dev.pocketdeck.application.scan

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.scan.ScannedCard

sealed interface ScanFailure {
    data object NoActiveScan : ScanFailure
}

data class ScanProgress(
    val framesProcessed: Int,
    val distinctCards: Int,
)

data class ConfirmedScan(
    val updatedCards: Int,
)

sealed interface ConfirmScanFailure {
    data class UnknownCards(
        val cardIds: List<CardId>,
    ) : ConfirmScanFailure
}

/** Inbound port: begin scanning the collection. Discards any scan still in progress. */
fun interface StartCollectionScan {
    operator fun invoke()
}

/** Inbound port: analyse one captured frame and fold it into the running scan. */
fun interface SubmitScanFrame {
    suspend operator fun invoke(frame: ScreenFrame): Outcome<ScanProgress, ScanFailure>
}

/** Inbound port: end the scan and obtain the merged cards for the player to review. */
fun interface FinishCollectionScan {
    operator fun invoke(): Outcome<List<ScannedCard>, ScanFailure>
}

/** Inbound port: write a (possibly player-corrected) review into the collection. */
fun interface ConfirmScanResults {
    suspend operator fun invoke(review: List<ScannedCard>): Outcome<ConfirmedScan, ConfirmScanFailure>
}
