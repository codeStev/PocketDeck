package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.EnergyType

/** Whether attached energy pays for an attack: typed symbols need that type, Colorless (`C`) takes any energy. */
internal object AttackCost {
    private const val COLORLESS = 'C'
    private const val UNPAYABLE = Int.MAX_VALUE

    fun canPay(
        cost: String,
        energy: List<EnergyType>,
    ): Boolean = missing(cost, energy) == 0

    /** Whether one more energy of [type] brings a Pokémon with [energy] closer to paying for any of [attacks]. */
    fun helps(
        attacks: List<Attack>,
        energy: List<EnergyType>,
        type: EnergyType,
    ): Boolean = attacks.any { missing(it.cost, energy + type) < missing(it.cost, energy) }

    /** The attack that hurts most; among equals the dearer one, as it is the one a deck is built to reach. */
    fun mainOf(attacks: List<Attack>): Attack? =
        attacks.maxWithOrNull(compareBy({ it.damage ?: 0 }, { it.cost.length }))

    /** How many more energy [cost] needs; typed energy is matched first because it can pay only for its own type. */
    private fun missing(
        cost: String,
        energy: List<EnergyType>,
    ): Int {
        val spare = energy.groupingBy { it }.eachCount().toMutableMap()
        var typedMissing = 0
        var colourless = 0
        for (symbol in cost) {
            if (symbol == COLORLESS) {
                colourless++
                continue
            }
            val type = EnergyType.entries.firstOrNull { it.symbol == symbol } ?: return UNPAYABLE
            val left = spare[type] ?: 0
            if (left == 0) typedMissing++ else spare[type] = left - 1
        }
        return typedMissing + (colourless - spare.values.sum()).coerceAtLeast(0)
    }
}
