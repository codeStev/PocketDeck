package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard

/**
 * Guesses the energy types of a deck whose list does not record them (a third of the tournament lists), from what its
 * attacks cost. A Pokémon's own type is no guide: Dragon-type Pokémon exist but there is no Dragon energy, and a
 * Colorless attacker takes anything, so the typed symbols of the attack costs say what the deck has to produce.
 */
object EnergyInference {
    fun of(entries: List<DeckEntry>): Set<EnergyType> {
        val pokemon = entries.mapNotNull { entry -> (entry.card as? PokemonCard)?.let { it to entry.copies } }
        val needed = neededByAttacks(pokemon)
        if (needed.isNotEmpty()) return needed
        return pokemon
            .map { (card, _) -> card.energyType }
            .filter { it in SELECTABLE }
            .toSet()
            .take(Deck.MAX_ENERGY_TYPES)
            .toSet()
    }

    /** The typed energy symbols of every attack, weighted by the copies of the Pokémon, the most needed first. */
    private fun neededByAttacks(pokemon: List<Pair<PokemonCard, Int>>): Set<EnergyType> {
        val weight = mutableMapOf<EnergyType, Int>()
        for ((card, copies) in pokemon) {
            for (attack in card.attacks) {
                for (type in SELECTABLE) if (type.symbol in attack.cost) weight.merge(type, copies, Int::plus)
            }
        }
        return weight.entries
            .sortedByDescending { it.value }
            .take(Deck.MAX_ENERGY_TYPES)
            .map { it.key }
            .toSet()
    }

    /** Dragon is only a Pokémon type; Colorless is no energy to produce. */
    private val SELECTABLE = EnergyType.entries.filter { it != EnergyType.DRAGON && it != EnergyType.COLORLESS }
}
