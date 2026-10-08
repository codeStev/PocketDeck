package dev.pocketdeck.adapter.ui.scan

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.codestev.pocketdesign.components.PdDialog
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R

/**
 * Says plainly what the auto-scroll accessibility service does, before it is switched on: it swipes the card list in the
 * game, reads nothing and collects nothing. The player has to agree; declining leaves them with manual scrolling.
 */
@Composable
internal fun AutoScrollConsentDialog(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
) {
    PdDialog(
        title = stringResource(R.string.consent_autoscroll_title),
        onDismissRequest = onDecline,
        confirmText = stringResource(R.string.consent_agree),
        onConfirm = onAgree,
        dismissText = stringResource(R.string.consent_decline),
    ) {
        Text(
            stringResource(R.string.consent_autoscroll_text),
            style = PocketDesign.typography.body,
            color = PocketDesign.colors.ink,
        )
    }
}
