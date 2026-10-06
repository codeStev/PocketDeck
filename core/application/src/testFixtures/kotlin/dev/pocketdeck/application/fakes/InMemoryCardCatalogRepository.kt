package dev.pocketdeck.application.fakes

import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InMemoryCardCatalogRepository(
    initial: CardCatalog = CardCatalog.EMPTY,
) : CardCatalogRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): StateFlow<CardCatalog> = state

    override suspend fun current(): CardCatalog = state.value

    override suspend fun replaceAll(cards: List<Card>) {
        state.value = CardCatalog(cards)
    }
}
