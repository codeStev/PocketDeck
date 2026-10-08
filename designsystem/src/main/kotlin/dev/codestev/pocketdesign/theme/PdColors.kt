package dev.codestev.pocketdesign.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * A categorical colour pair: [strong] is a graphic (stripe, dot, icon, tab) that holds 3:1 on [PdColors.surfaceCard],
 * [soft] is a tinted ground behind art or a card with [PdColors.ink] on top at 4.5:1. Never use [strong] as text.
 */
@Immutable
class PdTint(
    val strong: Color,
    val soft: Color,
)

/**
 * The semantic colours of one theme. Components read only these names, never a literal colour, so a brand can change
 * the whole look by supplying a different set. [tints] are the brand's categorical colours, looked up by key.
 */
@Immutable
class PdColors(
    val isLight: Boolean,
    val surfacePage: Color,
    val surfaceCard: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val focus: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val tints: Map<String, PdTint> = emptyMap(),
) {
    private val neutralTint = PdTint(strong = inkMuted, soft = surfaceSunken)

    /** The tint registered under [key]; an unknown key gives a neutral tint, so a missing brand entry never crashes. */
    fun tint(key: String): PdTint = tints[key] ?: neutralTint
}
