package dev.pocketdeck.application.collection

import dev.pocketdeck.domain.collection.CardCollection
import kotlinx.coroutines.flow.Flow

/** Outbound port: persistence of the player's card collection. */
interface CollectionRepository {
    fun observe(): Flow<CardCollection>

    suspend fun current(): CardCollection

    suspend fun save(collection: CardCollection)
}
