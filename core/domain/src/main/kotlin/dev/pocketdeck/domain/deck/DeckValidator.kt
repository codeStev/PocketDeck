package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage

class DeckValidator {
    fun validate(deck: Deck): List<DeckViolation> =
        listOfNotNull(sizeViolation(deck), basicPokemonViolation(deck), energyZoneViolation(deck)) +
            copyViolations(deck) +
            evolutionViolations(deck)

    private fun sizeViolation(deck: Deck) = DeckViolation.WrongSize(deck.size).takeIf { deck.size != Deck.SIZE }

    private fun basicPokemonViolation(deck: Deck) =
        DeckViolation.NoBasicPokemon.takeIf {
            pokemonIn(deck).none {
                it.stage ==
                    Stage.BASIC
            }
        }

    private fun energyZoneViolation(deck: Deck): DeckViolation? {
        val count = deck.energyTypes.size
        return DeckViolation.InvalidEnergyZone(count).takeIf { count !in 1..Deck.MAX_ENERGY_TYPES }
    }

    private fun copyViolations(deck: Deck): List<DeckViolation> =
        deck.entries
            .groupBy { it.card.name }
            .mapValues { (_, entries) -> entries.sumOf { it.copies } }
            .filterValues { it > Deck.MAX_COPIES_PER_NAME }
            .map { (name, copies) -> DeckViolation.TooManyCopies(name, copies) }

    private fun evolutionViolations(deck: Deck): List<DeckViolation> {
        val namesInDeck = pokemonIn(deck).map { it.name }.toSet()
        return pokemonIn(deck)
            .filter { it.evolvesFrom != null && it.evolvesFrom !in namesInDeck }
            .distinctBy { it.name }
            .map { DeckViolation.MissingEvolutionPrerequisite(it.name, requireNotNull(it.evolvesFrom)) }
    }

    private fun pokemonIn(deck: Deck): List<PokemonCard> = deck.entries.map { it.card }.filterIsInstance<PokemonCard>()
}
