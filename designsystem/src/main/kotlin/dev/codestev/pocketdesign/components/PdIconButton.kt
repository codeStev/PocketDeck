package dev.codestev.pocketdesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

enum class PdIconButtonStyle {
    /** A raised round button on a card or page. */
    Default,

    /** The main floating action of a screen; use once. */
    Accent,

    /** No fill: inside a top bar. */
    Plain,
}

private val IconSize = 24.dp
private val SpinnerSize = 22.dp
private val CardElevation = 4.dp
private val DotSize = 12.dp
private val DotRing = 2.dp
private val DotInset = 8.dp

/**
 * A 48dp round button for one icon action. The [contentDescription] is required: there is no visible label. [badge]
 * shows a small dot for something unread.
 * While [loading] a spinner replaces the icon and taps are ignored; the button stays fully visible, unlike when [enabled] is false.
 */
@Composable
fun PdIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PdIconButtonStyle = PdIconButtonStyle.Default,
    badge: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = PocketDesign.colors
    val shape = PocketDesign.shapes.pill
    val container =
        when (style) {
            PdIconButtonStyle.Default -> colors.surfaceCard
            PdIconButtonStyle.Accent -> colors.accent
            PdIconButtonStyle.Plain -> Color.Transparent
        }
    val tint = if (style == PdIconButtonStyle.Accent) colors.onAccent else colors.ink
    val elevation = if (style == PdIconButtonStyle.Default && colors.isLight) CardElevation else 0.dp
    Box(
        modifier =
            modifier
                .size(PdSpacing.minTouchTarget)
                .shadow(elevation, shape)
                .pdClickable(shape, onClick, enabled = enabled && !loading)
                .background(container, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            PdSpinner(size = SpinnerSize, color = colors.accent, strokeWidth = 2.5.dp)
        } else {
            Icon(icon, contentDescription, Modifier.size(IconSize), tint = tint)
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = DotInset, end = DotInset)
                    .size(DotSize)
                    .background(colors.danger, shape)
                    .border(DotRing, colors.surfaceCard, shape),
            )
        }
    }
}
