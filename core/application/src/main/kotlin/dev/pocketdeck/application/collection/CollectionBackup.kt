package dev.pocketdeck.application.collection

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.collection.CardCollection
import kotlinx.coroutines.flow.Flow

/** Outbound port: keeps the collection as it was before the last scan was saved, so a bad scan can be undone. */
interface CollectionBackup {
    suspend fun save(collection: CardCollection)

    suspend fun load(): CardCollection?

    fun observeAvailable(): Flow<Boolean>
}

sealed interface RestoreFailure {
    data object NoBackup : RestoreFailure
}

/** Inbound port: put the collection from before the last saved scan back. */
fun interface RestorePreviousCollection {
    suspend operator fun invoke(): Outcome<Unit, RestoreFailure>
}

/** Inbound port: whether there is a backup to restore. */
fun interface ObserveBackupAvailable {
    operator fun invoke(): Flow<Boolean>
}

class RestorePreviousCollectionService(
    private val collections: CollectionRepository,
    private val backup: CollectionBackup,
) : RestorePreviousCollection {
    override suspend fun invoke(): Outcome<Unit, RestoreFailure> {
        val previous = backup.load() ?: return Outcome.Failure(RestoreFailure.NoBackup)
        // Swap, so that restoring can itself be undone by restoring again.
        backup.save(collections.current())
        collections.save(previous)
        return Outcome.Success(Unit)
    }
}

class ObserveBackupAvailableService(
    private val backup: CollectionBackup,
) : ObserveBackupAvailable {
    override fun invoke() = backup.observeAvailable()
}
