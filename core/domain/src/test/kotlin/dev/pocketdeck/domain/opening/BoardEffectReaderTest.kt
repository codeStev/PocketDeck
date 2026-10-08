package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.Stage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class BoardEffectReaderTest {
    private val water = pokemon("Squirtle", EnergyType.WATER)
    private val fire = pokemon("Vulpix", EnergyType.FIRE)

    private val OpeningEffect.AttachEnergy.slot: SlotFilter get() = checkNotNull(target) { "effect has no target" }

    private fun attach(text: String): OpeningEffect.AttachEnergy {
        val effect = BoardEffectReader.read(text)
        effect.shouldBeInstanceOf<OpeningEffect.AttachEnergy>()
        return effect
    }

    @Test
    fun `reads Misty as coins until tails on a chosen water pokemon`() {
        val misty =
            attach(
                "Choose 1 of your [ W ] Pokémon, and flip a coin until you get tails. For each heads, " +
                    "take a [ W ] Energy from your Energy Zone and attach it to that Pokémon.",
            )
        misty.energy shouldBe EnergyType.WATER
        misty.coin shouldBe CoinRule.UNTIL_TAILS
        misty.slot.matches(water, isActive = false) shouldBe true
        misty.slot.matches(fire, isActive = false) shouldBe false
    }

    @Test
    fun `reads trainers that attach to a named, a benched or a staged pokemon`() {
        val brock = attach("Take a [ F ] Energy from your Energy Zone and attach it to Golem or Onix.")
        brock.slot.matches(pokemon("Onix", EnergyType.FIGHTING), false) shouldBe true

        val generator =
            attach(
                "Flip a coin. If heads, take a [ L ] Energy from your Energy Zone and attach it to " +
                    "1 of your Benched [ L ] Pokémon.",
            )
        generator.coin shouldBe CoinRule.FLIP_ONCE
        val pikachu = pokemon("Pikachu", EnergyType.LIGHTNING)
        generator.slot.matches(pikachu, isActive = false) shouldBe true
        generator.slot.matches(pikachu, isActive = true) shouldBe false

        val wally = attach("Take a [ C ] Energy from your Energy Zone and attach it to 1 of your Stage 2 Pokémon.")
        wally.energy shouldBe EnergyType.COLORLESS
        wally.slot.matches(pokemon("Charizard", stage = Stage.STAGE_2), false) shouldBe true
        wally.slot.matches(pokemon("Charmander"), false) shouldBe false
    }

    @Test
    fun `reads Kiawe as two energy that ends the turn`() {
        val kiawe =
            attach(
                "Choose 1 of your Alolan Marowak or Turtonator. Take 2 [ R ] Energy from your Energy Zone and " +
                    "attach it to that Pokémon. Your turn ends.",
            )
        kiawe.count shouldBe 2
        kiawe.endsTurn shouldBe true
    }

    @Test
    fun `reads abilities that attach from the zone`() {
        val magneton =
            attach(
                "Once during your turn, you may take a [L] Energy from your Energy Zone and attach it to this Pokémon.",
            )
        magneton.target shouldBe null

        val gardevoir =
            attach(
                "Once during your turn, you may take 1 [P] Energy from your Energy Zone and attach it to the [P] Pokémon in the Active Spot.",
            )
        gardevoir.slot.matches(pokemon("Ralts", EnergyType.PSYCHIC), isActive = true) shouldBe true
        gardevoir.slot.matches(pokemon("Ralts", EnergyType.PSYCHIC), isActive = false) shouldBe false

        val leafeon =
            attach(
                "Once during your turn, if this Pokémon is in the Active Spot, you may take a [G] Energy from your " +
                    "Energy Zone and attach it to 1 of your [G] Pokémon.",
            )
        leafeon.bearerPlacement shouldBe Placement.ACTIVE

        val giratina =
            attach(
                "Once during your turn, you may take a [P] Energy from your Energy Zone and attach it to " +
                    "this Pokémon. If you use this Ability, your turn ends.",
            )
        giratina.endsTurn shouldBe true
    }

    @Test
    fun `reads triggers on evolving and at the end of the first turn`() {
        val charmeleon =
            attach(
                "Once during your turn, when you play this Pokémon from your hand to evolve 1 of your Pokémon, " +
                    "you may take a [R] Energy from your Energy Zone and attach it to your Active [R] Pokémon.",
            )
        charmeleon.trigger shouldBe AbilityTrigger.ON_EVOLVE

        val zeraora =
            attach(
                "At the end of your first turn, take a [L] Energy from your Energy Zone and attach it to this Pokémon.",
            )
        zeraora.trigger shouldBe AbilityTrigger.END_OF_FIRST_TURN
    }

    @Test
    fun `reads attaching from the discard pile, of any type when none is named`() {
        val patch = attach("Attach a [ R ] Energy from your discard pile to your Active [ R ] Pokémon.")
        patch.source shouldBe EnergySource.DISCARD_PILE
        patch.energy shouldBe EnergyType.FIRE

        val volkner =
            attach(
                "Choose 1 of your Electivire or Luxray. Attach 2 [ L ] Energy from your discard pile to that Pokémon.",
            )
        volkner.count shouldBe 2

        val dragonair =
            attach(
                "Once during your turn, if this Pokémon is on your Bench, you may attach an Energy from your discard " +
                    "pile to your Active [N] Pokémon.",
            )
        dragonair.energy shouldBe null
        dragonair.bearerPlacement shouldBe Placement.BENCH
        dragonair.slot.matches(pokemon("Rayquaza", EnergyType.DRAGON), isActive = true) shouldBe true
    }

    @Test
    fun `reads switching and retreat effects`() {
        val skyla = BoardEffectReader.read("Switch your Active Stage 1 Pokémon with 1 of your Benched Pokémon.")
        skyla.shouldBeInstanceOf<OpeningEffect.SwitchOwn>()
        skyla.active.matches(pokemon("Wartortle", stage = Stage.STAGE_1), isActive = true) shouldBe true
        skyla.active.matches(pokemon("Squirtle"), isActive = true) shouldBe false

        val solgaleo =
            BoardEffectReader.read(
                "Once during your turn, if this Pokémon is on your Bench, you may switch it with your Active Pokémon.",
            )
        solgaleo.shouldBeInstanceOf<OpeningEffect.SwitchOwn>()
        solgaleo.bearerComesIn shouldBe true

        BoardEffectReader.read("During this turn, the Retreat Cost of your Active Pokémon is 2 less.") shouldBe
            OpeningEffect.RetreatDiscount(2)
        BoardEffectReader.read("Your Active Pokémon has no Retreat Cost.") shouldBe OpeningEffect.FreeRetreat
    }

    @Test
    fun `leaves out what it cannot honour`() {
        BoardEffectReader.read(
            "Take a [ P ] Energy from your Energy Zone and attach it to each of your Drifblim and Mismagius.",
        ) shouldBe
            null
        BoardEffectReader.read(
            "Switch your Active Pokémon that has damage on it with 1 of your Benched Pokémon.",
        ) shouldBe
            null
        BoardEffectReader.read("Heal 20 damage from 1 of your Pokémon.") shouldBe null
    }

    @Test
    fun `reads moving energy to the active pokemon`() {
        val dawn = BoardEffectReader.read("Move an Energy from 1 of your Benched Pokémon to your Active Pokémon.")
        dawn.shouldBeInstanceOf<OpeningEffect.MoveEnergy>()
        dawn.count shouldBe 1
        dawn.types shouldBe null
        dawn.from.matches(fire, isActive = false) shouldBe true
        dawn.from.matches(fire, isActive = true) shouldBe false

        val switchText = "Move a [ R ] , [ W ] , or [ L ] Energy from 1 of your Benched Pokémon to your Active Pokémon."
        val switch = BoardEffectReader.read(switchText)
        switch.shouldBeInstanceOf<OpeningEffect.MoveEnergy>()
        switch.types shouldBe setOf(EnergyType.FIRE, EnergyType.WATER, EnergyType.LIGHTNING)

        val surgeText =
            "Move all [ L ] Energy from your Benched Pokémon to your Raichu, Electrode, or Electabuzz in the Active Spot."
        val surge = BoardEffectReader.read(surgeText)
        surge.shouldBeInstanceOf<OpeningEffect.MoveEnergy>()
        surge.count shouldBe null
        surge.fromEach shouldBe true
        checkNotNull(surge.to).matches(pokemon("Raichu", EnergyType.LIGHTNING), isActive = true) shouldBe true
    }

    @Test
    fun `reads moving energy by ability`() {
        val vaporeonText =
            "As often as you like during your turn, you may move a [W] Energy from 1 of your Benched [W] Pokémon " +
                "to your Active [W] Pokémon."
        val vaporeon = BoardEffectReader.read(vaporeonText)
        vaporeon.shouldBeInstanceOf<OpeningEffect.MoveEnergy>()
        vaporeon.repeatable shouldBe true

        val tyranitarText =
            "Once during your turn, you may move all [D] Energy from each of your " +
                "Pokémon to this Pokémon."
        val tyranitar = BoardEffectReader.read(tyranitarText)
        tyranitar.shouldBeInstanceOf<OpeningEffect.MoveEnergy>()
        tyranitar.to shouldBe null
        tyranitar.fromEach shouldBe true
    }
}
