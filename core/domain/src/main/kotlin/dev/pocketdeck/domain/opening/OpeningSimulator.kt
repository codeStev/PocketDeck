package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.deck.Deck
import kotlin.random.Random

/**
 * Chances by a given turn (your own turns, counted from 1; turn 0 is the setup, where only Basics are laid out).
 * Both players draw every turn; going first only costs the first turn's energy, attack and evolving.
 *
 * Strict readings: a trainer can be played once it is in hand; a Pokémon only once it is on the board. A Pokémon
 * attacks only from the Active Spot, so reaching it (retreat, switch) counts. For Pokémon the plan is built around that
 * Pokémon's evolution line; for trainers it is a neutral plan.
 */
class OpeningReport(
    private val playable: Map<GameplayKey, List<Double>>,
    private val attacking: Map<GameplayKey, List<Double>>,
    private val attackingFully: Map<GameplayKey, List<Double>>,
    private val attackingOn: Map<GameplayKey, List<Double>>,
) {
    fun chanceToPlay(
        card: Card,
        turn: Int,
    ): Double = at(playable, card, turn)

    /** The Pokémon has attacked on or before [turn]: it was Active and had the energy for one of its attacks. */
    fun chanceToAttack(
        card: Card,
        turn: Int,
    ): Double = at(attacking, card, turn)

    /** The Pokémon has used its strongest attack on or before [turn]. */
    fun chanceToAttackFully(
        card: Card,
        turn: Int,
    ): Double = at(attackingFully, card, turn)

    /** The Pokémon attacks on [turn] itself; decks that drain their own energy can fall short of this after attacking once. */
    fun chanceToAttackOn(
        card: Card,
        turn: Int,
    ): Double = at(attackingOn, card, turn)

    private fun at(
        chances: Map<GameplayKey, List<Double>>,
        card: Card,
        turn: Int,
    ): Double = chances[card.gameplayKey]?.getOrNull(turn - 1) ?: 0.0
}

/**
 * Who starts. The first player's first turn has no Energy Zone energy, no attack and no evolving; the second player
 * can attach, attack and evolve on theirs (the Pokémon they set up in turn 0 are not "put into play this turn").
 */
enum class TurnOrder(
    val canEvolveOnFirstTurn: Boolean,
    val getsEnergyOnFirstTurn: Boolean,
    val canAttackOnFirstTurn: Boolean,
) {
    FIRST(canEvolveOnFirstTurn = false, getsEnergyOnFirstTurn = false, canAttackOnFirstTurn = false),
    SECOND(canEvolveOnFirstTurn = true, getsEnergyOnFirstTurn = true, canAttackOnFirstTurn = true),
}

/**
 * Plays the start of a deck many times with [random] (a seeded one gives the same report). Trainers that draw, search,
 * attach energy or switch Pokémon change the odds far from the plain formula, so only a simulation answers them.
 * The [catalog] lets Rare Candy link a Stage 2 card to its Basic.
 */
class OpeningSimulator(
    deck: Deck,
    catalog: CardCatalog,
    private val random: Random,
) {
    private val plan = DeckPlan(deck, catalog)

    /** [runs] games per plan: one neutral plan, and one built around each Pokémon of the deck. */
    fun simulate(
        turns: Int = DEFAULT_TURNS,
        order: TurnOrder = TurnOrder.FIRST,
        runs: Int = DEFAULT_RUNS,
    ): OpeningReport {
        val neutral = tally(Scenario(order), turns, runs)
        val perPokemon =
            plan.pokemon.associate {
                it.gameplayKey to
                    tally(
                        Scenario(order, plan.lineOf(it)),
                        turns,
                        runs,
                    )
            }
        val pokemonKeys = perPokemon.keys
        return OpeningReport(
            playable = neutral.playable.filterKeys { it !in pokemonKeys } + own(perPokemon) { it.playable },
            attacking = own(perPokemon) { it.attacking },
            attackingFully = own(perPokemon) { it.attackingFully },
            attackingOn = own(perPokemon) { it.attackingOn },
        )
    }

    private fun own(
        tallies: Map<GameplayKey, Series>,
        pick: (Series) -> Map<GameplayKey, List<Double>>,
    ): Map<GameplayKey, List<Double>> =
        tallies
            .mapNotNull { (key, series) ->
                pick(series)[key]?.let { key to it }
            }.toMap()

    private fun tally(
        scenario: Scenario,
        turns: Int,
        runs: Int,
    ): Series {
        val tally = Tally(turns)
        repeat(runs) {
            val game = OpeningGame(plan, random, scenario)
            game.deal()
            for (turn in 1..turns) {
                playTurn(game, turn)
                tally.add(game, turn)
            }
        }
        return tally.series(runs)
    }

    /** Draw, lay out and evolve, play a supporter, attach the zone's energy, move the right Pokémon up, then attack. */
    private fun playTurn(
        game: OpeningGame,
        turn: Int,
    ) {
        game.beginTurn(turn)
        game.settle()
        game.playSupporter()
        game.settle()
        game.attachZoneEnergy()
        game.promote()
        game.settle()
        game.finishTurn()
    }

    /** Chances per card and turn from one plan. */
    private class Series(
        val playable: Map<GameplayKey, List<Double>>,
        val attacking: Map<GameplayKey, List<Double>>,
        val attackingFully: Map<GameplayKey, List<Double>>,
        val attackingOn: Map<GameplayKey, List<Double>>,
    )

    /** Counts, per card and turn, in how many runs it could be played or attacked. */
    private class Tally(
        private val turns: Int,
    ) {
        private val playable = HashMap<GameplayKey, IntArray>()
        private val attacking = HashMap<GameplayKey, IntArray>()
        private val attackingFully = HashMap<GameplayKey, IntArray>()
        private val attackingOn = HashMap<GameplayKey, IntArray>()

        fun add(
            game: OpeningGame,
            turn: Int,
        ) {
            count(playable, game.playable, turn)
            count(attacking, game.attacking, turn)
            count(attackingFully, game.attackingFully, turn)
            count(attackingOn, game.attackedNow, turn)
        }

        fun series(runs: Int) =
            Series(share(playable, runs), share(attacking, runs), share(attackingFully, runs), share(attackingOn, runs))

        private fun count(
            into: MutableMap<GameplayKey, IntArray>,
            keys: Set<GameplayKey>,
            turn: Int,
        ) = keys.forEach { into.getOrPut(it) { IntArray(turns) }[turn - 1]++ }

        private fun share(
            counts: Map<GameplayKey, IntArray>,
            runs: Int,
        ) = counts.mapValues { (_, byTurn) -> byTurn.map { it.toDouble() / runs } }
    }

    companion object {
        const val DEFAULT_RUNS = 5_000
        const val DEFAULT_TURNS = 4
    }
}
