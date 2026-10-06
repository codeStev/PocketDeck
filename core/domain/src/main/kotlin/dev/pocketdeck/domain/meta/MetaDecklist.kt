package dev.pocketdeck.domain.meta

import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.EnergyType

/** The name a tournament site gives to a family of similar decks, e.g. "Mewtwo ex Gardevoir". */
@JvmInline
value class ArchetypeName(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ArchetypeName must not be blank" }
    }
}

data class DecklistEntry(
    val cardId: CardId,
    val copies: Int,
) {
    init {
        require(copies > 0) { "copies must be positive but was $copies" }
    }
}

/** How a player did with a deck in one tournament. */
data class TournamentResult(
    val placing: Int,
    val wins: Int,
    val losses: Int,
    val ties: Int,
) {
    val games: Int get() = wins + losses + ties

    /** A tie counts as half a win. */
    val winRate: Double get() = if (games == 0) 0.0 else (wins + ties / 2.0) / games
}

/** One deck that somebody played in a tournament. */
data class MetaDecklist(
    val archetype: ArchetypeName,
    val entries: List<DecklistEntry>,
    val energyTypes: Set<EnergyType>,
    val result: TournamentResult,
)
