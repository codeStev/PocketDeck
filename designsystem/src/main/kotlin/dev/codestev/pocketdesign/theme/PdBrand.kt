package dev.codestev.pocketdesign.theme

import androidx.compose.runtime.Immutable

/**
 * What an app brings to the design system: a name and one colour set per theme. Everything else (type scale, shapes,
 * spacing, components) is shared. Another project replaces only the brand.
 */
@Immutable
class PdBrand(
    val name: String,
    val light: PdColors,
    val dark: PdColors,
) {
    fun colors(darkTheme: Boolean): PdColors = if (darkTheme) dark else light
}
