package dev.pocketdeck.adapter.recognition

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class IdentificationConfidenceTest {
    @Test
    fun `a near perfect match is certain whatever the margin`() {
        // Reprints share their artwork, so their margin is close to zero.
        IdentificationConfidence.of(score = 0.99f, margin = 0.002f) shouldBe 1.0
    }

    @Test
    fun `a holo card that scores lower is still certain when no other card comes close`() {
        IdentificationConfidence.of(score = 0.93f, margin = 0.35f) shouldBe 1.0
        IdentificationConfidence.of(score = 0.88f, margin = 0.30f) shouldBe 1.0
    }

    @Test
    fun `a middling score with a small margin is doubtful`() {
        IdentificationConfidence.of(score = 0.93f, margin = 0.02f) shouldBeLessThan 0.8
    }

    @Test
    fun `a weak match that barely beats the runner up is very doubtful`() {
        IdentificationConfidence.of(score = 0.83f, margin = 0.04f) shouldBeLessThan 0.25
    }

    @Test
    fun `more separation never lowers the confidence`() {
        // A score this low contributes little by itself, so the margin decides.
        val low = IdentificationConfidence.of(score = 0.82f, margin = 0.05f)
        val medium = IdentificationConfidence.of(score = 0.82f, margin = 0.15f)
        val high = IdentificationConfidence.of(score = 0.82f, margin = 0.25f)

        medium shouldBeGreaterThan low
        high shouldBeGreaterThan medium
    }

    @Test
    fun `the result stays within 0 and 1`() {
        IdentificationConfidence.of(score = 0.5f, margin = -1f) shouldBe 0.0
        IdentificationConfidence.of(score = 1.0f, margin = 2f) shouldBe 1.0
    }
}
