package dev.codestev.pocketdesign.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codestev.pocketdesign.theme.PdMotion
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

/** One destination of [PdFloatingNav]: its icon, localised [label] and the [color] that identifies it. */
@Immutable
class PdNavTab(
    val label: String,
    val icon: ImageVector,
    val color: Color,
)

private val TabMinWidth = 72.dp
private val GlyphWidth = 44.dp
private val GlyphHeight = 36.dp
private val IconSize = 24.dp
private val FloatElevation = 12.dp

/**
 * The main navigation: a pill floating above the content (leave a margin to the screen edge and the gesture bar, and
 * reserve its height as bottom padding of scrolling content). Every tab owns a colour: an unselected tab shows its
 * outline icon in that colour, the selected tab fills its icon capsule with it and turns the label to ink. Labels stay
 * visible, so colour is never the only cue. Use two to five tabs with one-word labels and distinct colours.
 */
@Composable
fun PdFloatingNav(
    tabs: List<PdNavTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PocketDesign.colors
    val shape = PocketDesign.shapes.pill
    Row(
        modifier =
            modifier
                .shadow(FloatElevation, shape)
                .background(colors.surfaceCard, shape)
                .padding(PdSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space1),
    ) {
        tabs.forEachIndexed { index, tab -> NavTab(tab, selected = index == selectedIndex) { onSelect(index) } }
    }
}

@Composable
private fun NavTab(
    tab: PdNavTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = PocketDesign.colors
    val shape = PocketDesign.shapes.pill
    val glyphColor by animateColorAsState(
        if (selected) tab.color else Color.Transparent,
        tween(PdMotion.STATE_MILLIS),
        label = "navGlyph",
    )
    val iconTint by animateColorAsState(
        if (selected) colors.surfaceCard else tab.color,
        tween(PdMotion.STATE_MILLIS),
        label = "navIcon",
    )
    val labelColor by animateColorAsState(
        if (selected) colors.ink else colors.inkMuted,
        tween(PdMotion.STATE_MILLIS),
        label = "navLabel",
    )
    Column(
        modifier =
            Modifier
                .widthIn(min = TabMinWidth)
                .pdSelectable(shape, selected, onClick)
                .padding(horizontal = PdSpacing.space2, vertical = PdSpacing.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier.size(GlyphWidth, GlyphHeight).background(glyphColor, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                tab.icon,
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                tint = iconTint,
            )
        }
        PdCapFontScale {
            Text(
                tab.label,
                style = PocketDesign.typography.titleSm.copy(fontSize = 11.sp, lineHeight = 14.sp),
                color = labelColor,
                maxLines = 1,
            )
        }
    }
}
