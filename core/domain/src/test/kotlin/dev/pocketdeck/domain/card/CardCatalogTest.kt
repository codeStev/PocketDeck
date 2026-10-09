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

    @Test
    fun `a catalog stored before packs existed has to be loaded again`() {
        val withoutPacks = basicPokemon("Bulbasaur", id = "A1-001", packs = emptyList())

        CardCatalog(listOf(withoutPacks)).lacksCardDetails shouldBe true
        CardCatalog(listOf(bulbasaur)).lacksCardDetails shouldBe false
        CardCatalog.EMPTY.lacksCardDetails shouldBe false
    }

    @Test
    fun `one card without a pack is fine as long as others have one`() {
        val promo = basicPokemon("Pikachu", id = "P-A-001", packs = emptyList())

        CardCatalog(listOf(bulbasaur, promo)).lacksCardDetails shouldBe false
    }

    @Test
    fun `a catalog stored before rarities existed has to be loaded again`() {
        val withoutRarity = basicPokemon("Bulbasaur", id = "A1-001").copy(rarity = null)

        CardCatalog(listOf(withoutRarity)).lacksCardDetails shouldBe true
    }

    @Test
    fun `rarities are read from the dataset codes and ordered from common to crown`() {
        Rarity.ofCode("RR") shouldBe Rarity.DOUBLE_RARE
        Rarity.ofCode("nonsense") shouldBe null
        (Rarity.COMMON < Rarity.CROWN) shouldBe true
    }

    @Test
    fun `a catalog stored before the English pictures existed has to be loaded again`() {
        val withoutEnglish = basicPokemon("Bulbasaur", id = "A1-001").copy(englishArtwork = null)

        CardCatalog(listOf(withoutEnglish)).lacksCardDetails shouldBe true
    }

    @Test
    fun `a card shows the picture of the language, and the German one when there is no English print`() {
        val both =
            basicPokemon("Bulbasaur", id = "A1-001")
                .copy(
                    artwork = ArtworkUrl("https://x.test/de.webp"),
                    englishArtwork = ArtworkUrl("https://x.test/en.webp"),
                )
        val germanOnly = both.copy(englishArtwork = null)

        both.artworkIn(Language.GERMAN)?.value shouldBe "https://x.test/de.webp"
        both.artworkIn(Language.ENGLISH)?.value shouldBe "https://x.test/en.webp"
        germanOnly.artworkIn(Language.ENGLISH)?.value shouldBe "https://x.test/de.webp"
    }
}
