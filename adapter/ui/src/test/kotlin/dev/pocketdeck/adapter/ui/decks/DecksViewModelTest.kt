package dev.pocketdeck.adapter.ui.decks

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.deckcode.DeckCode
import dev.pocketdeck.application.deckcode.DeckCodeFailure
import dev.pocketdeck.application.deckcode.ExportDeckCode
import dev.pocketdeck.application.deckcode.ExportDeckCodeService
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.application.meta.InMemoryMetaSnapshotRepository
import dev.pocketdeck.application.meta.MetaSourceFailure
import dev.pocketdeck.application.meta.RefreshMetaSnapshot
import dev.pocketdeck.application.meta.RefreshMetaSnapshotService
import dev.pocketdeck.application.suggest.DeckSuggestionStrategy
import dev.pocketdeck.application.suggest.SuggestDecksService
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.CompletenessCalculator
import dev.pocketdeck.domain.deck.DeckSuggestion
import dev.pocketdeck.domain.deck.SuggestionOrigin
import dev.pocketdeck.domain.meta.MetaSnapshot
import dev.pocketdeck.domain.validFireDeck
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DecksViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val snapshots = InMemoryMetaSnapshotRepository()

    @BeforeEach
    fun installMainDispatcher() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun removeMainDispatcher() = Dispatchers.resetMain()

    private val strategy =
        DeckSuggestionStrategy {
            val deck = validFireDeck()
            val completeness =
                CompletenessCalculator().calculate(
                    deck,
                    Inventory.of(CardCollection.EMPTY, CardCatalog.EMPTY),
                )
            listOf(DeckSuggestion("Charizard", SuggestionOrigin.META, deck, completeness, 1.0))
        }

    private fun viewModel(
        refresh: RefreshMetaSnapshot,
        export: ExportDeckCode = ExportDeckCodeService(),
    ): DecksViewModel {
        val suggest =
            SuggestDecksService(
                InMemoryCardCatalogRepository(),
                InMemoryCollectionRepository(),
                snapshots,
                listOf(strategy),
            )
        return DecksViewModel(suggest, refresh, export, Language.ENGLISH)
    }

    private val succeeding = RefreshMetaSnapshot { Outcome.Success(MetaSnapshot.EMPTY) }

    @Test
    fun `shows the suggestions`() =
        runTest(dispatcher) {
            val viewModel = viewModel(succeeding)

            advanceUntilIdle()

            viewModel.state.value.items
                .single()
                .title shouldBe "Charizard"
        }

    @Test
    fun `refreshes the field when it opens`() =
        runTest(dispatcher) {
            var refreshes = 0
            val viewModel = viewModel({ refreshes++.let { Outcome.Success(MetaSnapshot.EMPTY) } })

            advanceUntilIdle()

            refreshes shouldBe 1
            viewModel.state.value.refreshing shouldBe false
        }

    @Test
    fun `tells the player when the field could not be loaded and keeps showing what it has`() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    RefreshMetaSnapshotService(
                        { Outcome.Failure(MetaSourceFailure.NotReachable("offline")) },
                        snapshots,
                    ),
                )

            advanceUntilIdle()

            viewModel.state.value.notice shouldBe DecksNotice.NOT_REACHABLE
            viewModel.state.value.items.size shouldBe 1
        }

    @Test
    fun `tells the player when no tournament deck was found`() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(RefreshMetaSnapshotService({ Outcome.Failure(MetaSourceFailure.NoDecklists) }, snapshots))

            advanceUntilIdle()

            viewModel.state.value.notice shouldBe DecksNotice.NO_DECKLISTS
        }

    @Test
    fun `a successful refresh clears the notice`() =
        runTest(dispatcher) {
            var succeed = false
            val viewModel =
                viewModel(
                    RefreshMetaSnapshot {
                        if (succeed) {
                            Outcome.Success(
                                MetaSnapshot.EMPTY,
                            )
                        } else {
                            Outcome.Failure(MetaSourceFailure.NoDecklists)
                        }
                    },
                )
            advanceUntilIdle()

            succeed = true
            viewModel.onRefresh()
            advanceUntilIdle()

            viewModel.state.value.notice shouldBe null
        }

    @Test
    fun `shows the code of a deck the game can import`() =
        runTest(dispatcher) {
            val code = DeckCode("AAAA")
            val viewModel = viewModel(succeeding, { Outcome.Success(code) })
            advanceUntilIdle()

            viewModel.onShowCode(
                viewModel.state.value.items
                    .single()
                    .key,
            )

            viewModel.state.value.code shouldBe DeckCodeUi.Ready("Charizard", "AAAA")
        }

    @Test
    fun `explains why a deck has no code`() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(succeeding, { Outcome.Failure(DeckCodeFailure.UnknownToGame(listOf(CardName("Mystery")))) })
            advanceUntilIdle()

            viewModel.onShowCode(
                viewModel.state.value.items
                    .single()
                    .key,
            )

            viewModel.state.value.code shouldBe DeckCodeUi.Unavailable("Charizard", listOf("Mystery"))
        }

    @Test
    fun `closing the code hides it`() =
        runTest(dispatcher) {
            val viewModel = viewModel(succeeding, { Outcome.Success(DeckCode("AAAA")) })
            advanceUntilIdle()
            viewModel.onShowCode(
                viewModel.state.value.items
                    .single()
                    .key,
            )

            viewModel.onCloseCode()

            viewModel.state.value.code shouldBe null
        }
}
