package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EnergyInferenceTest {
    private fun pokemon(
        name: String,
        type: EnergyType,
        vararg costs: String,
    ) = PokemonCard(
        id = CardId("T-$name"),
        name = CardName(name),
        energyType = type,
        stage = Stage.BASIC,
        hp = 100,
        attacks = costs.map { Attack("Hit", it, 50, null) },
    )

    @Test
    fun `reads the energy from what the attacks cost`() {
        val entries =
            listOf(
                DeckEntry(pokemon("Dragonite", EnergyType.DRAGON, "WLC"), 2),
                DeckEntry(pokemon("Greninja", EnergyType.WATER, "WC"), 2),
            )

        EnergyInference.of(entries) shouldBe setOf(EnergyType.WATER, EnergyType.LIGHTNING)
    }

    @Test
    fun `never offers Dragon, which is a Pokemon type and not an energy`() {
        val entries =
            listOf(
                DeckEntry(pokemon("Dratini", EnergyType.DRAGON, "CC"), 2),
                DeckEntry(pokemon("Mime", EnergyType.PSYCHIC, "CC"), 2),
            )

        EnergyInference.of(entries) shouldBe setOf(EnergyType.PSYCHIC)
    }

    @Test
    fun `keeps the most needed types when a deck wants more than three`() {
        val entries =
            listOf(
                DeckEntry(pokemon("A", EnergyType.FIRE, "R"), 2),
                DeckEntry(pokemon("B", EnergyType.WATER, "W"), 2),
                DeckEntry(pokemon("C", EnergyType.GRASS, "G"), 2),
                DeckEntry(pokemon("D", EnergyType.METAL, "M"), 1),
            )

        EnergyInference.of(entries) shouldBe setOf(EnergyType.FIRE, EnergyType.WATER, EnergyType.GRASS)
    }
}
