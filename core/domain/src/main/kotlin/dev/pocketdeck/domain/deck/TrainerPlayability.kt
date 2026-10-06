package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard

/** Something a deck must have for a trainer card to do anything. */
sealed interface Requirement {
    fun describe(): String

    fun isMetBy(deck: Deck): Boolean

    /** At least [MINIMUM] Pokémon of one energy type. */
    data class PokemonOfType(
        val type: EnergyType,
    ) : Requirement {
        override fun describe() = "a ${typeName(type)} Pokémon"

        override fun isMetBy(deck: Deck) =
            pokemon(deck).filter { it.card.energyType == type }.sumOf { it.copies } >= MINIMUM
    }

    /** The deck's Energy Zone has to be able to produce the type. */
    data class EnergyOfType(
        val type: EnergyType,
    ) : Requirement {
        override fun describe() = "${typeName(type)} energy in the Energy Zone"

        override fun isMetBy(deck: Deck) = type in deck.energyTypes
    }

    /** Pokémon with [fragment] in their name, as in “Team Rocket”. */
    data class NameContains(
        val fragment: String,
    ) : Requirement {
        override fun describe() = "a Pokémon with “$fragment” in its name"

        override fun isMetBy(deck: Deck) =
            pokemon(deck)
                .filter {
                    it.card.name.value
                        .contains(fragment, ignoreCase = true)
                }.sumOf { it.copies } >=
                MINIMUM
    }

    /** One of the Pokémon the card names. */
    data class AnyNamed(
        val names: List<String>,
    ) : Requirement {
        override fun describe() = "one of these Pokémon: ${names.joinToString(", ")}"

        override fun isMetBy(deck: Deck) =
            pokemon(deck).any { entry ->
                names.any {
                    entry.card.name.value
                        .contains(it, ignoreCase = true)
                }
            }
    }

    data class OfStage(
        val stage: Stage,
    ) : Requirement {
        override fun describe() = "a ${if (stage == Stage.STAGE_1) "Stage 1" else "Stage 2"} Pokémon"

        override fun isMetBy(deck: Deck) = pokemon(deck).any { it.card.stage == stage }
    }

    data object MegaPokemon : Requirement {
        override fun describe() = "a Mega Evolution Pokémon ex"

        override fun isMetBy(deck: Deck) =
            pokemon(deck).any {
                it.card.name.value
                    .startsWith("Mega ")
            }
    }

    data object ExPokemon : Requirement {
        override fun describe() = "a Pokémon ex"

        override fun isMetBy(deck: Deck) = pokemon(deck).any { it.card.isEx }
    }

    companion object {
        const val MINIMUM = 1

        private class Entry(
            val card: PokemonCard,
            val copies: Int,
        )

        private fun pokemon(deck: Deck): List<Entry> =
            deck.entries.mapNotNull { entry -> (entry.card as? PokemonCard)?.let { Entry(it, entry.copies) } }

        private fun typeName(type: EnergyType) = type.name.lowercase().replaceFirstChar(Char::uppercase)
    }
}

/**
 * Whether a trainer card can do anything in a deck. Many trainers talk about "[W] Pokémon", Pokémon with “Team Rocket”
 * in their name or one of a few named Pokémon; in a deck without them the card is a dead draw. The conditions are read
 * from the English card text, which words them the same way every time.
 */
class TrainerPlayability(
    catalog: CardCatalog,
) {
    private val pokemonNames: Set<String> =
        catalog.cards
            .filterIsInstance<PokemonCard>()
            .map { it.name.value.lowercase() }
            .filter {
                it.length >=
                    MIN_NAME_LENGTH
            }.toSet()

    /** What the deck lacks for [trainer] to be of use; empty when it is playable. */
    fun unmet(
        trainer: TrainerCard,
        deck: Deck,
    ): List<Requirement> = requirementsOf(trainer).filterNot { it.isMetBy(deck) }

    fun requirementsOf(card: Card): List<Requirement> {
        val text = (card as? TrainerCard)?.effect ?: return emptyList()
        val sentences = text.replace('’', '\'').split(SENTENCE_END).map { it.substringBefore(EXCEPTION) }
        return (sentences.flatMap(::typed) + sentences.flatMap(::named) + categories(text)).distinct()
    }

    /** Types listed as alternatives ("[R], [W], or [L] Energy") are choices, not demands. */
    private fun typed(sentence: String): List<Requirement> =
        if (TYPE_LIST.containsMatchIn(sentence)) emptyList() else typedTokens(sentence)

    private fun typedTokens(sentence: String): List<Requirement> =
        TYPED
            .findAll(sentence)
            .mapNotNull { match ->
                val type =
                    EnergyType.entries.firstOrNull { it.symbol == match.groupValues[1].single() }
                        ?: return@mapNotNull null
                val after = sentence.substring(match.range.last + 1)
                when {
                    POKEMON_AFTER.containsMatchIn(after) -> Requirement.PokemonOfType(type)

                    ENERGY_AFTER.containsMatchIn(
                        after,
                    ) && !OPPONENT_ENERGY.containsMatchIn(after) -> Requirement.EnergyOfType(type)

                    else -> null
                }
            }.toList()

    /** Quoted name fragments, and the Pokémon the sentence names (any one of them will do). */
    private fun named(sentence: String): List<Requirement> {
        val fragments = IN_NAME.findAll(sentence).map { Requirement.NameContains(it.groupValues[1]) }.toList()
        val names = namesIn(sentence)
        return fragments + listOfNotNull(names.takeIf { it.isNotEmpty() }?.let { Requirement.AnyNamed(it) })
    }

    private fun namesIn(sentence: String): List<String> {
        val words = sentence.split(WORD_BREAK).filter { it.isNotBlank() }
        val found = linkedSetOf<String>()
        for (start in words.indices) {
            for (length in MAX_NAME_WORDS downTo 1) {
                val phrase = words.drop(start).take(length).joinToString(" ")
                if (words.size - start >= length && phrase.lowercase() in pokemonNames) {
                    found += phrase
                    break
                }
            }
        }
        return found.toList()
    }

    private fun categories(text: String): List<Requirement> =
        listOfNotNull(
            Requirement.MegaPokemon.takeIf { MEGA_FROM_DECK.containsMatchIn(text) },
            Requirement.ExPokemon.takeIf {
                EX_FROM_DECK.containsMatchIn(
                    text,
                ) && !MEGA_FROM_DECK.containsMatchIn(text)
            },
        ) +
            STAGE.findAll(text).map {
                Requirement.OfStage(
                    if (it.groupValues[1] ==
                        "1"
                    ) {
                        Stage.STAGE_1
                    } else {
                        Stage.STAGE_2
                    },
                )
            }

    private companion object {
        const val MIN_NAME_LENGTH = 3
        const val MAX_NAME_WORDS = 4
        const val EXCEPTION = "except any"
        val SENTENCE_END = Regex("(?<=\\.)\\s+")
        val WORD_BREAK = Regex("[\\s,;:()“”\"]+|'s\\b|\\.(?=\\s|$)")
        val TYPE_LIST = Regex("\\]\\s*,\\s*(?:or\\s*)?\\[|\\]\\s+or\\s+\\[")
        val TYPED = Regex("\\[\\s*([GRWLPFDMN])\\s*\\]")
        val POKEMON_AFTER = Regex("^\\s*Pok[eé]mon")
        val ENERGY_AFTER = Regex("^\\s*Energy")
        val OPPONENT_ENERGY = Regex("^[^.]*(from|of) your opponent")
        val IN_NAME = Regex("[“\"]([^”\"]+)[”\"]\\s+in\\s+(?:its|their)\\s+name")
        val MEGA_FROM_DECK = Regex("Mega Evolution Pok[eé]mon ex from your deck")
        val EX_FROM_DECK = Regex("Pok[eé]mon ex from your deck")
        val STAGE = Regex("(?:your|all|any) Stage ([12]) Pok[eé]mon|Stage ([12]) Pok[eé]mon you find")
    }
}
