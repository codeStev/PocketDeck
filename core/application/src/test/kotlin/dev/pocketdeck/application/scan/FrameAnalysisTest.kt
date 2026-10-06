package dev.pocketdeck.application.scan

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FrameAnalysisTest {
    private fun analysis(
        expected: Int,
        read: Int,
    ) = FrameAnalysis(ScanProgress(1, 0, 0), tilesRecognized = 9, countsExpected = expected, countsRead = read)

    @Test
    fun `counts are visible only when every tile that should show one does`() {
        analysis(expected = 6, read = 6).countsVisible shouldBe true
        analysis(expected = 6, read = 5).countsVisible shouldBe false
        analysis(expected = 6, read = 0).countsVisible shouldBe false
    }

    @Test
    fun `a frame whose tiles expect no badge has nothing to wait for`() {
        analysis(expected = 0, read = 0).countsVisible shouldBe true
    }
}
