package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.basicPokemon
import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerKind
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.deckOf
import dev.pocketdeck.domain.evolvedPokemon
import dev.pocketdeck.domain.trainer
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.random.Random

class OpeningSimulatorTest {
    private val target = basicPokemon("Target")
    private val otherBasic = basicPokemon("Filler")

    private val seed = Random(7)

    /** Plain trainers that do nothing for the hand, so a deck can be padded up to 20 cards. */
    private fun padding(count: Int) = List(count) { trainer("Plain $it") }

    private fun deckOf20(
        vararg cards: Pair<Card, Int>,
        energy: Set<EnergyType> = setOf(EnergyType.FIRE),
    ): Deck {
        val plain = Deck.SIZE - cards.sumOf { it.second }
        return deckOf(*(cards.toList() + padding(plain).map { it to 1 }).toTypedArray(), energyTypes = energy)
    }

    /** [target] once, three other basics, the given extra cards, the rest padding. */
    private fun deckWith(vararg extra: Pair<Card, Int>) = deckOf20(target to 1, otherBasic to 3, *extra)

    private fun report(
        deck: Deck,
        order: TurnOrder = TurnOrder.FIRST,
    ) = OpeningSimulator(deck, CardCatalog(deck.entries.map { it.card }), Random(seed.nextInt()))
        .simulate(order = order, runs = 20_000)

    private fun chance(
        deck: Deck,
        turn: Int,
    ) = report(deck).chanceToPlay(target, turn)

    @Test
    fun `without drawing trainers a basic is likelier than plain odds because hands without one are redrawn`() {
        // P(target in the 5 cards) / P(hand has a basic) = 0.25 / 0.7183 = 0.348; each draw then takes 1 of the other
        // 15 cards: turn 1 draws once, turn 3 three times.
        chance(deckWith(), turn = 1) shouldBe (0.348 + 0.652 / 15 plusOrMinus 0.02)
        chance(deckWith(), turn = 3) shouldBe (0.348 + 0.652 * 3 / 15 plusOrMinus 0.02)
    }

    @Test
    fun `a draw supporter raises the chance`() {
        val research = trainer("Research", TrainerKind.SUPPORTER, effect = "Draw 2 cards.")
        val without = chance(deckWith(), turn = 2)
        val with = chance(deckWith(research to 2), turn = 2)
        (with > without + 0.03) shouldBe true
    }

    @Test
    fun `a basic search item makes finding a basic much likelier`() {
        val ball = trainer("Ball", effect = "Put 1 random Basic Pokemon from your deck into your hand.")
        val without = chance(deckWith(), turn = 2)
        val with = chance(deckWith(ball to 2), turn = 2)
        (with > without + 0.05) shouldBe true
    }

    @Test
    fun `an evolution is never playable on the first turn and never without its predecessor`() {
        val seed = basicPokemon("Seed")
        val sprout = evolvedPokemon("Sprout", evolvesFrom = "Seed")
        val withLine = report(deckOf20(seed to 2, sprout to 2))
        withLine.chanceToPlay(sprout, turn = 1) shouldBe 0.0
        (withLine.chanceToPlay(sprout, turn = 3) > 0.05) shouldBe true

        val withoutPredecessor = report(deckOf20(target to 2, sprout to 2))
        withoutPredecessor.chanceToPlay(sprout, turn = 4) shouldBe 0.0
    }

    @Test
    fun `the second player can evolve on the first turn and the first player cannot`() {
        val seed = basicPokemon("Seed")
        val sprout = evolvedPokemon("Sprout", evolvesFrom = "Seed")
        val deck = deckOf20(seed to 2, sprout to 2)
        report(deck, TurnOrder.FIRST).chanceToPlay(sprout, turn = 1) shouldBe 0.0
        (report(deck, TurnOrder.SECOND).chanceToPlay(sprout, turn = 1) > 0.02) shouldBe true
    }

    @Test
    fun `an evolution is playable later than it is in hand`() {
        val seed = basicPokemon("Seed")
        val sprout = evolvedPokemon("Sprout", evolvesFrom = "Seed")
        val ballDeck = deckOf20(seed to 2, sprout to 2)
        val report = report(ballDeck)
        // In hand by turn 2 for 5 + 1 cards of 20 is well over a third; being on the board needs the Seed as well.
        (report.chanceToPlay(sprout, turn = 2) < report.chanceToPlay(seed, turn = 2)) shouldBe true
    }

    @Test
    fun `rare candy plays a stage 2 without its stage 1 but not on the first turn`() {
        val seed = basicPokemon("Seed")
        val sprout = evolvedPokemon("Sprout", evolvesFrom = "Seed")
        val tree = evolvedPokemon("Tree", evolvesFrom = "Sprout", stage = Stage.STAGE_2)
        val candy =
            trainer(
                "Candy",
                effect =
                    "Choose 1 of your Basic Pokémon in play. If you have a Stage 2 card in your hand that evolves " +
                        "from that Pokémon, put that card onto the Basic Pokémon to evolve it, skipping the Stage 1. " +
                        "You can't use this card during your first turn or on a Basic Pokémon that was put into play " +
                        "this turn.",
            )
        val deck = deckOf20(seed to 2, tree to 2, candy to 2)
        val catalog = CardCatalog(listOf(seed, sprout, tree, candy))
        val withCandy = OpeningSimulator(deck, catalog, Random(1)).simulate(runs = 20_000)
        withCandy.chanceToPlay(tree, turn = 1) shouldBe 0.0
        val second = OpeningSimulator(deck, catalog, Random(1)).simulate(order = TurnOrder.SECOND, runs = 20_000)
        second.chanceToPlay(tree, turn = 1) shouldBe 0.0
        (withCandy.chanceToPlay(tree, turn = 3) > 0.05) shouldBe true

        val noCandy = deckOf20(seed to 2, tree to 2)
        val without = OpeningSimulator(noCandy, catalog, Random(1)).simulate(runs = 20_000)
        without.chanceToPlay(tree, turn = 4) shouldBe 0.0
    }

    @Test
    fun `the same seed gives the same report`() {
        val deck = deckWith()
        val catalog = CardCatalog(deck.entries.map { it.card })
        val a = OpeningSimulator(deck, catalog, Random(3)).simulate(runs = 2_000)
        val b = OpeningSimulator(deck, catalog, Random(3)).simulate(runs = 2_000)
        a.chanceToPlay(target, 3) shouldBe b.chanceToPlay(target, 3)
    }
}
