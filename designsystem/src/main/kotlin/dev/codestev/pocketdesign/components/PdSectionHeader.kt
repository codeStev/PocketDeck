package dev.codestev.pocketdesign.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

private val ChevronSize = 18.dp

/**
 * A `titleLg` heading that opens a group on an art-led screen, with an optional text action on the right ("See all"
 * and a chevron, in the accent colour). Leave [PdSpacing.space7] above it and [PdSpacing.space3] below. Not for a card's
 * own title (use `titleMd`).
 */
@Composable
fun PdSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = PocketDesign.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = PocketDesign.typography.titleLg,
            color = colors.ink,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (actionLabel != null) {
            Row(
                modifier =
                    Modifier
                        .heightIn(min = PdSpacing.minTouchTarget)
                        .pdClickable(PocketDesign.shapes.pill, onAction)
                        .padding(horizontal = PdSpacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    actionLabel,
                    style = PocketDesign.typography.titleSm.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = colors.accent,
                )
                Icon(
                    PdIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(ChevronSize),
                    tint = colors.accent,
                )
            }
        }
    }
}
