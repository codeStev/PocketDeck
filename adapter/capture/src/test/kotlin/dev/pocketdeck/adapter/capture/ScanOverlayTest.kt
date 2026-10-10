package dev.pocketdeck.adapter.capture

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ScanOverlayTest {
    private val fixedDp = 94

    @Test
    fun `the status text leaves room for the stop button on a phone`() {
        val density = 2.8125f
        val screen = 1080
        val stop = (80 * density).toInt()

        val width = statusMaxWidth(screen, density, stop)

        (width + stop + (fixedDp * density).toInt()) shouldBeLessThanOrEqual screen
        width shouldBeGreaterThan (100 * density).toInt()
    }

    @Test
    fun `a wider stop button, as in German or with a large font, leaves less room for the status`() {
        val narrow = statusMaxWidth(screenWidthPx = 1080, density = 2.8125f, stopWidthPx = 200)
        val wide = statusMaxWidth(screenWidthPx = 1080, density = 2.8125f, stopWidthPx = 320)

        (narrow - wide) shouldBe 120
    }

    @Test
    fun `a tiny screen still gets a readable minimum`() {
        statusMaxWidth(screenWidthPx = 200, density = 1f, stopWidthPx = 90) shouldBe 96
    }
}
