package dev.pocketdeck.application.scan

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.application.collection.CollectionRepository
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
    override suspend fun invoke(frame: ScreenFrame): Outcome<ScanProgress, ScanFailure> {
        val session = sessions.current() ?: return Outcome.Failure(ScanFailure.NoActiveScan)
        val updated = session.observe(recognizer.recognize(frame))
        sessions.save(updated)
        return Outcome.Success(ScanProgress.of(updated))
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

class ConfirmScanResultsService(
    private val catalogs: CardCatalogRepository,
    private val collections: CollectionRepository,
) : ConfirmScanResults {
    override suspend fun invoke(review: List<ScannedCard>): Outcome<ConfirmedScan, ConfirmScanFailure> {
        val catalog = catalogs.current()
        val unknown = review.map { it.cardId }.filter { it !in catalog }
        if (unknown.isNotEmpty()) return Outcome.Failure(ConfirmScanFailure.UnknownCards(unknown))

        val updated = review.fold(collections.current()) { acc, card -> acc.withCopies(card.cardId, card.copies) }
        collections.save(updated)
        return Outcome.Success(ConfirmedScan(updatedCards = review.size))
    }
}
