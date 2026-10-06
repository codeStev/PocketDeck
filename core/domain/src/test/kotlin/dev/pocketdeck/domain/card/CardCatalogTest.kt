package dev.pocketdeck.domain.card

import dev.pocketdeck.domain.basicPokemon
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CardCatalogTest {
    private val bulbasaur = basicPokemon("Bulbasaur", id = "A1-001")
    private val ivysaur = basicPokemon("Ivysaur", id = "A1-002")

    @Test
    fun `finds cards by id`() {
        val catalog = CardCatalog(listOf(bulbasaur, ivysaur))

        catalog[CardId("A1-002")] shouldBe ivysaur
        catalog[CardId("nope")] shouldBe null
        (CardId("A1-001") in catalog) shouldBe true
    }

    @Test
    fun `rejects duplicate ids`() {
        shouldThrow<IllegalArgumentException> { CardCatalog(listOf(bulbasaur, bulbasaur)) }
    }

    @Test
    fun `reports its size`() {
        CardCatalog(listOf(bulbasaur, ivysaur)).size shouldBe 2
        CardCatalog.EMPTY.size shouldBe 0
    }
}
