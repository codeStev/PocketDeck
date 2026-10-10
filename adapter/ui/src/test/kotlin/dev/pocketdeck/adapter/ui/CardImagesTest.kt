package dev.pocketdeck.adapter.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CardImagesTest {
    @Test
    fun `a TCGdex picture has a small version`() {
        thumbnailOf("https://assets.tcgdex.net/de/tcgp/A1/001/high.webp") shouldBe
            "https://assets.tcgdex.net/de/tcgp/A1/001/low.webp"
    }

    @Test
    fun `other hosts and missing pictures stay as they are`() {
        val github = "https://raw.githubusercontent.com/x/y/images/webp/cards/a1/001.webp"
        thumbnailOf(github) shouldBe github
        thumbnailOf(null) shouldBe null
    }
}
