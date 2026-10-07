package dev.pocketdeck.adapter.persistence

import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.deck.SavedCard

/** Writes the cards and energy of a saved deck as short strings: `a1-036:2;a1-037:1` and `FIRE,WATER`. */
internal object SavedDeckCodec {
    private const val CARD_SEPARATOR = ";"
    private const val COPIES_SEPARATOR = ":"
    private const val ENERGY_SEPARATOR = ","

    fun writeCards(cards: List<SavedCard>): String =
        cards.joinToString(CARD_SEPARATOR) { "${it.id.value}$COPIES_SEPARATOR${it.copies}" }

    fun readCards(text: String): List<SavedCard> =
        text
            .split(CARD_SEPARATOR)
            .filter { it.isNotEmpty() }
            .map { part ->
                val (id, copies) = part.split(COPIES_SEPARATOR)
                SavedCard(CardId(id), copies.toInt())
            }

    fun writeEnergy(types: Set<EnergyType>): String = types.joinToString(ENERGY_SEPARATOR) { it.name }

    fun readEnergy(text: String): Set<EnergyType> =
        text
            .split(ENERGY_SEPARATOR)
            .filter { it.isNotEmpty() }
            .map(EnergyType::valueOf)
            .toSet()
}
