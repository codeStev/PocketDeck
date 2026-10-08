package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.basicPokemon
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.evolvedPokemon
import dev.pocketdeck.domain.trainer
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class HandEffectReaderTest {
    private val basic = basicPokemon("Charmander")
    private val stage2 = evolvedPokemon("Charizard ex", evolvesFrom = "Charmeleon", stage = Stage.STAGE_2)

    @Test
    fun `reads plain drawing`() {
        HandEffectReader.read("Draw 2 cards.") shouldBe OpeningEffect.Draw(2)
        HandEffectReader.read("Draw a card.") shouldBe OpeningEffect.Draw(1)
    }

    @Test
    fun `reads a random search with its filter`() {
        val ball = HandEffectReader.read("Put 1 random Basic Pokemon from your deck into your hand.")
        ball.shouldBeInstanceOf<OpeningEffect.Search>()
        ball.count shouldBe 1
        ball.filter.matches(basic) shouldBe true
        ball.filter.matches(stage2) shouldBe false
    }

    @Test
    fun `reads a search that needs a coin flip`() {
        val pad = HandEffectReader.read("Flip a coin. If heads, put a random Item card from your deck into your hand.")
        pad.shouldBeInstanceOf<OpeningEffect.Search>()
        pad.coin shouldBe CoinRule.FLIP_ONCE
        pad.filter.matches(trainer("Potion")) shouldBe true
        pad.filter.matches(basic) shouldBe false
    }

    @Test
    fun `reads a search by name and by stage and hp`() {
        val gladion = HandEffectReader.read("Put 1 random Type: Null or Silvally from your deck into your hand.")
        gladion.shouldBeInstanceOf<OpeningEffect.Search>()
        gladion.filter.matches(basicPokemon("Silvally")) shouldBe true
        gladion.filter.matches(basic) shouldBe false

        val lisia =
            HandEffectReader.read(
                "Put 2 random Basic Pokémon with 50 HP or less from your deck into your hand.",
            )
        lisia.shouldBeInstanceOf<OpeningEffect.Search>()
        lisia.filter.matches(basic) shouldBe false // 60 HP
    }

    @Test
    fun `reads looking at the top cards`() {
        val effect =
            HandEffectReader.read(
                "Look at the top 4 cards of your deck. Put all Stage 1 Pokémon you find there into your hand. " +
                    "Shuffle the other cards back into your deck.",
            )
        effect.shouldBeInstanceOf<OpeningEffect.LookAtTop>()
        effect.count shouldBe 4
    }

    @Test
    fun `reads hand reshuffles`() {
        HandEffectReader.read(
            "Each player shuffles the cards in their hand into their deck, then draws that many cards.",
        ) shouldBe
            OpeningEffect.ShuffleHandAndDraw(redrawCount = null)
        HandEffectReader.read(
            "Shuffle your hand into your deck. Draw a card for each card in your opponent's hand.",
        ) shouldBe
            OpeningEffect.ShuffleHandAndDraw(HandEffectReader.ASSUMED_OPPONENT_HAND)
    }

    @Test
    fun `does not credit effects it cannot read or that only hurt the opponent`() {
        HandEffectReader.read("Your opponent shuffles their hand into their deck and draws 3 cards.") shouldBe null
        HandEffectReader.read("Heal 20 damage from 1 of your Pokémon.") shouldBe null
    }

    @Test
    fun `reads a search among named cards`() {
        val text =
            "Put 2 random cards from among Magneton, Heliolisk, and Clemont's " +
                "Backpack from your deck into your hand."
        val clemont = HandEffectReader.read(text)
        clemont.shouldBeInstanceOf<OpeningEffect.Search>()
        clemont.count shouldBe 2
        clemont.filter.matches(basicPokemon("Magneton")) shouldBe true
        clemont.filter.matches(trainer("Clemont's Backpack")) shouldBe true
        clemont.filter.matches(basicPokemon("Pikachu")) shouldBe false
    }

    @Test
    fun `reads a search for team rocket pokemon that repeats while the coin is heads`() {
        val text =
            "Flip a coin until you get tails. For each heads, put a random Pokémon " +
                "that has “Team Rocket” in its name " +
                "from your deck into your hand."
        val researcher = HandEffectReader.read(text)
        researcher.shouldBeInstanceOf<OpeningEffect.Search>()
        researcher.coin shouldBe CoinRule.UNTIL_TAILS
        researcher.filter.matches(basicPokemon("Team Rocket's Meowth")) shouldBe true
        researcher.filter.matches(basicPokemon("Meowth")) shouldBe false
    }

    @Test
    fun `reads evolving a pokemon in play from the deck`() {
        val quickGrow =
            "Choose 1 of your [ G ] Pokémon in play. Put a random [ G ] Pokémon " +
                "from your deck that evolves from that " +
                "Pokémon onto that Pokémon to evolve it. You can't use this card during your first turn or on a " +
                "Pokémon that was put into play this turn."
        val growth = HandEffectReader.read(quickGrow)
        growth.shouldBeInstanceOf<OpeningEffect.EvolveFromDeck>()
        growth.target.matches(pokemon("Bulbasaur", EnergyType.GRASS), isActive = true) shouldBe true
        growth.target.matches(pokemon("Charmander", EnergyType.FIRE), isActive = true) shouldBe false

        val wallaceText =
            "Choose 1 of your [ W ] Pokémon in play with a maximum HP of 50 or less. Put a random [ W ] Pokémon " +
                "from your deck that evolves from that Pokémon onto that Pokémon to evolve it."
        val wallace = HandEffectReader.read(wallaceText)
        wallace.shouldBeInstanceOf<OpeningEffect.EvolveFromDeck>()
        wallace.target.matches(pokemon("Magikarp", EnergyType.WATER), isActive = false) shouldBe false // 60 HP
    }
}
