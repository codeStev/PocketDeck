package dev.pocketdeck.domain.collection

import dev.pocketdeck.domain.basicPokemon
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.GameplayKey
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InventoryTest {
    private val commonPikachu = basicPokemon("Pikachu", id = "A1-094")
    private val fullArtPikachu = basicPokemon("Pikachu", id = "A1-250")
    private val otherPikachu =
        basicPokemon("Pikachu", id = "A2-001", gameplayKey = GameplayKey("Pikachu/other-attacks"))
    private val catalog = CardCatalog(listOf(commonPikachu, fullArtPikachu, otherPikachu))

    @Test
    fun `prints with the same gameplay key are interchangeable`() {
        val collection =
            CardCollection.EMPTY
                .withCopies(commonPikachu.id, 1)
                .withCopies(fullArtPikachu.id, 1)

        val inventory = Inventory.of(collection, catalog)

        inventory.copiesOf(commonPikachu) shouldBe 2
        inventory.copiesOf(fullArtPikachu) shouldBe 2
    }

    @Test
    fun `cards with different gameplay keys are not interchangeable`() {
        val inventory = Inventory.of(CardCollection.EMPTY.withCopies(commonPikachu.id, 2), catalog)

        inventory.copiesOf(otherPikachu) shouldBe 0
    }

    @Test
    fun `cards unknown to the catalog are ignored`() {
        val collection = CardCollection.EMPTY.withCopies(basicPokemon("Ghost", id = "gone").id, 3)

        Inventory.of(collection, catalog).copiesOf(commonPikachu) shouldBe 0
    }

    @Test
    fun `unowned cards have zero copies`() {
        Inventory.of(CardCollection.EMPTY, catalog).copiesOf(commonPikachu) shouldBe 0
        GameplayKey.of(CardName("Pikachu")) shouldBe commonPikachu.gameplayKey
    }
}
