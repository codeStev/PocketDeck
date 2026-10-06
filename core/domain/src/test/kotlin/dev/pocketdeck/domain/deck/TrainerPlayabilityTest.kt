package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.card.TrainerKind
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class TrainerPlayabilityTest {
    private fun pokemon(
        name: String,
        type: EnergyType = EnergyType.FIRE,
        stage: Stage = Stage.BASIC,
        from: String? = null,
    ) = PokemonCard(
        id = CardId("p-$name"),
        name = CardName(name),
        energyType = type,
        stage = stage,
        hp = 80,
        evolvesFrom = from?.let(::CardName),
        gameplayKey = GameplayKey("$name#p"),
        attacks = listOf(Attack("Hit", "${type.symbol}C", 40, null)),
    )

    private fun trainer(text: String) =
        TrainerCard(CardId("t-${text.hashCode()}"), CardName("T ${text.hashCode()}"), TrainerKind.ITEM, effect = text)

    private val flames = (1..4).map { pokemon("Flame $it") }
    private val catalog =
        CardCatalog(
            flames +
                pokemon("Electivire", EnergyType.LIGHTNING) +
                pokemon("Luxray", EnergyType.LIGHTNING) +
                pokemon("Muk", EnergyType.DARKNESS) +
                pokemon("Weezing", EnergyType.DARKNESS),
        )
    private val playability = TrainerPlayability(catalog)

    private fun deckOf(
        cards: List<Card>,
        energy: Set<EnergyType> = setOf(EnergyType.FIRE),
    ) = Deck(cards.map { DeckEntry(it, 2) }, energy)

    private fun unmet(
        text: String,
        deck: Deck,
    ) = playability.unmet(trainer(text), deck).map { it.describe() }

    private val fireDeck = deckOf(flames.take(3))

    @Test
    fun `a heal for water energy has nothing to do in a pure fire deck`() {
        val text = "Heal 40 damage from each of your Pokémon that has any [ W ] Energy attached."

        unmet(text, fireDeck).single() shouldContain "Water energy"
    }

    @Test
    fun `the same heal is fine when the deck produces water energy`() {
        val text = "Heal 40 damage from each of your Pokémon that has any [ W ] Energy attached."

        unmet(text, deckOf(flames.take(3), setOf(EnergyType.FIRE, EnergyType.WATER))) shouldBe emptyList()
    }

    @Test
    fun `a search for team rocket pokemon needs team rocket pokemon in the deck`() {
        val text =
            "Flip a coin until you get tails. For each heads, " +
                "put a random Pokémon that has “Team Rocket” in its name from your deck into your hand."
        val rocket = listOf(pokemon("Team Rocket's Meowth"), pokemon("Team Rocket's Mewtwo ex"))

        unmet(text, fireDeck).single() shouldContain "Team Rocket"
        unmet(text, deckOf(flames.take(2) + rocket)) shouldBe emptyList()
    }

    @Test
    fun `a boost for water pokemon needs water pokemon, not just the energy`() {
        val text = "Choose 1 of your [ W ] Pokémon in play with a maximum HP of 50 or less."

        unmet(text, fireDeck).single() shouldContain "Water Pokémon"
        unmet(text, deckOf(flames.take(2) + pokemon("Squirtle", EnergyType.WATER))) shouldBe emptyList()
    }

    @Test
    fun `a trainer for named pokemon needs one of them`() {
        val text =
            "Choose 1 of your Electivire or Luxray. Attach 2 [ L ] Energy from your discard pile to that Pokémon."

        val lightning =
            deckOf(flames.take(2) + catalog["p-Luxray".let(::CardId)]!!, setOf(EnergyType.FIRE, EnergyType.LIGHTNING))

        unmet(text, fireDeck).any { "Electivire" in it && "Luxray" in it } shouldBe true
        unmet(text, lightning) shouldBe emptyList()
    }

    @Test
    fun `removing the opponent's energy needs nothing of the deck's own`() {
        unmet(
            "Discard a [ R ] Energy from your opponent's Active Pokémon.",
            deckOf(flames.take(3), setOf(EnergyType.WATER)),
        ) shouldBe
            emptyList()
    }

    @Test
    fun `a stage 2 heal needs a stage 2 pokemon`() {
        val text = "Heal 60 damage from 1 of your Stage 2 Pokemon."
        val stage2 = pokemon("Big Flame", stage = Stage.STAGE_2, from = "Mid Flame")

        unmet(text, fireDeck).single() shouldContain "Stage 2"
        unmet(text, deckOf(flames.take(2) + stage2)) shouldBe emptyList()
    }

    @Test
    fun `a search for a mega pokemon ex needs one`() {
        val text = "Put a random Mega Evolution Pokémon ex from your deck into your hand."
        val mega = pokemon("Mega Flame ex")

        unmet(text, fireDeck).single() shouldContain "Mega"
        unmet(text, deckOf(flames.take(2) + mega)) shouldBe emptyList()
    }

    @Test
    fun `effects that need nothing in particular are always playable`() {
        unmet("Draw 2 cards.", fireDeck) shouldBe emptyList()
        unmet("Heal 20 damage from 1 of your Pokémon.", fireDeck) shouldBe emptyList()
        unmet("Switch out your opponent's Active Pokémon to the Bench.", fireDeck) shouldBe emptyList()
    }

    @Test
    fun `a trainer without a known text is not judged`() {
        playability.unmet(TrainerCard(CardId("t-x"), CardName("X"), TrainerKind.ITEM), fireDeck) shouldBe emptyList()
    }

    @Test
    fun `a single matching pokemon is enough for a boost that wants a type`() {
        val text = "Choose 1 of your [ W ] Pokémon in play."
        val splash =
            Deck(
                flames.take(3).map {
                    DeckEntry(it, 2)
                } + DeckEntry(pokemon("Squirtle", EnergyType.WATER), 1),
                setOf(EnergyType.FIRE),
            )

        unmet(text, splash) shouldBe emptyList()
    }

    @Test
    fun `knows pokemon with very short names`() {
        val text = "Put your Muk or Weezing in the Active Spot into your hand."

        unmet(text, fireDeck).single() shouldContain "Muk"
    }

    @Test
    fun `a list of energy types means any of them, so none is required on its own`() {
        val text = "Move a [ R ] , [ W ] , or [ L ] Energy from 1 of your Benched Pokémon to your Active Pokémon."

        unmet(text, deckOf(flames.take(3), setOf(EnergyType.FIRE))) shouldBe emptyList()
    }
}
