package dev.codestev.pocketdesign.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdMotion
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

private val ChipHeight = 36.dp
private val IconSize = 18.dp
private val BorderWidth = 2.dp

/**
 * A chip the player switches on and off to narrow a list ("Owned only"). Off it is an outlined pill on the card
 * surface, so it reads as something to press; on it fills with the accent and shows a check mark. The tap area is the
 * full 48dp. Not for status (use [PdChip]) and not for choosing one of several views (use [PdSegmentedTabs]).
 */
@Composable
fun PdFilterChip(
    text: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PocketDesign.colors
    val shape = PocketDesign.shapes.pill
    val spec = tween<Color>(PdMotion.STATE_MILLIS)
    val content by animateColorAsState(if (selected) colors.onAccent else colors.ink, spec, label = "chipContent")
    val fill by animateColorAsState(if (selected) colors.accent else colors.surfaceCard, spec, label = "chipFill")
    val outline by animateColorAsState(if (selected) colors.accent else colors.lineStrong, spec, label = "chipOutline")
    Box(modifier.heightIn(min = PdSpacing.minTouchTarget), contentAlignment = Alignment.Center) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = ChipHeight)
                    .pdSelectable(shape, selected, { onSelectedChange(!selected) }, Role.Checkbox)
                    .background(fill, shape)
                    .border(BorderWidth, outline, shape)
                    .padding(horizontal = PdSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) Icon(PdIcons.Check, contentDescription = null, Modifier.size(IconSize), tint = content)
            Text(
                text,
                style = PocketDesign.typography.titleSm.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = content,
            )
        }
    }
}
