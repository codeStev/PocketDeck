package dev.pocketdeck.adapter.ui.settings

/** The languages the player can pick for the app; [tag] is the language tag the system stores, null for "as the phone". */
enum class AppLanguageChoice(
    val tag: String?,
) {
    SYSTEM(null),
    ENGLISH("en"),
    GERMAN("de"),
    ;

    companion object {
        fun of(tag: String?): AppLanguageChoice =
            entries.firstOrNull { it.tag != null && it.tag.equals(tag, ignoreCase = true) } ?: SYSTEM
    }
}
