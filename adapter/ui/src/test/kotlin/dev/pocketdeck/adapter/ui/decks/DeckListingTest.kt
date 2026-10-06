package dev.pocketdeck.adapter.ui.decks

import dev.pocketdeck.domain.card.CardCatalog
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
import dev.pocketdeck.domain.validFireDeck
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DeckListingTest {
    private val deck = validFireDeck()
    private val cards = deck.entries.map { it.card }
    private val catalog = CardCatalog(cards)

    private fun suggestion(owned: Map<dev.pocketdeck.domain.card.Card, Int>): DeckSuggestion {
        val collection = owned.entries.fold(CardCollection.EMPTY) { c, (card, copies) -> c.withCopies(card.id, copies) }
        val completeness = CompletenessCalculator().calculate(deck, Inventory.of(collection, catalog))
        return DeckSuggestion("Charizard", SuggestionOrigin.META, deck, completeness, score = 1.0)
    }

    @Test
    fun `shows how much of the deck the player owns`() {
        val item = DeckListing.build(listOf(suggestion(cards.associateWith { 2 })), Language.ENGLISH).single()

        item.ownedPercent shouldBe 100
        item.missingCopies shouldBe 0
        item.totalCopies shouldBe 20
    }

    @Test
    fun `marks the cards that are missing`() {
        val owned = cards.associateWith { 2 } - charizardEx + (charmander to 1)
        val item = DeckListing.build(listOf(suggestion(owned)), Language.ENGLISH).single()

        item.missingCopies shouldBe 3
        item.cards.first { it.name == "Charizard ex" }.missingCopies shouldBe 2
        item.cards.first { it.name == "Charmander" }.missingCopies shouldBe 1
        item.ownedPercent shouldBe 85
    }

    @Test
    fun `lists what the player lacks before what they have`() {
        val owned = cards.associateWith { 2 } - charizardEx
        val item = DeckListing.build(listOf(suggestion(owned)), Language.ENGLISH).single()

        item.cards.first().name shouldBe "Charizard ex"
    }

    @Test
    fun `names the cards in the player's language`() {
        val german =
            charmander.copy(
                germanName =
                    dev.pocketdeck.domain.card
                        .CardName("Glumanda"),
            )
        val germanDeck =
            deck.copy(
                entries =
                    deck.entries.map {
                        if (it.card ==
                            charmander
                        ) {
                            it.copy(card = german)
                        } else {
                            it
                        }
                    },
            )
        val completeness = CompletenessCalculator().calculate(germanDeck, Inventory.of(CardCollection.EMPTY, catalog))
        val suggestion = DeckSuggestion("x", SuggestionOrigin.META, germanDeck, completeness, 1.0)

        DeckListing
            .build(
                listOf(suggestion),
                Language.GERMAN,
            ).single()
            .cards
            .map { it.name }
            .contains("Glumanda") shouldBe
            true
    }

    @Test
    fun `lists the substitutes with the card they replace`() {
        val base = suggestion(cards.associateWith { 2 })
        val adapted =
            base.copy(
                substitutions =
                    listOf(
                        Substitution(
                            charizardEx,
                            charmander,
                            copies = 2,
                            sharedEffects = setOf(EffectTag.HEAL, EffectTag.DISCARD_OWN_ENERGY),
                        ),
                    ),
            )

        val item = DeckListing.build(listOf(adapted), Language.ENGLISH).single()

        item.substitutes shouldBe
            listOf(
                SubstituteLine(
                    replacement = "Charmander",
                    original = "Charizard ex",
                    copies = 2,
                    effects = listOf(EffectTag.DISCARD_OWN_ENERGY, EffectTag.HEAL),
                ),
            )
        item.substituteCopies shouldBe 2
    }

    @Test
    fun `a deck as printed has no substitutes`() {
        DeckListing.build(listOf(suggestion(cards.associateWith { 2 })), Language.ENGLISH).single().substitutes shouldBe
            emptyList()
    }
}
