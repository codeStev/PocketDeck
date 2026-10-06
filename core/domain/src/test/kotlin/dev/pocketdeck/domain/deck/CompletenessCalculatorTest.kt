package dev.pocketdeck.domain.deck

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.charizardEx
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.charmeleon
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deckOf
import dev.pocketdeck.domain.magmar
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CompletenessCalculatorTest {
    private val calculator = CompletenessCalculator()
    private val deck = deckOf(charmander to 2, charmeleon to 2, charizardEx to 2, magmar to 2)
    private val catalog = CardCatalog(listOf(charmander, charmeleon, charizardEx, magmar))

    private fun inventoryOf(vararg owned: Pair<Card, Int>): Inventory {
        val collection = owned.fold(CardCollection.EMPTY) { acc, (card, copies) -> acc.withCopies(card.id, copies) }
        return Inventory.of(collection, catalog)
    }

    @Test
    fun `a fully owned deck is buildable`() {
        val inventory = inventoryOf(charmander to 2, charmeleon to 2, charizardEx to 2, magmar to 3)

        val completeness = calculator.calculate(deck, inventory)

        completeness.isBuildable shouldBe true
        completeness.ratio shouldBe 1.0
        completeness.missing shouldContainExactly emptyList()
    }

    @Test
    fun `missing copies are reported per card`() {
        val inventory = inventoryOf(charmander to 2, charmeleon to 1, magmar to 2)

        val completeness = calculator.calculate(deck, inventory)

        completeness.missing shouldContainExactly
            listOf(MissingCard(charmeleon, missingCopies = 1), MissingCard(charizardEx, missingCopies = 2))
        completeness.missingCopies shouldBe 3
        completeness.isBuildable shouldBe false
        completeness.ratio shouldBe (5.0 / 8.0).plusOrMinus(1e-9)
    }

    @Test
    fun `an empty inventory owns nothing of the deck`() {
        calculator.calculate(deck, Inventory.of(CardCollection.EMPTY, catalog)).ratio shouldBe 0.0
    }
}
