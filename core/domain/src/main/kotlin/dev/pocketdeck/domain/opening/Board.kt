package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard

/** A Pokémon in play, the turn it last arrived or evolved (it cannot evolve again that turn) and its attached energy. */
internal class InPlay(
    var card: PokemonCard,
    var changedOnTurn: Int,
    val energy: MutableList<EnergyType> = mutableListOf(),
    var abilityUsedOnTurn: Int = -1,
    /** The Pokémon Tool attached, if any; only tools that lower the Retreat Cost are played. */
    var tool: OpeningEffect.RetreatTool? = null,
)

/** The Active Spot and the Bench. Only the Active Pokémon attacks; the first Pokémon put down becomes Active. */
internal class Board {
    var active: InPlay? = null
        private set
    val bench = mutableListOf<InPlay>()

    val all: List<InPlay> get() = listOfNotNull(active) + bench
    val hasRoom: Boolean get() = all.size < MAX_IN_PLAY

    fun put(
        card: PokemonCard,
        turn: Int,
    ): InPlay {
        val slot = InPlay(card, turn)
        if (active == null) active = slot else bench += slot
        return slot
    }

    fun isActive(slot: InPlay): Boolean = slot === active

    fun swapActiveWith(slot: InPlay) {
        val old = checkNotNull(active) { "No Active Pokémon to swap" }
        bench.remove(slot)
        bench += old
        active = slot
    }

    private companion object {
        /** The Active Spot plus three on the Bench. */
        const val MAX_IN_PLAY = 4
    }
}
