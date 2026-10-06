package dev.pocketdeck.application.deckcode

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.deck.Deck
import java.util.Base64

/**
 * A deck in the form the game imports: scanning a QR code of this text in My Decks (Build New, Scan Code) builds the
 * deck from the cards the player owns.
 */
@JvmInline
value class DeckCode(
    val text: String,
) {
    init {
        require(text.isNotBlank()) { "A deck code must not be blank" }
    }

    fun bytes(): List<Byte> = Base64.getDecoder().decode(text).toList()
}

sealed interface DeckCodeFailure {
    /** The game's own number is not known for these cards, so the game could not tell which cards are meant. */
    data class UnknownToGame(
        val cards: List<CardName>,
    ) : DeckCodeFailure

    /** The game's deck editor offers only eight energies; a deck with none of them cannot be imported. */
    data object NoSelectableEnergy : DeckCodeFailure
}

/** Inbound port: turn a deck into a code the game can import. */
fun interface ExportDeckCode {
    operator fun invoke(deck: Deck): Outcome<DeckCode, DeckCodeFailure>
}
