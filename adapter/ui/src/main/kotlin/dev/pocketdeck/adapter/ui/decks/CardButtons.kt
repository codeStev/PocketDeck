package dev.pocketdeck.adapter.ui.decks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A secondary action on a deck card. The default outline is nearly invisible on the tinted cards, so the border takes
 * the accent colour; the primary action of a card is a filled button.
 */
@Composable
internal fun CardOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
            ),
        content = content,
    )
}

private const val DISABLED_ALPHA = 0.38f
