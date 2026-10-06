package dev.pocketdeck.domain.builder

import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.deck.Deck

/** What the player wants a built deck to be about. Everything left open is chosen by the builder. */
data class DeckFocus(
    /** The energy the deck may use; empty lets the builder choose. */
    val energyTypes: Set<EnergyType> = emptySet(),
    /** A Pokémon the deck is built around. */
    val core: PokemonCard? = null,
)

/** A deck made from the player's own cards. [rating] runs from 0 to 1 and compares decks built from one collection. */
data class BuiltDeck(
    val deck: Deck,
    val rating: Double,
    val centrepiece: PokemonCard,
)
