package dev.pocketdeck.domain.scan

import dev.pocketdeck.domain.card.CardId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ScanSessionTest {
    private val pikachu = CardId("A1-094")
    private val mew = CardId("A1-151")

    private fun seen(
        id: CardId,
        copies: Int,
        confidence: Double,
    ) = TileRecognition(id, copies, Confidence(confidence))

    @Test
    fun `a fresh session has seen nothing`() {
        val session = ScanSession.start()

        session.framesProcessed shouldBe 0
        session.distinctCards shouldBe 0
        session.summarize().shouldBeEmpty()
    }

    @Test
    fun `observing counts frames and distinct cards`() {
        val session =
            ScanSession
                .start()
                .observe(listOf(seen(pikachu, 1, 0.9), seen(mew, 2, 0.9)))
                .observe(listOf(seen(pikachu, 1, 0.9)))

        session.framesProcessed shouldBe 2
        session.distinctCards shouldBe 2
    }

    @Test
    fun `agreeing observations are accepted without review`() {
        val session =
            ScanSession
                .start()
                .observe(listOf(seen(pikachu, 2, 0.9)))
                .observe(listOf(seen(pikachu, 2, 0.95)))

        session.summarize() shouldBe
            listOf(ScannedCard(pikachu, copies = 2, confidence = Confidence(0.95), needsReview = false))
    }

    @Test
    fun `the copy count with the most confident support wins and is flagged for review`() {
        val session =
            ScanSession
                .start()
                .observe(listOf(seen(pikachu, 2, 0.9)))
                .observe(listOf(seen(pikachu, 2, 0.9)))
                .observe(listOf(seen(pikachu, 1, 0.9)))

        val result = session.summarize().single()

        result.copies shouldBe 2
        result.needsReview shouldBe true
    }

    @Test
    fun `a single low confidence observation needs review`() {
        val result =
            ScanSession
                .start()
                .observe(listOf(seen(mew, 1, 0.5)))
                .summarize()
                .single()

        result.needsReview shouldBe true
        result.confidence shouldBe Confidence(0.5)
    }

    @Test
    fun `ties are resolved towards the larger count`() {
        val session =
            ScanSession
                .start()
                .observe(listOf(seen(pikachu, 1, 0.9)))
                .observe(listOf(seen(pikachu, 2, 0.9)))

        session.summarize().single().copies shouldBe 2
    }

    @Test
    fun `results are sorted by card id`() {
        val session = ScanSession.start().observe(listOf(seen(mew, 1, 0.9), seen(pikachu, 1, 0.9)))

        session.summarize().map { it.cardId } shouldBe listOf(pikachu, mew)
    }

    @Test
    fun `observing returns a new session and leaves the original untouched`() {
        val original = ScanSession.start()

        original.observe(listOf(seen(pikachu, 1, 0.9)))

        original.distinctCards shouldBe 0
    }

    @Test
    fun `confidence and copies are validated`() {
        shouldThrow<IllegalArgumentException> { Confidence(1.1) }
        shouldThrow<IllegalArgumentException> { Confidence(-0.1) }
        shouldThrow<IllegalArgumentException> { seen(pikachu, 0, 0.9) }
    }

    private fun unreadable(
        id: CardId,
        confidence: Double,
    ) = TileRecognition(id, copies = null, confidence = Confidence(confidence))

    @Test
    fun `a tile without a visible count still counts as a seen card`() {
        val session = ScanSession.start().observe(listOf(unreadable(pikachu, 0.95)))

        session.distinctCards shouldBe 1
    }

    @Test
    fun `a card whose count was never readable is owned once and needs review`() {
        val result =
            ScanSession
                .start()
                .observe(listOf(unreadable(pikachu, 0.95)))
                .summarize()
                .single()

        result.copies shouldBe 1
        result.needsReview shouldBe true
        result.confidence shouldBe Confidence(0.95)
    }

    @Test
    fun `an unreadable observation does not disagree with a readable one`() {
        val session =
            ScanSession
                .start()
                .observe(listOf(unreadable(pikachu, 0.95)))
                .observe(listOf(seen(pikachu, 3, 0.9)))

        val result = session.summarize().single()

        result.copies shouldBe 3
        result.needsReview shouldBe false
    }

    @Test
    fun `a zero or negative count is rejected but an unknown count is not`() {
        shouldThrow<IllegalArgumentException> { TileRecognition(pikachu, copies = 0, confidence = Confidence(0.9)) }
        TileRecognition(pikachu, copies = null, confidence = Confidence(0.9)).copies shouldBe null
    }

    @Test
    fun `an unreadable count is reported as the reason`() {
        val result =
            ScanSession
                .start()
                .observe(listOf(unreadable(pikachu, 0.95)))
                .summarize()
                .single()

        result.reason shouldBe ReviewReason.COUNT_NEVER_READ
    }

    @Test
    fun `disagreeing counts are reported as the reason`() {
        val session =
            ScanSession
                .start()
                .observe(listOf(seen(pikachu, 1, 0.9)))
                .observe(listOf(seen(pikachu, 2, 0.9)))

        session.summarize().single().reason shouldBe ReviewReason.COUNT_DISAGREES
    }

    @Test
    fun `a doubtful identification is reported as the reason`() {
        val result =
            ScanSession
                .start()
                .observe(listOf(seen(mew, 1, 0.5)))
                .summarize()
                .single()

        result.reason shouldBe ReviewReason.LOW_CONFIDENCE
    }

    @Test
    fun `a card that needs no review has no reason`() {
        val result =
            ScanSession
                .start()
                .observe(listOf(seen(mew, 2, 0.95)))
                .summarize()
                .single()

        result.reason shouldBe null
        result.needsReview shouldBe false
    }

    @Test
    fun `the breakdown counts the cards per reason`() {
        val session =
            ScanSession.start().observe(
                listOf(unreadable(pikachu, 0.95), seen(mew, 1, 0.5), seen(CardId("a1-001"), 1, 0.95)),
            )

        session.breakdown() shouldBe ReviewBreakdown(neverCounted = 1, disagreed = 0, lowConfidence = 1)
        session.breakdown().total shouldBe 2
    }

    @Test
    fun `a view that combines several prints beats views that saw only one of them`() {
        val session =
            ScanSession
                .start()
                .observe(listOf(TileRecognition(pikachu, copies = 3, confidence = Confidence(0.95))))
                .observe(listOf(TileRecognition(pikachu, copies = 5, confidence = Confidence(0.95), parts = 2)))
                .observe(listOf(TileRecognition(pikachu, copies = 3, confidence = Confidence(0.95))))

        val result = session.summarize().single()

        result.copies shouldBe 5
        result.needsReview shouldBe false
    }

    @Test
    fun `equally complete views that disagree are still flagged`() {
        val session =
            ScanSession
                .start()
                .observe(listOf(TileRecognition(pikachu, copies = 5, confidence = Confidence(0.95), parts = 2)))
                .observe(listOf(TileRecognition(pikachu, copies = 4, confidence = Confidence(0.95), parts = 2)))

        session.summarize().single().reason shouldBe ReviewReason.COUNT_DISAGREES
    }

    @Test
    fun `a combined view needs at least one tile`() {
        shouldThrow<IllegalArgumentException> {
            TileRecognition(
                pikachu,
                copies = 2,
                confidence = Confidence(0.9),
                parts = 0,
            )
        }
    }
}
