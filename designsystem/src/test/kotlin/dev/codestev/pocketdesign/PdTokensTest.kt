package dev.codestev.pocketdesign

import dev.codestev.pocketdesign.brand.SignalBrand
import dev.codestev.pocketdesign.icons.PdIcons
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PdTokensTest {
    @Test
    fun `an unknown tint key falls back to a neutral tint instead of crashing`() {
        val colors = SignalBrand.light

        colors.tint("does-not-exist").soft shouldBe colors.surfaceSunken
        colors.tint("does-not-exist").strong shouldBe colors.inkMuted
    }

    @Test
    fun `both themes of the brand offer the same tints`() {
        SignalBrand.light.tints.keys shouldBe SignalBrand.dark.tints.keys
        SignalBrand.light.tints.size shouldBe 10
    }

    @Test
    fun `the brand picks its colours by theme`() {
        SignalBrand.brand.colors(darkTheme = false).isLight shouldBe true
        SignalBrand.brand.colors(darkTheme = true).isLight shouldBe false
    }

    @Test
    fun `every icon parses into drawable paths`() {
        PdIcons.all shouldHaveSize 18
        PdIcons.all.forEach { icon -> (icon.root.size > 0) shouldBe true }
    }
}
