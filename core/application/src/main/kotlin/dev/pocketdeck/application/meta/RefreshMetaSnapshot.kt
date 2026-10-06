package dev.pocketdeck.application.meta

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.meta.ArchetypeAggregator
import dev.pocketdeck.domain.meta.MetaSnapshot

/** Inbound port: fetch the recent tournament decks and make them the current picture of the field. */
fun interface RefreshMetaSnapshot {
    suspend operator fun invoke(): Outcome<MetaSnapshot, MetaSourceFailure>
}

class RefreshMetaSnapshotService(
    private val source: MetaDecklistSource,
    private val snapshots: MetaSnapshotRepository,
    private val aggregator: ArchetypeAggregator = ArchetypeAggregator(),
) : RefreshMetaSnapshot {
    override suspend fun invoke(): Outcome<MetaSnapshot, MetaSourceFailure> =
        when (val outcome = source.recentDecklists()) {
            is Outcome.Failure -> {
                outcome
            }

            is Outcome.Success -> {
                val snapshot = aggregator.aggregate(outcome.value)
                snapshots.save(snapshot)
                Outcome.Success(snapshot)
            }
        }
}

/** Inbound port: follow the current picture of the field. */
fun interface ObserveMetaSnapshot {
    operator fun invoke(): kotlinx.coroutines.flow.Flow<MetaSnapshot>
}

class ObserveMetaSnapshotService(
    private val snapshots: MetaSnapshotRepository,
) : ObserveMetaSnapshot {
    override fun invoke() = snapshots.observe()
}
