package dev.pocketdeck.adapter.persistence

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** One row per printed card. Pokémon and trainers share a table; kind-specific columns are nullable. */
@Entity(tableName = "card")
internal data class CardEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    // Added in schema version 2; rows migrated from version 1 have no German name until the next sync.
    @ColumnInfo(defaultValue = "") val germanName: String,
    val gameplayKey: String,
    val artworkUrl: String?,
    val energyType: String?,
    val stage: String?,
    val hp: Int?,
    val evolvesFrom: String?,
    val retreatCost: Int?,
    val trainerKind: String?,
    // Added in schema version 4; rows migrated from version 3 have none until the next sync.
    val gameEntityId: Int? = null,
    // Added in schema version 5: what the card does, see CardEffectsCodec for the format.
    val attacks: String? = null,
    val abilityName: String? = null,
    val abilityEffect: String? = null,
    val effectText: String? = null,
)

@Entity(tableName = "owned_card")
internal data class OwnedCardEntity(
    @PrimaryKey val cardId: String,
    val copies: Int,
)

/** The collection as it was before the last scan was saved. */
@Entity(tableName = "owned_card_backup")
internal data class BackupOwnedCardEntity(
    @PrimaryKey val cardId: String,
    val copies: Int,
)
