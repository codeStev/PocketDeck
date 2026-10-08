package dev.pocketdeck.adapter.ui.decks

import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.opening.OpeningAnalysis
import dev.pocketdeck.domain.opening.PokemonOdds
import dev.pocketdeck.domain.opening.TurnOdds
import dev.pocketdeck.domain.validFireDeck
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OpeningListingTest {
    private fun odds(
        ever: List<Double>,
        thatTurn: List<Double>,
    ) = PokemonOdds(charmander, ever.indices.map { TurnOdds(it + 1, ever[it], thatTurn[it]) })

    private fun build(pokemon: PokemonOdds) =
        OpeningListing.build(OpeningAnalysis(listOf(pokemon), emptyList()), Language.ENGLISH).goingFirst.single()

    @Test
    fun `shows chances as whole percent under the name in the player's language`() {
        val row = build(odds(listOf(0.0, 0.334, 0.526, 0.644), listOf(0.0, 0.334, 0.526, 0.644)))

        row.name shouldBe "Charmander"
        row.hasAttacked shouldBe listOf(0, 33, 53, 64)
    }

    @Test
    fun `leaves out the per turn line for a pokemon that attacks every turn once it can`() {
        build(odds(listOf(0.0, 0.3, 0.5, 0.6), listOf(0.0, 0.3, 0.5, 0.6))).attacksThatTurn shouldBe null
    }

    @Test
    fun `adds the per turn line when attacks drain the energy so it cannot attack every turn`() {
        val row = build(odds(listOf(0.0, 0.33, 0.52, 0.64), listOf(0.0, 0.33, 0.19, 0.29)))

        row.attacksThatTurn shouldBe listOf(0, 33, 19, 29)
    }

    @Test
    fun `gives the same deck the same id however its prints and entries are ordered`() {
        val deck = validFireDeck()

        DeckListing.deckIdOf(deck) shouldBe DeckListing.deckIdOf(deck.copy(entries = deck.entries.reversed()))
        (DeckListing.deckIdOf(deck) == DeckListing.deckIdOf(deck.copy(entries = deck.entries.drop(1)))) shouldBe false
    }
}
