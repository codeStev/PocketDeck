package dev.pocketdeck.domain.card

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CardIdentifiersTest {
    @Test
    fun `card id keeps its value`() {
        CardId("A1-001").value shouldBe "A1-001"
    }

    @Test
    fun `identifiers reject blank values`() {
        shouldThrow<IllegalArgumentException> { CardId(" ") }
        shouldThrow<IllegalArgumentException> { CardName("") }
        shouldThrow<IllegalArgumentException> { GameplayKey("  ") }
        shouldThrow<IllegalArgumentException> { ArtworkUrl(" ") }
    }

    @Test
    fun `gameplay key defaults to the card name`() {
        GameplayKey.of(CardName("Pikachu")) shouldBe GameplayKey("Pikachu")
    }
}
