package dev.pocketdeck.adapter.recognition

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MatchAcceptanceTest {
    @Test
    fun `a clean match is accepted whatever the runner up`() {
        MatchAcceptance.accepts(best = 0.95f, runnerUp = 0.94f) shouldBe true
    }

    @Test
    fun `a weaker match is accepted when it stands clearly apart`() {
        MatchAcceptance.accepts(best = 0.83f, runnerUp = 0.60f) shouldBe true
    }

    @Test
    fun `stadium and promo cards that score low are accepted when nothing else comes close`() {
        // Real examples: Training Area scored 0.78 with the runner up at 0.45.
        MatchAcceptance.accepts(best = 0.78f, runnerUp = 0.45f) shouldBe true
        MatchAcceptance.accepts(best = 0.64f, runnerUp = 0.43f) shouldBe true
    }

    @Test
    fun `two near identical candidates are ambiguous and rejected`() {
        // Real example: b2a-009 scored 0.883 and b4b-256 0.875.
        MatchAcceptance.accepts(best = 0.85f, runnerUp = 0.84f) shouldBe false
    }

    @Test
    fun `a poor match is rejected even if the runner up is worse`() {
        MatchAcceptance.accepts(best = 0.55f, runnerUp = 0.20f) shouldBe false
    }

    @Test
    fun `a single candidate needs a good score of its own`() {
        MatchAcceptance.accepts(best = 0.70f, runnerUp = null) shouldBe true
        MatchAcceptance.accepts(best = 0.50f, runnerUp = null) shouldBe false
    }
}
