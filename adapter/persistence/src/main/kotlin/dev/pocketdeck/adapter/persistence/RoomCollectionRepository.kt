package dev.pocketdeck.adapter.persistence

import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.collection.CardCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomCollectionRepository(
    private val dao: OwnedCardDao,
) : CollectionRepository {
    override fun observe(): Flow<CardCollection> = dao.observeAll().map(::toCollection)

    override suspend fun current(): CardCollection = toCollection(dao.getAll())

    override suspend fun save(collection: CardCollection) =
        dao.replaceAll(collection.copiesByCard.map { (id, copies) -> OwnedCardEntity(id.value, copies) })

    private fun toCollection(rows: List<OwnedCardEntity>): CardCollection =
        rows.fold(CardCollection.EMPTY) { acc, row -> acc.withCopies(CardId(row.cardId), row.copies) }
}
