package dev.pocketdeck.application.meta

import dev.pocketdeck.domain.meta.MetaSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Outbound port: keeps the latest snapshot of the competitive field. */
interface MetaSnapshotRepository {
    fun observe(): Flow<MetaSnapshot>

    suspend fun current(): MetaSnapshot

    suspend fun save(snapshot: MetaSnapshot)
}

/** The field changes with every tournament, so a snapshot is only worth keeping while the app runs. */
class InMemoryMetaSnapshotRepository : MetaSnapshotRepository {
    private val state = MutableStateFlow(MetaSnapshot.EMPTY)

    override fun observe(): Flow<MetaSnapshot> = state

    override suspend fun current(): MetaSnapshot = state.value

    override suspend fun save(snapshot: MetaSnapshot) {
        state.value = snapshot
    }
}
