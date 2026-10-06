package dev.pocketdeck.adapter.recognition

import dev.pocketdeck.application.scan.ScreenFrame
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.scan.TileRecognition
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class GridCardRecognizerTest {
    private val index = TestImages.loadIndex()
    private val screenshot = TestImages.frame("collection-grid-nine-tiles.png")

    private val singleCopy = CopyCountReader { _, _ -> CopyCountReading(copies = 1, confidence = 1.0) }

    private fun recognizer(copies: CopyCountReader = singleCopy) =
        GridCardRecognizer(index, CollectionGridDetector(), copies)

    private suspend fun firstTile(frame: ScreenFrame) = recognizer().recognize(frame).first()

    /** What the game shows in this screenshot, row by row (German names: Glurak ex, Klibbe, Kangama, ...). */
    private val expectedIds =
        listOf(
            "a2b-108",
            "b3a-100",
            "b3b-098",
            "b4-214",
            "b4-215",
            "b4a-103",
            "a1-281",
            "b4-203",
            "a1-256",
        ).map(::CardId)

    @Test
    fun `identifies every card on a real collection screen`() =
        runTest {
            val recognitions = recognizer().recognize(screenshot)

            recognitions.map { it.cardId } shouldBe expectedIds
        }

    @Test
    fun `confident matches get a high confidence`() =
        runTest {
            recognizer().recognize(screenshot).forEach { it.confidence.value shouldBeGreaterThan 0.9 }
        }

    @Test
    fun `the copy count comes from the badge reader`() =
        runTest {
            // Columns start at x = 49, 382 and 713, so this reader reports 1, 2 and 3 copies per column.
            val byColumn =
                recognizer(
                    CopyCountReader {
                        _,
                        tile,
                        ->
                        CopyCountReading(copies = tile.x / 300 + 1, confidence = 1.0)
                    },
                )

            byColumn.recognize(screenshot).map { it.copies } shouldBe listOf(1, 2, 3, 1, 2, 3, 1, 2, 3)
        }

    @Test
    fun `an unsure badge reading lowers the overall confidence`() =
        runTest {
            val unsure = recognizer(CopyCountReader { _, _ -> CopyCountReading(copies = 1, confidence = 0.4) })

            unsure.recognize(screenshot).forEach { it.confidence.value shouldBe 0.4 }
        }

    @Test
    fun `tiles that are not known cards are skipped`() =
        runTest {
            val blank = TestImages.solid(1080, 2340, TestImages.GRID_BACKGROUND)

            recognizer().recognize(blank) shouldBe emptyList<TileRecognition>()
        }

    @Test
    fun `a tile without a visible count is recognised with an unknown count`() =
        runTest {
            val noBadge = recognizer(CopyCountReader { _, _ -> null })

            val recognitions = noBadge.recognize(screenshot)

            recognitions.map { it.cardId } shouldBe expectedIds
            recognitions.all { it.copies == null } shouldBe true
            recognitions.forEach { it.confidence.value shouldBeGreaterThan 0.9 }
        }

    @Test
    fun `a partly covered tile is still recognised`() =
        runTest {
            // Cover the right-hand part of the first tile's artwork, like the game's floating scroll button does.
            val covered = screenshot.pixels.copyOf()
            for (y in 900..990) for (x in 320..366) covered[y * screenshot.width + x] = 0xFFE8EEF4.toInt()
            val frame = ScreenFrame(screenshot.width, screenshot.height, covered)

            firstTile(frame).cardId shouldBe expectedIds.first()
        }

    @Test
    fun `reprints with identical artwork resolve to the same card every time`() =
        runTest {
            val thumbnail = index.thumbnailOf(expectedIds.first())
            val other = index.thumbnailOf(CardId("a1-001"))
            val twins = listOf("zz-reprint" to thumbnail, "aa-original" to thumbnail, "a1-001" to other)
            val twinBytes = TestImages.indexBytes(twins, index.parameters)
            val twinIndex = CardIndex.read(java.io.ByteArrayInputStream(twinBytes))

            val first =
                GridCardRecognizer(
                    twinIndex,
                    CollectionGridDetector(),
                    singleCopy,
                ).recognize(screenshot).first()

            first.cardId shouldBe CardId("aa-original")
        }

    @Test
    fun `identical tiles in one frame are different prints and their counts add up`() =
        runTest {
            // Copy the first tile onto the other two of its row: three prints of the same card side by side.
            val tiles = CollectionGridDetector().detect(screenshot).take(3)
            val pixels = screenshot.pixels.copyOf()
            for (target in tiles.drop(1)) {
                for (y in 0 until tiles[0].height) {
                    for (x in 0 until tiles[0].width) {
                        pixels[(target.y + y) * screenshot.width + target.x + x] =
                            screenshot.pixels[(tiles[0].y + y) * screenshot.width + tiles[0].x + x]
                    }
                }
            }
            val frame = ScreenFrame(screenshot.width, screenshot.height, pixels)
            val twoPerTile = CopyCountReader { _, _ -> CopyCountReading(copies = 2, confidence = 1.0) }

            val recognitions = recognizer(twoPerTile).recognize(frame)

            val combined = recognitions.single { it.cardId == expectedIds.first() }
            combined.copies shouldBe 6
            combined.parts shouldBe 3
            recognitions.size shouldBe 7
        }

    @Test
    fun `one unreadable twin makes the combined count unknown`() =
        runTest {
            val tiles = CollectionGridDetector().detect(screenshot).take(2)
            val pixels = screenshot.pixels.copyOf()
            for (y in 0 until tiles[0].height) {
                for (x in 0 until tiles[0].width) {
                    pixels[(tiles[1].y + y) * screenshot.width + tiles[1].x + x] =
                        screenshot.pixels[(tiles[0].y + y) * screenshot.width + tiles[0].x + x]
                }
            }
            val frame = ScreenFrame(screenshot.width, screenshot.height, pixels)
            var first = true
            val oneUnreadable =
                CopyCountReader {
                    _,
                    _,
                    ->
                    if (first) null.also { first = false } else CopyCountReading(2, 1.0)
                }

            val combined = recognizer(oneUnreadable).recognize(frame).single { it.cardId == expectedIds.first() }

            combined.copies shouldBe null
            combined.parts shouldBe 2
        }

    @Test
    fun `only the tiles above the navigation fade expect a count badge`() =
        runTest {
            val recognitions = recognizer().recognize(screenshot)

            // The nine-tile screenshot has three rows; the third ends at 91% of the screen height, in the fade.
            recognitions.map { it.expectsCount } shouldBe List(6) { true } + List(3) { false }
        }
}
