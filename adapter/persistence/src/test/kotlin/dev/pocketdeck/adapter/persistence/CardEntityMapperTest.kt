package dev.pocketdeck.adapter.persistence

import dev.pocketdeck.domain.card.Ability
import dev.pocketdeck.domain.card.ArtworkUrl
import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.card.TrainerKind
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CardEntityMapperTest {
    private val charizard: Card =
        PokemonCard(
            id = CardId("a1-036"),
            name = CardName("Charizard ex"),
            energyType = EnergyType.FIRE,
            stage = Stage.STAGE_2,
            hp = 180,
            evolvesFrom = CardName("Charmeleon"),
            retreatCost = 2,
            gameplayKey = GameplayKey("Charizard ex#abc"),
            artwork = ArtworkUrl("https://example.test/a1-036.webp"),
            germanName = CardName("Glurak ex"),
            gameEntityId = 360,
            attacks =
                listOf(
                    Attack("Fire Spin", "RRC", 150, "Discard 2 [R] Energy from this Pokémon."),
                    Attack("Claw", "C", null, null),
                ),
            ability = Ability("Blaze", "Once during your turn, you may attach a [R] Energy."),
            packs = listOf("Charizard", "Premium Missions"),
        )
    private val bulbasaur: Card =
        PokemonCard(CardId("a1-001"), CardName("Bulbasaur"), EnergyType.GRASS, Stage.BASIC, hp = 70)
    private val giovanni: Card =
        TrainerCard(
            CardId("a1-223"),
            CardName("Giovanni"),
            TrainerKind.SUPPORTER,
            GameplayKey("Giovanni#x"),
            gameEntityId = 230,
            packs = listOf("Mewtwo"),
        )

    @Test
    fun `every kind of card survives a round trip through its entity`() {
        listOf(charizard, bulbasaur, giovanni).forEach { card ->
            card.toEntity().toDomain() shouldBe card
        }
    }

    @Test
    fun `the packs of a card are kept in order and a card without packs has none`() {
        charizard.toEntity().toDomain().packs shouldBe listOf("Charizard", "Premium Missions")
        bulbasaur.toEntity().packs shouldBe null
        bulbasaur.toEntity().toDomain().packs shouldBe emptyList()
    }

    @Test
    fun `a pokemon entity carries its battle stats`() {
        val entity = charizard.toEntity()

        entity.kind shouldBe "POKEMON"
        entity.energyType shouldBe "FIRE"
        entity.stage shouldBe "STAGE_2"
        entity.hp shouldBe 180
        entity.evolvesFrom shouldBe "Charmeleon"
        entity.trainerKind shouldBe null
    }

    @Test
    fun `a trainer entity carries only its kind`() {
        val entity = giovanni.toEntity()

        entity.kind shouldBe "TRAINER"
        entity.trainerKind shouldBe "SUPPORTER"
        entity.hp shouldBe null
        entity.energyType shouldBe null
    }

    @Test
    fun `the german name is stored and restored`() {
        charizard.toEntity().germanName shouldBe "Glurak ex"
        (charizard.toEntity().toDomain() as PokemonCard).germanName shouldBe CardName("Glurak ex")
    }

    @Test
    fun `rows migrated without a german name fall back to the english name`() {
        val migrated = charizard.toEntity().copy(germanName = "")

        migrated.toDomain().germanName shouldBe CardName("Charizard ex")
    }

    @Test
    fun `a corrupt entity is reported instead of guessed`() {
        val corrupt = bulbasaur.toEntity().copy(kind = "DIGIMON")

        shouldThrow<IllegalStateException> { corrupt.toDomain() }
    }
}
