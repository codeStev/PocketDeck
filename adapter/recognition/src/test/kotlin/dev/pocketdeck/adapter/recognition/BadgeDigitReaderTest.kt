package dev.pocketdeck.adapter.recognition

import dev.pocketdeck.application.scan.ScreenFrame
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BadgeDigitReaderTest {
    private val templates = DigitTemplates.fromResource()
    private val reader = BadgeDigitReader(templates)
    private val detector = CollectionGridDetector()

    private val settled = TestImages.frame("collection-grid-settled-counts.png")
    private val settledTiles = detector.detect(settled)

    private fun readAll(
        frame: ScreenFrame,
        tiles: List<TileBounds>,
    ) = tiles.map { reader.read(frame, it) }

    @Test
    fun `reads the counts of a settled collection screen`() {
        // Read off the screenshot by eye: row by row, the badges show 1 1 1 / 2 1 4 / 1 1 4.
        val counts = readAll(settled, settledTiles).map { it?.copies }

        counts shouldBe listOf(1, 1, 1, 2, 1, 4, 1, 1, 4)
    }

    @Test
    fun `reads a screen where every card is owned once`() {
        val frame = TestImages.frame("collection-grid-nine-tiles.png")

        readAll(frame, detector.detect(frame)).map { it?.copies } shouldBe List(9) { 1 }
    }

    @Test
    fun `clean real glyphs are read with full confidence`() {
        val readings = readAll(settled, settledTiles).filterNotNull()
        val ones = readings.filter { it.copies == 1 }

        ones.size shouldBe 6
        ones.forEach { it.confidence shouldBeGreaterThan 0.95 }
    }

    @Test
    fun `a digit only known from a stand-in font is read but with reduced confidence`() {
        val four = readAll(settled, settledTiles)[5]!!
        val realTemplatesOnly = BadgeDigitReader(DigitTemplates.parse(withoutGameGlyph(digit = 4)))

        val reading = realTemplatesOnly.read(settled, settledTiles[5])!!

        four.copies shouldBe 4
        reading.copies shouldBe 4
        reading.confidence shouldBeLessThan four.confidence
        reading.confidence shouldBeGreaterThan 0.5
    }

    @Test
    fun `reads high and two digit counts with confidence`() {
        val frame = TestImages.frame("collection-grid-high-counts.png")
        val readings = readAll(frame, detector.detect(frame))

        // Row by row the badges show 4 1 9 / 10 7 1 / 1 2 9.
        readings.map { it?.copies } shouldBe listOf(4, 1, 9, 10, 7, 1, 1, 2, 9)
        readings.forEach { it!!.confidence shouldBeGreaterThan 0.9 }
    }

    @Test
    fun `no badge tab means no reading`() {
        val withoutBadges = painted(settled, settledTiles) { _, _ -> 0xFFC8A05A.toInt() }

        readAll(withoutBadges, settledTiles).all { it == null } shouldBe true
    }

    @Test
    fun `a badge that is still fading in is not read`() {
        val fading = painted(settled, settledTiles) { _, _ -> 0xFF788296.toInt() }

        readAll(fading, settledTiles).all { it == null } shouldBe true
    }

    @Test
    fun `reads two digit counts`() {
        val tile = settledTiles.first()
        val frame = withBadgeDigits(settled, tile, listOf(1, 2))

        reader.read(frame, tile)?.copies shouldBe 12
    }

    @Test
    fun `an empty badge tab is not a count`() {
        val tile = settledTiles.first()
        val frame = withBadgeDigits(settled, tile, emptyList())

        reader.read(frame, tile) shouldBe null
    }

    @Test
    fun `the template set covers every digit`() {
        templates.templates.map { it.digit }.toSet() shouldBe (0..9).toSet()
        templates.templates
            .filter { !it.synthetic }
            .map { it.digit }
            .toSet() shouldBe (0..9).toSet()
    }

    /** Repaints the badge window of every tile with [color] for pixels that are not part of the glyph area. */
    private fun painted(
        frame: ScreenFrame,
        tiles: List<TileBounds>,
        color: (Int, Int) -> Int,
    ): ScreenFrame {
        val pixels = frame.pixels.copyOf()
        for (tile in tiles) {
            val top = tile.y + (0.84 * tile.height).toInt()
            for (y in top until tile.y + tile.height) {
                for (x in tile.x until tile.x + (0.55 * tile.width).toInt()) pixels[y * frame.width + x] = color(x, y)
            }
        }
        return ScreenFrame(frame.width, frame.height, pixels)
    }

    /** Redraws a tile's badge: the game's slate tab plus the template glyphs of [digits], 8 px apart. */
    private fun withBadgeDigits(
        frame: ScreenFrame,
        tile: TileBounds,
        digits: List<Int>,
    ): ScreenFrame {
        val pixels = frame.pixels.copyOf()
        val tabTop = tile.y + (0.90 * tile.height).toInt()
        for (y in tabTop until tile.y + tile.height) {
            for (x in tile.x until tile.x + (0.46 * tile.width).toInt()) pixels[y * frame.width + x] = SLATE
        }
        var x = tile.x + (0.14 * tile.width).toInt()
        digits.forEach { digit ->
            val glyph = templates.templates.first { it.digit == digit && !it.synthetic }
            for (gy in 0 until glyph.height) {
                for (gx in 0 until glyph.width) {
                    val level = glyph.raw[gy * glyph.width + gx].toInt() and 0xFF
                    if (level >
                        140
                    ) {
                        pixels[(tabTop + 8 + gy) * frame.width + x + gx] = 0xFF000000.toInt() or (level * 0x010101)
                    }
                }
            }
            x += glyph.width + 8
        }
        return ScreenFrame(frame.width, frame.height, pixels)
    }

    /** The template file without the real glyph of [digit], so that digit falls back to its synthetic stand-in. */
    private fun withoutGameGlyph(digit: Int): String {
        val stream = requireNotNull(DigitTemplates::class.java.getResourceAsStream("/digit_templates.txt"))
        val lines = stream.bufferedReader().use { it.readText() }.lines()
        return lines.filterNot { it.startsWith("$digit game ") }.joinToString("\n")
    }

    private companion object {
        const val SLATE = 0xFF4C5C74.toInt()
    }
}
