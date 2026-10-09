package dev.pocketdeck.application.advice

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
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.meta.Archetype
import dev.pocketdeck.domain.meta.ArchetypeName
import dev.pocketdeck.domain.meta.DecklistEntry
import dev.pocketdeck.domain.meta.MetaDecklist
import dev.pocketdeck.domain.meta.MetaSnapshot
import dev.pocketdeck.domain.meta.TournamentResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class AdvicePromptWriterTest {
    private val bulbasaur =
        PokemonCard(
            id = CardId("a1-001"),
            name = CardName("Bulbasaur"),
            energyType = EnergyType.GRASS,
            stage = Stage.BASIC,
            hp = 70,
            retreatCost = 1,
            gameplayKey = GameplayKey("Bulbasaur#a"),
            attacks =
                listOf(
                    Attack("Vine Whip", "GC", 40, null),
                    Attack("Leech", "GGC", 60, "Heal 20 damage from this Pokémon."),
                ),
        )
    private val ivysaur =
        bulbasaur.copy(
            id = CardId("a1-002"),
            name = CardName("Ivysaur"),
            stage = Stage.STAGE_1,
            evolvesFrom = CardName("Bulbasaur"),
            hp = 90,
            gameplayKey = GameplayKey("Ivysaur#a"),
        )
    private val erika =
        TrainerCard(
            CardId("a1-219"),
            CardName("Erika"),
            TrainerKind.SUPPORTER,
            GameplayKey("Erika#a"),
            effect = "Heal 50 damage.",
        )
    private val unowned =
        TrainerCard(
            CardId("a1-220"),
            CardName("Unowned Item"),
            TrainerKind.ITEM,
            GameplayKey("U#a"),
            effect = "Draw a card.",
        )
    private val cards: List<Card> = listOf(bulbasaur, ivysaur, erika, unowned)
    private val catalog = CardCatalog(cards)

    private fun request(
        wish: String? = null,
        copies: Int = 3,
        meta: MetaSnapshot = MetaSnapshot.EMPTY,
        feedback: List<AdviceFeedback> = emptyList(),
    ): AdviceRequest {
        val owned =
            listOf(
                bulbasaur,
                ivysaur,
                erika,
            ).fold(CardCollection.EMPTY) { c, card -> c.withCopies(card.id, copies) }
        return AdviceRequest(wish, catalog, Inventory.of(owned, catalog), meta, feedback)
    }

    @Test
    fun `lists every owned card with its id and at most two usable copies`() {
        val text = AdvicePromptWriter.write(request(copies = 3)).collection

        text shouldContain "a1-001 | Bulbasaur | Grass Basic | 70hp r1 x2 |"
        text shouldContain "a1-002 | Ivysaur | Grass Stage 1 (from Bulbasaur) | 90hp"
        text shouldContain "a1-219 | Erika | Supporter | x2 | Heal 50 damage."
    }

    @Test
    fun `does not mention cards the player does not own`() {
        AdvicePromptWriter.write(request()).collection shouldNotContain "a1-220"
    }

    @Test
    fun `describes attacks with cost damage and effect`() {
        val text = AdvicePromptWriter.write(request()).collection

        text shouldContain "GC 40 Vine Whip"
        // Bulbasaur and Ivysaur share the effect, so it is written once and referred to.
        text shouldContain "GGC 60 Leech: [E1]"
        text shouldContain "[E1] = Heal 20 damage from this Pokémon."
    }

    @Test
    fun `passes the wish on and leaves it out when there is none`() {
        AdvicePromptWriter.write(request(wish = "a grass deck")).task shouldContain "a grass deck"
        AdvicePromptWriter.write(request(wish = null)).task shouldContain "best deck"
    }

    @Test
    fun `summarises the tournament picture`() {
        val list =
            MetaDecklist(
                ArchetypeName("Grass Rush"),
                listOf(DecklistEntry(bulbasaur.id, 2), DecklistEntry(erika.id, 2)),
                setOf(EnergyType.GRASS),
                TournamentResult(1, 8, 2, 0),
            )
        val meta = MetaSnapshot(listOf(Archetype(ArchetypeName("Grass Rush"), listOf(list), share = 0.25)))

        val text = AdvicePromptWriter.write(request(meta = meta)).tournament

        text shouldContain "Grass Rush"
        text shouldContain "2 Bulbasaur"
        text shouldContain "Erika"
    }

    @Test
    fun `says plainly when there is no tournament data`() {
        AdvicePromptWriter.write(request()).tournament shouldContain "No tournament data"
    }

    @Test
    fun `states the rules the deck must follow`() {
        val system = AdvicePromptWriter.write(request()).system

        system shouldContain "exactly 20"
        system shouldContain "At most 2"
        system shouldContain "Basic Pokémon"
        system shouldContain AdviceFormat.TOOL_NAME
    }

    @Test
    fun `explains what was wrong with an earlier answer`() {
        val advice =
            DeckAdvice(
                "T",
                "R",
                listOf(AdvisedCard(bulbasaur.id.value, bulbasaur.name.value, 2)),
                listOf(EnergyType.GRASS),
            )

        val text = AdviceFeedback(advice, listOf("The deck has 2 cards but must have exactly 20")).describe()

        text shouldContain "exactly 20"
        text shouldContain "corrected deck"
    }

    @Test
    fun `knows the energy names the advisor may use`() {
        AdviceFormat.energyName(EnergyType.LIGHTNING) shouldBe "Lightning"
        AdviceFormat.energyOf("lightning") shouldBe EnergyType.LIGHTNING
        AdviceFormat.energyOf("Banana") shouldBe null
    }

    private fun unplayable(): Pair<PokemonCard, PokemonCard> {
        val venusaur =
            bulbasaur.copy(
                id = CardId("a1-003"),
                name = CardName("Venusaur"),
                stage = Stage.STAGE_2,
                evolvesFrom = CardName("Ivysaur"),
                hp = 150,
                gameplayKey = GameplayKey("Venusaur#a"),
            )
        val charizard =
            bulbasaur.copy(
                id = CardId("a1-036"),
                name = CardName("Charizard"),
                stage = Stage.STAGE_2,
                evolvesFrom = CardName("Charmeleon"),
                gameplayKey = GameplayKey("Charizard#a"),
            )
        return venusaur to charizard
    }

    private fun requestOwning(
        vararg owned: Card,
        all: List<Card> = cards,
    ): AdviceRequest {
        val collection = owned.fold(CardCollection.EMPTY) { c, card -> c.withCopies(card.id, 2) }
        val catalog = CardCatalog(all)
        return AdviceRequest(null, catalog, Inventory.of(collection, catalog), MetaSnapshot.EMPTY)
    }

    @Test
    fun `leaves out an evolution the player cannot play because they own nothing to evolve from`() {
        val (venusaur, charizard) = unplayable()
        val request = requestOwning(bulbasaur, ivysaur, venusaur, charizard, all = cards + venusaur + charizard)

        val text = AdvicePromptWriter.write(request).collection

        text shouldContain "a1-003 | Venusaur"
        text shouldNotContain "a1-036"
    }

    @Test
    fun `keeps a Stage 2 that Rare Candy can bring in without its Stage 1`() {
        val (venusaur, _) = unplayable()
        val rareCandy =
            TrainerCard(
                CardId("a1-216"),
                CardName("Rare Candy"),
                TrainerKind.ITEM,
                GameplayKey("RC#a"),
                effect = "Evolve.",
            )
        val request = requestOwning(bulbasaur, venusaur, rareCandy, all = cards + venusaur + rareCandy)

        AdvicePromptWriter.write(request).collection shouldContain "a1-003 | Venusaur"
    }

    @Test
    fun `writes an effect that several cards share once and refers to it`() {
        val shared = "Flip a coin. If heads, this attack does 30 more damage."
        val a = bulbasaur.copy(attacks = listOf(Attack("Slam", "C", 20, shared)))
        val b = a.copy(id = CardId("a1-010"), name = CardName("Oddish"), gameplayKey = GameplayKey("Oddish#a"))
        val request = requestOwning(a, b, all = listOf(a, b))

        val text = AdvicePromptWriter.write(request).collection

        text.split(shared).size - 1 shouldBe 1
        text shouldContain "[E1] = $shared"
        text shouldContain "C 20 Slam: [E1]"
    }

    @Test
    fun `expanding the references gives back exactly the effects of every listed card`() {
        val shared = "Flip a coin. If heads, this attack does 30 more damage."
        val a = bulbasaur.copy(attacks = listOf(Attack("Slam", "C", 20, shared), Attack("Poke", "G", 10, "Short.")))
        val b = a.copy(id = CardId("a1-010"), name = CardName("Oddish"), gameplayKey = GameplayKey("Oddish#a"))
        val request = requestOwning(a, b, erika, all = listOf(a, b, erika))

        val text = AdvicePromptWriter.write(request).collection
        val definitions =
            Regex(
                """(\[E\d+]) = (.*)""",
            ).findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
        val expanded =
            definitions.entries.fold(
                text.substringAfter("List of effects:").substringAfter("\n\n"),
            ) { t, (code, full) -> t.replace(code, full) }

        expanded shouldContain "C 20 Slam: $shared"
        expanded shouldContain "G 10 Poke: Short."
        expanded shouldContain "Supporter | x2 | Heal 50 damage."
    }

    @Test
    fun `an effect shared only once or too short for a reference stays in the line`() {
        val short = "Draw a card."
        val a = bulbasaur.copy(attacks = listOf(Attack("Slam", "C", 20, short)))
        val b = a.copy(id = CardId("a1-010"), name = CardName("Oddish"), gameplayKey = GameplayKey("Oddish#a"))

        val text = AdvicePromptWriter.write(requestOwning(a, b, all = listOf(a, b))).collection

        text shouldNotContain "[E1]"
        text shouldNotContain "List of effects"
    }
}
