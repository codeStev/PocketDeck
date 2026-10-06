package dev.pocketdeck.adapter.recognition

import dev.pocketdeck.application.scan.ScreenFrame
import dev.pocketdeck.domain.card.CardId
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ArtFingerprinterTest {
    private val parameters =
        FingerprintParameters(width = 48, height = 28, x0 = 0.0625f, y0 = 0.125f, x1 = 0.9375f, y1 = 0.5f)
    private val fingerprinter = ArtFingerprinter(parameters)

    private fun solidFrame(
        width: Int,
        height: Int,
        gray: Int,
    ) = TestImages.solid(width, height, (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray)

    @Test
    fun `a flat image keeps its brightness`() {
        val frame = solidFrame(367, 512, gray = 200)

        val thumbnail = fingerprinter.fingerprint(frame, TileBounds(0, 0, 367, 512))

        thumbnail.size shouldBe 48 * 28
        thumbnail.all { (it.toInt() and 0xFF) == 200 } shouldBe true
    }

    @Test
    fun `only the illustration region of the tile matters`() {
        val tile = TileBounds(10, 20, 300, 420)
        val plain = solidFrame(400, 500, gray = 100)
        val pixels = plain.pixels.copyOf()
        for (y in 20 until 20 + (0.10 * 420).toInt()) for (x in 10 until 310) pixels[y * 400 + x] = 0xFFFFFFFF.toInt()
        for (y in 20 + (0.55 * 420).toInt() until 440) for (x in 10 until 310) pixels[y * 400 + x] = 0xFF000000.toInt()
        val altered = ScreenFrame(400, 500, pixels)

        fingerprinter.fingerprint(altered, tile).toList() shouldBe fingerprinter.fingerprint(plain, tile).toList()
    }

    @Test
    fun `matches the python tool on a real reference card`() {
        val reference = TestImages.frame("reference-a1-036.png")
        val expected = TestImages.loadIndex().thumbnailOf(CardId("a1-036"))

        val actual = fingerprinter.fingerprint(reference, TileBounds(0, 0, reference.width, reference.height))

        val meanDifference =
            actual.indices.sumOf { abs((actual[it].toInt() and 0xFF) - (expected[it].toInt() and 0xFF)) } /
                actual.size.toDouble()
        meanDifference shouldBeLessThan 0.25
    }

    @Test
    fun `parameters from the index header drive the fingerprint size`() {
        val tiny = ArtFingerprinter(FingerprintParameters(8, 4, 0f, 0f, 1f, 1f))

        tiny.fingerprint(solidFrame(80, 40, gray = 50), TileBounds(0, 0, 80, 40)).size shouldBe 32
    }
}
