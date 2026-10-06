package dev.pocketdeck.application.suggest

import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.charizardEx
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.charmeleon
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.giovanni
import dev.pocketdeck.domain.magmar
import dev.pocketdeck.domain.meta.Archetype
import dev.pocketdeck.domain.meta.ArchetypeName
import dev.pocketdeck.domain.meta.DecklistEntry
import dev.pocketdeck.domain.meta.MetaDecklist
import dev.pocketdeck.domain.meta.MetaSnapshot
import dev.pocketdeck.domain.meta.TournamentResult
import dev.pocketdeck.domain.pokeBall
import dev.pocketdeck.domain.potion
import dev.pocketdeck.domain.professorsResearch
import dev.pocketdeck.domain.rareCandy
import dev.pocketdeck.domain.sabrina
import dev.pocketdeck.domain.trainer
import dev.pocketdeck.domain.validFireDeck
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MetaBasedSuggestionStrategyTest {
    private val staples = listOf(pokeBall, potion, rareCandy, professorsResearch, sabrina, giovanni)
    private val xSpeed = trainer("X Speed")
    private val allCards = listOf(charmander, charmeleon, charizardEx, magmar, xSpeed) + staples
    private val catalog = CardCatalog(allCards)

    private val fireList = validFireDeck().entries.map { DecklistEntry(it.card.id, it.copies) }

    private fun list(
        entries: List<DecklistEntry> = fireList,
        placing: Int = 1,
        energy: Set<EnergyType> = setOf(EnergyType.FIRE),
        name: String = "Charizard",
    ) = MetaDecklist(ArchetypeName(name), entries, energy, TournamentResult(placing, wins = 5, losses = 2, ties = 0))

    private fun snapshot(vararg archetypes: Archetype) = MetaSnapshot(archetypes.toList())

    private fun archetype(
        name: String,
        vararg lists: MetaDecklist,
        share: Double = 0.2,
    ) = Archetype(ArchetypeName(name), lists.toList(), share)

    private fun ownedEverything() =
        Inventory.of(
            allCards.fold(CardCollection.EMPTY) { collection, card -> collection.withCopies(card.id, 2) },
            catalog,
        )

    private fun context(
        meta: MetaSnapshot,
        inventory: Inventory = ownedEverything(),
    ) = SuggestionContext(catalog, inventory, meta)

    @Test
    fun `suggests an archetype's list that the player owns completely`() {
        val result =
            MetaBasedSuggestionStrategy().suggest(context(snapshot(archetype("Charizard", list(name = "Charizard")))))

        result.single().completeness.isBuildable shouldBe true
        result.single().title shouldBe "Charizard"
    }

    @Test
    fun `names the cards the player is missing`() {
        val inventory = Inventory.of(CardCollection.EMPTY.withCopies(charmander.id, 2), catalog)

        val result = MetaBasedSuggestionStrategy().suggest(context(snapshot(archetype("Charizard", list())), inventory))

        result.single().completeness.isBuildable shouldBe false
        result
            .single()
            .completeness.missing
            .map { it.card.name.value }
            .contains("Charizard ex") shouldBe true
    }

    @Test
    fun `picks the variant that the player is closest to owning, not the best placed`() {
        val withXSpeed = fireList.filterNot { it.cardId == rareCandy.id } + DecklistEntry(xSpeed.id, 2)
        val ownedCards = allCards.filterNot { it == rareCandy }
        val ownsAllButRareCandy =
            Inventory.of(ownedCards.fold(CardCollection.EMPTY) { c, card -> c.withCopies(card.id, 2) }, catalog)

        val result =
            MetaBasedSuggestionStrategy().suggest(
                context(
                    snapshot(archetype("Charizard", list(placing = 1), list(withXSpeed, placing = 2))),
                    ownsAllButRareCandy,
                ),
            )

        result.single().completeness.isBuildable shouldBe true
        result
            .single()
            .deck.entries
            .any { it.card == xSpeed } shouldBe true
    }

    @Test
    fun `leaves out lists with a card the catalog does not know`() {
        val unknown = list(fireList + DecklistEntry(CardId("zz-999"), 1))

        MetaBasedSuggestionStrategy().suggest(context(snapshot(archetype("Charizard", unknown)))) shouldBe emptyList()
    }

    @Test
    fun `leaves out lists that are not a legal deck`() {
        val tooSmall = list(fireList.take(3))

        MetaBasedSuggestionStrategy().suggest(context(snapshot(archetype("Charizard", tooSmall)))) shouldBe emptyList()
    }

    @Test
    fun `works out the energy zone when the list does not name one`() {
        val result =
            MetaBasedSuggestionStrategy().suggest(
                context(snapshot(archetype("Charizard", list(energy = emptySet())))),
            )

        result.single().deck.energyTypes shouldBe setOf(EnergyType.FIRE)
    }

    @Test
    fun `a deck the player owns scores higher than the same deck they do not`() {
        val owned = MetaBasedSuggestionStrategy().suggest(context(snapshot(archetype("Charizard", list())))).single()
        val empty =
            MetaBasedSuggestionStrategy()
                .suggest(context(snapshot(archetype("Charizard", list())), Inventory.of(CardCollection.EMPTY, catalog)))
                .single()

        (owned.score > empty.score) shouldBe true
    }
}
