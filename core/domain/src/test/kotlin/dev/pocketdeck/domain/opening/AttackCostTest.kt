package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.EnergyType.FIRE
import dev.pocketdeck.domain.card.EnergyType.WATER
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AttackCostTest {
    @Test
    fun `typed energy must match`() {
        AttackCost.canPay("R", listOf(FIRE)) shouldBe true
        AttackCost.canPay("R", listOf(WATER)) shouldBe false
        AttackCost.canPay("RW", listOf(FIRE, WATER)) shouldBe true
        AttackCost.canPay("RW", listOf(FIRE, FIRE)) shouldBe false
    }

    @Test
    fun `colourless takes any energy but still needs an energy per symbol`() {
        AttackCost.canPay("C", listOf(WATER)) shouldBe true
        AttackCost.canPay("RC", listOf(FIRE)) shouldBe false
        AttackCost.canPay("RC", listOf(FIRE, WATER)) shouldBe true
        AttackCost.canPay("CC", listOf(FIRE, FIRE)) shouldBe true
    }

    @Test
    fun `typed energy is not spent on colourless first`() {
        AttackCost.canPay("CR", listOf(FIRE, WATER)) shouldBe true
    }

    @Test
    fun `an unknown symbol can never be paid`() {
        AttackCost.canPay("X", listOf(FIRE)) shouldBe false
    }

    @Test
    fun `energy helps when it closes the gap`() {
        val attacks = listOf(Attack("Ember", "RR", 50, null))
        AttackCost.helps(attacks, listOf(FIRE), FIRE) shouldBe true
        AttackCost.helps(attacks, emptyList(), WATER) shouldBe false
        AttackCost.helps(attacks, listOf(FIRE, FIRE), FIRE) shouldBe false
    }

    @Test
    fun `the main attack is the one that hurts most`() {
        val cheap = Attack("Scratch", "C", 10, null)
        val strong = Attack("Flare", "RRR", 100, null)
        AttackCost.mainOf(listOf(cheap, strong)) shouldBe strong
        AttackCost.mainOf(emptyList()) shouldBe null
    }
}
