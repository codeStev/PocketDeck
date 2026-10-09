package dev.pocketdeck.adapter.ui.collection

import dev.pocketdeck.domain.basicPokemon
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerKind
import dev.pocketdeck.domain.charizardEx
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.magmar
import dev.pocketdeck.domain.pokeBall
import dev.pocketdeck.domain.professorsResearch
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CollectionListingTest {
    private val catalog = CardCatalog(listOf(charmander, charizardEx, magmar, pokeBall, professorsResearch))
    private val collection =
        CardCollection.EMPTY
            .withCopies(charmander.id, 2)
            .withCopies(pokeBall.id, 1)

    private fun build(
        query: String = "",
        ownedOnly: Boolean = false,
        collection: CardCollection = this.collection,
        language: Language = Language.ENGLISH,
    ) = CollectionListing.build(catalog, collection, CollectionFilter(query, ownedOnly), language)

    @Test
    fun `lists every catalog card with its owned copies in catalog order`() {
        val listing = build()

        listing.items.map { it.name to it.copies } shouldBe
            listOf(
                "Charmander" to 2,
                "Charizard ex" to 0,
                "Magmar" to 0,
                "Poké Ball" to 1,
                "Professor's Research" to 0,
            )
    }

    @Test
    fun `counts unique and total owned cards`() {
        val listing = build()

        listing.uniqueOwned shouldBe 2
        listing.totalOwned shouldBe 3
        listing.catalogSize shouldBe 5
    }

    @Test
    fun `owned only hides cards without copies`() {
        build(ownedOnly = true).items.map { it.name } shouldBe listOf("Charmander", "Poké Ball")
    }

    @Test
    fun `search matches names case insensitively`() {
        build(query = "CHAR").items.map { it.name } shouldBe listOf("Charmander", "Charizard ex")
    }

    @Test
    fun `search matches card ids`() {
        build(query = "T-Magmar").items.map { it.name } shouldBe listOf("Magmar")
    }

    @Test
    fun `search and owned only combine`() {
        build(query = "char", ownedOnly = true).items.map { it.name } shouldBe listOf("Charmander")
    }

    @Test
    fun `blank search matches everything`() {
        build(query = "   ").items.size shouldBe 5
    }

    @Test
    fun `owned cards missing from the catalog do not count`() {
        val stray = collection.withCopies(basicPokemon("Ghost").id, 4)

        build(collection = stray).totalOwned shouldBe 3
    }

    @Test
    fun `describes pokemon by type stage and hp and trainers by kind`() {
        val items = build().items.associateBy { it.name }

        items.getValue("Charmander").detail shouldBe CardDetail.OfPokemon(EnergyType.FIRE, Stage.BASIC, 60)
        items.getValue("Charizard ex").detail shouldBe CardDetail.OfPokemon(EnergyType.FIRE, Stage.STAGE_2, 90)
        items.getValue("Poké Ball").detail shouldBe CardDetail.OfTrainer(TrainerKind.ITEM)
        items.getValue("Professor's Research").detail shouldBe CardDetail.OfTrainer(TrainerKind.SUPPORTER)
    }

    @Test
    fun `energy types are written in title case`() {
        val card = basicPokemon("Squirtle", energyType = EnergyType.WATER)

        val listing =
            CollectionListing.build(
                CardCatalog(listOf(card)),
                CardCollection.EMPTY,
                CollectionFilter(),
                Language.ENGLISH,
            )

        listing.items.single().detail shouldBe CardDetail.OfPokemon(EnergyType.WATER, Stage.BASIC, 60)
    }

    private val glurak =
        PokemonCard(
            id =
                dev.pocketdeck.domain.card
                    .CardId("a1-036"),
            name = CardName("Charizard ex"),
            energyType = EnergyType.FIRE,
            stage = Stage.STAGE_2,
            hp = 180,
            evolvesFrom = CardName("Charmeleon"),
            germanName = CardName("Glurak ex"),
        )
    private val bilingual = CardCatalog(listOf(glurak))

    @Test
    fun `shows names in the requested language`() {
        bilingualListing("", Language.ENGLISH).items.single().name shouldBe "Charizard ex"
        bilingualListing("", Language.GERMAN).items.single().name shouldBe "Glurak ex"
    }

    private fun bilingualListing(
        query: String,
        language: Language,
    ) = CollectionListing.build(bilingual, CardCollection.EMPTY, CollectionFilter(query), language)

    @Test
    fun `search finds a card by either of its names regardless of the display language`() {
        bilingualListing("glurak", Language.ENGLISH).items.size shouldBe 1
        bilingualListing("charizard", Language.GERMAN).items.size shouldBe 1
        bilingualListing("bisasam", Language.GERMAN).items.size shouldBe 0
    }
}
