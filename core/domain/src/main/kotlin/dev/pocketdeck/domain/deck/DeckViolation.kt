package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.CardName

sealed interface DeckViolation {
    data class WrongSize(
        val actual: Int,
    ) : DeckViolation

    data class TooManyCopies(
        val name: CardName,
        val copies: Int,
    ) : DeckViolation

    data object NoBasicPokemon : DeckViolation

    data class MissingEvolutionPrerequisite(
        val evolution: CardName,
        val requires: CardName,
    ) : DeckViolation

    data class InvalidEnergyZone(
        val typeCount: Int,
    ) : DeckViolation
}
