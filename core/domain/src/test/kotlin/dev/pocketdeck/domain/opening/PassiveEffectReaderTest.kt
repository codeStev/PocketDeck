package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.Stage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/** The texts are the real ones from the card database. */
class PassiveEffectReaderTest {
    private val basic = pokemon("Bulbasaur", EnergyType.GRASS)
    private val stage1 = pokemon("Ivysaur", EnergyType.GRASS, Stage.STAGE_1)
    private val water = pokemon("Squirtle", EnergyType.WATER)
    private val psychic = pokemon("Abra", EnergyType.PSYCHIC)

    @Test
    fun `reads an ability that draws at the end of the turn while Active`() {
        val effect =
            PassiveEffectReader.read(
                "At the end of your turn, if this Pokémon is in the Active Spot, draw a card.",
            )

        effect shouldBe OpeningEffect.EveryTurn(TurnMoment.END_OF_TURN, OpeningEffect.Draw(1), Placement.ACTIVE)
    }

    @Test
    fun `reads an ability that fetches a Pokémon at the start of the turn`() {
        val effect =
            PassiveEffectReader.read(
                "At the beginning of your turn, if this Pokémon is in the Active Spot, put a random [P] Pokémon " +
                    "from your deck into your hand.",
            )

        effect.shouldBeInstanceOf<OpeningEffect.EveryTurn>()
        effect.moment shouldBe TurnMoment.START_OF_TURN
        effect.action
            .shouldBeInstanceOf<OpeningEffect.Search>()
            .filter
            .matches(psychic) shouldBe true
        effect.action
            .shouldBeInstanceOf<OpeningEffect.Search>()
            .filter
            .matches(water) shouldBe false
    }

    @Test
    fun `reads the Stadiums that search, draw up, reroll the zone and lower retreat`() {
        val forest =
            PassiveEffectReader.read(
                "Once during each player's turn, that player may put a random Basic [ G ] Pokémon from their deck into their hand.",
            )
        forest.shouldBeInstanceOf<OpeningEffect.Stadium>()
        val search = forest.rule.shouldBeInstanceOf<StadiumRule.OncePerTurn>().effect
        search.shouldBeInstanceOf<OpeningEffect.Search>().filter.matches(basic) shouldBe true
        search.filter.matches(stage1) shouldBe false

        PassiveEffectReader.read(
            "At the end of each player's turn, that player draws cards until they have 3 cards in their hand.",
        ) shouldBe OpeningEffect.Stadium(StadiumRule.DrawUpTo(3))

        PassiveEffectReader.read(
            "Once during each player's turn, that player may discard the Energy that has been generated in their " +
                "Energy Zone. If they do, the next Energy is produced.",
        ) shouldBe OpeningEffect.Stadium(StadiumRule.RerollEnergyZone)

        val plaza =
            PassiveEffectReader.read(
                "The Retreat Cost of each [ P ] Pokémon in play (both yours and your opponent's) is 2 less.",
            )
        val rule = plaza.shouldBeInstanceOf<OpeningEffect.Stadium>().rule.shouldBeInstanceOf<StadiumRule.LowerRetreat>()
        rule.amount shouldBe 2
        rule.filter.matches(psychic) shouldBe true
        rule.filter.matches(water) shouldBe false
    }

    @Test
    fun `reads the Tools and the bench ability that lower the retreat cost`() {
        val balloon =
            PassiveEffectReader.read("The Retreat Cost of the Basic Pokémon this card is attached to is 1 less.")
        balloon.shouldBeInstanceOf<OpeningEffect.RetreatTool>().amount shouldBe 1
        balloon.wearer.matches(basic) shouldBe true
        balloon.wearer.matches(stage1) shouldBe false

        val boat = PassiveEffectReader.read("The Retreat Cost of the [ W ] Pokémon this card is attached to is 1 less.")
        boat.shouldBeInstanceOf<OpeningEffect.RetreatTool>().wearer.matches(water) shouldBe true

        val aura =
            PassiveEffectReader.read(
                "As long as this Pokémon is on your Bench, your Active [D] Pokémon's Retreat Cost is 1 less.",
            )
        aura.shouldBeInstanceOf<OpeningEffect.RetreatAura>().active.matches(
            pokemon("Zorua", EnergyType.DARKNESS),
        ) shouldBe
            true
        aura.active.matches(water) shouldBe false
    }

    @Test
    fun `reads doubled energy and Miraidon ex, and nothing it does not understand`() {
        PassiveEffectReader.read(
            "Each [G] Energy attached to your [G] Pokémon provides 2 [G] Energy. This effect doesn't stack.",
        ) shouldBe OpeningEffect.DoubleEnergy(EnergyType.GRASS)

        PassiveEffectReader.read(
            "Once during your turn, when you put this Pokémon from your hand onto your Bench, you may switch it with " +
                "your Active Pokémon. If you do, move all of your Energy in play to this Pokémon.",
        ) shouldBe OpeningEffect.SwitchInAndTakeEnergy

        PassiveEffectReader.read("This Pokémon takes −20 damage from attacks.") shouldBe null
    }

    @Test
    fun `reads Mythical Slab as a look at the top card`() {
        val slab =
            HandEffectReader.read(
                "Look at the top card of your deck. If that card is a [ P ] Pokémon, put it into your hand. " +
                    "If it is not a [ P ] Pokémon, put it on the bottom of your deck.",
            )

        slab.shouldBeInstanceOf<OpeningEffect.LookAtTop>().count shouldBe 1
        slab.filter.matches(psychic) shouldBe true
        slab.filter.matches(water) shouldBe false
    }
}
