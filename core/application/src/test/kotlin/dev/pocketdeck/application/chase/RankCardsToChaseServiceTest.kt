package dev.pocketdeck.application.chase

import app.cash.turbine.test
import dev.pocketdeck.application.fakes.InMemoryCardCatalogRepository
import dev.pocketdeck.application.fakes.InMemoryCollectionRepository
import dev.pocketdeck.application.meta.InMemoryMetaSnapshotRepository
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.meta.ArchetypeAggregator
import dev.pocketdeck.domain.meta.ArchetypeName
import dev.pocketdeck.domain.meta.DecklistEntry
import dev.pocketdeck.domain.meta.MetaDecklist
import dev.pocketdeck.domain.meta.TournamentResult
import dev.pocketdeck.domain.sabrina
import dev.pocketdeck.domain.validFireDeck
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext

class RankCardsToChaseServiceTest {
    private val deck = validFireDeck()
    private val cards = deck.entries.map { it.card }
    private val catalogs = InMemoryCardCatalogRepository(CardCatalog(cards))
    private val collections = InMemoryCollectionRepository(ownedExcept(sabrina.id.value))
    private val snapshots = InMemoryMetaSnapshotRepository()
    private val service = RankCardsToChaseService(catalogs, collections, snapshots, context = EmptyCoroutineContext)

    private fun ownedExcept(id: String) =
        cards.filter { it.id.value != id }.fold(CardCollection.EMPTY) { owned, card -> owned.withCopies(card.id, 2) }

    private val field =
        ArchetypeAggregator().aggregate(
            listOf(
                MetaDecklist(
                    ArchetypeName("Fire"),
                    deck.entries.map { DecklistEntry(it.card.id, it.copies) },
                    deck.energyTypes,
                    TournamentResult(placing = 1, wins = 3, losses = 1, ties = 0),
                ),
            ),
        )

    @Test
    fun `has nothing to chase before the tournament decks are loaded`() =
        runTest {
            service().test {
                awaitItem().cards shouldBe emptyList()
            }
        }

    @Test
    fun `names the card that would finish a tournament deck`() =
        runTest {
            snapshots.save(field)

            service().test {
                awaitItem().cards.single().card shouldBe sabrina
            }
        }

    @Test
    fun `ranks again when the collection changes`() =
        runTest {
            snapshots.save(field)

            service().test {
                awaitItem().cards.size shouldBe 1
                collections.save(ownedExcept("none"))
                awaitItem().cards shouldBe emptyList()
            }
        }

    @Test
    fun `ranks again when the field changes`() =
        runTest {
            service().test {
                awaitItem().cards shouldBe emptyList()
                snapshots.save(field)
                awaitItem().cards.size shouldBe 1
            }
        }
}
