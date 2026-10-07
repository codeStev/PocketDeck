package dev.pocketdeck.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pocketdeck.LogAdvisorDiagnostics
import dev.pocketdeck.LogScanDiagnostics
import dev.pocketdeck.adapter.carddb.MasterCatalogSource
import dev.pocketdeck.adapter.limitless.LimitlessMetaDecklistSource
import dev.pocketdeck.adapter.llm.AnthropicDeckAdvisor
import dev.pocketdeck.adapter.llm.OpenRouterDeckAdvisor
import dev.pocketdeck.adapter.llm.OpenRouterModelCatalog
import dev.pocketdeck.adapter.persistence.PocketDeckDatabases
import dev.pocketdeck.adapter.persistence.PocketDeckStorage
import dev.pocketdeck.adapter.persistence.SharedPreferencesAdvisorSettingsStore
import dev.pocketdeck.adapter.persistence.SharedPreferencesConsentStore
import dev.pocketdeck.adapter.recognition.BadgeDigitReader
import dev.pocketdeck.adapter.recognition.CardIndex
import dev.pocketdeck.adapter.recognition.CollectionGridDetector
import dev.pocketdeck.adapter.recognition.DetectorGridLocator
import dev.pocketdeck.adapter.recognition.DigitTemplates
import dev.pocketdeck.adapter.recognition.GridCardRecognizer
import dev.pocketdeck.adapter.ui.collection.CollectionViewModel
import dev.pocketdeck.adapter.ui.decks.AiAssistant
import dev.pocketdeck.adapter.ui.decks.DecksViewModel
import dev.pocketdeck.adapter.ui.scan.ConsentAccess
import dev.pocketdeck.adapter.ui.scan.ScanViewModel
import dev.pocketdeck.adapter.ui.settings.SettingsViewModel
import dev.pocketdeck.application.advice.AdvisorDiagnostics
import dev.pocketdeck.application.advice.AdvisorModelCatalog
import dev.pocketdeck.application.advice.AdvisorProvider
import dev.pocketdeck.application.advice.AdvisorSettingsStore
import dev.pocketdeck.application.advice.AskAiForDeck
import dev.pocketdeck.application.advice.AskAiForDeckService
import dev.pocketdeck.application.advice.ConfiguredDeckAdvisor
import dev.pocketdeck.application.advice.DeckAdvisor
import dev.pocketdeck.application.advice.ListSuitableModels
import dev.pocketdeck.application.advice.ListSuitableModelsService
import dev.pocketdeck.application.advice.ObserveAdvisorSettingsService
import dev.pocketdeck.application.advice.SaveAdvisorSettingsService
import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.application.catalog.CardCatalogSource
import dev.pocketdeck.application.catalog.ObserveCardCatalog
import dev.pocketdeck.application.catalog.ObserveCardCatalogService
import dev.pocketdeck.application.catalog.SyncCardCatalog
import dev.pocketdeck.application.catalog.SyncCardCatalogService
import dev.pocketdeck.application.collection.AdjustOwnedCount
import dev.pocketdeck.application.collection.AdjustOwnedCountService
import dev.pocketdeck.application.collection.CollectionBackup
import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.application.collection.ObserveBackupAvailable
import dev.pocketdeck.application.collection.ObserveBackupAvailableService
import dev.pocketdeck.application.collection.ObserveCollection
import dev.pocketdeck.application.collection.ObserveCollectionService
import dev.pocketdeck.application.collection.RestorePreviousCollection
import dev.pocketdeck.application.collection.RestorePreviousCollectionService
import dev.pocketdeck.application.consent.ConsentStore
import dev.pocketdeck.application.consent.GrantConsentService
import dev.pocketdeck.application.consent.ObserveConsentService
import dev.pocketdeck.application.deckcode.ExportDeckCode
import dev.pocketdeck.application.deckcode.ExportDeckCodeService
import dev.pocketdeck.application.meta.InMemoryMetaSnapshotRepository
import dev.pocketdeck.application.meta.MetaDecklistSource
import dev.pocketdeck.application.meta.MetaSnapshotRepository
import dev.pocketdeck.application.meta.RefreshMetaSnapshot
import dev.pocketdeck.application.meta.RefreshMetaSnapshotService
import dev.pocketdeck.application.scan.CardRecognizer
import dev.pocketdeck.application.scan.ConfirmScanResults
import dev.pocketdeck.application.scan.ConfirmScanResultsService
import dev.pocketdeck.application.scan.FinishCollectionScan
import dev.pocketdeck.application.scan.FinishCollectionScanService
import dev.pocketdeck.application.scan.GridLocator
import dev.pocketdeck.application.scan.InMemoryScanSessionStore
import dev.pocketdeck.application.scan.ObserveScanProgress
import dev.pocketdeck.application.scan.ObserveScanProgressService
import dev.pocketdeck.application.scan.ScanDiagnostics
import dev.pocketdeck.application.scan.ScanSessionStore
import dev.pocketdeck.application.scan.StartCollectionScan
import dev.pocketdeck.application.scan.StartCollectionScanService
import dev.pocketdeck.application.scan.SubmitScanFrame
import dev.pocketdeck.application.scan.SubmitScanFrameService
import dev.pocketdeck.application.suggest.BuildDeck
import dev.pocketdeck.application.suggest.BuildDeckService
import dev.pocketdeck.application.suggest.HeuristicSuggestionStrategy
import dev.pocketdeck.application.suggest.ListDeckCentrepieces
import dev.pocketdeck.application.suggest.ListDeckCentrepiecesService
import dev.pocketdeck.application.suggest.MetaBasedSuggestionStrategy
import dev.pocketdeck.application.suggest.SuggestDecks
import dev.pocketdeck.application.suggest.SuggestDecksService
import dev.pocketdeck.domain.card.Language
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
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
    fun collectionBackup(storage: PocketDeckStorage): CollectionBackup = storage.backup

    @Provides
    fun observeBackupAvailable(backup: CollectionBackup): ObserveBackupAvailable = ObserveBackupAvailableService(backup)

    @Provides
    fun restorePreviousCollection(
        collections: CollectionRepository,
        backup: CollectionBackup,
    ): RestorePreviousCollection = RestorePreviousCollectionService(collections, backup)

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
    fun gridLocator(): GridLocator = DetectorGridLocator(CollectionGridDetector())

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
    @Singleton
    fun scanDiagnostics(): ScanDiagnostics = LogScanDiagnostics()

    @Provides
    fun finishCollectionScan(
        sessions: ScanSessionStore,
        diagnostics: ScanDiagnostics,
    ): FinishCollectionScan = FinishCollectionScanService(sessions, diagnostics)

    @Provides
    fun observeScanProgress(sessions: ScanSessionStore): ObserveScanProgress = ObserveScanProgressService(sessions)

    @Provides
    fun confirmScanResults(
        catalogs: CardCatalogRepository,
        collections: CollectionRepository,
        backup: CollectionBackup,
    ): ConfirmScanResults = ConfirmScanResultsService(catalogs, collections, backup)

    // --- tournament decks and deck suggestions
    @Provides
    @Singleton
    fun httpClient(): HttpClient =
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            // A large model can think for a minute or more before it answers.
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }

    // --- AI advisors
    @Provides
    @Singleton
    fun advisorSettingsStore(
        @ApplicationContext context: Context,
    ): AdvisorSettingsStore = SharedPreferencesAdvisorSettingsStore(context)

    @Provides
    @Singleton
    fun advisorDiagnostics(): AdvisorDiagnostics = LogAdvisorDiagnostics()

    @Provides
    fun deckAdvisor(
        settings: AdvisorSettingsStore,
        client: HttpClient,
        diagnostics: AdvisorDiagnostics,
    ): DeckAdvisor =
        ConfiguredDeckAdvisor(
            settings,
            mapOf(
                AdvisorProvider.ANTHROPIC to AnthropicDeckAdvisor(client, diagnostics = diagnostics),
                AdvisorProvider.OPENROUTER to OpenRouterDeckAdvisor(client, diagnostics = diagnostics),
            ),
            diagnostics,
        )

    @Provides
    fun askAiForDeck(
        catalogs: CardCatalogRepository,
        collections: CollectionRepository,
        snapshots: MetaSnapshotRepository,
        advisor: DeckAdvisor,
        diagnostics: AdvisorDiagnostics,
    ): AskAiForDeck = AskAiForDeckService(catalogs, collections, snapshots, advisor, diagnostics = diagnostics)

    @Provides
    fun aiAssistant(
        askAiForDeck: AskAiForDeck,
        settings: AdvisorSettingsStore,
    ) = AiAssistant(askAiForDeck, ObserveAdvisorSettingsService(settings))

    @Provides
    fun advisorModelCatalog(client: HttpClient): AdvisorModelCatalog = OpenRouterModelCatalog(client)

    @Provides
    fun listSuitableModels(catalog: AdvisorModelCatalog): ListSuitableModels = ListSuitableModelsService(catalog)

    @Provides
    fun settingsViewModelFactory(
        settings: AdvisorSettingsStore,
        listModels: ListSuitableModels,
    ) = SettingsViewModel.Factory(
        ObserveAdvisorSettingsService(settings),
        SaveAdvisorSettingsService(settings),
        listModels,
    )

    @Provides
    fun metaDecklistSource(client: HttpClient): MetaDecklistSource = LimitlessMetaDecklistSource(client)

    @Provides
    @Singleton
    fun metaSnapshotRepository(): MetaSnapshotRepository = InMemoryMetaSnapshotRepository()

    @Provides
    fun refreshMetaSnapshot(
        source: MetaDecklistSource,
        snapshots: MetaSnapshotRepository,
    ): RefreshMetaSnapshot = RefreshMetaSnapshotService(source, snapshots)

    @Provides
    fun suggestDecks(
        catalogs: CardCatalogRepository,
        collections: CollectionRepository,
        snapshots: MetaSnapshotRepository,
    ): SuggestDecks =
        SuggestDecksService(
            catalogs,
            collections,
            snapshots,
            listOf(MetaBasedSuggestionStrategy(), HeuristicSuggestionStrategy()),
        )

    // --- driving adapter wiring
    @Provides
    fun decksViewModelFactory(
        suggestDecks: SuggestDecks,
        refreshMetaSnapshot: RefreshMetaSnapshot,
        exportDeckCode: ExportDeckCode,
        buildDeck: BuildDeck,
        listDeckCentrepieces: ListDeckCentrepieces,
        aiAssistant: AiAssistant,
        language: Language,
    ) = DecksViewModel.Factory(
        suggestDecks,
        refreshMetaSnapshot,
        exportDeckCode,
        buildDeck,
        listDeckCentrepieces,
        aiAssistant,
        language,
    )

    @Provides
    fun buildDeck(
        catalogs: CardCatalogRepository,
        collections: CollectionRepository,
        snapshots: MetaSnapshotRepository,
    ): BuildDeck = BuildDeckService(catalogs, collections, snapshots)

    @Provides
    fun listDeckCentrepieces(
        catalogs: CardCatalogRepository,
        collections: CollectionRepository,
        snapshots: MetaSnapshotRepository,
    ): ListDeckCentrepieces = ListDeckCentrepiecesService(catalogs, collections, snapshots)

    @Provides
    fun exportDeckCode(): ExportDeckCode = ExportDeckCodeService()

    @Provides
    fun scanViewModelFactory(
        observeProgress: ObserveScanProgress,
        finishScan: FinishCollectionScan,
        confirmScan: ConfirmScanResults,
        observeCatalog: ObserveCardCatalog,
        observeCollection: ObserveCollection,
        observeBackup: ObserveBackupAvailable,
        restorePrevious: RestorePreviousCollection,
        language: Language,
        consent: ConsentStore,
    ) = ScanViewModel.Factory(
        observeProgress,
        finishScan,
        confirmScan,
        observeCatalog,
        observeCollection,
        observeBackup,
        restorePrevious,
        language,
        ConsentAccess(ObserveConsentService(consent), GrantConsentService(consent)),
    )

    @Provides
    @Singleton
    fun consentStore(
        @ApplicationContext context: Context,
    ): ConsentStore = SharedPreferencesConsentStore(context)

    @Provides
    fun collectionViewModelFactory(
        observeCatalog: ObserveCardCatalog,
        observeCollection: ObserveCollection,
        adjustOwnedCount: AdjustOwnedCount,
        syncCardCatalog: SyncCardCatalog,
        language: Language,
    ) = CollectionViewModel.Factory(observeCatalog, observeCollection, adjustOwnedCount, syncCardCatalog, language)

    private const val CONNECT_TIMEOUT_MS = 15_000L
    private const val REQUEST_TIMEOUT_MS = 180_000L
    private const val MASTER_DATASET_ASSET = "master_cards.json"
    private const val CARD_INDEX_ASSET = "card_index.bin"
}
