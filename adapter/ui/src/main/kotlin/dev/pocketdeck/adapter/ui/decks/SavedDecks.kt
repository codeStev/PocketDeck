package dev.pocketdeck.adapter.ui.decks

import dev.pocketdeck.application.savedeck.DeleteSavedDeck
import dev.pocketdeck.application.savedeck.ObserveSavedDecks
import dev.pocketdeck.application.savedeck.RenameSavedDeck
import dev.pocketdeck.application.savedeck.SaveDeck

/** The inbound ports behind the "Saved" tab of the decks screen. */
class SavedDecks(
    val observe: ObserveSavedDecks,
    val save: SaveDeck,
    val rename: RenameSavedDeck,
    val delete: DeleteSavedDeck,
)

/** What the player is asked about a kept deck; at most one question at a time. */
sealed interface SavedDeckDialog {
    data class Rename(
        val id: Long,
        val title: String,
    ) : SavedDeckDialog

    data class ConfirmDelete(
        val id: Long,
        val title: String,
    ) : SavedDeckDialog
}
