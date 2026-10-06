package dev.pocketdeck.adapter.recognition

import dev.pocketdeck.domain.card.CardId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.EOFException

class CardIndexTest {
    private fun indexBytes(
        entries: List<Pair<String, ByteArray>>,
        magic: String = "PDIX",
        version: Int = 1,
    ) = TestImages.indexBytes(entries, magic = magic, version = version)

    private fun levels(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private val ramp = levels(0, 40, 80, 120, 160, 200)
    private val reversed = ramp.reversedArray()
    private val bumpy = levels(10, 200, 10, 200, 10, 200)

    private fun smallIndex() =
        CardIndex.read(
            ByteArrayInputStream(
                indexBytes(
                    listOf(
                        "a1-001" to ramp,
                        "a1-002" to reversed,
                        "a1-003" to bumpy,
                    ),
                ),
            ),
        )

    @Test
    fun `reads the header parameters and every entry`() {
        val index = smallIndex()

        index.size shouldBe 3
        index.parameters shouldBe FingerprintParameters(3, 2, 0.0625f, 0.125f, 0.9375f, 0.5f)
    }

    @Test
    fun `the nearest entry to its own thumbnail is itself with a perfect score`() {
        val best = smallIndex().nearest(ramp, 2)

        best.first().cardId shouldBe CardId("a1-001")
        best.first().score shouldBeGreaterThan 0.999f
        best[1].score shouldBeGreaterThan -1.01f
    }

    @Test
    fun `brightness and contrast changes do not affect the match`() {
        val brighter = ByteArray(6) { ((ramp[it].toInt() and 0xFF) / 2 + 100).toByte() }

        smallIndex().nearest(brighter, 1).single().run {
            cardId shouldBe CardId("a1-001")
            score shouldBeGreaterThan 0.999f
        }
    }

    @Test
    fun `an inverted thumbnail matches the reversed entry`() {
        smallIndex().nearest(reversed, 1).single().cardId shouldBe CardId("a1-002")
    }

    @Test
    fun `returns at most the requested number of matches best first`() {
        val matches = smallIndex().nearest(ramp, 3)

        matches.size shouldBe 3
        matches.map { it.score } shouldBe matches.map { it.score }.sortedDescending()
    }

    @Test
    fun `a flat thumbnail matches nothing well`() {
        smallIndex().nearest(ByteArray(6) { 77 }, 1).single().score shouldBe 0f
    }

    @Test
    fun `rejects foreign and unsupported data`() {
        shouldThrow<IllegalArgumentException> {
            CardIndex.read(
                ByteArrayInputStream(indexBytes(emptyList(), magic = "NOPE")),
            )
        }
        shouldThrow<IllegalArgumentException> {
            CardIndex.read(
                ByteArrayInputStream(indexBytes(emptyList(), version = 2)),
            )
        }
    }

    @Test
    fun `rejects a truncated file`() {
        val truncated = indexBytes(listOf("a1-001" to ramp)).dropLast(3).toByteArray()

        shouldThrow<EOFException> { CardIndex.read(ByteArrayInputStream(truncated)) }
    }

    @Test
    fun `the shipped index holds one entry per distinct artwork`() {
        val index = TestImages.loadIndex()

        // 4,317 printed cards, of which 700 are reprints of another card with identical artwork and effect.
        index.size shouldBe 3617
        index.parameters.width shouldBe 48
        index.parameters.height shouldBe 28
        index.thumbnailOf(CardId("a2b-108")).size shouldBe 48 * 28
    }

    @Test
    fun `a reprint is represented by the lowest id of its artwork`() {
        val index = TestImages.loadIndex()

        // a4b-061 reprints a1-039 (same artwork, same effect), so only the original is in the index.
        index.thumbnailOf(CardId("a1-039")).size shouldBe 48 * 28
        shouldThrow<IllegalArgumentException> { index.thumbnailOf(CardId("a4b-061")) }
    }
}
