package dev.codestev.pocketdesign.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.codestev.pocketdesign.theme.PdMotion
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

private val FieldMinHeight = 52.dp
private val FieldBorder = 2.dp
private val FieldIconSize = 22.dp
private val TrackWidth = 52.dp
private val TrackHeight = 32.dp
private val ThumbSize = 24.dp
private val ThumbInset = 4.dp
private const val DISABLED_ALPHA = 0.45f

/**
 * A text field: 52dp on the sunken surface with a 2dp border that holds 3:1 on every surface; focus turns the border
 * to the accent colour and [isError] to the danger colour. The label is a short [placeholder]; explanations go in
 * [supportingText] under the field (replaced by the error text when [isError]). Never write a sentence as the label.
 */
@Composable
fun PdTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = PocketDesign.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val borderColor =
        when {
            isError -> colors.danger
            focused -> colors.accent
            else -> colors.lineStrong
        }
    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else DISABLED_ALPHA),
            enabled = enabled,
            singleLine = singleLine,
            textStyle = PocketDesign.typography.titleSm.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            interactionSource = source,
            decorationBox = { inner ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = FieldMinHeight)
                            .background(colors.surfaceSunken, PocketDesign.shapes.md)
                            .border(FieldBorder, borderColor, PocketDesign.shapes.md)
                            .padding(horizontal = PdSpacing.space4),
                    horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon !=
                        null
                    ) {
                        Icon(
                            leadingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(FieldIconSize),
                            tint = colors.inkMuted,
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = PocketDesign.typography.titleSm, color = colors.inkMuted)
                        }
                        inner()
                    }
                }
            },
        )
        if (supportingText != null) {
            Text(
                supportingText,
                modifier = Modifier.padding(start = PdSpacing.space4, end = PdSpacing.space4, top = PdSpacing.space1),
                style = PocketDesign.typography.bodySm,
                color = if (isError) colors.danger else colors.inkMuted,
            )
        }
    }
}

/**
 * A setting that is on or off. The [label] is always visible and the whole row is the touch target (48dp at least);
 * apply the change immediately and name what turns on. Secondary text goes in [supportingText]. For actions use a
 * button, for more than two choices use [PdSegmentedTabs].
 */
@Composable
fun PdSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    val colors = PocketDesign.colors
    val source = remember { MutableInteractionSource() }
    val shape = PocketDesign.shapes.md
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = PdSpacing.minTouchTarget)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .pdFocusRing(source, shape)
                .clip(shape)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    interactionSource = source,
                    indication = null,
                    onValueChange = onCheckedChange,
                ),
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = PocketDesign.typography.titleSm, color = colors.ink)
            if (supportingText !=
                null
            ) {
                Text(supportingText, style = PocketDesign.typography.bodySm, color = colors.inkMuted)
            }
        }
        SwitchTrack(checked)
    }
}

@Composable
private fun SwitchTrack(checked: Boolean) {
    val colors = PocketDesign.colors
    val trackColor by animateColorAsState(
        if (checked) colors.accent else colors.lineStrong,
        tween(PdMotion.STATE_MILLIS),
        label = "switchTrack",
    )
    val thumbX by animateDpAsState(
        if (checked) TrackWidth - ThumbSize - ThumbInset else ThumbInset,
        tween(PdMotion.STATE_MILLIS),
        label = "switchThumb",
    )
    Box(Modifier.size(TrackWidth, TrackHeight).background(trackColor, PocketDesign.shapes.pill)) {
        Box(
            Modifier
                .offset { IntOffset(thumbX.roundToPx(), ThumbInset.roundToPx()) }
                .size(ThumbSize)
                .background(colors.surfaceCard, PocketDesign.shapes.pill),
        )
    }
}
