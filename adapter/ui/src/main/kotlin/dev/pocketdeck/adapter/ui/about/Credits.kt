package dev.pocketdeck.adapter.ui.about

import dev.pocketdeck.adapter.ui.R

/** Where PocketDeck is developed and where its privacy statement is kept. */
internal object Links {
    const val REPOSITORY = "https://github.com/codeStev/PocketDeck"
    const val PRIVACY = "$REPOSITORY/blob/main/PRIVACY.md"
}

/**
 * One line of the credits. [name] must appear in `NOTICE.md` (a test checks it), so the screen and the notice file cannot drift
 * apart; [what] says what PocketDeck takes from it and [licence] under which terms.
 */
internal data class Credit(
    val name: String,
    val what: Int,
    val licence: String,
)

internal val CREDITS =
    listOf(
        Credit("PocketDecks/pokemon-tcg-pocket-cards", R.string.credit_card_data, "AGPL-3.0-or-later"),
        Credit("Limitless", R.string.credit_tournaments, "public API"),
        Credit("TCGdex", R.string.credit_images, "MIT"),
        Credit("flibustier/pokemon-tcg-pocket-database", R.string.credit_numbers, "MIT"),
        Credit("PokeAPI", R.string.credit_species, "BSD-3-Clause"),
        Credit("KevinGutowski/tcgp-deck-qr", R.string.credit_deck_code, "MIT"),
        Credit("Fredoka", R.string.credit_font, "SIL OFL 1.1"),
        Credit("Nunito", R.string.credit_font, "SIL OFL 1.1"),
        Credit("Open source libraries", R.string.credit_libraries, "Apache-2.0"),
    )
