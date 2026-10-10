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
    val gameEntityId: Int? = null,
    /** The packs (or other sources, such as missions) the card can be obtained from. */
    val packs: List<String> = emptyList(),
    /** The short rarity code (`C`, `RR`, `SAR`, ...). */
    val rarityCode: String? = null,
    val attacks: List<AttackDto> = emptyList(),
    val ability: AbilityDto? = null,
    /** The effect text of a trainer card. */
    val text: String? = null,
)

@Serializable
internal data class AttackDto(
    val name: String,
    val cost: String = "",
    val damage: Int? = null,
    val effect: String? = null,
)

@Serializable
internal data class AbilityDto(
    val name: String,
    val effect: String,
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
