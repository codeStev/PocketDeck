package dev.pocketdeck.adapter.ui.decks

import dev.pocketdeck.domain.deck.SuggestionOrigin
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DeckKindTest {
    @Test
    fun `tournament decks are their own kind`() {
        DeckKind.of(SuggestionOrigin.META) shouldBe DeckKind.TOURNAMENT
    }

    @Test
    fun `decks the builder or an AI made are generated`() {
        DeckKind.of(SuggestionOrigin.HEURISTIC) shouldBe DeckKind.GENERATED
        DeckKind.of(SuggestionOrigin.AI) shouldBe DeckKind.GENERATED
    }

    @Test
    fun `a notice belongs to the tab whose action caused it`() {
        val tournament = listOf(DecksNotice.NOT_REACHABLE, DecksNotice.RATE_LIMITED, DecksNotice.NO_DECKLISTS)
        tournament.forEach { DeckKind.of(it) shouldBe DeckKind.TOURNAMENT }
        DecksNotice.entries
            .filterNot { it in tournament }
            .forEach { DeckKind.of(it) shouldBe DeckKind.GENERATED }
    }

    @Test
    fun `lists only the decks of a kind and keeps their order`() {
        val items =
            listOf(
                item("a", SuggestionOrigin.AI),
                item("b", SuggestionOrigin.META),
                item("c", SuggestionOrigin.HEURISTIC),
                item("d", SuggestionOrigin.META),
            )

        items.ofKind(DeckKind.TOURNAMENT).map { it.title } shouldBe listOf("b", "d")
        items.ofKind(DeckKind.GENERATED).map { it.title } shouldBe listOf("a", "c")
    }

    private fun item(
        title: String,
        origin: SuggestionOrigin,
    ) = DeckItem(title, title, origin, 100, 0, 20, emptyList())
}
