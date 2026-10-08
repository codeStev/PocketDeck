package dev.codestev.pocketdesign.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** The largest font scale tab and navigation labels follow. */
internal const val TAB_LABEL_MAX_FONT_SCALE = 1.3f

/**
 * Labels that sit in a bar of fixed width cannot wrap, and at the largest system font ("Tournament", "Settings") they would
 * be cut off. They stop growing at [max] times the normal size, as Material's own navigation bar does, while body text,
 * titles and buttons keep following the system setting.
 */
@Composable
internal fun PdCapFontScale(
    max: Float = TAB_LABEL_MAX_FONT_SCALE,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, minOf(density.fontScale, max)),
        content = content,
    )
}
