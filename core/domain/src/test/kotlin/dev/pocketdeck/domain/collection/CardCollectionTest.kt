package dev.pocketdeck.domain.collection

import dev.pocketdeck.domain.card.CardId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CardCollectionTest {
    private val pikachu = CardId("A1-094")

    @Test
    fun `an empty collection owns nothing`() {
        CardCollection.EMPTY.copiesOf(pikachu) shouldBe 0
    }

    @Test
    fun `adjusting adds and removes copies`() {
        val collection = CardCollection.EMPTY.adjusted(pikachu, 2).adjusted(pikachu, -1)

        collection.copiesOf(pikachu) shouldBe 1
    }

    @Test
    fun `copies never drop below zero`() {
        CardCollection.EMPTY.adjusted(pikachu, -5).copiesOf(pikachu) shouldBe 0
    }

    @Test
    fun `copies are capped at the maximum`() {
        CardCollection.EMPTY.adjusted(pikachu, 1000).copiesOf(pikachu) shouldBe CardCollection.MAX_COPIES_PER_CARD
    }

    @Test
    fun `setting zero copies removes the card from the collection`() {
        val collection = CardCollection.EMPTY.withCopies(pikachu, 3).withCopies(pikachu, 0)

        collection shouldBe CardCollection.EMPTY
    }

    @Test
    fun `setting negative copies is rejected`() {
        shouldThrow<IllegalArgumentException> { CardCollection.EMPTY.withCopies(pikachu, -1) }
    }

    @Test
    fun `a collection is not modified by adjusting`() {
        val original = CardCollection.EMPTY.withCopies(pikachu, 1)

        original.adjusted(pikachu, 1)

        original.copiesOf(pikachu) shouldBe 1
    }
}
