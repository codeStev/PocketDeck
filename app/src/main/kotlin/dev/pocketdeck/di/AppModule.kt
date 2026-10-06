package dev.pocketdeck.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pocketdeck.adapter.carddb.MasterCatalogSource
import dev.pocketdeck.adapter.persistence.PocketDeckDatabases
import dev.pocketdeck.adapter.persistence.PocketDeckStorage
import dev.pocketdeck.adapter.ui.collection.CollectionViewModel
import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.application.catalog.CardCatalogSource
import dev.pocketdeck.application.catalog.ObserveCardCatalog
import dev.pocketdeck.application.catalog.ObserveCardCatalogService
import dev.pocketdeck.application.catalog.SyncCardCatalog
import dev.pocketdeck.application.catalog.SyncCardCatalogService
import dev.pocketdeck.application.collection.AdjustOwnedCount
import dev.pocketdeck.application.collection.AdjustOwnedCountService
import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.application.collection.ObserveCollection
import dev.pocketdeck.application.collection.ObserveCollectionService
import dev.pocketdeck.domain.card.Language
import java.util.Locale
import javax.inject.Singleton

/**
 * The composition root: the only place that knows which adapter implements which port.
 * Everything else depends on the port interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // --- driven adapters -> outbound ports
    @Provides
    @Singleton
    fun storage(
        @ApplicationContext context: Context,
    ): PocketDeckStorage = PocketDeckDatabases.create(context)

    @Provides
    fun cardCatalogRepository(storage: PocketDeckStorage): CardCatalogRepository = storage.catalogs

    @Provides
    fun collectionRepository(storage: PocketDeckStorage): CollectionRepository = storage.collections

    @Provides
    fun cardCatalogSource(
        @ApplicationContext context: Context,
    ): CardCatalogSource = MasterCatalogSource { context.assets.open(MASTER_DATASET_ASSET) }

    @Provides
    fun displayLanguage(): Language = Language.fromIsoCode(Locale.getDefault().language)

    // --- use cases -> inbound ports
    @Provides
    fun syncCardCatalog(
        source: CardCatalogSource,
        catalogs: CardCatalogRepository,
    ): SyncCardCatalog = SyncCardCatalogService(source, catalogs)

    @Provides
    fun observeCardCatalog(catalogs: CardCatalogRepository): ObserveCardCatalog = ObserveCardCatalogService(catalogs)

    @Provides
    fun observeCollection(collections: CollectionRepository): ObserveCollection = ObserveCollectionService(collections)

    @Provides
    fun adjustOwnedCount(
        catalogs: CardCatalogRepository,
        collections: CollectionRepository,
    ): AdjustOwnedCount = AdjustOwnedCountService(catalogs, collections)

    // --- driving adapter wiring
    @Provides
    fun collectionViewModelFactory(
        observeCatalog: ObserveCardCatalog,
        observeCollection: ObserveCollection,
        adjustOwnedCount: AdjustOwnedCount,
        syncCardCatalog: SyncCardCatalog,
        language: Language,
    ) = CollectionViewModel.Factory(observeCatalog, observeCollection, adjustOwnedCount, syncCardCatalog, language)

    private const val MASTER_DATASET_ASSET = "master_cards.json"
}
