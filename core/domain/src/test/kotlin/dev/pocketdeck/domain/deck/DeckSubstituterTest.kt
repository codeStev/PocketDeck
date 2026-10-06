package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.charizardEx
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.charmeleon
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.magmar
import dev.pocketdeck.domain.validFireDeck
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DeckSubstituterTest {
    private val deck = validFireDeck()
    private val deckCards = deck.entries.map { it.card }

    private fun pokemon(
        name: String,
        energy: EnergyType = EnergyType.FIRE,
        stage: Stage = Stage.BASIC,
        hp: Int = 60,
        evolvesFrom: String? = null,
    ) = PokemonCard(
        id = CardId("x-$name"),
        name = CardName(name),
        energyType = energy,
        stage = stage,
        hp = hp,
        evolvesFrom = evolvesFrom?.let(::CardName),
        gameplayKey = GameplayKey("$name#x"),
    )

    /** Owns two copies of everything in the deck except [missing], plus two of each of [extra]. */
    private fun substitute(
        missing: dev.pocketdeck.domain.card.Card,
        vararg extra: PokemonCard,
        deck: Deck = this.deck,
    ): SubstitutedDeck? {
        val owned = (deckCards - missing).map { it to 2 } + extra.map { it to 2 }
        val collection = owned.fold(CardCollection.EMPTY) { c, (card, copies) -> c.withCopies(card.id, copies) }
        val catalog = CardCatalog(deckCards + extra)
        return DeckSubstituter().substitute(deck, Inventory.of(collection, catalog), catalog)
    }

    @Test
    fun `replaces a missing pokemon with an owned one of the same kind`() {
        val result = substitute(magmar, pokemon("Elekid"))!!

        result.substitutions.single().let {
            it.original shouldBe magmar
            it.replacement.name.value shouldBe "Elekid"
            it.copies shouldBe 2
        }
        result.deck.size shouldBe Deck.SIZE
        result.deck.copiesNamed(CardName("Elekid")) shouldBe 2
        result.deck.copiesNamed(magmar.name) shouldBe 0
    }

    @Test
    fun `does not replace with another energy type`() {
        substitute(magmar, pokemon("Staryu", energy = EnergyType.WATER)) shouldBe null
    }

    @Test
    fun `does not replace with another stage`() {
        substitute(magmar, pokemon("Machoke", stage = Stage.STAGE_1, evolvesFrom = "Machop")) shouldBe null
    }

    @Test
    fun `does not replace an ex pokemon with a plain one or the other way round`() {
        substitute(magmar, pokemon("Moltres ex")) shouldBe null
    }

    @Test
    fun `prefers the replacement closest in hit points`() {
        val result =
            substitute(
                magmar,
                pokemon("Weak", hp = 30),
                pokemon("Close", hp = 70),
                pokemon("Strong", hp = 120),
            )!!

        result.substitutions
            .single()
            .replacement.name.value shouldBe "Close"
    }

    @Test
    fun `does not take cards the deck already needs`() {
        // Charmander is the only other Fire basic; it is owned twice and the deck uses both, so none is spare.
        substitute(magmar) shouldBe null
    }

    @Test
    fun `gives up when the replacement would break the evolution line`() {
        // Charmander is needed for Charmeleon; a different basic cannot stand in for it.
        substitute(charmander, pokemon("Elekid")) shouldBe null
    }

    @Test
    fun `can replace an evolution when the replacement evolves from the same pokemon`() {
        val result =
            substitute(charizardEx, pokemon("Charizard", stage = Stage.STAGE_2, hp = 150, evolvesFrom = "Charmeleon"))
        // ex status differs, so the plain Charizard is no stand-in for Charizard ex.
        result shouldBe null
        val exStandIn = pokemon("Mega Charizard ex", stage = Stage.STAGE_2, hp = 190, evolvesFrom = "Charmeleon")
        substitute(charizardEx, exStandIn)!!
            .substitutions
            .single()
            .replacement.name.value shouldBe "Mega Charizard ex"
    }

    @Test
    fun `keeps at most two copies of one name`() {
        val result = substitute(magmar, pokemon("Elekid"))!!

        result.deck.entries
            .groupBy { it.card.name }
            .values
            .all { entries -> entries.sumOf { it.copies } <= Deck.MAX_COPIES_PER_NAME } shouldBe true
    }

    @Test
    fun `has nothing to do when nothing is missing`() {
        val everything = deckCards.fold(CardCollection.EMPTY) { c, card -> c.withCopies(card.id, 2) }
        val catalog = CardCatalog(deckCards)

        DeckSubstituter().substitute(deck, Inventory.of(everything, catalog), catalog) shouldBe null
    }
}
