package dev.pocketdeck.application.meta

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.meta.ArchetypeName
import dev.pocketdeck.domain.meta.DecklistEntry
import dev.pocketdeck.domain.meta.MetaDecklist
import dev.pocketdeck.domain.meta.MetaSnapshot
import dev.pocketdeck.domain.meta.TournamentResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RefreshMetaSnapshotServiceTest {
    private val list =
        MetaDecklist(
            ArchetypeName("Mewtwo"),
            listOf(DecklistEntry(CardId("a1-001"), 2)),
            setOf(EnergyType.PSYCHIC),
            TournamentResult(1, 3, 0, 0),
        )

    @Test
    fun `aggregates the fetched decklists and keeps them`() =
        runTest {
            val snapshots = InMemoryMetaSnapshotRepository()
            val service = RefreshMetaSnapshotService({ Outcome.Success(listOf(list, list)) }, snapshots)

            val outcome = service()

            (outcome as Outcome.Success)
                .value.archetypes
                .single()
                .decklists.size shouldBe 2
            snapshots.observe().first() shouldBe outcome.value
        }

    @Test
    fun `keeps the old snapshot when the source cannot be reached`() =
        runTest {
            val snapshots = InMemoryMetaSnapshotRepository()
            RefreshMetaSnapshotService({ Outcome.Success(listOf(list)) }, snapshots)()
            val failing =
                RefreshMetaSnapshotService({ Outcome.Failure(MetaSourceFailure.NotReachable("offline")) }, snapshots)

            failing() shouldBe Outcome.Failure(MetaSourceFailure.NotReachable("offline"))
            snapshots
                .observe()
                .first()
                .archetypes.size shouldBe 1
        }

    @Test
    fun `starts without any field`() =
        runTest {
            InMemoryMetaSnapshotRepository().observe().first() shouldBe MetaSnapshot.EMPTY
        }
}
