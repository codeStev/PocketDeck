package dev.codestev.pocketdesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

private val TrackPadding = 4.dp
private val SegmentHeight = 36.dp
private val SegmentElevation = 3.dp

/**
 * A pill track whose selected segment is lifted: switch between two to four views of the same content. Give it the
 * width to fill (`Modifier.fillMaxWidth()`); the segments share it in proportion to their labels, so keep every label on one line. Not for
 * navigating between screens (that is [PdFloatingNav]).
 */
@Composable
fun PdSegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = PocketDesign.shapes.pill
    Row(modifier.background(PocketDesign.colors.surfaceSunken, shape).padding(TrackPadding)) {
        labels.forEachIndexed { index, label ->
            Segment(label, selected = index == selectedIndex, weight = label.weight()) { onSelect(index) }
        }
    }
}

/** Wider words get wider segments, so a long label ("Tournament") is not cut off with a large system font. */
private fun String.weight() = (length + LABEL_PADDING_WEIGHT).toFloat()

private const val LABEL_PADDING_WEIGHT = 3

@Composable
private fun RowScope.Segment(
    label: String,
    selected: Boolean,
    weight: Float,
    onClick: () -> Unit,
) {
    val colors = PocketDesign.colors
    val shape = PocketDesign.shapes.pill
    Box(
        modifier =
            Modifier
                .weight(weight)
                .heightIn(min = SegmentHeight)
                .then(if (selected && colors.isLight) Modifier.shadow(SegmentElevation, shape) else Modifier)
                .pdSelectable(shape, selected, onClick)
                .background(
                    if (selected) colors.surfaceRaised else androidx.compose.ui.graphics.Color.Transparent,
                    shape,
                ).padding(horizontal = PdSpacing.space3),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = PocketDesign.typography.titleSm.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = if (selected) colors.ink else colors.inkMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
