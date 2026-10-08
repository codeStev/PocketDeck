package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerKind
import dev.pocketdeck.domain.deckOf
import dev.pocketdeck.domain.trainer
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.random.Random

class OpeningEnergyEffectsTest {
    private fun report(
        cards: List<Pair<Card, Int>>,
        energy: Set<EnergyType>,
        order: TurnOrder = TurnOrder.SECOND,
    ): OpeningReport {
        val plain = (1..20 - cards.sumOf { it.second }).map { trainer("Plain $it") to 1 }
        val deck = deckOf(*(cards + plain).toTypedArray(), energyTypes = energy)
        val catalog = CardCatalog(deck.entries.map { it.card })
        return OpeningSimulator(deck, catalog, Random(11)).simulate(order = order, runs = 10_000)
    }

    @Test
    fun `a benched attacker cannot attack until the Active Pokémon can pay to retreat`() {
        val attacker = pokemon("Ember", attacks = listOf(Attack("Ember", "R", 30, null)))
        val heavy = (1..3).map { pokemon("Heavy $it", retreatCost = 3) to 1 }
        val report = report(listOf(attacker to 1) + heavy, setOf(EnergyType.FIRE))
        // Whenever it arrives after the setup it sits on the Bench behind a Pokémon that cannot pay 3 to retreat.
        (report.chanceToPlay(attacker, 1) - report.chanceToAttack(attacker, 1) > 0.02) shouldBe true

        val light = (1..3).map { pokemon("Light $it", retreatCost = 0) to 1 }
        val free = report(listOf(attacker to 1) + light, setOf(EnergyType.FIRE))
        free.chanceToAttack(attacker, 1) shouldBe free.chanceToPlay(attacker, 1)
    }

    @Test
    fun `a trainer that attaches several energy makes a costly attack reachable sooner`() {
        val attacker =
            pokemon("Wave", EnergyType.WATER, attacks = listOf(Attack("Wave", "WWW", 90, null)))
        val misty =
            trainer(
                "Misty",
                TrainerKind.SUPPORTER,
                effect =
                    "Choose 1 of your [ W ] Pokémon, and flip a coin until you get tails. For each heads, " +
                        "take a [ W ] Energy from your Energy Zone and attach it to that Pokémon.",
            )
        val base = report(listOf(attacker to 2), setOf(EnergyType.WATER))
        base.chanceToAttack(attacker, 2) shouldBe 0.0 // two energy by the second turn at most

        val withMisty = report(listOf(attacker to 2, misty to 2), setOf(EnergyType.WATER))
        (withMisty.chanceToAttack(attacker, 2) > 0.01) shouldBe true
    }

    @Test
    fun `an ability that attaches energy to its own pokemon counts as an extra energy each turn`() {
        val ability =
            "Once during your turn, you may take a [R] Energy from your Energy Zone and attach it to this Pokémon."
        val boosted = pokemon("Magnet", ability = ability, attacks = listOf(Attack("Zap", "RR", 40, null)))
        val plain = pokemon("Plain", attacks = listOf(Attack("Zap", "RR", 40, null)))

        report(listOf(plain to 2), setOf(EnergyType.FIRE)).chanceToAttack(plain, 1) shouldBe 0.0
        (report(listOf(boosted to 2), setOf(EnergyType.FIRE)).chanceToAttack(boosted, 1) > 0.1) shouldBe true
    }

    @Test
    fun `a bench pokemon that refills energy from the discard pile keeps a draining attacker attacking`() {
        val burst =
            Attack(
                "Mega Burst",
                "RL",
                50,
                "Discard all [R] and [L] Energy from this Pokémon, and this attack does 50 damage for each Energy " +
                    "you discarded in this way.",
            )
        val attacker = pokemon("Rayquaza", EnergyType.DRAGON, attacks = listOf(burst))
        val helper =
            pokemon(
                "Dragonair",
                EnergyType.DRAGON,
                ability =
                    "Once during your turn, if this Pokémon is on your Bench, you may attach an Energy from your " +
                        "discard pile to your Active [N] Pokémon.",
            )
        val idle = pokemon("Idle", EnergyType.DRAGON)
        val energy = setOf(EnergyType.FIRE, EnergyType.LIGHTNING)

        val without = report(listOf(attacker to 2, idle to 2), energy)
        val with = report(listOf(attacker to 2, helper to 2), energy)

        // Without the helper it takes two turns to pay for the next burst; with it the discard pile pays the second energy.
        (with.chanceToAttackOn(attacker, 4) > without.chanceToAttackOn(attacker, 4) + 0.02) shouldBe true
    }

    @Test
    fun `moving energy from a benched pokemon lets the attacker pay for a costly attack sooner`() {
        val attacker = pokemon("Blaze", attacks = listOf(Attack("Blaze", "RRR", 90, null)))
        val batteryText =
            "Once during your turn, you may take a [R] Energy from your Energy Zone and attach it to this Pokémon."
        val battery = pokemon("Battery", ability = batteryText)
        val dawn = trainer("Dawn", effect = "Move an Energy from 1 of your Benched Pokémon to your Active Pokémon.")
        val energy = setOf(EnergyType.FIRE)

        val without = report(listOf(attacker to 2, battery to 2), energy)
        without.chanceToAttack(attacker, 2) shouldBe 0.0 // at most two zone energy by the second turn

        val with = report(listOf(attacker to 2, battery to 2, dawn to 2), energy)
        (with.chanceToAttack(attacker, 2) > 0.01) shouldBe true
    }

    @Test
    fun `a trainer that evolves from the deck makes an evolution playable sooner`() {
        val seed = pokemon("Seed", EnergyType.GRASS)
        val sprout = pokemon("Sprout", EnergyType.GRASS, stage = Stage.STAGE_1, evolvesFrom = "Seed")
        val quickGrow =
            trainer(
                "Quick-Grow Extract",
                effect =
                    "Choose 1 of your [ G ] Pokémon in play. Put a random [ G ] Pokémon from your deck that evolves " +
                        "from that Pokémon onto that Pokémon to evolve it. You can't use this card during your first " +
                        "turn or on a Pokémon that was put into play this turn.",
            )
        val energy = setOf(EnergyType.GRASS)

        val without = report(listOf(seed to 2, sprout to 2), energy, TurnOrder.FIRST)
        val with = report(listOf(seed to 2, sprout to 2, quickGrow to 2), energy, TurnOrder.FIRST)
        (with.chanceToPlay(sprout, 2) > without.chanceToPlay(sprout, 2) + 0.03) shouldBe true
    }
}
