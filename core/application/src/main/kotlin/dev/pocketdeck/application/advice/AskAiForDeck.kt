package dev.pocketdeck.application.advice

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.deck.DeckSuggestion

sealed interface AskAiFailure {
    data class AdvisorFailed(
        val failure: AdvisorFailure,
    ) : AskAiFailure

    /** The advisor kept proposing decks that break the rules or use cards the player does not own. */
    data class NoValidDeck(
        val problems: List<String>,
    ) : AskAiFailure
}

/** Inbound port: ask the AI for a deck made of the player's own cards, optionally with a wish in their own words. */
fun interface AskAiForDeck {
    suspend operator fun invoke(wish: String?): Outcome<DeckSuggestion, AskAiFailure>
}
