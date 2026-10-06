package dev.pocketdeck.domain.builder

import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage

/** A Pokémon with everything it has to evolve from, starting at a Basic Pokémon; every card of it is owned. */
internal data class PokemonLine(
    val chain: List<PokemonCard>,
) {
    val last: PokemonCard get() = chain.last()
}

internal object LineFinder {
    /** Every owned Pokémon whose whole evolution line is owned. Evolutions without their predecessors are unplayable. */
    fun linesOf(owned: OwnedCards): List<PokemonLine> =
        owned.pokemon.mapNotNull { card -> chainOf(owned, card)?.let(::PokemonLine) }

    fun lineOf(
        owned: OwnedCards,
        card: PokemonCard,
    ): PokemonLine? = chainOf(owned, card)?.let(::PokemonLine)

    private fun chainOf(
        owned: OwnedCards,
        card: PokemonCard,
    ): List<PokemonCard>? {
        if (card.stage == Stage.BASIC) return listOf(card)
        val predecessor = owned.predecessorOf(card) ?: return null
        return chainOf(owned, predecessor)?.plus(card)
    }
}
