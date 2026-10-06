package dev.pocketdeck.domain.card

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EffectClassifierTest {
    private fun tags(text: String) = EffectClassifier.tagsOf(text)

    @Test
    fun `recognises discarding the pokemon's own energy`() {
        tags(
            "Discard 2 Energy from this Pokémon. During your opponent's next turn, this Pokémon takes −30 damage from attacks.",
        ) shouldBe setOf(EffectTag.DISCARD_OWN_ENERGY, EffectTag.DAMAGE_REDUCTION)
        tags("Discard a [W] and a [L] Energy from this Pokémon.") shouldBe setOf(EffectTag.DISCARD_OWN_ENERGY)
    }

    @Test
    fun `recognises attaching energy from the discard pile`() {
        tags(
            "Once during your turn, if this Pokémon is on your Bench, you may attach an Energy from your discard pile to your Active [N] Pokémon.",
        ) shouldBe setOf(EffectTag.ATTACH_ENERGY, EffectTag.ATTACH_ENERGY_FROM_DISCARD)
    }

    @Test
    fun `recognises attaching energy from the energy zone`() {
        tags("Take a [W] and a [L] Energy from your Energy Zone and attach them to this Pokémon.") shouldBe
            setOf(EffectTag.ATTACH_ENERGY, EffectTag.ATTACH_ENERGY_FROM_ZONE)
    }

    @Test
    fun `recognises making the opponent lose energy`() {
        tags("Discard a random Energy from your opponent's Active Pokémon.") shouldBe
            setOf(EffectTag.DISCARD_OPPONENT_ENERGY)
    }

    @Test
    fun `recognises random targets and bench damage`() {
        tags(
            "1 of your opponent's Pokémon is chosen at random 4 times. For each time a Pokémon was chosen, do 50 damage to it.",
        ) shouldBe
            setOf(EffectTag.RANDOM_TARGETS, EffectTag.DAMAGE_SCALING)
        tags("This attack also does 20 damage to each of your opponent's Benched Pokémon.") shouldBe
            setOf(EffectTag.BENCH_DAMAGE)
    }

    @Test
    fun `recognises healing, switching, drawing and searching`() {
        tags("Heal 30 damage from this Pokémon.") shouldBe setOf(EffectTag.HEAL)
        tags("Switch this Pokémon with 1 of your Benched Pokémon.") shouldBe setOf(EffectTag.SWITCH)
        tags("Draw 2 cards.") shouldBe setOf(EffectTag.DRAW)
        tags("Put 1 random Basic Pokémon from your deck into your hand.") shouldBe setOf(EffectTag.SEARCH_DECK)
    }

    @Test
    fun `recognises special conditions and coin flips`() {
        tags("Your opponent's Active Pokémon is now Poisoned.") shouldBe setOf(EffectTag.POISON)
        tags("Flip a coin. If heads, your opponent's Active Pokémon is now Paralyzed.") shouldBe
            setOf(EffectTag.COIN_FLIP, EffectTag.PARALYZE)
    }

    @Test
    fun `an attack that only deals damage has no effect tags`() {
        tags("") shouldBe emptySet()
    }

    @Test
    fun `reads all attacks and the ability of a pokemon`() {
        val card =
            PokemonCard(
                id = CardId("x-1"),
                name = CardName("Test"),
                energyType = EnergyType.WATER,
                stage = Stage.BASIC,
                hp = 70,
                attacks =
                    listOf(
                        Attack("A", "W", 20, "Heal 30 damage from this Pokémon."),
                        Attack("B", "WW", 50, null),
                    ),
                ability = Ability("Haste", "Once during your turn, you may draw a card."),
            )

        EffectClassifier.tagsOf(card) shouldBe setOf(EffectTag.HEAL, EffectTag.DRAW)
    }
}
