package dev.pocketdeck.adapter.carddb

import kotlinx.serialization.Serializable

/** Mirrors `master_cards.json` as produced by `tools/master_dataset`. Only the fields the app uses. */
@Serializable
internal data class MasterDatasetDto(
    val schemaVersion: Int,
    val cards: List<MasterCardDto>,
)

@Serializable
internal data class MasterCardDto(
    val id: String,
    val kind: String,
    val names: NamesDto,
    val gameplayKey: String,
    val artwork: ArtworkDto,
    val energyType: String? = null,
    val stage: String? = null,
    val evolvesFrom: NamesDto? = null,
    val hp: Int? = null,
    val retreatCost: Int? = null,
    val trainerKind: String? = null,
)

@Serializable
internal data class NamesDto(
    val en: String,
    val de: String,
)

@Serializable
internal data class ArtworkDto(
    val en: String? = null,
    val de: String? = null,
)
