package dev.pocketdeck.adapter.ui.scan

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.consent_autoscroll_title)) },
        text = { Text(stringResource(R.string.consent_autoscroll_text)) },
        confirmButton = { TextButton(onClick = onAgree) { Text(stringResource(R.string.consent_agree)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.consent_decline)) } },
    )
}
