package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.basicPokemon
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.charizardEx
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.charmeleon
import dev.pocketdeck.domain.deckOf
import dev.pocketdeck.domain.magmar
import dev.pocketdeck.domain.pokeBall
import dev.pocketdeck.domain.potion
import dev.pocketdeck.domain.professorsResearch
import dev.pocketdeck.domain.validFireDeck
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DeckValidatorTest {
    private val validator = DeckValidator()

    @Test
    fun `a legal deck has no violations`() {
        validator.validate(validFireDeck()).shouldBeEmpty()
    }

    @Test
    fun `a deck must contain exactly twenty cards`() {
        val deck = deckOf(charmander to 2, magmar to 2)

        validator.validate(deck) shouldContain DeckViolation.WrongSize(actual = 4)
    }

    @Test
    fun `no more than two copies per card name`() {
        val secondPrint = basicPokemon("Magmar", id = "T-Magmar-fullart")
        val deck = deckOf(magmar to 2, secondPrint to 1, charmander to 2, charmeleon to 2, charizardEx to 2)

        validator.validate(deck) shouldContain DeckViolation.TooManyCopies(CardName("Magmar"), copies = 3)
    }

    @Test
    fun `a deck needs a basic pokemon`() {
        val deck = deckOf(pokeBall to 2, potion to 2, professorsResearch to 2)

        validator.validate(deck) shouldContain DeckViolation.NoBasicPokemon
    }

    @Test
    fun `a stage 1 pokemon needs its basic in the deck`() {
        val deck = deckOf(charmeleon to 2, magmar to 2)

        validator.validate(deck) shouldContain
            DeckViolation.MissingEvolutionPrerequisite(CardName("Charmeleon"), CardName("Charmander"))
    }

    @Test
    fun `a stage 2 pokemon needs its stage 1 in the deck`() {
        val deck = deckOf(charizardEx to 2, charmander to 2)

        validator.validate(deck) shouldContain
            DeckViolation.MissingEvolutionPrerequisite(CardName("Charizard ex"), CardName("Charmeleon"))
    }

    @Test
    fun `the energy zone needs one to three types`() {
        validator.validate(validFireDeck().copy(energyTypes = emptySet())) shouldContainExactly
            listOf(DeckViolation.InvalidEnergyZone(typeCount = 0))

        val fourTypes = setOf(EnergyType.FIRE, EnergyType.WATER, EnergyType.GRASS, EnergyType.PSYCHIC)
        validator.validate(validFireDeck().copy(energyTypes = fourTypes)) shouldContainExactly
            listOf(DeckViolation.InvalidEnergyZone(typeCount = 4))
    }

    @Test
    fun `deck size counts every copy`() {
        validFireDeck().size shouldBe Deck.SIZE
    }
}
