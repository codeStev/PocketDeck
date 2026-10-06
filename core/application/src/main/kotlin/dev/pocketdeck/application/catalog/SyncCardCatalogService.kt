package dev.pocketdeck.application.catalog

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.card.Card

class SyncCardCatalogService(
    private val source: CardCatalogSource,
    private val repository: CardCatalogRepository,
) : SyncCardCatalog {
    override suspend fun invoke(): Outcome<CatalogSyncReport, CatalogSyncFailure> =
        when (val fetched = source.fetchAll()) {
            is Outcome.Failure -> Outcome.Failure(CatalogSyncFailure.SourceNotReachable(fetched.reason.reason))
            is Outcome.Success -> store(fetched.value)
        }

    private suspend fun store(cards: List<Card>): Outcome<CatalogSyncReport, CatalogSyncFailure> {
        // An empty answer is almost certainly a broken source; never wipe a good local catalog with it.
        if (cards.isEmpty()) return Outcome.Failure(CatalogSyncFailure.EmptyCatalog)
        repository.replaceAll(cards)
        return Outcome.Success(CatalogSyncReport(cards.size))
    }
}
