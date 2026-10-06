package dev.pocketdeck.application.collection

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.domain.card.CardId

class AdjustOwnedCountService(
    private val catalogs: CardCatalogRepository,
    private val collections: CollectionRepository,
) : AdjustOwnedCount {
    override suspend fun invoke(
        cardId: CardId,
        delta: Int,
    ): Outcome<Int, AdjustCountFailure> {
        if (cardId !in catalogs.current()) return Outcome.Failure(AdjustCountFailure.UnknownCard(cardId))
        val updated = collections.current().adjusted(cardId, delta)
        collections.save(updated)
        return Outcome.Success(updated.copiesOf(cardId))
    }
}
