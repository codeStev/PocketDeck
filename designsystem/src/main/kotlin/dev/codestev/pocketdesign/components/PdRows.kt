package dev.codestev.pocketdesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

private val RowMinHeight = 60.dp
private val ChevronSize = 20.dp
private val LinkIconSize = 16.dp

/** A hairline between rows. Decorative: never the only thing that marks a control. */
@Composable
fun PdDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(PocketDesign.colors.line))
}

/** Collects the rows of a [PdRowGroup]. */
class PdRowGroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    /**
     * Adds a row: a [title], an optional [value] below it and a [trailing] control (a [PdTextLink], [PdChevron] or a
     * switch). Without [onClick] the row is not tappable.
     */
    fun row(
        title: String,
        onClick: (() -> Unit)? = null,
        value: String? = null,
        trailing: (@Composable () -> Unit)? = null,
    ) {
        rows += { RowContent(title, onClick, value, trailing) }
    }
}

/**
 * Plain settings rows in one rounded group, separated by hairlines. Rows are at least 60dp high; group related rows,
 * keep titles to one line and do not nest groups.
 *
 * ```
 * PdRowGroup {
 *     row("Online status", onClick = ::open, value = "All friends", trailing = { PdTextLink("Change") })
 *     row("Language", onClick = ::open, trailing = { PdChevron() })
 * }
 * ```
 */
@Composable
fun PdRowGroup(
    modifier: Modifier = Modifier,
    content: PdRowGroupScope.() -> Unit,
) {
    val scope = PdRowGroupScope().apply(content)
    val shape = PocketDesign.shapes.lg
    Column(
        modifier
            .fillMaxWidth()
            .pdCardShadow(shape)
            .clip(shape)
            .background(PocketDesign.colors.surfaceCard),
    ) {
        scope.rows.forEachIndexed { index, row ->
            if (index > 0) PdDivider()
            row()
        }
    }
}

@Composable
private fun RowContent(
    title: String,
    onClick: (() -> Unit)?,
    value: String?,
    trailing: (@Composable () -> Unit)?,
) {
    val colors = PocketDesign.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .then(if (onClick != null) Modifier.pdClickable(RectangleShape, onClick) else Modifier)
                .padding(horizontal = PdSpacing.space4, vertical = PdSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = PocketDesign.typography.titleSm, color = colors.ink)
            if (value != null) Text(value, style = PocketDesign.typography.bodySm, color = colors.inkMuted)
        }
        trailing?.invoke()
    }
}

/** A bare chevron for the end of a row that opens something. */
@Composable
fun PdChevron(modifier: Modifier = Modifier) {
    Icon(
        PdIcons.ChevronRight,
        contentDescription = null,
        modifier = modifier.size(ChevronSize),
        tint = PocketDesign.colors.inkMuted,
    )
}

/** A text link with a chevron ("Change"), in the accent colour, for the end of a row. */
@Composable
fun PdTextLink(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = PocketDesign.colors
    Row(
        modifier =
            modifier.then(
                // A soft corner, not a pill: when the text wraps at a large font a pill would clip its first letters.
                if (onClick != null) {
                    Modifier
                        .heightIn(min = PdSpacing.minTouchTarget)
                        .pdClickable(PocketDesign.shapes.sm, onClick)
                        .padding(horizontal = PdSpacing.space1)
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            Modifier.weight(1f, fill = false),
            style = PocketDesign.typography.titleSm.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = colors.accent,
        )
        Icon(
            PdIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(LinkIconSize),
            tint = colors.accent,
        )
    }
}

/**
 * A label and a value in `titleMd` with one icon action, such as copying a code. The label is a small caps line; the
 * value uses tabular numerals. Give [actionDescription] when [actionIcon] is set.
 */
@Composable
fun PdInfoCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    actionIcon: ImageVector? = null,
    actionDescription: String? = null,
    onAction: () -> Unit = {},
) {
    PdCard(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), style = PocketDesign.typography.label, color = PocketDesign.colors.inkMuted)
                Text(value, style = PocketDesign.typography.numeric, color = PocketDesign.colors.ink)
            }
            if (actionIcon !=
                null
            ) {
                PdIconButton(actionIcon, actionDescription.orEmpty(), onAction, style = PdIconButtonStyle.Plain)
            }
        }
    }
}
