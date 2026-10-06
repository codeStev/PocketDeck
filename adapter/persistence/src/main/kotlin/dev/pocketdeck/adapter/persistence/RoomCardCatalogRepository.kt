package dev.pocketdeck.adapter.persistence

import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class RoomCardCatalogRepository(
    private val dao: CardDao,
) : CardCatalogRepository {
    // Mapping thousands of rows is CPU work, so it stays off the caller's (often main) thread.
    override fun observe(): Flow<CardCatalog> = dao.observeAll().map(::toCatalog).flowOn(Dispatchers.Default)

    override suspend fun current(): CardCatalog = toCatalog(dao.getAll())

    override suspend fun replaceAll(cards: List<Card>) =
        dao.replaceAll(
            withContext(Dispatchers.Default) {
                cards.map(Card::toEntity)
            },
        )

    private fun toCatalog(entities: List<CardEntity>) = CardCatalog(entities.map(CardEntity::toDomain))
}
