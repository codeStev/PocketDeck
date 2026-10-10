package dev.pocketdeck.adapter.ui.decks

import dev.pocketdeck.application.savedeck.KeptDeck
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EffectTag
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.deck.DeckEntry
import dev.pocketdeck.domain.deck.DeckSuggestion
import dev.pocketdeck.domain.deck.SuggestionOrigin

/** One distinct card of a suggested deck. */
data class DeckCardLine(
    val name: String,
    val artworkUrl: String?,
    val copies: Int,
    val missingCopies: Int,
    /** Pokémon or trainer, for grouping the list when a deck is shared. */
    val isPokemon: Boolean = false,
)

/** [copies] of the card [original] are played as [replacement], which the player owns. */
data class SubstituteLine(
    val replacement: String,
    val original: String,
    val copies: Int,
    /** What the two cards do alike, e.g. both throw energy away. */
    val effects: List<EffectTag> = emptyList(),
)

/** A key Pokémon of a deck: its picture and the type that tints the deck. */
data class DeckHero(
    val artworkUrl: String?,
    val type: EnergyType,
)

const val MAX_HEROES = 3

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
    /** Why the deck is as it is, when an advisor explained it. */
    val rationale: String? = null,
    /** Set for a kept deck: the id it is renamed and deleted by. */
    val savedId: Long? = null,
    /** Identifies the cards of the deck, whatever list it is in and whichever prints it uses; see [DeckListing.deckIdOf]. */
    val deckId: Int = 0,
    /** The deck's key Pokémon, most important first (at most [MAX_HEROES]): their art is the deck's picture. */
    val heroes: List<DeckHero> = emptyList(),
    /** The energy the deck's Energy Zone makes, for the shared list. */
    val energyTypes: List<EnergyType> = emptyList(),
) {
    val substituteCopies: Int get() = substitutes.sumOf { it.copies }
}

object DeckListing {
    fun build(
        suggestions: List<DeckSuggestion>,
        language: Language,
    ): List<DeckItem> = suggestions.mapIndexed { index, suggestion -> item(index, suggestion, language) }

    fun buildSaved(
        kept: List<KeptDeck>,
        language: Language,
    ): List<DeckItem> =
        kept.mapIndexed { index, deck ->
            item(index, deck.suggestion, language).copy(key = "saved-${deck.id.value}", savedId = deck.id.value)
        }

    /** The same cards give the same id, whichever prints they are and wherever the deck is listed; used to match saved decks. */
    fun deckIdOf(deck: Deck): Int {
        val copies =
            deck.entries.groupBy { it.card.gameplayKey.value }.mapValues { (_, entries) ->
                entries.sumOf { it.copies }
            }
        return listOf(copies.toSortedMap().toList(), deck.energyTypes.sortedBy { it.ordinal }).hashCode()
    }

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
                        artworkUrl = card.artworkIn(language)?.value,
                        copies = entries.sumOf { it.copies },
                        missingCopies = missing[key] ?: 0,
                        isPokemon = card is PokemonCard,
                    )
                }.sortedByDescending { it.missingCopies > 0 }
        val heroes = heroesOf(suggestion, language)
        return DeckItem(
            key = "${suggestion.title}#$index",
            title = suggestion.centrepiece?.nameIn(language)?.value ?: suggestion.title,
            origin = suggestion.origin,
            ownedPercent = Math.round(suggestion.completeness.ratio * PERCENT).toInt(),
            missingCopies = suggestion.completeness.missingCopies,
            totalCopies = suggestion.completeness.totalCopies,
            cards = lines,
            rationale = suggestion.rationale,
            deckId = deckIdOf(suggestion.deck),
            heroes = heroes,
            energyTypes = suggestion.deck.energyTypes.sortedBy { it.ordinal },
            substitutes =
                suggestion.substitutions.map {
                    SubstituteLine(
                        it.replacement.nameIn(language).value,
                        it.original.nameIn(language).value,
                        it.copies,
                        it.sharedEffects.sortedBy(EffectTag::ordinal),
                    )
                },
        )
    }

    /**
     * One Pokémon per evolution line, the centrepiece first, then the rest by ex, stage and copies. A mixed deck such as
     * "Mega Gardevoir ex + Mega Diancie ex" shows each of its lines, not three cards of one.
     */
    private fun heroesOf(
        suggestion: DeckSuggestion,
        language: Language,
    ): List<DeckHero> {
        val pokemon = suggestion.deck.entries.filter { it.card is PokemonCard }
        val byName = pokemon.associateBy { it.card.name }

        fun rootOf(entry: DeckEntry): CardName {
            var current = entry.card as PokemonCard
            while (true) {
                current = (current.evolvesFrom?.let { byName[it]?.card } as? PokemonCard) ?: return current.name
            }
        }
        val ranked =
            pokemon.sortedWith(
                compareByDescending<DeckEntry> { it.card.id == suggestion.centrepiece?.id }
                    .thenByDescending { (it.card as PokemonCard).isEx }
                    .thenByDescending { (it.card as PokemonCard).stage }
                    .thenByDescending { it.copies },
            )
        return ranked
            .distinctBy(::rootOf)
            .take(MAX_HEROES)
            .map { DeckHero(it.card.artworkIn(language)?.value, (it.card as PokemonCard).energyType) }
    }

    private const val PERCENT = 100
}
