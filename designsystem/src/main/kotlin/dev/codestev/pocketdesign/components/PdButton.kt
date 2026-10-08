package dev.codestev.pocketdesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

enum class PdButtonStyle {
    /** The one action a card or screen is about. */
    Primary,

    /** A supporting action: outlined. */
    Secondary,

    /** A supporting action: quiet accent fill. */
    Tonal,

    /** A link inside a flow. */
    Text,

    /** A destructive action: soft danger fill, never a filled red button. */
    Danger,
}

enum class PdButtonSize { Regular, Small }

private const val DISABLED_ALPHA = 0.45f
private val SMALL_FONT = 14.sp
private val SMALL_LINE_HEIGHT = 20.sp
private val BorderWidth = 2.dp
private val IconSize = 20.dp
private val LoadingSize = 18.dp

/**
 * A pill button with a 48dp minimum height (36dp for [PdButtonSize.Small], for dense rows only).
 *
 * Use one [PdButtonStyle.Primary] per card or screen and verbs for labels ("Import code", not "OK"). The label is
 * already localised by the caller. While [loading] the button shows a spinner and ignores taps.
 *
 * ```
 * PdButton("Import code", onClick = ::import, modifier = Modifier.fillMaxWidth())
 * ```
 */
@Composable
fun PdButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PdButtonStyle = PdButtonStyle.Primary,
    size: PdButtonSize = PdButtonSize.Regular,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val colors = PocketDesign.colors
    val shape = PocketDesign.shapes.pill
    val container =
        when (style) {
            PdButtonStyle.Primary -> colors.accent
            PdButtonStyle.Secondary -> colors.surfaceCard
            PdButtonStyle.Tonal -> colors.accentSoft
            PdButtonStyle.Text -> Color.Transparent
            PdButtonStyle.Danger -> colors.dangerSoft
        }
    val content =
        when (style) {
            PdButtonStyle.Primary -> colors.onAccent
            PdButtonStyle.Secondary -> colors.ink
            PdButtonStyle.Tonal -> colors.onAccentSoft
            PdButtonStyle.Text -> colors.accent
            PdButtonStyle.Danger -> colors.danger
        }
    val small = size == PdButtonSize.Small
    // Centre the line box: the default alignment leaves the extra line height above the glyphs, so labels sit low.
    val centred = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    val textStyle =
        PocketDesign.typography.titleSm.let {
            val base = if (small) it.copy(fontSize = SMALL_FONT, lineHeight = SMALL_LINE_HEIGHT) else it
            base.copy(lineHeightStyle = centred)
        }
    Row(
        modifier =
            modifier
                .heightIn(min = if (small) 36.dp else PdSpacing.minTouchTarget)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .pdClickable(shape, onClick, enabled = enabled && !loading)
                .background(container, shape)
                .then(
                    if (style ==
                        PdButtonStyle.Secondary
                    ) {
                        Modifier.border(BorderWidth, colors.lineStrong, shape)
                    } else {
                        Modifier
                    },
                ).padding(
                    vertical = PdSpacing.space2,
                    horizontal =
                        if (style ==
                            PdButtonStyle.Text
                        ) {
                            PdSpacing.space3
                        } else if (small) {
                            PdSpacing.space4
                        } else {
                            PdSpacing.space5
                        },
                ),
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            PdSpinner(size = LoadingSize, color = content, strokeWidth = 2.dp)
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(IconSize), tint = content)
        }
        Text(text, style = textStyle, color = content)
    }
}
