package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.Ability
import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EffectTag
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.card.TrainerKind
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.collection.Inventory
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** A deck built around throwing energy away and getting it back, as in the player's Rayquaza deck. */
class DeckSubstituterEffectsTest {
    private val discardEnergy =
        "Discard 2 Energy from this Pokémon. During your opponent's next turn, this Pokémon takes −30 damage."
    private val recycle =
        Ability(
            "Dragon's Blessing",
            "Once during your turn, you may attach an Energy from your discard pile to your Active Pokémon.",
        )

    private fun pokemon(
        name: String,
        hp: Int = 100,
        cost: String = "WLC",
        effect: String? = null,
        ability: Ability? = null,
        type: EnergyType = EnergyType.DRAGON,
    ) = PokemonCard(
        id = CardId("x-$name"),
        name = CardName(name),
        energyType = type,
        stage = Stage.BASIC,
        hp = hp,
        gameplayKey = GameplayKey("$name#x"),
        attacks = listOf(Attack("Attack", cost, 100, effect)),
        ability = ability,
    )

    private fun trainer(
        name: String,
        kind: TrainerKind,
        text: String,
    ) = TrainerCard(CardId("t-$name"), CardName(name), kind, GameplayKey("$name#t"), effect = text)

    private val fire = pokemon("Gouging Fire", effect = discardEnergy)
    private val dragonair = pokemon("Dragonair", cost = "CC", ability = recycle)
    private val filler = (1..8).map { pokemon("Filler $it", cost = "C") }

    private fun deckWith(
        hole: Card,
        energy: Set<EnergyType> = setOf(EnergyType.WATER, EnergyType.LIGHTNING),
    ): Deck {
        val others = listOf(dragonair) + filler.take(8)
        val entries = (others.map { DeckEntry(it, 2) } + DeckEntry(hole, 2)).take(10)
        return Deck(entries, energy)
    }

    private fun substitute(
        hole: Card,
        vararg available: Card,
        energy: Set<EnergyType> = setOf(EnergyType.WATER, EnergyType.LIGHTNING),
    ): SubstitutedDeck? {
        val deck = deckWith(hole, energy)
        val owned = (deck.entries.map { it.card } - hole).map { it to 2 } + available.map { it to 2 }
        val catalog = CardCatalog((deck.entries.map { it.card } + available).distinct())
        val collection = owned.fold(CardCollection.EMPTY) { c, (card, n) -> c.withCopies(card.id, n) }
        return DeckSubstituter().substitute(deck, Inventory.of(collection, catalog), catalog)
    }

    @Test
    fun `keeps the playstyle by replacing with a card that also throws energy away`() {
        val thrower = pokemon("Thrower", hp = 130, effect = "Discard all Energy from this Pokémon.")
        val closerButPlain = pokemon("Plain", hp = 100)

        val result = substitute(fire, closerButPlain, thrower)!!

        result.substitutions.single().replacement shouldBe thrower
        result.substitutions.single().sharedEffects shouldBe setOf(EffectTag.DISCARD_OWN_ENERGY)
    }

    @Test
    fun `does not replace with a card that lacks the effect the deck builds on`() {
        substitute(fire, pokemon("Plain", hp = 100)) shouldBe null
    }

    @Test
    fun `a card of another type is fine when the deck's energy can pay for its attack`() {
        val water =
            pokemon("Swimmer", cost = "WC", effect = "Discard a [W] Energy from this Pokémon.", type = EnergyType.WATER)

        substitute(fire, water)!!.substitutions.single().replacement shouldBe water
    }

    @Test
    fun `a card whose attacks need an energy the deck does not produce is no stand-in`() {
        val psychic =
            pokemon("Mind", cost = "PC", effect = "Discard a [P] Energy from this Pokémon.", type = EnergyType.PSYCHIC)

        substitute(fire, psychic) shouldBe null
    }

    @Test
    fun `a trainer is replaced by one of the same kind with the same effect`() {
        val ownDraw = trainer("Research", TrainerKind.SUPPORTER, "Draw 2 cards.")
        val otherDraw = trainer("Planning", TrainerKind.SUPPORTER, "Draw 3 cards.")

        val result = substitute(ownDraw, otherDraw)!!

        result.substitutions.single().replacement shouldBe otherDraw
    }

    @Test
    fun `a trainer is not replaced by one that does something else or is another kind`() {
        val draw = trainer("Research", TrainerKind.SUPPORTER, "Draw 2 cards.")

        substitute(draw, trainer("Potion", TrainerKind.SUPPORTER, "Heal 20 damage from 1 of your Pokémon.")) shouldBe
            null
        substitute(draw, trainer("Gadget", TrainerKind.ITEM, "Draw 2 cards.")) shouldBe null
    }

    @Test
    fun `a trainer without a known effect is never replaced`() {
        val unknown = TrainerCard(CardId("t-u"), CardName("Unknown"), TrainerKind.ITEM, GameplayKey("u#t"))

        substitute(unknown, trainer("Other", TrainerKind.ITEM, "Draw 2 cards.")) shouldBe null
    }

    @Test
    fun `a trainer is not a stand-in when its condition cannot be met in the deck`() {
        val ownDraw = trainer("Research", TrainerKind.SUPPORTER, "Draw 2 cards.")
        val picky = trainer("Picky", TrainerKind.SUPPORTER, "Draw 3 cards. Choose 1 of your [ F ] Pokémon in play.")

        substitute(ownDraw, picky) shouldBe null
    }
}
