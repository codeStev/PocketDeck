package dev.pocketdeck.domain.card

/**
 * How rare a card is, from the most common to the rarest; the declaration order is the order of rarity. [code] is the short
 * code the card dataset uses. A promo card keeps the rarity it would have in a pack.
 */
enum class Rarity(
    val code: String,
) {
    COMMON("C"),
    UNCOMMON("U"),
    RARE("R"),
    DOUBLE_RARE("RR"),
    ART_RARE("AR"),
    SUPER_RARE("SR"),
    SHINY("S"),
    SPECIAL_ART_RARE("SAR"),
    SHINY_SUPER_RARE("SSR"),
    IMMERSIVE("IM"),
    CROWN("UR"),
    ;

    companion object {
        fun ofCode(code: String?): Rarity? = entries.firstOrNull { it.code == code }
    }
}
