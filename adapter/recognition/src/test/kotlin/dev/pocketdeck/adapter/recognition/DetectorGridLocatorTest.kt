package dev.pocketdeck.adapter.recognition

import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DetectorGridLocatorTest {
    private val locator = DetectorGridLocator(CollectionGridDetector())

    @Test
    fun `describes the three rows of a real collection screen`() {
        val geometry = locator.locate(TestImages.frame("collection-grid-nine-tiles.png"))!!

        geometry.visibleRows shouldBe 3
        geometry.rowPitch shouldBeInRange 455..459
        geometry.firstRowTop shouldBeInRange 782..786
        geometry.lastRowBottom shouldBeInRange 2136..2142
        geometry.centerX shouldBeInRange 535..545
    }

    @Test
    fun `the pitch is the distance between rows, not the tile height`() {
        val geometry = locator.locate(TestImages.frame("collection-grid-settled-counts.png"))!!

        // Tiles are 441 px tall with a 15 px gap between rows.
        geometry.rowPitch shouldBeInRange 454..458
    }

    @Test
    fun `a single visible row still gets a pitch from the tile size`() {
        val full = TestImages.frame("collection-grid-nine-tiles.png")
        val oneRow = TestImages.blankedFrom(full, fromY = 1300, color = TestImages.GRID_BACKGROUND)

        val geometry = locator.locate(oneRow)!!

        geometry.visibleRows shouldBe 1
        geometry.rowPitch shouldBeInRange 440..470
    }

    @Test
    fun `a screen without a grid has no geometry`() {
        locator.locate(TestImages.solid(1080, 2340, TestImages.GRID_BACKGROUND)) shouldBe null
    }
}
