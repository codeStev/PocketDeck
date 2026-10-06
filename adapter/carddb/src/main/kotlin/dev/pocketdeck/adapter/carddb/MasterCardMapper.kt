package dev.pocketdeck.adapter.carddb

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

/** The dataset contains something this app version does not understand (e.g. a newly introduced card kind). */
internal class InvalidCardData(
    cardId: String,
    problem: String,
) : IllegalArgumentException("Card $cardId: $problem")

private const val KIND_POKEMON = "pokemon"
private const val KIND_TRAINER = "trainer"

internal fun MasterCardDto.toDomain(): Card =
    when (kind) {
        KIND_POKEMON -> toPokemon()
        KIND_TRAINER -> toTrainer()
        else -> throw InvalidCardData(id, "unknown card kind '$kind'")
    }

private fun MasterCardDto.toPokemon(): PokemonCard =
    try {
        PokemonCard(
            id = CardId(id),
            name = CardName(names.en),
            energyType = energyTypeOf(),
            stage = stageOf(),
            hp = hp ?: throw InvalidCardData(id, "pokemon without hp"),
            evolvesFrom = evolvesFrom?.let { CardName(it.en) },
            retreatCost = retreatCost ?: 0,
            gameplayKey = GameplayKey(gameplayKey),
            artwork = bestArtwork(),
            germanName = CardName(names.de),
            gameEntityId = gameEntityId,
        )
    } catch (invalid: InvalidCardData) {
        throw invalid
    } catch (invalid: IllegalArgumentException) {
        throw InvalidCardData(id, invalid.message.orEmpty())
    }

private fun MasterCardDto.toTrainer() =
    TrainerCard(
        id = CardId(id),
        name = CardName(names.en),
        kind = trainerKindOf(),
        gameplayKey = GameplayKey(gameplayKey),
        artwork = bestArtwork(),
        germanName = CardName(names.de),
        gameEntityId = gameEntityId,
    )

/** The player's game is usually German, so the German picture matches what they see in the game. */
private fun MasterCardDto.bestArtwork(): ArtworkUrl? = (artwork.de ?: artwork.en)?.let(::ArtworkUrl)

private fun MasterCardDto.energyTypeOf(): EnergyType =
    EnergyType.entries.firstOrNull { it.name.equals(energyType, ignoreCase = true) }
        ?: throw InvalidCardData(id, "unknown energy type '$energyType'")

private fun MasterCardDto.stageOf(): Stage =
    when (stage) {
        "basic" -> Stage.BASIC
        "stage1" -> Stage.STAGE_1
        "stage2" -> Stage.STAGE_2
        else -> throw InvalidCardData(id, "unknown stage '$stage'")
    }

private fun MasterCardDto.trainerKindOf(): TrainerKind =
    TrainerKind.entries.firstOrNull { it.name.equals(trainerKind, ignoreCase = true) }
        ?: throw InvalidCardData(id, "unknown trainer kind '$trainerKind'")
