package dev.pocketdeck.domain.card

enum class Stage { BASIC, STAGE_1, STAGE_2 }

enum class TrainerKind { ITEM, SUPPORTER, TOOL, STADIUM }

/** One attack of a Pokémon. [cost] lists the energy letters as the game does, e.g. `WLC`; [effect] is its text. */
data class Attack(
    val name: String,
    val cost: String,
    val damage: Int?,
    val effect: String?,
)

data class Ability(
    val name: String,
    val effect: String,
)

sealed interface Card {
    val id: CardId
    val name: CardName
    val gameplayKey: GameplayKey

    /** The picture to show by default: the German print when there is one, since the game is usually played in German. */
    val artwork: ArtworkUrl?

    /** The English print of the card's picture; null when the dataset has none. */
    val englishArtwork: ArtworkUrl?

    /** The number the game itself uses for this card; its deck codes name cards by it. Null if it is not known. */
    val gameEntityId: Int?

    /** The name as printed in German; equals [name] when no translation is known. */
    val germanName: CardName

    /** The texts of everything this card does, in English as the dataset has them. */
    val effectTexts: List<String>

    /** Where the card can be obtained: the packs it is drawn from, or other sources such as missions. May be empty. */
    val packs: List<String>

    /** How rare the card is; null when the dataset does not say. */
    val rarity: Rarity?

    /** The expansion the card belongs to, as the first part of its id (`a1`, `b2a`, `pa`). */
    val set: String get() = id.value.substringBefore('-')

    /** The picture in the language the player reads the app in, so the card text on it matches the names around it. */
    fun artworkIn(language: Language): ArtworkUrl? =
        when (language) {
            Language.ENGLISH -> englishArtwork ?: artwork
            Language.GERMAN -> artwork
        }

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
    override val englishArtwork: ArtworkUrl? = null,
    override val germanName: CardName = name,
    override val gameEntityId: Int? = null,
    val attacks: List<Attack> = emptyList(),
    val ability: Ability? = null,
    override val packs: List<String> = emptyList(),
    override val rarity: Rarity? = null,
) : Card {
    override val effectTexts: List<String> get() = attacks.mapNotNull { it.effect } + listOfNotNull(ability?.effect)

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
    override val englishArtwork: ArtworkUrl? = null,
    override val germanName: CardName = name,
    override val gameEntityId: Int? = null,
    /** What the card says; null when not known. */
    val effect: String? = null,
    override val packs: List<String> = emptyList(),
    override val rarity: Rarity? = null,
) : Card {
    override val effectTexts: List<String> get() = listOfNotNull(effect)
}
