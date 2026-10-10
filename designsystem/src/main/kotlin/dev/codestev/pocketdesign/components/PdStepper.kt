package dev.codestev.pocketdesign.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdMotion
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

private val ButtonSize = 44.dp
private val IconSize = 20.dp
private val CountWidth = 32.dp
private const val DISABLED_ALPHA = 0.4f

/**
 * Two round 44dp buttons around a count, for editing how many of something there are. Both buttons stay visible and
 * disable at the ends of [range]. The two descriptions are localised by the caller ("Remove one copy of Bulbasaur").
 */
@Composable
fun PdStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    decrementDescription: String,
    incrementDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(PdIcons.Minus, decrementDescription, enabled = value > range.first) { onValueChange(value - 1) }
        // The number slides the way it counts: up when it grows, down when it shrinks.
        AnimatedContent(
            targetState = value,
            modifier = Modifier.widthIn(min = CountWidth),
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (
                    slideInVertically(tween(PdMotion.STATE_MILLIS)) { direction * it / 2 } +
                        fadeIn(tween(PdMotion.STATE_MILLIS))
                ).togetherWith(
                    slideOutVertically(tween(PdMotion.STATE_MILLIS)) { -direction * it / 2 } +
                        fadeOut(tween(PdMotion.STATE_MILLIS)),
                )
            },
            contentAlignment = Alignment.Center,
            label = "stepperValue",
        ) { shown ->
            Text(
                text = shown.toString(),
                modifier = Modifier.fillMaxWidth(),
                style = PocketDesign.typography.numeric,
                color = PocketDesign.colors.ink,
                textAlign = TextAlign.Center,
            )
        }
        StepButton(PdIcons.Plus, incrementDescription, enabled = value < range.last) { onValueChange(value + 1) }
    }
}

@Composable
private fun StepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = PocketDesign.shapes.pill
    Box(
        modifier =
            Modifier
                .size(ButtonSize)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .pdClickable(shape, onClick, enabled = enabled)
                .background(PocketDesign.colors.surfaceSunken, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(IconSize), tint = PocketDesign.colors.ink)
    }
}
