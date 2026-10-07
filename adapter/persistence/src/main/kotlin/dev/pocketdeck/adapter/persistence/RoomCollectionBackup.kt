package dev.pocketdeck.adapter.persistence

import dev.pocketdeck.application.collection.CollectionBackup
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.collection.CardCollection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** The backup of the active profile: every account keeps its own "before the last scan" copy. */
internal class RoomCollectionBackup(
    private val dao: BackupOwnedCardDao,
    private val activeProfile: Flow<Long>,
) : CollectionBackup {
    override suspend fun save(collection: CardCollection) {
        val profile = activeProfile.first()
        dao.replaceAll(
            profile,
            collection.copiesByCard.map { (id, copies) ->
                BackupOwnedCardEntity(profile, id.value, copies)
            },
        )
    }

    override suspend fun load(): CardCollection? {
        val rows = dao.getAll(activeProfile.first())
        if (rows.isEmpty()) return null
        return rows.fold(CardCollection.EMPTY) { acc, row -> acc.withCopies(CardId(row.cardId), row.copies) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAvailable(): Flow<Boolean> =
        activeProfile.flatMapLatest { profile -> dao.observeCount(profile).map { it > 0 } }
}
