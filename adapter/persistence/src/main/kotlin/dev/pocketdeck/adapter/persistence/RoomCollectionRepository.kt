package dev.pocketdeck.adapter.persistence

import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.collection.CardCollection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** The collection of the active profile: switching the profile makes [observe] emit the other account's cards. */
internal class RoomCollectionRepository(
    private val dao: OwnedCardDao,
    private val activeProfile: Flow<Long>,
) : CollectionRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<CardCollection> =
        activeProfile.flatMapLatest { profile -> dao.observeAll(profile).map(::toCollection) }

    override suspend fun current(): CardCollection = toCollection(dao.getAll(activeProfile.first()))

    override suspend fun save(collection: CardCollection) {
        val profile = activeProfile.first()
        dao.replaceAll(
            profile,
            collection.copiesByCard.map { (id, copies) ->
                OwnedCardEntity(profile, id.value, copies)
            },
        )
    }

    private fun toCollection(rows: List<OwnedCardEntity>): CardCollection =
        rows.fold(CardCollection.EMPTY) { acc, row -> acc.withCopies(CardId(row.cardId), row.copies) }
}
