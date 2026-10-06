package dev.pocketdeck.domain.card

import dev.pocketdeck.domain.basicPokemon
import dev.pocketdeck.domain.evolvedPokemon
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CardTest {
    @Test
    fun `a basic pokemon cannot evolve from another pokemon`() {
        shouldThrow<IllegalArgumentException> {
            PokemonCard(
                id = CardId("x"),
                name = CardName("Charmander"),
                energyType = EnergyType.FIRE,
                stage = Stage.BASIC,
                hp = 60,
                evolvesFrom = CardName("Something"),
            )
        }
    }

    @Test
    fun `an evolved pokemon must name its predecessor`() {
        shouldThrow<IllegalArgumentException> {
            PokemonCard(
                id = CardId("x"),
                name = CardName("Charmeleon"),
                energyType = EnergyType.FIRE,
                stage = Stage.STAGE_1,
                hp = 90,
            )
        }
    }

    @Test
    fun `hp and retreat cost must be sensible`() {
        shouldThrow<IllegalArgumentException> {
            PokemonCard(CardId("x"), CardName("A"), EnergyType.FIRE, Stage.BASIC, hp = 0)
        }
        shouldThrow<IllegalArgumentException> {
            PokemonCard(CardId("x"), CardName("A"), EnergyType.FIRE, Stage.BASIC, hp = 10, retreatCost = -1)
        }
    }

    @Test
    fun `ex pokemon are recognised by their name`() {
        evolvedPokemon("Charizard ex", evolvesFrom = "Charmeleon", stage = Stage.STAGE_2).isEx shouldBe true
        basicPokemon("Charmander").isEx shouldBe false
    }
}
