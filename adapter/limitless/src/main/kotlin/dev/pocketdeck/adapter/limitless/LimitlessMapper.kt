package dev.pocketdeck.adapter.limitless

import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.meta.ArchetypeName
import dev.pocketdeck.domain.meta.DecklistEntry
import dev.pocketdeck.domain.meta.MetaDecklist
import dev.pocketdeck.domain.meta.TournamentResult

private const val NUMBER_WIDTH = 3

/** Limitless writes `A1` / `P-A` and `39`; the card dataset writes `a1-039` / `pa-039`. */
internal fun CardLineDto.cardId(): CardId =
    CardId("${set.lowercase().replace("-", "")}-${number.trim().padStart(NUMBER_WIDTH, '0')}")

internal fun String.toEnergyType(): EnergyType? =
    EnergyType.entries.firstOrNull {
        it.name.equals(trim(), ignoreCase = true)
    }

/** A standing without a decklist (the player did not submit one) gives nothing to learn from. */
internal fun StandingDto.toDecklist(position: Int): MetaDecklist? {
    val list = decklist ?: return null
    val archetype = deck?.name?.takeIf { it.isNotBlank() } ?: return null
    val entries = (list.pokemon + list.trainer).filter { it.count > 0 }.map { DecklistEntry(it.cardId(), it.count) }
    if (entries.isEmpty()) return null
    val record = record ?: RecordDto()
    return MetaDecklist(
        archetype = ArchetypeName(archetype),
        entries = entries,
        energyTypes = list.energy.mapNotNull { it.toEnergyType() }.toSet(),
        result = TournamentResult(placing ?: position, record.wins, record.losses, record.ties),
    )
}
