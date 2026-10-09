package dev.codestev.pocketdesign.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdMotion
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

private val DialogMaxWidth = 360.dp
private val DialogElevation = 12.dp

/**
 * A confirmation or a small form over a dimmed scrim. Name the object in [title] ("Delete this deck?") and use the
 * action verb for [confirmText]; [destructive] turns the confirming button into the soft danger style. At most two
 * buttons (leave [dismissText] out for a dialog that only has "Done"); the body goes in [content]. Longer tasks use [PdFullScreenPanel].
 */
@Composable
fun PdDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogEntrance(DIALOG_ENTER) {
            DialogCard(title, onDismissRequest, confirmText, onConfirm, modifier, dismissText, destructive, content)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogCard(
    title: String,
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier,
    dismissText: String?,
    destructive: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = PocketDesign.shapes.xl
    Column(
        modifier =
            modifier
                .padding(horizontal = PdSpacing.space4)
                .widthIn(max = DialogMaxWidth)
                .fillMaxWidth()
                .shadow(DialogElevation, shape)
                .background(PocketDesign.colors.surfaceCard, shape)
                .padding(PdSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(PdSpacing.space3),
    ) {
        Text(title, style = PocketDesign.typography.titleMd, color = PocketDesign.colors.ink)
        content()
        // A flow, not a row: a long translation of the dismiss label would otherwise leave the confirm button a few letters of width.
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = PdSpacing.space2),
            horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(PdSpacing.space2),
        ) {
            if (dismissText != null) PdButton(dismissText, onDismissRequest, style = PdButtonStyle.Text)
            PdButton(confirmText, onConfirm, style = if (destructive) PdButtonStyle.Danger else PdButtonStyle.Primary)
        }
    }
}

/**
 * A panel over the whole screen with a title and a close button, for content that is a task of its own: a long list, a
 * form with a keyboard. [closeDescription] is the localised description of the close button.
 */
@Composable
fun PdFullScreenPanel(
    title: String,
    closeDescription: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogEntrance(PANEL_ENTER) {
            PanelBody(title, closeDescription, onClose, content)
        }
    }
}

@Composable
private fun PanelBody(
    title: String,
    closeDescription: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(PocketDesign.colors.surfacePage).imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PdSpacing.space2, vertical = PdSpacing.space2),
            horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PdIconButton(PdIcons.Close, closeDescription, onClose, style = PdIconButtonStyle.Plain)
            Text(title, style = PocketDesign.typography.titleMd, color = PocketDesign.colors.ink)
        }
        content()
    }
}

/** A dialog grows in and a panel slides up a little; closing is immediate, the platform removes the window at once. */
@Composable
private fun DialogEntrance(
    enter: EnterTransition,
    content: @Composable () -> Unit,
) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(state, enter = enter, exit = ExitTransition.None) { content() }
}

private val DIALOG_ENTER =
    fadeIn(tween(PdMotion.ENTER_MILLIS)) + scaleIn(tween(PdMotion.ENTER_MILLIS), PdMotion.ENTER_SCALE)

/** A full-screen panel starts this fraction of its height lower. */
private const val PANEL_SLIDE_DIVISOR = 12
private val PANEL_ENTER =
    fadeIn(tween(PdMotion.ENTER_MILLIS)) + slideInVertically(tween(PdMotion.ENTER_MILLIS)) { it / PANEL_SLIDE_DIVISOR }
