package dev.pocketdeck.application.advice

import dev.pocketdeck.domain.card.EnergyType

/** The words advisor and application agree on; every advisor adapter uses them for its tool and its answer. */
object AdviceFormat {
    const val TOOL_NAME = "submit_deck"
    const val TOOL_DESCRIPTION = "Submit the finished deck. Call this exactly once."
    const val TITLE = "title"
    const val RATIONALE = "rationale"
    const val CARDS = "cards"
    const val CARD_ID = "id"
    const val CARD_COUNT = "count"
    const val ENERGY = "energy"

    /** The energies the game's deck editor offers. */
    val selectableEnergy: List<EnergyType> =
        EnergyType.entries.filter {
            it != EnergyType.DRAGON &&
                it != EnergyType.COLORLESS
        }

    fun energyName(type: EnergyType): String = type.name.lowercase().replaceFirstChar(Char::uppercase)

    fun energyOf(name: String): EnergyType? =
        EnergyType.entries.firstOrNull {
            it.name.equals(name.trim(), ignoreCase = true)
        }
}
