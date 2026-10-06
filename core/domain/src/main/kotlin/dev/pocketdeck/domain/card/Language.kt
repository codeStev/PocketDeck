package dev.pocketdeck.domain.card

/** Languages in which card names are available. The English name is the canonical one used by the rules. */
enum class Language {
    ENGLISH,
    GERMAN,
    ;

    companion object {
        /** Maps an ISO 639-1 code such as the one of the device locale; anything unsupported becomes English. */
        fun fromIsoCode(code: String): Language =
            when (code.lowercase()) {
                "de" -> GERMAN
                else -> ENGLISH
            }
    }
}
