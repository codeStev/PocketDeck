package dev.pocketdeck.adapter.capture

import dev.pocketdeck.application.scan.ScanProgress
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ProgressTextTest {
    @Test
    fun `shows the cards found so far`() {
        ProgressText.of(ScanProgress(framesProcessed = 12, distinctCards = 142, cardsNeedingReview = 0)) shouldBe
            "142 cards"
    }

    @Test
    fun `mentions how many cards need a look`() {
        ProgressText.of(ScanProgress(12, 142, 7)) shouldBe "142 cards · 7 to check"
    }

    @Test
    fun `a single card is not pluralised`() {
        ProgressText.of(ScanProgress(1, 1, 0)) shouldBe "1 card"
    }

    @Test
    fun `before the first card is recognised it asks the player to scroll`() {
        ProgressText.of(ScanProgress(0, 0, 0)) shouldBe "Scroll through My Cards"
        ProgressText.of(null) shouldBe "Scroll through My Cards"
    }

    @Test
    fun `in automatic mode the text says the app is scrolling by itself`() {
        ProgressText.of(null, automatic = true) shouldBe "Scanning…"
        ProgressText.of(ScanProgress(5, 12, 0), automatic = true) shouldBe "Scanning… 12 cards"
        ProgressText.of(ScanProgress(5, 12, 3), automatic = true) shouldBe "Scanning… 12 cards · 3 to check"
    }
}
