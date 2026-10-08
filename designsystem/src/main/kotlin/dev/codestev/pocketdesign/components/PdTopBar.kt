package dev.codestev.pocketdesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

private val TagHeight = 44.dp
private val TagIconSize = 20.dp
private val TagShape =
    RoundedCornerShape(topStartPercent = 0, topEndPercent = 50, bottomEndPercent = 50, bottomStartPercent = 0)

/**
 * The top of a screen: the brand tag bleeds off the left edge in the accent colour and is fully rounded on the right,
 * with plain icon [actions] at the end. Place it as the first element, edge to edge (no start padding around it), once
 * per screen. The tag never wraps, so keep its text short (an account name, a screen name). Pass [onTagClick] to make
 * it open something, such as an account switcher, and [tagTrailingIcon] to hint at that.
 */
@Composable
fun PdTopBar(
    tag: String,
    modifier: Modifier = Modifier,
    onTagClick: (() -> Unit)? = null,
    tagTrailingIcon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = PocketDesign.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = PdSpacing.space3).padding(end = PdSpacing.space4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .height(TagHeight)
                    .then(if (onTagClick != null) Modifier.pdClickable(TagShape, onTagClick) else Modifier)
                    .background(colors.accent, TagShape)
                    .padding(start = PdSpacing.space4, end = PdSpacing.space5),
            horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tag, style = PocketDesign.typography.titleMd, color = colors.onAccent, maxLines = 1)
            if (tagTrailingIcon != null) {
                Icon(
                    tagTrailingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(TagIconSize),
                    tint = colors.onAccent,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
