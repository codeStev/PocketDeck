package dev.pocketdeck.adapter.ui.decks

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.advice.AdvisorFailure
import dev.pocketdeck.application.advice.AdvisorProvider
import dev.pocketdeck.application.advice.AdvisorSettings
import dev.pocketdeck.application.advice.AdvisorSettingsStore
import dev.pocketdeck.application.advice.AskAiFailure
import dev.pocketdeck.application.advice.AskAiForDeck
import dev.pocketdeck.application.advice.ObserveAdvisorSettingsService
import dev.pocketdeck.application.deckcode.DeckCode
import dev.pocketdeck.application.deckcode.DeckCodeFailure
import dev.pocketdeck.application.deckcode.ExportDeckCode
import dev.pocketdeck.application.deckcode.ExportDeckCodeService
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.application.fakes.InMemorySavedDeckRepository
import dev.pocketdeck.application.meta.InMemoryMetaSnapshotRepository
import dev.pocketdeck.application.meta.MetaSourceFailure
import dev.pocketdeck.application.meta.RefreshMetaSnapshot
import dev.pocketdeck.application.meta.RefreshMetaSnapshotService
import dev.pocketdeck.application.opening.AnalyseDeckOpening
import dev.pocketdeck.application.savedeck.DeleteSavedDeckService
import dev.pocketdeck.application.savedeck.ObserveSavedDecksService
import dev.pocketdeck.application.savedeck.RenameSavedDeckService
import dev.pocketdeck.application.savedeck.SaveDeckService
import dev.pocketdeck.application.suggest.BuildDeck
import dev.pocketdeck.application.suggest.BuildDeckFailure
import dev.pocketdeck.application.suggest.DeckSuggestionStrategy
import dev.pocketdeck.application.suggest.ListDeckCentrepieces
import dev.pocketdeck.application.suggest.SuggestDecksService
import dev.pocketdeck.domain.builder.DeckFocus
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EffectTag
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.charizardEx
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.CompletenessCalculator
import dev.pocketdeck.domain.deck.DeckSuggestion
import dev.pocketdeck.domain.deck.Substitution
import dev.pocketdeck.domain.deck.SuggestionOrigin
import dev.pocketdeck.domain.meta.MetaSnapshot
import dev.pocketdeck.domain.opening.OpeningAnalysis
import dev.pocketdeck.domain.opening.PokemonOdds
import dev.pocketdeck.domain.opening.TurnOdds
import dev.pocketdeck.domain.validFireDeck
import io.kotest.matchers.shouldBe
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
import java.time.Clock
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class DecksViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val snapshots = InMemoryMetaSnapshotRepository()

    @BeforeEach
    fun installMainDispatcher() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun removeMainDispatcher() = Dispatchers.resetMain()

    private val savedRepository = InMemorySavedDeckRepository()
    private val savedDecks =
        SavedDecks(
            ObserveSavedDecksService(
                savedRepository,
                InMemoryCardCatalogRepository(CardCatalog(validFireDeck().entries.map { it.card })),
                InMemoryCollectionRepository(),
            ),
            SaveDeckService(savedRepository, Clock.systemUTC()),
            RenameSavedDeckService(savedRepository),
            DeleteSavedDeckService(savedRepository),
        )

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

    private var analyses = 0
    private val analyseOpening =
        AnalyseDeckOpening {
            analyses++
            OpeningAnalysis(
                goingFirst = listOf(PokemonOdds(charmander, (1..4).map { TurnOdds(it, it / 10.0, it / 10.0) })),
                goingSecond = emptyList(),
            )
        }

    private fun viewModel(
        refresh: RefreshMetaSnapshot,
        export: ExportDeckCode = ExportDeckCodeService(),
        build: BuildDeck = BuildDeck { Outcome.Failure(BuildDeckFailure.NotEnoughCards) },
        centrepieces: ListDeckCentrepieces = ListDeckCentrepieces { emptyList() },
        askAi: AskAiForDeck = AskAiForDeck { Outcome.Failure(AskAiFailure.NoValidDeck(emptyList())) },
        advisor: AdvisorSettingsStore = InMemoryAdvisorSettingsStore(),
    ): DecksViewModel {
        val suggest =
            SuggestDecksService(
                InMemoryCardCatalogRepository(),
                InMemoryCollectionRepository(),
                snapshots,
                listOf(strategy),
                EmptyCoroutineContext,
            )
        return DecksViewModel(
            suggest,
            refresh,
            export,
            build,
            centrepieces,
            aiAssistant(askAi, advisor),
            savedDecks,
            analyseOpening,
            Language.ENGLISH,
        )
    }

    private class InMemoryAdvisorSettingsStore(
        initial: AdvisorSettings = AdvisorSettings(),
    ) : AdvisorSettingsStore {
        private val state = kotlinx.coroutines.flow.MutableStateFlow(initial)

        override fun observe() = state

        override suspend fun current() = state.value

        override suspend fun save(settings: AdvisorSettings) {
            state.value = settings
        }
    }

    private fun aiAssistant(
        ask: AskAiForDeck,
        store: AdvisorSettingsStore,
    ) = AiAssistant(ask, ObserveAdvisorSettingsService(store))

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

            viewModel.state.value.code shouldBe DeckCodeUi.Ready("Charizard", "AAAA", emptyList())
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

    @Test
    fun `the code of a deck with stand-ins says which cards it swaps`() =
        runTest(dispatcher) {
            val deck = validFireDeck()
            val completeness =
                CompletenessCalculator().calculate(
                    deck,
                    Inventory.of(CardCollection.EMPTY, CardCatalog.EMPTY),
                )
            val swap = Substitution(charizardEx, charmander, copies = 2, sharedEffects = setOf(EffectTag.HEAL))
            val adapted = DeckSuggestion("Charizard", SuggestionOrigin.META, deck, completeness, 1.0, listOf(swap))
            val suggest =
                SuggestDecksService(
                    InMemoryCardCatalogRepository(),
                    InMemoryCollectionRepository(),
                    InMemoryMetaSnapshotRepository(),
                    listOf(DeckSuggestionStrategy { listOf(adapted) }),
                    EmptyCoroutineContext,
                )
            val viewModel =
                DecksViewModel(
                    suggest,
                    succeeding,
                    { Outcome.Success(DeckCode("AAAA")) },
                    { Outcome.Failure(BuildDeckFailure.NotEnoughCards) },
                    { emptyList() },
                    aiAssistant(
                        AskAiForDeck { Outcome.Failure(AskAiFailure.NoValidDeck(emptyList())) },
                        InMemoryAdvisorSettingsStore(),
                    ),
                    savedDecks,
                    analyseOpening,
                    Language.ENGLISH,
                )
            advanceUntilIdle()

            viewModel.onShowCode(
                viewModel.state.value.items
                    .single()
                    .key,
            )

            (viewModel.state.value.code as DeckCodeUi.Ready).substitutes shouldBe
                listOf(SubstituteLine("Charmander", "Charizard ex", 2, listOf(EffectTag.HEAL)))
        }

    private val builtSuggestion: DeckSuggestion
        get() {
            val deck = validFireDeck()
            val completeness =
                CompletenessCalculator().calculate(
                    deck,
                    Inventory.of(CardCollection.EMPTY, CardCatalog.EMPTY),
                )
            return DeckSuggestion(
                "Charmander",
                SuggestionOrigin.HEURISTIC,
                deck,
                completeness,
                0.4,
                centrepiece = charmander,
            )
        }

    @Test
    fun `lists the pokemon a deck can be built around when the player opens the builder`() =
        runTest(dispatcher) {
            val viewModel = viewModel(succeeding, centrepieces = { listOf(charmander, charizardEx) })
            advanceUntilIdle()

            viewModel.onOpenBuilder()
            advanceUntilIdle()

            viewModel.state.value.centrepieces
                ?.map { it.name } shouldBe listOf("Charmander", "Charizard ex")
        }

    @Test
    fun `builds a deck around the chosen pokemon and shows it first`() =
        runTest(dispatcher) {
            var asked: DeckFocus? = null
            val viewModel =
                viewModel(
                    succeeding,
                    build = { focus ->
                        asked = focus
                        Outcome.Success(builtSuggestion)
                    },
                    centrepieces = { listOf(charmander) },
                )
            advanceUntilIdle()
            viewModel.onOpenBuilder()
            advanceUntilIdle()

            viewModel.onBuild(charmander.id.value)
            advanceUntilIdle()

            asked?.core shouldBe charmander
            viewModel.state.value.items
                .first()
                .origin shouldBe SuggestionOrigin.HEURISTIC
            viewModel.state.value.centrepieces shouldBe null
            viewModel.state.value.building shouldBe false
        }

    @Test
    fun `builds the best deck when no pokemon is chosen`() =
        runTest(dispatcher) {
            var asked: DeckFocus? = null
            val viewModel =
                viewModel(succeeding, build = { focus ->
                    asked = focus
                    Outcome.Success(builtSuggestion)
                })
            advanceUntilIdle()

            viewModel.onBuild(null)
            advanceUntilIdle()

            asked shouldBe DeckFocus()
        }

    @Test
    fun `tells the player when the collection is too small to build a deck`() =
        runTest(dispatcher) {
            val viewModel = viewModel(succeeding)
            advanceUntilIdle()

            viewModel.onBuild(null)
            advanceUntilIdle()

            viewModel.state.value.notice shouldBe DecksNotice.BUILD_FAILED
            viewModel.state.value.building shouldBe false
        }

    @Test
    fun `the code of a built deck can be shown`() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    succeeding,
                    { Outcome.Success(DeckCode("AAAA")) },
                    build = { Outcome.Success(builtSuggestion) },
                )
            advanceUntilIdle()
            viewModel.onBuild(null)
            advanceUntilIdle()

            viewModel.onShowCode(
                viewModel.state.value.items
                    .first()
                    .key,
            )

            (viewModel.state.value.code as DeckCodeUi.Ready).text shouldBe "AAAA"
        }

    private val aiSuggestion: DeckSuggestion
        get() {
            val deck = validFireDeck()
            val completeness =
                CompletenessCalculator().calculate(
                    deck,
                    Inventory.of(CardCollection.EMPTY, CardCatalog.EMPTY),
                )
            return DeckSuggestion(
                "Fire Rush",
                SuggestionOrigin.AI,
                deck,
                completeness,
                1.0,
                rationale = "Fast attackers.",
            )
        }

    private val configured = AdvisorSettings().withKey(AdvisorProvider.OPENROUTER, "sk-or")

    @Test
    fun `knows whether an AI is set up`() =
        runTest(dispatcher) {
            val without = viewModel(succeeding)
            val with = viewModel(succeeding, advisor = InMemoryAdvisorSettingsStore(configured))
            advanceUntilIdle()

            without.state.value.aiConfigured shouldBe false
            with.state.value.aiConfigured shouldBe true
        }

    @Test
    fun `shows the deck the AI built first, with its reasons`() =
        runTest(dispatcher) {
            var wish: String? = "unset"
            val viewModel =
                viewModel(succeeding, askAi = { w ->
                    wish = w
                    Outcome.Success(aiSuggestion)
                }, advisor = InMemoryAdvisorSettingsStore(configured))
            advanceUntilIdle()

            viewModel.onAskAi("a fast deck")
            advanceUntilIdle()

            wish shouldBe "a fast deck"
            viewModel.state.value.items
                .first()
                .origin shouldBe SuggestionOrigin.AI
            viewModel.state.value.items
                .first()
                .rationale shouldBe "Fast attackers."
            viewModel.state.value.asking shouldBe false
        }

    @Test
    fun `sends no wish when the player wrote none`() =
        runTest(dispatcher) {
            var wish: String? = "unset"
            val viewModel =
                viewModel(succeeding, askAi = { w ->
                    wish = w
                    Outcome.Success(aiSuggestion)
                }, advisor = InMemoryAdvisorSettingsStore(configured))
            advanceUntilIdle()

            viewModel.onAskAi("   ")
            advanceUntilIdle()

            wish shouldBe null
        }

    @Test
    fun `points to the settings instead of asking when no key is set up`() =
        runTest(dispatcher) {
            val viewModel = viewModel(succeeding)
            advanceUntilIdle()

            viewModel.onAskAi("anything")
            advanceUntilIdle()

            viewModel.state.value.notice shouldBe DecksNotice.AI_NEEDS_KEY
            viewModel.state.value.asking shouldBe false
        }

    private suspend fun TestScope.noticeFor(failure: AskAiFailure): DecksNotice? {
        val viewModel =
            viewModel(
                succeeding,
                askAi = { Outcome.Failure(failure) },
                advisor = InMemoryAdvisorSettingsStore(configured),
            )
        advanceUntilIdle()
        viewModel.onAskAi(null)
        advanceUntilIdle()
        return viewModel.state.value.notice
    }

    @Test
    fun `tells the player why the AI could not help`() =
        runTest(dispatcher) {
            noticeFor(AskAiFailure.AdvisorFailed(AdvisorFailure.InvalidKey)) shouldBe DecksNotice.AI_INVALID_KEY
            noticeFor(AskAiFailure.AdvisorFailed(AdvisorFailure.Busy("slow"))) shouldBe DecksNotice.AI_BUSY
            noticeFor(AskAiFailure.AdvisorFailed(AdvisorFailure.NotReachable("offline"))) shouldBe
                DecksNotice.AI_UNREACHABLE
            noticeFor(AskAiFailure.AdvisorFailed(AdvisorFailure.Rejected("no such model"))) shouldBe
                DecksNotice.AI_REJECTED
            noticeFor(AskAiFailure.NoValidDeck(listOf("x"))) shouldBe DecksNotice.AI_NO_VALID_DECK
        }

    @Test
    fun `passes on what the service said`() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    succeeding,
                    askAi = { Outcome.Failure(AskAiFailure.AdvisorFailed(AdvisorFailure.Rejected("no such model"))) },
                    advisor = InMemoryAdvisorSettingsStore(configured),
                )
            advanceUntilIdle()

            viewModel.onAskAi(null)
            advanceUntilIdle()

            viewModel.state.value.noticeDetail shouldBe "no such model"
        }

    @Test
    fun `a saved deck shows up in the saved list with its own key`() =
        runTest(dispatcher) {
            val viewModel = viewModel(succeeding)
            advanceUntilIdle()

            viewModel.onSave(
                viewModel.state.value.items
                    .single()
                    .key,
            )
            advanceUntilIdle()

            val saved =
                viewModel.state.value.saved
                    .single()
            saved.title shouldBe "Charizard"
            saved.savedId shouldBe 1L
            saved.key shouldBe "saved-1"
        }

    @Test
    fun `renaming and deleting a saved deck asks first and then changes the list`() =
        runTest(dispatcher) {
            val viewModel = viewModel(succeeding)
            advanceUntilIdle()
            viewModel.onSave(
                viewModel.state.value.items
                    .single()
                    .key,
            )
            advanceUntilIdle()

            viewModel.onRenameSavedRequested(
                viewModel.state.value.saved
                    .single(),
            )
            viewModel.onSavedRenamed("My deck")
            advanceUntilIdle()
            viewModel.state.value.saved
                .single()
                .title shouldBe "My deck"
            viewModel.state.value.savedDialog shouldBe null

            viewModel.onDeleteSavedRequested(
                viewModel.state.value.saved
                    .single(),
            )
            viewModel.state.value.savedDialog shouldBe SavedDeckDialog.ConfirmDelete(1L, "My deck")
            viewModel.onSavedDeleteConfirmed()
            advanceUntilIdle()
            viewModel.state.value.saved shouldBe emptyList()
        }

    @Test
    fun `the code of a saved deck can be shown`() =
        runTest(dispatcher) {
            val viewModel = viewModel(succeeding, export = { Outcome.Success(DeckCode("AAAA")) })
            advanceUntilIdle()
            viewModel.onSave(
                viewModel.state.value.items
                    .single()
                    .key,
            )
            advanceUntilIdle()

            viewModel.onShowCode("saved-1")

            (viewModel.state.value.code as DeckCodeUi.Ready).text shouldBe "AAAA"
        }

    @Test
    fun `works out the opening odds of a deck once, when asked for`() =
        runTest(dispatcher) {
            val viewModel = viewModel(succeeding)
            advanceUntilIdle()
            val deckId =
                viewModel.state.value.items
                    .single()
                    .deckId

            viewModel.onShowOpening(deckId)
            viewModel.state.value.openings[deckId] shouldBe OpeningState.Loading
            advanceUntilIdle()
            viewModel.onShowOpening(deckId)
            advanceUntilIdle()

            val ready = viewModel.state.value.openings[deckId] as OpeningState.Ready
            ready.odds.goingFirst
                .single()
                .name shouldBe "Charmander"
            analyses shouldBe 1
        }

    @Test
    fun `knows which decks are already kept`() =
        runTest(dispatcher) {
            val viewModel = viewModel(succeeding)
            advanceUntilIdle()
            val item =
                viewModel.state.value.items
                    .single()
            viewModel.state.value.savedDeckIds shouldBe emptySet()

            viewModel.onSave(item.key)
            advanceUntilIdle()

            viewModel.state.value.savedDeckIds shouldBe setOf(item.deckId)
        }
}
