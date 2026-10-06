package dev.pocketdeck.application.fakes

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.catalog.CardCatalogSource
import dev.pocketdeck.application.catalog.SourceUnavailable
import dev.pocketdeck.domain.card.Card

class FakeCardCatalogSource(
    var result: Outcome<List<Card>, SourceUnavailable>,
) : CardCatalogSource {
    override suspend fun fetchAll() = result
}
