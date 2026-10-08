package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.deck.Deck
import kotlin.random.Random

/**
 * How likely a Pokémon is to be attacking by [turn] (your own turns, from 1): [hasAttacked] if it has attacked on or
 * before that turn, [attacksThatTurn] if it attacks on that very turn. The two differ for decks that drain their own
 * energy with every attack.
 */
data class TurnOdds(
    val turn: Int,
    val hasAttacked: Double,
    val attacksThatTurn: Double,
)

data class PokemonOdds(
    val card: PokemonCard,
    val turns: List<TurnOdds>,
)

/** The odds of every Pokémon of a deck, for the player who starts and for the one who goes second. */
data class OpeningAnalysis(
    val goingFirst: List<PokemonOdds>,
    val goingSecond: List<PokemonOdds>,
)

/**
 * Simulates a deck's opening for one seat and reads the odds of each of its Pokémon, the strongest hitters first. A
 * few thousand starts per plan are enough: the numbers are shown in whole percent and a plan's noise is about 1 point.
 */
class OpeningAnalyser(
    private val random: Random,
) {
    fun analyse(
        deck: Deck,
        catalog: CardCatalog,
        order: TurnOrder,
    ): List<PokemonOdds> {
        val report = OpeningSimulator(deck, catalog, random).simulate(order = order, runs = RUNS)
        val pokemon =
            deck.entries
                .map { it.card }
                .filterIsInstance<PokemonCard>()
                .distinctBy { it.gameplayKey }
        return pokemon
            .sortedWith(compareByDescending<PokemonCard> { it.strongestDamage() }.thenBy { it.name.value })
            .map { card ->
                PokemonOdds(
                    card,
                    (1..OpeningSimulator.DEFAULT_TURNS).map { turn ->
                        TurnOdds(turn, report.chanceToAttack(card, turn), report.chanceToAttackOn(card, turn))
                    },
                )
            }
    }

    private fun PokemonCard.strongestDamage() = attacks.maxOfOrNull { it.damage ?: 0 } ?: 0

    private companion object {
        const val RUNS = 2_000
    }
}
