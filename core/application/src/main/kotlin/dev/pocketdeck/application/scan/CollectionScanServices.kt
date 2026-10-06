package dev.pocketdeck.application.scan

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.application.collection.CollectionBackup
import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.scan.ReviewReason
import dev.pocketdeck.domain.scan.ScanSession
import dev.pocketdeck.domain.scan.ScannedCard
import kotlinx.coroutines.flow.map

class StartCollectionScanService(
    private val sessions: ScanSessionStore,
) : StartCollectionScan {
    override fun invoke() = sessions.save(ScanSession.start())
}

class SubmitScanFrameService(
    private val recognizer: CardRecognizer,
    private val sessions: ScanSessionStore,
) : SubmitScanFrame {
    override suspend fun invoke(frame: ScreenFrame): Outcome<FrameAnalysis, ScanFailure> {
        val session = sessions.current() ?: return Outcome.Failure(ScanFailure.NoActiveScan)
        val recognitions = recognizer.recognize(frame)
        val updated = session.observe(recognitions)
        sessions.save(updated)
        val expected = recognitions.filter { it.expectsCount }
        val analysis =
            FrameAnalysis(
                ScanProgress.of(updated),
                recognitions.size,
                expected.size,
                expected.count { it.copies != null },
            )
        return Outcome.Success(analysis)
    }
}

class ObserveScanProgressService(
    private val sessions: ScanSessionStore,
) : ObserveScanProgress {
    override fun invoke() = sessions.observe().map { session -> session?.let(ScanProgress::of) }
}

class FinishCollectionScanService(
    private val sessions: ScanSessionStore,
) : FinishCollectionScan {
    override fun invoke(): Outcome<List<ScannedCard>, ScanFailure> {
        val session = sessions.current() ?: return Outcome.Failure(ScanFailure.NoActiveScan)
        sessions.clear()
        return Outcome.Success(session.summarize())
    }
}

/** A count the scan never saw is no information: what we already knew about the card is better than a guess of 1. */
private fun countToStore(
    card: ScannedCard,
    current: CardCollection,
): Int =
    if (card.reason == ReviewReason.COUNT_NEVER_READ) maxOf(current.copiesOf(card.cardId), card.copies) else card.copies

class ConfirmScanResultsService(
    private val catalogs: CardCatalogRepository,
    private val collections: CollectionRepository,
    private val backup: CollectionBackup,
) : ConfirmScanResults {
    override suspend fun invoke(
        review: List<ScannedCard>,
        replaceCollection: Boolean,
    ): Outcome<ConfirmedScan, ConfirmScanFailure> {
        val catalog = catalogs.current()
        val unknown = review.map { it.cardId }.filter { it !in catalog }
        if (unknown.isNotEmpty()) return Outcome.Failure(ConfirmScanFailure.UnknownCards(unknown))

        val current = collections.current()
        val base = if (replaceCollection) CardCollection.EMPTY else current
        val updated = review.fold(base) { acc, card -> acc.withCopies(card.cardId, countToStore(card, current)) }
        // Keep what the player had, so that a scan that went wrong can be undone.
        if (current.copiesByCard.isNotEmpty()) backup.save(current)
        collections.save(updated)
        return Outcome.Success(ConfirmedScan(updatedCards = review.size))
    }
}
