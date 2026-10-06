package dev.pocketdeck.domain.card

/** The energy types of the game; [symbol] is the letter the game uses in attack costs, e.g. `WLC`. */
enum class EnergyType(
    val symbol: Char,
) {
    GRASS('G'),
    FIRE('R'),
    WATER('W'),
    LIGHTNING('L'),
    PSYCHIC('P'),
    FIGHTING('F'),
    DARKNESS('D'),
    METAL('M'),
    DRAGON('N'),
    COLORLESS('C'),
}
