package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.card.TrainerKind

/** Which cards a "put a random ... from your deck" or "look at the top cards" effect can find. */
fun interface CardFilter {
    fun matches(card: Card): Boolean
}

/** Reads the noun phrase of a search effect ("Basic Pokémon with 50 HP or less", "Stadium card", "Type: Null or Silvally"). */
internal object CardFilterReader {
    private val description =
        Regex(
            "^(?:(basic|stage 1|stage 2) )?(?:(mega evolution) )?(?:\\[ ?([a-z]) ?] )?" +
                "(pokemon tool cards?|pokemon|item cards?|stadium cards?|supporter cards?)( ex)?" +
                "(?: with (?:(\\d+) hp or less|a maximum hp of (\\d+) or less))?" +
                "(?: that has [“\"](.+?)[”\"] in its name)?$",
        )
    private val cardWords = Regex("pokemon|cards?|basic|stage|item|stadium|supporter|tool")
    private val nameSeparator = Regex(", and |, or |, | or | and ")

    fun read(phrase: String): CardFilter? {
        val text = phrase.trim()
        val parts = description.matchEntire(text.lowercase())?.destructured
        return when {
            parts != null -> described(parts.toList())
            cardWords.containsMatchIn(text.lowercase()) -> null
            else -> named(text.split(nameSeparator).map { it.trim().lowercase() })
        }
    }

    private fun named(names: List<String>) = CardFilter { it.name.value.lowercase() in names }

    private fun described(parts: List<String>): CardFilter? {
        val stage = parts[0]
        val mega = parts[1]
        val symbol = parts[2]
        val noun = parts[3]
        val ex = parts[4]
        val maxHp = (parts[5].ifEmpty { parts[6] }).toIntOrNull()
        val nameHas = parts[7]
        val energy =
            symbol.takeIf { it.isNotEmpty() }?.let { s ->
                EnergyType.entries.firstOrNull {
                    it.symbol ==
                        s.uppercase()[0]
                }
            }
        if (symbol.isNotEmpty() && energy == null) return null
        return CardFilter { card ->
            matchesNoun(card, noun) &&
                (card !is PokemonCard || matchesPokemon(card, stage, energy, maxHp)) &&
                (mega.isEmpty() || card.name.value.startsWith(MEGA_PREFIX)) &&
                (ex.isEmpty() || card.name.value.endsWith(EX_SUFFIX)) &&
                (
                    nameHas.isEmpty() ||
                        card.name.value
                            .lowercase()
                            .contains(nameHas)
                )
        }
    }

    private fun matchesNoun(
        card: Card,
        noun: String,
    ): Boolean =
        when {
            noun == "pokemon" -> card is PokemonCard
            noun.startsWith("pokemon tool") -> card is TrainerCard && card.kind == TrainerKind.TOOL
            noun.startsWith("item") -> card is TrainerCard && card.kind == TrainerKind.ITEM
            noun.startsWith("stadium") -> card is TrainerCard && card.kind == TrainerKind.STADIUM
            else -> card is TrainerCard && card.kind == TrainerKind.SUPPORTER
        }

    private fun matchesPokemon(
        card: PokemonCard,
        stage: String,
        energy: EnergyType?,
        maxHp: Int?,
    ): Boolean =
        (stage.isEmpty() || card.stage == stageOf(stage)) &&
            (energy == null || card.energyType == energy) &&
            (maxHp == null || card.hp <= maxHp)

    private fun stageOf(word: String) =
        when (word) {
            "basic" -> Stage.BASIC
            "stage 1" -> Stage.STAGE_1
            else -> Stage.STAGE_2
        }

    private const val MEGA_PREFIX = "Mega "
    private const val EX_SUFFIX = " ex"
}
