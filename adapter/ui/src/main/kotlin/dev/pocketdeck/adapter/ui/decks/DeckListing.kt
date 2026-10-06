package dev.pocketdeck.adapter.ui.decks

import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.deck.DeckSuggestion
import dev.pocketdeck.domain.deck.SuggestionOrigin

/** One distinct card of a suggested deck. */
data class DeckCardLine(
    val name: String,
    val artworkUrl: String?,
    val copies: Int,
    val missingCopies: Int,
)

/** [copies] of the card [original] are played as [replacement], which the player owns. */
data class SubstituteLine(
    val replacement: String,
    val original: String,
    val copies: Int,
)

/** A suggested deck as shown in the list. */
data class DeckItem(
    val key: String,
    val title: String,
    val origin: SuggestionOrigin,
    val ownedPercent: Int,
    val missingCopies: Int,
    val totalCopies: Int,
    val cards: List<DeckCardLine>,
    val substitutes: List<SubstituteLine> = emptyList(),
) {
    val substituteCopies: Int get() = substitutes.sumOf { it.copies }
}

object DeckListing {
    fun build(
        suggestions: List<DeckSuggestion>,
        language: Language,
    ): List<DeckItem> = suggestions.mapIndexed { index, suggestion -> item(index, suggestion, language) }

    private fun item(
        index: Int,
        suggestion: DeckSuggestion,
        language: Language,
    ): DeckItem {
        val missing: Map<GameplayKey, Int> =
            suggestion.completeness.missing.associate { it.card.gameplayKey to it.missingCopies }
        // Prints of the same card are interchangeable, so they are one line with the copies added up.
        val lines =
            suggestion.deck.entries
                .groupBy { it.card.gameplayKey }
                .map { (key, entries) ->
                    val card = entries.first().card
                    DeckCardLine(
                        name = card.nameIn(language).value,
                        artworkUrl = card.artwork?.value,
                        copies = entries.sumOf { it.copies },
                        missingCopies = missing[key] ?: 0,
                    )
                }.sortedByDescending { it.missingCopies > 0 }
        return DeckItem(
            key = "${suggestion.title}#$index",
            title = suggestion.title,
            origin = suggestion.origin,
            ownedPercent = Math.round(suggestion.completeness.ratio * PERCENT).toInt(),
            missingCopies = suggestion.completeness.missingCopies,
            totalCopies = suggestion.completeness.totalCopies,
            cards = lines,
            substitutes =
                suggestion.substitutions.map {
                    SubstituteLine(it.replacement.nameIn(language).value, it.original.nameIn(language).value, it.copies)
                },
        )
    }

    private const val PERCENT = 100
}
