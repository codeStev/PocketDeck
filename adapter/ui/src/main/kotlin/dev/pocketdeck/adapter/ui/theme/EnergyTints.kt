package dev.pocketdeck.adapter.ui.theme

import dev.pocketdeck.domain.card.EnergyType

/** The design system's tint (by key) that stands for an energy type; the library itself knows no Pokémon. */
fun EnergyType.tintKey(): String =
    when (this) {
        EnergyType.GRASS -> "green"
        EnergyType.FIRE -> "orange"
        EnergyType.WATER -> "blue"
        EnergyType.LIGHTNING -> "yellow"
        EnergyType.PSYCHIC -> "purple"
        EnergyType.FIGHTING -> "brown"
        EnergyType.DARKNESS -> "slate"
        EnergyType.METAL -> "steel"
        EnergyType.DRAGON -> "teal"
        EnergyType.COLORLESS -> "gray"
    }
