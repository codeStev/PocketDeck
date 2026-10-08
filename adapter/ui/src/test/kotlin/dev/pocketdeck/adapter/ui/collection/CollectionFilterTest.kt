package dev.pocketdeck.adapter.ui.collection

import dev.pocketdeck.domain.basicPokemon
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.card.Rarity
import dev.pocketdeck.domain.card.TrainerKind
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.trainer
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CollectionFilterTest {
    private val bulbasaur =
        basicPokemon(
            "Bulbasaur",
            id = "a1-001",
            energyType = EnergyType.GRASS,
        ).copy(rarity = Rarity.COMMON)
    private val charizard = basicPokemon("Charizard ex", id = "a1-036").copy(rarity = Rarity.DOUBLE_RARE)
    private val mew = basicPokemon("Mew", id = "a1a-001", energyType = EnergyType.PSYCHIC).copy(rarity = Rarity.CROWN)
    private val ball = trainer("Poké Ball", TrainerKind.ITEM, id = "a1-214").copy(rarity = Rarity.UNCOMMON)
    private val unknown = basicPokemon("Unknown", id = "b1-001").copy(rarity = null)
    private val catalog = CardCatalog(listOf(bulbasaur, charizard, mew, ball, unknown))
    private val collection =
        CardCollection.EMPTY
            .withCopies(mew.id, 1)
            .withCopies(bulbasaur.id, 3)

    private fun names(filter: CollectionFilter) =
        CollectionListing.build(catalog, collection, filter, Language.ENGLISH).items.map { it.name }

    @Test
    fun `a set is the first part of the card id`() {
        bulbasaur.set shouldBe "a1"
        mew.set shouldBe "a1a"
    }

    @Test
    fun `filters by set, rarity and type`() {
        names(CollectionFilter(sets = setOf("a1"))) shouldBe listOf("Bulbasaur", "Charizard ex", "Poké Ball")
        names(CollectionFilter(rarities = setOf(Rarity.CROWN, Rarity.UNCOMMON))) shouldBe listOf("Mew", "Poké Ball")
        names(CollectionFilter(types = setOf(CardType.OfEnergy(EnergyType.GRASS)))) shouldBe listOf("Bulbasaur")
        names(CollectionFilter(types = setOf(CardType.OfTrainer(TrainerKind.ITEM)))) shouldBe listOf("Poké Ball")
    }

    @Test
    fun `choices of one group are alternatives and the groups all have to match`() {
        names(CollectionFilter(sets = setOf("a1", "b1"), rarities = setOf(Rarity.COMMON, Rarity.DOUBLE_RARE))) shouldBe
            listOf("Bulbasaur", "Charizard ex")
        names(CollectionFilter(sets = setOf("a1a"), rarities = setOf(Rarity.COMMON))) shouldBe emptyList()
    }

    @Test
    fun `combines with the search and the owned-only switch`() {
        names(CollectionFilter(query = "m", ownedOnly = true, sets = setOf("a1a"))) shouldBe listOf("Mew")
    }

    @Test
    fun `sorts by name, by copies owned and by rarity with the rarest first`() {
        names(CollectionFilter(sort = CollectionSort.NAME)) shouldBe
            listOf("Bulbasaur", "Charizard ex", "Mew", "Poké Ball", "Unknown")
        names(CollectionFilter(sort = CollectionSort.COPIES)).take(2) shouldBe listOf("Bulbasaur", "Mew")
        names(CollectionFilter(sort = CollectionSort.RARITY)) shouldBe
            listOf("Mew", "Charizard ex", "Poké Ball", "Bulbasaur", "Unknown")
    }

    @Test
    fun `the number order is the catalog order`() {
        names(CollectionFilter()) shouldBe listOf("Bulbasaur", "Charizard ex", "Mew", "Poké Ball", "Unknown")
    }

    @Test
    fun `offers only sets, rarities and types the catalog has`() {
        val options = CollectionListing.build(catalog, collection, CollectionFilter(), Language.ENGLISH).options

        options.sets shouldBe listOf("a1", "a1a", "b1")
        options.rarities shouldBe listOf(Rarity.COMMON, Rarity.UNCOMMON, Rarity.DOUBLE_RARE, Rarity.CROWN)
        options.types.contains(CardType.OfEnergy(EnergyType.DRAGON)) shouldBe false
        options.types.contains(CardType.OfTrainer(TrainerKind.ITEM)) shouldBe true
    }

    @Test
    fun `counts the choices made in the panel`() {
        CollectionFilter().chosenCount shouldBe 0
        CollectionFilter(ownedOnly = true, query = "x").chosenCount shouldBe 0
        CollectionFilter(sets = setOf("a1", "a2"), sort = CollectionSort.NAME).chosenCount shouldBe 3
        CollectionFilter(
            sets = setOf("a1"),
            sort = CollectionSort.NAME,
            ownedOnly = true,
        ).withoutPanelChoices() shouldBe
            CollectionFilter(ownedOnly = true)
    }
}
