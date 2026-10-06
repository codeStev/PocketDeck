package dev.pocketdeck.adapter.ui.scan

import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.charmander
import dev.pocketdeck.domain.magmar
import dev.pocketdeck.domain.pokeBall
import dev.pocketdeck.domain.scan.Confidence
import dev.pocketdeck.domain.scan.ScannedCard
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReviewListingTest {
    private val german = charmander.copy(germanName = CardName("Glumanda"))
    private val catalog = CardCatalog(listOf(german, magmar, pokeBall))

    private fun scanned(
        id: CardId,
        copies: Int = 1,
        confidence: Double = 0.95,
        needsReview: Boolean = false,
    ) = ScannedCard(id, copies, Confidence(confidence), needsReview)

    @Test
    fun `cards that need a look come first, then the rest in catalog order`() {
        val items =
            ReviewListing.build(
                listOf(scanned(magmar.id), scanned(pokeBall.id, needsReview = true), scanned(german.id)),
                catalog,
                Language.ENGLISH,
            )

        items.map { it.name } shouldBe listOf("Poké Ball", "Charmander", "Magmar")
    }

    @Test
    fun `shows names in the requested language`() {
        val items = ReviewListing.build(listOf(scanned(german.id)), catalog, Language.GERMAN)

        items.single().name shouldBe "Glumanda"
    }

    @Test
    fun `carries the count, confidence and artwork`() {
        val item =
            ReviewListing
                .build(
                    listOf(scanned(magmar.id, copies = 3, confidence = 0.6)),
                    catalog,
                    Language.ENGLISH,
                ).single()

        item.copies shouldBe 3
        item.confidence shouldBe 0.6
        item.artworkUrl shouldBe null
    }

    @Test
    fun `cards unknown to the catalog are left out`() {
        val items = ReviewListing.build(listOf(scanned(CardId("ghost")), scanned(magmar.id)), catalog, Language.ENGLISH)

        items.map { it.cardId } shouldBe listOf(magmar.id)
    }
}
