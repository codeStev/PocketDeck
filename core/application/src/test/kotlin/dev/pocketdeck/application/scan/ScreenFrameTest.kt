package dev.pocketdeck.application.scan

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test

class ScreenFrameTest {
    @Test
    fun `pixel buffer must match the dimensions`() {
        shouldThrow<IllegalArgumentException> { ScreenFrame(width = 2, height = 2, pixels = IntArray(3)) }
    }

    @Test
    fun `dimensions must be positive`() {
        shouldThrow<IllegalArgumentException> { ScreenFrame(width = 0, height = 1, pixels = IntArray(0)) }
    }
}
