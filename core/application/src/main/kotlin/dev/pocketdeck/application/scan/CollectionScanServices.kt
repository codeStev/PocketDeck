package dev.pocketdeck.application.scan

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.domain.collection.CardCollection
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
        val analysis =
            FrameAnalysis(ScanProgress.of(updated), recognitions.size, recognitions.count { it.copies != null })
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

class ConfirmScanResultsService(
    private val catalogs: CardCatalogRepository,
    private val collections: CollectionRepository,
) : ConfirmScanResults {
    override suspend fun invoke(
        review: List<ScannedCard>,
        replaceCollection: Boolean,
    ): Outcome<ConfirmedScan, ConfirmScanFailure> {
        val catalog = catalogs.current()
        val unknown = review.map { it.cardId }.filter { it !in catalog }
        if (unknown.isNotEmpty()) return Outcome.Failure(ConfirmScanFailure.UnknownCards(unknown))

        val base = if (replaceCollection) CardCollection.EMPTY else collections.current()
        val updated = review.fold(base) { acc, card -> acc.withCopies(card.cardId, card.copies) }
        collections.save(updated)
        return Outcome.Success(ConfirmedScan(updatedCards = review.size))
    }
}
