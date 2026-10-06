package dev.pocketdeck.domain.card

enum class Stage { BASIC, STAGE_1, STAGE_2 }

enum class TrainerKind { ITEM, SUPPORTER, TOOL, STADIUM }

sealed interface Card {
    val id: CardId
    val name: CardName
    val gameplayKey: GameplayKey
    val artwork: ArtworkUrl?

    /** The number the game itself uses for this card; its deck codes name cards by it. Null if it is not known. */
    val gameEntityId: Int?

    /** The name as printed in German; equals [name] when no translation is known. */
    val germanName: CardName

    fun nameIn(language: Language): CardName =
        when (language) {
            Language.ENGLISH -> name
            Language.GERMAN -> germanName
        }
}

data class PokemonCard(
    override val id: CardId,
    override val name: CardName,
    val energyType: EnergyType,
    val stage: Stage,
    val hp: Int,
    val evolvesFrom: CardName? = null,
    val retreatCost: Int = 0,
    override val gameplayKey: GameplayKey = GameplayKey.of(name),
    override val artwork: ArtworkUrl? = null,
    override val germanName: CardName = name,
    override val gameEntityId: Int? = null,
) : Card {
    init {
        require(hp > 0) { "hp must be positive but was $hp" }
        require(retreatCost >= 0) { "retreatCost must not be negative but was $retreatCost" }
        require((stage == Stage.BASIC) == (evolvesFrom == null)) {
            "Only evolved pokemon name a predecessor (stage=$stage, evolvesFrom=$evolvesFrom)"
        }
    }

    val isEx: Boolean get() = name.value.endsWith(EX_SUFFIX)

    private companion object {
        const val EX_SUFFIX = " ex"
    }
}

data class TrainerCard(
    override val id: CardId,
    override val name: CardName,
    val kind: TrainerKind,
    override val gameplayKey: GameplayKey = GameplayKey.of(name),
    override val artwork: ArtworkUrl? = null,
    override val germanName: CardName = name,
    override val gameEntityId: Int? = null,
) : Card
