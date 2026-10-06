package dev.pocketdeck.domain

import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.card.TrainerKind

fun basicPokemon(
    name: String,
    id: String = "T-$name",
    energyType: EnergyType = EnergyType.FIRE,
    gameplayKey: GameplayKey = GameplayKey.of(CardName(name)),
) = PokemonCard(
    id = CardId(id),
    name = CardName(name),
    energyType = energyType,
    stage = Stage.BASIC,
    hp = 60,
    gameplayKey = gameplayKey,
)

fun evolvedPokemon(
    name: String,
    evolvesFrom: String,
    stage: Stage = Stage.STAGE_1,
    id: String = "T-$name",
    energyType: EnergyType = EnergyType.FIRE,
) = PokemonCard(
    id = CardId(id),
    name = CardName(name),
    energyType = energyType,
    stage = stage,
    hp = 90,
    evolvesFrom = CardName(evolvesFrom),
)

fun trainer(
    name: String,
    kind: TrainerKind = TrainerKind.ITEM,
    id: String = "T-$name",
) = TrainerCard(id = CardId(id), name = CardName(name), kind = kind)
