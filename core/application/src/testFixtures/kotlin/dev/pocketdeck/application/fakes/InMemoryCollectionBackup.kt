package dev.pocketdeck.application.fakes

import dev.pocketdeck.application.collection.CollectionBackup
import dev.pocketdeck.domain.collection.CardCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryCollectionBackup : CollectionBackup {
    private val stored = MutableStateFlow<CardCollection?>(null)

    override suspend fun save(collection: CardCollection) {
        stored.value = collection
    }

    override suspend fun load(): CardCollection? = stored.value

    override fun observeAvailable(): Flow<Boolean> = stored.map { it != null }
}
