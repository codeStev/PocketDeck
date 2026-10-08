package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.EnergyType

/**
 * Energy an attack discards from its own Pokémon: [count] of them (all when null), only of [types] when given, chosen at
 * random or not, or one of each listed type. Attacks like Mega Burst or Dragon Impact drain the Pokémon this way.
 */
data class EnergyDiscard(
    val count: Int?,
    val types: Set<EnergyType>?,
    val random: Boolean,
    val onePerType: Boolean,
)

object AttackEffectReader {
    private val discard = Regex("(?:^|\\. )discard ((?:an?|\\d+|all)\\b[^.]*?) ?energy from this pokemon")
    private val type = Regex("\\[ ?([a-z]) ?]")

    fun read(attack: Attack): EnergyDiscard? {
        val text = attack.effect?.replace('é', 'e')?.lowercase() ?: return null
        val clause = discard.find(text)?.groupValues?.get(1) ?: return null
        val types = type.findAll(clause).mapNotNull { symbolToType(it.groupValues[1]) }.toSet()
        val count = if (clause.startsWith("all")) null else clause.substringBefore(' ').toIntOrNull() ?: 1
        return EnergyDiscard(
            count = count,
            types = types.ifEmpty { null },
            random = clause.contains("random"),
            onePerType = count == 1 && types.size > 1,
        )
    }

    private fun symbolToType(letter: String) = EnergyType.entries.firstOrNull { it.symbol == letter.uppercase()[0] }
}
