package dev.pocketdeck.adapter.ui.chase

import dev.pocketdeck.application.chase.RankCardsToChaseService
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.application.meta.InMemoryMetaSnapshotRepository
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.meta.ArchetypeAggregator
import dev.pocketdeck.domain.meta.ArchetypeName
import dev.pocketdeck.domain.meta.DecklistEntry
import dev.pocketdeck.domain.meta.MetaDecklist
import dev.pocketdeck.domain.meta.TournamentResult
import dev.pocketdeck.domain.sabrina
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
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class ChaseViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val deck = validFireDeck()
    private val cards = deck.entries.map { it.card }
    private val germanSabrina = sabrina.copy(germanName = CardName("Sabrina auf Deutsch"), packs = listOf("Mewtwo"))
    private val inDeck = cards.map { if (it == sabrina) germanSabrina else it }
    private val snapshots = InMemoryMetaSnapshotRepository()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(language: Language = Language.ENGLISH): ChaseViewModel {
        val owned = (inDeck - germanSabrina).fold(CardCollection.EMPTY) { total, card -> total.withCopies(card.id, 2) }
        val service =
            RankCardsToChaseService(
                InMemoryCardCatalogRepository(CardCatalog(inDeck)),
                InMemoryCollectionRepository(owned),
                snapshots,
                context = EmptyCoroutineContext,
            )
        return ChaseViewModel(service, language)
    }

    private suspend fun loadField() =
        snapshots.save(
            ArchetypeAggregator().aggregate(
                listOf(
                    MetaDecklist(
                        ArchetypeName("Fire"),
                        deck.entries.map { DecklistEntry(it.card.id, it.copies) },
                        deck.energyTypes,
                        TournamentResult(placing = 1, wins = 3, losses = 1, ties = 0),
                    ),
                ),
            ),
        )

    @Test
    fun `shows nothing until the tournament decks are loaded`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.state.value shouldBe ChaseUiState()
        }

    @Test
    fun `lists the card and the pack that would finish a tournament deck`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            loadField()
            advanceUntilIdle()

            val state = viewModel.state.value
            state.cards.single().run {
                name shouldBe "Sabrina"
                decksFinished shouldBe 1
                packs shouldBe listOf("Mewtwo")
            }
            state.packs.single().run {
                name shouldBe "Mewtwo"
                cardCount shouldBe 1
                cardNames shouldBe listOf("Sabrina")
            }
        }

    @Test
    fun `names cards in the language of the app`() =
        runTest(dispatcher) {
            val viewModel = viewModel(Language.GERMAN)
            loadField()
            advanceUntilIdle()

            viewModel.state.value.cards
                .single()
                .name shouldBe "Sabrina auf Deutsch"
        }
}
