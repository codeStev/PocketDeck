package dev.pocketdeck.adapter.capture

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ScanOverlayTest {
    private val reservedDp = 176

    @Test
    fun `the status text leaves room for the stop button on a phone`() {
        val density = 2.8125f
        val screen = 1080

        val width = statusMaxWidth(screen, density)

        (width + (reservedDp * density).toInt()) shouldBeLessThanOrEqual screen
        width shouldBeGreaterThan (100 * density).toInt()
    }

    @Test
    fun `the status text leaves room for the stop button on a narrow screen`() {
        statusMaxWidth(screenWidthPx = 320, density = 1f) shouldBe 144
    }

    @Test
    fun `a tiny screen still gets a readable minimum`() {
        statusMaxWidth(screenWidthPx = 200, density = 1f) shouldBe 96
    }
}
