package dev.pocketdeck.application.fakes

import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.domain.collection.CardCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InMemoryCollectionRepository(
    initial: CardCollection = CardCollection.EMPTY,
) : CollectionRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): StateFlow<CardCollection> = state

    override suspend fun current(): CardCollection = state.value

    override suspend fun save(collection: CardCollection) {
        state.value = collection
    }
}
