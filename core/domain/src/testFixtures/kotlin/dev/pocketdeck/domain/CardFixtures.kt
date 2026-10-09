package dev.pocketdeck.domain

import dev.pocketdeck.domain.card.ArtworkUrl
import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Rarity
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.card.TrainerKind

private val TACKLE = Attack("Tackle", "C", 20, null)

/** Real cards all come from somewhere; a catalog where no card has a pack counts as stored by an older version. */
const val TEST_PACK = "Test Pack"

fun basicPokemon(
    name: String,
    id: String = "T-$name",
    energyType: EnergyType = EnergyType.FIRE,
    gameplayKey: GameplayKey = GameplayKey.of(CardName(name)),
    packs: List<String> = listOf(TEST_PACK),
) = PokemonCard(
    id = CardId(id),
    name = CardName(name),
    energyType = energyType,
    stage = Stage.BASIC,
    hp = 60,
    gameplayKey = gameplayKey,
    gameEntityId = entityIdOf(id),
    englishArtwork = ArtworkUrl("https://example.test/en/$id.webp"),
    attacks = listOf(TACKLE),
    packs = packs,
    rarity = Rarity.COMMON,
)

fun evolvedPokemon(
    name: String,
    evolvesFrom: String,
    stage: Stage = Stage.STAGE_1,
    id: String = "T-$name",
    energyType: EnergyType = EnergyType.FIRE,
    packs: List<String> = listOf(TEST_PACK),
) = PokemonCard(
    id = CardId(id),
    name = CardName(name),
    energyType = energyType,
    stage = stage,
    hp = 90,
    evolvesFrom = CardName(evolvesFrom),
    gameEntityId = entityIdOf(id),
    englishArtwork = ArtworkUrl("https://example.test/en/$id.webp"),
    attacks = listOf(TACKLE),
    packs = packs,
    rarity = Rarity.COMMON,
)

fun trainer(
    name: String,
    kind: TrainerKind = TrainerKind.ITEM,
    id: String = "T-$name",
    packs: List<String> = listOf(TEST_PACK),
    effect: String? = null,
) = TrainerCard(
    id = CardId(id),
    name = CardName(name),
    kind = kind,
    gameEntityId = entityIdOf(id),
    englishArtwork = ArtworkUrl("https://example.test/en/$id.webp"),
    packs = packs,
    effect = effect,
    rarity = Rarity.COMMON,
)

/** A stable stand-in for the number the game gives a card. */
private fun entityIdOf(id: String) = (id.hashCode() and 0xFFFFF) + 1
