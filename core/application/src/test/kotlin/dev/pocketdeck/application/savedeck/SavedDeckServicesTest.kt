package dev.pocketdeck.application.savedeck

import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.application.fakes.InMemorySavedDeckRepository
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.charizardEx
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.charmeleon
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.CompletenessCalculator
import dev.pocketdeck.domain.deck.DeckSuggestion
import dev.pocketdeck.domain.deck.SuggestionOrigin
import dev.pocketdeck.domain.deckOf
import dev.pocketdeck.domain.giovanni
import dev.pocketdeck.domain.magmar
import dev.pocketdeck.domain.pokeBall
import dev.pocketdeck.domain.potion
import dev.pocketdeck.domain.professorsResearch
import dev.pocketdeck.domain.rareCandy
import dev.pocketdeck.domain.sabrina
import dev.pocketdeck.domain.validFireDeck
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SavedDeckServicesTest {
    private val now = Instant.parse("2026-10-07T10:00:00Z")
    private val saved = InMemorySavedDeckRepository()
    private val catalog =
        CardCatalog(
            listOf(
                charmander,
                charmeleon,
                charizardEx,
                magmar,
                pokeBall,
                potion,
                rareCandy,
                professorsResearch,
                sabrina,
                giovanni,
            ),
        )
    private val collections = InMemoryCollectionRepository()
    private val save = SaveDeckService(saved, Clock.fixed(now, ZoneOffset.UTC))
    private val observe = ObserveSavedDecksService(saved, InMemoryCardCatalogRepository(catalog), collections)

    private fun aiSuggestion(title: String = "Fire rush"): DeckSuggestion {
        val deck = validFireDeck()
        return DeckSuggestion(
            title = title,
            origin = SuggestionOrigin.AI,
            deck = deck,
            completeness = CompletenessCalculator().calculate(deck, Inventory.of(CardCollection.EMPTY, catalog)),
            score = 1.0,
            rationale = "Fast and simple",
        )
    }

    @Test
    fun `keeps the cards, the energy, the origin and when it was saved`() =
        runTest {
            save(aiSuggestion())

            val content =
                saved
                    .observe()
                    .first()
                    .single()
                    .content
            content.title shouldBe "Fire rush"
            content.origin shouldBe SuggestionOrigin.AI
            content.cards.sumOf { it.copies } shouldBe 20
            content.energyTypes shouldBe validFireDeck().energyTypes
            content.rationale shouldBe "Fast and simple"
            content.savedAt shouldBe now
        }

    @Test
    fun `saving the same cards again keeps one deck`() =
        runTest {
            val first = save(aiSuggestion())
            val second = save(aiSuggestion(title = "Another title"))

            second shouldBe first
            saved.observe().first().size shouldBe 1
        }

    @Test
    fun `a kept deck is judged against the collection as it is now`() =
        runTest {
            save(aiSuggestion())
            observe()
                .first()
                .single()
                .suggestion.completeness.isBuildable shouldBe false

            collections.save(
                CardCollection.EMPTY.let { empty ->
                    validFireDeck().entries.fold(empty) { acc, entry -> acc.withCopies(entry.card.id, entry.copies) }
                },
            )

            observe()
                .first()
                .single()
                .suggestion.completeness.isBuildable shouldBe true
        }

    @Test
    fun `renaming changes the title and ignores a blank one`() =
        runTest {
            val id = save(aiSuggestion())

            RenameSavedDeckService(saved)(id, "  Mine ")
            RenameSavedDeckService(saved)(id, "   ")

            observe()
                .first()
                .single()
                .suggestion.title shouldBe "Mine"
        }

    @Test
    fun `deleting removes the deck`() =
        runTest {
            val id = save(aiSuggestion())

            DeleteSavedDeckService(saved)(id)

            observe().first() shouldBe emptyList()
        }

    @Test
    fun `a card missing from the catalog is left out of the kept deck`() =
        runTest {
            val smallDeck = deckOf(charmander to 2, magmar to 2)
            val suggestion = aiSuggestion().copy(deck = smallDeck)
            save(suggestion)
            val withoutMagmar =
                ObserveSavedDecksService(
                    saved,
                    InMemoryCardCatalogRepository(CardCatalog(listOf(charmander))),
                    collections,
                )

            withoutMagmar()
                .first()
                .single()
                .suggestion.deck.size shouldBe 2
        }
}
