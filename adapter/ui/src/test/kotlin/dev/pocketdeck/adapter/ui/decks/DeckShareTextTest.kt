package dev.pocketdeck.adapter.ui.decks

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DeckShareTextTest {
    private val labels =
        ShareLabels(
            pokemon = "Pokémon",
            trainers = "Trainers",
            energy = "Energy",
            importCode = "Import code:",
            noCode = "No import code.",
            footer = "Made with PocketDeck",
        )
    private val deck =
        SharedDeck(
            title = "Mewtwo ex Gardevoir",
            pokemon = listOf("2× Mewtwo ex", "2× Ralts"),
            trainers = listOf("2× Poké Ball"),
            energy = "Psychic",
            code = "AAAA",
        )

    @Test
    fun `groups the cards, names the energy and ends with the import code`() {
        shareText(deck, labels) shouldBe
            """
            Mewtwo ex Gardevoir

            Pokémon
            2× Mewtwo ex
            2× Ralts

            Trainers
            2× Poké Ball

            Energy: Psychic

            Import code:
            AAAA

            Made with PocketDeck
            https://github.com/codeStev/PocketDeck
            """.trimIndent()
    }

    @Test
    fun `says so when the game cannot import the deck`() {
        val text = shareText(deck.copy(code = null), labels)

        text.contains("No import code.") shouldBe true
        text.contains("AAAA") shouldBe false
    }

    @Test
    fun `leaves out a group that has no cards`() {
        shareText(deck.copy(trainers = emptyList()), labels).contains("Trainers") shouldBe false
    }

    private fun SharedDeck.copy(
        code: String? = this.code,
        trainers: List<String> = this.trainers,
    ) = SharedDeck(title, pokemon, trainers, energy, code)
}
