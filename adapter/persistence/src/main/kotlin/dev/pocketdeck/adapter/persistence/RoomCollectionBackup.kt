package dev.pocketdeck.adapter.persistence

import dev.pocketdeck.application.collection.CollectionBackup
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.collection.CardCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomCollectionBackup(
    private val dao: BackupOwnedCardDao,
) : CollectionBackup {
    override suspend fun save(collection: CardCollection) =
        dao.replaceAll(collection.copiesByCard.map { (id, copies) -> BackupOwnedCardEntity(id.value, copies) })

    override suspend fun load(): CardCollection? {
        val rows = dao.getAll()
        if (rows.isEmpty()) return null
        return rows.fold(CardCollection.EMPTY) { acc, row -> acc.withCopies(CardId(row.cardId), row.copies) }
    }

    override fun observeAvailable(): Flow<Boolean> = dao.observeCount().map { it > 0 }
}
