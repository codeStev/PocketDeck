package dev.pocketdeck.application.suggest

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.application.meta.InMemoryMetaSnapshotRepository
import dev.pocketdeck.domain.builder.DeckFocus
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
import dev.pocketdeck.domain.deck.SuggestionOrigin
import dev.pocketdeck.domain.meta.ArchetypeAggregator
import dev.pocketdeck.domain.meta.ArchetypeName
import dev.pocketdeck.domain.meta.DecklistEntry
import dev.pocketdeck.domain.meta.MetaDecklist
import dev.pocketdeck.domain.meta.MetaSnapshot
import dev.pocketdeck.domain.meta.TournamentResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class HeuristicDeckSuggestionsTest {
    private fun pokemon(
        name: String,
        type: EnergyType,
        hp: Int = 70,
    ) = PokemonCard(
        id = CardId("p-$name"),
        name = CardName(name),
        energyType = type,
        stage = Stage.BASIC,
        hp = hp,
        gameplayKey = GameplayKey("$name#p"),
        attacks = listOf(Attack("Hit", "${type.symbol}C", 50, null)),
    )

    private fun trainer(
        name: String,
        text: String,
    ) = TrainerCard(CardId("t-$name"), CardName(name), TrainerKind.ITEM, GameplayKey("$name#t"), effect = text)

    private val fire = (1..8).map { pokemon("Flame $it", EnergyType.FIRE) }
    private val water = (1..8).map { pokemon("Wave $it", EnergyType.WATER, hp = 90) }
    private val trainers =
        listOf(
            trainer("Ball", "Draw 2 cards."),
            trainer("Potion", "Heal 20 damage."),
            trainer("Swap", "Switch your Active Pokémon."),
        )
    private val everything: List<Card> = fire + water + trainers

    private val catalog = CardCatalog(everything)
    private val inventory =
        Inventory.of(everything.fold(CardCollection.EMPTY) { c, card -> c.withCopies(card.id, 2) }, catalog)
    private val context = SuggestionContext(catalog, inventory, MetaSnapshot.EMPTY)

    @Test
    fun `suggests decks without any tournament data`() {
        val suggestions = HeuristicSuggestionStrategy().suggest(context)

        suggestions.isNotEmpty() shouldBe true
        suggestions.all { it.origin == SuggestionOrigin.HEURISTIC } shouldBe true
        suggestions.all { it.completeness.isBuildable } shouldBe true
    }

    @Test
    fun `offers decks of different kinds`() {
        val energy = HeuristicSuggestionStrategy().suggest(context).map { it.deck.energyTypes }

        (energy.distinct().size > 1) shouldBe true
    }

    @Test
    fun `every suggestion knows what it is built around`() {
        HeuristicSuggestionStrategy().suggest(context).all { it.centrepiece != null } shouldBe true
    }

    @Test
    fun `suggests nothing when the collection cannot make a deck`() {
        val tiny = Inventory.of(CardCollection.EMPTY.withCopies(fire.first().id, 2), catalog)

        HeuristicSuggestionStrategy().suggest(SuggestionContext(catalog, tiny, MetaSnapshot.EMPTY)) shouldBe emptyList()
    }

    @Test
    fun `builds a deck around the pokemon the player picked`() =
        runTest {
            val service =
                BuildDeckService(
                    InMemoryCardCatalogRepository(catalog),
                    InMemoryCollectionRepository(collection()),
                    InMemoryMetaSnapshotRepository(),
                )

            val outcome = service(DeckFocus(core = water[2]))

            (outcome as Outcome.Success)
                .value.deck.entries
                .any { it.card == water[2] } shouldBe true
            outcome.value.centrepiece shouldBe water[2]
        }

    @Test
    fun `says so when the collection is too small to build a deck`() =
        runTest {
            val service =
                BuildDeckService(
                    InMemoryCardCatalogRepository(catalog),
                    InMemoryCollectionRepository(),
                    InMemoryMetaSnapshotRepository(),
                )

            service(DeckFocus()) shouldBe Outcome.Failure(BuildDeckFailure.NotEnoughCards)
        }

    @Test
    fun `lists the pokemon a deck can be built around, strongest first`() =
        runTest {
            val service =
                ListDeckCentrepiecesService(
                    InMemoryCardCatalogRepository(catalog),
                    InMemoryCollectionRepository(collection()),
                    InMemoryMetaSnapshotRepository(),
                )

            service()
                .first()
                .name.value
                .startsWith("Wave") shouldBe true
        }

    private fun collection() = everything.fold(CardCollection.EMPTY) { c, card -> c.withCopies(card.id, 2) }

    @Test
    fun `follows the tournament decks when there are some`() {
        val partner = trainer("Partner", "Move along.")
        val withPartner = CardCatalog(everything + partner)
        val owned =
            (everything + partner).fold(CardCollection.EMPTY) { c, card -> c.withCopies(card.id, 2) }
        val decks =
            List(10) {
                MetaDecklist(
                    ArchetypeName("A"),
                    listOf(DecklistEntry(partner.id, 2), DecklistEntry(fire[0].id, 2)),
                    setOf(EnergyType.FIRE),
                    TournamentResult(1, 3, 1, 0),
                )
            }
        val meta = ArchetypeAggregator().aggregate(decks)

        val suggestion =
            HeuristicSuggestionStrategy()
                .suggest(SuggestionContext(withPartner, Inventory.of(owned, withPartner), meta))
                .first { it.deck.energyTypes == setOf(EnergyType.FIRE) }

        suggestion.deck.entries.any { it.card == partner && it.copies == 2 } shouldBe true
    }
}
