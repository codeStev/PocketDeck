package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.deckOf
import dev.pocketdeck.domain.trainer
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.random.Random

class EnergyZoneTest {
    private fun basic(
        name: String,
        vararg attacks: Attack,
    ) = PokemonCard(
        CardId("T-$name"),
        CardName(name),
        EnergyType.FIRE,
        Stage.BASIC,
        hp = 60,
        attacks = attacks.toList(),
    )

    /** The attacker once, three basics that cannot attack, the rest trainers that do nothing. */
    private fun report(
        attacker: PokemonCard,
        order: TurnOrder,
        energy: Set<EnergyType> = setOf(EnergyType.FIRE),
    ): OpeningReport {
        val entries =
            listOf(attacker to 1) + (1..3).map { basic("Idle $it") to 1 } + (1..16).map { trainer("Plain $it") to 1 }
        val deck = deckOf(*entries.toTypedArray(), energyTypes = energy)
        return OpeningSimulator(
            deck,
            CardCatalog(
                deck.entries.map {
                    it.card
                },
            ),
            Random(5),
        ).simulate(order = order, runs = 20_000)
    }

    @Test
    fun `the first player has no energy on the first turn, the second player has`() {
        val attacker = basic("Ember", Attack("Ember", "R", 30, null))
        val first = report(attacker, TurnOrder.FIRST)
        first.chanceToAttack(attacker, turn = 1) shouldBe 0.0
        (first.chanceToAttack(attacker, turn = 2) > 0.1) shouldBe true

        // With one attacker in play the only energy goes to it, so attacking is exactly being in play.
        val second = report(attacker, TurnOrder.SECOND)
        second.chanceToAttack(attacker, turn = 1) shouldBe second.chanceToPlay(attacker, turn = 1)
    }

    @Test
    fun `a two energy attack waits for the second energy`() {
        val attacker = basic("Blaze", Attack("Blaze", "RR", 60, null))
        val second = report(attacker, TurnOrder.SECOND)
        second.chanceToAttack(attacker, turn = 1) shouldBe 0.0
        (second.chanceToAttack(attacker, turn = 2) > 0.05) shouldBe true

        val first = report(attacker, TurnOrder.FIRST)
        first.chanceToAttack(attacker, turn = 2) shouldBe 0.0
        (first.chanceToAttack(attacker, turn = 3) > 0.05) shouldBe true
    }

    @Test
    fun `energy of the wrong type never pays a typed cost but always pays colourless`() {
        val typed = basic("Ember", Attack("Ember", "R", 30, null))
        report(typed, TurnOrder.SECOND, energy = setOf(EnergyType.WATER)).chanceToAttack(typed, turn = 4) shouldBe 0.0

        val colourless = basic("Tackle", Attack("Tackle", "C", 30, null))
        val report = report(colourless, TurnOrder.SECOND, energy = setOf(EnergyType.WATER))
        report.chanceToAttack(colourless, turn = 1) shouldBe report.chanceToPlay(colourless, turn = 1)
    }

    @Test
    fun `the strongest attack needs more energy than the weakest`() {
        val attacker = basic("Charge", Attack("Spark", "R", 10, null), Attack("Flare", "RRR", 100, null))
        val report = report(attacker, TurnOrder.SECOND)
        (report.chanceToAttack(attacker, turn = 1) > 0.1) shouldBe true
        report.chanceToAttackFully(attacker, turn = 2) shouldBe 0.0
        (report.chanceToAttackFully(attacker, turn = 3) > 0.02) shouldBe true
    }

    @Test
    fun `a deck without energy types gets no energy`() {
        val attacker = basic("Ember", Attack("Ember", "R", 30, null))
        report(attacker, TurnOrder.SECOND, energy = emptySet()).chanceToAttack(attacker, turn = 4) shouldBe 0.0
    }
}
