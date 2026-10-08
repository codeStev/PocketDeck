package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Ability
import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage

/** A Pokémon for opening tests; unlike the shared fixtures it lets a test choose type, retreat cost, ability and attacks. */
internal fun pokemon(
    name: String,
    type: EnergyType = EnergyType.FIRE,
    stage: Stage = Stage.BASIC,
    evolvesFrom: String? = null,
    retreatCost: Int = 0,
    ability: String? = null,
    attacks: List<Attack> = emptyList(),
) = PokemonCard(
    id = CardId("T-$name"),
    name = CardName(name),
    energyType = type,
    stage = stage,
    hp = 60,
    evolvesFrom = evolvesFrom?.let(::CardName) ?: if (stage == Stage.BASIC) null else CardName("Pre-$name"),
    retreatCost = retreatCost,
    attacks = attacks,
    ability = ability?.let { Ability("Ability", it) },
)
