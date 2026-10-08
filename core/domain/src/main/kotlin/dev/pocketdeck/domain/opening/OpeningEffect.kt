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
}
