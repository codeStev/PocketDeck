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
import dev.pocketdeck.adapter.recognition.BadgeDigitReader
import dev.pocketdeck.adapter.recognition.CardIndex
import dev.pocketdeck.adapter.recognition.CollectionGridDetector
import dev.pocketdeck.adapter.recognition.DigitTemplates
import dev.pocketdeck.adapter.recognition.GridCardRecognizer
import dev.pocketdeck.adapter.ui.collection.CollectionViewModel
import dev.pocketdeck.adapter.ui.scan.ScanViewModel
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
import dev.pocketdeck.application.scan.CardRecognizer
import dev.pocketdeck.application.scan.ConfirmScanResults
import dev.pocketdeck.application.scan.ConfirmScanResultsService
import dev.pocketdeck.application.scan.FinishCollectionScan
import dev.pocketdeck.application.scan.FinishCollectionScanService
import dev.pocketdeck.application.scan.InMemoryScanSessionStore
import dev.pocketdeck.application.scan.ObserveScanProgress
import dev.pocketdeck.application.scan.ObserveScanProgressService
import dev.pocketdeck.application.scan.ScanSessionStore
import dev.pocketdeck.application.scan.StartCollectionScan
import dev.pocketdeck.application.scan.StartCollectionScanService
import dev.pocketdeck.application.scan.SubmitScanFrame
import dev.pocketdeck.application.scan.SubmitScanFrameService
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

    // --- scanning: recognition adapter and the scan use cases
    @Provides
    @Singleton
    fun cardIndex(
        @ApplicationContext context: Context,
    ): CardIndex = context.assets.open(CARD_INDEX_ASSET).use(CardIndex::read)

    @Provides
    @Singleton
    fun cardRecognizer(index: CardIndex): CardRecognizer =
        GridCardRecognizer(index, CollectionGridDetector(), BadgeDigitReader(DigitTemplates.fromResource()))

    @Provides
    @Singleton
    fun scanSessionStore(): ScanSessionStore = InMemoryScanSessionStore()

    @Provides
    fun startCollectionScan(sessions: ScanSessionStore): StartCollectionScan = StartCollectionScanService(sessions)

    @Provides
    fun submitScanFrame(
        recognizer: CardRecognizer,
        sessions: ScanSessionStore,
    ): SubmitScanFrame = SubmitScanFrameService(recognizer, sessions)

    @Provides
    fun finishCollectionScan(sessions: ScanSessionStore): FinishCollectionScan = FinishCollectionScanService(sessions)

    @Provides
    fun observeScanProgress(sessions: ScanSessionStore): ObserveScanProgress = ObserveScanProgressService(sessions)

    @Provides
    fun confirmScanResults(
        catalogs: CardCatalogRepository,
        collections: CollectionRepository,
    ): ConfirmScanResults = ConfirmScanResultsService(catalogs, collections)

    // --- driving adapter wiring
    @Provides
    fun scanViewModelFactory(
        observeProgress: ObserveScanProgress,
        finishScan: FinishCollectionScan,
        confirmScan: ConfirmScanResults,
        observeCatalog: ObserveCardCatalog,
        language: Language,
    ) = ScanViewModel.Factory(observeProgress, finishScan, confirmScan, observeCatalog, language)

    @Provides
    fun collectionViewModelFactory(
        observeCatalog: ObserveCardCatalog,
        observeCollection: ObserveCollection,
        adjustOwnedCount: AdjustOwnedCount,
        syncCardCatalog: SyncCardCatalog,
        language: Language,
    ) = CollectionViewModel.Factory(observeCatalog, observeCollection, adjustOwnedCount, syncCardCatalog, language)

    private const val MASTER_DATASET_ASSET = "master_cards.json"
    private const val CARD_INDEX_ASSET = "card_index.bin"
}
