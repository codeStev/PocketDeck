package dev.pocketdeck.adapter.capture

import dev.pocketdeck.application.scan.ScreenFrame
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FrameChangeDetectorTest {
    private fun frame(vararg changes: Pair<Int, Int>): ScreenFrame {
        val pixels = IntArray(200 * 300) { 0xFF112233.toInt() }
        changes.forEach { (index, color) -> pixels[index] = color }
        return ScreenFrame(200, 300, pixels)
    }

    @Test
    fun `the first frame is always new`() {
        FrameChangeDetector().isNew(frame()) shouldBe true
    }

    @Test
    fun `an identical frame is not new`() {
        val detector = FrameChangeDetector()
        detector.isNew(frame())

        detector.isNew(frame()) shouldBe false
    }

    @Test
    fun `a changed frame is new again`() {
        val detector = FrameChangeDetector()
        detector.isNew(frame())

        // Repaint a whole block so that the sampled pixels certainly see it.
        val moved = ScreenFrame(200, 300, IntArray(200 * 300) { 0xFF445566.toInt() })

        detector.isNew(moved) shouldBe true
        detector.isNew(moved) shouldBe false
    }

    @Test
    fun `a frame of a different size is new`() {
        val detector = FrameChangeDetector()
        detector.isNew(frame())

        detector.isNew(ScreenFrame(100, 100, IntArray(100 * 100) { 0xFF112233.toInt() })) shouldBe true
    }

    @Test
    fun `forgetting makes the next identical frame new again`() {
        val detector = FrameChangeDetector()
        detector.isNew(frame())

        detector.reset()

        detector.isNew(frame()) shouldBe true
    }
}
