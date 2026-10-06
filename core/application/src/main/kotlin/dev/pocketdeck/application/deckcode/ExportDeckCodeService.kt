package dev.pocketdeck.application.deckcode

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.deck.Deck
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Writes the game's deck code (format reverse-engineered by the community, see tcgp-deck-qr): a count and one 24-bit
 * number per trainer card, the same for Pokémon, then the energy types. Every copy of a card is listed on its own.
 */
class ExportDeckCodeService : ExportDeckCode {
    override fun invoke(deck: Deck): Outcome<DeckCode, DeckCodeFailure> {
        val unknown =
            deck.entries
                .map { it.card }
                .filter { it.gameEntityId == null }
                .distinct()
        if (unknown.isNotEmpty()) return Outcome.Failure(DeckCodeFailure.UnknownToGame(unknown.map { it.name }))
        val energy = deck.energyTypes.mapNotNull { ENERGY_CODES[it] }
        if (energy.isEmpty()) return Outcome.Failure(DeckCodeFailure.NoSelectableEnergy)

        val out = ByteArrayOutputStream()
        writeSection(out, idsOf(deck, TrainerCard::class.java).map { it + TRAINER_ID_OFFSET })
        writeSection(out, idsOf(deck, PokemonCard::class.java))
        out.write(energy.size)
        energy.forEach(out::write)
        return Outcome.Success(DeckCode(Base64.getEncoder().encodeToString(out.toByteArray())))
    }

    private fun idsOf(
        deck: Deck,
        kind: Class<*>,
    ): List<Int> =
        deck.entries
            .filter { kind.isInstance(it.card) }
            .flatMap { entry -> List(entry.copies) { requireNotNull(entry.card.gameEntityId) } }

    private fun writeSection(
        out: ByteArrayOutputStream,
        ids: List<Int>,
    ) {
        out.write(ids.size)
        ids.forEach { id ->
            out.write(id shr BITS_PER_BYTE * 2)
            out.write(id shr BITS_PER_BYTE)
            out.write(id)
        }
    }

    private companion object {
        const val TRAINER_ID_OFFSET = 10_000_000
        const val BITS_PER_BYTE = 8

        /** The numbers the game gives its eight selectable energies; Dragon and Colorless cannot be chosen. */
        val ENERGY_CODES =
            mapOf(
                EnergyType.GRASS to 1,
                EnergyType.FIRE to 2,
                EnergyType.WATER to 3,
                EnergyType.LIGHTNING to 4,
                EnergyType.PSYCHIC to 5,
                EnergyType.FIGHTING to 6,
                EnergyType.DARKNESS to 7,
                EnergyType.METAL to 8,
            )
    }
}
