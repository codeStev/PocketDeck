package dev.pocketdeck.application.chase

import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.application.meta.MetaSnapshotRepository
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.meta.ChaseRanker
import dev.pocketdeck.domain.meta.ChaseRanking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.CoroutineContext

/**
 * Inbound port: the cards worth getting next and the packs to open for them, best first. Follows the collection, the
 * catalog and the field, so it is empty until the tournament decks have been loaded.
 */
fun interface RankCardsToChase {
    operator fun invoke(): Flow<ChaseRanking>
}

class RankCardsToChaseService(
    private val catalogs: CardCatalogRepository,
    private val collections: CollectionRepository,
    private val snapshots: MetaSnapshotRepository,
    private val ranker: ChaseRanker = ChaseRanker(),
    /** Where the ranking is worked out: it goes through every tournament list, so not on the main thread. */
    private val context: CoroutineContext = Dispatchers.Default,
) : RankCardsToChase {
    override fun invoke(): Flow<ChaseRanking> =
        combine(catalogs.observe(), collections.observe(), snapshots.observe()) { catalog, collection, meta ->
            ranker.rank(meta, catalog, Inventory.of(collection, catalog))
        }.flowOn(context)
}
