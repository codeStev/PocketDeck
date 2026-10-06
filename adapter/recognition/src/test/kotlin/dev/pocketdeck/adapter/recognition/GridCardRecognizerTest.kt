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
    fun `a partly covered tile is still recognised, with less confidence`() =
        runTest {
            // Cover the right-hand part of the first tile's artwork, like the game's floating scroll button does.
            val covered = screenshot.pixels.copyOf()
            for (y in 900..990) for (x in 320..366) covered[y * screenshot.width + x] = 0xFFE8EEF4.toInt()
            val frame = ScreenFrame(screenshot.width, screenshot.height, covered)

            val uncoveredConfidence =
                recognizer()
                    .recognize(screenshot)
                    .first()
                    .confidence.value
            val first = recognizer().recognize(frame).first()

            first.cardId shouldBe expectedIds.first()
            // Less certain than the uncovered tile, which scores well above 0.9.
            first.confidence.value shouldBeLessThan
                recognizer()
                    .recognize(screenshot)
                    .first()
                    .confidence.value
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
}
