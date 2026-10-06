package dev.pocketdeck.domain.card

import dev.pocketdeck.domain.basicPokemon
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LanguageTest {
    @Test
    fun `maps iso codes and falls back to english`() {
        Language.fromIsoCode("de") shouldBe Language.GERMAN
        Language.fromIsoCode("DE") shouldBe Language.GERMAN
        Language.fromIsoCode("fr") shouldBe Language.ENGLISH
        Language.fromIsoCode("") shouldBe Language.ENGLISH
    }

    @Test
    fun `a card names itself in each language`() {
        val card =
            PokemonCard(
                id = CardId("a1-036"),
                name = CardName("Charizard ex"),
                energyType = EnergyType.FIRE,
                stage = Stage.BASIC,
                hp = 180,
                germanName = CardName("Glurak ex"),
            )

        card.nameIn(Language.ENGLISH) shouldBe CardName("Charizard ex")
        card.nameIn(Language.GERMAN) shouldBe CardName("Glurak ex")
    }

    @Test
    fun `the german name defaults to the english one`() {
        basicPokemon("Magmar").nameIn(Language.GERMAN) shouldBe CardName("Magmar")
    }
}
