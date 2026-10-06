package dev.pocketdeck.application.suggest

import app.cash.turbine.test
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.application.meta.InMemoryMetaSnapshotRepository
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.CompletenessCalculator
import dev.pocketdeck.domain.deck.DeckSuggestion
import dev.pocketdeck.domain.deck.SuggestionOrigin
import dev.pocketdeck.domain.validFireDeck
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext

class SuggestDecksServiceTest {
    private fun suggestion(
        title: String,
        score: Double,
    ): DeckSuggestion {
        val deck = validFireDeck()
        val completeness =
            CompletenessCalculator().calculate(
                deck,
                Inventory.of(CardCollection.EMPTY, CardCatalog.EMPTY),
            )
        return DeckSuggestion(title, SuggestionOrigin.META, deck, completeness, score)
    }

    @Test
    fun `puts the best suggestion of all strategies first`() =
        runTest {
            val low = DeckSuggestionStrategy { listOf(suggestion("low", 0.2)) }
            val high = DeckSuggestionStrategy { listOf(suggestion("high", 0.9), suggestion("mid", 0.5)) }
            val service =
                SuggestDecksService(
                    InMemoryCardCatalogRepository(),
                    InMemoryCollectionRepository(),
                    InMemoryMetaSnapshotRepository(),
                    listOf(low, high),
                    EmptyCoroutineContext,
                )

            service().test {
                awaitItem().map { it.title } shouldBe listOf("high", "mid", "low")
            }
        }

    @Test
    fun `suggests again when the collection changes`() =
        runTest {
            val collections = InMemoryCollectionRepository()
            val owned =
                DeckSuggestionStrategy { context ->
                    listOf(suggestion("c${context.inventory.copiesOf(charmander)}", 1.0))
                }
            val catalogs = InMemoryCardCatalogRepository(CardCatalog(listOf(charmander)))
            val service =
                SuggestDecksService(
                    catalogs,
                    collections,
                    InMemoryMetaSnapshotRepository(),
                    listOf(owned),
                    EmptyCoroutineContext,
                )

            service().test {
                awaitItem().single().title shouldBe "c0"
                collections.save(CardCollection.EMPTY.withCopies(charmander.id, 2))
                awaitItem().single().title shouldBe "c2"
            }
        }
}
