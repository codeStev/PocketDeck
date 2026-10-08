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

class OpeningStandingEffectsTest {
    private fun report(
        cards: List<Pair<Card, Int>>,
        energy: Set<EnergyType>,
        order: TurnOrder = TurnOrder.SECOND,
    ): OpeningReport {
        val plain = (1..20 - cards.sumOf { it.second }).map { trainer("Plain $it") to 1 }
        val deck = deckOf(*(cards + plain).toTypedArray(), energyTypes = energy)
        return OpeningSimulator(deck, CardCatalog(deck.entries.map { it.card }), Random(5))
            .simulate(order = order, runs = 10_000)
    }

    private val fireball = Attack("Fireball", "RR", 60, null)

    @Test
    fun `an ability that draws at the end of every turn finds cards sooner`() {
        val target = pokemon("Target", attacks = listOf(fireball))
        val drawer =
            pokemon(
                "Drawer",
                ability = "At the end of your turn, if this Pokémon is in the Active Spot, draw a card.",
                attacks = listOf(fireball),
            )
        val without = report(listOf(target to 1, pokemon("Other") to 3), setOf(EnergyType.FIRE))
        val with = report(listOf(target to 1, drawer to 3), setOf(EnergyType.FIRE))

        (with.chanceToPlay(target, 3) > without.chanceToPlay(target, 3) + 0.03) shouldBe true
    }

    @Test
    fun `a Stadium that fetches a Basic each turn makes a Basic likelier`() {
        val target = pokemon("Target", EnergyType.GRASS, attacks = listOf(fireball))
        val forest =
            trainer(
                "Forest",
                TrainerKind.STADIUM,
                effect =
                    "Once during each player's turn, that player may put a random Basic [ G ] Pokémon from their " +
                        "deck into their hand.",
            )
        val others = pokemon("Other", EnergyType.GRASS) to 3
        val without = report(listOf(target to 1, others), setOf(EnergyType.GRASS))
        val with = report(listOf(target to 1, others, forest to 2), setOf(EnergyType.GRASS))

        (with.chanceToPlay(target, 3) > without.chanceToPlay(target, 3) + 0.05) shouldBe true
    }

    @Test
    fun `energy that counts double lets an attack be paid with half the energy`() {
        val serperior =
            pokemon(
                "Serperior",
                EnergyType.GRASS,
                ability =
                    "Each [G] Energy attached to your [G] Pokémon provides 2 [G] Energy. This effect doesn't stack.",
                attacks = listOf(Attack("Coil", "GGGG", 100, null)),
            )
        val plain = pokemon("Plain", EnergyType.GRASS, attacks = listOf(Attack("Coil", "GGGG", 100, null)))

        // Two turns give two energy: only a doubled pair pays for four.
        report(listOf(plain to 2), setOf(EnergyType.GRASS)).chanceToAttack(plain, 2) shouldBe 0.0
        (report(listOf(serperior to 2), setOf(EnergyType.GRASS)).chanceToAttack(serperior, 2) > 0.1) shouldBe true
    }

    @Test
    fun `a Stadium that rerolls the Energy Zone helps a deck with two types`() {
        val fire = pokemon("Fire", EnergyType.FIRE, attacks = listOf(Attack("Burn", "R", 30, null)))
        val cave =
            trainer(
                "Cave",
                TrainerKind.STADIUM,
                effect =
                    "Once during each player's turn, that player may discard the Energy that has been generated in " +
                        "their Energy Zone. If they do, the next Energy is produced.",
            )
        val types = setOf(EnergyType.FIRE, EnergyType.WATER)
        val without = report(listOf(fire to 2), types)
        val with = report(listOf(fire to 2, cave to 2), types)

        (with.chanceToAttack(fire, 2) > without.chanceToAttack(fire, 2) + 0.02) shouldBe true
    }

    @Test
    fun `a Tool that lowers the retreat cost lets a heavy Pokémon retreat sooner`() {
        val attacker = pokemon("Ember", attacks = listOf(Attack("Ember", "R", 30, null)))
        val heavy = (1..3).map { pokemon("Heavy $it", retreatCost = 1) to 1 }
        val balloon =
            trainer(
                "Balloon",
                TrainerKind.TOOL,
                effect = "The Retreat Cost of the Basic Pokémon this card is attached to is 1 less.",
            )
        val without = report(listOf(attacker to 1) + heavy, setOf(EnergyType.FIRE))
        val with = report(listOf(attacker to 1, balloon to 4) + heavy, setOf(EnergyType.FIRE))

        (with.chanceToAttack(attacker, 1) > without.chanceToAttack(attacker, 1) + 0.02) shouldBe true
    }

    @Test
    fun `an Eevee that evolves at once reaches its evolution on the first turn of the first player`() {
        val eevee =
            pokemon(
                "Eevee",
                ability =
                    "As long as this Pokémon is in the Active Spot, it can evolve during your first turn or the turn you play it.",
            )
        val flareon = pokemon("Flareon", stage = Stage.STAGE_1, evolvesFrom = "Eevee")
        val plainEevee = pokemon("Eevee", ability = null)

        report(listOf(plainEevee to 2, flareon to 2), setOf(EnergyType.FIRE), TurnOrder.FIRST)
            .chanceToPlay(flareon, 1) shouldBe 0.0
        (
            report(listOf(eevee to 2, flareon to 2), setOf(EnergyType.FIRE), TurnOrder.FIRST).chanceToPlay(flareon, 1) >
                0.05
        ) shouldBe
            true
    }
}
