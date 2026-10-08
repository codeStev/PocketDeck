package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard

/** Which of the player's Pokémon in play an effect may pick: by card, and by Active Spot or Bench. */
fun interface SlotFilter {
    fun matches(
        card: PokemonCard,
        isActive: Boolean,
    ): Boolean
}

enum class CoinRule { NONE, FLIP_ONCE, UNTIL_TAILS }

/** When an ability fires: when the player chooses, when its Pokémon evolves another, or at the end of the first turn. */
enum class AbilityTrigger { ON_DEMAND, ON_EVOLVE, END_OF_FIRST_TURN }

/** Where an ability's Pokémon must be for the ability to work. */
enum class Placement { ANYWHERE, ACTIVE, BENCH }

/** When a "beginning/end of your turn" ability fires. */
enum class TurnMoment { START_OF_TURN, END_OF_TURN }

/** What a Stadium does while it is in play; it works for both players, the simulation only plays its own side. */
sealed interface StadiumRule {
    /** Once during each player's turn the player may do [effect] (a search or a draw). */
    data class OncePerTurn(
        val effect: OpeningEffect,
    ) : StadiumRule

    /** At the end of the player's turn they draw until they hold [handSize] cards. */
    data class DrawUpTo(
        val handSize: Int,
    ) : StadiumRule

    /** Once per turn the player may throw away the energy waiting in the Energy Zone, and the next one is produced. */
    data object RerollEnergyZone : StadiumRule

    /** Every Pokémon matching [filter] has its Retreat Cost lowered by [amount]. */
    data class LowerRetreat(
        val filter: CardFilter,
        val amount: Int,
    ) : StadiumRule
}

/** Where attached energy comes from: the Energy Zone creates it, the discard pile gives back what was discarded. */
enum class EnergySource { ENERGY_ZONE, DISCARD_PILE }

/**
 * The effects of trainers and abilities that change what the player can play or attack with in the opening turns:
 * cards moving between deck, hand and board, energy being attached, and Pokémon changing places. Everything else a
 * card does is left out, so the odds err on the careful side.
 */
sealed interface OpeningEffect {
    data class Draw(
        val count: Int,
    ) : OpeningEffect

    /** Puts [count] random cards matching [filter] from the deck into the hand; [coin] says how flips scale the count. */
    data class Search(
        val count: Int,
        val filter: CardFilter,
        val coin: CoinRule = CoinRule.NONE,
    ) : OpeningEffect

    /** Looks at the top [count] cards, takes the ones matching [filter] and shuffles the rest back. */
    data class LookAtTop(
        val count: Int,
        val filter: CardFilter,
    ) : OpeningEffect

    /** Evolves a Basic Pokémon in play straight into a Stage 2 card from the hand, skipping the Stage 1. */
    data object RareCandy : OpeningEffect

    /**
     * Evolves a Pokémon matching [target] with a random card from the deck that evolves from it (Quick-Grow Extract). Like
     * Rare Candy it works neither on the first turn nor on a Pokémon put into play this turn.
     */
    data class EvolveFromDeck(
        val target: SlotFilter,
        val evolution: CardFilter,
    ) : OpeningEffect

    /** Shuffles the hand into the deck and draws [redrawCount] cards, or as many as were shuffled in when null. */
    data class ShuffleHandAndDraw(
        val redrawCount: Int?,
    ) : OpeningEffect

    /**
     * Attaches [count] [energy] taken from [source] to a Pokémon matching [target], or to the Pokémon with the ability
     * when [target] is null. A null [energy] means any type, chosen by the player (only a discard pile offers that).
     * [coin] says how coin flips scale the count; [endsTurn] forfeits the attack.
     */
    data class AttachEnergy(
        val energy: EnergyType?,
        val count: Int,
        val source: EnergySource = EnergySource.ENERGY_ZONE,
        val coin: CoinRule = CoinRule.NONE,
        val target: SlotFilter? = null,
        val endsTurn: Boolean = false,
        val trigger: AbilityTrigger = AbilityTrigger.ON_DEMAND,
        val bearerPlacement: Placement = Placement.ANYWHERE,
    ) : OpeningEffect

    /**
     * Moves [count] energy (all when null) of [types] (any when null) from Pokémon matching [from] (every one of them
     * when [fromEach], else one) to the Active Pokémon matching [to], or to the Pokémon with the ability when [to] is
     * null. [repeatable] is "as often as you like".
     */
    data class MoveEnergy(
        val types: Set<EnergyType>?,
        val count: Int?,
        val from: SlotFilter,
        val to: SlotFilter?,
        val fromEach: Boolean = false,
        val repeatable: Boolean = false,
    ) : OpeningEffect

    /** Swaps the Active Pokémon (when it matches [active]) with a benched one; [bearerComesIn] swaps in the ability's own Pokémon. */
    data class SwitchOwn(
        val active: SlotFilter,
        val bearerComesIn: Boolean = false,
    ) : OpeningEffect

    /** Lowers the Active Pokémon's Retreat Cost by [amount] for this turn. */
    data class RetreatDiscount(
        val amount: Int,
    ) : OpeningEffect

    /** The Active Pokémon retreats without paying. */
    data object FreeRetreat : OpeningEffect

    /**
     * An ability that draws or searches at the [moment] of every turn, while its Pokémon stands at [placement]
     * (Suicune ex draws at the end of the turn it is Active, Meloetta fetches a [P] Pokémon at the start).
     */
    data class EveryTurn(
        val moment: TurnMoment,
        val action: OpeningEffect,
        val placement: Placement,
    ) : OpeningEffect

    /** A Stadium: played from the hand, then in play (one at a time) until the game ends. */
    data class Stadium(
        val rule: StadiumRule,
    ) : OpeningEffect

    /** A Pokémon Tool that lowers the Retreat Cost of the Pokémon matching [wearer] it is attached to. */
    data class RetreatTool(
        val wearer: CardFilter,
        val amount: Int,
    ) : OpeningEffect

    /** While its Pokémon is on the Bench, the Active Pokémon matching [active] retreats [amount] cheaper (Bombirdier). */
    data class RetreatAura(
        val active: CardFilter,
        val amount: Int,
    ) : OpeningEffect

    /** Each [type] energy on a [type] Pokémon counts as two (Serperior). It does not stack. */
    data class DoubleEnergy(
        val type: EnergyType,
    ) : OpeningEffect

    /** When put onto the Bench this Pokémon may swap with the Active one and take all energy in play (Miraidon ex). */
    data object SwitchInAndTakeEnergy : OpeningEffect

    /** While this Pokémon is Active it can evolve in the first turn and in the turn it was put into play (Eevee). */
    data object EvolvesAtOnce : OpeningEffect
}
