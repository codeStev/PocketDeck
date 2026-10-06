package dev.pocketdeck.adapter.recognition

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

class CollectionGridDetectorTest {
    private val detector = CollectionGridDetector()
    private val screenshot = TestImages.frame("collection-grid-nine-tiles.png")

    private fun TileBounds.closeTo(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) = abs(this.x - x) <= 2 && abs(this.y - y) <= 2 && abs(this.width - width) <= 3 && abs(this.height - height) <= 3

    @Test
    fun `finds the three by three tiles of a real collection screen`() {
        val tiles = detector.detect(screenshot)

        val expectedColumns = listOf(49, 382, 713)
        val expectedRows = listOf(784, 1241, 1698)
        tiles.size shouldBe 9
        for (y in expectedRows) {
            for (x in expectedColumns) {
                tiles.any { it.closeTo(x, y, 318, 442) } shouldBe true
            }
        }
    }

    @Test
    fun `tiles are returned top to bottom and left to right`() {
        val tiles = detector.detect(screenshot)

        tiles shouldBe tiles.sortedWith(compareBy({ it.y }, { it.x }))
    }

    @Test
    fun `rows that are cut off are not reported`() {
        val cutAfterFirstRow = TestImages.blankedFrom(screenshot, fromY = 1300, color = TestImages.GRID_BACKGROUND)

        val tiles = detector.detect(cutAfterFirstRow)

        tiles.size shouldBe 3
        tiles.all { it.y in 780..790 } shouldBe true
    }

    @Test
    fun `an empty screen has no tiles`() {
        detector.detect(TestImages.solid(1080, 2340, TestImages.GRID_BACKGROUND)) shouldBe emptyList()
    }

    @Test
    fun `a screen that is not a card grid has no tiles`() {
        detector.detect(TestImages.solid(1080, 2340, 0xFF202020.toInt())) shouldBe emptyList()
    }
}
