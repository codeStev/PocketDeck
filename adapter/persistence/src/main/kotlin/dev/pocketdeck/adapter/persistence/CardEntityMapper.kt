package dev.pocketdeck.adapter.persistence

import dev.pocketdeck.domain.card.Ability
import dev.pocketdeck.domain.card.ArtworkUrl
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.card.TrainerKind

private const val KIND_POKEMON = "POKEMON"
private const val KIND_TRAINER = "TRAINER"

internal fun Card.toEntity(): CardEntity =
    when (this) {
        is PokemonCard -> {
            baseEntity(KIND_POKEMON).copy(
                energyType = energyType.name,
                stage = stage.name,
                hp = hp,
                evolvesFrom = evolvesFrom?.value,
                retreatCost = retreatCost,
                attacks = AttackCodec.encode(attacks),
                abilityName = ability?.name,
                abilityEffect = ability?.effect,
            )
        }

        is TrainerCard -> {
            baseEntity(KIND_TRAINER).copy(trainerKind = kind.name, effectText = effect)
        }
    }

private fun Card.baseEntity(kind: String) =
    CardEntity(
        id = id.value,
        kind = kind,
        name = name.value,
        germanName = germanName.value,
        gameplayKey = gameplayKey.value,
        artworkUrl = artwork?.value,
        energyType = null,
        stage = null,
        hp = null,
        evolvesFrom = null,
        retreatCost = null,
        trainerKind = null,
        gameEntityId = gameEntityId,
    )

/** Throws [IllegalStateException] for rows this app version cannot have written (corrupt or newer database). */
internal fun CardEntity.toDomain(): Card =
    when (kind) {
        KIND_POKEMON -> toPokemon()
        KIND_TRAINER -> toTrainer()
        else -> error("Card $id has unknown kind '$kind'")
    }

private fun CardEntity.toPokemon() =
    PokemonCard(
        id = CardId(id),
        name = CardName(name),
        energyType = EnergyType.valueOf(requireColumn(energyType, "energyType")),
        stage = Stage.valueOf(requireColumn(stage, "stage")),
        hp = requireColumn(hp, "hp"),
        evolvesFrom = evolvesFrom?.let(::CardName),
        retreatCost = requireColumn(retreatCost, "retreatCost"),
        gameplayKey = GameplayKey(gameplayKey),
        artwork = artworkUrl?.let(::ArtworkUrl),
        germanName = germanNameOrEnglish(),
        gameEntityId = gameEntityId,
        attacks = AttackCodec.decode(attacks),
        ability = abilityName?.let { Ability(it, abilityEffect.orEmpty()) },
    )

private fun CardEntity.toTrainer() =
    TrainerCard(
        id = CardId(id),
        name = CardName(name),
        kind = TrainerKind.valueOf(requireColumn(trainerKind, "trainerKind")),
        gameplayKey = GameplayKey(gameplayKey),
        artwork = artworkUrl?.let(::ArtworkUrl),
        germanName = germanNameOrEnglish(),
        gameEntityId = gameEntityId,
        effect = effectText,
    )

private fun CardEntity.germanNameOrEnglish() = CardName(germanName.ifBlank { name })

private fun <T : Any> CardEntity.requireColumn(
    value: T?,
    column: String,
): T = value ?: error("Card $id (kind $kind) is missing column '$column'")
