package dev.pocketdeck.adapter.ui.decks

import dev.pocketdeck.domain.deck.SuggestionOrigin

/**
 * The kinds of deck the screen keeps apart: what tournament players played, what was made for the player, and what the
 * player chose to keep.
 */
enum class DeckKind {
    TOURNAMENT,
    GENERATED,
    SAVED,
    ;

    companion object {
        fun of(origin: SuggestionOrigin): DeckKind = if (origin == SuggestionOrigin.META) TOURNAMENT else GENERATED

        /** Loading the tournament decks is the tournament tab's business; everything else comes from asking for a deck. */
        fun of(notice: DecksNotice): DeckKind =
            if (notice == DecksNotice.NOT_REACHABLE || notice == DecksNotice.NO_DECKLISTS) TOURNAMENT else GENERATED
    }
}

internal fun List<DeckItem>.ofKind(kind: DeckKind): List<DeckItem> = filter { DeckKind.of(it.origin) == kind }
