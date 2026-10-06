package dev.pocketdeck.domain.meta

import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.EnergyType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ArchetypeAggregatorTest {
    private fun list(
        archetype: String,
        placing: Int = 1,
        wins: Int = 3,
        losses: Int = 1,
    ) = MetaDecklist(
        archetype = ArchetypeName(archetype),
        entries = listOf(DecklistEntry(CardId("a1-001"), 2)),
        energyTypes = setOf(EnergyType.GRASS),
        result = TournamentResult(placing, wins, losses, ties = 0),
    )

    @Test
    fun `groups decklists by archetype and computes the share of the field`() {
        val snapshot =
            ArchetypeAggregator().aggregate(
                listOf(list("Mewtwo"), list("Mewtwo"), list("Mewtwo"), list("Pikachu")),
            )

        snapshot.archetypes.map { it.name.value } shouldBe listOf("Mewtwo", "Pikachu")
        snapshot.archetypes.first().share shouldBe (0.75 plusOrMinus 1e-9)
        snapshot.archetypes
            .first()
            .decklists.size shouldBe 3
    }

    @Test
    fun `orders the decklists of an archetype by placing`() {
        val snapshot = ArchetypeAggregator().aggregate(listOf(list("Mewtwo", placing = 5), list("Mewtwo", placing = 1)))

        snapshot.archetypes
            .single()
            .decklists
            .map { it.result.placing } shouldBe listOf(1, 5)
    }

    @Test
    fun `a small sample does not make a win rate look perfect`() {
        val lucky = Archetype(ArchetypeName("Lucky"), listOf(list("Lucky", wins = 2, losses = 0)), share = 0.01)
        val proven = Archetype(ArchetypeName("Proven"), List(20) { list("Proven", wins = 15, losses = 5) }, share = 0.2)

        (proven.winRate > lucky.winRate) shouldBe true
    }

    @Test
    fun `an empty field gives an empty snapshot`() {
        ArchetypeAggregator().aggregate(emptyList()).archetypes shouldBe emptyList()
    }

    @Test
    fun `a decklist entry needs at least one copy`() {
        shouldThrow<IllegalArgumentException> { DecklistEntry(CardId("a1-001"), 0) }
    }

    @Test
    fun `the win rate of a result ignores ties as half a win`() {
        TournamentResult(placing = 1, wins = 2, losses = 1, ties = 2).winRate shouldBe (0.6 plusOrMinus 1e-9)
    }
}
