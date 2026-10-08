package dev.codestev.pocketdesign

import androidx.compose.ui.graphics.Color
import dev.codestev.pocketdesign.brand.SignalBrand
import dev.codestev.pocketdesign.theme.PdColors
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test
import kotlin.math.pow

/**
 * The brand's colours are only worth shipping if they stay legible: text pairs hold 4.5:1 and graphics (borders, focus
 * rings, tint marks) 3:1 in both themes, so an edit that breaks a pair fails the build.
 */
class PdColorContrastTest {
    private fun channel(value: Float): Double =
        if (value <=
            0.03928f
        ) {
            value / 12.92
        } else {
            ((value + 0.055) / 1.055).pow(2.4)
        }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val (high, low) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (high + 0.05) / (low + 0.05)
    }

    private fun failures(c: PdColors): List<String> {
        val text =
            listOf(
                "ink on surfaceCard" to (c.ink to c.surfaceCard),
                "ink on surfacePage" to (c.ink to c.surfacePage),
                "ink on surfaceRaised" to (c.ink to c.surfaceRaised),
                "ink on surfaceSunken" to (c.ink to c.surfaceSunken),
                "inkMuted on surfaceCard" to (c.inkMuted to c.surfaceCard),
                "inkMuted on surfacePage" to (c.inkMuted to c.surfacePage),
                "inkMuted on surfaceSunken" to (c.inkMuted to c.surfaceSunken),
                "onAccent on accent" to (c.onAccent to c.accent),
                "accent on surfaceCard" to (c.accent to c.surfaceCard),
                "accent on surfacePage" to (c.accent to c.surfacePage),
                "onAccentSoft on accentSoft" to (c.onAccentSoft to c.accentSoft),
                "success on successSoft" to (c.success to c.successSoft),
                "success on surfaceCard" to (c.success to c.surfaceCard),
                "warning on warningSoft" to (c.warning to c.warningSoft),
                "warning on surfaceCard" to (c.warning to c.surfaceCard),
                "danger on dangerSoft" to (c.danger to c.dangerSoft),
                "danger on surfaceCard" to (c.danger to c.surfaceCard),
                "info on infoSoft" to (c.info to c.infoSoft),
                "info on surfaceCard" to (c.info to c.surfaceCard),
            ) +
                c.tints.flatMap { (key, tint) ->
                    listOf(
                        "ink on $key soft" to (c.ink to tint.soft),
                        "inkMuted on $key soft" to (c.inkMuted to tint.soft),
                    )
                }
        val graphics =
            listOf(
                "lineStrong on surfaceCard" to (c.lineStrong to c.surfaceCard),
                "lineStrong on surfacePage" to (c.lineStrong to c.surfacePage),
                "focus on surfaceCard" to (c.focus to c.surfaceCard),
                "focus on surfacePage" to (c.focus to c.surfacePage),
                "focus on surfaceSunken" to (c.focus to c.surfaceSunken),
            ) + c.tints.map { (key, tint) -> "$key strong on surfaceCard" to (tint.strong to c.surfaceCard) }
        return text.filter { (_, p) -> contrast(p.first, p.second) < TEXT_CONTRAST }.map { "text ${it.first}" } +
            graphics.filter { (_, p) -> contrast(p.first, p.second) < GRAPHIC_CONTRAST }.map { "graphic ${it.first}" }
    }

    @Test
    fun `every text and graphic pair of the light theme is legible`() {
        failures(SignalBrand.light).shouldBeEmpty()
    }

    @Test
    fun `every text and graphic pair of the dark theme is legible`() {
        failures(SignalBrand.dark).shouldBeEmpty()
    }

    private companion object {
        const val TEXT_CONTRAST = 4.5
        const val GRAPHIC_CONTRAST = 3.0
    }
}
