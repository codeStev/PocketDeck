package dev.pocketdeck.application.scan

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ScrollShiftTest {
    private val width = 40
    private val height = 200

    /** A picture whose every row differs, like a grid of different cards; [offset] is how far it is scrolled. */
    private fun frame(offset: Int): ScreenFrame {
        val pixels =
            IntArray(width * height) {
                val row = offset + it / width
                val column = it % width
                val hash = Random(row * 31 + column / 4).nextInt(0x1000000)
                0xFF000000.toInt() or hash
            }
        return ScreenFrame(width, height, pixels)
    }

    @Test
    fun `measures how far the content moved up`() {
        measureScrollShift(frame(0), frame(37), top = 0, bottom = height) shouldBe 37
    }

    @Test
    fun `measures no movement for an unchanged picture`() {
        measureScrollShift(frame(10), frame(10), top = 0, bottom = height) shouldBe 0
    }

    @Test
    fun `only looks inside the given band`() {
        measureScrollShift(frame(0), frame(60), top = 20, bottom = 180) shouldBe 60
    }

    @Test
    fun `gives up when the two pictures have nothing in common`() {
        measureScrollShift(frame(0), frame(5_000), top = 0, bottom = height) shouldBe null
    }
}
