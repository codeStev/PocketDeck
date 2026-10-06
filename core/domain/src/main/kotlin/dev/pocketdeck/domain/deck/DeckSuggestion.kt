package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.Card

/** Where a suggestion comes from; the player may trust a tournament list more than a generated one. */
enum class SuggestionOrigin { META, HEURISTIC }

/** A deck the player could play, with how much of it they already own. */
data class DeckSuggestion(
    val title: String,
    val origin: SuggestionOrigin,
    val deck: Deck,
    val completeness: DeckCompleteness,
    /** Higher is better; comparable only between suggestions of the same list. */
    val score: Double,
    /** What the player would play instead of cards they lack; empty when the deck is the original list. */
    val substitutions: List<Substitution> = emptyList(),
    /** The Pokémon a built deck is about; the title falls back to its English name. */
    val centrepiece: Card? = null,
)
