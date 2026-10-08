package dev.codestev.pocketdesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

enum class PdChipKind { Default, Selected, Success, Warning, Danger }

private val ChipHeight = 32.dp
private val IconSize = 16.dp
private val DotSize = 10.dp

/**
 * A 32dp pill for a short fact. Status kinds ([PdChipKind.Success], [PdChipKind.Warning], [PdChipKind.Danger]) always
 * need a word, never colour alone. [dot] adds a small round swatch for a categorical tint (the swatch is a graphic; the
 * text stays in ink). A chip is not a button: wrap it in a 48dp tap area if it must be tappable.
 */
@Composable
fun PdChip(
    text: String,
    modifier: Modifier = Modifier,
    kind: PdChipKind = PdChipKind.Default,
    leadingIcon: ImageVector? = null,
    dot: Color? = null,
) {
    val colors = PocketDesign.colors
    val (container, content) =
        when (kind) {
            PdChipKind.Default -> colors.surfaceSunken to colors.ink
            PdChipKind.Selected -> colors.accentSoft to colors.onAccentSoft
            PdChipKind.Success -> colors.successSoft to colors.success
            PdChipKind.Warning -> colors.warningSoft to colors.warning
            PdChipKind.Danger -> colors.dangerSoft to colors.danger
        }
    Row(
        modifier =
            modifier
                .heightIn(min = ChipHeight)
                .background(container, PocketDesign.shapes.pill)
                .padding(horizontal = PdSpacing.space3),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(DotSize).background(dot, PocketDesign.shapes.pill))
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(IconSize), tint = content)
        }
        Text(text, style = PocketDesign.typography.titleSm.copy(fontSize = 13.sp, lineHeight = 18.sp), color = content)
    }
}
