package dev.pocketdeck.adapter.ui.collection

import app.cash.turbine.test
import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.catalog.CardCatalogSource
import dev.pocketdeck.application.catalog.CatalogSyncFailure
import dev.pocketdeck.application.catalog.ObserveCardCatalogService
import dev.pocketdeck.application.catalog.SourceUnavailable
import dev.pocketdeck.application.catalog.SyncCardCatalogService
import dev.pocketdeck.application.collection.AdjustOwnedCountService
import dev.pocketdeck.application.collection.ObserveCollectionService
import dev.pocketdeck.application.fakes.FakeCardCatalogSource
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.domain.basicPokemon
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.magmar
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val catalogs = InMemoryCardCatalogRepository()
    private val collections = InMemoryCollectionRepository()
    private val source = FakeCardCatalogSource(Outcome.Success(listOf(charmander, magmar)))

    @BeforeEach
    fun installMainDispatcher() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun removeMainDispatcher() = Dispatchers.resetMain()

    private fun viewModel(
        catalogSource: CardCatalogSource = source,
        language: Language = Language.ENGLISH,
    ) = CollectionViewModel(
        observeCatalog = ObserveCardCatalogService(catalogs),
        observeCollection = ObserveCollectionService(collections),
        adjustOwnedCount = AdjustOwnedCountService(catalogs, collections),
        syncCardCatalog = SyncCardCatalogService(catalogSource, catalogs),
        language = language,
        listingDispatcher = dispatcher,
    )

    private suspend fun TestScope.settled(viewModel: CollectionViewModel): CollectionUiState {
        var latest = viewModel.state.value
        viewModel.state.test {
            advanceUntilIdle()
            latest = expectMostRecentItem()
        }
        return latest
    }

    @Test
    fun `downloads the catalog on first launch when none is stored`() =
        runTest(dispatcher) {
            val state = settled(viewModel())

            state.items.map { it.name } shouldBe listOf("Charmander", "Magmar")
            state.sync shouldBe SyncState.Idle
        }

    @Test
    fun `downloads again when the stored cards lack the game's card numbers`() =
        runTest(dispatcher) {
            catalogs.replaceAll(listOf(charmander.copy(gameEntityId = null)))

            settled(viewModel())

            catalogs.current().cards.map { it.name.value } shouldBe listOf("Charmander", "Magmar")
        }

    @Test
    fun `does not download again when a catalog is already stored`() =
        runTest(dispatcher) {
            catalogs.replaceAll(listOf(charmander))

            val state = settled(viewModel())

            state.items.map { it.name } shouldBe listOf("Charmander")
        }

    @Test
    fun `shows the failure when the first download fails`() =
        runTest(dispatcher) {
            source.result = Outcome.Failure(SourceUnavailable("offline"))

            val state = settled(viewModel())

            state.sync shouldBe SyncState.Failed(CatalogSyncFailure.SourceNotReachable("offline"))
            state.items shouldBe emptyList()
        }

    @Test
    fun `adjusting a card updates its owned count`() =
        runTest(dispatcher) {
            catalogs.replaceAll(listOf(charmander))
            val viewModel = viewModel()
            settled(viewModel)

            viewModel.adjust(charmander.id, delta = 1)
            viewModel.adjust(charmander.id, delta = 1)

            val state = settled(viewModel)
            state.items.single().copies shouldBe 2
            state.totalOwned shouldBe 2
        }

    @Test
    fun `search and owned-only filter the listing`() =
        runTest(dispatcher) {
            catalogs.replaceAll(listOf(charmander, magmar))
            collections.save(collections.current().withCopies(magmar.id, 1))
            val viewModel = viewModel()

            viewModel.onQueryChange("mag")
            settled(viewModel).items.map { it.name } shouldBe listOf("Magmar")

            viewModel.onQueryChange("")
            viewModel.onOwnedOnlyChange(true)
            val state = settled(viewModel)
            state.items.map { it.name } shouldBe listOf("Magmar")
            state.query shouldBe ""
            state.ownedOnly shouldBe true
        }

    @Test
    fun `a manual sync replaces the catalog and reports success`() =
        runTest(dispatcher) {
            catalogs.replaceAll(listOf(charmander))
            val viewModel = viewModel()
            settled(viewModel)

            viewModel.sync()

            settled(viewModel).items.map { it.name } shouldBe listOf("Charmander", "Magmar")
        }

    @Test
    fun `reports the syncing state while the download runs`() =
        runTest(dispatcher) {
            catalogs.replaceAll(listOf(charmander))
            val download = CompletableDeferred<Unit>()
            val slowSource =
                object : CardCatalogSource {
                    override suspend fun fetchAll(): Outcome<List<Card>, SourceUnavailable> {
                        download.await()
                        return Outcome.Success(listOf(charmander, magmar))
                    }
                }
            val viewModel = viewModel(slowSource)

            viewModel.state.test {
                awaitItem().sync shouldBe SyncState.Idle
                viewModel.sync()
                awaitItem().sync shouldBe SyncState.Syncing
                download.complete(Unit)
                awaitItem().sync shouldBe SyncState.Idle
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `displays card names in the configured language`() =
        runTest(dispatcher) {
            val glurak = basicPokemon("Charizard ex", id = "a1-036").copy(germanName = CardName("Glurak ex"))
            catalogs.replaceAll(listOf(glurak))

            val state = settled(viewModel(language = Language.GERMAN))

            state.items.single().name shouldBe "Glurak ex"
        }

    @Test
    fun `typed text is available at once while the list follows after a short pause`() =
        runTest(dispatcher) {
            catalogs.replaceAll(listOf(charmander, magmar))
            val viewModel = viewModel()
            settled(viewModel).items.size shouldBe 2

            viewModel.onQueryChange("m")
            viewModel.onQueryChange("ma")
            viewModel.onQueryChange("mag")

            // The text field must never wait for the filtering, or keystrokes get lost.
            viewModel.queryText.value shouldBe "mag"
            viewModel.state.value.items.size shouldBe 2

            settled(viewModel).items.map { it.name } shouldBe listOf("Magmar")
        }
}
