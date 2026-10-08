package dev.codestev.pocketdesign.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.codestev.pocketdesign.theme.PdMotion
import dev.codestev.pocketdesign.theme.PocketDesign

private val FocusGap = 2.dp
private val FocusStroke = 2.dp

/** A press scales the element to [PdMotion.PRESSED_SCALE] over [PdMotion.PRESS_MILLIS] ms. */
internal fun Modifier.pdPressScale(interactionSource: InteractionSource): Modifier =
    composed {
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) PdMotion.PRESSED_SCALE else 1f,
            animationSpec = tween(PdMotion.PRESS_MILLIS),
            label = "pdPressScale",
        )
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }

/**
 * A solid 2dp ring in the focus colour, outside the element with a 2dp gap, so it always lands on the surface behind
 * the element and holds 3:1 there.
 */
internal fun Modifier.pdFocusRing(
    interactionSource: InteractionSource,
    shape: Shape,
): Modifier =
    composed {
        val focused by interactionSource.collectIsFocusedAsState()
        val ring = PocketDesign.colors.focus
        if (!focused) {
            Modifier
        } else {
            Modifier.drawBehind {
                val grow = (FocusGap + FocusStroke).toPx()
                val outline =
                    shape.createOutline(
                        Size(size.width + 2 * grow, size.height + 2 * grow),
                        layoutDirection,
                        this,
                    )
                translate(-grow, -grow) { drawOutline(outline, ring, style = Stroke(FocusStroke.toPx())) }
            }
        }
    }

/**
 * Makes an element clickable the design system's way: pressed scale, visible focus ring, clipped to [shape].
 * Put the background after this modifier so it is clipped too.
 */
internal fun Modifier.pdClickable(
    shape: Shape,
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
): Modifier =
    composed {
        val source = remember { MutableInteractionSource() }
        pdPressScale(source)
            .pdFocusRing(source, shape)
            .clip(shape)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = role,
                onClick = onClick,
            )
    }

/** Like [pdClickable], for something that can be selected (a navigation tab, a segment). */
internal fun Modifier.pdSelectable(
    shape: Shape,
    selected: Boolean,
    onClick: () -> Unit,
    role: Role = Role.Tab,
): Modifier =
    composed {
        val source = remember { MutableInteractionSource() }
        pdPressScale(source)
            .pdFocusRing(source, shape)
            .clip(shape)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                role = role,
                onClick = onClick,
            )
    }
