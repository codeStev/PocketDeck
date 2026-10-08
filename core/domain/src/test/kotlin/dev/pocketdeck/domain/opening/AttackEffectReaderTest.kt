package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.EnergyType
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AttackEffectReaderTest {
    private fun read(effect: String?) = AttackEffectReader.read(Attack("Attack", "RL", 50, effect))

    @Test
    fun `reads a drain of all energy of the named types`() {
        read(
            "Discard all [R] and [L] Energy from this Pokémon, and this attack does 50 damage for each Energy you discarded in this way.",
        ) shouldBe
            EnergyDiscard(
                count = null,
                types = setOf(EnergyType.FIRE, EnergyType.LIGHTNING),
                random = false,
                onePerType = false,
            )
    }

    @Test
    fun `reads random and counted discards`() {
        read("Discard 2 random Energy from this Pokémon.") shouldBe
            EnergyDiscard(2, null, random = true, onePerType = false)
        read("Discard a random Energy from this Pokémon.") shouldBe
            EnergyDiscard(1, null, random = true, onePerType = false)
        read("Discard 2 [R] Energy from this Pokémon.") shouldBe EnergyDiscard(2, setOf(EnergyType.FIRE), false, false)
        read("Discard all Energy from this Pokémon.") shouldBe EnergyDiscard(null, null, false, false)
    }

    @Test
    fun `reads one energy of each listed type`() {
        read("Discard a [R], [W], and [L] Energy from this Pokémon.") shouldBe
            EnergyDiscard(
                1,
                setOf(EnergyType.FIRE, EnergyType.WATER, EnergyType.LIGHTNING),
                random = false,
                onePerType = true,
            )
    }

    @Test
    fun `ignores discards of the opponent's energy and coin dependent ones`() {
        read("Discard a random Energy from your opponent's Active Pokémon.") shouldBe null
        read("Flip a coin. If tails, discard 2 random Energy from this Pokémon.") shouldBe null
        read(null) shouldBe null
    }

    @Test
    fun `reads one of each type named with articles and unusual wording`() {
        read("Discard a [W] and a [L] Energy from this Pokémon.") shouldBe
            EnergyDiscard(1, setOf(EnergyType.WATER, EnergyType.LIGHTNING), random = false, onePerType = true)

        val alsoHurts =
            "Discard an Energy from this Pokémon, and this attack also does 20 " +
                "damage to each Benched Pokémon."
        read(alsoHurts) shouldBe EnergyDiscard(1, null, random = false, onePerType = false)
    }
}
